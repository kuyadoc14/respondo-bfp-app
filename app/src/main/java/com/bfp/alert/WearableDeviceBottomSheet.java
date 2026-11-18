package com.bfp.alert;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class WearableDeviceBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "WearableDeviceSheet";

    private View dotStatus;
    private TextView tvStatusTitle;
    private TextView tvDeviceName;
    private TextView tvDeviceMac;
    private Button btnConnectToggle;
    private Button btnOpenBluetoothSettings;
    private Button btnTestSos;

    private final BroadcastReceiver bleStateReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent != null && BLESOSService.ACTION_BLE_STATE_CHANGED.equals(intent.getAction())) {
                updateUI();
            }
        }
    };

    public static WearableDeviceBottomSheet newInstance() {
        return new WearableDeviceBottomSheet();
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.bottom_sheet_wearable_device, container, false);

        dotStatus = view.findViewById(R.id.dotStatus);
        tvStatusTitle = view.findViewById(R.id.tvStatusTitle);
        tvDeviceName = view.findViewById(R.id.tvDeviceName);
        tvDeviceMac = view.findViewById(R.id.tvDeviceMac);
        btnConnectToggle = view.findViewById(R.id.btnConnectToggle);
        btnOpenBluetoothSettings = view.findViewById(R.id.btnOpenBluetoothSettings);
        btnTestSos = view.findViewById(R.id.btnTestSos);

        btnConnectToggle.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx == null) return;
            if (BLESOSService.isDeviceConnected()) {
                BLESOSService.disconnectWearable(ctx);
                Toast.makeText(ctx, "Disconnecting Respondo Wearable...", Toast.LENGTH_SHORT).show();
            } else {
                BLESOSService.connectWearable(ctx);
                Toast.makeText(ctx, "Connecting directly to Respondo Wearable...", Toast.LENGTH_SHORT).show();
            }
            updateUI();
        });

        btnOpenBluetoothSettings.setOnClickListener(v -> {
            try {
                Intent settingsIntent = new Intent(Settings.ACTION_BLUETOOTH_SETTINGS);
                settingsIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(settingsIntent);
            } catch (Exception e) {
                Toast.makeText(getContext(), "Unable to open Bluetooth settings: " + e.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });

        btnTestSos.setOnClickListener(v -> {
            Context ctx = getContext();
            if (ctx != null) {
                BLESOSService.triggerSosTest(ctx);
                Toast.makeText(ctx, "🚨 Testing Emergency SOS Trigger...", Toast.LENGTH_SHORT).show();
                dismiss();
            }
        });

        updateUI();
        return view;
    }

    private void updateUI() {
        if (!isAdded() || getContext() == null) return;

        boolean connected = BLESOSService.isDeviceConnected();
        boolean connecting = BLESOSService.isDeviceConnecting();
        String name = BLESOSService.getConnectedDeviceName();
        String mac = BLESOSService.getConnectedDeviceAddress();

        if (mac == null || mac.isEmpty()) {
            mac = getContext().getSharedPreferences("bfp_prefs", Context.MODE_PRIVATE)
                    .getString(BLEManager.PREF_BLE_MAC, "A0:DD:6C:74:BE:DC");
        }

        if (tvDeviceName != null) {
            tvDeviceName.setText(name != null ? name : "Respondo Device");
        }
        if (tvDeviceMac != null) {
            tvDeviceMac.setText(mac != null ? mac : "A0:DD:6C:74:BE:DC");
        }

        if (connected) {
            if (dotStatus != null) dotStatus.setBackgroundResource(R.drawable.circle_dot_green);
            if (tvStatusTitle != null) {
                tvStatusTitle.setText("CONNECTED");
                tvStatusTitle.setTextColor(Color.parseColor("#4CAF50"));
            }
            if (btnConnectToggle != null) {
                btnConnectToggle.setText("DISCONNECT WEARABLE");
                btnConnectToggle.setEnabled(true);
                btnConnectToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#333333")));
                btnConnectToggle.setTextColor(Color.WHITE);
            }
        } else if (connecting) {
            if (dotStatus != null) dotStatus.setBackgroundResource(R.drawable.circle_dot_orange);
            if (tvStatusTitle != null) {
                tvStatusTitle.setText("CONNECTING...");
                tvStatusTitle.setTextColor(Color.parseColor("#FF9800"));
            }
            if (btnConnectToggle != null) {
                btnConnectToggle.setText("CONNECTING DIRECTLY...");
                btnConnectToggle.setEnabled(false);
                btnConnectToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#5D4037")));
                btnConnectToggle.setTextColor(Color.parseColor("#FFCCBC"));
            }
        } else {
            if (dotStatus != null) dotStatus.setBackgroundResource(R.drawable.circle_dot_gray);
            if (tvStatusTitle != null) {
                tvStatusTitle.setText("DISCONNECTED");
                tvStatusTitle.setTextColor(Color.parseColor("#9E9E9E"));
            }
            if (btnConnectToggle != null) {
                btnConnectToggle.setText("CONNECT DIRECTLY");
                btnConnectToggle.setEnabled(true);
                btnConnectToggle.setBackgroundTintList(ColorStateList.valueOf(Color.parseColor("#2E7D32")));
                btnConnectToggle.setTextColor(Color.WHITE);
            }
        }
    }

    @Override
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter(BLESOSService.ACTION_BLE_STATE_CHANGED);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(bleStateReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(bleStateReceiver, filter);
            }
        }
        updateUI();
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(bleStateReceiver);
            } catch (Exception ignored) {}
        }
    }
}
