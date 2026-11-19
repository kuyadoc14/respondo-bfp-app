package com.bfp.alert;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.os.CountDownTimer;
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

    private static final String PREF_SECURITY = "admin_auth_security";
    private static final String KEY_FAILED_ATTEMPTS = "failed_attempts";
    private static final String KEY_LOCKOUT_UNTIL = "lockout_until";
    private static final int MAX_FAILED_ATTEMPTS = 5;
    private static final long LOCKOUT_DURATION_MS = 30000; // 30 seconds

    private FirebaseAuth mAuth;
    private Button btnLogin;
    private TextView tvError;
    private CountDownTimer lockoutTimer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        mAuth = FirebaseAuth.getInstance();

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        btnLogin   = findViewById(R.id.btnLogin);
        Button   btnDismiss = findViewById(R.id.btnDismiss);
        tvError    = findViewById(R.id.tvLoginError);

        btnDismiss.setOnClickListener(v -> {
            finish();
            overridePendingTransition(0, 0);
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            btnLogin.performClick();
            return true;
        });

        // Check if previously locked out
        checkLockoutState();

        btnLogin.setOnClickListener(v -> {
            if (isLockedOut()) {
                checkLockoutState();
                return;
            }

            String email    = etEmail.getText().toString().trim();
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
                            AdminAccess.check(mAuth.getCurrentUser(), (isAdmin, error) -> {
                                if (isFinishing()) return;
                                if (error != null || !isAdmin) {
                                    mAuth.signOut();
                                    btnLogin.setEnabled(true);
                                    btnLogin.setText("Sign In");
                                    tvError.setText(error != null
                                            ? AdminAccess.getVerificationErrorMessage(error)
                                            : "This account is not set up as an administrator.");
                                    return;
                                }

                                // Reset security lockout on verified admin login
                                resetFailedAttempts();
                                saveAdminToken();
                                openDashboard();
                            });

                        } else {
                            recordFailedAttempt();

                            if (isLockedOut()) {
                                checkLockoutState();
                                return;
                            }

                            btnLogin.setEnabled(true);
                            btnLogin.setText("Sign In");

                            int remaining = getRemainingAttempts();
                            String msg = task.getException() != null ? task.getException().getMessage() : "Login failed.";

                            String baseMsg;
                            if (msg != null && msg.contains("password")) {
                                baseMsg = "Incorrect email or password.";
                            } else if (msg != null && msg.contains("email")) {
                                baseMsg = "Invalid email format.";
                            } else {
                                baseMsg = "Login failed. Try again.";
                            }
                            tvError.setText(baseMsg + " (" + remaining + " attempt" + (remaining != 1 ? "s" : "") + " remaining)");
                        }
                    });
        });

        AdminAccess.check(mAuth.getCurrentUser(), (isAdmin, error) -> {
            if (isFinishing()) return;
            if (error != null) {
                tvError.setText(AdminAccess.getVerificationErrorMessage(error));
            } else if (isAdmin) {
                openDashboard();
            }
        });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
            lockoutTimer = null;
        }
    }

    private boolean isLockedOut() {
        SharedPreferences sp = getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE);
        long lockoutUntil = sp.getLong(KEY_LOCKOUT_UNTIL, 0);
        return System.currentTimeMillis() < lockoutUntil;
    }

    private int getRemainingAttempts() {
        SharedPreferences sp = getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE);
        int failed = sp.getInt(KEY_FAILED_ATTEMPTS, 0);
        return Math.max(0, MAX_FAILED_ATTEMPTS - failed);
    }

    private void recordFailedAttempt() {
        SharedPreferences sp = getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE);
        int failed = sp.getInt(KEY_FAILED_ATTEMPTS, 0) + 1;
        SharedPreferences.Editor editor = sp.edit().putInt(KEY_FAILED_ATTEMPTS, failed);
        if (failed >= MAX_FAILED_ATTEMPTS) {
            long lockoutUntil = System.currentTimeMillis() + LOCKOUT_DURATION_MS;
            editor.putLong(KEY_LOCKOUT_UNTIL, lockoutUntil);
        }
        editor.apply();
    }

    private void resetFailedAttempts() {
        SharedPreferences sp = getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE);
        sp.edit().remove(KEY_FAILED_ATTEMPTS).remove(KEY_LOCKOUT_UNTIL).apply();
        if (lockoutTimer != null) {
            lockoutTimer.cancel();
            lockoutTimer = null;
        }
    }

    private void checkLockoutState() {
        SharedPreferences sp = getSharedPreferences(PREF_SECURITY, Context.MODE_PRIVATE);
        long lockoutUntil = sp.getLong(KEY_LOCKOUT_UNTIL, 0);
        long remainingMs = lockoutUntil - System.currentTimeMillis();

        if (remainingMs > 0) {
            btnLogin.setEnabled(false);
            if (lockoutTimer != null) lockoutTimer.cancel();

            lockoutTimer = new CountDownTimer(remainingMs, 1000) {
                @Override
                public void onTick(long millisUntilFinished) {
                    long sec = Math.max(1, millisUntilFinished / 1000);
                    btnLogin.setText("Locked (" + sec + "s)");
                    tvError.setText("Too many failed attempts. Login locked for " + sec + "s.");
                }

                @Override
                public void onFinish() {
                    resetFailedAttempts();
                    btnLogin.setEnabled(true);
                    btnLogin.setText("Sign In");
                    tvError.setText("");
                }
            }.start();
        } else if (sp.contains(KEY_LOCKOUT_UNTIL)) {
            resetFailedAttempts();
            btnLogin.setEnabled(true);
            btnLogin.setText("Sign In");
        }
    }

    private void saveAdminToken() {
        FirebaseMessaging.getInstance()
                .getToken()
                .addOnSuccessListener(token -> {
                    if (mAuth.getCurrentUser() == null) return;
                    String uid = mAuth.getCurrentUser().getUid();
                    Map<String, Object> data = new HashMap<>();
                    data.put("fcmToken", token);
                    FirebaseFirestore.getInstance()
                            .collection("admin_tokens")
                            .document(uid)
                            .set(data);
                });
    }

    private void openDashboard() {
        Intent intent = new Intent(this, AdminDashboardActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP |
                Intent.FLAG_ACTIVITY_SINGLE_TOP);
        startActivity(intent);
        overridePendingTransition(0, 0);
        finish();
    }
}
