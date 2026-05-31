package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.MediaController;
import android.widget.TextView;
import android.widget.VideoView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class FirstAidDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_detail);

        String            title           = getIntent().getStringExtra("title");
        String            category        = getIntent().getStringExtra("category");
        String            description     = getIntent().getStringExtra("description");
        String            videoUrl        = getIntent().getStringExtra("videoUrl");
        String            storageVideoUrl = getIntent().getStringExtra("storageVideoUrl");
        String            iconEmoji       = getIntent().getStringExtra("iconEmoji");
        ArrayList<String> steps           = getIntent().getStringArrayListExtra("steps");
        ArrayList<String> photoUrls       = getIntent().getStringArrayListExtra("photoUrls");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tvDetailIcon))
                .setText(iconEmoji != null ? iconEmoji : "🩺");
        ((TextView) findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView) findViewById(R.id.tvDetailCategory)).setText(category);
        ((TextView) findViewById(R.id.tvDetailDescription)).setText(description);

        // Photos
        if (photoUrls != null && !photoUrls.isEmpty()) {
            findViewById(R.id.tvPhotosLabel).setVisibility(View.VISIBLE);
            findViewById(R.id.photoScrollView).setVisibility(View.VISIBLE);
            LinearLayout container = findViewById(R.id.photoContainer);
            int size   = dp(170);
            int margin = dp(10);

            for (int i = 0; i < photoUrls.size(); i++) {
                final String url = photoUrls.get(i);
                final int    idx = i;
                ImageView img = new ImageView(this);
                LinearLayout.LayoutParams lp =
                        new LinearLayout.LayoutParams(size, size);
                lp.setMargins(0, 0, margin, 0);
                img.setLayoutParams(lp);
                img.setScaleType(ImageView.ScaleType.CENTER_CROP);
                Glide.with(this).load(url)
                        .placeholder(android.R.drawable.ic_menu_gallery)
                        .into(img);
                img.setOnClickListener(v -> {
                    Intent intent = new Intent(this,
                            PhotoViewerActivity.class);
                    intent.putExtra("startIndex", idx);
                    intent.putStringArrayListExtra("allUrls", photoUrls);
                    startActivity(intent);
                });
                container.addView(img);
            }
        }

        // Steps
        LinearLayout stepsContainer = findViewById(R.id.stepsContainer);
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                LinearLayout row = new LinearLayout(this);
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setPadding(0, 0, 0, dp(14));

                TextView num = new TextView(this);
                num.setText(String.valueOf(i + 1));
                num.setTextColor(0xFFFFFFFF);
                num.setTextSize(12);
                num.setGravity(Gravity.CENTER);
                num.setBackgroundColor(0xFFe63946);
                LinearLayout.LayoutParams np =
                        new LinearLayout.LayoutParams(dp(32), dp(32));
                np.setMargins(0, 0, dp(12), 0);
                num.setLayoutParams(np);

                TextView step = new TextView(this);
                step.setText(steps.get(i));
                step.setTextColor(0xFF000000);
                step.setTextSize(14);
                step.setLineSpacing(4, 1);
                LinearLayout.LayoutParams sp =
                        new LinearLayout.LayoutParams(
                                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
                step.setLayoutParams(sp);

                row.addView(num);
                row.addView(step);
                stepsContainer.addView(row);
            }
        }

        // In-app video
        if (storageVideoUrl != null && !storageVideoUrl.isEmpty()) {
            findViewById(R.id.tvInAppVideoLabel).setVisibility(View.VISIBLE);
            VideoView videoView = findViewById(R.id.inAppVideoView);
            videoView.setVisibility(View.VISIBLE);
            MediaController mc = new MediaController(this);
            mc.setAnchorView(videoView);
            videoView.setMediaController(mc);
            videoView.setVideoURI(Uri.parse(storageVideoUrl));
            videoView.requestFocus();
        }

        // YouTube
        Button btnVideo = findViewById(R.id.btnWatchVideo);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            btnVideo.setOnClickListener(v ->
                    startActivity(new Intent(
                            Intent.ACTION_VIEW, Uri.parse(videoUrl))));
        } else {
            btnVideo.setText("No YouTube video");
            btnVideo.setEnabled(false);
            btnVideo.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(0xFF444466));
        }
    }

    private int dp(int val) {
        return (int)(val *
                getResources().getDisplayMetrics().density);
    }
}