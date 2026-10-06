package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;
import android.util.Log;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;

public class RoleSelectionDialog extends BottomSheetDialogFragment {

    private static final String ARG_ALERT_ID = "alert_id";

    public interface OnRoleSelectedListener {
        void onRoleSelected(String role, UserProfile profile);
        void onRoleSkipped();
    }

    private String alertId;
    private OnRoleSelectedListener listener;
    private UserProfile currentProfile;
    private String profileOwnerUid;
    private TextView tvVictimSubtext;
    private View cardRoleVictim;
    private View cardRoleBystander;

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

        currentProfile = new UserProfile();
        tvVictimSubtext = view.findViewById(R.id.tvVictimSubtext);
        cardRoleVictim = view.findViewById(R.id.cardRoleVictim);
        cardRoleBystander = view.findViewById(R.id.cardRoleBystander);
        loadSignedInProfile();

        cardRoleVictim.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onRoleSelected("victim", getCurrentAccountProfile());
            }
        });

        cardRoleBystander.setOnClickListener(v -> {
            dismiss();
            if (listener != null) {
                listener.onRoleSelected("bystander", getCurrentAccountProfile());
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

    private void loadSignedInProfile() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            tvVictimSubtext.setText("Auto-fill is available when signed in.");
            return;
        }

        profileOwnerUid = user.getUid();
        tvVictimSubtext.setText("Loading your saved profile...");
        cardRoleVictim.setEnabled(false);
        cardRoleBystander.setEnabled(false);

        FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(profileOwnerUid)
                .get()
                .addOnSuccessListener(document -> {
                    if (!isAdded()) return;
                    if (isCurrentProfileOwner()) {
                        if (document.exists() && document.getData() != null) {
                            currentProfile = UserProfile.fromMap(document.getData());
                        }
                        if (!currentProfile.getFullName().isEmpty()) {
                            tvVictimSubtext.setText("Auto-fills profile for "
                                    + currentProfile.getFullName() + " into Patient 1.");
                        } else {
                            tvVictimSubtext.setText("No saved profile found. You can enter details manually.");
                        }
                    } else {
                        currentProfile = new UserProfile();
                        tvVictimSubtext.setText("Signed-out profile won't be used. Enter details manually.");
                    }
                    cardRoleVictim.setEnabled(true);
                    cardRoleBystander.setEnabled(true);
                })
                .addOnFailureListener(error -> {
                    Log.e("RoleSelectionDialog", "Failed to load signed-in profile", error);
                    if (!isAdded()) return;
                    currentProfile = new UserProfile();
                    tvVictimSubtext.setText("Profile couldn't be loaded. You can enter details manually.");
                    cardRoleVictim.setEnabled(true);
                    cardRoleBystander.setEnabled(true);
                    Toast.makeText(requireContext(),
                            "Saved profile unavailable; report can still be entered manually.",
                            Toast.LENGTH_LONG).show();
                });
    }

    @Nullable
    private UserProfile getCurrentAccountProfile() {
        return isCurrentProfileOwner() ? currentProfile : null;
    }

    private boolean isCurrentProfileOwner() {
        FirebaseUser currentUser = FirebaseAuth.getInstance().getCurrentUser();
        return profileOwnerUid != null && currentUser != null
                && profileOwnerUid.equals(currentUser.getUid());
    }
}
