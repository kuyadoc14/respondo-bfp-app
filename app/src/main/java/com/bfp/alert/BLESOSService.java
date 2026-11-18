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
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
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

public class BLESOSService extends Service implements SensorEventListener {

    private static final String TAG = "BLESOSService";
    public static final String CHANNEL_ID_MONITOR = "esp32_ble_monitor";
    public static final String CHANNEL_ID_ALERTS  = "esp32_sos_alert";
    private static final int NOTIFICATION_ID_FOREGROUND = 1001;
    private static final int NOTIFICATION_ID_SOS = 1002;

    public static final String ACTION_BLE_STATE_CHANGED    = "com.bfp.alert.BLE_STATE_CHANGED";
    public static final String ACTION_SOS_SENT             = "com.bfp.alert.SOS_SENT";
    public static final String ACTION_BLE_ROLE_CONFIRMED   = "com.bfp.alert.BLE_ROLE_CONFIRMED";
    public static final String ACTION_CONNECT_WEARABLE     = "com.bfp.alert.CONNECT_WEARABLE";
    public static final String ACTION_DISCONNECT_WEARABLE  = "com.bfp.alert.DISCONNECT_WEARABLE";
    public static final String ACTION_TEST_SOS             = "com.bfp.alert.TEST_SOS";
    public static final String EXTRA_CONNECTED             = "extra_connected";
    public static final String EXTRA_CONNECTING            = "extra_connecting";
    public static final String EXTRA_DEVICE_NAME           = "extra_device_name";
    public static final String EXTRA_DEVICE_MAC            = "extra_device_mac";
    public static final String EXTRA_ALERT_ID              = "extra_alert_id";
    public static final String EXTRA_ROLE                  = "extra_role";
    public static final String EXTRA_SOURCE                = "extra_source";
    private static final String ACTION_TRIGGER_SHAKE_SOS = "com.bfp.alert.TRIGGER_SHAKE_SOS";

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
    private long lastShakeTriggerTime = 0;
    private TextToSpeech tts = null;
    private SensorManager sensorManager;
    private ShakeGestureDetector shakeGestureDetector;

    public static boolean isDeviceConnected() {
        return sInstance != null && sInstance.isConnected;
    }

    public static boolean isDeviceConnecting() {
        return sInstance != null && sInstance.bleManager != null && sInstance.bleManager.isConnecting();
    }

    public static String getConnectedDeviceName() {
        if (sInstance != null && sInstance.bleManager != null) {
            return sInstance.bleManager.getConnectedDeviceName();
        }
        return "Respondo Wearable";
    }

    public static String getConnectedDeviceAddress() {
        if (sInstance != null && sInstance.bleManager != null) {
            return sInstance.bleManager.getConnectedDeviceAddress();
        }
        return null;
    }

    public static void connectWearable(Context context) {
        connectWearable(context, null);
    }

    public static void connectWearable(Context context, String mac) {
        if (context == null) return;
        if (sInstance != null && sInstance.bleManager != null) {
            if (mac != null && !mac.trim().isEmpty()) {
                sInstance.bleManager.connectByMac(mac.trim());
            } else {
                sInstance.bleManager.startScan();
            }
            return;
        }
        Intent intent = new Intent(context, BLESOSService.class);
        intent.setAction(ACTION_CONNECT_WEARABLE);
        if (mac != null) intent.putExtra(EXTRA_DEVICE_MAC, mac);
        startServiceIntent(context, intent);
    }

    public static void disconnectWearable(Context context) {
        if (context == null) return;
        if (sInstance != null && sInstance.bleManager != null) {
            sInstance.bleManager.disconnect();
            return;
        }
        Intent intent = new Intent(context, BLESOSService.class);
        intent.setAction(ACTION_DISCONNECT_WEARABLE);
        startServiceIntent(context, intent);
    }

    public static void syncStatusWithWearable(String status) {
        if (sInstance != null && sInstance.bleManager != null && status != null) {
            sInstance.bleManager.sendCommand(status);
        }
    }

    public static void triggerSosTest(Context context) {
        if (context == null) return;
        if (sInstance != null) {
            sInstance.triggerEmergencySOS("TEST");
            return;
        }
        Intent intent = new Intent(context, BLESOSService.class);
        intent.setAction(ACTION_TEST_SOS);
        startServiceIntent(context, intent);
    }

