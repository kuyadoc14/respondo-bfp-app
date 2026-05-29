package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;
import java.util.HashMap;
import java.util.Map;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.auth.FirebaseAuth;

public class AdminLoginActivity extends AppCompatActivity {

    private FirebaseAuth mAuth;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_login);

        mAuth = FirebaseAuth.getInstance();

        EditText etEmail    = findViewById(R.id.etEmail);
        EditText etPassword = findViewById(R.id.etPassword);
        Button   btnLogin   = findViewById(R.id.btnLogin);

        // If already logged in, skip to dashboard
        if (mAuth.getCurrentUser() != null) {
            startActivity(new Intent(this, AdminDashboardActivity.class));
            finish();
            return;
        }

        btnLogin.setOnClickListener(v -> {
            String email    = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                Toast.makeText(this, "Please fill in all fields.", Toast.LENGTH_SHORT).show();
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Logging in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {

                            // Save FCM token now that admin is logged in
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

                            startActivity(new Intent(this, AdminDashboardActivity.class));
                            finish();

                        } else {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Login");
                            Toast.makeText(this,
                                    "Login failed: " + task.getException().getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        }
                    });
        });
    }
}