package com.bfp.alert;

import android.content.Context;
import android.content.SharedPreferences;
import android.hardware.Sensor;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.switchmaterial.SwitchMaterial;

import java.util.Locale;

public class ShakeSettingsBottomSheet extends BottomSheetDialogFragment {

    public static final String TAG = "ShakeSettingsBottomSheet";

    private SwitchMaterial switchShakeEnabled;
    private View containerShakeOptions;
    private RadioGroup rgShakeScope;
    private RadioButton rbScopeForeground;
    private RadioButton rbScopeBackground;
    private TextView tvWakeUpSensorNote;
    private TextView tvThresholdDisplay;
    private SeekBar sbShakeThreshold;
    private Button btnSaveShakeSettings;

    public interface OnSettingsSavedListener {
        void onSettingsSaved(boolean enabled, boolean background, float threshold);
    }

    private OnSettingsSavedListener listener;

    public static ShakeSettingsBottomSheet newInstance() {
        return new ShakeSettingsBottomSheet();
    }

    public void setOnSettingsSavedListener(OnSettingsSavedListener listener) {
        this.listener = listener;
    }

    @Override
    public int getTheme() {
        return R.style.Theme_BFPAlert_BottomSheet;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.bottom_sheet_shake_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Context context = requireContext();
        SharedPreferences prefs = context.getSharedPreferences(BLESOSService.PREFS, Context.MODE_PRIVATE);

        switchShakeEnabled = view.findViewById(R.id.switchShakeEnabled);
        containerShakeOptions = view.findViewById(R.id.containerShakeOptions);
        rgShakeScope = view.findViewById(R.id.rgShakeScope);
        rbScopeForeground = view.findViewById(R.id.rbScopeForeground);
        rbScopeBackground = view.findViewById(R.id.rbScopeBackground);
        tvWakeUpSensorNote = view.findViewById(R.id.tvWakeUpSensorNote);
        tvThresholdDisplay = view.findViewById(R.id.tvThresholdDisplay);
        sbShakeThreshold = view.findViewById(R.id.sbShakeThreshold);
        btnSaveShakeSettings = view.findViewById(R.id.btnSaveShakeSettings);

        boolean enabled = prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false);
        boolean background = prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false);
        float threshold = prefs.getFloat(
                ShakeGestureDetector.PREF_THRESHOLD, ShakeGestureDetector.DEFAULT_THRESHOLD);
        threshold = Math.max(ShakeGestureDetector.MIN_THRESHOLD,
                Math.min(ShakeGestureDetector.MAX_THRESHOLD, threshold));

        switchShakeEnabled.setChecked(enabled);
        containerShakeOptions.setVisibility(enabled ? View.VISIBLE : View.GONE);

        if (background) {
            rbScopeBackground.setChecked(true);
        } else {
            rbScopeForeground.setChecked(true);
        }

        SensorManager sensors = (SensorManager) context.getSystemService(Context.SENSOR_SERVICE);
        Sensor wakeSensor = ShakeGestureDetector.getBackgroundSensor(sensors);
        if (wakeSensor != null && wakeSensor.isWakeUpSensor()) {
            tvWakeUpSensorNote.setText("✓ Hardware wake-up accelerometer detected. Background shake functions even with screen turned off.");
            tvWakeUpSensorNote.setTextColor(0xFF81C784);
        } else {
            tvWakeUpSensorNote.setText("⚠ Standard accelerometer available. Works consistently while screen is on; screen-off detection may be restricted by device battery saver.");
            tvWakeUpSensorNote.setTextColor(0xFFFFB74D);
        }

        int maxProgress = Math.round((ShakeGestureDetector.MAX_THRESHOLD - ShakeGestureDetector.MIN_THRESHOLD) * 10);
        sbShakeThreshold.setMax(maxProgress);
        sbShakeThreshold.setProgress(Math.round((threshold - ShakeGestureDetector.MIN_THRESHOLD) * 10));
        updateThresholdDisplay(threshold);

        switchShakeEnabled.setOnCheckedChangeListener((btn, isChecked) -> {
            containerShakeOptions.setVisibility(isChecked ? View.VISIBLE : View.GONE);
        });

        sbShakeThreshold.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                float val = ShakeGestureDetector.MIN_THRESHOLD + progress / 10.0f;
                updateThresholdDisplay(val);
            }
            @Override public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        btnSaveShakeSettings.setOnClickListener(v -> {
            boolean isEnabled = switchShakeEnabled.isChecked();
            boolean isBg = rbScopeBackground.isChecked();
            float chosenThreshold = ShakeGestureDetector.MIN_THRESHOLD + sbShakeThreshold.getProgress() / 10.0f;

            if (isEnabled && (sensors == null || ShakeGestureDetector.getSensor(sensors) == null)) {
                Toast.makeText(context, "Shake sensor is not available on this device.", Toast.LENGTH_SHORT).show();
                return;
            }

            prefs.edit()
                    .putBoolean(ShakeGestureDetector.PREF_ENABLED, isEnabled)
                    .putBoolean(ShakeGestureDetector.PREF_BACKGROUND, isBg)
                    .putFloat(ShakeGestureDetector.PREF_THRESHOLD, chosenThreshold)
                    .apply();

            if (listener != null) {
                listener.onSettingsSaved(isEnabled, isBg, chosenThreshold);
            }

            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).refreshShakeDetection();
            }
            BLESOSService.refreshShakeMonitoring(context);

            Toast.makeText(context,
                    isEnabled ? "Shake SOS settings updated" : "Shake SOS disabled",
                    Toast.LENGTH_SHORT).show();
            dismiss();
        });
    }

    private void updateThresholdDisplay(float value) {
        String label;
        if (value <= 4.0f) {
            label = "Very Sensitive";
        } else if (value <= 7.0f) {
            label = "Sensitive";
        } else if (value <= 10.0f) {
            label = "Normal / Balanced";
        } else {
            label = "Firm Shake Only";
        }
        tvThresholdDisplay.setText(String.format(Locale.getDefault(), "%.1f m/s² (%s)", value, label));
    }
}
