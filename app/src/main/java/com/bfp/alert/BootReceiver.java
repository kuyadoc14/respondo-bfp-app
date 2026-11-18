package com.bfp.alert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.util.Log;

public class BootReceiver extends BroadcastReceiver {
    private static final String TAG = "BootReceiver";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        String action = intent.getAction();
        if (Intent.ACTION_BOOT_COMPLETED.equals(action)
                || "android.intent.action.QUICKBOOT_POWERON".equals(action)
                || "com.htc.intent.action.QUICKBOOT_POWERON".equals(action)
                || Intent.ACTION_MY_PACKAGE_REPLACED.equals(action)) {
            Log.i(TAG, "Boot/Package updated detected: " + action);
            SharedPreferences prefs = context.getSharedPreferences(BLESOSService.PREFS, Context.MODE_PRIVATE);
            boolean shakeBg = prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false)
                    && prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false);
            boolean hasWearable = prefs.getString("ble_device_mac", null) != null;

            if (shakeBg || hasWearable) {
                Log.i(TAG, "Starting BLESOSService for background monitor (shake=" + shakeBg + ", wearable=" + hasWearable + ")");
                BLESOSService.start(context);
            }
        }
    }
}
