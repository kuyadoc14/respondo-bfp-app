package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Button;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;

public class FirstAidDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_detail);

        String title       = getIntent().getStringExtra("title");
        String category    = getIntent().getStringExtra("category");
        String description = getIntent().getStringExtra("description");
        String videoUrl    = getIntent().getStringExtra("videoUrl");
        String iconEmoji   = getIntent().getStringExtra("iconEmoji");
        ArrayList<String> steps =
                getIntent().getStringArrayListExtra("steps");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        ((TextView) findViewById(R.id.tvDetailIcon)).setText(
                iconEmoji != null ? iconEmoji : "🩺");
        ((TextView) findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView) findViewById(R.id.tvDetailCategory)).setText(category);
        ((TextView) findViewById(R.id.tvDetailDescription)).setText(description);

        // Build steps dynamically
        LinearLayout stepsContainer = findViewById(R.id.stepsContainer);
        stepsContainer.removeAllViews();
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, 12);

                // Step number circle
                TextView numView = new TextView(this);
                numView.setText(String.valueOf(i + 1));
                numView.setTextColor(0xFFFFFFFF);
                numView.setTextSize(12);
                numView.setGravity(Gravity.CENTER);
                numView.setBackground(getDrawable(android.R.drawable.dialog_holo_dark_frame));
                LinearLayout.LayoutParams numParams =
                        new LinearLayout.LayoutParams(40, 40);
                numParams.setMargins(0, 0, 12, 0);
                numView.setLayoutParams(numParams);
                numView.setBackgroundColor(0xFFe63946);

                // Step text
                TextView stepView = new TextView(this);
                stepView.setText(steps.get(i));
                stepView.setTextColor(0xFFCCCCCC);
                stepView.setTextSize(14);
                stepView.setLineSpacing(4, 1);
                LinearLayout.LayoutParams stepParams =
                        new LinearLayout.LayoutParams(0,
                                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                stepView.setLayoutParams(stepParams);

                row.addView(numView);
                row.addView(stepView);
                stepsContainer.addView(row);
            }
        }

        // Video button
        Button btnVideo = findViewById(R.id.btnWatchVideo);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            btnVideo.setOnClickListener(v -> {
                Intent browserIntent = new Intent(
                        Intent.ACTION_VIEW, Uri.parse(videoUrl));
                startActivity(browserIntent);
            });
        } else {
            btnVideo.setText("No video available");
            btnVideo.setEnabled(false);
            btnVideo.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF444466));
        }
    }
}