package com.bfp.alert;

import android.app.Application;

import com.cloudinary.android.MediaManager;

import java.util.HashMap;
import java.util.Map;

public class BFPApplication extends Application {

    @Override
    public void onCreate() {
        super.onCreate();

        // Initialize Cloudinary
        java.util.Map<String, Object> config = new java.util.HashMap<>();
        config.put("cloud_name", "ds8c4o8q4");
        config.put("api_key",    "986862986147198");
        config.put("api_secret", "Xx2WgEGLTWMfGk_BlxIl71QKL5A");
        com.cloudinary.android.MediaManager.init(this, config);
    }
}