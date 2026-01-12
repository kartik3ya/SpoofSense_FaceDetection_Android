package com.spoofsense.facedetection;

import android.annotation.SuppressLint;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.Manifest;
import androidx.annotation.NonNull;
import androidx.annotation.OptIn;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ExperimentalGetImage;
import androidx.camera.core.ImageAnalysis;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.ImageProxy;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.constraintlayout.widget.ConstraintLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;
import com.spoofsense.facedetection.utils.AppPreferences;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class MainActivity extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    // Updated API details
    private static final String API_URL = "https://z3jwq0rjyj.execute-api.ap-south-1.amazonaws.com/prod/antispoofing";
    private static final String API_KEY = "Gg7UIgXzG98XRbw8epG7C3NUVlhDfZHV5axdtoEh";
    private static final String TAG = "MainActivity";
    boolean isFirstRun = true;

    private TextView feedbackTextView;
    private FaceDetector faceDetector;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button captureButton, btn_check_liveness;
    private ImageView capturedImageView;
    private ConstraintLayout waitLayout, guidelineLayout, mainLayout;
    String base64String;
    private boolean isReal = false;
    private Vibrator vibrator;
    private long mLastClickTime = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        captureButton = findViewById(R.id.captureButton);
        btn_check_liveness = findViewById(R.id.btn_check_liveness);
        previewView = findViewById(R.id.previewView);
        feedbackTextView = findViewById(R.id.feedbackTextView);
        capturedImageView = findViewById(R.id.capturedImageView);
        waitLayout = findViewById(R.id.waitLayout);
        mainLayout = findViewById(R.id.mainLayout);
        guidelineLayout = findViewById(R.id.guidelineLayout);

        // Fixed assignment logic for shared preferences
        isFirstRun = AppPreferences.getInstance(getApplicationContext()).getBoolean("IS_FIRST_RUN", isFirstRun);

        captureButton.setVisibility(View.GONE);
        waitLayout.setVisibility(View.GONE);

        if (isFirstRun){
            guidelineLayout.setVisibility(View.VISIBLE);
            mainLayout.setVisibility(View.GONE);
        }
        else{
            mainLayout.setVisibility(View.VISIBLE);
            guidelineLayout.setVisibility(View.GONE);
        }

        btn_check_liveness.setOnClickListener(view -> {
            isFirstRun = false;
            AppPreferences.getInstance(MainActivity.this).put("IS_FIRST_RUN", isFirstRun);
            guidelineLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_ALL)
                .build();
        faceDetector = FaceDetection.getClient(options);

        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            requestCameraPermission();
        } else {
            startFrontCamera();
        }
    }

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

                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                        .build();

                imageCapture = new ImageCapture.Builder()
                        .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                        .build();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                ImageAnalysis imageAnalysis = new ImageAnalysis.Builder()
                        .setTargetResolution(new Size(640, 480))
                        .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                        .build();
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::detectFaces);

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);
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
                            Face face = faces.get(0);
                            Rect boundingBox = face.getBoundingBox();
                            checkFacePositionAndDistance(boundingBox, imageProxy.getWidth(), imageProxy.getHeight(), boundingBox.width(), boundingBox.height());
                        } else {
                            feedbackTextView.setText("No face detected");
                        }
                    })
                    .addOnFailureListener(e -> Log.e("FaceDetection", "Face detection failed", e))
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void checkFacePositionAndDistance(Rect faceBoundingBox, int imageWidth, int imageHeight, int faceWidth, int faceHeight) {
        int faceCenterX = faceBoundingBox.centerX();
        int faceCenterY = faceBoundingBox.centerY();

        int ovalCenterX = imageWidth / 2;
        int ovalCenterY = (int) (imageHeight * 0.35);

        int horizontalMargin = (int) (imageWidth * 0.1);
        int verticalMargin = (int) (imageHeight * 0.1);

        boolean isCenteredX = faceCenterX > (ovalCenterX - horizontalMargin) && faceCenterX < (ovalCenterX + horizontalMargin);
        boolean isCenteredY = faceCenterY > (ovalCenterY - verticalMargin) && faceCenterY < (ovalCenterY + verticalMargin);

        int closeThreshold = 500;
        int farThreshold = 300;

        if (!isCenteredX || !isCenteredY) {
            showFeedback("Center your face");
            captureButton.setVisibility(View.GONE);
        } else if (faceWidth > closeThreshold || faceHeight > closeThreshold) {
            showFeedback("Move away from the camera");
            captureButton.setVisibility(View.GONE);
        } else if (faceWidth < farThreshold || faceHeight < farThreshold) {
            showFeedback("Move closer to the camera");
            captureButton.setVisibility(View.GONE);
        } else {
            showFeedback("Perfect! Stay still");
            captureButton.setVisibility(View.VISIBLE);
        }
    }

    public void onCaptureButtonClick(View view) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 3000){
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vibrator.vibrate(VibrationEffect.createOneShot(100, VibrationEffect.DEFAULT_AMPLITUDE));
            } else {
                vibrator.vibrate(100);
            }
        }

        takePhoto();
    }

    private void takePhoto() {
        if (imageCapture != null) {
            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new ByteArrayOutputStream()).build();

            imageCapture.takePicture(outputOptions, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    @SuppressLint("RestrictedApi") byte[] imageData = ((ByteArrayOutputStream) outputOptions.getOutputStream()).toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                    runOnUiThread(() -> {
                        capturedImageView.setImageBitmap(bitmap);
                        capturedImageView.setVisibility(View.VISIBLE);
                        base64String = compressBitmapToBase64(bitmap);

                        File imageFile = saveBitmapToFile(bitmap);
                        Uri imageUri = Uri.fromFile(imageFile);

                        sendPostRequest(base64String, imageUri.toString());
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() -> Toast.makeText(MainActivity.this,"Photo capture failed: " + exception.getMessage(),Toast.LENGTH_LONG).show());
                }
            });
        }
    }

    private void showFeedback(String message) {
        feedbackTextView.setText(message);
    }

    private String compressBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        byte[] compressedByteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(compressedByteArray, Base64.DEFAULT);
    }

    private File saveBitmapToFile(Bitmap bitmap) {
        File file = new File(getExternalFilesDir(null), "captured_image.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    private JSONObject prepareJsonData(String base64String) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("data", base64String);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    @Override
    protected void onResume() {
        super.onResume();
        waitLayout.setVisibility(View.GONE);
    }

    private void sendPostRequest(String base64String, String imageFileString) {
        waitLayout.setVisibility(View.VISIBLE);
        JSONObject jsonObject = prepareJsonData(base64String);
        String jsonString = jsonObject.toString();

        OkHttpClient client = new OkHttpClient.Builder()
                .connectTimeout(30, TimeUnit.SECONDS)
                .writeTimeout(30, TimeUnit.SECONDS)
                .readTimeout(30, TimeUnit.SECONDS)
                .build();

        RequestBody requestBody = RequestBody.create(
                jsonString,
                MediaType.get("application/json; charset=utf-8")
        );

        Request request = new Request.Builder()
                .url(API_URL)
                .post(requestBody)
                .addHeader("x-api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    waitLayout.setVisibility(View.GONE);
                    // Detailed error Toast for debugging
                    Toast.makeText(MainActivity.this, "Network Error: " + e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";
                    try {
                        JSONObject jsonResp = new JSONObject(responseBody);
                        boolean success = jsonResp.optBoolean("success", false);
                        String modelOutput = jsonResp.optString("model_output", "");
                        
                        Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                        runOnUiThread(() -> {
                            isReal = modelOutput.equalsIgnoreCase("real");
                            intent.putExtra("jsonResponse", responseBody);
                            intent.putExtra("IS_REAL", isReal);
                            intent.putExtra("imageUri", imageFileString);
                            startActivity(intent);
                        });
                    } catch (JSONException e) {
                        runOnUiThread(() -> {
                            waitLayout.setVisibility(View.GONE);
                            Toast.makeText(MainActivity.this, "Parsing Error: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                        });
                    }
                } else {
                    runOnUiThread(() -> {
                        waitLayout.setVisibility(View.GONE);
                        // Detailed API error Toast for debugging
                        Toast.makeText(MainActivity.this, "API Error (" + response.code() + "): " + response.message(), Toast.LENGTH_LONG).show();
                    });
                }
            }
        });
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                startFrontCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        // Ensuring demo resets for next launch
        AppPreferences.getInstance().put("IS_FIRST_RUN", true);
    }
}
