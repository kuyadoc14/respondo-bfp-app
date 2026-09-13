package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.media3.common.MediaItem;
import androidx.media3.common.Player;
import androidx.media3.exoplayer.ExoPlayer;
import androidx.media3.ui.PlayerView;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class FirstAidDetailFragment extends Fragment {

    private ExoPlayer exoPlayer;
    private String    storageVideoUrl;

    public static FirstAidDetailFragment newInstance(
            String title, String category, String description,
            String videoUrl, String storageVideoUrl,
            String iconEmoji,
            ArrayList<String> steps, ArrayList<String> photoUrls) {

        FirstAidDetailFragment fragment = new FirstAidDetailFragment();
        Bundle args = new Bundle();
        args.putString("title",           title);
        args.putString("category",        category);
        args.putString("description",     description);
        args.putString("videoUrl",        videoUrl);
        args.putString("storageVideoUrl", storageVideoUrl);
        args.putString("iconEmoji",       iconEmoji);
        args.putStringArrayList("steps",     steps);
        args.putStringArrayList("photoUrls", photoUrls);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_first_aid_detail, container, false);

        Bundle args = getArguments();
        if (args == null) return view;

        String            title       = args.getString("title");
        String            category    = args.getString("category");
        String            description = args.getString("description");
        String            videoUrl    = args.getString("videoUrl");
        storageVideoUrl               = args.getString("storageVideoUrl");
        String            iconEmoji   = args.getString("iconEmoji");
        ArrayList<String> steps       = args.getStringArrayList("steps");
        ArrayList<String> photoUrls   = args.getStringArrayList("photoUrls");

        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher().onBackPressed());

        ((TextView) view.findViewById(R.id.tvDetailIcon))
                .setText(iconEmoji != null ? iconEmoji : "🩺");
        ((TextView) view.findViewById(R.id.tvDetailTitle)).setText(title);
        ((TextView) view.findViewById(R.id.tvDetailCategory)).setText(category);
        ((TextView) view.findViewById(R.id.tvDetailDescription)).setText(description);

        // ── Offline notice ───────────────────────────────────────
        TextView tvOfflineNotice = view.findViewById(R.id.tvOfflineNotice);
        if (!NetworkUtils.isOnline(requireContext())) {
            tvOfflineNotice.setVisibility(View.VISIBLE);
        }

        // ── Photos ───────────────────────────────────────────────
        if (photoUrls != null && !photoUrls.isEmpty()) {
            view.findViewById(R.id.tvPhotosLabel)
                    .setVisibility(View.VISIBLE);
            view.findViewById(R.id.photoScrollView)
                    .setVisibility(View.VISIBLE);
            LinearLayout photoContainer =
                    view.findViewById(R.id.photoContainer);
            int size   = dp(170);
            int margin = dp(10);

            for (int i = 0; i < photoUrls.size(); i++) {
                final String url = photoUrls.get(i);
                final int    idx = i;
                ImageView img = new ImageView(requireContext());
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
                    PhotoViewerFragment photoFragment =
                            PhotoViewerFragment.newInstance(
                                    idx, photoUrls);
                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer,
                                    photoFragment)
                            .addToBackStack(null)
                            .commit();
                });
                photoContainer.addView(img);
            }
        }

        // ── Steps ────────────────────────────────────────────────
        LinearLayout stepsContainer =
                view.findViewById(R.id.stepsContainer);
        if (steps != null) {
            for (int i = 0; i < steps.size(); i++) {
                LinearLayout row = new LinearLayout(requireContext());
                row.setOrientation(LinearLayout.HORIZONTAL);
                row.setGravity(Gravity.CENTER_VERTICAL);
                row.setBackgroundResource(R.drawable.card_bg);
                row.setPadding(dp(12), dp(12), dp(12), dp(12));
                LinearLayout.LayoutParams rowLp =
                        new LinearLayout.LayoutParams(
                                ViewGroup.LayoutParams.MATCH_PARENT,
                                ViewGroup.LayoutParams.WRAP_CONTENT);
                rowLp.setMargins(0, 0, 0, dp(10));
                row.setLayoutParams(rowLp);

                TextView num = new TextView(requireContext());
                num.setText(String.valueOf(i + 1));
                num.setTextColor(0xFFFFFFFF);
                num.setTextSize(12);
                num.setGravity(Gravity.CENTER);
                num.setBackgroundResource(R.drawable.bg_step_number);
                LinearLayout.LayoutParams np =
                        new LinearLayout.LayoutParams(dp(28), dp(28));
                np.setMargins(0, 0, dp(12), 0);
                num.setLayoutParams(np);

                TextView step = new TextView(requireContext());
                step.setText(steps.get(i));
                step.setTextColor(0xFF1A1A2E);
                step.setTextSize(14);
                step.setLineSpacing(4, 1);
                step.setLayoutParams(new LinearLayout.LayoutParams(
                        0,
                        LinearLayout.LayoutParams.WRAP_CONTENT,
                        1f));

                row.addView(num);
                row.addView(step);
                stepsContainer.addView(row);
            }
        }

        // ── Video ────────────────────────────────────────────────
        if (storageVideoUrl != null && !storageVideoUrl.isEmpty()) {
            setupVideo(view);
        }

        // ── YouTube button ────────────────────────────────────────
        Button btnVideo = view.findViewById(R.id.btnWatchVideo);
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

        return view;
    }

    // ── Video setup — uses cache if available ─────────────────────
    private void setupVideo(View view) {
        view.findViewById(R.id.tvInAppVideoLabel)
                .setVisibility(View.VISIBLE);

        PlayerView  playerView    = view.findViewById(R.id.exoPlayerView);
        TextView    tvVideoStatus = view.findViewById(R.id.tvVideoStatus);
        ProgressBar videoProgress =
                view.findViewById(R.id.videoDownloadProgress);

        playerView.setVisibility(View.VISIBLE);

        // Check cache — guard against null path
        if (VideoCache.isCached(requireContext(), storageVideoUrl)) {
            String localPath =
                    VideoCache.getCachedPath(requireContext(), storageVideoUrl);

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

        } else if (NetworkUtils.isOnline(requireContext())) {
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

        VideoCache.downloadVideo(requireContext(), storageVideoUrl,
                new VideoCache.DownloadCallback() {
                    @Override
                    public void onProgress(int percent) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() ->
                                    videoProgress.setProgress(percent));
                        }
                    }

                    @Override
                    public void onComplete(String localPath) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                videoProgress.setVisibility(View.GONE);
                                tvVideoStatus.setText(
                                        "✓ Available offline");
                                tvVideoStatus.setTextColor(0xFF34C759);
                            });
                        }
                    }

                    @Override
                    public void onError(String error) {
                        if (getActivity() != null) {
                            getActivity().runOnUiThread(() -> {
                                videoProgress.setVisibility(View.GONE);
                                tvVideoStatus.setVisibility(View.GONE);
                            });
                        }
                    }
                });
    }

    private void playVideo(PlayerView playerView, Uri uri) {
        exoPlayer = new ExoPlayer.Builder(requireContext()).build();
        playerView.setPlayer(exoPlayer);
        playerView.setKeepScreenOn(true);
        exoPlayer.setMediaItem(MediaItem.fromUri(uri));
        exoPlayer.setRepeatMode(Player.REPEAT_MODE_OFF);
        exoPlayer.prepare();
    }

    @Override
    public void onPause() {
        super.onPause();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (exoPlayer != null) exoPlayer.pause();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (exoPlayer != null) {
            exoPlayer.release();
            exoPlayer = null;
        }
    }

    private int dp(int val) {
        return (int)(val *
                requireContext().getResources()
                        .getDisplayMetrics().density);
    }
}
