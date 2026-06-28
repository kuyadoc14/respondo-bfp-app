package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class FirstAidDetailActivity extends AppCompatActivity {

    private ExoPlayer exoPlayer;
    private String    storageVideoUrl;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_detail);

        String            title       = getIntent().getStringExtra("title");
        String            category    = getIntent().getStringExtra("category");
        String            description = getIntent().getStringExtra("description");
        String            videoUrl    = getIntent().getStringExtra("videoUrl");
        storageVideoUrl               = getIntent().getStringExtra("storageVideoUrl");
        String            iconEmoji   = getIntent().getStringExtra("iconEmoji");
        ArrayList<String> steps       = getIntent().getStringArrayListExtra("steps");
        ArrayList<String> photoUrls   = getIntent().getStringArrayListExtra("photoUrls");

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());
        ((TextView) findViewById(R.id.tvDetailIcon))
                .setText(iconEmoji != null ? iconEmoji : "🩺");
        ((TextView) findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView) findViewById(R.id.tvDetailCategory)).setText(category);
        ((TextView) findViewById(R.id.tvDetailDescription)).setText(description);

        // ── Offline notice ───────────────────────────────────────
        TextView tvOfflineNotice = findViewById(R.id.tvOfflineNotice);
        if (!NetworkUtils.isOnline(this)) {
            tvOfflineNotice.setVisibility(View.VISIBLE);
        }

        // ── Photos ───────────────────────────────────────────────
        if (photoUrls != null && !photoUrls.isEmpty()) {
            findViewById(R.id.tvPhotosLabel)
                    .setVisibility(View.VISIBLE);
            findViewById(R.id.photoScrollView)
                    .setVisibility(View.VISIBLE);
            LinearLayout container =
                    findViewById(R.id.photoContainer);
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

                // Glide automatically uses disk cache
                // when offline it serves from cache
                Glide.with(this)
                        .load(url)
                        .diskCacheStrategy(
                                com.bumptech.glide.load.engine
                                        .DiskCacheStrategy.ALL)
                        .placeholder(
                                android.R.drawable.ic_menu_gallery)
                        .error(android.R.drawable.ic_menu_gallery)
                        .into(img);

                img.setOnClickListener(v -> {
                    Intent intent = new Intent(this,
                            PhotoViewerActivity.class);
                    intent.putExtra("startIndex", idx);
                    intent.putStringArrayListExtra(
                            "allUrls", photoUrls);
                    startActivity(intent);
                });
                container.addView(img);
            }
        }

        // ── Steps ────────────────────────────────────────────────
        LinearLayout stepsContainer =
                findViewById(R.id.stepsContainer);
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
                                0,
                                LinearLayout.LayoutParams.WRAP_CONTENT,
                                1f);
                step.setLayoutParams(sp);

                row.addView(num);
                row.addView(step);
                stepsContainer.addView(row);
            }
        }

        // ── Video ────────────────────────────────────────────────
        if (storageVideoUrl != null && !storageVideoUrl.isEmpty()) {
            setupVideo();
        }

        // ── YouTube button ────────────────────────────────────────
        Button btnVideo = findViewById(R.id.btnWatchVideo);
        if (videoUrl != null && !videoUrl.isEmpty()) {
            btnVideo.setOnClickListener(v ->
                    startActivity(new Intent(
                            Intent.ACTION_VIEW, Uri.parse(videoUrl))));
        } else {
            btnVideo.setText("No YouTube video");
            btnVideo.setEnabled(false);
            btnVideo.setBackgroundTintList(
                    android.content.res.ColorStateList
                            .valueOf(0xFF444466));
        }
    }

    // ── Video setup — uses cache if available ─────────────────────
    private void setupVideo() {
        findViewById(R.id.tvInAppVideoLabel)
                .setVisibility(View.VISIBLE);

        PlayerView  playerView    = findViewById(R.id.exoPlayerView);
        TextView    tvVideoStatus = findViewById(R.id.tvVideoStatus);
        ProgressBar videoProgress =
                findViewById(R.id.videoDownloadProgress);

        playerView.setVisibility(View.VISIBLE);

        // Check cache — guard against null path
        if (VideoCache.isCached(this, storageVideoUrl)) {
            String localPath =
                    VideoCache.getCachedPath(this, storageVideoUrl);

            if (localPath != null && !localPath.isEmpty()) {
                // Play from local file
                playVideo(playerView,
                        Uri.fromFile(new java.io.File(localPath)));
                tvVideoStatus.setText("✓ Available offline");
                tvVideoStatus.setTextColor(0xFF34C759);
                tvVideoStatus.setVisibility(View.VISIBLE);
            } else {
                // getCachedPath returned null even though isCached
                // was true — fall back to streaming
                streamVideo(playerView, tvVideoStatus, videoProgress);
            }

        } else if (NetworkUtils.isOnline(this)) {
            streamVideo(playerView, tvVideoStatus, videoProgress);

        } else {
            // Offline and not cached
            playerView.setVisibility(View.GONE);
            tvVideoStatus.setText(
                    "⚠ Video not available offline. "
                            + "Connect to the internet to download it.");
            tvVideoStatus.setTextColor(0xFFFF3B30);
            tvVideoStatus.setVisibility(View.VISIBLE);
        }
    }

    private void streamVideo(PlayerView playerView,
                             TextView tvVideoStatus,
                             ProgressBar videoProgress) {
        if (storageVideoUrl == null
                || storageVideoUrl.isEmpty()) return;

        playVideo(playerView, Uri.parse(storageVideoUrl));
        tvVideoStatus.setText("⬇ Downloading for offline...");
        tvVideoStatus.setTextColor(0xFFFF9500);
        tvVideoStatus.setVisibility(View.VISIBLE);
        videoProgress.setVisibility(View.VISIBLE);

        VideoCache.downloadVideo(this, storageVideoUrl,
                new VideoCache.DownloadCallback() {
                    @Override
                    public void onProgress(int percent) {
                        runOnUiThread(() ->
                                videoProgress.setProgress(percent));
                    }

                    @Override
                    public void onComplete(String localPath) {
                        runOnUiThread(() -> {
                            videoProgress.setVisibility(View.GONE);
                            tvVideoStatus.setText(
                                    "✓ Available offline");
                            tvVideoStatus.setTextColor(0xFF34C759);
                        });
                    }

                    @Override
                    public void onError(String error) {
                        runOnUiThread(() -> {
                            videoProgress.setVisibility(View.GONE);
                            tvVideoStatus.setVisibility(View.GONE);
                        });
                    }
                });
    }

    private void playVideo(PlayerView playerView, Uri uri) {
        exoPlayer = new ExoPlayer.Builder(this).build();
        playerView.setPlayer(exoPlayer);
        playerView.setKeepScreenOn(true);
        exoPlayer.setMediaItem(MediaItem.fromUri(uri));
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        exoPlayer.prepare();
    }

    @Override protected void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override protected void onStop() {
        super.onStop();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override protected void onDestroy() {
        super.onDestroy();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private int dp(int val) {
        return (int)(val *
                getResources().getDisplayMetrics().density);
    }
}