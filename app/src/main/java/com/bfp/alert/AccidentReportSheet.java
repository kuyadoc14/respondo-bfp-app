package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.material.bottomsheet.BottomSheetDialogFragment;
import com.google.android.material.button.MaterialButton;
import com.google.android.material.chip.Chip;
import com.google.android.material.chip.ChipGroup;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AccidentReportSheet
        extends BottomSheetDialogFragment {

    // The alert ID created when SOS was sent
    // We update this document with the report
    private static final String ARG_ALERT_ID =
        "alertId";

    private String              alertId;
    private FirebaseFirestore   db;
    private LinearLayout        patientContainer;
    private int                 patientCount = 0;

    // Patient view holder for easy reading
    private static class PatientView {
        EditText     etName, etAge, etBirthdate,
                     etAddress, etAllergies,
                     etEmergencyContact,
                     etCondition;
        ChipGroup    chipGroupConsciousness;
        SwitchMaterial switchSeizure;
    }

    private final List<PatientView> patientViews =
        new ArrayList<>();

    // ── Factory ──────────────────────────────────
    public static AccidentReportSheet newInstance(
            String alertId) {
        AccidentReportSheet sheet =
            new AccidentReportSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ALERT_ID, alertId);
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        if (getArguments() != null) {
            alertId = getArguments()
                .getString(ARG_ALERT_ID);
        }
        // Full screen bottom sheet
        setStyle(STYLE_NORMAL,
            R.style.Theme_BFPAlert_BottomSheet);
    }

    @Nullable
    @Override
    public View onCreateView(
            @NonNull LayoutInflater inflater,
            @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {

        View view = inflater.inflate(
            R.layout.layout_accident_report,
            container, false);

        patientContainer =
            view.findViewById(R.id.patientContainer);

        // Add first patient card by default
        addPatientCard();

        // Add patient button
        MaterialButton btnAdd =
            view.findViewById(R.id.btnAddPatient);
        btnAdd.setOnClickListener(v ->
            addPatientCard());

        // Submit
        MaterialButton btnSubmit =
            view.findViewById(R.id.btnSubmitReport);
        btnSubmit.setOnClickListener(v ->
            submitReport(view));

        // Skip
        MaterialButton btnSkip =
            view.findViewById(R.id.btnSkipReport);
        btnSkip.setOnClickListener(v -> dismiss());

        return view;
    }

    // ── Add a patient card ────────────────────────
    private void addPatientCard() {
        patientCount++;

        View card = LayoutInflater.from(
            requireContext()).inflate(
                R.layout.layout_patient_card,
                patientContainer, false);

        PatientView pv = new PatientView();
        pv.etName = card.findViewById(
            R.id.etPatientName);
        pv.etAge = card.findViewById(
            R.id.etPatientAge);
        pv.etBirthdate = card.findViewById(
            R.id.etPatientBirthdate);
        pv.etAddress = card.findViewById(
            R.id.etPatientAddress);
        pv.etAllergies = card.findViewById(
            R.id.etPatientAllergies);
        pv.etEmergencyContact = card.findViewById(
            R.id.etEmergencyContact);
        pv.etCondition = card.findViewById(
            R.id.etPatientCondition);
        pv.chipGroupConsciousness = card.findViewById(
            R.id.chipGroupConsciousness);
        pv.switchSeizure = card.findViewById(
            R.id.switchSeizure);

        // Patient number label
        TextView tvNum = card.findViewById(
            R.id.tvPatientNumber);
        tvNum.setText("Patient " + patientCount);

        // Remove button
        TextView btnRemove = card.findViewById(
            R.id.btnRemovePatient);

        // Hide remove on first card
        if (patientCount == 1) {
            btnRemove.setVisibility(View.GONE);
        }

        final int index = patientViews.size();
        btnRemove.setOnClickListener(v -> {
            patientContainer.removeView(card);
            patientViews.remove(index);
            renumberPatients();
        });

        patientViews.add(pv);
        patientContainer.addView(card);
    }

    // Renumber patient cards after removal
    private void renumberPatients() {
        patientCount = 0;
        for (int i = 0;
                i < patientContainer.getChildCount();
                i++) {
            View card =
                patientContainer.getChildAt(i);
            TextView tvNum = card.findViewById(
                R.id.tvPatientNumber);
            if (tvNum != null) {
                patientCount++;
                tvNum.setText(
                    "Patient " + patientCount);
            }
            // Show remove on all except first
            TextView btnRemove = card.findViewById(
                R.id.btnRemovePatient);
            if (btnRemove != null) {
                btnRemove.setVisibility(
                    i == 0 ? View.GONE : View.VISIBLE);
            }
        }
    }

    // ── Read selected chip text ───────────────────
    private String getSelectedChip(
            ChipGroup group) {
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return null;
        Chip chip = group.findViewById(id);
        return chip != null
            ? chip.getText().toString() : null;
    }

    // ── Submit report to Firestore ────────────────
    private void submitReport(View root) {
        if (alertId == null) {
            Toast.makeText(requireContext(),
                "No SOS alert linked.",
                Toast.LENGTH_SHORT).show();
            return;
        }

        // ── Validate victim count ─────────────────
        EditText etCount =
            root.findViewById(R.id.etVictimCount);
        String countStr = etCount.getText()
            .toString().trim();
        if (countStr.isEmpty()) {
            etCount.setError("Required");
            etCount.requestFocus();
            return;
        }
        int victimCount =
            Integer.parseInt(countStr);

        // ── Read incident fields ──────────────────
        ChipGroup cgType =
            root.findViewById(
                R.id.chipGroupAccidentType);
        ChipGroup cgNature =
            root.findViewById(R.id.chipGroupNature);
        ChipGroup cgMode =
            root.findViewById(R.id.chipGroupMode);

        String accidentType =
            getSelectedChip(cgType);
        String natureOfInjury =
            getSelectedChip(cgNature);
        String modeOfInjury =
            getSelectedChip(cgMode);

        // ── Build patients list ───────────────────
        List<Map<String, Object>> patients =
            new ArrayList<>();

        for (PatientView pv : patientViews) {
            Map<String, Object> patient =
                new HashMap<>();

            String name = pv.etName.getText()
                .toString().trim();
            String age = pv.etAge.getText()
                .toString().trim();
            String birthdate =
                pv.etBirthdate.getText()
                    .toString().trim();
            String address = pv.etAddress.getText()
                .toString().trim();
            String allergies =
                pv.etAllergies.getText()
                    .toString().trim();
            String emergencyContact =
                pv.etEmergencyContact.getText()
                    .toString().trim();
            String condition =
                pv.etCondition.getText()
                    .toString().trim();
            String consciousness =
                getSelectedChip(
                    pv.chipGroupConsciousness);
            boolean seizure =
                pv.switchSeizure.isChecked();

            // Only add non-empty fields
            if (!name.isEmpty())
                patient.put("name", name);
            if (!age.isEmpty())
                patient.put("age",
                    Integer.parseInt(age));
            if (!birthdate.isEmpty())
                patient.put("birthdate", birthdate);
            if (!address.isEmpty())
                patient.put("address", address);
            if (!allergies.isEmpty())
                patient.put("allergies",
                    allergies);
            if (!emergencyContact.isEmpty())
                patient.put("emergencyContact",
                    emergencyContact);
            if (!condition.isEmpty())
                patient.put("condition", condition);
            if (consciousness != null)
                patient.put("levelOfConsciousness",
                    consciousness);
            patient.put("seizure", seizure);

            // Only include if at least one field
            if (patient.size() > 1)
                patients.add(patient);
        }

        // ── Build update map ──────────────────────
        Map<String, Object> update =
            new HashMap<>();
        update.put("countOfVictims", victimCount);
        if (accidentType != null)
            update.put("accidentType",
                accidentType);
        if (natureOfInjury != null)
            update.put("natureOfInjury",
                natureOfInjury);
        if (modeOfInjury != null)
            update.put("modeOfInjury",
                modeOfInjury);
        if (!patients.isEmpty())
            update.put("patients", patients);

        // ── Update Firestore document ─────────────
        db.collection("sos_alerts")
            .document(alertId)
            .update(update)
            .addOnSuccessListener(u -> {
                Toast.makeText(requireContext(),
                    "Report sent to BFP.",
                    Toast.LENGTH_SHORT).show();
                dismiss();
            })
            .addOnFailureListener(e ->
                Toast.makeText(requireContext(),
                    "Failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show());
    }
}