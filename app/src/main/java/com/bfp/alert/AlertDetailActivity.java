package com.bfp.alert;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class AlertDetailActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_detail);

        FirebaseFirestore db = FirebaseFirestore.getInstance();

        // Get data passed from map marker tap
        String alertId = getIntent().getStringExtra("alertId");
        String status  = getIntent().getStringExtra("status");
        String userId  = getIntent().getStringExtra("userId");
        double lat     = getIntent().getDoubleExtra("latitude",  0);
        double lng     = getIntent().getDoubleExtra("longitude", 0);
        long   millis  = getIntent().getLongExtra("timestamp",   0);

        TextView tvStatus   = findViewById(R.id.tvDetailStatus);
        TextView tvTime     = findViewById(R.id.tvDetailTime);
        TextView tvLocation = findViewById(R.id.tvDetailLocation);
        TextView tvUser     = findViewById(R.id.tvDetailUser);
        Button   btnResolve = findViewById(R.id.btnDetailResolve);
        Button   btnBack    = findViewById(R.id.btnDetailBack);

        // Status badge color
        boolean isActive = "active".equals(status);
        tvStatus.setText("Status: " + (isActive ? "ACTIVE" : "RESOLVED"));
        tvStatus.setTextColor(isActive ? 0xFFe63946 : 0xFF2a9d8f);

        // Time
        if (millis > 0) {
            String formatted = new SimpleDateFormat(
                    "MMM dd, yyyy hh:mm a", Locale.getDefault())
                    .format(new Date(millis));
            tvTime.setText("Time: " + formatted);
        } else {
            tvTime.setText("Time: —");
        }

        tvLocation.setText("Location: " + lat + ", " + lng);
        tvUser.setText("User ID: " + userId);

        // Hide resolve button if already resolved
        if (!isActive) {
            btnResolve.setVisibility(View.GONE);
        }

        btnResolve.setOnClickListener(v -> {
            if (alertId == null) return;
            db.collection("sos_alerts").document(alertId)
                    .update("status", "resolved")
                    .addOnSuccessListener(unused -> {
                        Toast.makeText(this,
                                "Alert marked as resolved.", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
        });

        btnBack.setOnClickListener(v -> finish());
    }
}