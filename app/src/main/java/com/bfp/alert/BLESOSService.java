package com.bfp.alert;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.content.pm.ServiceInfo;
import android.media.AudioAttributes;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Build;
import android.os.IBinder;
import android.util.Log;

import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import android.speech.tts.TextToSpeech;
import java.util.Locale;
import java.util.List;
import java.util.ArrayList;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class BLESOSService extends Service {

    private static final String TAG = "BLESOSService";
    public static final String CHANNEL_ID_MONITOR = "esp32_ble_monitor";
    public static final String CHANNEL_ID_ALERTS  = "esp32_sos_alert";
    private static final int NOTIFICATION_ID_FOREGROUND = 1001;
    private static final int NOTIFICATION_ID_SOS = 1002;

    public static final String ACTION_BLE_STATE_CHANGED    = "com.bfp.alert.BLE_STATE_CHANGED";
    public static final String ACTION_SOS_SENT             = "com.bfp.alert.SOS_SENT";
    public static final String ACTION_BLE_ROLE_CONFIRMED   = "com.bfp.alert.BLE_ROLE_CONFIRMED";
    public static final String EXTRA_CONNECTED             = "extra_connected";
    public static final String EXTRA_ALERT_ID              = "extra_alert_id";
    public static final String EXTRA_ROLE                  = "extra_role";

    private static final String PREFS     = "bfp_prefs";
    private static final String KEY_ALERT = "activeAlertId";
    private static final String KEY_USER  = "userId";

    private static BLESOSService sInstance = null;
    private BLEManager bleManager;
    private FusedLocationProviderClient locationClient;
    private FirebaseFirestore db;
    private boolean isConnected = false;
    private String lastDeviceCoordinates = null;
    private long lastTriggerTime = 0;
    private TextToSpeech tts = null;

    public static boolean isDeviceConnected() {
        return sInstance != null && sInstance.isConnected;
    }

    public static void start(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, BLESOSService.class);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to start BLESOSService: " + e.getMessage());
        }
    }

    public static void stop(Context context) {
        if (context == null) return;
        try {
            Intent intent = new Intent(context, BLESOSService.class);
            context.stopService(intent);
        } catch (Exception ignored) {}
    }

    @Override
    public void onCreate() {
        super.onCreate();
        sInstance = this;
        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        createNotificationChannels();
        startForegroundServiceNotification("Searching for Respondo Wearable...");

        initTTS();
        initBLE();
    }

    private void createNotificationChannels() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager nm = getSystemService(NotificationManager.class);
            if (nm == null) return;

            // Low priority channel for ongoing BLE connection
            NotificationChannel monitorChan = new NotificationChannel(
                    CHANNEL_ID_MONITOR,
                    "ESP32 Connection Monitor",
                    NotificationManager.IMPORTANCE_LOW
            );
            monitorChan.setDescription("Maintains direct Bluetooth connection to Respondo Wearable");
            nm.createNotificationChannel(monitorChan);

            // High priority emergency alert channel with sound & vibration
            NotificationChannel alertChan = new NotificationChannel(
                    CHANNEL_ID_ALERTS,
                    "Emergency SOS Alerts",
                    NotificationManager.IMPORTANCE_HIGH
            );
            alertChan.setDescription("Alert notifications when Respondo Wearable is triggered");
            alertChan.enableVibration(true);
            Uri alarmUri = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
            AudioAttributes audioAttributes = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            alertChan.setSound(alarmUri, audioAttributes);
            nm.createNotificationChannel(alertChan);
        }
    }

    private void startForegroundServiceNotification(String statusText) {
        Notification notification = buildForegroundNotification(statusText);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            int serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                // Android 14+ support both connected device and location
                serviceType = ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE | ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION;
            }
            try {
                startForeground(NOTIFICATION_ID_FOREGROUND, notification, serviceType);
            } catch (Exception e) {
                Log.w(TAG, "Fallback to standard startForeground: " + e.getMessage());
                startForeground(NOTIFICATION_ID_FOREGROUND, notification);
            }
        } else {
            startForeground(NOTIFICATION_ID_FOREGROUND, notification);
        }
    }

    private Notification buildForegroundNotification(String statusText) {
        Intent openAppIntent = new Intent(this, MainActivity.class);
        openAppIntent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        PendingIntent pendingIntent = PendingIntent.getActivity(
                this, 0, openAppIntent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        return new NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
                .setContentTitle("Respondo Wearable Monitor")
                .setContentText(statusText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build();
    }

    private void updateForegroundNotification(String statusText) {
        Notification notification = buildForegroundNotification(statusText);
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_FOREGROUND, notification);
        }
    }

    private void initTTS() {
        try {
            tts = new TextToSpeech(this, status -> {
                if (status == TextToSpeech.SUCCESS && tts != null) {
                    tts.setLanguage(Locale.US);
                }
            });
        } catch (Exception e) {
            Log.w(TAG, "TTS initialization error: " + e.getMessage());
        }
    }

    private void speakText(String textToSpeak) {
        if (tts != null && textToSpeak != null && !textToSpeak.isEmpty()) {
            tts.speak(textToSpeak, TextToSpeech.QUEUE_FLUSH, null, "BFP_BLE_" + System.currentTimeMillis());
        }
    }

    private void handleRoleConfirmation(String role) {
        String alertId = getSavedAlertId();
        Log.i(TAG, "Wearable confirmed role: " + role + " for alertId: " + alertId);

        if (alertId != null && !alertId.isEmpty()) {
            if ("victim".equalsIgnoreCase(role)) {
                Map<String, Object> update = new HashMap<>();
                update.put("reporterRole", "victim");
                update.put("countOfVictims", 1);
                update.put("reportSubmitted", true);

                try {
                    UserProfile profile = UserProfileManager.getLocalProfile(this);
                    if (profile != null && !profile.isEmpty()) {
                        if (!profile.getFullName().isEmpty()) update.put("reporterName", profile.getFullName());
                        if (!profile.getPhone().isEmpty()) update.put("reporterPhone", profile.getPhone());
                        update.put("reporterProfile", profile.toMap());

                        Map<String, Object> patientMap = profile.toPatientMap();
                        List<Map<String, Object>> patients = new ArrayList<>();
                        patients.add(patientMap);
                        update.put("patients", patients);
                    }
                } catch (Exception err) {
                    Log.e(TAG, "Error attaching profile to victim alert: " + err.getMessage());
                }

                db.collection("sos_alerts").document(alertId).update(update);
                updateForegroundNotification("Respondo Wearable: Confirmed as VICTIM");
                speakText("Victim status confirmed. Your medical profile and emergency contacts have been sent to BFP.");
            } else if ("bystander".equalsIgnoreCase(role)) {
                Map<String, Object> update = new HashMap<>();
                update.put("reporterRole", "bystander");
                db.collection("sos_alerts").document(alertId).update(update);
                updateForegroundNotification("Respondo Wearable: Confirmed as BYSTANDER");
                speakText("Bystander status confirmed. Responders have been alerted.");
            }
        }

        Intent intent = new Intent(ACTION_BLE_ROLE_CONFIRMED);
        intent.putExtra(EXTRA_ROLE, role);
        intent.putExtra(EXTRA_ALERT_ID, alertId);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private void initBLE() {
        bleManager = new BLEManager(this, new BLEManager.BLEListener() {
            @Override
            public void onSOSReceived(String deviceInfo) {
                Log.d(TAG, "🚨 onSOSReceived from ESP32: " + deviceInfo);
                if (deviceInfo != null && deviceInfo.startsWith("LAT:")) {
                    lastDeviceCoordinates = deviceInfo;
                }
                // Debounce rapid multiple triggers within 4 seconds
                long now = System.currentTimeMillis();
                if (now - lastTriggerTime > 4000) {
                    lastTriggerTime = now;
                    triggerEmergencySOS();
                }
            }

            @Override
            public void onRoleConfirmed(String role) {
                Log.d(TAG, "🚨 onRoleConfirmed from wearable: " + role);
                handleRoleConfirmation(role);
            }

            @Override
            public void onConnected() {
                Log.d(TAG, "Respondo Wearable Connected to BLESOSService");
                isConnected = true;
                updateForegroundNotification("Connected to paired Respondo Wearable");
                broadcastState(true);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Respondo Wearable Disconnected from BLESOSService");
                isConnected = false;
                if (bleManager != null && bleManager.isBonded()) {
                    updateForegroundNotification("Searching for paired Respondo Wearable...");
                } else {
                    updateForegroundNotification("Respondo Wearable: Pair in Phone Bluetooth Settings");
                }
                broadcastState(false);
            }

            @Override
            public void onScanStarted() {
                Log.d(TAG, "Scanning for Respondo Wearable...");
            }
        });

        bleManager.startScan();
    }

    private void broadcastState(boolean connected) {
        Intent intent = new Intent(ACTION_BLE_STATE_CHANGED);
        intent.putExtra(EXTRA_CONNECTED, connected);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private synchronized void triggerEmergencySOS() {
        Log.i(TAG, "Executing Background SOS dispatch to Firestore...");

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, null)
                    .addOnSuccessListener(loc -> {
                        if (loc != null) {
                            uploadAlertToFirestore(loc.getLatitude(), loc.getLongitude());
                        } else {
                            locationClient.getLastLocation().addOnSuccessListener(lastLoc -> {
                                if (lastLoc != null) {
                                    uploadAlertToFirestore(lastLoc.getLatitude(), lastLoc.getLongitude());
                                } else {
                                    uploadAlertFromFallback();
                                }
                            }).addOnFailureListener(e -> uploadAlertFromFallback());
                        }
                    })
                    .addOnFailureListener(e -> uploadAlertFromFallback());
        } else {
            uploadAlertFromFallback();
        }
    }

    private void uploadAlertFromFallback() {
        double lat = 0.0;
        double lng = 0.0;
        if (lastDeviceCoordinates != null && lastDeviceCoordinates.startsWith("LAT:")) {
            try {
                // Expected format: LAT:14.115246,LNG:122.957632,ID:TTGO_ESP32_SOS
                String[] parts = lastDeviceCoordinates.split(",");
                for (String p : parts) {
                    if (p.startsWith("LAT:")) lat = Double.parseDouble(p.substring(4));
                    if (p.startsWith("LNG:")) lng = Double.parseDouble(p.substring(4));
                }
            } catch (Exception ignored) {}
        }
        uploadAlertToFirestore(lat, lng);
    }

    private void uploadAlertToFirestore(double lat, double lng) {
        String userId = getOrCreateUserId();

        Map<String, Object> alert = new HashMap<>();
        alert.put("userId", userId);
        alert.put("latitude", lat);
        alert.put("longitude", lng);
        alert.put("timestamp", FieldValue.serverTimestamp());
        alert.put("status", "active");
        alert.put("source", "ESP32_BLE");
        alert.put("deviceToken", "");

        db.collection("sos_alerts")
                .add(alert)
                .addOnSuccessListener(ref -> {
                    String alertId = ref.getId();
                    Log.i(TAG, "Emergency SOS successfully written to Firestore! ID: " + alertId);

                    saveAlertId(alertId);
                    showHighPriorityAlertNotification(alertId);
                    speakText("Emergency alert sent. Long press your wearable if you are the victim, or double press if reporting for someone else.");

                    Intent intent = new Intent(ACTION_SOS_SENT);
                    intent.putExtra(EXTRA_ALERT_ID, alertId);
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload SOS alert from service: " + e.getMessage()));
    }

    private void showHighPriorityAlertNotification(String alertId) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setContentTitle("🚨 EMERGENCY SOS SENT TO BFP!")
                .setContentText("Respondo Wearable panic button triggered. Help has been dispatched.")
                .setSmallIcon(R.mipmap.ic_launcher)
                .setPriority(NotificationCompat.PRIORITY_MAX)
                .setCategory(NotificationCompat.CATEGORY_ALARM)
                .setAutoCancel(true)
                .setContentIntent(pi)
                .build();

        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {
            nm.notify(NOTIFICATION_ID_SOS, notification);
        }
    }

    private String getOrCreateUserId() {
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_USER, null);
        if (id == null) {
            id = "user_" + System.currentTimeMillis();
            prefs.edit().putString(KEY_USER, id).apply();
        }
        return id;
    }

    private void saveAlertId(String id) {
        getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALERT, id)
                .apply();
    }

    private String getSavedAlertId() {
        return getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getString(KEY_ALERT, null);
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (bleManager != null && !bleManager.isConnected()) {
            bleManager.startScan();
        }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        if (bleManager != null) {
            bleManager.disconnect();
        }
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
