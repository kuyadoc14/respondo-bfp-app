package com.bfp.alert;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class BFPApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Cloudinary: supports unsigned preset or signed credentials via local.properties
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("cloud_name", ApiSecurityConfig.getCloudinaryCloudName());
        String cKey = ApiSecurityConfig.getCloudinaryApiKey();
        String cSecret = ApiSecurityConfig.getCloudinaryApiSecret();
        if (!cKey.isEmpty() && !cSecret.isEmpty()) {
            config.put("api_key", cKey);
            config.put("api_secret", cSecret);
        }
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