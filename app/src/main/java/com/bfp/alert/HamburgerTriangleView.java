package com.bfp.alert;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.util.AttributeSet;
import android.view.View;
import android.view.animation.DecelerateInterpolator;

import androidx.annotation.Nullable;

/**
 * Custom animated icon view that smoothly morphs between 3 horizontal bars (hamburger)
 * and an equilateral triangle when opened / clicked.
 */
public class HamburgerTriangleView extends View {

    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private float progress = 0.0f; // 0.0 = hamburger, 1.0 = triangle
    private boolean isOpen = false;
    private ValueAnimator animator;

    public HamburgerTriangleView(Context context) {
        super(context);
        init();
    }

    public HamburgerTriangleView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public HamburgerTriangleView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setStrokeJoin(Paint.Join.ROUND);
        paint.setColor(Color.WHITE);
        float density = getResources().getDisplayMetrics().density;
        paint.setStrokeWidth(2.6f * density);
    }

    public void setLineColor(int color) {
        paint.setColor(color);
        invalidate();
    }

    public boolean isOpen() {
        return isOpen;
    }

    public void setOpen(boolean open, boolean animate) {
        if (this.isOpen == open && animator == null) return;
        this.isOpen = open;

        if (animator != null) {
            animator.cancel();
        }

        float target = open ? 1.0f : 0.0f;
        if (!animate) {
            progress = target;
            invalidate();
            return;
        }

        animator = ValueAnimator.ofFloat(progress, target);
        animator.setDuration(300);
        animator.setInterpolator(new DecelerateInterpolator(1.6f));
        animator.addUpdateListener(animation -> {
            progress = (float) animation.getAnimatedValue();
            invalidate();
        });
        animator.start();
    }

    public void toggle(boolean animate) {
        setOpen(!isOpen, animate);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        float padLeft = getPaddingLeft();
        float padRight = getPaddingRight();
        float padTop = getPaddingTop();
        float padBottom = getPaddingBottom();

        float width = getWidth() - padLeft - padRight;
        float height = getHeight() - padTop - padBottom;
        if (width <= 0 || height <= 0) return;

        float cx = padLeft + width / 2.0f;
        float cy = padTop + height / 2.0f;

        // Hamburger Coordinates (progress = 0)
        float hLeft = padLeft + width * 0.16f;
        float hRight = padLeft + width * 0.84f;
        float hTopY = padTop + height * 0.22f;
        float hMidY = cy;
        float hBotY = padTop + height * 0.78f;

        // Triangle Coordinates (progress = 1) - Pointing down or apex top
        float tApexX = cx;
        float tApexY = padTop + height * 0.16f;
        float tLeftX = padLeft + width * 0.18f;
        float tRightX = padLeft + width * 0.82f;
        float tBaseY = padTop + height * 0.80f;

        // Line 1: Morph from Top Bar -> Left Triangle Leg (Apex to Bottom-Left)
        float l1_x1 = lerp(hLeft, tApexX, progress);
        float l1_y1 = lerp(hTopY, tApexY, progress);
        float l1_x2 = lerp(hRight, tLeftX, progress);
        float l1_y2 = lerp(hTopY, tBaseY, progress);
        canvas.drawLine(l1_x1, l1_y1, l1_x2, l1_y2, paint);

        // Line 2: Morph from Middle Bar -> Right Triangle Leg (Apex to Bottom-Right)
        float l2_x1 = lerp(hLeft, tApexX, progress);
        float l2_y1 = lerp(hMidY, tApexY, progress);
        float l2_x2 = lerp(hRight, tRightX, progress);
        float l2_y2 = lerp(hMidY, tBaseY, progress);
        canvas.drawLine(l2_x1, l2_y1, l2_x2, l2_y2, paint);

        // Line 3: Morph from Bottom Bar -> Triangle Base (Bottom-Left to Bottom-Right)
        float l3_x1 = lerp(hLeft, tLeftX, progress);
        float l3_y1 = lerp(hBotY, tBaseY, progress);
        float l3_x2 = lerp(hRight, tRightX, progress);
        float l3_y2 = lerp(hBotY, tBaseY, progress);
        canvas.drawLine(l3_x1, l3_y1, l3_x2, l3_y2, paint);
    }

    private float lerp(float a, float b, float f) {
        return a + (b - a) * f;
    }
}
