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
import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;
import java.io.InputStream;

public class ResultActivity extends AppCompatActivity {

    String jsonResponse, imageUriString;
    boolean isReal;
    ImageView ivResult, ivCapturedImage;
    TextView tvJsonResult;
    TextView tvResult;
    Button btn_home;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ivResult = findViewById(R.id.ivResult);
        ivCapturedImage = findViewById(R.id.ivCapturedImage);
        tvJsonResult = findViewById(R.id.tvJsonResult);
        tvResult = findViewById(R.id.tvResult);
        btn_home = findViewById(R.id.btn_home);
        
        imageUriString = getIntent().getStringExtra("imageUri");
        jsonResponse = getIntent().getStringExtra("jsonResponse");
        isReal = getIntent().getBooleanExtra("IS_REAL", false);

        tvJsonResult.setText("Real: " + isReal + "\nResponse: " + jsonResponse);
        
        ivResult.setVisibility(View.VISIBLE);
        if (isReal) {
            ivResult.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.success));
            tvResult.setText("Liveness confirmed");
        } else {
            ivResult.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.fail));
            tvResult.setText("Please try again. Ensure that \nthe selfie is captured with\nsufficient light.");
        }

        if (imageUriString != null) {
            Uri imageUri = Uri.parse(imageUriString);
            try {
                InputStream inputStream = getContentResolver().openInputStream(imageUri);
                Bitmap bitmap = BitmapFactory.decodeStream(inputStream);
                ivCapturedImage.setImageBitmap(bitmap);
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }

        btn_home.setOnClickListener(this::onHomeButtonClick);
    }

    private void onHomeButtonClick(View view) {
        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        startActivity(intent);
        finishAffinity();
    }
}
