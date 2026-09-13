package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertDetailFragment extends Fragment {

    private FirebaseFirestore db;
    private String alertId;

    public static AlertDetailFragment newInstance(String alertId, Map<String, Object> data) {
        AlertDetailFragment fragment = new AlertDetailFragment();
        Bundle args = new Bundle();
        args.putString("alertId", alertId);
        if (data != null) {
            if (data.get("status") != null) {
                args.putString("status", (String) data.get("status"));
            }
            if (data.get("userId") != null) {
                args.putString("userId", (String) data.get("userId"));
            }
            if (data.get("latitude") != null) {
                args.putDouble("latitude", ((Number) data.get("latitude")).doubleValue());
            }
            if (data.get("longitude") != null) {
                args.putDouble("longitude", ((Number) data.get("longitude")).doubleValue());
            }
            if (data.get("timestamp") != null) {
                Object ts = data.get("timestamp");
                if (ts instanceof Timestamp) {
                    args.putLong("timestamp", ((Timestamp) ts).toDate().getTime());
                }
            }
        }
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_alert_detail, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        db = FirebaseFirestore.getInstance();
        alertId = requireArguments().getString("alertId");

        View btnBack = view.findViewById(R.id.btnBack);
        if (btnBack != null) {
            btnBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        }

        View btnDetailBack = view.findViewById(R.id.btnDetailBack);
        if (btnDetailBack != null) {
            btnDetailBack.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());
        }

        if (alertId != null) {
            loadAlertData();
        }
    }

    private void loadAlertData() {
        db.collection("sos_alerts")
                .document(alertId)
                .get()
                .addOnSuccessListener(doc -> {
                    if (!doc.exists()) return;
                    bindData(doc);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(),
                                "Failed to load: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    @SuppressWarnings("unchecked")
    private void bindData(DocumentSnapshot doc) {
        String status = doc.getString("status");
        boolean isActive = "active".equals(status);

        TextView tvStatus = requireView().findViewById(R.id.tvDetailStatus);
        if (tvStatus != null) {
            tvStatus.setText(isActive ? "ACTIVE" : "RESOLVED");
            tvStatus.setTextColor(isActive ? 0xFFFF3B30 : 0xFF34C759);
        }

        TextView tvTime = requireView().findViewById(R.id.tvDetailTime);
        Object ts = doc.get("timestamp");
        if (tvTime != null && ts instanceof Timestamp) {
            tvTime.setText(new SimpleDateFormat("MMM dd, yyyy  hh:mm a", Locale.getDefault())
                    .format(((Timestamp) ts).toDate()));
        }

        TextView tvUser = requireView().findViewById(R.id.tvDetailUser);
        if (tvUser != null) {
            tvUser.setText(doc.getString("userId"));
        }

        TextView tvCoords = requireView().findViewById(R.id.tvDetailCoords);
        Double lat = doc.getDouble("latitude");
        Double lng = doc.getDouble("longitude");
        if (tvCoords != null && lat != null) {
            tvCoords.setText(String.format(Locale.getDefault(), "%.5f, %.5f", lat, lng));
        }

        View btnNav = requireView().findViewById(R.id.btnNavigate);
        if (btnNav != null && lat != null) {
            btnNav.setOnClickListener(v -> {
                String uri = "google.navigation:q=" + lat + "," + lng + "&mode=d";
                Intent i = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                i.setPackage("com.google.android.apps.maps");
                if (i.resolveActivity(requireActivity().getPackageManager()) != null) {
                    startActivity(i);
                }
            });
        }

        View btnStation = requireView().findViewById(R.id.btnDirectionsFromStation);
        if (btnStation != null && lat != null) {
            final double finalLat = lat;
            final double finalLng = lng;
            btnStation.setOnClickListener(v -> loadStationAndNavigate(finalLat, finalLng));
        }

        View btnResolve = requireView().findViewById(R.id.btnResolve);
        if (btnResolve != null) {
            if (isActive) {
                btnResolve.setVisibility(View.VISIBLE);
                btnResolve.setOnClickListener(v -> resolveAlert());
            } else {
                btnResolve.setVisibility(View.GONE);
            }
        }

        bindReportSection(doc);
    }

    @SuppressWarnings("unchecked")
    private void bindReportSection(DocumentSnapshot doc) {
        View reportSection = requireView().findViewById(R.id.reportSection);
        if (reportSection == null) return;

        Object victims = doc.get("countOfVictims");
        String accidentType = doc.getString("accidentType");
        String nature = doc.getString("natureOfInjury");
        String mode = doc.getString("modeOfInjury");
        List<Map<String, Object>> patients = (List<Map<String, Object>>) doc.get("patients");

        boolean hasReport = victims != null || accidentType != null || nature != null || mode != null
                || (patients != null && !patients.isEmpty());

        TextView tvNoReport = requireView().findViewById(R.id.tvNoReport);
        if (!hasReport) {
            if (tvNoReport != null) tvNoReport.setVisibility(View.VISIBLE);
            reportSection.setVisibility(View.GONE);
            return;
        }

        reportSection.setVisibility(View.VISIBLE);
        if (tvNoReport != null) tvNoReport.setVisibility(View.GONE);

        setField(R.id.tvAccidentTypeVal, accidentType);
        setField(R.id.tvVictimsVal, victims != null ? victims.toString() : null);
        setField(R.id.tvNatureVal, nature);
        setField(R.id.tvModeVal, mode);

        LinearLayout patientsContainer = requireView().findViewById(R.id.patientsContainer);
        if (patientsContainer == null || patients == null || patients.isEmpty()) return;

        patientsContainer.removeAllViews();
        for (int i = 0; i < patients.size(); i++) {
            View card = buildPatientCard(i + 1, patients.get(i));
            patientsContainer.addView(card);
        }
    }

    private View buildPatientCard(int number, Map<String, Object> p) {
        LinearLayout card = new LinearLayout(requireContext());
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFF8F8F8);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);

        addCardRow(card, "Patient " + number, null, true);
        addCardRow(card, "Name", str(p.get("name")), false);
        addCardRow(card, "Age", str(p.get("age")), false);
        addCardRow(card, "Birthdate", str(p.get("birthdate")), false);
        addCardRow(card, "Address", str(p.get("address")), false);
        addCardRow(card, "Allergies", str(p.get("allergies")), false);
        addCardRow(card, "Emergency Contact", str(p.get("emergencyContact")), false);
        addCardRow(card, "Level of Consciousness", str(p.get("levelOfConsciousness")), false);
        addCardRow(card, "Condition", str(p.get("condition")), false);

        Object seizure = p.get("seizure");
        if (seizure != null) {
            addCardRow(card, "Seizure", Boolean.TRUE.equals(seizure) ? "Yes" : "No", false);
        }

        return card;
    }

    private void addCardRow(LinearLayout parent, String label, String value, boolean isHeader) {
        if (!isHeader && (value == null || value.isEmpty())) return;

        if (isHeader) {
            TextView tv = new TextView(requireContext());
            tv.setText(label);
            tv.setTextColor(0xFF1C1C1E);
            tv.setTextSize(13);
            tv.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(10));
            tv.setLayoutParams(lp);
            parent.addView(tv);
            return;
        }

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rlp);

        TextView tvLabel = new TextView(requireContext());
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF8E8E93);
        tvLabel.setTextSize(12);
        LinearLayout.LayoutParams llp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(llp);

        TextView tvVal = new TextView(requireContext());
        tvVal.setText(value);
        tvVal.setTextColor(0xFF1C1C1E);
        tvVal.setTextSize(13);
        tvVal.setGravity(Gravity.END);
        LinearLayout.LayoutParams vlp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tvVal.setLayoutParams(vlp);

        row.addView(tvLabel);
        row.addView(tvVal);
        parent.addView(row);
    }

    private void setField(int viewId, String value) {
        TextView tv = requireView().findViewById(viewId);
        if (tv == null) return;
        if (value != null && !value.isEmpty()) {
            tv.setText(value);
            tv.setVisibility(View.VISIBLE);
        } else {
            tv.setVisibility(View.GONE);
        }
    }

    private String str(Object o) {
        return o != null ? o.toString() : null;
    }

    private void loadStationAndNavigate(double destLat, double destLng) {
        Toast.makeText(requireContext(), "Loading BFP station...", Toast.LENGTH_SHORT).show();

        db.collection("bfp_stations")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Toast.makeText(requireContext(), "No BFP station found in Firestore.", Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot station = snap.getDocuments().get(0);
                    Double sLat = station.getDouble("latitude");
                    Double sLng = station.getDouble("longitude");

                    if (sLat == null || sLng == null) {
                        Toast.makeText(requireContext(), "Station coordinates not set.", Toast.LENGTH_SHORT).show();
                        return;
                    }

                    String uri = "https://www.google.com/maps/dir/" + sLat + "," + sLng + "/" + destLat + "," + destLng;
                    Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(uri));
                    intent.setPackage("com.google.android.apps.maps");

                    if (intent.resolveActivity(requireActivity().getPackageManager()) != null) {
                        startActivity(intent);
                    } else {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(uri)));
                    }
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private void resolveAlert() {
        db.collection("sos_alerts")
                .document(alertId)
                .update("status", "resolved")
                .addOnSuccessListener(u -> {
                    Toast.makeText(requireContext(), "Alert resolved.", Toast.LENGTH_SHORT).show();
                    requireActivity().getSupportFragmentManager().popBackStack();
                })
                .addOnFailureListener(e ->
                        Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show());
    }

    private int dp(int val) {
        return (int) (val * getResources().getDisplayMetrics().density);
    }
}
