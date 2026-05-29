package com.bfp.alert;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class MainActivity extends AppCompatActivity {

    private FusedLocationProviderClient locationClient;
    private FirebaseFirestore db;
    private Button btnSOS;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        db = FirebaseFirestore.getInstance();
        locationClient = LocationServices.getFusedLocationProviderClient(this);

        btnSOS = findViewById(R.id.btnSOS);
        TextView btnAdminLogin = findViewById(R.id.btnAdminLogin);

        btnSOS.setOnClickListener(v -> sendSOSAlert());

        btnAdminLogin.setOnClickListener(v ->
                startActivity(new Intent(this, AdminLoginActivity.class)));
    }

    private void sendSOSAlert() {
        if (ActivityCompat.checkSelfPermission(this,
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, 100);
            return;
        }

        btnSOS.setEnabled(false);
        btnSOS.setText("Sending...");

        locationClient.getLastLocation().addOnSuccessListener(location -> {
            Map<String, Object> alert = new HashMap<>();
            alert.put("userId",      "user_" + System.currentTimeMillis());
            alert.put("latitude",    location != null ? location.getLatitude()  : 0.0);
            alert.put("longitude",   location != null ? location.getLongitude() : 0.0);
            alert.put("timestamp",   FieldValue.serverTimestamp());
            alert.put("status",      "active");
            alert.put("deviceToken", "");

            db.collection("sos_alerts").add(alert)
                    .addOnSuccessListener(ref -> {
                        btnSOS.setText("SENT ✓");
                        Toast.makeText(this,
                                "SOS Sent! BFP has been alerted.", Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        btnSOS.setEnabled(true);
                        btnSOS.setText("SOS");
                        Toast.makeText(this,
                                "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        });
    }
}