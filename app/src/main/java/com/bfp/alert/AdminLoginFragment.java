package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class AdminLoginFragment extends Fragment {

    private FirebaseAuth mAuth;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.activity_admin_login,
                container,
                false);

        mAuth = FirebaseAuth.getInstance();

        boolean fromLogout = requireArguments().getBoolean("fromLogout", false);

        if (!fromLogout && mAuth.getCurrentUser() != null) {
            ((MainActivity) requireActivity()).openAdminDashboard();
            return view;
        }

        EditText etEmail = view.findViewById(R.id.etEmail);
        EditText etPassword = view.findViewById(R.id.etPassword);
        Button btnLogin = view.findViewById(R.id.btnLogin);
        Button btnDismiss = view.findViewById(R.id.btnDismiss);
        TextView tvError = view.findViewById(R.id.tvLoginError);

        btnDismiss.setOnClickListener(v -> {
            requireActivity().getSupportFragmentManager().popBackStack();
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            btnLogin.performClick();
            return true;
        });

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

        return view;
    }
}
