package com.bfp.alert;

import android.hardware.Sensor;

import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShakeGestureDetectorTest {

    @Test
    public void requiresThreeDistinctPeaksAboveTheConfiguredThreshold() {
        ShakeGestureDetector detector = new ShakeGestureDetector(8.0f);

        assertThreePeaksTrigger(detector, 12.0f);
    }

    @Test
    public void sameShakeIsMoreLikelyToTriggerAtLowerIntensityThreshold() {
        ShakeGestureDetector sensitiveDetector = new ShakeGestureDetector(4.0f);
        ShakeGestureDetector firmDetector = new ShakeGestureDetector(10.0f);

        assertTrue(sendThreePeaks(sensitiveDetector, 7.0f));
        assertFalse(sendThreePeaks(firmDetector, 7.0f));
    }

    @Test
    public void ignoresLinearAccelerationSensorToKeepThresholdScaleConsistent() {
        ShakeGestureDetector detector = new ShakeGestureDetector(2.0f);

        assertFalse(detector.onSensorChanged(new float[]{30f, 0f, 0f}, 1_000_000_000L,
                Sensor.TYPE_LINEAR_ACCELERATION));
    }

    private void assertThreePeaksTrigger(ShakeGestureDetector detector, float impulse) {
        assertTrue(sendThreePeaks(detector, impulse));
    }

    private boolean sendThreePeaks(ShakeGestureDetector detector, float impulse) {
        long timestamp = 1_000_000_000L;
        detector.onSensorChanged(new float[]{0f, 0f, 9.8f}, timestamp,
                Sensor.TYPE_ACCELEROMETER);
        boolean triggered = false;
        for (int i = 0; i < 3; i++) {
            timestamp += 300_000_000L;
            detector.onSensorChanged(new float[]{0f, 0f, 9.8f}, timestamp,
                    Sensor.TYPE_ACCELEROMETER);
            timestamp += 20_000_000L;
            triggered = detector.onSensorChanged(
                    new float[]{impulse, 0f, 9.8f}, timestamp, Sensor.TYPE_ACCELEROMETER);
            for (int j = 0; j < 20; j++) {
                timestamp += 20_000_000L;
                triggered |= detector.onSensorChanged(new float[]{0f, 0f, 9.8f},
                        timestamp, Sensor.TYPE_ACCELEROMETER);
            }
        }
        return triggered;
    }
}
