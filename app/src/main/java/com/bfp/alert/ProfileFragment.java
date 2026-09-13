package com.bfp.alert;

import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FrameLayout adminLoginOverlay;
    private FrameLayout adminLoginCardContainer;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_profile, container, false);

        adminLoginOverlay = view.findViewById(R.id.adminLoginOverlay);
        adminLoginCardContainer = view.findViewById(R.id.adminLoginCardContainer);

        view.findViewById(R.id.btnGoToAdmin)
                .setOnClickListener(v -> {
                    if (FirebaseAuth.getInstance().getCurrentUser() != null) {
                        ((MainActivity) requireActivity())
                                .openAdminDashboard();
                    } else {
                        showAdminLoginPopup();
                    }
                });

        adminLoginOverlay.setOnClickListener(v -> hideAdminLoginPopup());

        return view;
    }

    private void showAdminLoginPopup() {
        adminLoginCardContainer.removeAllViews();

        View loginView = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_admin_login, adminLoginCardContainer, false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        params.setMargins(24, 0, 24, 0);
        loginView.setLayoutParams(params);

        bindLoginCard(loginView);
        adminLoginCardContainer.addView(loginView);
        adminLoginOverlay.setVisibility(View.VISIBLE);
    }

    private void hideAdminLoginPopup() {
        adminLoginOverlay.setVisibility(View.GONE);
        if (adminLoginCardContainer.getChildCount() > 0) {
            adminLoginCardContainer.removeAllViews();
        }
    }

    private void bindLoginCard(View loginView) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        EditText etEmail = loginView.findViewById(R.id.etEmail);
        EditText etPassword = loginView.findViewById(R.id.etPassword);
        MaterialButton btnLogin = loginView.findViewById(R.id.btnLogin);
        MaterialButton btnDismiss = loginView.findViewById(R.id.btnDismiss);
        TextView tvError = loginView.findViewById(R.id.tvLoginError);

        btnDismiss.setOnClickListener(v -> hideAdminLoginPopup());

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            tvError.setText("");

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText("Please enter email and password.");
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnSuccessListener(token -> {
                                        String uid = mAuth.getCurrentUser() != null
                                                ? mAuth.getCurrentUser().getUid()
                                                : "";
                                        if (!uid.isEmpty()) {
                                            Map<String, Object> data = new HashMap<>();
                                            data.put("fcmToken", token);
                                            FirebaseFirestore.getInstance()
                                                    .collection("admin_tokens")
                                                    .document(uid)
                                                    .set(data);
                                        }
                                    });

                            hideAdminLoginPopup();
                            ((MainActivity) requireActivity()).openAdminDashboard();
                        } else {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Sign In");
                            String msg = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Login failed.";

                            if (msg != null && msg.contains("password")) {
                                tvError.setText("Incorrect email or password.");
                            } else if (msg != null && msg.contains("email")) {
                                tvError.setText("Invalid email format.");
                            } else {
                                tvError.setText("Login failed. Try again.");
                            }
                        }
                    });
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            btnLogin.performClick();
            return true;
        });
    }
}