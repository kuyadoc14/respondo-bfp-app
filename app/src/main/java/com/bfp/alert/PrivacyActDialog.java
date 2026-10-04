package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class PrivacyActDialog extends BottomSheetDialogFragment {

    public interface OnPrivacyConsentListener {
        void onConsentGranted();
        void onConsentDeclined();
    }

    private OnPrivacyConsentListener listener;

    public static PrivacyActDialog newInstance() {
        return new PrivacyActDialog();
    }

    public void setOnPrivacyConsentListener(OnPrivacyConsentListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setStyle(STYLE_NORMAL, R.style.Theme_BFPAlert_BottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_privacy_act, container, false);

        CheckBox chkAgree = view.findViewById(R.id.chkAgreePrivacy);
        MaterialButton btnAgree = view.findViewById(R.id.btnAgreePrivacy);
        MaterialButton btnDecline = view.findViewById(R.id.btnDeclinePrivacy);

        chkAgree.setOnCheckedChangeListener((buttonView, isChecked) -> {
            btnAgree.setAlpha(isChecked ? 1.0f : 0.6f);
        });

        // Initial state
        btnAgree.setAlpha(0.6f);

        btnAgree.setOnClickListener(v -> {
            if (!chkAgree.isChecked()) {
                Toast.makeText(requireContext(),
                        "Please check the agreement box to indicate your consent under RA 10173.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            String dateStr = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
            UserProfileManager.setPrivacyConsentAccepted(requireContext(), true, dateStr);

            dismiss();
            if (listener != null) {
                listener.onConsentGranted();
            }
        });

        btnDecline.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onConsentDeclined();
            }
        });

        return view;
    }
}
