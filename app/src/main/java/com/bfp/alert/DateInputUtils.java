package com.bfp.alert;

import android.app.DatePickerDialog;
import android.content.Context;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.widget.EditText;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.Locale;

/**
 * Utility to format, validate, and attach auto-formatting and date-picker behaviors
 * to date input fields (specifically YYYY-MM-DD format for birthdates).
 */
public final class DateInputUtils {

    private static final String DATE_FORMAT = "yyyy-MM-dd";
    private static final int MAX_RAW_DIGITS = 8; // YYYYMMDD

    private DateInputUtils() {}

    /**
     * Attaches an automatic hyphen formatter and optional date-picker dialog to an EditText.
     * When user types "20060316", it automatically formats to "2006-03-16".
     * Also supports double-click/long-click or programmatically opening a calendar DatePicker.
     */
    public static void setupBirthdateInput(final EditText editText) {
        if (editText == null) return;

        // Max characters: "YYYY-MM-DD" is 10 chars
        editText.setFilters(new InputFilter[]{ new InputFilter.LengthFilter(10) });

        editText.addTextChangedListener(new TextWatcher() {
            private boolean isFormatting = false;
            private int prevLen = 0;

            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {
                prevLen = s != null ? s.length() : 0;
            }

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                if (isFormatting || s == null) return;

                String currentText = s.toString();
                // Strip everything except digits
                String digits = currentText.replaceAll("[^0-9]", "");
                if (digits.length() > MAX_RAW_DIGITS) {
                    digits = digits.substring(0, MAX_RAW_DIGITS);
                }

                StringBuilder formatted = new StringBuilder();
                int len = digits.length();
                for (int i = 0; i < len; i++) {
                    formatted.append(digits.charAt(i));
                    if ((i == 3 || i == 5) && (i != len - 1)) {
                        formatted.append('-');
                    }
                }

                // If user just typed the 4th or 6th digit, add the trailing dash so they see the format immediately
                if (currentText.length() > prevLen) {
                    if (len == 4 || len == 6) {
                        formatted.append('-');
                    }
                }

                String result = formatted.toString();
                if (!result.equals(currentText)) {
                    isFormatting = true;
                    editText.setText(result);
                    editText.setSelection(Math.min(result.length(), editText.getText().length()));
                    isFormatting = false;
                }
            }
        });
    }

    /**
     * Opens an Android DatePickerDialog to allow picking a birthdate visually,
     * populating the target EditText with YYYY-MM-DD.
     */
    public static void showDatePickerDialog(Context context, final EditText targetEditText) {
        if (context == null || targetEditText == null) return;

        Calendar calendar = Calendar.getInstance();

        // Attempt to parse existing text if available
        String current = targetEditText.getText().toString().trim();
        if (isValidDate(current)) {
            try {
                SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
                Date parsed = sdf.parse(current);
                if (parsed != null) {
                    calendar.setTime(parsed);
                }
            } catch (ParseException ignored) {}
        }

        int year = calendar.get(Calendar.YEAR);
        int month = calendar.get(Calendar.MONTH);
        int day = calendar.get(Calendar.DAY_OF_MONTH);

        DatePickerDialog dialog = new DatePickerDialog(context, (view, y, m, d) -> {
            String formatted = String.format(Locale.US, "%04d-%02d-%02d", y, m + 1, d);
            targetEditText.setText(formatted);
            targetEditText.setSelection(formatted.length());
        }, year, month, day);

        // Birthdate cannot be in the future
        dialog.getDatePicker().setMaxDate(System.currentTimeMillis());
        dialog.show();
    }

    /**
     * Checks if the given string strictly conforms to valid YYYY-MM-DD date.
     */
    public static boolean isValidDate(String text) {
        if (text == null || text.trim().length() != 10) return false;
        SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
        sdf.setLenient(false);
        try {
            Date d = sdf.parse(text.trim());
            return d != null;
        } catch (ParseException e) {
            return false;
        }
    }

    /**
     * Calculates age from a YYYY-MM-DD birthdate, or returns -1 if invalid or future.
     */
    public static int calculateAge(String birthdateStr) {
        if (!isValidDate(birthdateStr)) return -1;
        try {
            SimpleDateFormat sdf = new SimpleDateFormat(DATE_FORMAT, Locale.US);
            Date birth = sdf.parse(birthdateStr.trim());
            if (birth == null) return -1;

            Calendar dob = Calendar.getInstance();
            dob.setTime(birth);

            Calendar today = Calendar.getInstance();
            if (dob.after(today)) return -1;

            int age = today.get(Calendar.YEAR) - dob.get(Calendar.YEAR);
            if (today.get(Calendar.DAY_OF_YEAR) < dob.get(Calendar.DAY_OF_YEAR)) {
                age--;
            }
            return Math.max(0, age);
        } catch (Exception e) {
            return -1;
        }
    }
}
