package com.spoofsense.facedetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.TextView;
import androidx.appcompat.app.AppCompatActivity;


public class ResultActivity extends AppCompatActivity {

    String jsonResponse;
    boolean isReal;
    ImageView ivResult;
    TextView tvJsonResult;
    TextView tvResult;
    Button btn_home;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_result);

        ivResult = findViewById(R.id.ivResult);
        tvJsonResult = findViewById(R.id.tvJsonResult);
        tvResult = findViewById(R.id.tvResult);
        btn_home = findViewById(R.id.btn_home);

        // Retrieve the String and boolean from the Intent and display json data
        jsonResponse = getIntent().getStringExtra("jsonResponse");
        isReal = getIntent().getBooleanExtra("IS_REAL", false);  // default to false if not found

        tvJsonResult.setText(isReal+ ":---" + jsonResponse);
        if (isReal){
            ivResult.setVisibility(View.VISIBLE);
            ivResult.setImageDrawable(getResources().getDrawable(R.drawable.success));
            tvResult.setText("Liveness confirmed");
        }else{
            ivResult.setVisibility(View.VISIBLE);
            ivResult.setImageDrawable(getResources().getDrawable(R.drawable.fail));
            tvResult.setText("Please try again. Ensure that \n" +
                    "the selfie is captured with\n" +
                    " sufficient light.");
        }

        btn_home.setOnClickListener(this::onHomeButtonClick);

    }

    // Button click method with Intent
    private void onHomeButtonClick(View view) {
        // Use an explicit intent to start a new activity
        Intent intent = new Intent(ResultActivity.this, HomeActivity.class);
        startActivity(intent);
        finishAffinity();
    }
}