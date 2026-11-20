package com.bfp.alert;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.util.Log;
import android.view.View;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity implements SensorEventListener {

    private static final int REQUEST_CODE_PERMISSIONS = 300;

    private final SosFragment       sosFragment       = new SosFragment();
    private final FirstAidFragment  firstAidFragment  = new FirstAidFragment();
    private final ProfileFragment   profileFragment   = new ProfileFragment();
    private SensorManager sensorManager;
    private ShakeGestureDetector shakeGestureDetector;

    private View splashOverlay;
    private TextView splashStatusText;
    private View splashProgress;
    private View btnGrantPermissions;
    private View btnContinueAnyway;
    private boolean isPermissionsFlowActive = false;
    private boolean isFragmentLoaded = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        splashOverlay = findViewById(R.id.splashOverlay);
        splashStatusText = findViewById(R.id.splashStatusText);
        splashProgress = findViewById(R.id.splashProgress);
        btnGrantPermissions = findViewById(R.id.btnGrantPermissions);
        btnContinueAnyway = findViewById(R.id.btnContinueAnyway);

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        SystemBarUtils.applyBottomInsetPadding(bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.sos) {
                loadFragment(sosFragment);
                return true;
            } else if (id == R.id.firstAid) {
                loadFragment(firstAidFragment);
                return true;
            } else if (id == R.id.profile) {
                loadFragment(profileFragment);
                return true;
            }
            return false;
        });

        View btnGlobalVoice = findViewById(R.id.btnGlobalVoice);
        if (btnGlobalVoice != null) {
            setupMovableChathead(btnGlobalVoice);
        }

        if (btnGrantPermissions != null) {
            btnGrantPermissions.setOnClickListener(v -> requestAppPermissions());
        }
        if (btnContinueAnyway != null) {
            btnContinueAnyway.setOnClickListener(v -> proceedToApp(false));
        }

        List<String> missing = getMissingPermissions();
        if (missing.isEmpty()) {
            proceedToApp(true);
        } else {
            holdOnStandbyForPermissions(missing);
        }
    }

    private List<String> getMissingPermissions() {
        List<String> list = new ArrayList<>();

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_FINE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_COARSE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.ACCESS_COARSE_LOCATION);
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            list.add(Manifest.permission.RECORD_AUDIO);
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.BLUETOOTH_SCAN);
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.BLUETOOTH_CONNECT);
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                list.add(Manifest.permission.POST_NOTIFICATIONS);
            }
        }

        return list;
    }

    private void holdOnStandbyForPermissions(List<String> missing) {
        isPermissionsFlowActive = true;
        setBottomNavVisibility(false);
        if (splashOverlay != null) {
            splashOverlay.setVisibility(View.VISIBLE);
            splashOverlay.setAlpha(1.0f);
        }
        if (splashProgress != null) splashProgress.setVisibility(View.VISIBLE);
        if (btnGrantPermissions != null) btnGrantPermissions.setVisibility(View.GONE);
        if (btnContinueAnyway != null) btnContinueAnyway.setVisibility(View.GONE);
        if (splashStatusText != null) {
            splashStatusText.setText("Waiting for permissions...");
        }

        new Handler(Looper.getMainLooper()).postDelayed(this::requestAppPermissions, 300);
    }

    private void requestAppPermissions() {
        List<String> missing = getMissingPermissions();
        if (!missing.isEmpty()) {
            if (splashProgress != null) splashProgress.setVisibility(View.VISIBLE);
            if (btnGrantPermissions != null) btnGrantPermissions.setVisibility(View.GONE);
            if (btnContinueAnyway != null) btnContinueAnyway.setVisibility(View.GONE);
            if (splashStatusText != null) {
                splashStatusText.setText("Please allow permissions to continue...");
            }
            requestPermissions(missing.toArray(new String[0]), REQUEST_CODE_PERMISSIONS);
        } else {
            proceedToApp(true);
        }
    }

    private void proceedToApp(boolean animated) {
        isPermissionsFlowActive = false;
        if (!isFragmentLoaded) {
            loadFragment(sosFragment);
            isFragmentLoaded = true;
        }
        setBottomNavVisibility(true);

        BLESOSService.start(this);
        refreshShakeDetection();

        if (splashOverlay != null && splashOverlay.getVisibility() == View.VISIBLE) {
            if (animated) {
                splashOverlay.animate()
                        .alpha(0f)
                        .setDuration(350)
                        .withEndAction(() -> splashOverlay.setVisibility(View.GONE))
                        .start();
            } else {
                splashOverlay.setVisibility(View.GONE);
            }
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            List<String> stillMissing = getMissingPermissions();
            if (stillMissing.isEmpty()) {
                if (splashStatusText != null) {
                    splashStatusText.setText("Ready!");
                }
                new Handler(Looper.getMainLooper()).postDelayed(() -> proceedToApp(true), 200);
            } else {
                if (splashProgress != null) splashProgress.setVisibility(View.GONE);
                if (splashStatusText != null) {
                    splashStatusText.setText("Location and Bluetooth permissions allow BFP responders to locate you in emergencies.");
                }
                if (btnGrantPermissions != null) btnGrantPermissions.setVisibility(View.VISIBLE);
                if (btnContinueAnyway != null) btnContinueAnyway.setVisibility(View.VISIBLE);
            }
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (!isPermissionsFlowActive) {
            BLESOSService.start(this);
            refreshShakeDetection();
        } else {
            if (getMissingPermissions().isEmpty()) {
                proceedToApp(true);
            }
        }
    }

    @Override
    protected void onPause() {
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
        shakeGestureDetector = null;
        super.onPause();
    }

    public void refreshShakeDetection() {
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        shakeGestureDetector = null;

        android.content.SharedPreferences prefs =
                getSharedPreferences("bfp_prefs", MODE_PRIVATE);
        if (!prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false)
                || prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false)) {
            return;
        }

        Sensor sensor = ShakeGestureDetector.getSensor(sensorManager);
        if (sensor == null) return;

        shakeGestureDetector = new ShakeGestureDetector(
                prefs.getFloat(ShakeGestureDetector.PREF_THRESHOLD, ShakeGestureDetector.DEFAULT_THRESHOLD));
        if (!sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
            shakeGestureDetector = null;
            Log.e("MainActivity", "Unable to register the foreground shake SOS sensor.");
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (shakeGestureDetector != null
                && shakeGestureDetector.onSensorChanged(event.values, event.timestamp, event.sensor.getType())) {
            BLESOSService.triggerShakeSOS(this);
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private void setBottomNavVisibility(boolean visible) {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
        View btnGlobalVoice = findViewById(R.id.btnGlobalVoice);
        if (btnGlobalVoice != null) {
            btnGlobalVoice.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void loadFragment(Fragment fragment) {
        setBottomNavVisibility(true);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public void openAdminLogin() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminLoginFragment())
                .addToBackStack("admin_login")
                .commit();
    }

    public void openAdminDashboard() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminDashboardFragment())
                .commit();
    }

    public void openAdminFirstAid() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminFirstAidFragment())
                .addToBackStack("admin_first_aid")
                .commit();
    }

    public void returnToMain() {
        setBottomNavVisibility(true);
        getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        loadFragment(sosFragment);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.sos);
    }

    public void switchToFirstAid() {
        switchToFirstAid("");
    }

    public void switchToFirstAid(String query) {
        BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.firstAid);
        }
        loadFragment(firstAidFragment);
        if (query != null && !query.isEmpty()) {
            firstAidFragment.setSearchQuery(query);
        }
    }

    public void openFirstAidDetail(FirstAidItem item) {
        openFirstAidDetail(item, false);
    }

    public void openFirstAidDetail(FirstAidItem item, boolean autoPlayVideo) {
        if (item == null) return;
        setBottomNavVisibility(true);
        FirstAidDetailFragment detailFragment =
                FirstAidDetailFragment.newInstance(
                        item.title != null ? item.title : "",
                        item.category != null ? item.category : "",
                        item.description != null ? item.description : "",
                        item.videoUrl != null ? item.videoUrl : "",
                        item.storageVideoUrl != null ? item.storageVideoUrl : "",
                        item.iconEmoji != null ? item.iconEmoji : "🩺",
                        new java.util.ArrayList<>(item.steps != null ? item.steps : new java.util.ArrayList<>()),
                        new java.util.ArrayList<>(item.photoUrls != null ? item.photoUrls : new java.util.ArrayList<>()),
                        autoPlayVideo);

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack("first_aid_detail")
                .commitAllowingStateLoss();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            android.view.MenuItem itemToSelect = bottomNav.getMenu().findItem(R.id.firstAid);
            if (itemToSelect != null) {
                itemToSelect.setChecked(true);
            }
        }
    }

    public void playCurrentVideo() {
        androidx.fragment.app.Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current instanceof FirstAidDetailFragment) {
            ((FirstAidDetailFragment) current).playVideo();
        }
    }

    public void pauseCurrentVideo() {
        androidx.fragment.app.Fragment current = getSupportFragmentManager().findFragmentById(R.id.fragmentContainer);
        if (current instanceof FirstAidDetailFragment) {
            ((FirstAidDetailFragment) current).pauseVideo();
        }
    }

    private VoiceAssistantDialog voiceAssistantDialog;

    public boolean isHandsFreeEnabled() {
        return getSharedPreferences("bfp_prefs", MODE_PRIVATE)
                .getBoolean("handsFreeVoiceEnabled", false);
    }

    public void showVoiceAssistant() {
        showVoiceAssistant(isHandsFreeEnabled());
    }

    public void showVoiceAssistant(boolean handsFree) {
        if (voiceAssistantDialog != null && voiceAssistantDialog.isShowing()) {
            return;
        }

        voiceAssistantDialog = new VoiceAssistantDialog(
                this,
                new VoiceAssistantDialog.ActionListener() {
                    @Override
                    public void onSendSOS() {
                        triggerSOSFromVoice("victim", null);
                    }

                    @Override
                    public void onSendSOSWithRole(String role) {
                        triggerSOSFromVoice(role, null);
                    }

                    @Override
                    public void onSendSOSWithDetails(String incidentNotes, String role) {
                        triggerSOSFromVoice(role, incidentNotes);
                    }

                    @Override
                    public void onOpenFirstAid() {
                        switchToFirstAid();
                    }

                    @Override
                    public void onSearchFirstAid(String query) {
                        switchToFirstAid(query);
                    }

                    @Override
                    public void onOpenFirstAidDetail(FirstAidItem item) {
                        openFirstAidDetail(item, false);
                    }

                    @Override
                    public void onOpenFirstAidDetail(FirstAidItem item, boolean autoPlayVideo) {
                        openFirstAidDetail(item, autoPlayVideo);
                    }

                    @Override
                    public void onPlayVideo() {
                        playCurrentVideo();
                    }

                    @Override
                    public void onPauseVideo() {
                        pauseCurrentVideo();
                    }

                    @Override
                    public void onOpenProfile() {
                        switchToProfile();
                    }

                    @Override
                    public void onOpenSOS() {
                        switchToSOS();
                    }

                    @Override
                    public void onCheckStatus() {
                        if (sosFragment != null) {
                            sosFragment.checkCurrentAlertStatus();
                        }
                    }

                    @Override
                    public void onCancelSOS() {
                        if (sosFragment != null) {
                            sosFragment.cancelActiveSOS();
                        }
                    }

                    @Override
                    public void onUpdateCondition(String conditionNotes) {
                        if (sosFragment != null) {
                            sosFragment.updateAlertCondition(conditionNotes);
                        }
                    }
                },
                handsFree);

        VoiceAssistantDialog shown = voiceAssistantDialog;
        shown.setOnDismissListener(d -> {
            if (voiceAssistantDialog == shown) {
                voiceAssistantDialog = null;
            }
        });
        shown.show();
    }

    public void dismissVoiceAssistant() {
        if (voiceAssistantDialog != null) {
            try {
                voiceAssistantDialog.dismiss();
            } catch (Exception ignored) {}
            voiceAssistantDialog = null;
        }
    }

    public void triggerSOSFromVoice(String role, String incidentNotes) {
        switchToSOS();
        if (sosFragment != null) {
            sosFragment.sendSOSAlert(role, incidentNotes);
        }
    }

    public void switchToProfile() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.profile);
        }
        loadFragment(profileFragment);
    }

    public void switchToSOS() {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.sos);
        }
        loadFragment(sosFragment);
    }

    private void setupMovableChathead(View bubble) {
        if (bubble == null) return;
        bubble.setOnTouchListener(new View.OnTouchListener() {
            private float initialX, initialY;
            private float initialTouchX, initialTouchY;
            private boolean isDragging = false;
            private final int touchSlop = (int) (10 * getResources().getDisplayMetrics().density);

            @Override
            public boolean onTouch(View v, android.view.MotionEvent event) {
                switch (event.getAction()) {
                    case android.view.MotionEvent.ACTION_DOWN:
                        initialX = v.getX();
                        initialY = v.getY();
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        isDragging = false;
                        v.animate().scaleX(1.1f).scaleY(1.1f).setDuration(100).start();
                        return true;

                    case android.view.MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - initialTouchX;
                        float dy = event.getRawY() - initialTouchY;
                        if (!isDragging && (Math.abs(dx) > touchSlop || Math.abs(dy) > touchSlop)) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            View parent = (View) v.getParent();
                            if (parent != null) {
                                float newX = initialX + dx;
                                float newY = initialY + dy;
                                float minX = 0;
                                float maxX = parent.getWidth() - v.getWidth();
                                float minY = 0;
                                View bottomNav = findViewById(R.id.bottomNav);
                                float maxY = parent.getHeight() - v.getHeight() - (bottomNav != null ? bottomNav.getHeight() : 0);

                                v.setX(Math.max(minX, Math.min(newX, maxX)));
                                v.setY(Math.max(minY, Math.min(newY, maxY)));
                            }
                        }
                        return true;

                    case android.view.MotionEvent.ACTION_UP:
                    case android.view.MotionEvent.ACTION_CANCEL:
                        v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(100).start();
                        if (!isDragging) {
                            v.performClick();
                            showVoiceAssistant();
                        } else {
                            // Snap smoothly to nearest screen edge (left or right)
                            View parent = (View) v.getParent();
                            if (parent != null) {
                                float parentWidth = parent.getWidth();
                                float margin = 16 * getResources().getDisplayMetrics().density;
                                float targetX = (v.getX() + v.getWidth() / 2f < parentWidth / 2f)
                                        ? margin
                                        : (parentWidth - v.getWidth() - margin);

                                v.animate()
                                        .x(targetX)
                                        .setDuration(280)
                                        .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f))
                                        .start();
                            }
                        }
                        return true;
                }
                return false;
            }
        });
    }
}
