package com.spoofsense.facedetection;

import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.text.Html;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatButton;


public class HomeActivity extends AppCompatActivity {

    AppCompatButton btn_check_liveness;
    TextView tv_app_title, tv_app_subtitle;

    String text = "<font color='#0E68C0'>Spoof</font><font color='#222222'>Sense</font>";
    String versionName = "";

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_home);

        btn_check_liveness = findViewById(R.id.btn_check_liveness);
        tv_app_title = findViewById(R.id.tv_app_title);
        tv_app_subtitle = findViewById(R.id.tv_app_subtitle);

        try {
            versionName = getApplicationContext().getPackageManager().getPackageInfo(getApplicationContext().getPackageName(), 0).versionName;
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }

        tv_app_title.setText(Html.fromHtml(text, Html.FROM_HTML_MODE_LEGACY), TextView.BufferType.SPANNABLE);
        tv_app_subtitle.setText("face v" + versionName);

        btn_check_liveness.setOnClickListener(this::onCheckLivenessButtonClick);




    }

    // Button click method with Intent
    private void onCheckLivenessButtonClick(View view) {
        // Use an explicit intent to start a new activity
        Intent intent = new Intent(HomeActivity.this, MainActivity.class);
        startActivity(intent);
    }
}