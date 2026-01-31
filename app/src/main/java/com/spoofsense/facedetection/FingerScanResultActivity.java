package com.spoofsense.facedetection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;

import java.io.FileNotFoundException;

public class FingerScanResultActivity extends AppCompatActivity {

    String imageUriString;
    ImageView ivResult, ivCapturedImage;
    TextView tvResult;
    Button btn_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_finger_scan_result);

        ivResult = findViewById(R.id.ivResult);
        ivCapturedImage = findViewById(R.id.ivCapturedImage);
        tvResult = findViewById(R.id.tvResult);
        btn_home = findViewById(R.id.btn_home);

        // Get the image URI from the intent
        imageUriString = getIntent().getStringExtra("imageUri");

        // Display success icon and liveness confirmed message
        ivResult.setVisibility(View.VISIBLE);
        ivResult.setImageDrawable(getResources().getDrawable(R.drawable.success));
        tvResult.setText("Liveness confirmed");

        // Load and display the captured image
        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            Bitmap bitmap = null;
            try {
                bitmap = BitmapFactory.decodeStream(getContentResolver().openInputStream(imageUri));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
            if (bitmap != null) {
                ivCapturedImage.setImageBitmap(bitmap);
            }
        }

        btn_home.setOnClickListener(this::onHomeButtonClick);
    }

    // Button click method with Intent
    private void onHomeButtonClick(View view) {
        // Use an explicit intent to start HomeActivity
        Intent intent = new Intent(FingerScanResultActivity.this, HomeActivity.class);
        startActivity(intent);
        finishAffinity();
    }
}
