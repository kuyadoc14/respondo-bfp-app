package com.bfp.alert;

import android.Manifest;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.core.app.ActivityCompat;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import android.content.SharedPreferences;
import java.util.UUID;

public class BLEManager {

    private static final String TAG = "BLEManager";

    // Must match ESP32 sketch exactly
    public static final String SERVICE_UUID =
            "12345678-1234-1234-1234-123456789abc";
    public static final String CHARACTERISTIC_UUID =
            "87654321-4321-4321-4321-cba987654321";
    public static final String DEVICE_NAME =
            "Respondo Device";
    public static final String ALT_DEVICE_NAME =
            "Respondo Wearable";
    public static final String LEGACY_DEVICE_NAME =
            "BFP-SOS-Device";

    public static boolean isRespondoDevice(String name) {
        if (name == null) return false;
        String lower = name.toLowerCase(java.util.Locale.ROOT);
        return lower.contains("respondo") || lower.contains("bfp-sos") || lower.contains("ttgo");
    }
    private static final String CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb";
    public static final String PREF_BLE_MAC =
            "ble_wearable_mac";
    public static final String PREF_BLE_NAME =
            "ble_wearable_name";

    public interface BLEListener {
        void onSOSReceived(String deviceInfo);
        default void onRoleConfirmed(String role) {}
        void onConnected();
        void onDisconnected();
        void onScanStarted();
        default void onConnecting(String deviceName, String mac) {}
    }

    private final Context       ctx;
    private final BLEListener   listener;
    private final Handler       handler;
    private BluetoothAdapter    adapter;
    private BluetoothLeScanner  scanner;
    private BluetoothGatt       gatt;
    private boolean             scanning = false;
    private boolean             connected = false;
    private boolean             connecting = false;
    private String              connectedDeviceName = null;
    private String              connectedDeviceAddress = null;

    public BLEManager(Context ctx, BLEListener listener) {
        this.ctx      = ctx;
        this.listener = listener;
        this.handler  = new Handler(Looper.getMainLooper());
        initBluetooth();
    }

    private void initBluetooth() {
        BluetoothManager manager =
                (BluetoothManager) ctx.getSystemService(
                        Context.BLUETOOTH_SERVICE);
        if (manager != null) {
            adapter = manager.getAdapter();
        }
    }

    public BluetoothDevice getBondedWearable() {
        if (adapter == null || !adapter.isEnabled()) return null;
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        Set<BluetoothDevice> bonded = adapter.getBondedDevices();
        if (bonded != null) {
            for (BluetoothDevice d : bonded) {
                String bName = d.getName();
                if (isRespondoDevice(bName)) {
                    return d;
                }
            }
        }
        return null;
    }

    public boolean isBonded() {
        return getBondedWearable() != null;
    }

    public String getSavedDeviceMac() {
        return ctx.getSharedPreferences("bfp_prefs", Context.MODE_PRIVATE)
                .getString(PREF_BLE_MAC, null);
    }

    public String getSavedDeviceName() {
        return ctx.getSharedPreferences("bfp_prefs", Context.MODE_PRIVATE)
                .getString(PREF_BLE_NAME, DEVICE_NAME);
    }

    public boolean isConnecting() {
        return connecting;
    }

    public boolean isScanning() {
        return scanning;
    }

    public String getConnectedDeviceName() {
        if (connectedDeviceName != null && !connectedDeviceName.isEmpty()) {
            return connectedDeviceName;
        }
        return DEVICE_NAME;
    }

    public String getConnectedDeviceAddress() {
        if (connectedDeviceAddress != null && !connectedDeviceAddress.isEmpty()) {
            return connectedDeviceAddress;
        }
        return getSavedDeviceMac();
    }

