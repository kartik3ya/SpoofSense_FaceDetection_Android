package com.spoofsense.facedetection;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;

import java.io.FileNotFoundException;

public class ResultActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ImageView ivResult = findViewById(R.id.ivResult);
        ImageView ivCapturedImage = findViewById(R.id.ivCapturedImage);
        TextView tvJsonResult = findViewById(R.id.tvJsonResult);
        TextView tvResult = findViewById(R.id.tvResult);
        Button btn_home = findViewById(R.id.btn_home);

        String jsonResponse = getIntent().getStringExtra("jsonResponse");
        boolean isReal = getIntent().getBooleanExtra("IS_REAL", false);
        String imageUriString = getIntent().getStringExtra("imageUri");

        // SHOW FULL DEBUG INFO
        tvJsonResult.setText("DEBUG OUTPUT:\n" + jsonResponse);

        if (isReal) {
            ivResult.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.success));
            tvResult.setText("Liveness confirmed");
        } else {
            ivResult.setImageDrawable(ContextCompat.getDrawable(this, R.drawable.fail));
            tvResult.setText("Verification failed.\nCheck debug info below.");
        }

        if (imageUriString != null) {
            try {
                Bitmap b = BitmapFactory.decodeStream(getContentResolver().openInputStream(Uri.parse(imageUriString)));
                ivCapturedImage.setImageBitmap(b);
            } catch (FileNotFoundException e) { e.printStackTrace(); }
        }

        btn_home.setOnClickListener(v -> {
            startActivity(new Intent(this, HomeActivity.class));
            finishAffinity();
        });
    }
}
