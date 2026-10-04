package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;

public class RoleSelectionDialog extends BottomSheetDialogFragment {

    private static final String ARG_ALERT_ID = "alert_id";

    public interface OnRoleSelectedListener {
        void onRoleSelected(String role, UserProfile profile);
        void onRoleSkipped();
    }

    private String alertId;
    private OnRoleSelectedListener listener;
    private UserProfile currentProfile;

    public static RoleSelectionDialog newInstance(String alertId) {
        RoleSelectionDialog dialog = new RoleSelectionDialog();
        Bundle args = new Bundle();
        args.putString(ARG_ALERT_ID, alertId);
        dialog.setArguments(args);
        return dialog;
    }

    public void setOnRoleSelectedListener(OnRoleSelectedListener listener) {
        this.listener = listener;
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (getArguments() != null) {
            alertId = getArguments().getString(ARG_ALERT_ID);
        }
        setStyle(STYLE_NORMAL, R.style.Theme_BFPAlert_BottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.dialog_role_selection, container, false);

        currentProfile = UserProfileManager.getLocalProfile(requireContext());
        TextView tvVictimSubtext = view.findViewById(R.id.tvVictimSubtext);
        if (currentProfile != null && !currentProfile.getFullName().isEmpty()) {
            tvVictimSubtext.setText("Auto-fills profile for " + currentProfile.getFullName() + " into Patient 1.");
        }

        view.findViewById(R.id.cardRoleVictim).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onRoleSelected("victim", currentProfile);
            }
        });

        view.findViewById(R.id.cardRoleBystander).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onRoleSelected("bystander", currentProfile);
            }
        });

        view.findViewById(R.id.btnSkipRole).setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onRoleSkipped();
            }
        });

        return view;
    }
}
