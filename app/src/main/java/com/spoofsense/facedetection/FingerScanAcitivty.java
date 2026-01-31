package com.spoofsense.facedetection;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Rect;
import android.media.Image;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.SystemClock;
import android.os.Vibrator;
import android.util.Log;
import android.util.Size;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
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
import androidx.camera.core.TorchState;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.datatransport.backend.cct.BuildConfig;
import com.google.common.util.concurrent.ListenableFuture;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Executor;

public class FingerScanAcitivty extends AppCompatActivity {

    private static final int CAMERA_PERMISSION_REQUEST_CODE = 100;
    private static final String TAG = "FingerScanActivity";

    private TextView feedbackTextView;
    private FaceDetector faceDetector;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private Camera camera;
    private Button captureButton;
    private long mLastClickTime = 0;
    private Vibrator vibrator;
    private Handler handler = new Handler();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_scan_acitivty);

        vibrator = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        captureButton = findViewById(R.id.captureButton);
        previewView = findViewById(R.id.previewView);
        feedbackTextView = findViewById(R.id.tvFeedback);

        // Show capture button initially for finger scan
        captureButton.setVisibility(View.VISIBLE);

        // Initialize ML Kit Face Detection (for basic object detection)
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
            // Start back camera
            startBackCamera();
        }
    }

    // Request camera permission
    private void requestCameraPermission() {
        ActivityCompat.requestPermissions(this,
                new String[]{Manifest.permission.CAMERA},
                CAMERA_PERMISSION_REQUEST_CODE);
    }

    @OptIn(markerClass = ExperimentalGetImage.class)
    private void startBackCamera() {
        final ListenableFuture<ProcessCameraProvider> cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                ProcessCameraProvider cameraProvider = cameraProviderFuture.get();

                // Select the back camera
                CameraSelector cameraSelector = new CameraSelector.Builder()
                        .requireLensFacing(CameraSelector.LENS_FACING_BACK)
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
                imageAnalysis.setAnalyzer(ContextCompat.getMainExecutor(this), this::detectObjects);

                // Bind the camera to the lifecycle
                cameraProvider.unbindAll();
                camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture, imageAnalysis);

                // Turn on flashlight by default
                if (camera.getCameraInfo().hasFlashUnit()) {
                    camera.getCameraControl().enableTorch(true);
                }
            } catch (Exception e) {
                Log.e("CameraX", "Error starting camera", e);
            }
        }, ContextCompat.getMainExecutor(this));
    }

    @ExperimentalGetImage
    private void detectObjects(ImageProxy imageProxy) {
        @SuppressLint("UnsafeExperimentalUsageError")
        Image mediaImage = imageProxy.getImage();
        if (mediaImage != null) {
            InputImage image = InputImage.fromMediaImage(mediaImage, imageProxy.getImageInfo().getRotationDegrees());

            // Using face detection as a basic object detection
            faceDetector.process(image)
                    .addOnSuccessListener(faces -> {
                        // For finger scan, we're just showing the camera view
                        // The user will manually align and capture
                        if (!faces.isEmpty()) {
                            feedbackTextView.setText("Align your finger within the frame");
                        } else {
                            feedbackTextView.setText("Align your finger within the frame");
                        }
                    })
                    .addOnFailureListener(e -> Log.e("Detection", "Detection failed", e))
                    .addOnCompleteListener(task -> {
                        imageProxy.close(); // Close imageProxy when processing is done
                    });
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
            // Create output options
            ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(new ByteArrayOutputStream()).build();

            imageCapture.takePicture(outputOptions, getExecutor(), new ImageCapture.OnImageSavedCallback() {
                @Override
                public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                    // Get image data from OutputFileOptions
                    @SuppressLint("RestrictedApi") byte[] imageData = ((ByteArrayOutputStream) outputOptions.getOutputStream()).toByteArray();
                    Bitmap bitmap = BitmapFactory.decodeByteArray(imageData, 0, imageData.length);

                    // Save the image
                    File imageFile = saveBitmapToFile(bitmap);
                    Uri imageUri = Uri.fromFile(imageFile);

                    // Navigate to result activity with 2 second delay
                    runOnUiThread(() -> {
                        handler.postDelayed(() -> {
                            Intent intent = new Intent(FingerScanAcitivty.this, FingerScanResultActivity.class);
                            intent.putExtra("imageUri", imageUri.toString());
                            startActivity(intent);
                        }, 2000);
                    });
                }

                @Override
                public void onError(@NonNull ImageCaptureException exception) {
                    runOnUiThread(() -> {
                        feedbackTextView.setText("Photo capture failed: " + exception.getMessage());
                    });
                }
            });
        }
    }

    private Executor getExecutor() {
        return ContextCompat.getMainExecutor(this);
    }

    private File saveBitmapToFile(Bitmap bitmap) {
        File file = new File(getExternalFilesDir(null), "finger_captured_image.jpg");
        try (FileOutputStream out = new FileOutputStream(file)) {
            bitmap.compress(Bitmap.CompressFormat.JPEG, 50, out);
            out.flush();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return file;
    }

    // Handle the result of the permission request
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == CAMERA_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted, open the camera
                // Start back camera
                startBackCamera();
            } else {
                // Permission denied
                finish();
            }
        }
    }
}
