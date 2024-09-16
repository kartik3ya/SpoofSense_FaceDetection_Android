package com.spoofsense.facedetection;

import android.os.Bundle;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.activity.EdgeToEdge;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

public class ResultActivity extends AppCompatActivity {

    String jsonResponse;
    boolean isReal;
    ImageView ivResult;
    TextView tvJsonResult;
    TextView tvResult;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ivResult = findViewById(R.id.ivResult);
        tvJsonResult = findViewById(R.id.tvJsonResult);
        tvResult = findViewById(R.id.tvResult);

        // Retrieve the String and boolean from the Intent
        jsonResponse = getIntent().getStringExtra("jsonResponse");
        isReal = getIntent().getBooleanExtra("IS_REAL", false);  // default to false if not found

        tvJsonResult.setText(isReal+ ":---" + jsonResponse);
        if (isReal){
            ivResult.setVisibility(View.VISIBLE);
            ivResult.setImageDrawable(getResources().getDrawable(R.drawable.success));
            tvResult.setText("Liveness confirmed!");
        }else{
            ivResult.setVisibility(View.VISIBLE);
            ivResult.setImageDrawable(getResources().getDrawable(R.drawable.fail));
            tvResult.setText("Please try again. Make sure the \nselfie is clicked in proper lighting!");
        }
    }
}