package com.spoofsense.facedetection;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.PointF;
import android.graphics.Rect;
import android.media.Image;
import android.os.Bundle;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LifecycleOwner;

import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private FaceOverlayView faceOverlayView;
    private TextView feedbackTextView;
    private FaceDetector faceDetector;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button captureButton;
    private String currentPhotoPath; // Store the path of the captured image
    private ImageView capturedImageView;
    private ProgressBar progressBar;
    private boolean isImageCaptured = false; // Flag to prevent multiple captures
    String base64String;
    private boolean isReal = false;
    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        captureButton = findViewById(R.id.captureButton);
        previewView = findViewById(R.id.previewView);
        faceOverlayView = findViewById(R.id.faceOverlayView);
        feedbackTextView = findViewById(R.id.feedbackTextView);
        capturedImageView = findViewById(R.id.capturedImageView);
        progressBar = findViewById(R.id.progressBar);

        // Hide the capture button initially
        captureButton.setVisibility(View.GONE);
        progressBar.setVisibility(View.GONE);


        // Initialize ML Kit Face Detection
        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        faceDetector = FaceDetection.getClient(options);

        checkCameraPermission();
    }

    // Check if camera permission is granted
    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            // If permission is not granted, request the permission
            requestCameraPermission();
        } else {
            // Permission already granted, open the camera
            // Start front camera
            startFrontCamera();
        }
    }

    // Request camera permission
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startFrontCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Select the front camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                // Preview use case
                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                // Image analysis use case
                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), imageProxy -> {
                    detectFaces(imageProxy);
                });

                // Bind the camera to the lifecycle
                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview,imageCapture, imageAnalysis);
            } catch (Exception e) {
                Log.e("CameraX", "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void detectFaces(ImageProxy imageProxy) {
        @SuppressLint("UnsafeExperimentalUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty()) {
                            Face face = faces.get(0); // Get the first face (assuming one face for simplicity)

                            // Detect the position and size of the face
                            Rect boundingBox = face.getBoundingBox();
                            int faceWidth = boundingBox.width();
                            int faceHeight = boundingBox.height();

                            // Check face position and size to provide feedback
                            checkFacePositionAndDistance(boundingBox, imageProxy.getWidth(), imageProxy.getHeight(), faceWidth, faceHeight);
                        } else {
                            feedbackTextView.setText("No face detected");
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.e("FaceDetection", "Face detection failed", e);
                    })
                    .addOnCompleteListener(task -> {
                        imageProxy.close(); // Close imageProxy when processing is done
                    });
        }
    }

    private void checkFacePositionAndDistance(Rect faceBoundingBox, int imageWidth, int imageHeight, int faceWidth, int faceHeight) {
        int faceCenterX = faceBoundingBox.centerX();
        int faceCenterY = faceBoundingBox.centerY();

        // Define thresholds for centering the face in the oval
        int ovalCenterX = imageWidth / 2;
        int ovalCenterY = imageHeight / 2;
        int horizontalMargin = (int) (imageWidth * 0.1); // Allow a 10% margin for centering
        int verticalMargin = (int) (imageHeight * 0.1);

        boolean isCenteredX = faceCenterX > (ovalCenterX - horizontalMargin) && faceCenterX < (ovalCenterX + horizontalMargin);
        boolean isCenteredY = faceCenterY > (ovalCenterY - verticalMargin) && faceCenterY < (ovalCenterY + verticalMargin);

        // Define size thresholds for moving closer or farther
        int closeThreshold = 500;
        int farThreshold = 300;

        if (!isCenteredX || !isCenteredY) {
            showFeedback("Center your face");
            captureButton.setVisibility(View.GONE); // Hide the capture button
        } else if (faceWidth > closeThreshold || faceHeight > closeThreshold) {
            showFeedback("Move away from the camera");
            captureButton.setVisibility(View.GONE); // Hide the capture button
        } else if (faceWidth < farThreshold || faceHeight < farThreshold) {
            showFeedback("Move closer to the camera");
            captureButton.setVisibility(View.GONE); // Hide the capture button
        } else {
            showFeedback("Perfect! Stay still");
            captureButton.setVisibility(View.VISIBLE); // Show the capture button
        }
    }

    public void onCaptureButtonClick(View view) {
        takePhoto();
    }

    private void takePhoto() {
        if (imageCapture != null) {

            isImageCaptured = true; // Set the flag to true to prevent multiple captures

            // Create output options
            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new ByteArrayOutputStream()).build();

            imageCapture.takePicture(outputOptions, getExecutor(), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    // Get image data from OutputFileOptions
                    byte[] imageData = ((ByteArrayOutputStream) outputOptions.getOutputStream()).toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                    // Display the image
                    runOnUiThread(() -> {
                        capturedImageView.setImageBitmap(bitmap);
                        capturedImageView.setVisibility(View.VISIBLE);
                        Toast.makeText(MainActivity.this,"Photo captured successfully!",Toast.LENGTH_LONG).show();
                        // Convert to Base64
                        base64String = convertImageToBase64(imageData);
                        // Send the Base64 string via POST request
                        Log.d("TAG_FOR_BASE64STRING","base64String:---"+ base64String);
                        sendPostRequest(base64String);
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this,"Photo capture failed: " + exception.getMessage(),Toast.LENGTH_LONG).show();
//                        showFeedback("Photo capture failed: " + exception.getMessage());
                    });
                }
            });
        }
    }
    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    // Utility method to get the output directory
    private File getOutputDirectory() {
        File mediaDir = getExternalMediaDirs()[0];
        File file = new File(mediaDir, "FaceDetectionApp");
        if (!file.exists()) {
            file.mkdirs();
        }
        return file;
    }

    private void showFeedback(String message) {
        // Update UI to show feedback
        feedbackTextView.setText(message); // Assuming feedbackTextView is defined in your layout
    }

    private String convertImageToBase64(byte[] imageData) {
        return Base64.encodeToString(imageData, Base64.DEFAULT);
    }

    // Prepare the JSON data
    private JSONObject prepareJsonData(String base64String) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("data", base64String);
            // Add other fields as needed
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    // Send POST request
    private void sendPostRequest(String base64String) {

        progressBar.setVisibility(View.VISIBLE);
        // Create JSON object
        JSONObject jsonObject = prepareJsonData(base64String);

        // Convert JSON to string
        String jsonString = jsonObject.toString();

        // Create OkHttpClient instance
        OkHttpClient client = new OkHttpClient();

        // Create request body
        RequestBody requestBody = RequestBody.create(
                jsonString,
                MediaType.get("application/json; charset=utf-8")
        );

        // Build the request
        Request request = new Request.Builder()
                .url("https://690xidqbzi.execute-api.ap-south-1.amazonaws.com/dev/antispoofing")
                .post(requestBody)
                .addHeader("x-api-key","0UpOY9TMUq7iE7HvEGmKJaQ0dkkzQ6Er4K1Rm363")
                .addHeader("Content-Type", "application/json")
                .build();

        // Send the request asynchronously
        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    progressBar.setVisibility(View.GONE);
                    Toast.makeText(MainActivity.this, "Request failed", Toast.LENGTH_SHORT).show();
                });
                // Handle request failure
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    try {
                        JSONObject jsonObject = new JSONObject(responseBody);

                        // Extract the "model_output" value
                        String modelOutput = jsonObject.getString("model_output");
                        Log.d("POST_SUCCESS", "Model Output: " + modelOutput);
                        if (modelOutput.equalsIgnoreCase("real")){
                            isReal = true;
                        }
                        else {
                            isReal = false;
                        }

                        Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                        intent.putExtra("jsonResponse", responseBody);
                        intent.putExtra("IS_REAL",isReal);
                        startActivity(intent);
                        // Handle successful response
                    Log.d("POST_SUCCESS", responseBody);
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Request POST_SUCCESS", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    });
                    } catch (JSONException e) {
                        throw new RuntimeException(e);
                    }
                } else {
                    // Handle response error
                    Log.e("POST_ERROR", "Response error: " + response.message());
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Request POST_ERROR", Toast.LENGTH_SHORT).show();
                        progressBar.setVisibility(View.GONE);
                    });                }
            }
        });

    }

    // Handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the camera
                // Start front camera
                startFrontCamera();
            } else {
                // Permission denied
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }
}
