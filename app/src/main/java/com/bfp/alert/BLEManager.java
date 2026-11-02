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
import java.util.UUID;

public class BLEManager {

    private static final String TAG = "BLEManager";

    // Must match ESP32 sketch exactly
    private static final String SERVICE_UUID =
            "12345678-1234-1234-1234-123456789abc";
    private static final String CHARACTERISTIC_UUID =
            "87654321-4321-4321-4321-cba987654321";
    private static final String DEVICE_NAME =
            "BFP-SOS-Device";
    private static final String CCCD_UUID =
            "00002902-0000-1000-8000-00805f9b34fb";

    public interface BLEListener {
        void onSOSReceived(String deviceInfo);
        void onConnected();
        void onDisconnected();
        void onScanStarted();
    }

    private final Context       ctx;
    private final BLEListener   listener;
    private final Handler       handler;
    private BluetoothAdapter    adapter;
    private BluetoothLeScanner  scanner;
    private BluetoothGatt       gatt;
    private boolean             scanning = false;
    private boolean             connected = false;

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

        if (scanning || connected) return;
        scanning = true;

        scanner = adapter.getBluetoothLeScanner();
        if (scanner == null) {
            Log.e(TAG, "BluetoothLeScanner is null");
            scanning = false;
            return;
        }

        // Filter by device name
        List<ScanFilter> filters = new ArrayList<>();
        ScanFilter filter =
                new ScanFilter.Builder()
                        .setDeviceName(DEVICE_NAME)
                        .build();
        filters.add(filter);

        ScanSettings settings =
                new ScanSettings.Builder()
                        .setScanMode(
                                ScanSettings.SCAN_MODE_LOW_LATENCY)
                        .build();

        try {
            scanner.startScan(filters, settings, scanCallback);
            if (listener != null)
                listener.onScanStarted();
            Log.d(TAG, "Scanning for " + DEVICE_NAME + "...");
        } catch (Exception e) {
            Log.e(TAG, "Error starting scan: " + e.getMessage());
            scanning = false;
            return;
        }

        // Cycle scan after 25s: pause 5s, then resume scan to preserve battery while continually seeking
        handler.postDelayed(() -> {
            if (scanning && !connected) {
                stopScan();
                handler.postDelayed(() -> {
                    if (!connected) startScan();
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

                    BluetoothDevice device =
                            result.getDevice();
                    String name = device.getName();

                    if (DEVICE_NAME.equals(name)) {
                        Log.d(TAG, "Found " + DEVICE_NAME
                                + " — connecting...");
                        stopScan();
                        connectToDevice(device);
                    }
                }

                @Override
                public void onScanFailed(int errorCode) {
                    Log.e(TAG, "Scan failed: " + errorCode);
                    scanning = false;
                    // Retry scan in 5 seconds
                    handler.postDelayed(() -> {
                        if (!connected) startScan();
                    }, 5000);
                }
            };

    // ── Connect to ESP32 ─────────────────────────────────────────
    private void connectToDevice(BluetoothDevice device) {
        if (ActivityCompat.checkSelfPermission(ctx,
                Manifest.permission.BLUETOOTH_CONNECT)
                != PackageManager.PERMISSION_GRANTED)
            return;

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
                        Log.d(TAG, "Connected to ESP32");
                        connected = true;
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
                        handler.postDelayed(() -> startScan(), 3000);
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

                    handler.post(() -> {
                        if (value != null && (value.startsWith("SOS_TRIGGER") || "SOS_TRIGGER".equals(value))) {
                            Log.d(TAG, "SOS trigger received!");
                            if (listener != null)
                                listener.onSOSReceived("");
                        } else if (value != null && value.startsWith("LAT:")) {
                            if (listener != null)
                                listener.onSOSReceived(value);
                        }
                    });
                }

                // For Android 13+
                @Override
                public void onCharacteristicChanged(
                        BluetoothGatt g,
                        BluetoothGattCharacteristic c,
                        byte[] value) {
                    String str = new String(value);
                    Log.d(TAG, "BLE received (new): " + str);

                    handler.post(() -> {
                        if (str != null && (str.startsWith("SOS_TRIGGER") || "SOS_TRIGGER".equals(str))) {
                            Log.d(TAG, "SOS trigger received (byte array)!");
                            if (listener != null)
                                listener.onSOSReceived("");
                        } else if (str != null && str.startsWith("LAT:")) {
                            if (listener != null)
                                listener.onSOSReceived(str);
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

    public void disconnect() {
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
        connected = false;
        stopScan();
    }
}