    public void connectByMac(String mac) {
        if (mac == null || mac.trim().isEmpty() || adapter == null || !adapter.isEnabled()) {
            startScan();
            return;
        }
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return;
        }
        try {
            BluetoothDevice device = adapter.getRemoteDevice(mac.trim().toUpperCase());
            Log.d(TAG, "Direct connecting to MAC: " + device.getAddress());
            connectToDevice(device);
        } catch (Exception e) {
            Log.w(TAG, "Failed direct connect by MAC, falling back to scan: " + e.getMessage());
            startScan();
        }
    }

    // ── Start scanning for ESP32 ─────────────────────────────────
    public void startScan() {
        if (adapter == null || !adapter.isEnabled()) {
            Log.e(TAG, "Bluetooth not enabled");
            return;
        }

        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_SCAN)
                != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "No BLUETOOTH_SCAN permission");
            return;
        }

        if (scanning || connected || connecting) return;

        // Priority 1: Direct connect to already paired/bonded wearable
        BluetoothDevice paired = getBondedWearable();
        if (paired != null) {
            Log.d(TAG, "Found paired wearable in phone settings: " + paired.getName() + " (" + paired.getAddress() + ") — connecting directly...");
            connectToDevice(paired);
            return;
        }

        scanning = true;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            scanning = false;
            return;
        }

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setScanMode(
                                ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .setReportDelay(0)
                        .build();

        try {
            // Unfiltered scan ensures all BLE advertisement packets are received across all Android chipsets
            scanner.startScan(null, settings, scanCallback);
            if (listener != null)
                listener.onScanStarted();
            Log.d(TAG, "Scanning for " + DEVICE_NAME + " (broad scan)...");
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan: " + e.getMessage());
            scanning = false;
            return;
        }

        // Cycle scan after 25s: pause 5s, then resume scan to preserve battery while continually seeking
        handler.postDelayed(() -> {
            if (scanning && !connected && !connecting) {
                stopScan();
                handler.postDelayed(() -> {
                    if (!connected && !connecting) startScan();
                }, 4000);
            }
        }, 25000);
    }

    public void stopScan() {
        if (!scanning) return;
        scanning = false;
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_SCAN)
                == PackageManager.PERMISSION_GRANTED) {
            if (scanner != null) {
                try {
                    scanner.stopScan(scanCallback);
                } catch (Exception ignored) {}
            }
        }
        Log.d(TAG, "Scan stopped");
    }

    // ── Scan callback ────────────────────────────────────────────
    private final ScanCallback scanCallback =
            new ScanCallback() {
                @Override
                public void onScanResult(int callbackType,
                                         ScanResult result) {
                    if (ActivityCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED)
                        return;

                    BluetoothDevice device = result.getDevice();
                    String name = device.getName();
                    if (name == null && result.getScanRecord() != null) {
                        name = result.getScanRecord().getDeviceName();
                    }

                    boolean matchesUuid = false;
                    if (result.getScanRecord() != null && result.getScanRecord().getServiceUuids() != null) {
                        for (android.os.ParcelUuid pu : result.getScanRecord().getServiceUuids()) {
                            if (SERVICE_UUID.equalsIgnoreCase(pu.toString())) {
                                matchesUuid = true;
                                break;
                            }
                        }
                    }

                    String savedMac = getSavedDeviceMac();
                    boolean matchesSavedMac = savedMac != null && savedMac.equalsIgnoreCase(device.getAddress());

                    if (isRespondoDevice(name) || matchesUuid || matchesSavedMac) {
                        Log.d(TAG, "Found " + (name != null ? name : "Device") + " (" + device.getAddress() + ") during scan");
                        stopScan();
                        if (device.getBondState() != BluetoothDevice.BOND_BONDED) {
                            Log.i(TAG, "Initiating Bluetooth pairing with " + name);
                            try {
                                device.createBond();
                            } catch (Exception ignored) {}
                        }
                        connectToDevice(device);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e(TAG, "Scan failed: " + errorCode);
                    scanning = false;
                    // Retry scan in 5 seconds
                    handler.postDelayed(() -> {
                        if (!connected && !connecting) startScan();
                    }, 5000);
                }
            };

    // ── Connect to ESP32 ─────────────────────────────────────────
    private void connectToDevice(BluetoothDevice device) {
        if (device == null) return;
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            return;

        stopScan();
        connecting = true;
        connected = false;
        connectedDeviceName = device.getName() != null ? device.getName() : DEVICE_NAME;
        connectedDeviceAddress = device.getAddress();

        try {
            ctx.getSharedPreferences("bfp_prefs", Context.MODE_PRIVATE)
                    .edit()
                    .putString(PREF_BLE_MAC, device.getAddress())
                    .putString(PREF_BLE_NAME, connectedDeviceName)
                    .apply();
        } catch (Exception ignored) {}

        if (listener != null) {
            handler.post(() -> listener.onConnecting(connectedDeviceName, connectedDeviceAddress));
        }

        if (gatt != null) {
            try {
                gatt.close();
            } catch (Exception ignored) {}
            gatt = null;
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            gatt = device.connectGatt(ctx, false, gattCallback, BluetoothDevice.TRANSPORT_LE);
        } else {
            gatt = device.connectGatt(ctx, false, gattCallback);
        }
    }

    // ── GATT callback ────────────────────────────────────────────
    private final BluetoothGattCallback gattCallback =
            new BluetoothGattCallback() {

                @Override
                public void onConnectionStateChange(
                        BluetoothGatt g, int status,
                        int newState) {
                    if (ActivityCompat.checkSelfPermission(
                            ctx,
                            Manifest.permission.BLUETOOTH_CONNECT)
                            != PackageManager.PERMISSION_GRANTED)
                        return;

                    if (newState == BluetoothGatt.STATE_CONNECTED) {
                        Log.d(TAG, "Connected to ESP32: " + g.getDevice().getName() + " (" + g.getDevice().getAddress() + ")");
                        connected = true;
                        connecting = false;
                        connectedDeviceName = g.getDevice().getName() != null ? g.getDevice().getName() : DEVICE_NAME;
                        connectedDeviceAddress = g.getDevice().getAddress();

                        // Request HIGH connection priority for 7.5ms BLE latency
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            g.requestConnectionPriority(BluetoothGatt.CONNECTION_PRIORITY_HIGH);
                        }

                        if (gatt != null) {
                            gatt.discoverServices();
                        }

                        handler.post(() -> {
                            if (listener != null)
                                listener.onConnected();
                        });

                    } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                        Log.d(TAG, "Disconnected from ESP32");
                        connected = false;
                        connecting = false;
                        if (gatt != null) {
                            try {
                                gatt.close();
                            } catch (Exception ignored) {}
                            gatt = null;
                        }

                        handler.post(() -> {
                            if (listener != null)
                                listener.onDisconnected();
                        });

                        // Auto-reconnect scan after 3 seconds
                        handler.postDelayed(() -> {
                            if (!connected && !connecting) startScan();
                        }, 3000);
                    }
                }

                @Override
                public void onServicesDiscovered(
                        BluetoothGatt g, int status) {
                    if (status == BluetoothGatt.GATT_SUCCESS) {
                        enableNotifications();
                    }
                }

                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt g,
                        BluetoothGattCharacteristic c) {
                    String value = c.getStringValue(0);
                    Log.d(TAG, "BLE received: " + value);
                    processReceivedMessage(value);
                }

                // For Android 13+
                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt g,
                        BluetoothGattCharacteristic c,
                        byte[] value) {
                    String str = new String(value);
                    Log.d(TAG, "BLE received (new): " + str);
                    processReceivedMessage(str);
                }

                private void processReceivedMessage(String raw) {
                    if (raw == null) return;
                    String value = raw.trim();
                    handler.post(() -> {
                        if (value.startsWith("SOS:VICTIM") || value.startsWith("ROLE_VICTIM") || "VICTIM".equals(value)) {
                            Log.d(TAG, "🚨 VICTIM SOS received from wearable!");
                            if (listener != null) {
                                listener.onSOSReceived("VICTIM");
                                listener.onRoleConfirmed("victim");
                            }
                        } else if (value.startsWith("SOS:BYSTANDER") || value.startsWith("ROLE_BYSTANDER") || "BYSTANDER".equals(value)) {
                            Log.d(TAG, "👤 BYSTANDER SOS received from wearable!");
                            if (listener != null) {
                                listener.onSOSReceived("BYSTANDER");
                                listener.onRoleConfirmed("bystander");
                            }
                        } else if (value.startsWith("SOS_TRIGGER") || "SOS".equals(value)) {
                            Log.d(TAG, "🚨 SOS trigger received!");
                            if (listener != null) listener.onSOSReceived("");
                        } else if (value.startsWith("LAT:")) {
                            if (listener != null) listener.onSOSReceived(value);
                        }
                    });
                }
            };

    // ── Enable notifications on characteristic ───────────────────
    private void enableNotifications() {
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            return;

        if (gatt == null) return;

        BluetoothGattService service =
                gatt.getService(
                        UUID.fromString(SERVICE_UUID));

        if (service == null) {
            Log.e(TAG, "Service not found");
            return;
        }

        BluetoothGattCharacteristic characteristic =
                service.getCharacteristic(
                        UUID.fromString(CHARACTERISTIC_UUID));

        if (characteristic == null) {
            Log.e(TAG, "Characteristic not found");
            return;
        }

        // Enable notifications on the GATT client
        gatt.setCharacteristicNotification(
                characteristic, true);

        // Write to CCCD descriptor to enable server-side notifications
        BluetoothGattDescriptor descriptor =
                characteristic.getDescriptor(
                        UUID.fromString(CCCD_UUID));

        if (descriptor != null) {
            descriptor.setValue(
                    BluetoothGattDescriptor
                            .ENABLE_NOTIFICATION_VALUE);
            gatt.writeDescriptor(descriptor);
            Log.d(TAG, "Notifications enabled on ESP32");
        }
    }

    public boolean isConnected() { return connected; }

    public boolean sendCommand(String message) {
        if (gatt == null || !connected || message == null) return false;
        if (ActivityCompat.checkSelfPermission(ctx, Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED) {
            return false;
        }
        try {
            BluetoothGattService service = gatt.getService(UUID.fromString(SERVICE_UUID));
            if (service == null) return false;
            BluetoothGattCharacteristic characteristic = service.getCharacteristic(UUID.fromString(CHARACTERISTIC_UUID));
            if (characteristic == null) return false;

            byte[] bytes = message.getBytes(java.nio.charset.StandardCharsets.UTF_8);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                return gatt.writeCharacteristic(characteristic, bytes, BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT) == android.bluetooth.BluetoothStatusCodes.SUCCESS;
            } else {
                characteristic.setValue(bytes);
                characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
                return gatt.writeCharacteristic(characteristic);
            }
        } catch (Exception e) {
            Log.e(TAG, "Failed to write to BLE characteristic: " + e.getMessage());
            return false;
        }
    }

    public void disconnect() {
        connecting = false;
        connected = false;
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT)
                == PackageManager.PERMISSION_GRANTED) {
            if (gatt != null) {
                try {
                    gatt.disconnect();
                    gatt.close();
                } catch (Exception ignored) {}
                gatt = null;
            }
        }
        stopScan();
        handler.post(() -> {
            if (listener != null) listener.onDisconnected();
        });
    }
}
