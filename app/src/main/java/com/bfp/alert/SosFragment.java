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
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.ActivityCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.HashMap;
import java.util.Map;

public class SosFragment extends Fragment {

    private FusedLocationProviderClient locationClient;
    private FirebaseFirestore db;
    private Button btnSOS;
    private TextView tvStatus;
    private LinearLayout statusCard;
    private ListenerRegistration alertListener;
    private String activeAlertId = null;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.fragment_sos, container, false);

        db             = FirebaseFirestore.getInstance();
        locationClient = LocationServices
                .getFusedLocationProviderClient(requireActivity());

        btnSOS     = view.findViewById(R.id.btnSOS);
        tvStatus   = view.findViewById(R.id.tvStatus);
        statusCard = view.findViewById(R.id.statusCard);

        Animation pulse = AnimationUtils.loadAnimation(
                requireContext(), R.anim.pulse);
        btnSOS.startAnimation(pulse);

        btnSOS.setOnClickListener(v -> sendSOSAlert());

        // Check if there's already an active alert when fragment loads
        checkExistingAlert();

        return view;
    }

    // Check Firestore for any existing active alert from this session
    private void checkExistingAlert() {
        String sessionUserId = getSessionUserId();
        if (sessionUserId == null) return;

        db.collection("sos_alerts")
                .whereEqualTo("userId", sessionUserId)
                .whereEqualTo("status", "active")
                .limit(1)
                .get()
                .addOnSuccessListener(snapshots -> {
                    if (!snapshots.isEmpty()) {
                        activeAlertId = snapshots.getDocuments().get(0).getId();
                        setSOSSentState();
                        listenForResolution(activeAlertId);
                    }
                });
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
            // Generate a stable userId for this install
            String userId = getOrCreateUserId();

            Map<String, Object> alert = new HashMap<>();
            alert.put("userId",      userId);
            alert.put("latitude",    location != null ? location.getLatitude()  : 0.0);
            alert.put("longitude",   location != null ? location.getLongitude() : 0.0);
            alert.put("timestamp",   FieldValue.serverTimestamp());
            alert.put("status",      "active");
            alert.put("deviceToken", "");

            db.collection("sos_alerts").add(alert)
                    .addOnSuccessListener(ref -> {
                        activeAlertId = ref.getId();
                        setSOSSentState();
                        listenForResolution(activeAlertId);
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

    // Real-time listener — watches for admin resolving the alert
    private void listenForResolution(String alertId) {
        if (alertListener != null) alertListener.remove();

        alertListener = db.collection("sos_alerts")
                .document(alertId)
                .addSnapshotListener((snapshot, e) -> {
                    if (e != null || snapshot == null) return;
                    String status = snapshot.getString("status");

                    if ("resolved".equals(status)) {
                        setResolvedState();
                        activeAlertId = null;
                        if (alertListener != null) {
                            alertListener.remove();
                            alertListener = null;
                        }
                    }
                });
    }

    // ── UI States ────────────────────────────────────────────────

    private void setSOSSentState() {
        if (getView() == null) return;

        // Disable SOS button
        btnSOS.setEnabled(false);
        btnSOS.setText("SOS\nSENT");
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF883333));

        // Show warning status card
        statusCard.setVisibility(View.VISIBLE);
        statusCard.setBackgroundResource(R.drawable.bg_status_warning);

        tvStatus.setText("🚒  Help is on the way!\nBFP has received your alert.");
        tvStatus.setTextColor(0xFFFFBB33);

        // Start pulsing dot
        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.setBackgroundResource(R.drawable.circle_dot);
            Animation pulseDotAnim = AnimationUtils.loadAnimation(
                    requireContext(), R.anim.pulse_dot);
            pulseDot.startAnimation(pulseDotAnim);
        }

        // Fade in animation
        Animation fadeIn = AnimationUtils.loadAnimation(
                requireContext(), android.R.anim.fade_in);
        statusCard.startAnimation(fadeIn);
    }

    private void setResolvedState() {
        if (getView() == null) return;

        // Re-enable SOS button
        btnSOS.setEnabled(true);
        btnSOS.setText("SOS");
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFe63946));

        Animation pulse = AnimationUtils.loadAnimation(
                requireContext(), R.anim.pulse);
        btnSOS.startAnimation(pulse);

        // Switch to resolved status card
        statusCard.setVisibility(View.VISIBLE);
        statusCard.setBackgroundResource(R.drawable.bg_status_resolved);

        tvStatus.setText("✅  Help has arrived!\nYour alert has been resolved.");
        tvStatus.setTextColor(0xFF2a9d8f);

        // Stop pulsing dot and turn green
        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
            pulseDot.setBackgroundResource(R.drawable.circle_dot_green);
        }

        Toast.makeText(requireContext(),
                "Your alert has been resolved by BFP.",
                Toast.LENGTH_LONG).show();

        // Hide card after 6 seconds
        statusCard.postDelayed(() -> {
            if (getView() != null) {
                Animation fadeOut = AnimationUtils.loadAnimation(
                        requireContext(), android.R.anim.fade_out);
                fadeOut.setAnimationListener(new Animation.AnimationListener() {
                    @Override public void onAnimationStart(Animation a) {}
                    @Override public void onAnimationRepeat(Animation a) {}
                    @Override public void onAnimationEnd(Animation a) {
                        if (getView() != null)
                            statusCard.setVisibility(View.GONE);
                    }
                });
                statusCard.startAnimation(fadeOut);
            }
        }, 6000);
    }

    // ── User ID helpers ──────────────────────────────────────────

    private String getOrCreateUserId() {
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("bfp_prefs",
                        android.content.Context.MODE_PRIVATE);
        String userId = prefs.getString("userId", null);
        if (userId == null) {
            userId = "user_" + System.currentTimeMillis();
            prefs.edit().putString("userId", userId).apply();
        }
        return userId;
    }

    private String getSessionUserId() {
        android.content.SharedPreferences prefs =
                requireContext().getSharedPreferences("bfp_prefs",
                        android.content.Context.MODE_PRIVATE);
        return prefs.getString("userId", null);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (alertListener != null) {
            alertListener.remove();
            alertListener = null;
        }
    }
}