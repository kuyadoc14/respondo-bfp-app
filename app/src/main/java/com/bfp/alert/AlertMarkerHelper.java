package com.bfp.alert;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;

import com.google.android.gms.maps.model.BitmapDescriptor;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;

/**
 * Generates custom high-contrast map markers:
 * - Active alert: bright red with glowing halo effect, rendered in front (Z-Index = 100).
 * - Deployed alert: vibrant cyan/blue with beacon glow, rendered in front (Z-Index = 90).
 * - Resolved alert: dark forest green, semi-transparent (alpha = 0.60), rendered in back (Z-Index = 1).
 */
public final class AlertMarkerHelper {

    public static final float ACTIVE_Z_INDEX = 100f;
    public static final float DEPLOYED_Z_INDEX = 90f;
    public static final float RESOLVED_Z_INDEX = 1f;

    public static final float ACTIVE_ALPHA = 1.0f;
    public static final float DEPLOYED_ALPHA = 1.0f;
    public static final float RESOLVED_ALPHA = 0.60f;

    public static final float ANCHOR_U = 0.5f;
    public static final float ANCHOR_V = 52f / 58f; // Tip at y=52dp in a 58dp tall bitmap

    private static BitmapDescriptor activeIcon = null;
    private static BitmapDescriptor deployedIcon = null;
    private static BitmapDescriptor resolvedIcon = null;

    private AlertMarkerHelper() {}

    public static synchronized BitmapDescriptor getActiveMarkerIcon(Context context) {
        if (activeIcon == null) {
            activeIcon = BitmapDescriptorFactory.fromBitmap(createActiveMarkerBitmap(context));
        }
        return activeIcon;
    }

    public static synchronized BitmapDescriptor getDeployedMarkerIcon(Context context) {
        if (deployedIcon == null) {
            deployedIcon = BitmapDescriptorFactory.fromBitmap(createDeployedMarkerBitmap(context));
        }
        return deployedIcon;
    }

    public static synchronized BitmapDescriptor getResolvedMarkerIcon(Context context) {
        if (resolvedIcon == null) {
            resolvedIcon = BitmapDescriptorFactory.fromBitmap(createResolvedMarkerBitmap(context));
        }
        return resolvedIcon;
    }

    private static Bitmap createActiveMarkerBitmap(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.round(48f * density);
        int h = Math.round(58f * density);
        float cx = w / 2f;
        float tipY = 52f * density;
        float headCenterY = 20f * density;
        float r = 12f * density;
        float d = tipY - headCenterY;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Subtle glowing effect (multi-stop radial gradient halo)
        float glowRadius = 19f * density;
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new RadialGradient(
                cx, headCenterY, glowRadius,
                new int[]{0x70EF4444, 0x40EF4444, 0x15EF4444, 0x00EF4444},
                new float[]{0.0f, 0.45f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, headCenterY, glowRadius, glowPaint);

        // 2. Subtle ground contact shadow under tip
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x35000000);
        canvas.drawOval(new RectF(cx - 4f * density, tipY - 1.5f * density, cx + 4f * density, tipY + 2.5f * density), shadowPaint);

        // 3. Teardrop pin geometry
        float phi = (float) Math.toDegrees(Math.acos(r / d));
        float startAngle = 90f + phi;
        float sweepAngle = 360f - 2f * phi;

        Path pinPath = new Path();
        pinPath.arcTo(new RectF(cx - r, headCenterY - r, cx + r, headCenterY + r), startAngle, sweepAngle, false);
        pinPath.lineTo(cx, tipY);
        pinPath.close();

        // 4. Emergency Red Gradient Fill
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new LinearGradient(
                cx, headCenterY - r,
                cx, tipY,
                0xFFFF3B30, 0xFFDC2626,
                Shader.TileMode.CLAMP));
        canvas.drawPath(pinPath, fillPaint);

