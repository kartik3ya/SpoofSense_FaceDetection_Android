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
import android.os.Bundle;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Base64;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.CameraSelector;
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
    private static final String API_URL = "https://690xidqbzi.execute-api.ap-south-1.amazonaws.com/dev/antispoofing";
    private static final String API_KEY = "0UpOY9TMUq7iE7HvEGmKJaQ0dkkzQ6Er4K1Rm363";
    private static final String TAG = "MainActivity";
    boolean isFirstRun = true;

    private TextView feedbackTextView;
    private TextView tvApiResult;
    private String lastApiResponse = "";

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

        tvApiResult = findViewById(R.id.tvApiResult);
        tvApiResult.setVisibility(View.GONE);

        // Optional: tap the result box to see raw JSON
        tvApiResult.setOnClickListener(v -> {
            if (lastApiResponse == null || lastApiResponse.isEmpty()) return;
            new androidx.appcompat.app.AlertDialog.Builder(MainActivity.this)
                    .setTitle("API Response")
                    .setMessage(lastApiResponse)
                    .setPositiveButton("OK", null)
                    .show();
        });

        capturedImageView = findViewById(R.id.capturedImageView);
        waitLayout = findViewById(R.id.waitLayout);
        mainLayout = findViewById(R.id.mainLayout);
        guidelineLayout = findViewById(R.id.guidelineLayout);

        AppPreferences.getInstance(getApplicationContext()).getBoolean("IS_FIRST_RUN", isFirstRun);

        // Hide the capture button initially
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

        btn_check_liveness.setOnClickListener(v -> {
            isFirstRun = false;
            AppPreferences.getInstance().put("IS_FIRST_RUN",isFirstRun);
            mainLayout.setVisibility(View.VISIBLE);
            guidelineLayout.setVisibility(View.GONE);
        });

        // Initialize FaceDetector
        FaceDetectorOptions options =
                new FaceDetectorOptions.Builder()
                        .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
                        .enableTracking()
                        .build();

        faceDetector = FaceDetection.getClient(options);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(this, android.Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{android.Manifest.permission.CAMERA},
                    CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startCamera();
        }
    }

    private void startCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                Preview preview = new Preview.Builder().build();
                preview.setSurfaceProvider(previewView.getSurfaceProvider());

                imageCapture = new ImageCapture.Builder()
                        .setTargetResolution(new Size(1080, 1920))
                        .build();

                ImageAnalysis imageAnalysis =
                        new ImageAnalysis.Builder()
                                .setTargetResolution(new Size(480, 640))
                                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                                .build();

                imageAnalysis.setAnalyzer(getExecutor(), this::analyzeImage);

                CameraSelector cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA;

                cameraProvider.unbindAll();
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

            } catch (Exception e) {
                Log.e(TAG, "Error starting camera: " + e.getMessage());
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private void analyzeImage(ImageProxy imageProxy) {
        @SuppressLint("UnsafeOptInUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (faces.size() > 0) {
                            captureButton.setVisibility(View.VISIBLE);
                            showFeedback("Face detected");
                        } else {
                            captureButton.setVisibility(View.GONE);
                            showFeedback("No face detected");
                        }
                    })
                    .addOnFailureListener(e -> Log.e(TAG, "Face detection failed: " + e.getMessage()))
                    .addOnCompleteListener(task -> imageProxy.close());
        } else {
            imageProxy.close();
        }
    }

    public void onCaptureButtonClick(View view) {

        // mis-clicking prevention, using threshold of 1000 ms
        if (SystemClock.elapsedRealtime() - mLastClickTime < 3000){
            return;
        }
        mLastClickTime = SystemClock.elapsedRealtime();

        // Add vibration feedback
        if (vibrator != null) {
            long[] pattern = {0, 100}; // Wait 0 ms, vibrate for 100 ms
            vibrator.vibrate(pattern, -1); // -1 means no repeat
        }

        takePhoto();
    }

    private void takePhoto() {
        if (imageCapture != null) {
            ImageCapture.OutputFileOptions outputOptions =
                    new ImageCapture.OutputFileOptions.Builder(new ByteArrayOutputStream()).build();

            imageCapture.takePicture(outputOptions, getExecutor(), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    @SuppressLint("RestrictedApi")
                    byte[] imageData = ((ByteArrayOutputStream) outputOptions.getOutputStream()).toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                    runOnUiThread(() -> {
                        capturedImageView.setImageBitmap(bitmap);
                        capturedImageView.setVisibility(View.VISIBLE);

                        base64String = compressBitmapToBase64(bitmap);

                        File imageFile = saveBitmapToFile(bitmap);
                        Uri imageUri = Uri.fromFile(imageFile);

                        // Clear previous API result (new capture)
                        tvApiResult.setVisibility(View.GONE);
                        tvApiResult.setText("");
                        lastApiResponse = "";

                        sendPostRequest(base64String, imageUri.toString());
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() ->
                            Toast.makeText(MainActivity.this,
                                    "Photo capture failed: " + exception.getMessage(),
                                    Toast.LENGTH_LONG).show()
                    );
                }
            });
        }
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private void showFeedback(String message) {
        feedbackTextView.setText(message);
    }

    private String compressBitmapToBase64(Bitmap bitmap) {
        ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 70, byteArrayOutputStream);
        byte[] byteArray = byteArrayOutputStream.toByteArray();
        return Base64.encodeToString(byteArray, Base64.DEFAULT);
    }

    private File saveBitmapToFile(Bitmap bitmap) {
        File imageFile = new File(getExternalFilesDir(null), "captured_image.jpg");
        try (FileOutputStream fos = new FileOutputStream(imageFile)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 90, fos);
            fos.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return imageFile;
    }

    private JSONObject prepareJsonData(String base64String) {
        JSONObject jsonObject = new JSONObject();
        try {
            jsonObject.put("image_base64", base64String);
        } catch (JSONException e) {
            e.printStackTrace();
        }
        return jsonObject;
    }

    private void sendPostRequest(String base64String, String imageFileString) {

        waitLayout.setVisibility(View.VISIBLE);

        JSONObject jsonObject = prepareJsonData(base64String);
        String jsonString = jsonObject.toString();

        OkHttpClient.Builder builder = new OkHttpClient.Builder();
        builder.connectTimeout(30, TimeUnit.MINUTES)
                .writeTimeout(30, TimeUnit.MINUTES)
                .readTimeout(30, TimeUnit.MINUTES);

        OkHttpClient client = builder.build();

        MediaType JSON = MediaType.get("application/json; charset=utf-8");
        RequestBody body = RequestBody.create(jsonString, JSON);

        Request request = new Request.Builder()
                .url(API_URL)
                .post(body)
                .addHeader("x-api-key", API_KEY)
                .addHeader("Content-Type", "application/json")
                .build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                e.printStackTrace();
                runOnUiThread(() -> {
                    waitLayout.setVisibility(View.GONE);
                    lastApiResponse = "";
                    tvApiResult.setText("Request failed:\n" + e.getMessage());
                    tvApiResult.setVisibility(View.VISIBLE);
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                if (response.isSuccessful()) {
                    String responseBody = response.body() != null ? response.body().string() : "";

                    runOnUiThread(() -> {
                        waitLayout.setVisibility(View.GONE);
                        lastApiResponse = responseBody;

                        try {
                            JSONObject obj = new JSONObject(responseBody);

                            boolean success = obj.optBoolean("success", false);
                            String modelOutput = obj.optString("model_output", "unknown");
                            isReal = "real".equalsIgnoreCase(modelOutput);

                            StringBuilder sb = new StringBuilder();
                            sb.append(success ? "✅ Success" : "❌ Failed");
                            sb.append("\nModel output: ").append(modelOutput);
                            if (obj.has("score")) sb.append("\nScore: ").append(obj.optDouble("score"));
                            if (obj.has("confidence")) sb.append("\nConfidence: ").append(obj.optDouble("confidence"));
                            if (obj.has("message")) sb.append("\nMessage: ").append(obj.optString("message"));

                            tvApiResult.setText(sb.toString());
                            tvApiResult.setVisibility(View.VISIBLE);

                        } catch (Exception e) {
                            tvApiResult.setText("Error parsing JSON:\n" + e.getMessage() + "\n\nRaw:\n" + responseBody);
                            tvApiResult.setVisibility(View.VISIBLE);
                        }
                    });

                } else {
                    String errBody = response.body() != null ? response.body().string() : "";
                    Log.e("POST_ERROR", "Response error: " + response.code() + " " + response.message());
                    String finalErrBody = errBody;

                    runOnUiThread(() -> {
                        waitLayout.setVisibility(View.GONE);
                        lastApiResponse = finalErrBody;

                        String msg = "HTTP error: " + response.code() + " " + response.message();
                        if (finalErrBody != null && !finalErrBody.isEmpty()) {
                            msg += "\n\n" + finalErrBody;
                        }
                        tvApiResult.setText(msg);
                        tvApiResult.setVisibility(View.VISIBLE);
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
                startCamera();
            } else {
                Toast.makeText(this, "Camera permission denied", Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        isFirstRun = true;
        AppPreferences.getInstance().put("IS_FIRST_RUN", isFirstRun);
    }
}