    private static void startServiceIntent(Context context, Intent intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to send command to BLESOSService: " + e.getMessage());
        }
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
        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);

        createNotificationChannels();
        startForegroundServiceNotification("Searching for Respondo Wearable...");

        initTTS();
        initBLE();
        updateShakeSensorRegistration();
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

        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID_MONITOR)
                .setContentTitle("Respondo Wearable Monitor")
                .setContentText(statusText)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentIntent(pendingIntent)
                .setOngoing(true)
                .setPriority(NotificationCompat.PRIORITY_LOW);
        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false)
                && prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false)) {
            Sensor backgroundSensor = sensorManager == null
                    ? null : ShakeGestureDetector.getBackgroundSensor(sensorManager);
            builder.setSubText(backgroundSensor != null && backgroundSensor.isWakeUpSensor()
                    ? "Shake SOS active in background, including screen-off"
                    : "Shake SOS active while screen is on; screen-off detection may be unavailable");
        }
        return builder.build();
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
                long now = System.currentTimeMillis();
                if (now - lastTriggerTime > 2000) {
                    lastTriggerTime = now;
                    String role = null;
                    if ("VICTIM".equalsIgnoreCase(deviceInfo)) {
                        role = "victim";
                    } else if ("BYSTANDER".equalsIgnoreCase(deviceInfo)) {
                        role = "bystander";
                    }

                    String existingAlertId = getSavedAlertId();
                    if (existingAlertId != null && !existingAlertId.isEmpty() && role != null) {
                        Log.i(TAG, "Active alert exists (" + existingAlertId + "). Updating role to: " + role);
                        handleRoleConfirmation(role);
                        syncStatusWithWearable("STATUS:ACTIVE");
                    } else {
                        triggerEmergencySOS("ESP32_BLE", role);
                    }
                }
            }

            @Override
            public void onRoleConfirmed(String role) {
                Log.d(TAG, "🚨 onRoleConfirmed from wearable: " + role);
                handleRoleConfirmation(role);
            }

            @Override
            public void onConnecting(String deviceName, String mac) {
                Log.d(TAG, "Connecting to Respondo Wearable: " + deviceName + " (" + mac + ")");
                updateForegroundNotification("Connecting to " + (deviceName != null ? deviceName : "Respondo Wearable") + "...");
                broadcastState(false, true, deviceName, mac);
            }

            @Override
            public void onConnected() {
                Log.d(TAG, "Respondo Wearable Connected to BLESOSService");
                isConnected = true;
                String name = bleManager != null ? bleManager.getConnectedDeviceName() : "Respondo Wearable";
                String mac = bleManager != null ? bleManager.getConnectedDeviceAddress() : null;
                updateForegroundNotification("Connected to " + name);
                broadcastState(true, false, name, mac);

                new android.os.Handler(android.os.Looper.getMainLooper()).postDelayed(() -> {
                    String activeId = getSavedAlertId();
                    if (activeId != null && !activeId.isEmpty()) {
                        syncStatusWithWearable("STATUS:ACTIVE");
                    } else {
                        syncStatusWithWearable("STATUS:IDLE");
                    }
                }, 1000);
            }

            @Override
            public void onDisconnected() {
                Log.d(TAG, "Respondo Wearable Disconnected from BLESOSService");
                isConnected = false;
                String mac = bleManager != null ? bleManager.getSavedDeviceMac() : null;
                updateForegroundNotification("Respondo Wearable: Disconnected");
                broadcastState(false, false, null, mac);
            }

            @Override
            public void onScanStarted() {
                Log.d(TAG, "Scanning for Respondo Wearable...");
                broadcastState(false, true, "Respondo Wearable", bleManager != null ? bleManager.getSavedDeviceMac() : null);
            }
        });

        bleManager.startScan();
    }

    private void broadcastState(boolean connected, boolean connecting, String devName, String devMac) {
        Intent intent = new Intent(ACTION_BLE_STATE_CHANGED);
        intent.putExtra(EXTRA_CONNECTED, connected);
        intent.putExtra(EXTRA_CONNECTING, connecting);
        if (devName != null) intent.putExtra(EXTRA_DEVICE_NAME, devName);
        if (devMac != null) intent.putExtra(EXTRA_DEVICE_MAC, devMac);
        intent.setPackage(getPackageName());
        sendBroadcast(intent);
    }

    private synchronized void triggerEmergencySOS() {
        triggerEmergencySOS("ESP32_BLE", null);
    }

    private synchronized void triggerEmergencySOS(String source) {
        triggerEmergencySOS(source, null);
    }

    private synchronized void triggerEmergencySOS(String source, String role) {
        Log.i(TAG, "Executing Background SOS dispatch to Firestore... source=" + source + ", role=" + role);

        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            // Immediate location retrieval via getLastLocation (<5ms)
            locationClient.getLastLocation()
                    .addOnSuccessListener(lastLoc -> {
                        if (lastLoc != null) {
                            uploadAlertToFirestore(lastLoc.getLatitude(), lastLoc.getLongitude(), source, role);
                        } else {
                            uploadAlertFromFallback(source, role);
                        }
                    })
                    .addOnFailureListener(e -> {
                        Log.w(TAG, "getLastLocation failed: " + e.getMessage());
                        uploadAlertFromFallback(source, role);
                    });
        } else {
            uploadAlertFromFallback(source, role);
        }
    }

    private void uploadAlertFromFallback(String source, String role) {
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
        uploadAlertToFirestore(lat, lng, source, role);
    }

    private void uploadAlertToFirestore(double lat, double lng, String source, String role) {
        String userId = getOrCreateUserId();

        Map<String, Object> alert = new HashMap<>();
        alert.put("userId", userId);
        alert.put("latitude", lat);
        alert.put("longitude", lng);
        alert.put("timestamp", FieldValue.serverTimestamp());
        alert.put("status", "active");
        alert.put("source", source);
        alert.put("deviceToken", "");

        String effectiveRole = (role != null && !role.isEmpty()) ? role : "victim";
        alert.put("reporterRole", effectiveRole);

        if ("victim".equalsIgnoreCase(effectiveRole)) {
            alert.put("countOfVictims", 1);
            alert.put("reportSubmitted", true);
            try {
                UserProfile profile = UserProfileManager.getLocalProfile(this);
                if (profile != null && !profile.isEmpty()) {
                    if (!profile.getFullName().isEmpty()) alert.put("reporterName", profile.getFullName());
                    if (!profile.getPhone().isEmpty()) alert.put("reporterPhone", profile.getPhone());
                    alert.put("reporterProfile", profile.toMap());

                    Map<String, Object> patientMap = profile.toPatientMap();
                    List<Map<String, Object>> patients = new ArrayList<>();
                    patients.add(patientMap);
                    alert.put("patients", patients);
                }
            } catch (Exception err) {
                Log.e(TAG, "Error attaching profile to victim alert: " + err.getMessage());
            }
        }

        db.collection("sos_alerts")
                .add(alert)
                .addOnSuccessListener(ref -> {
                    String alertId = ref.getId();
                    Log.i(TAG, "Emergency SOS successfully written to Firestore! ID: " + alertId);

                    saveAlertId(alertId);
                    showHighPriorityAlertNotification(alertId, source);

                    // Sync active status back to ESP32 immediately
                    syncStatusWithWearable("STATUS:ACTIVE");

                    if ("PHONE_SHAKE".equals(source)) {
                        speakText("Emergency alert sent by shake gesture.");
                    } else if ("bystander".equalsIgnoreCase(effectiveRole)) {
                        speakText("Emergency alert sent. Bystander report submitted.");
                    } else {
                        speakText("Emergency alert sent. Medical profile transmitted.");
                    }

                    Intent intent = new Intent(ACTION_SOS_SENT);
                    intent.putExtra(EXTRA_ALERT_ID, alertId);
                    intent.putExtra(EXTRA_ROLE, effectiveRole);
                    intent.putExtra(EXTRA_SOURCE, source);
                    intent.setPackage(getPackageName());
                    sendBroadcast(intent);
                })
                .addOnFailureListener(e -> Log.e(TAG, "Failed to upload SOS alert from service: " + e.getMessage()));
    }

    private void showHighPriorityAlertNotification(String alertId, String source) {
        Intent intent = new Intent(this, MainActivity.class);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(
                this, 1, intent,
                PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
        );

        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID_ALERTS)
                .setContentTitle("🚨 EMERGENCY SOS SENT TO BFP!")
                .setContentText("PHONE_SHAKE".equals(source)
                        ? "Phone shake detected. Help has been dispatched."
                        : "Respondo Wearable panic button triggered. Help has been dispatched.")
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
        if (intent != null) {
            String action = intent.getAction();
            if (ACTION_TRIGGER_SHAKE_SOS.equals(action)) {
                handleShakeDetected();
            } else if (ACTION_CONNECT_WEARABLE.equals(action)) {
                String mac = intent.getStringExtra(EXTRA_DEVICE_MAC);
                if (bleManager != null) {
                    if (mac != null && !mac.trim().isEmpty()) {
                        bleManager.connectByMac(mac.trim());
                    } else {
                        bleManager.startScan();
                    }
                }
            } else if (ACTION_DISCONNECT_WEARABLE.equals(action)) {
                if (bleManager != null) {
                    bleManager.disconnect();
                }
            } else if (ACTION_TEST_SOS.equals(action)) {
                triggerEmergencySOS("TEST");
            }
        }
        updateShakeSensorRegistration();
        if (bleManager != null && !bleManager.isConnected() && !bleManager.isConnecting()) {
            bleManager.startScan();
        }
        return START_STICKY;
    }

    public static void triggerShakeSOS(Context context) {
        if (context == null) return;
        Intent intent = new Intent(context, BLESOSService.class);
        intent.setAction(ACTION_TRIGGER_SHAKE_SOS);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                ContextCompat.startForegroundService(context, intent);
            } else {
                context.startService(intent);
            }
        } catch (RuntimeException e) {
            Log.e(TAG, "Failed to dispatch shake-triggered SOS to the service.", e);
        }
    }

    public static void refreshShakeMonitoring(Context context) {
        BLESOSService service = sInstance;
        if (service != null) {
            service.updateShakeSensorRegistration();
        } else if (context != null) {
            start(context);
        }
    }

    private void updateShakeSensorRegistration() {
        if (sensorManager == null) return;
        sensorManager.unregisterListener(this);
        shakeGestureDetector = null;

        SharedPreferences prefs = getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        if (!prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false)
                || !prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false)) {
            return;
        }

        Sensor sensor = ShakeGestureDetector.getBackgroundSensor(sensorManager);
        if (sensor == null) {
            Log.e(TAG, "Shake SOS is enabled, but this device has no supported accelerometer.");
            return;
        }

        if (!sensor.isWakeUpSensor()) {
            Log.w(TAG, "No wake-up accelerometer is available; background shake detection may stop when the screen turns off.");
        }

        shakeGestureDetector = new ShakeGestureDetector(
                prefs.getFloat(ShakeGestureDetector.PREF_THRESHOLD, ShakeGestureDetector.DEFAULT_THRESHOLD));
        if (!sensorManager.registerListener(this, sensor, SensorManager.SENSOR_DELAY_GAME)) {
            shakeGestureDetector = null;
            Log.e(TAG, "Unable to register the background shake SOS sensor.");
        } else {
            Log.i(TAG, "Background shake sensor registered: " + sensor.getName()
                    + ", wake-up=" + sensor.isWakeUpSensor());
        }
    }

    @Override
    public void onSensorChanged(SensorEvent event) {
        if (shakeGestureDetector != null
                && shakeGestureDetector.onSensorChanged(event.values, event.timestamp, event.sensor.getType())) {
            handleShakeDetected();
        }
    }

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
    }

    private synchronized void handleShakeDetected() {
        long now = System.currentTimeMillis();
        if (now - lastShakeTriggerTime < 8000) return;
        String activeAlertId = getSavedAlertId();
        if (activeAlertId != null && !activeAlertId.isEmpty()) {
            Log.i(TAG, "Ignoring shake SOS because an alert is already active.");
            return;
        }
        lastShakeTriggerTime = now;
        triggerEmergencySOS("PHONE_SHAKE");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        sInstance = null;
        if (sensorManager != null) {
            sensorManager.unregisterListener(this);
        }
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
