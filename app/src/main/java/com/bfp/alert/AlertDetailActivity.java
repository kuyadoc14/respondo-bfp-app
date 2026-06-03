package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
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

    private double alertLat, alertLng;
    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_detail);

        db       = FirebaseFirestore.getInstance();
        String alertId = getIntent().getStringExtra("alertId");
        String status  = getIntent().getStringExtra("status");
        String userId  = getIntent().getStringExtra("userId");
        alertLat       = getIntent().getDoubleExtra("latitude",  0);
        alertLng       = getIntent().getDoubleExtra("longitude", 0);
        long   millis  = getIntent().getLongExtra("timestamp",   0);

        TextView tvStatus   = findViewById(R.id.tvDetailStatus);
        TextView tvTime     = findViewById(R.id.tvDetailTime);
        TextView tvLocation = findViewById(R.id.tvDetailLocation);
        TextView tvUser     = findViewById(R.id.tvDetailUser);
        Button btnResolve   = findViewById(R.id.btnDetailResolve);
        Button btnBack      = findViewById(R.id.btnDetailBack);
        Button btnNavigate  = findViewById(R.id.btnDirections);
        Button btnFromStation =
                findViewById(R.id.btnDirectionsFromStation);

        // Fill details
        boolean isActive = "active".equals(status);
        tvStatus.setText(
                "Status: " + (isActive ? "ACTIVE" : "RESOLVED"));
        tvStatus.setTextColor(
                isActive ? 0xFFFC4D4D : 0xFF2a9d8f);

        if (millis > 0) {
            tvTime.setText("Time: " +
                    new SimpleDateFormat(
                            "MMM dd, yyyy hh:mm a",
                            Locale.getDefault())
                            .format(new Date(millis)));
        } else {
            tvTime.setText("Time: —");
        }

        tvLocation.setText(String.format(
                Locale.getDefault(),
                "📍 %.5f, %.5f", alertLat, alertLng));
        tvUser.setText("User ID: " + userId);

        // Hide resolve if already resolved
        if (!isActive) {
            btnResolve.setVisibility(View.GONE);
        }

        btnResolve.setOnClickListener(v -> {
            if (alertId == null) return;
            db.collection("sos_alerts")
                    .document(alertId)
                    .update("status", "resolved")
                    .addOnSuccessListener(u -> {
                        Toast.makeText(this,
                                "Alert resolved.",
                                Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e ->
                            Toast.makeText(this,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
        });

        btnBack.setOnClickListener(v -> finish());

        // Navigate from current device location
        btnNavigate.setOnClickListener(v ->
                openGoogleMapsApp(alertLat, alertLng));

        // Navigate from BFP station
        btnFromStation.setOnClickListener(v ->
                openDirectionsFromStation());
    }

    // Open Google Maps app for turn-by-turn navigation
    private void openGoogleMapsApp(double destLat,
                                   double destLng) {
        String uri = "google.navigation:q="
                + destLat + "," + destLng + "&mode=d";
        Intent intent = new Intent(
                Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage(
                "com.google.android.apps.maps");

        if (intent.resolveActivity(
                getPackageManager()) != null) {
            startActivity(intent);
        } else {
            String browserUri =
                    "https://www.google.com/maps/dir/?api=1"
                            + "&destination="
                            + destLat + "," + destLng
                            + "&travelmode=driving";
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(browserUri)));
        }
    }

    // Open Google Maps with route from BFP station
    private void openDirectionsFromStation() {
        db.collection("bfp_stations")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Toast.makeText(this,
                                "No BFP station found.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    Double sLat = snap.getDocuments()
                            .get(0).getDouble("latitude");
                    Double sLng = snap.getDocuments()
                            .get(0).getDouble("longitude");

                    if (sLat == null || sLng == null) {
                        Toast.makeText(this,
                                "Station coordinates not set.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Open Google Maps from station to alert
                    String uri =
                            "https://www.google.com/maps/dir/"
                                    + sLat + "," + sLng
                                    + "/" + alertLat + "," + alertLng
                                    + "/?travelmode=driving";
                    Intent intent = new Intent(
                            Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage(
                            "com.google.android.apps.maps");

                    if (intent.resolveActivity(
                            getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        intent.setPackage(null);
                        startActivity(intent);
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }
}