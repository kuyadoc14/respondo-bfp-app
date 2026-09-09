package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertDetailActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String            alertId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_alert_detail);

        db      = FirebaseFirestore.getInstance();
        alertId = getIntent().getStringExtra(
            "alertId");

        // Back
        View btnBack = findViewById(R.id.btnBack);
        if (btnBack != null)
            btnBack.setOnClickListener(
                v -> finish());

        // Load full data from Firestore
        // so we always have the latest report
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
                Toast.makeText(this,
                    "Failed to load: "
                        + e.getMessage(),
                    Toast.LENGTH_SHORT).show());
    }

    @SuppressWarnings("unchecked")
    private void bindData(DocumentSnapshot doc) {

        // ── Status ────────────────────────────────
        String status = doc.getString("status");
        boolean isActive = "active".equals(status);

        TextView tvStatus =
            findViewById(R.id.tvDetailStatus);
        if (tvStatus != null) {
            tvStatus.setText(
                isActive ? "ACTIVE" : "RESOLVED");
            tvStatus.setTextColor(
                isActive ? 0xFFFF3B30 : 0xFF34C759);
        }

        // ── Time ──────────────────────────────────
        TextView tvTime =
            findViewById(R.id.tvDetailTime);
        Object ts = doc.get("timestamp");
        if (tvTime != null
                && ts instanceof Timestamp) {
            tvTime.setText(
                new SimpleDateFormat(
                    "MMM dd, yyyy  hh:mm a",
                    Locale.getDefault()).format(
                        ((Timestamp) ts).toDate()));
        }

        // ── User ──────────────────────────────────
        TextView tvUser =
            findViewById(R.id.tvDetailUser);
        if (tvUser != null)
            tvUser.setText(doc.getString("userId"));

        // ── Coords ───────────────────────────────
        TextView tvCoords =
            findViewById(R.id.tvDetailCoords);
        Double lat = doc.getDouble("latitude");
        Double lng = doc.getDouble("longitude");
        if (tvCoords != null && lat != null) {
            tvCoords.setText(String.format(
                Locale.getDefault(),
                "%.5f, %.5f", lat, lng));
        }

        // ── Navigate button ───────────────────────
        View btnNav =
            findViewById(R.id.btnNavigate);
        if (btnNav != null && lat != null) {
            btnNav.setOnClickListener(v -> {
                String uri =
                    "google.navigation:q="
                    + lat + "," + lng
                    + "&mode=d";
                Intent i = new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(uri));
                i.setPackage(
                    "com.google.android.apps.maps");
                if (i.resolveActivity(
                        getPackageManager()) != null)
                    startActivity(i);
            });
        }

        // ── Resolve button ────────────────────────
        View btnResolve =
            findViewById(R.id.btnResolve);
        if (btnResolve != null) {
            if (isActive) {
                btnResolve.setVisibility(
                    View.VISIBLE);
                btnResolve.setOnClickListener(
                    v -> resolveAlert());
            } else {
                btnResolve.setVisibility(View.GONE);
            }
        }

        // ── Accident Report section ───────────────
        bindReportSection(doc);
    }

        @SuppressWarnings("unchecked")
    private void bindReportSection(
            DocumentSnapshot doc) {

        // Use View instead of LinearLayout
        // because reportSection is a CardView in XML
        View reportSection =
            findViewById(R.id.reportSection);
        if (reportSection == null) return;

        Object victims      = doc.get("countOfVictims");
        String accidentType = doc.getString("accidentType");
        String nature       = doc.getString("natureOfInjury");
        String mode         = doc.getString("modeOfInjury");
        List<Map<String, Object>> patients =
            (List<Map<String, Object>>)
                doc.get("patients");

        boolean hasReport = victims != null
            || accidentType != null
            || nature != null
            || mode != null
            || (patients != null
                && !patients.isEmpty());

        TextView tvNoReport =
            findViewById(R.id.tvNoReport);

        if (!hasReport) {
            if (tvNoReport != null)
                tvNoReport.setVisibility(View.VISIBLE);
            reportSection.setVisibility(View.GONE);
            return;
        }

        reportSection.setVisibility(View.VISIBLE);
        if (tvNoReport != null)
            tvNoReport.setVisibility(View.GONE);

        setField(R.id.tvAccidentTypeVal, accidentType);
        setField(R.id.tvVictimsVal,
            victims != null
                ? victims.toString() : null);
        setField(R.id.tvNatureVal, nature);
        setField(R.id.tvModeVal,   mode);

        // Patient cards
        LinearLayout patientsContainer =
            findViewById(R.id.patientsContainer);
        if (patientsContainer == null
                || patients == null
                || patients.isEmpty()) return;

        patientsContainer.removeAllViews();
        for (int i = 0; i < patients.size(); i++) {
            View card = buildPatientCard(
                i + 1, patients.get(i));
            patientsContainer.addView(card);
        }
    }

    private View buildPatientCard(
            int number,
            Map<String, Object> p) {

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(
            LinearLayout.VERTICAL);
        card.setBackgroundColor(0xFFF8F8F8);
        int pad = dp(16);
        card.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams lp =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams
                    .MATCH_PARENT,
                LinearLayout.LayoutParams
                    .WRAP_CONTENT);
        lp.setMargins(0, 0, 0, dp(12));
        card.setLayoutParams(lp);

        // Patient number header
        addCardRow(card,
            "Patient " + number, null, true);

        // Fields
        addCardRow(card, "Name",
            str(p.get("name")), false);
        addCardRow(card, "Age",
            str(p.get("age")), false);
        addCardRow(card, "Birthdate",
            str(p.get("birthdate")), false);
        addCardRow(card, "Address",
            str(p.get("address")), false);
        addCardRow(card, "Allergies",
            str(p.get("allergies")), false);
        addCardRow(card, "Emergency Contact",
            str(p.get("emergencyContact")), false);
        addCardRow(card,
            "Level of Consciousness",
            str(p.get("levelOfConsciousness")),
            false);
        addCardRow(card, "Condition",
            str(p.get("condition")), false);

        Object seizure = p.get("seizure");
        if (seizure != null) {
            addCardRow(card, "Seizure",
                Boolean.TRUE.equals(seizure)
                    ? "Yes" : "No",
                false);
        }

        return card;
    }

    private void addCardRow(
            LinearLayout parent,
            String label,
            String value,
            boolean isHeader) {

        if (!isHeader && (value == null
                || value.isEmpty())) return;

        if (isHeader) {
            TextView tv = new TextView(this);
            tv.setText(label);
            tv.setTextColor(0xFF1C1C1E);
            tv.setTextSize(13);
            tv.setTypeface(null,
                android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams lp =
                new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams
                        .WRAP_CONTENT,
                    LinearLayout.LayoutParams
                        .WRAP_CONTENT);
            lp.setMargins(0, 0, 0, dp(10));
            tv.setLayoutParams(lp);
            parent.addView(tv);
            return;
        }

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(
            LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rlp =
            new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams
                    .MATCH_PARENT,
                LinearLayout.LayoutParams
                    .WRAP_CONTENT);
        rlp.setMargins(0, 0, 0, dp(6));
        row.setLayoutParams(rlp);

        TextView tvLabel = new TextView(this);
        tvLabel.setText(label);
        tvLabel.setTextColor(0xFF8E8E93);
        tvLabel.setTextSize(12);
        LinearLayout.LayoutParams llp =
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams
                    .WRAP_CONTENT, 1f);
        tvLabel.setLayoutParams(llp);

        TextView tvVal = new TextView(this);
        tvVal.setText(value);
        tvVal.setTextColor(0xFF1C1C1E);
        tvVal.setTextSize(13);
        tvVal.setGravity(Gravity.END);
        LinearLayout.LayoutParams vlp =
            new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams
                    .WRAP_CONTENT, 1f);
        tvVal.setLayoutParams(vlp);

        row.addView(tvLabel);
        row.addView(tvVal);
        parent.addView(row);
    }

    private void setField(int viewId,
                           String value) {
        TextView tv = findViewById(viewId);
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

    private void resolveAlert() {
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
    }

    private int dp(int val) {
        return (int)(val *
            getResources()
                .getDisplayMetrics().density);
    }
}