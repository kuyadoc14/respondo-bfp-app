package com.bfp.alert;

import android.Manifest;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.HashMap;
import java.util.Map;

public class SosFragment extends Fragment {

    private FusedLocationProviderClient locationClient;
    private FirebaseFirestore db;
    private Button btnSOS;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sos, container, false);

        db             = FirebaseFirestore.getInstance();
        locationClient = LocationServices
                .getFusedLocationProviderClient(requireActivity());

        btnSOS = view.findViewById(R.id.btnSOS);

        // Pulse animation
        Animation pulse = AnimationUtils.loadAnimation(
                requireContext(), R.anim.pulse);
        btnSOS.startAnimation(pulse);

        btnSOS.setOnClickListener(v -> sendSOSAlert());

        return view;
    }

    private void sendSOSAlert() {
        if (ActivityCompat.checkSelfPermission(requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(),
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
                        Toast.makeText(requireContext(),
                                "SOS Sent! BFP has been alerted.",
                                Toast.LENGTH_LONG).show();
                    })
                    .addOnFailureListener(e -> {
                        btnSOS.setEnabled(true);
                        btnSOS.setText("SOS");
                        Toast.makeText(requireContext(),
                                "Failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        });
    }
}