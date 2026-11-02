package com.bfp.alert;

import android.view.View;
import android.view.ViewGroup;

import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

/**
 * Utility to adapt layouts cleanly to system bars (status bar / camera cutouts at top,
 * and navigation bar / gesture handle at bottom) on Android 15+ edge-to-edge screens.
 */
public class SystemBarUtils {

    /**
     * Applies top system bar inset (status bar / notch) as extra padding to the given header view.
     * The background of the view stays edge-to-edge, while the text/icons inside are pushed safely
     * below the system status bar and notification icons.
     */
    public static void applyTopInsetPadding(View view) {
        if (view == null) return;
        final int initialTop = view.getPaddingTop();
        final int initialLeft = view.getPaddingLeft();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(initialLeft, initialTop + insets.top, initialRight, initialBottom);
            return windowInsets;
        });

        requestInsetsWhenAttached(view);
    }

    /**
     * Applies top system bar inset as top margin (for floating elements like badges).
     */
    public static void applyTopInsetMargin(View view) {
        if (view == null) return;
        ViewGroup.LayoutParams params = view.getLayoutParams();
        if (!(params instanceof ViewGroup.MarginLayoutParams)) return;
        final int initialMargin = ((ViewGroup.MarginLayoutParams) params).topMargin;

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            ViewGroup.MarginLayoutParams lp = (ViewGroup.MarginLayoutParams) v.getLayoutParams();
            if (lp != null) {
                lp.topMargin = initialMargin + insets.top;
                v.setLayoutParams(lp);
            }
            return windowInsets;
        });

        requestInsetsWhenAttached(view);
    }

    /**
     * Applies bottom system bar inset (gesture bar or 3-button navigation bar) as extra padding
     * so that bottom navigation bars, buttons, or lists are not covered by the phone's navigation.
     */
    public static void applyBottomInsetPadding(View view) {
        if (view == null) return;
        final int initialTop = view.getPaddingTop();
        final int initialLeft = view.getPaddingLeft();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(initialLeft, initialTop, initialRight, initialBottom + insets.bottom);
            return windowInsets;
        });

        requestInsetsWhenAttached(view);
    }

    /**
     * Applies both top and bottom system bar insets to a view.
     */
    public static void applySystemBarsPadding(View view) {
        if (view == null) return;
        final int initialTop = view.getPaddingTop();
        final int initialLeft = view.getPaddingLeft();
        final int initialRight = view.getPaddingRight();
        final int initialBottom = view.getPaddingBottom();

        ViewCompat.setOnApplyWindowInsetsListener(view, (v, windowInsets) -> {
            Insets insets = windowInsets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(
                    initialLeft,
                    initialTop + insets.top,
                    initialRight,
                    initialBottom + insets.bottom
            );
            return windowInsets;
        });

        requestInsetsWhenAttached(view);
    }

    private static void requestInsetsWhenAttached(View view) {
        if (view.isAttachedToWindow()) {
            ViewCompat.requestApplyInsets(view);
        } else {
            view.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener() {
                @Override
                public void onViewAttachedToWindow(View v) {
                    v.removeOnAttachStateChangeListener(this);
                    ViewCompat.requestApplyInsets(v);
                }

                @Override
                public void onViewDetachedFromWindow(View v) {}
            });
        }
    }
}
