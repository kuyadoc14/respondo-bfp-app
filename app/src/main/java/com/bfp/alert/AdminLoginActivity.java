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

        // Only skip login if we came here from a
        // non-logout source AND user is logged in
        boolean fromLogout = getIntent()
                .getBooleanExtra("fromLogout", false);

        if (!fromLogout
                && mAuth.getCurrentUser() != null) {
            startActivity(new Intent(
                    this, AdminDashboardActivity.class));
            finish();
            return;
        }

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button   btnLogin   = findViewById(R.id.btnLogin);
        Button   btnDismiss = findViewById(R.id.btnDismiss);
        TextView tvError    = findViewById(R.id.tvLoginError);

        btnDismiss.setOnClickListener(v -> finish());

        etPassword.setOnEditorActionListener(
                (v, actionId, event) -> {
                    btnLogin.performClick();
                    return true;
                });

        btnLogin.setOnClickListener(v -> {
            String email    =
                    etEmail.getText().toString().trim();
            String password =
                    etPassword.getText().toString().trim();
            tvError.setText("");

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText(
                        "Please enter email and password.");
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");

            mAuth.signInWithEmailAndPassword(
                            email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            // Save FCM token
                            FirebaseMessaging.getInstance()
                                    .getToken()
                                    .addOnSuccessListener(token -> {
                                        String uid = mAuth
                                                .getCurrentUser().getUid();
                                        Map<String, Object> data =
                                                new HashMap<>();
                                        data.put("fcmToken", token);
                                        FirebaseFirestore.getInstance()
                                                .collection("admin_tokens")
                                                .document(uid)
                                                .set(data);
                                    });

                            // Go to dashboard cleanly
                            Intent intent = new Intent(
                                    this, AdminDashboardActivity.class);
                            intent.addFlags(
                                    Intent.FLAG_ACTIVITY_CLEAR_TOP |
                                            Intent.FLAG_ACTIVITY_SINGLE_TOP);
                            startActivity(intent);
                            finish();

                        } else {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Sign In");

                            String msg = task.getException()
                                    != null
                                    ? task.getException()
                                    .getMessage()
                                    : "Login failed.";

                            if (msg != null &&
                                    msg.contains("password")) {
                                tvError.setText(
                                        "Incorrect email or password.");
                            } else if (msg != null &&
                                    msg.contains("email")) {
                                tvError.setText(
                                        "Invalid email format.");
                            } else {
                                tvError.setText(
                                        "Login failed. Try again.");
                            }
                        }
                    });
        });
    }
}