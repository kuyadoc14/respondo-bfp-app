package com.bfp.alert;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class BFPApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Cloudinary with unsigned configuration (NEVER expose api_secret in client APK)
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("cloud_name", ApiSecurityConfig.getCloudinaryCloudName());
        com.cloudinary.android.MediaManager.init(this, config);

        // Ensure background SOS monitor is running if background shake or wearable is enabled
        android.content.SharedPreferences prefs = getSharedPreferences(BLESOSService.PREFS, MODE_PRIVATE);
        boolean shakeBg = prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false)
                && prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false);
        boolean hasWearable = prefs.getString("ble_device_mac", null) != null;
        if (shakeBg || hasWearable) {
            BLESOSService.start(this);
        }
    }
}