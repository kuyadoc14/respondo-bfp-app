package com.bfp.alert;

import android.hardware.Sensor;
import android.hardware.SensorManager;

final class ShakeGestureDetector {

    static final String PREF_ENABLED = "shakeSosEnabled";
    static final String PREF_BACKGROUND = "shakeSosBackgroundEnabled";
    static final String PREF_THRESHOLD = "shakeSosThreshold";
    static final float DEFAULT_THRESHOLD = 8.0f;
    static final float MIN_THRESHOLD = 2.0f;
    static final float MAX_THRESHOLD = 14.0f;

    private static final long SHAKE_WINDOW_NANOS = 2_000_000_000L;
    private static final long TRIGGER_COOLDOWN_NANOS = 8_000_000_000L;
    private static final int REQUIRED_PEAKS = 3;
    private static final double GRAVITY_FILTER_TIME_CONSTANT_SECONDS = 0.2;

    private final float threshold;
    private final float[] gravity = new float[3];
    private boolean gravityInitialized;
    private boolean peakActive;
    private int peakCount;
    private long firstPeakNanos;
    private long lastTriggerNanos;
    private long lastSensorTimestampNanos;

    ShakeGestureDetector(float threshold) {
        this.threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
    }

    static Sensor getSensor(SensorManager sensorManager) {
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    static Sensor getBackgroundSensor(SensorManager sensorManager) {
        Sensor wakeUpSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
        return wakeUpSensor != null ? wakeUpSensor : getSensor(sensorManager);
    }

    boolean onSensorChanged(float[] values, long timestampNanos, int sensorType) {
        if (sensorType != Sensor.TYPE_ACCELEROMETER || values == null || values.length < 3) {
            return false;
        }

        if (!gravityInitialized || timestampNanos <= lastSensorTimestampNanos) {
            System.arraycopy(values, 0, gravity, 0, gravity.length);
            gravityInitialized = true;
            lastSensorTimestampNanos = timestampNanos;
            return false;
        }

        double elapsedSeconds = (timestampNanos - lastSensorTimestampNanos) / 1_000_000_000.0;
        lastSensorTimestampNanos = timestampNanos;
        if (elapsedSeconds > 1.0) {
            System.arraycopy(values, 0, gravity, 0, gravity.length);
            peakActive = false;
            peakCount = 0;
            return false;
        }

        float alpha = (float) Math.exp(
                -elapsedSeconds / GRAVITY_FILTER_TIME_CONSTANT_SECONDS);
        float x = values[0] - gravity[0];
        float y = values[1] - gravity[1];
        float z = values[2] - gravity[2];
        for (int i = 0; i < gravity.length; i++) {
            gravity[i] = alpha * gravity[i] + (1.0f - alpha) * values[i];
        }

        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);
        if (magnitude < threshold * 0.55f) {
            peakActive = false;
            return false;
        }
        if (magnitude < threshold || peakActive) return false;

        peakActive = true;
        if (peakCount == 0 || timestampNanos - firstPeakNanos > SHAKE_WINDOW_NANOS) {
            peakCount = 1;
            firstPeakNanos = timestampNanos;
        } else {
            peakCount++;
        }

        if (peakCount < REQUIRED_PEAKS) return false;
        if (lastTriggerNanos > 0
                && timestampNanos - lastTriggerNanos < TRIGGER_COOLDOWN_NANOS) {
            peakCount = 0;
            return false;
        }

        peakCount = 0;
        lastTriggerNanos = timestampNanos;
        return true;
    }
}
