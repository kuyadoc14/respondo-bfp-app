package com.bfp.alert;

import android.Manifest;
import android.graphics.Typeface;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Build;
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

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationServices;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SosFragment extends Fragment {

    private static final String TAG       = "SosFragment";
    private static final String PREFS     = "bfp_prefs";
    private static final String KEY_ALERT = "activeAlertId";
    private static final String KEY_USER  = "userId";
    private static final String KEY_HANDS_FREE = "handsFreeVoiceEnabled";
    private static final int REQUEST_MIC_MANUAL = 200;
    private static final int REQUEST_MIC_HANDS_FREE = 201;

    // Static so they survive tab switches (fragment .replace() destroys views)
    private static ListenerRegistration sListener = null;
    private static String               sAlertId  = null;

    private FirebaseFirestore          db;
    private FusedLocationProviderClient locationClient;

    private Button       btnSOS;
    private View         ringOuter;
    private View         ringMid;
    private TextView     tvStatus;
    private TextView     tvLiveLabel;
    private LinearLayout statusCard;
    private View         btnOpenReport;
    private SwitchMaterial switchHandsFree;
    private VoiceAssistantDialog voiceAssistantDialog;
    private boolean suppressHandsFreeListener;

    // BLE
    private BLEManager bleManager;
    private View       bleBadge;

    private final BroadcastReceiver bleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (BLESOSService.ACTION_BLE_STATE_CHANGED.equals(action)) {
                boolean connected = intent.getBooleanExtra(BLESOSService.EXTRA_CONNECTED, false);
                if (bleBadge != null) {
                    bleBadge.setVisibility(connected ? View.VISIBLE : View.GONE);
                }
            } else if (BLESOSService.ACTION_SOS_SENT.equals(action)) {
                String alertId = intent.getStringExtra(BLESOSService.EXTRA_ALERT_ID);
                if (alertId != null) {
                    sAlertId = alertId;
                    saveAlertId(alertId);
                    setSOSSentState();
                    attachLiveListener(alertId);
                    promptRoleSelection(alertId);
                }
            }
        }
    };

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
        tvLiveLabel = view.findViewById(R.id.tvLiveLabel);
        statusCard = view.findViewById(R.id.statusCard);
        bleBadge   = view.findViewById(R.id.bleBadge);

        SystemBarUtils.applyTopInsetPadding(view.findViewById(R.id.topBar));
        SystemBarUtils.applyTopInsetMargin(bleBadge);

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

        switchHandsFree = view.findViewById(R.id.switchHandsFree);
        switchHandsFree.setChecked(isHandsFreeEnabled());
        switchHandsFree.setOnCheckedChangeListener((button, checked) -> {
            if (suppressHandsFreeListener) return;

            if (checked) {
                enableHandsFreeMode();
            } else {
                setHandsFreeEnabled(false);
                if (voiceAssistantDialog != null
                        && voiceAssistantDialog.isShowing()) {
                    voiceAssistantDialog.dismiss();
                }
            }
        });

        btnOpenReport = view.findViewById(R.id.btnOpenReport);
        if (btnOpenReport != null) {
            btnOpenReport.setOnClickListener(v -> {
                String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
                if (alertId != null) {
                    promptRoleSelection(alertId);
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
    public void onStart() {
        super.onStart();
        if (getContext() != null) {
            IntentFilter filter = new IntentFilter();
            filter.addAction(BLESOSService.ACTION_BLE_STATE_CHANGED);
            filter.addAction(BLESOSService.ACTION_SOS_SENT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                getContext().registerReceiver(bleReceiver, filter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                getContext().registerReceiver(bleReceiver, filter);
            }
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(bleReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        if (bleBadge != null) {
            bleBadge.setVisibility(BLESOSService.isDeviceConnected() ? View.VISIBLE : View.GONE);
        }
        syncWithFirestore();
        if (isHandsFreeEnabled()) {
            if (ContextCompat.checkSelfPermission(
                    requireContext(),
                    Manifest.permission.RECORD_AUDIO)
                    == PackageManager.PERMISSION_GRANTED) {
                showVoiceAssistant(true);
            } else {
                requestPermissions(
                        new String[]{Manifest.permission.RECORD_AUDIO},
                        REQUEST_MIC_HANDS_FREE);
            }
        }
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

                    if ("active".equals(status) || "deployed".equals(status)) {
                        sAlertId = alertId;
                        if ("deployed".equals(status)) {
                            setDeployedState();
                        } else {
                            setSOSSentState();
                        }
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

                    if ("deployed".equals(status)) {
                        setDeployedState();
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "🚒 BFP Responders are on their way!", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("resolved".equals(status)) {
                        clearAlertState();
                        setResolvedState();
                    } else if ("active".equals(status)) {
                        setSOSSentState();
                    }
                });
    }

    // ─────────────────────────────────────────────────────────
    // Send SOS from button tap
    // Replace your existing sendSOSAlert() in SosFragment.java
// with this complete version

    public void sendSOSAlert() {
        sendSOSAlert(null);
    }

    public void sendSOSAlert(String role) {
        if (!isAdded() || getContext() == null) return;

        // If alert is already active and role is specified:
        if (sAlertId != null && !sAlertId.isEmpty()) {
            if ("victim".equalsIgnoreCase(role)) {
                autoSubmitVictimDetails(sAlertId, null);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "SOS updated: Victim details auto-transmitted.", Toast.LENGTH_SHORT).show();
                }
            } else if (role != null && !role.isEmpty()) {
                Map<String, Object> update = new HashMap<>();
                update.put("reporterRole", role);
                db.collection("sos_alerts").document(sAlertId).update(update);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "SOS updated: You are set as " + role + ".", Toast.LENGTH_SHORT).show();
                }
            }
            return;
        }

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
                    if (!isAdded() || getContext() == null) return;
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
                    if (role != null && !role.isEmpty()) {
                        alert.put("reporterRole", role);
                    }

                    db.collection("sos_alerts")
                            .add(alert)
                            .addOnSuccessListener(ref -> {
                                if (!isAdded()) return;
                                sAlertId = ref.getId();
                                saveAlertId(sAlertId);
                                setSOSSentState();
                                attachLiveListener(sAlertId);

                                if ("victim".equalsIgnoreCase(role)) {
                                    autoSubmitVictimDetails(sAlertId, null);
                                } else {
                                    promptRoleSelection(sAlertId);
                                }
                            })
                            .addOnFailureListener(e -> {
                                if (!isAdded()) return;
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

    public void cancelActiveSOS() {
        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (alertId != null) {
            Map<String, Object> update = new HashMap<>();
            update.put("status", "resolved");
            update.put("resolvedReason", "Cancelled by citizen");
            db.collection("sos_alerts").document(alertId).update(update);
            clearAlertState();
            setIdleState();
            if (getContext() != null) {
                Toast.makeText(getContext(), "SOS alert cancelled.", Toast.LENGTH_SHORT).show();
            }
        } else {
            if (getContext() != null) {
                Toast.makeText(getContext(), "No active SOS alert to cancel.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    public void checkCurrentAlertStatus() {
        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (alertId == null) {
            if (getContext() != null) {
                Toast.makeText(getContext(), "No active SOS alert. You are safe.", Toast.LENGTH_SHORT).show();
            }
            return;
        }

        db.collection("sos_alerts").document(alertId).get()
                .addOnSuccessListener(doc -> {
                    if (!isAdded() || getContext() == null) return;
                    if (!doc.exists()) {
                        Toast.makeText(getContext(), "No active SOS alert found.", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    String status = doc.getString("status");
                    if ("deployed".equalsIgnoreCase(status)) {
                        Toast.makeText(getContext(), "🚒 BFP Responders are DEPLOYED and heading to your location!", Toast.LENGTH_LONG).show();
                    } else if ("active".equalsIgnoreCase(status)) {
                        Toast.makeText(getContext(), "SOS is active. Dispatchers are processing your alert.", Toast.LENGTH_LONG).show();
                    } else {
                        Toast.makeText(getContext(), "Alert status: " + status, Toast.LENGTH_SHORT).show();
                    }
                });
    }

    public void updateAlertCondition(String conditionNotes) {
        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (alertId != null && conditionNotes != null && !conditionNotes.trim().isEmpty()) {
            Map<String, Object> update = new HashMap<>();
            update.put("incidentNotes", conditionNotes.trim());
            update.put("lastConditionUpdate", com.google.firebase.firestore.FieldValue.serverTimestamp());
            db.collection("sos_alerts").document(alertId).update(update);
            if (getContext() != null) {
                Toast.makeText(getContext(), "Condition updated: " + conditionNotes, Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void autoSubmitVictimDetails(String alertId, @Nullable UserProfile profile) {
        if (alertId == null || !isAdded()) return;

        Context ctx = getContext();
        if (ctx == null) return;

        UserProfile victimProfile = profile != null ? profile : UserProfileManager.getLocalProfile(ctx);

        Map<String, Object> update = new HashMap<>();
        update.put("reporterRole", "victim");
        update.put("countOfVictims", 1);
        update.put("reportSubmitted", true);

        if (victimProfile != null && !victimProfile.isEmpty()) {
            if (!victimProfile.getFullName().isEmpty()) {
                update.put("reporterName", victimProfile.getFullName());
            }
            if (!victimProfile.getPhone().isEmpty()) {
                update.put("reporterPhone", victimProfile.getPhone());
            }
            update.put("reporterProfile", victimProfile.toMap());

            Map<String, Object> patientMap = victimProfile.toPatientMap();
            List<Map<String, Object>> patients = new ArrayList<>();
            patients.add(patientMap);
            update.put("patients", patients);
        }

        db.collection("sos_alerts")
                .document(alertId)
                .update(update)
                .addOnSuccessListener(u -> {
                    if (!isAdded() || getContext() == null) return;
                    markReportSubmitted(alertId);
                    hideReportButton();
                    Toast.makeText(getContext(),
                            "🚑 SOS Sent! Your medical & personal details were automatically dispatched to BFP.",
                            Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    Log.e(TAG, "Failed auto-submitting victim details: " + e.getMessage());
                });
    }

    private void promptRoleSelection(String alertId) {
        if (!isAdded()) return;
        RoleSelectionDialog dialog = RoleSelectionDialog.newInstance(alertId);
        dialog.setOnRoleSelectedListener(new RoleSelectionDialog.OnRoleSelectedListener() {
            @Override
            public void onRoleSelected(String role, UserProfile profile) {
                if ("victim".equalsIgnoreCase(role)) {
                    autoSubmitVictimDetails(alertId, profile);
                } else {
                    openAccidentReportSheet(alertId, role, profile);
                }
            }

            @Override
            public void onRoleSkipped() {
                if (getContext() != null) {
                    Toast.makeText(getContext(),
                            "SOS active. You can add report details anytime.",
                            Toast.LENGTH_SHORT).show();
                }
            }
        });
        dialog.show(getChildFragmentManager(), "role_selection");
    }

    private void openAccidentReportSheet(String alertId, String role, UserProfile profile) {
        if (!isAdded()) return;
        AccidentReportSheet sheet = AccidentReportSheet.newInstance(alertId, role, profile);
        sheet.setOnReportSubmittedListener(() -> {
            markReportSubmitted(alertId);
            hideReportButton();
        });
        sheet.show(getChildFragmentManager(), "accident_report");
    }

    // ─────────────────────────────────────────────────────────
    // BLE init — listens for ESP32 trigger
    private void initBLE() {
        if (getContext() != null) {
            BLESOSService.start(getContext());
        }
        if (bleBadge != null) {
            bleBadge.setVisibility(BLESOSService.isDeviceConnected() ? View.VISIBLE : View.GONE);
        }
    }

    private void showVoiceAssistant() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MIC_MANUAL);
            return;
        }

        showVoiceAssistant(isHandsFreeEnabled());
    }

    private void showVoiceAssistant(boolean handsFreeMode) {
        if (voiceAssistantDialog != null
                && voiceAssistantDialog.isShowing()) {
            return;
        }

        voiceAssistantDialog = new VoiceAssistantDialog(
                requireContext(),
                new VoiceAssistantDialog.ActionListener() {
                            @Override
                            public void onSendSOS() {
                                sendSOSAlert();
                            }

                            @Override
                            public void onSendSOSWithRole(String role) {
                                sendSOSAlert(role);
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

                            @Override
                            public void onOpenFirstAidDetail(FirstAidItem item) {
                                if (item != null && getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity())
                                            .openFirstAidDetail(item);
                                } else if (item != null) {
                                    onSearchFirstAid(item.title);
                                }
                            }

                            @Override
                            public void onCheckStatus() {
                                checkCurrentAlertStatus();
                            }

                            @Override
                            public void onCancelSOS() {
                                cancelActiveSOS();
                            }

                            @Override
                            public void onUpdateCondition(String conditionNotes) {
                                updateAlertCondition(conditionNotes);
                            }
                        },
                handsFreeMode);
        VoiceAssistantDialog shownDialog = voiceAssistantDialog;
        shownDialog.setOnDismissListener(dialog -> {
            if (voiceAssistantDialog == shownDialog) {
                voiceAssistantDialog = null;
            }
        });
        shownDialog.show();
    }

    private void enableHandsFreeMode() {
        if (ContextCompat.checkSelfPermission(
                requireContext(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(
                    new String[]{Manifest.permission.RECORD_AUDIO},
                    REQUEST_MIC_HANDS_FREE);
            return;
        }

        setHandsFreeEnabled(true);
        showVoiceAssistant(true);
    }

    private boolean isHandsFreeEnabled() {
        return requireContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .getBoolean(KEY_HANDS_FREE, true);
    }

    private void setHandsFreeEnabled(boolean enabled) {
        requireContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                .edit()
                .putBoolean(KEY_HANDS_FREE, enabled)
                .apply();
    }

    private void setHandsFreeSwitchChecked(boolean checked) {
        if (switchHandsFree == null) return;
        suppressHandsFreeListener = true;
        switchHandsFree.setChecked(checked);
        suppressHandsFreeListener = false;
    }

    @Override
    public void onRequestPermissionsResult(
            int requestCode,
            @NonNull String[] permissions,
            @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(
                requestCode, permissions, grantResults);
        if (requestCode != REQUEST_MIC_MANUAL
                && requestCode != REQUEST_MIC_HANDS_FREE) {
            return;
        }

        boolean granted = grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED;
        if (requestCode == REQUEST_MIC_HANDS_FREE) {
            if (granted) {
                setHandsFreeEnabled(true);
                setHandsFreeSwitchChecked(true);
                showVoiceAssistant(true);
            } else {
                setHandsFreeEnabled(false);
                setHandsFreeSwitchChecked(false);
                Toast.makeText(requireContext(),
                        "Microphone permission is required for Hands-free mode.",
                        Toast.LENGTH_LONG).show();
            }
        } else if (granted) {
            showVoiceAssistant(isHandsFreeEnabled());
        } else {
            Toast.makeText(requireContext(),
                    "Microphone permission is required to use voice commands.",
                    Toast.LENGTH_LONG).show();
        }
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
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
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

    private CharSequence formatSOSButtonText(String title, String subtitle) {
        if (subtitle == null || subtitle.isEmpty()) {
            return title;
        }
        SpannableStringBuilder ssb = new SpannableStringBuilder();
        ssb.append(title);
        ssb.setSpan(new AbsoluteSizeSpan(26, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("\n");
        int subStart = ssb.length();
        ssb.append(subtitle);
        ssb.setSpan(new AbsoluteSizeSpan(13, true), subStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), subStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    private void setSOSSentState() {
        if (getView() == null) return;

        stopPulsingAnimation();
        btnSOS.setEnabled(false);
        btnSOS.setText(formatSOSButtonText("SOS", "SENT"));
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFF883333));

        if (statusCard != null) {
            statusCard.setVisibility(View.VISIBLE);
        }
        if (tvLiveLabel != null) {
            tvLiveLabel.setText("ALERT ACTIVE");
            tvLiveLabel.setTextColor(0xFFFF6B00);
        }
        if (tvStatus != null) {
            tvStatus.setText("Alert sent. Responders have been notified and dispatch is pending.");
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

    private void setDeployedState() {
        if (getView() == null) return;

        stopPulsingAnimation();
        btnSOS.setEnabled(false);
        btnSOS.setText(formatSOSButtonText("SOS", "EN ROUTE"));
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFEA580C));

        if (statusCard != null) {
            statusCard.setVisibility(View.VISIBLE);
        }
        if (tvLiveLabel != null) {
            tvLiveLabel.setText("RESPONDERS DEPLOYED");
            tvLiveLabel.setTextColor(0xFFFF6B00);
        }
        if (tvStatus != null) {
            tvStatus.setText("🚒 Help is on the way!\nBFP units are deployed and heading to your location.");
        }

        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (btnOpenReport != null) {
            if (alertId != null && isReportSubmitted(alertId)) {
                btnOpenReport.setVisibility(View.GONE);
            } else {
                btnOpenReport.setVisibility(View.VISIBLE);
            }
        }

        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
            pulseDot.setBackgroundResource(R.drawable.circle_dot_orange);
            android.view.animation.Animation dotAnim =
                    android.view.animation.AnimationUtils.loadAnimation(
                            requireContext(), R.anim.pulse_dot);
            pulseDot.startAnimation(dotAnim);
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
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFFF3B30));
        if (tvLiveLabel != null) {
            tvLiveLabel.setText("RESOLVED");
            tvLiveLabel.setTextColor(0xFF10B981);
        }

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
        if (voiceAssistantDialog != null) {
            voiceAssistantDialog.dismiss();
            voiceAssistantDialog = null;
        }
        super.onDestroyView();
        stopPulsingAnimation();
        // Do NOT remove sListener here —
        // it needs to survive tab switches
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Only remove listener when fragment
        // is truly destroyed
        if (sListener != null) {
            sListener.remove();
            sListener = null;
        }
    }
}
