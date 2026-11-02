package com.bfp.alert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

final class AlertDensityMapUtils {

    private static final double AREA_CELL_SIZE_DEGREES = 0.001;

    private AlertDensityMapUtils() {
    }

    static List<AlertGroup> groupAlerts(
            List<Map<String, Object>> alerts,
            List<String> alertIds) {
        Map<String, MutableAlertGroup> groups = new LinkedHashMap<>();
        int itemCount = Math.min(alerts.size(), alertIds.size());

        for (int i = 0; i < itemCount; i++) {
            Map<String, Object> alert = alerts.get(i);
            if (alert == null) continue;

            Object latitudeValue = alert.get("latitude");
            Object longitudeValue = alert.get("longitude");
            if (!(latitudeValue instanceof Number)
                    || !(longitudeValue instanceof Number)) {
                continue;
            }

            double latitude = ((Number) latitudeValue).doubleValue();
            double longitude = ((Number) longitudeValue).doubleValue();
            if (!Double.isFinite(latitude) || !Double.isFinite(longitude)
                    || latitude < -90 || latitude > 90
                    || longitude < -180 || longitude > 180
                    || (latitude == 0 && longitude == 0)) {
                continue;
            }

            long latitudeCell = Math.round(latitude / AREA_CELL_SIZE_DEGREES);
            long longitudeCell = Math.round(longitude / AREA_CELL_SIZE_DEGREES);
            String cellKey = latitudeCell + ":" + longitudeCell;
            MutableAlertGroup group = groups.get(cellKey);
            if (group == null) {
                group = new MutableAlertGroup();
                groups.put(cellKey, group);
            }
            group.add(latitude, longitude, alertIds.get(i));
        }

        List<AlertGroup> result = new ArrayList<>(groups.size());
        for (MutableAlertGroup group : groups.values()) {
            result.add(group.toAlertGroup());
        }
        return result;
    }

    static BitmapDescriptor createDensityIcon(Context context, int count) {
        float density = context.getResources().getDisplayMetrics().density;
        int diameterDp = getDiameterDp(count);
        int diameter = Math.round(diameterDp * density);
        float center = diameter / 2f;
        float radius = center - 2.5f * density;

        int color = count >= 6 ? 0xFFB71C1C
                : count >= 3 ? 0xFFF57C00
                : 0xFFD93025;
        Bitmap bitmap = Bitmap.createBitmap(diameter, diameter, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        Paint circlePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        circlePaint.setColor(color);
        canvas.drawCircle(center, center, radius, circlePaint);

        circlePaint.setStyle(Paint.Style.STROKE);
        circlePaint.setStrokeWidth(2.5f * density);
        circlePaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(center, center, radius, circlePaint);

        String label = count > 99 ? "99+" : String.valueOf(count);
        Paint textPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        textPaint.setColor(0xFFFFFFFF);
        textPaint.setTextAlign(Paint.Align.CENTER);
        textPaint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        textPaint.setTextSize((count > 99 ? 12 : 14) * density);
        Paint.FontMetrics fontMetrics = textPaint.getFontMetrics();
        float textBaseline = center - (fontMetrics.ascent + fontMetrics.descent) / 2f;
        canvas.drawText(label, center, textBaseline, textPaint);

        return BitmapDescriptorFactory.fromBitmap(bitmap);
    }

    static int getDiameterDp(int count) {
        return count >= 10 ? 60 : count >= 5 ? 54 : count >= 2 ? 48 : 42;
    }

    static final class AlertGroup {
        private final double latitude;
        private final double longitude;
        private final List<String> alertIds;

        private AlertGroup(double latitude, double longitude, List<String> alertIds) {
            this.latitude = latitude;
            this.longitude = longitude;
            this.alertIds = Collections.unmodifiableList(new ArrayList<>(alertIds));
        }

        double getLatitude() {
            return latitude;
        }

        double getLongitude() {
            return longitude;
        }

        List<String> getAlertIds() {
            return alertIds;
        }

        int getCount() {
            return alertIds.size();
        }
    }

    private static final class MutableAlertGroup {
        private double latitudeTotal;
        private double longitudeTotal;
        private final List<String> alertIds = new ArrayList<>();

        private void add(double latitude, double longitude, String alertId) {
            latitudeTotal += latitude;
            longitudeTotal += longitude;
            alertIds.add(alertId);
        }

        private AlertGroup toAlertGroup() {
            return new AlertGroup(
                    latitudeTotal / alertIds.size(),
                    longitudeTotal / alertIds.size(),
                    alertIds);
        }
    }
}
