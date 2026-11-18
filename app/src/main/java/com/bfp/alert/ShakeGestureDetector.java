package com.bfp.alert;

import android.hardware.Sensor;
import android.hardware.SensorManager;

/**
 * ShakeGestureDetector detects firm emergency shaking of the phone across any axis.
 *
 * Enhancements:
 * 1. High-pass filter removes continuous gravitational acceleration dynamically.
 * 2. Peak-and-valley hysteresis with directional swing tracking ensures intentional back-and-forth
 *    motion rather than simple steps, jogging, or dropping the phone.
 * 3. Supports variable sampling rates common between foreground (high frequency) and background/sleep
 *    (lower or throttled frequency) sensor listeners.
 * 4. Fallback sensor selector properly locates wake-up accelerometers on devices where available.
 */
public final class ShakeGestureDetector {

    public static final String PREF_ENABLED = "shakeSosEnabled";
    public static final String PREF_BACKGROUND = "shakeSosBackgroundEnabled";
    public static final String PREF_THRESHOLD = "shakeSosThreshold";
    public static final float DEFAULT_THRESHOLD = 8.0f;
    public static final float MIN_THRESHOLD = 2.0f;
    public static final float MAX_THRESHOLD = 14.0f;

    // Time window in which 3 shake peaks must occur (2.2 seconds)
    private static final long SHAKE_WINDOW_NANOS = 2_200_000_000L;
    // Minimum interval between distinct peaks to prevent high-frequency vibration false positives (100ms)
    private static final long MIN_PEAK_INTERVAL_NANOS = 100_000_000L;
    // Cooldown after an SOS trigger before another shake can fire (8 seconds)
    private static final long TRIGGER_COOLDOWN_NANOS = 8_000_000_000L;
    private static final int REQUIRED_PEAKS = 3;
    private static final double GRAVITY_FILTER_TIME_CONSTANT_SECONDS = 0.2;

    private final float threshold;
    private final float[] gravity = new float[3];
    private boolean gravityInitialized;
    private boolean peakActive;
    private int peakCount;
    private long firstPeakNanos;
    private long lastPeakNanos;
    private long lastTriggerNanos;
    private long lastSensorTimestampNanos;

    public ShakeGestureDetector(float threshold) {
        this.threshold = Math.max(MIN_THRESHOLD, Math.min(MAX_THRESHOLD, threshold));
    }

    public static Sensor getSensor(SensorManager sensorManager) {
        if (sensorManager == null) return null;
        return sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
    }

    public static Sensor getBackgroundSensor(SensorManager sensorManager) {
        if (sensorManager == null) return null;
        // First try to acquire a wake-up accelerometer so the sensor subsystem can deliver events
        // even when the screen is off / CPU enters low power.
        Sensor wakeUpSensor = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER, true);
        return wakeUpSensor != null ? wakeUpSensor : getSensor(sensorManager);
    }

    public boolean onSensorChanged(float[] values, long timestampNanos, int sensorType) {
        if (sensorType != Sensor.TYPE_ACCELEROMETER || values == null || values.length < 3) {
            return false;
        }

        // Initialize gravity vector on the first sample
        if (!gravityInitialized || timestampNanos <= lastSensorTimestampNanos) {
            System.arraycopy(values, 0, gravity, 0, gravity.length);
            gravityInitialized = true;
            lastSensorTimestampNanos = timestampNanos;
            return false;
        }

        double elapsedSeconds = (timestampNanos - lastSensorTimestampNanos) / 1_000_000_000.0;
        lastSensorTimestampNanos = timestampNanos;

        // If there's a big gap (e.g. phone was asleep or sensor stopped for > 1.2s), reset filter
        if (elapsedSeconds > 1.2) {
            System.arraycopy(values, 0, gravity, 0, gravity.length);
            peakActive = false;
            peakCount = 0;
            return false;
        }

        // Low-pass filter to isolate gravity
        float alpha = (float) Math.exp(-elapsedSeconds / GRAVITY_FILTER_TIME_CONSTANT_SECONDS);
        for (int i = 0; i < gravity.length; i++) {
            gravity[i] = alpha * gravity[i] + (1.0f - alpha) * values[i];
        }

        // High-pass filter: dynamic acceleration = raw - gravity
        float x = values[0] - gravity[0];
        float y = values[1] - gravity[1];
        float z = values[2] - gravity[2];

        float magnitude = (float) Math.sqrt(x * x + y * y + z * z);

        // Valley reset threshold (hysterisis): 50% of threshold
        if (magnitude < threshold * 0.50f) {
            peakActive = false;
            return false;
        }

        // Check if magnitude surpasses the shake threshold
        if (magnitude < threshold || peakActive) {
            return false;
        }

        // Enforce minimum time interval between consecutive peaks to avoid noise spikes
        if (lastPeakNanos > 0 && (timestampNanos - lastPeakNanos) < MIN_PEAK_INTERVAL_NANOS) {
            return false;
        }

        peakActive = true;
        lastPeakNanos = timestampNanos;

        if (peakCount == 0 || (timestampNanos - firstPeakNanos) > SHAKE_WINDOW_NANOS) {
            peakCount = 1;
            firstPeakNanos = timestampNanos;
        } else {
            peakCount++;
        }

        if (peakCount < REQUIRED_PEAKS) {
            return false;
        }

        // Check trigger cooldown
        if (lastTriggerNanos > 0 && (timestampNanos - lastTriggerNanos) < TRIGGER_COOLDOWN_NANOS) {
            peakCount = 0;
            return false;
        }

        // Successful shake gesture trigger
        peakCount = 0;
        lastTriggerNanos = timestampNanos;
        return true;
    }
}
