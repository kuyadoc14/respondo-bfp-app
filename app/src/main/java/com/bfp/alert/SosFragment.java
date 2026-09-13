package com.bfp.alert;

import android.Manifest;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.util.Log;
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
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.HashMap;
import java.util.Map;

public class SosFragment extends Fragment {

    private static final String TAG       = "SosFragment";
    private static final String PREFS     = "bfp_prefs";
    private static final String KEY_ALERT = "activeAlertId";
    private static final String KEY_USER  = "userId";

    // Static so they survive tab switches (fragment .replace() destroys views)
    private static ListenerRegistration sListener = null;
    private static String               sAlertId  = null;

    private FirebaseFirestore          db;
    private FusedLocationProviderClient locationClient;

    private Button       btnSOS;
    private View         ringOuter;
    private View         ringMid;
    private TextView     tvStatus;
    private LinearLayout statusCard;
    private View         btnOpenReport;

    // BLE
    private BLEManager bleManager;
    private View       bleBadge;

    // ─────────────────────────────────────────────────────────
    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
                R.layout.fragment_sos, container, false);

        db             = FirebaseFirestore.getInstance();
        locationClient = LocationServices
                .getFusedLocationProviderClient(
                        requireActivity());

        btnSOS     = view.findViewById(R.id.btnSOS);
        ringOuter  = view.findViewById(R.id.ringOuter);
        ringMid    = view.findViewById(R.id.ringMid);
        tvStatus   = view.findViewById(R.id.tvStatus);
        statusCard = view.findViewById(R.id.statusCard);
        bleBadge   = view.findViewById(R.id.bleBadge);

        // Tactile press & release spring effect
        btnSOS.setOnTouchListener((v, event) -> {
            if (!btnSOS.isEnabled()) return false;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.93f).scaleY(0.93f).setDuration(120).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).setDuration(150).start();
                    break;
            }
            return false;
        });

        // Voice assistant button
        View btnVoice = view.findViewById(R.id.btnVoice);
        if (btnVoice != null) {
            btnVoice.setOnClickListener(
                    v -> showVoiceAssistant());
        }

        btnOpenReport = view.findViewById(R.id.btnOpenReport);
        if (btnOpenReport != null) {
            btnOpenReport.setOnClickListener(v -> {
                String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
                if (alertId != null) {
                    AccidentReportSheet sheet = AccidentReportSheet.newInstance(alertId);
                    sheet.setOnReportSubmittedListener(() -> {
                        markReportSubmitted(alertId);
                        hideReportButton();
                    });
                    sheet.show(getChildFragmentManager(), "accident_report");
                }
            });
        }

        btnSOS.setOnClickListener(v -> sendSOSAlert());

        // Init BLE
        initBLE();

        // Restore state from SharedPreferences immediately
        String saved = getSavedAlertId();
        if (saved != null) {
            sAlertId = saved;
            setSOSSentState();
        } else {
            startPulsingAnimation();
        }

        // Sync with current Firestore state
        syncWithFirestore();

        return view;
    }

    // ─────────────────────────────────────────────────────────
    // Called every onResume so the SOS button
    // resets instantly after admin resolves
    @Override
    public void onResume() {
        super.onResume();
        syncWithFirestore();
    }

    // ─────────────────────────────────────────────────────────
    // Check current Firestore state for the saved alert
    // and attach a live listener
    private void syncWithFirestore() {
        String alertId = sAlertId != null
                ? sAlertId : getSavedAlertId();

        if (alertId == null) {
            // No active alert — make sure button is ready
            setIdleState();
            return;
        }

        // One-time GET to get current status
        db.collection("sos_alerts")
                .document(alertId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) {
                        // Document deleted — clear state
                        clearAlertState();
                        setIdleState();
                        return;
                    }

                    String status = doc.getString("status");

                    if ("active".equals(status)) {
                        sAlertId = alertId;
                        setSOSSentState();
                        attachLiveListener(alertId);

                        Boolean reportSubmitted = doc.getBoolean("reportSubmitted");
                        if (Boolean.TRUE.equals(reportSubmitted) || doc.contains("patients") || doc.contains("accidentType") || isReportSubmitted(alertId)) {
                            markReportSubmitted(alertId);
                            hideReportButton();
                        }
                    } else {
                        // Already resolved
                        clearAlertState();
                        setIdleState();
                    }
                })
                .addOnFailureListener(e ->
                        Log.e(TAG, "Sync failed: "
                                + e.getMessage()));
    }

    // Attach live listener — survives tab switches
    // because sListener is static
    private void attachLiveListener(String alertId) {
        // Remove old listener if any
        if (sListener != null) {
            sListener.remove();
            sListener = null;
        }

        sListener = db.collection("sos_alerts")
                .document(alertId)
                .addSnapshotListener((snap, e) -> {
                    if (e != null || snap == null) return;

                    Boolean reportSubmitted = snap.getBoolean("reportSubmitted");
                    if (Boolean.TRUE.equals(reportSubmitted) || snap.contains("patients") || snap.contains("accidentType")) {
                        markReportSubmitted(alertId);
                        hideReportButton();
                    }

                    String status = snap.getString("status");
                    Log.d(TAG, "Live update: " + status);

                    if ("resolved".equals(status)) {
                        // Immediately update UI without
                        // waiting for tab switch
                        clearAlertState();
                        setResolvedState();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────
    // Send SOS from button tap
    // Replace your existing sendSOSAlert() in SosFragment.java
// with this complete version

    private void sendSOSAlert() {
        if (ActivityCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{
                            Manifest.permission.ACCESS_FINE_LOCATION
                    }, 100);
            return;
        }

        btnSOS.setEnabled(false);
        btnSOS.setText("Sending...");

        locationClient.getLastLocation()
                .addOnSuccessListener(location -> {
                    String userId = getOrCreateUserId();

                    Map<String, Object> alert =
                            new HashMap<>();
                    alert.put("userId",    userId);
                    alert.put("latitude",  location != null
                            ? location.getLatitude()  : 0.0);
                    alert.put("longitude", location != null
                            ? location.getLongitude() : 0.0);
                    alert.put("timestamp",
                            com.google.firebase.firestore
                                    .FieldValue.serverTimestamp());
                    alert.put("status",      "active");
                    alert.put("deviceToken", "");

                    db.collection("sos_alerts")
                            .add(alert)
                            .addOnSuccessListener(ref -> {
                                sAlertId = ref.getId();
                                saveAlertId(sAlertId);
                                setSOSSentState();
                                attachLiveListener(sAlertId);

                                // ── Show accident report sheet
                                // immediately after SOS is sent
                                // so the bystander can send
                                // patient data while waiting
                                AccidentReportSheet sheet =
                                        AccidentReportSheet
                                                .newInstance(sAlertId);
                                String currentAlertId = sAlertId;
                                sheet.setOnReportSubmittedListener(() -> {
                                    markReportSubmitted(currentAlertId);
                                    hideReportButton();
                                });
                                sheet.show(
                                        getChildFragmentManager(),
                                        "accident_report");
                            })
                            .addOnFailureListener(e -> {
                                btnSOS.setEnabled(true);
                                btnSOS.setText("SOS");
                                Toast.makeText(
                                                requireContext(),
                                                "Failed: "
                                                        + e.getMessage(),
                                                Toast.LENGTH_SHORT)
                                        .show();
                            });
                });
    }

    // ─────────────────────────────────────────────────────────
    // BLE init — listens for ESP32 trigger
    private void initBLE() {
        bleManager = new BLEManager(
                requireContext(),
                new BLEManager.BLEListener() {

                    @Override
                    public void onSOSReceived(
                            String deviceInfo) {
                        requireActivity().runOnUiThread(() -> {

                            // Clear any existing alert state
                            // before new BLE SOS so it
                            // doesn't block sending
                            if (sAlertId != null) {
                                Log.d(TAG,
                                        "BLE SOS: clearing "
                                                + "old alert state");
                                clearAlertState();
                            }

                            Toast.makeText(
                                    requireContext(),
                                    "🚨 SOS from ESP32!",
                                    Toast.LENGTH_SHORT).show();

                            sendSOSAlert();
                        });
                    }

                    @Override
                    public void onConnected() {
                        requireActivity().runOnUiThread(() -> {
                            if (bleBadge != null)
                                bleBadge.setVisibility(
                                        View.VISIBLE);
                            Toast.makeText(
                                    requireContext(),
                                    "🔵 ESP32 connected",
                                    Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override
                    public void onDisconnected() {
                        requireActivity().runOnUiThread(() -> {
                            if (bleBadge != null)
                                bleBadge.setVisibility(
                                        View.GONE);
                        });
                    }

                    @Override
                    public void onScanStarted() {
                        Log.d(TAG, "BLE scanning...");
                    }
                });

        bleManager.startScan();
    }

    private void showVoiceAssistant() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                    requireActivity(),
                    new String[]{
                            Manifest.permission.RECORD_AUDIO},
                    200);
            return;
        }

        VoiceAssistantDialog dialog =
                new VoiceAssistantDialog(
                        requireContext(),
                        new VoiceAssistantDialog.ActionListener() {
                            @Override
                            public void onSendSOS() {
                                sendSOSAlert();
                            }

                            @Override
                            public void onOpenFirstAid() {
                                if (getActivity()
                                        instanceof MainActivity) {
                                    ((MainActivity) getActivity())
                                            .switchToFirstAid();
                                }
                            }

                            @Override
                            public void onSearchFirstAid(
                                    String query) {
                                if (getActivity()
                                        instanceof MainActivity) {
                                    ((MainActivity) getActivity())
                                            .switchToFirstAid(query);
                                }
                            }
                        });
        dialog.show();
    }

    // ─────────────────────────────────────────────────────────
    // UI states & animations

    private void startPulsingAnimation() {
        if (getContext() == null || btnSOS == null) return;

        // Button gentle heartbeat pulse
        Animation pulse = AnimationUtils.loadAnimation(
                requireContext(), R.anim.pulse);
        btnSOS.startAnimation(pulse);

        // Inner radar wave ripple
        if (ringMid != null) {
            ringMid.setVisibility(View.VISIBLE);
            Animation ring1 = AnimationUtils.loadAnimation(
                    requireContext(), R.anim.pulse_ring_1);
            ringMid.startAnimation(ring1);
        }

        // Outer radar wave ripple (staggered delay)
        if (ringOuter != null) {
            ringOuter.setVisibility(View.VISIBLE);
            Animation ring2 = AnimationUtils.loadAnimation(
                    requireContext(), R.anim.pulse_ring_2);
            ringOuter.startAnimation(ring2);
        }
    }

    private void stopPulsingAnimation() {
        if (btnSOS != null) {
            btnSOS.clearAnimation();
        }
        if (ringMid != null) {
            ringMid.clearAnimation();
            ringMid.setVisibility(View.GONE);
        }
        if (ringOuter != null) {
            ringOuter.clearAnimation();
            ringOuter.setVisibility(View.GONE);
        }
    }

    private void setIdleState() {
        if (getView() == null) return;

        btnSOS.setEnabled(true);
        btnSOS.setText("SOS");
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFFF3B30));

        startPulsingAnimation();

        if (statusCard != null) {
            statusCard.setVisibility(View.GONE);
        }
        if (btnOpenReport != null) {
            btnOpenReport.setVisibility(View.GONE);
        }
    }

    private void setSOSSentState() {
        if (getView() == null) return;

        stopPulsingAnimation();
        btnSOS.setEnabled(false);
        btnSOS.setText("SOS\nSENT");
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFF883333));

        if (statusCard != null) {
            statusCard.setVisibility(View.VISIBLE);
        }
        if (tvStatus != null) {
            tvStatus.setText("Alert sent. Responders have been notified.");
        }

        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (btnOpenReport != null) {
            if (alertId != null && isReportSubmitted(alertId)) {
                btnOpenReport.setVisibility(View.GONE);
            } else {
                btnOpenReport.setVisibility(View.VISIBLE);
            }
        }

        // Pulse dot
        View pulseDot = getView().findViewById(
                R.id.pulseDot);
        if (pulseDot != null) {
            Animation dotAnim =
                    AnimationUtils.loadAnimation(
                            requireContext(), R.anim.pulse_dot);
            pulseDot.startAnimation(dotAnim);
        }
    }

    private void markReportSubmitted(String alertId) {
        if (getContext() == null || alertId == null) return;
        requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean("report_sent_" + alertId, true)
                .apply();
    }

    private boolean isReportSubmitted(String alertId) {
        if (getContext() == null || alertId == null) return false;
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean("report_sent_" + alertId, false);
    }

    private void hideReportButton() {
        if (btnOpenReport != null) {
            btnOpenReport.setVisibility(View.GONE);
        }
    }

    private void setResolvedState() {
        if (getView() == null) return;

        // Stop pulse dot
        View pulseDot = getView().findViewById(
                R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
            pulseDot.setBackgroundResource(
                    R.drawable.circle_dot_green);
        }

        btnSOS.setEnabled(true);
        btnSOS.setText("SOS");
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFFF3B30));

        startPulsingAnimation();

        Toast.makeText(requireContext(),
                "Alert resolved by BFP.",
                Toast.LENGTH_LONG).show();

        // Hide status card after 6 seconds
        statusCard.postDelayed(() -> {
            if (getView() != null)
                statusCard.setVisibility(View.GONE);
        }, 6000);
    }

    // ─────────────────────────────────────────────────────────
    // SharedPreferences helpers

    private String getOrCreateUserId() {
        SharedPreferences prefs =
                requireContext().getSharedPreferences(
                        PREFS, Context.MODE_PRIVATE);
        String id = prefs.getString(KEY_USER, null);
        if (id == null) {
            id = "user_" + System.currentTimeMillis();
            prefs.edit().putString(KEY_USER, id).apply();
        }
        return id;
    }

    private void saveAlertId(String id) {
        requireContext()
                .getSharedPreferences(PREFS,
                        Context.MODE_PRIVATE)
                .edit()
                .putString(KEY_ALERT, id)
                .apply();
    }

    private String getSavedAlertId() {
        return requireContext()
                .getSharedPreferences(PREFS,
                        Context.MODE_PRIVATE)
                .getString(KEY_ALERT, null);
    }

    private void clearAlertState() {
        sAlertId = null;
        requireContext()
                .getSharedPreferences(PREFS,
                        Context.MODE_PRIVATE)
                .edit()
                .remove(KEY_ALERT)
                .apply();

        if (sListener != null) {
            sListener.remove();
            sListener = null;
        }
    }

    // ─────────────────────────────────────────────────────────
    @Override
    public void onDestroyView() {
        super.onDestroyView();
        stopPulsingAnimation();
        // Do NOT remove sListener here —
        // it needs to survive tab switches
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (bleManager != null)
            bleManager.disconnect();
        // Only remove listener when fragment
        // is truly destroyed
        if (sListener != null) {
            sListener.remove();
            sListener = null;
        }
    }
}

