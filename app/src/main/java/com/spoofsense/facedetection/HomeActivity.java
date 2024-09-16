package com.spoofsense.facedetection;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;


public class HomeActivity extends AppCompatActivity {

    AppCompatButton btn_check_liveness;
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btn_check_liveness = findViewById(R.id.btn_check_liveness);
        btn_check_liveness.setOnClickListener(this::onCheckLivenessButtonClick);

    }

    // Button click method with Intent
    private void onCheckLivenessButtonClick(View view) {
        // Use an explicit intent to start a new activity
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        startActivity(intent);
    }
}