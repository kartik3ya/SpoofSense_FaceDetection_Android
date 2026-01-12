package com.spoofsense.facedetection;

import android.annotation.SuppressLint;
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
    // UPDATED CREDENTIALS
    private static final String API_URL = "https://z3jwq0rjyj.execute-api.ap-south-1.amazonaws.com/prod/robust";
    private static final String API_KEY = "Gg7UIgXzG98XRbw8epG7C3NUVlhDfZHV5axdtoEh";
    
    private FaceDetector faceDetector;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Button captureButton, btn_check_liveness;
    private ImageView capturedImageView;
    private ConstraintLayout waitLayout, guidelineLayout, mainLayout;
    private TextView feedbackTextView;
    private Vibrator vibrator;
    private long mLastClickTime = 0;
    private boolean isFirstRun = true;

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

        isFirstRun = AppPreferences.getInstance(getApplicationContext()).getBoolean("IS_FIRST_RUN", true);

        if (isFirstRun) {
            guidelineLayout.setVisibility(View.VISIBLE);
            mainLayout.setVisibility(View.GONE);
        } else {
            mainLayout.setVisibility(View.VISIBLE);
            guidelineLayout.setVisibility(View.GONE);
        }

        btn_check_liveness.setOnClickListener(v -> {
            isFirstRun = false;
            AppPreferences.getInstance(this).put("IS_FIRST_RUN", false);
            guidelineLayout.setVisibility(View.GONE);
            mainLayout.setVisibility(View.VISIBLE);
        });

        FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                .build();
        faceDetector = FaceDetection.getClient(options);

        checkCameraPermission();
    }

    private void checkCameraPermission() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.CAMERA}, CAMERA_PERMISSION_REQUEST_CODE);
        } else {
            startFrontCamera();
        }
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startFrontCamera() {
        ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                CameraSelector cameraSelector = new CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_FRONT).build();
                imageCapture = new ImageCapture.Builder().setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY).build();
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
                Log.e("CameraX", "Error", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void detectFaces(ImageProxy imageProxy) {
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        if (!faces.isEmpty()) {
                            Rect box = faces.get(0).getBoundingBox();
                            checkFacePosition(box, imageProxy.getWidth(), imageProxy.getHeight());
                        } else {
                            feedbackTextView.setText("No face detected");
                            captureButton.setVisibility(View.GONE);
                        }
                    })
                    .addOnCompleteListener(task -> imageProxy.close());
        }
    }

    private void checkFacePosition(Rect box, int w, int h) {
        int centerX = box.centerX();
        int centerY = box.centerY();
        int targetY = (int) (h * 0.35);
        
        boolean centered = Math.abs(centerX - (w / 2)) < (w * 0.1) && Math.abs(centerY - targetY) < (h * 0.1);
        boolean sized = box.width() > 300 && box.width() < 500;

        if (!centered) feedbackTextView.setText("Center your face");
        else if (box.width() > 500) feedbackTextView.setText("Move away");
        else if (box.width() < 300) feedbackTextView.setText("Move closer");
        else {
            feedbackTextView.setText("Perfect! Stay still");
            captureButton.setVisibility(View.VISIBLE);
            return;
        }
        captureButton.setVisibility(View.GONE);
    }

    public void onCaptureButtonClick(View view) {
        if (SystemClock.elapsedRealtime() - mLastClickTime < 3000) return;
        mLastClickTime = SystemClock.elapsedRealtime();

        if (vibrator != null) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) vibrator.vibrate(VibrationEffect.createOneShot(100, 255));
            else vibrator.vibrate(100);
        }
        takePhoto();
    }

    private void takePhoto() {
        if (imageCapture == null) return;
        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(outputStream).build();

        imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults results) {
                byte[] data = outputStream.toByteArray();
                Bitmap bitmap = BitmapFactory.decodeByteArray(data, 0, data.length);
                capturedImageView.setImageBitmap(bitmap);
                capturedImageView.setVisibility(View.VISIBLE);
                
                String base64 = compressToBase64(bitmap);
                File file = saveBitmap(bitmap);
                sendPostRequest(base64, Uri.fromFile(file).toString());
            }

            @Override
            public void onError(@NonNull ImageCaptureException e) {
                Toast.makeText(MainActivity.this, "Capture failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    private String compressToBase64(Bitmap b) {
        ByteArrayOutputStream os = new ByteArrayOutputStream();
        b.compress(Bitmap.CompressFormat.JPEG, 70, os);
        return Base64.encodeToString(os.toByteArray(), Base64.DEFAULT);
    }

    private File saveBitmap(Bitmap b) {
        File f = new File(getExternalFilesDir(null), "temp_selfie.jpg");
        try (FileOutputStream out = new FileOutputStream(f)) {
            b.compress(Bitmap.CompressFormat.JPEG, 50, out);
        } catch (IOException e) { e.printStackTrace(); }
        return f;
    }

    private void sendPostRequest(String base64, String uri) {
        waitLayout.setVisibility(View.VISIBLE);
        OkHttpClient client = new OkHttpClient.Builder().connectTimeout(30, TimeUnit.SECONDS).build();
        
        JSONObject json = new JSONObject();
        try { json.put("data", base64); } catch (JSONException e) { e.printStackTrace(); }

        RequestBody body = RequestBody.create(json.toString(), MediaType.get("application/json; charset=utf-8"));
        Request request = new Request.Builder().url(API_URL).post(body).addHeader("x-api-key", API_KEY).build();

        client.newCall(request).enqueue(new Callback() {
            @Override
            public void onFailure(@NonNull Call call, @NonNull IOException e) {
                runOnUiThread(() -> {
                    waitLayout.setVisibility(View.GONE);
                    // DEBUG: Pass network error to Result screen
                    Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                    intent.putExtra("jsonResponse", "NETWORK ERROR: " + e.getMessage());
                    intent.putExtra("IS_REAL", false);
                    intent.putExtra("imageUri", uri);
                    startActivity(intent);
                    Toast.makeText(MainActivity.this, "Network error", Toast.LENGTH_SHORT).show();
                });
            }

            @Override
            public void onResponse(@NonNull Call call, @NonNull Response response) throws IOException {
                String bodyStr = response.body() != null ? response.body().string() : "No response body";
                runOnUiThread(() -> {
                    waitLayout.setVisibility(View.GONE);
                    Intent intent = new Intent(MainActivity.this, ResultActivity.class);
                    intent.putExtra("imageUri", uri);
                    
                    if (response.isSuccessful()) {
                        try {
                            JSONObject respJson = new JSONObject(bodyStr);
                            boolean isReal = respJson.optString("model_output", "").equalsIgnoreCase("real");
                            intent.putExtra("jsonResponse", bodyStr);
                            intent.putExtra("IS_REAL", isReal);
                        } catch (JSONException e) {
                            intent.putExtra("jsonResponse", "PARSING ERROR: " + bodyStr);
                            intent.putExtra("IS_REAL", false);
                        }
                    } else {
                        // DEBUG: Pass API Error (e.g. 403, 500) to Result screen
                        intent.putExtra("jsonResponse", "API ERROR (" + response.code() + "): " + bodyStr);
                        intent.putExtra("IS_REAL", false);
                        Toast.makeText(MainActivity.this, "API error: " + response.code(), Toast.LENGTH_SHORT).show();
                    }
                    startActivity(intent);
                });
            }
        });
    }
}
