package com.bfp.alert;

import android.Manifest;
import android.app.Activity;
import android.graphics.Typeface;
import android.location.Location;
import android.location.LocationManager;
import android.provider.Settings;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.AbsoluteSizeSpan;
import android.text.style.StyleSpan;
import android.util.TypedValue;

import android.content.BroadcastReceiver;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentSender;
import android.os.Build;
import android.content.Context;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.IntentSenderRequest;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.android.gms.common.api.ResolvableApiException;
import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationSettingsRequest;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationTokenSource;
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
    private static long                 sLastSosDispatchTime = 0;
    private static final long           SOS_COOLDOWN_MS = 15000; // 15s spam cooldown

    private FirebaseFirestore          db;
    private FusedLocationProviderClient locationClient;

    private String pendingSosRole = null;
    private String pendingSosNotes = null;
    private final ActivityResultLauncher<IntentSenderRequest> locationSettingsLauncher =
            registerForActivityResult(new ActivityResultContracts.StartIntentSenderForResult(), result -> {
                if (!isAdded() || getContext() == null) return;
                if (result.getResultCode() == Activity.RESULT_OK) {
                    // User accepted turning on device location — proceed immediately
                    String roleToSend = pendingSosRole;
                    String notesToSend = pendingSosNotes;
                    pendingSosRole = null;
                    pendingSosNotes = null;
                    fetchLocationAndDispatchSOS(roleToSend, notesToSend);
                } else {
                    pendingSosRole = null;
                    pendingSosNotes = null;
                    resetSOSButtonToIdle();
                    Toast.makeText(requireContext(),
                            "Location must be turned ON before sending an SOS alert.",
                            Toast.LENGTH_LONG).show();
                }
            });

    private Button       btnSOS;
    private View         ringOuter;
    private View         ringMid;
    private TextView     tvStatus;
    private TextView     tvLiveLabel;
    private TextView     tvInstruction;
    private View         liveIndicatorRow;
    private LinearLayout statusCard;
    private View         btnOpenReport;
    private SwitchMaterial switchHandsFree;
    private VoiceAssistantDialog voiceAssistantDialog;
    private boolean suppressHandsFreeListener;

    // BLE
    private BLEManager bleManager;
    private View       bleBadge;
    private View       dotBleStatus;
    private TextView   tvBleBadgeText;
    private TextView   tvShakeBadgeText;
    private RoleSelectionDialog roleSelectionDialog;

    // Animated Top-Left Menu & Popup List
    private HamburgerTriangleView hamburgerView;
    private View       btnSosMenu;
    private View       menuPopupCard;
    private View       menuDismissOverlay;

    private final BroadcastReceiver bleReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (intent == null || intent.getAction() == null) return;
            String action = intent.getAction();
            if (BLESOSService.ACTION_BLE_STATE_CHANGED.equals(action)) {
                updateBleBadgeUI(intent);
            } else if (BLESOSService.ACTION_SOS_SENT.equals(action)) {
                String alertId = intent.getStringExtra(BLESOSService.EXTRA_ALERT_ID);
                String role = intent.getStringExtra(BLESOSService.EXTRA_ROLE);
                String source = intent.getStringExtra(BLESOSService.EXTRA_SOURCE);
                if (alertId != null) {
                    sAlertId = alertId;
                    saveAlertId(alertId);
                    setSOSSentState();
                    attachLiveListener(alertId);

                    if ("ESP32_BLE".equals(source) || (role != null && !role.isEmpty())) {
                        handleWearableRole(role != null ? role : "victim");
                    } else {
                        promptRoleSelection(alertId);
                    }
                }
            } else if (BLESOSService.ACTION_BLE_ROLE_CONFIRMED.equals(action)) {
                String role = intent.getStringExtra(BLESOSService.EXTRA_ROLE);
                handleWearableRole(role);
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

        btnSOS           = view.findViewById(R.id.btnSOS);
        ringOuter        = view.findViewById(R.id.ringOuter);
        ringMid          = view.findViewById(R.id.ringMid);
        tvStatus         = view.findViewById(R.id.tvStatus);
        tvLiveLabel      = view.findViewById(R.id.tvLiveLabel);
        tvInstruction    = view.findViewById(R.id.tvInstruction);
        liveIndicatorRow = view.findViewById(R.id.liveIndicatorRow);
        statusCard       = view.findViewById(R.id.statusCard);
        bleBadge   = view.findViewById(R.id.bleBadge);
        dotBleStatus = view.findViewById(R.id.dotBleStatus);
        tvBleBadgeText = view.findViewById(R.id.tvBleBadgeText);
        tvShakeBadgeText = view.findViewById(R.id.tvShakeBadgeText);

        // Animated Top-Left Menu & Mini List Popup
        btnSosMenu = view.findViewById(R.id.btnSosMenu);
        hamburgerView = view.findViewById(R.id.hamburgerView);
        menuPopupCard = view.findViewById(R.id.menuPopupCard);
        menuDismissOverlay = view.findViewById(R.id.menuDismissOverlay);

        if (btnSosMenu != null) {
            btnSosMenu.setOnClickListener(v -> toggleMenuPopup());
        }
        if (menuDismissOverlay != null) {
            menuDismissOverlay.setOnClickListener(v -> closeMenuPopup());
        }

        if (bleBadge != null) {
            bleBadge.setOnClickListener(v -> {
                closeMenuPopup();
                WearableDeviceBottomSheet.newInstance().show(getChildFragmentManager(), WearableDeviceBottomSheet.TAG);
            });
        }
        View btnShakeSettings = view.findViewById(R.id.btnShakeSettings);
        if (btnShakeSettings != null) {
            btnShakeSettings.setOnClickListener(v -> {
                closeMenuPopup();
                showShakeSettingsDialog();
            });
        }
        updateShakeBadgeUI();

        SystemBarUtils.applyTopInsetPadding(view.findViewById(R.id.topBar));
        if (btnSosMenu != null) {
            SystemBarUtils.applyTopInsetMargin(btnSosMenu);
        }
        if (menuPopupCard != null) {
            SystemBarUtils.applyTopInsetMargin(menuPopupCard);
        }

        // Tactile 3D press & release spring effect
        btnSOS.setOnTouchListener((v, event) -> {
            if (!btnSOS.isEnabled()) return false;
            switch (event.getAction()) {
                case android.view.MotionEvent.ACTION_DOWN:
                    v.animate().scaleX(0.96f).scaleY(0.96f).translationY(4f).setDuration(70).start();
                    break;
                case android.view.MotionEvent.ACTION_UP:
                case android.view.MotionEvent.ACTION_CANCEL:
                    v.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
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
                if (getActivity() instanceof MainActivity) {
                    ((MainActivity) getActivity()).dismissVoiceAssistant();
                }
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

    private void toggleMenuPopup() {
        if (menuPopupCard == null || hamburgerView == null) return;
        boolean opening = menuPopupCard.getVisibility() != View.VISIBLE;
        hamburgerView.setOpen(opening, true);

        if (opening) {
            if (menuDismissOverlay != null) menuDismissOverlay.setVisibility(View.VISIBLE);
            menuPopupCard.setVisibility(View.VISIBLE);
            menuPopupCard.setAlpha(0f);
            menuPopupCard.setTranslationY(-24f);
            menuPopupCard.setScaleX(0.85f);
            menuPopupCard.setScaleY(0.85f);
            menuPopupCard.setPivotX(0f);
            menuPopupCard.setPivotY(0f);
            menuPopupCard.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .scaleX(1f)
                    .scaleY(1f)
                    .setDuration(260)
                    .setInterpolator(new android.view.animation.OvershootInterpolator(1.15f))
                    .start();
        } else {
            closeMenuPopup();
        }
    }

    private void closeMenuPopup() {
        if (menuPopupCard == null) return;
        if (hamburgerView != null) hamburgerView.setOpen(false, true);
        if (menuDismissOverlay != null) menuDismissOverlay.setVisibility(View.GONE);
        if (menuPopupCard.getVisibility() == View.VISIBLE) {
            menuPopupCard.animate()
                    .alpha(0f)
                    .translationY(-18f)
                    .scaleX(0.9f)
                    .scaleY(0.9f)
                    .setDuration(180)
                    .withEndAction(() -> menuPopupCard.setVisibility(View.GONE))
                    .start();
        }
    }

    private void showShakeSettingsDialog() {
        ShakeSettingsBottomSheet bottomSheet = ShakeSettingsBottomSheet.newInstance();
        bottomSheet.setOnSettingsSavedListener((enabled, background, threshold) -> {
            updateShakeBadgeUI();
        });
        bottomSheet.show(getChildFragmentManager(), ShakeSettingsBottomSheet.TAG);
    }

    private void updateShakeBadgeUI() {
        if (tvShakeBadgeText == null || getContext() == null) return;
        SharedPreferences prefs = requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        boolean enabled = prefs.getBoolean(ShakeGestureDetector.PREF_ENABLED, false);
        boolean background = prefs.getBoolean(ShakeGestureDetector.PREF_BACKGROUND, false);
        if (!enabled) {
            tvShakeBadgeText.setText("Shake Off");
            tvShakeBadgeText.setTextColor(0xFF888888);
        } else if (background) {
            tvShakeBadgeText.setText("Shake: Background");
            tvShakeBadgeText.setTextColor(0xFFFF9800);
        } else {
            tvShakeBadgeText.setText("Shake: App Only");
            tvShakeBadgeText.setTextColor(0xFFFFFFFF);
        }
    }

    private String formatShakeThreshold(float threshold) {
        return String.format(java.util.Locale.getDefault(),
                "Shake intensity threshold: %.1f m/s²", threshold);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
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
            filter.addAction(BLESOSService.ACTION_BLE_ROLE_CONFIRMED);
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
        closeMenuPopup();
        if (getContext() != null) {
            try {
                getContext().unregisterReceiver(bleReceiver);
            } catch (Exception ignored) {}
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        updateBleBadgeUI(null);
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
                        BLESOSService.syncStatusWithWearable("STATUS:DEPLOYED");
                        if (getContext() != null) {
                            Toast.makeText(getContext(), "BFP Responders are on their way!", Toast.LENGTH_SHORT).show();
                        }
                    } else if ("resolved".equals(status)) {
                        clearAlertState();
                        setResolvedState();
                        BLESOSService.syncStatusWithWearable("STATUS:RESOLVED");
                    } else if ("active".equals(status)) {
                        setSOSSentState();
                        BLESOSService.syncStatusWithWearable("STATUS:ACTIVE");
                    }
                });
    }

    // ─────────────────────────────────────────────────────────
    // Send SOS from button tap
    // Replace your existing sendSOSAlert() in SosFragment.java
// with this complete version

    public void sendSOSAlert() {
        sendSOSAlert(null, null);
    }

    public void sendSOSAlert(String role) {
        sendSOSAlert(role, null);
    }

    public void sendSOSAlert(String role, String incidentNotes) {
        if (!isAdded() || getContext() == null) return;

        // If alert is already active, update it with incident notes / role immediately
        if (sAlertId != null && !sAlertId.isEmpty()) {
            if ("victim".equalsIgnoreCase(role) || (incidentNotes != null && !incidentNotes.trim().isEmpty())) {
                autoSubmitVictimDetails(sAlertId, null, incidentNotes);
                if (getContext() != null) {
                    Toast.makeText(getContext(), "SOS updated with incident details.", Toast.LENGTH_SHORT).show();
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

        long now = System.currentTimeMillis();
        if (now - sLastSosDispatchTime < SOS_COOLDOWN_MS) {
            long remainingSec = Math.max(1, (SOS_COOLDOWN_MS - (now - sLastSosDispatchTime)) / 1000);
            Toast.makeText(getContext(), "Please wait " + remainingSec + "s before sending another emergency alert.", Toast.LENGTH_SHORT).show();
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
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSOS.setText(formatSOSButtonText("SOS", "LOCATING..."));

        ensureLocationSettingsAndSend(role, incidentNotes);
    }

    private void ensureLocationSettingsAndSend(String role, String incidentNotes) {
        if (!isAdded() || getContext() == null) return;

        LocationRequest locationRequest = new LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, 5000)
                .setMinUpdateIntervalMillis(2000)
                .build();
        LocationSettingsRequest.Builder builder = new LocationSettingsRequest.Builder()
                .addLocationRequest(locationRequest)
                .setAlwaysShow(true);

        LocationServices.getSettingsClient(requireActivity())
                .checkLocationSettings(builder.build())
                .addOnSuccessListener(response -> {
                    if (!isAdded() || getContext() == null) return;
                    fetchLocationAndDispatchSOS(role, incidentNotes);
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    if (e instanceof ResolvableApiException) {
                        try {
                            pendingSosRole = role;
                            pendingSosNotes = incidentNotes;
                            ResolvableApiException resolvable = (ResolvableApiException) e;
                            locationSettingsLauncher.launch(
                                    new IntentSenderRequest.Builder(resolvable.getResolution()).build());
                        } catch (Exception sendEx) {
                            Log.e(TAG, "Failed to launch location resolution: " + sendEx.getMessage());
                            resetSOSButtonToIdle();
                            showEnableLocationDialog();
                        }
                    } else {
                        resetSOSButtonToIdle();
                        showEnableLocationDialog();
                    }
                });
    }

    private void fetchLocationAndDispatchSOS(String role, String incidentNotes) {
        if (!isAdded() || getContext() == null) return;
        if (ActivityCompat.checkSelfPermission(requireContext(), Manifest.permission.ACCESS_FINE_LOCATION)
                != PackageManager.PERMISSION_GRANTED) {
            resetSOSButtonToIdle();
            return;
        }

        btnSOS.setEnabled(false);
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSOS.setText(formatSOSButtonText("SOS", "SENDING..."));

        CancellationTokenSource cts = new CancellationTokenSource();
        locationClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cts.getToken())
                .addOnSuccessListener(currLoc -> {
                    if (!isAdded() || getContext() == null) return;
                    if (isValidLocation(currLoc)) {
                        dispatchAlertWithLocation(currLoc.getLatitude(), currLoc.getLongitude(), role, incidentNotes);
                    } else {
                        locationClient.getLastLocation()
                                .addOnSuccessListener(lastLoc -> {
                                    if (!isAdded() || getContext() == null) return;
                                    if (isValidLocation(lastLoc)) {
                                        dispatchAlertWithLocation(lastLoc.getLatitude(), lastLoc.getLongitude(), role, incidentNotes);
                                    } else {
                                        resetSOSButtonToIdle();
                                        showLocationUnavailableDialog();
                                    }
                                })
                                .addOnFailureListener(err -> {
                                    if (!isAdded() || getContext() == null) return;
                                    resetSOSButtonToIdle();
                                    showLocationUnavailableDialog();
                                });
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    locationClient.getLastLocation()
                            .addOnSuccessListener(lastLoc -> {
                                if (!isAdded() || getContext() == null) return;
                                if (isValidLocation(lastLoc)) {
                                    dispatchAlertWithLocation(lastLoc.getLatitude(), lastLoc.getLongitude(), role, incidentNotes);
                                } else {
                                    resetSOSButtonToIdle();
                                    showLocationUnavailableDialog();
                                }
                            })
                            .addOnFailureListener(err -> {
                                if (!isAdded() || getContext() == null) return;
                                resetSOSButtonToIdle();
                                showLocationUnavailableDialog();
                            });
                });
    }

    private boolean isValidLocation(Location loc) {
        return loc != null && !(loc.getLatitude() == 0.0 && loc.getLongitude() == 0.0);
    }

    private void dispatchAlertWithLocation(double lat, double lng, String role, String incidentNotes) {
        if (!isAdded() || getContext() == null) return;
        sLastSosDispatchTime = System.currentTimeMillis();
        String userId = getOrCreateUserId();

        Map<String, Object> alert = new HashMap<>();
        alert.put("userId",    userId);
        alert.put("latitude",  lat);
        alert.put("longitude", lng);
        alert.put("timestamp", FieldValue.serverTimestamp());
        alert.put("status",    "active");
        alert.put("deviceToken", "");

        String finalRole = role;
        if (incidentNotes != null && !incidentNotes.trim().isEmpty()) {
            alert.put("incidentNotes", incidentNotes.trim());
            alert.put("description", incidentNotes.trim());
            alert.put("accidentType", detectIncidentType(incidentNotes));
            if (finalRole == null || finalRole.isEmpty()) {
                finalRole = "victim";
            }
        }

        if (finalRole != null && !finalRole.isEmpty()) {
            alert.put("reporterRole", finalRole);
        }

        final String effectiveRole = finalRole;

        db.collection("sos_alerts")
                .add(alert)
                .addOnSuccessListener(ref -> {
                    if (!isAdded()) return;
                    sAlertId = ref.getId();
                    saveAlertId(sAlertId);
                    setSOSSentState();
                    attachLiveListener(sAlertId);

                    if ("victim".equalsIgnoreCase(effectiveRole) || (incidentNotes != null && !incidentNotes.trim().isEmpty())) {
                        autoSubmitVictimDetails(sAlertId, null, incidentNotes);
                    } else {
                        promptRoleSelection(sAlertId);
                    }
                })
                .addOnFailureListener(e -> {
                    if (!isAdded()) return;
                    sLastSosDispatchTime = 0;
                    resetSOSButtonToIdle();
                    Toast.makeText(
                                    requireContext(),
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT)
                            .show();
                });
    }

    private void resetSOSButtonToIdle() {
        if (btnSOS == null) return;
        btnSOS.setEnabled(true);
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        btnSOS.setText("SOS");
    }

    private void showEnableLocationDialog() {
        if (!isAdded() || getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Location Required")
                .setMessage("Your device location is turned off. Please turn on Location Services so BFP responders can locate you, then try again.")
                .setPositiveButton("Open Location Settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showLocationUnavailableDialog() {
        if (!isAdded() || getContext() == null) return;
        new AlertDialog.Builder(requireContext())
                .setTitle("Unable to Detect Location")
                .setMessage("Could not detect your GPS coordinates. Please make sure Location is turned ON (try moving near a window or outdoors) and tap SOS again.")
                .setPositiveButton("Location Settings", (d, w) -> {
                    try {
                        startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS));
                    } catch (Exception ignored) {}
                })
                .setNegativeButton("OK", null)
                .show();
    }

    private String detectIncidentType(String notes) {
        if (notes == null) return "Emergency Incident";
        String lower = notes.toLowerCase();
        if (lower.contains("gas") || lower.contains("lpg") || lower.contains("leak")) {
            return "Gas Leak / Hazmat";
        }
        if (lower.contains("burn") || lower.contains("fire") || lower.contains("sunog") || lower.contains("smoke")) {
            return "Structure Fire";
        }
        if (lower.contains("car") || lower.contains("vehic") || lower.contains("crash") || lower.contains("banggaan")) {
            return "Vehicular Accident";
        }
        if (lower.contains("drown") || lower.contains("flood") || lower.contains("baha") || lower.contains("tubig")) {
            return "Water Rescue / Flood";
        }
        if (lower.contains("cpr") || lower.contains("chok") || lower.contains("bleed") || lower.contains("stroke") || lower.contains("unconscious")) {
            return "Medical Emergency";
        }
        if (lower.contains("trap") || lower.contains("naipit") || lower.contains("collapse")) {
            return "Search & Rescue / Trapped";
        }
        return "Emergency Incident";
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
                        Toast.makeText(getContext(), "BFP Responders are DEPLOYED and heading to your location!", Toast.LENGTH_LONG).show();
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
        autoSubmitVictimDetails(alertId, profile, null);
    }

    private void autoSubmitVictimDetails(String alertId, @Nullable UserProfile profile, @Nullable String incidentNotes) {
        if (alertId == null || !isAdded()) return;

        Context ctx = getContext();
        if (ctx == null) return;

        UserProfile victimProfile = profile != null ? profile : UserProfileManager.getLocalProfile(ctx);

        Map<String, Object> update = new HashMap<>();
        update.put("reporterRole", "victim");
        update.put("countOfVictims", 1);
        update.put("reportSubmitted", true);

        if (incidentNotes != null && !incidentNotes.trim().isEmpty()) {
            update.put("incidentNotes", incidentNotes.trim());
            update.put("description", incidentNotes.trim());
            update.put("accidentType", detectIncidentType(incidentNotes));
        }

        if (victimProfile != null && !victimProfile.isEmpty()) {
            if (!victimProfile.getFullName().isEmpty()) {
                update.put("reporterName", victimProfile.getFullName());
            }
            if (!victimProfile.getPhone().isEmpty()) {
                update.put("reporterPhone", victimProfile.getPhone());
            }
            update.put("reporterProfile", victimProfile.toMap());

            Map<String, Object> patientMap = victimProfile.toPatientMap();
            if (incidentNotes != null && !incidentNotes.trim().isEmpty()) {
                String existingCond = (String) patientMap.get("condition");
                patientMap.put("condition", (existingCond != null && !existingCond.isEmpty() ? existingCond + " | " : "") + "Incident: " + incidentNotes.trim());
            }
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
                    String msg = (incidentNotes != null && !incidentNotes.trim().isEmpty())
                            ? "SOS Dispatched! Incident details ('" + incidentNotes.trim() + "') sent to BFP."
                            : "SOS Sent! Your medical & personal details were automatically dispatched to BFP.";
                    Toast.makeText(getContext(), msg, Toast.LENGTH_LONG).show();
                })
                .addOnFailureListener(e -> {
                    if (!isAdded() || getContext() == null) return;
                    Log.e(TAG, "Failed auto-submitting victim details: " + e.getMessage());
                });
    }

    private void promptRoleSelection(String alertId) {
        if (!isAdded()) return;
        if (roleSelectionDialog != null && roleSelectionDialog.isAdded()) {
            try {
                roleSelectionDialog.dismiss();
            } catch (Exception ignored) {}
        }
        roleSelectionDialog = RoleSelectionDialog.newInstance(alertId);
        roleSelectionDialog.setOnRoleSelectedListener(new RoleSelectionDialog.OnRoleSelectedListener() {
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
        roleSelectionDialog.show(getChildFragmentManager(), "role_selection");
    }

    private void handleWearableRole(String role) {
        if (!isAdded() || getContext() == null) return;

        if (roleSelectionDialog != null && roleSelectionDialog.isAdded()) {
            try {
                roleSelectionDialog.dismiss();
            } catch (Exception ignored) {}
            roleSelectionDialog = null;
        }

        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if ("victim".equalsIgnoreCase(role)) {
            if (alertId != null) {
                autoSubmitVictimDetails(alertId, null);
            }
            Toast.makeText(getContext(), "🚑 Wearable: Victim details auto-transmitted to BFP.", Toast.LENGTH_LONG).show();
        } else if ("bystander".equalsIgnoreCase(role)) {
            if (alertId != null) {
                openAccidentReportSheet(alertId, "bystander", null);
            }
            Toast.makeText(getContext(), "👤 Wearable: Bystander report mode opened.", Toast.LENGTH_SHORT).show();
        }
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
        updateBleBadgeUI(null);
    }

    private void updateBleBadgeUI(@Nullable Intent intent) {
        if (bleBadge == null) return;
        boolean connected = false;
        boolean connecting = false;

        if (intent != null && intent.hasExtra(BLESOSService.EXTRA_CONNECTED)) {
            connected = intent.getBooleanExtra(BLESOSService.EXTRA_CONNECTED, false);
            connecting = intent.getBooleanExtra(BLESOSService.EXTRA_CONNECTING, false);
        } else {
            connected = BLESOSService.isDeviceConnected();
            connecting = BLESOSService.isDeviceConnecting();
        }

        bleBadge.setVisibility(View.VISIBLE);

        if (connected) {
            bleBadge.setBackgroundResource(R.drawable.bg_ble_badge_connected);
            if (dotBleStatus != null) dotBleStatus.setBackgroundResource(R.drawable.circle_dot_green);
            if (tvBleBadgeText != null) {
                tvBleBadgeText.setText("Respondo Wearable");
                tvBleBadgeText.setTextColor(0xFFFFFFFF);
            }
        } else if (connecting) {
            bleBadge.setBackgroundResource(R.drawable.bg_ble_badge_connecting);
            if (dotBleStatus != null) dotBleStatus.setBackgroundResource(R.drawable.circle_dot_orange);
            if (tvBleBadgeText != null) {
                tvBleBadgeText.setText("Connecting...");
                tvBleBadgeText.setTextColor(0xFFFFB74D);
            }
        } else {
            bleBadge.setBackgroundResource(R.drawable.bg_ble_badge_disconnected);
            if (dotBleStatus != null) dotBleStatus.setBackgroundResource(R.drawable.circle_dot_gray);
            if (tvBleBadgeText != null) {
                tvBleBadgeText.setText("Wearable Off");
                tvBleBadgeText.setTextColor(0xFFCCCCCC);
            }
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

        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showVoiceAssistant(isHandsFreeEnabled());
            return;
        }
        showVoiceAssistant(isHandsFreeEnabled());
    }

    private void showVoiceAssistant(boolean handsFreeMode) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).showVoiceAssistant(handsFreeMode);
            return;
        }
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
                                sendSOSAlert(role, null);
                            }

                            @Override
                            public void onSendSOSWithDetails(String incidentNotes, String role) {
                                sendSOSAlert(role, incidentNotes);
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
                                onOpenFirstAidDetail(item, false);
                            }

                            @Override
                            public void onOpenFirstAidDetail(FirstAidItem item, boolean autoPlayVideo) {
                                if (item != null && getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity())
                                            .openFirstAidDetail(item, autoPlayVideo);
                                } else if (item != null) {
                                    onSearchFirstAid(item.title);
                                }
                            }

                            @Override
                            public void onPlayVideo() {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).playCurrentVideo();
                                }
                            }

                            @Override
                            public void onPauseVideo() {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).pauseCurrentVideo();
                                }
                            }

                            @Override
                            public void onOpenProfile() {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).switchToProfile();
                                }
                            }

                            @Override
                            public void onOpenSOS() {
                                if (getActivity() instanceof MainActivity) {
                                    ((MainActivity) getActivity()).switchToSOS();
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
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setText("SOS");
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFFF3B30));

        startPulsingAnimation();

        if (tvInstruction != null) {
            tvInstruction.setVisibility(View.VISIBLE);
            tvInstruction.setAlpha(1f);
        }
        if (liveIndicatorRow != null) {
            liveIndicatorRow.setVisibility(View.GONE);
        }
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
        ssb.setSpan(new AbsoluteSizeSpan(24, true), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), 0, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);

        ssb.append("\n");
        int subStart = ssb.length();
        ssb.append(subtitle);
        ssb.setSpan(new AbsoluteSizeSpan(12, true), subStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        ssb.setSpan(new StyleSpan(Typeface.BOLD), subStart, ssb.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return ssb;
    }

    private void startEmergencyRadarPulse() {
        if (getContext() == null || btnSOS == null) return;

        Animation pulse = AnimationUtils.loadAnimation(
                requireContext(), R.anim.pulse);
        btnSOS.startAnimation(pulse);

        if (ringMid != null) {
            ringMid.setVisibility(View.VISIBLE);
            Animation ring1 = AnimationUtils.loadAnimation(
                    requireContext(), R.anim.pulse_ring_1);
            ringMid.startAnimation(ring1);
        }
        if (ringOuter != null) {
            ringOuter.setVisibility(View.VISIBLE);
            Animation ring2 = AnimationUtils.loadAnimation(
                    requireContext(), R.anim.pulse_ring_2);
            ringOuter.startAnimation(ring2);
        }
    }

    private void setSOSSentState() {
        if (getView() == null) return;

        // Smoothly fade out and hide "Hold to send emergency alert"
        if (tvInstruction != null) {
            tvInstruction.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (tvInstruction != null) tvInstruction.setVisibility(View.GONE);
            }).start();
        }

        // Remove Alert Active indicator completely
        if (liveIndicatorRow != null) {
            liveIndicatorRow.setVisibility(View.GONE);
        }
        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
        }

        btnSOS.setEnabled(false);
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSOS.setText(formatSOSButtonText("SOS", "SENT"));
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFF883333));

        // Smooth active dispatch radar rings
        startEmergencyRadarPulse();

        if (statusCard != null) {
            if (statusCard.getVisibility() != View.VISIBLE) {
                statusCard.setVisibility(View.VISIBLE);
                statusCard.setAlpha(0f);
                statusCard.setTranslationY(20f);
                statusCard.animate().alpha(1f).translationY(0f).setDuration(350)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }

        // Keep "Alert sent. Responders have been notified."
        if (tvStatus != null) {
            tvStatus.setText("Alert sent. Responders have been notified.");
        }

        // Keep accident report with smooth entrance animation
        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (btnOpenReport != null) {
            if (alertId != null && isReportSubmitted(alertId)) {
                btnOpenReport.setVisibility(View.GONE);
            } else {
                btnOpenReport.setVisibility(View.VISIBLE);
                btnOpenReport.setAlpha(0f);
                btnOpenReport.setTranslationY(12f);
                btnOpenReport.animate().alpha(1f).translationY(0f).setDuration(300).start();
            }
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
            btnOpenReport.animate().alpha(0f).translationY(10f).setDuration(250).withEndAction(() -> {
                if (btnOpenReport != null) btnOpenReport.setVisibility(View.GONE);
            }).start();
        }
    }

    private void setDeployedState() {
        if (getView() == null) return;

        // Smoothly fade out and hide "Hold to send emergency alert"
        if (tvInstruction != null) {
            tvInstruction.animate().alpha(0f).setDuration(250).withEndAction(() -> {
                if (tvInstruction != null) tvInstruction.setVisibility(View.GONE);
            }).start();
        }

        // Remove responder deployed indicator row completely
        if (liveIndicatorRow != null) {
            liveIndicatorRow.setVisibility(View.GONE);
        }
        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
        }

        btnSOS.setEnabled(false);
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 18);
        btnSOS.setText(formatSOSButtonText("SOS", "EN ROUTE"));
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFEA580C));

        // Smooth active dispatch radar rings
        startEmergencyRadarPulse();

        if (statusCard != null) {
            if (statusCard.getVisibility() != View.VISIBLE) {
                statusCard.setVisibility(View.VISIBLE);
                statusCard.setAlpha(0f);
                statusCard.setTranslationY(20f);
                statusCard.animate().alpha(1f).translationY(0f).setDuration(350)
                        .setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
            }
        }

        // Keep "Help is on the way!" — REMOVE firetruck emoji completely
        if (tvStatus != null) {
            tvStatus.setText("Help is on the way!\nBFP units are deployed and heading to your location.");
        }

        // Keep accident report with smooth entrance animation
        String alertId = sAlertId != null ? sAlertId : getSavedAlertId();
        if (btnOpenReport != null) {
            if (alertId != null && isReportSubmitted(alertId)) {
                btnOpenReport.setVisibility(View.GONE);
            } else {
                btnOpenReport.setVisibility(View.VISIBLE);
                btnOpenReport.setAlpha(0f);
                btnOpenReport.setTranslationY(12f);
                btnOpenReport.animate().alpha(1f).translationY(0f).setDuration(300).start();
            }
        }
    }

    private void setResolvedState() {
        if (getView() == null) return;

        if (liveIndicatorRow != null) {
            liveIndicatorRow.setVisibility(View.GONE);
        }
        View pulseDot = getView().findViewById(R.id.pulseDot);
        if (pulseDot != null) {
            pulseDot.clearAnimation();
        }

        btnSOS.setEnabled(true);
        btnSOS.animate().scaleX(1.0f).scaleY(1.0f).translationY(0f).setDuration(100).start();
        btnSOS.setText("SOS");
        btnSOS.setTextSize(TypedValue.COMPLEX_UNIT_SP, 46);
        btnSOS.setBackgroundTintList(
                android.content.res.ColorStateList
                        .valueOf(0xFFFF3B30));

        // Restore instruction smoothly
        if (tvInstruction != null) {
            tvInstruction.setVisibility(View.VISIBLE);
            tvInstruction.setAlpha(0f);
            tvInstruction.animate().alpha(1f).setDuration(350).start();
        }

        startPulsingAnimation();

        if (tvStatus != null) {
            tvStatus.setText("Alert resolved by BFP.");
        }

        Toast.makeText(requireContext(),
                "Alert resolved by BFP.",
                Toast.LENGTH_LONG).show();

        // Fade status card out smoothly after 4 seconds
        if (statusCard != null) {
            statusCard.postDelayed(() -> {
                if (getView() != null && statusCard != null) {
                    statusCard.animate().alpha(0f).translationY(16f).setDuration(400)
                            .withEndAction(() -> {
                                if (statusCard != null) statusCard.setVisibility(View.GONE);
                            }).start();
                }
            }, 4000);
        }
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
        if (getContext() != null) {
            requireContext()
                    .getSharedPreferences(PREFS,
                            Context.MODE_PRIVATE)
                    .edit()
                    .remove(KEY_ALERT)
                    .apply();
        }
        BLESOSService.syncStatusWithWearable("STATUS:RESOLVED");

        if (sListener != null) {
            sListener.remove();
            sListener = null;
        }
    }

    // ─────────────────────────────────────────────────────────
    @Override
    public void onDestroyView() {
        if (voiceAssistantDialog != null && !isHandsFreeEnabled()) {
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
