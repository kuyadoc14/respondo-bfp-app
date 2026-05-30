package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class AdminLoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        mAuth = FirebaseAuth.getInstance();

        // If already logged in skip popup and go straight to dashboard
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, AdminDashboardActivity.class));
            finish();
            return;
        }

        EditText  etEmail    = findViewById(R.id.etEmail);
        EditText  etPassword = findViewById(R.id.etPassword);
        Button    btnLogin   = findViewById(R.id.btnLogin);
        Button    btnDismiss = findViewById(R.id.btnDismiss);
        TextView  tvError    = findViewById(R.id.tvLoginError);

        // Cancel button closes the popup
        btnDismiss.setOnClickListener(v -> finish());

        // Allow Enter key on password field
        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            btnLogin.performClick();
            return true;
        });

        btnLogin.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            tvError.setText("");

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText("Please enter email and password.");
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Logging in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            // Save FCM token for push notifications
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnSuccessListener(token -> {
                                        String uid = mAuth.getCurrentUser().getUid();
                                        Map<String, Object> data = new HashMap<>();
                                        data.put("fcmToken", token);
                                        FirebaseFirestore.getInstance()
                                                .collection("admin_tokens")
                                                .document(uid)
                                                .set(data);
                                    });

                            // Close popup then open dashboard
                            finish();
                            startActivity(new Intent(this,
                                    AdminDashboardActivity.class));

                        } else {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Login");

                            String code = task.getException() != null
                                    ? task.getException().getClass().getSimpleName()
                                    : "";
                            if (task.getException() != null &&
                                    task.getException().getMessage() != null &&
                                    task.getException().getMessage()
                                            .contains("password")) {
                                tvError.setText("Incorrect email or password.");
                            } else if (task.getException() != null &&
                                    task.getException().getMessage() != null &&
                                    task.getException().getMessage()
                                            .contains("email")) {
                                tvError.setText("Invalid email format.");
                            } else {
                                tvError.setText("Login failed. Try again.");
                            }
                        }
                    });
        });
    }
}