        // 5. Crisp white outline
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.6f * density);
        strokePaint.setColor(0xFFFFFFFF);
        canvas.drawPath(pinPath, strokePaint);

        // 6. Inner white emblem core
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, headCenterY, 5f * density, innerPaint);

        // 7. Micro inner red beacon dot
        Paint coreDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coreDotPaint.setStyle(Paint.Style.FILL);
        coreDotPaint.setColor(0xFFDC2626);
        canvas.drawCircle(cx, headCenterY, 2.2f * density, coreDotPaint);

        return bitmap;
    }

    private static Bitmap createDeployedMarkerBitmap(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.round(48f * density);
        int h = Math.round(58f * density);
        float cx = w / 2f;
        float tipY = 52f * density;
        float headCenterY = 20f * density;
        float r = 12f * density;
        float d = tipY - headCenterY;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Vibrant Orange emergency beacon glow
        float glowRadius = 19f * density;
        Paint glowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        glowPaint.setShader(new RadialGradient(
                cx, headCenterY, glowRadius,
                new int[]{0x70EA580C, 0x40EA580C, 0x15EA580C, 0x00EA580C},
                new float[]{0.0f, 0.45f, 0.75f, 1.0f},
                Shader.TileMode.CLAMP));
        canvas.drawCircle(cx, headCenterY, glowRadius, glowPaint);

        // 2. Contact shadow
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x35000000);
        canvas.drawOval(new RectF(cx - 4f * density, tipY - 1.5f * density, cx + 4f * density, tipY + 2.5f * density), shadowPaint);

        // 3. Teardrop pin geometry
        float phi = (float) Math.toDegrees(Math.acos(r / d));
        float startAngle = 90f + phi;
        float sweepAngle = 360f - 2f * phi;

        Path pinPath = new Path();
        pinPath.arcTo(new RectF(cx - r, headCenterY - r, cx + r, headCenterY + r), startAngle, sweepAngle, false);
        pinPath.lineTo(cx, tipY);
        pinPath.close();

        // 4. Vibrant Safety Orange Gradient Fill
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new LinearGradient(
                cx, headCenterY - r,
                cx, tipY,
                0xFFFB923C, 0xFFEA580C,
                Shader.TileMode.CLAMP));
        canvas.drawPath(pinPath, fillPaint);

        // 5. White outline
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.6f * density);
        strokePaint.setColor(0xFFFFFFFF);
        canvas.drawPath(pinPath, strokePaint);

        // 6. Inner white emblem core
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0xFFFFFFFF);
        canvas.drawCircle(cx, headCenterY, 5f * density, innerPaint);

        // 7. Micro inner orange dot
        Paint coreDotPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        coreDotPaint.setStyle(Paint.Style.FILL);
        coreDotPaint.setColor(0xFFC2410C);
        canvas.drawCircle(cx, headCenterY, 2.2f * density, coreDotPaint);

        return bitmap;
    }

    private static Bitmap createResolvedMarkerBitmap(Context context) {
        float density = context.getResources().getDisplayMetrics().density;
        int w = Math.round(48f * density);
        int h = Math.round(58f * density);
        float cx = w / 2f;
        float tipY = 52f * density;
        float headCenterY = 22f * density;
        float r = 10.5f * density; // Subdued, slightly more compact pin
        float d = tipY - headCenterY;

        Bitmap bitmap = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);

        // 1. Subtle ground contact shadow under tip
        Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        shadowPaint.setColor(0x20000000);
        canvas.drawOval(new RectF(cx - 3.5f * density, tipY - 1f * density, cx + 3.5f * density, tipY + 2f * density), shadowPaint);

        // 2. Teardrop pin geometry
        float phi = (float) Math.toDegrees(Math.acos(r / d));
        float startAngle = 90f + phi;
        float sweepAngle = 360f - 2f * phi;

        Path pinPath = new Path();
        pinPath.arcTo(new RectF(cx - r, headCenterY - r, cx + r, headCenterY + r), startAngle, sweepAngle, false);
        pinPath.lineTo(cx, tipY);
        pinPath.close();

        // 3. Dark Forest Green Fill
        Paint fillPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        fillPaint.setStyle(Paint.Style.FILL);
        fillPaint.setShader(new LinearGradient(
                cx, headCenterY - r,
                cx, tipY,
                0xFF2E7D32, 0xFF1B5E20,
                Shader.TileMode.CLAMP));
        canvas.drawPath(pinPath, fillPaint);

        // 4. Subtle semi-transparent stroke
        Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        strokePaint.setStyle(Paint.Style.STROKE);
        strokePaint.setStrokeWidth(1.2f * density);
        strokePaint.setColor(0xB3FFFFFF);
        canvas.drawPath(pinPath, strokePaint);

        // 5. Inner muted checkmark / dot
        Paint innerPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
        innerPaint.setStyle(Paint.Style.FILL);
        innerPaint.setColor(0xCCFFFFFF);
        canvas.drawCircle(cx, headCenterY, 3.8f * density, innerPaint);

        return bitmap;
    }
}
