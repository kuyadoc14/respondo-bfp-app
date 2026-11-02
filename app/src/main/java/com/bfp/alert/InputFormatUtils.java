package com.bfp.alert;

import android.text.InputFilter;
import android.text.InputType;
import android.widget.EditText;

/**
 * Common formatting and sanitation helpers for user inputs.
 */
public final class InputFormatUtils {

    private InputFormatUtils() {}

    /**
     * Applies standard blood type formatting: auto-uppercase and max 4 characters.
     */
    public static void setupBloodTypeInput(final EditText editText) {
        if (editText == null) return;
        editText.setFilters(new InputFilter[]{
                new InputFilter.AllCaps(),
                new InputFilter.LengthFilter(4)
        });
    }

    /**
     * Applies standard phone number formatting constraints (numbers, +, space, hyphen) and reasonable max length.
     */
    public static void setupPhoneInput(final EditText editText) {
        if (editText == null) return;
        editText.setInputType(InputType.TYPE_CLASS_PHONE);
        editText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(16)
        });
    }

    /**
     * Limits age input to positive digits up to 3 characters (e.g. 0 - 120).
     */
    public static void setupAgeInput(final EditText editText) {
        if (editText == null) return;
        editText.setInputType(InputType.TYPE_CLASS_NUMBER);
        editText.setFilters(new InputFilter[]{
                new InputFilter.LengthFilter(3)
        });
    }
}
