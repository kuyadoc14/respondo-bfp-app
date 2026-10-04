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

    public interface OnReportSubmittedListener {
        void onReportSubmitted();
    }

    private OnReportSubmittedListener reportSubmittedListener;

    public void setOnReportSubmittedListener(OnReportSubmittedListener listener) {
        this.reportSubmittedListener = listener;
    }

    private static final String ARG_ALERT_ID = "alertId";
    private static final String ARG_ROLE = "role";
    private static final String ARG_PROFILE = "profile";

    private String              alertId;
    private String              role = "bystander";
    private UserProfile         userProfile;
    private FirebaseFirestore   db;
    private LinearLayout        patientContainer;
    private int                 patientCount = 0;

    // Bystander views
    private LinearLayout   sectionBystanderContact;
    private LinearLayout   containerBystanderFields;
    private SwitchMaterial switchIncludeBystanderInfo;
    private EditText       etBystanderName;
    private EditText       etBystanderPhone;

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

    // ── Factories ──────────────────────────────────
    public static AccidentReportSheet newInstance(String alertId) {
        return newInstance(alertId, "bystander", null);
    }

    public static AccidentReportSheet newInstance(String alertId, String role, UserProfile profile) {
        AccidentReportSheet sheet = new AccidentReportSheet();
        Bundle args = new Bundle();
        args.putString(ARG_ALERT_ID, alertId);
        args.putString(ARG_ROLE, role != null ? role : "bystander");
        if (profile != null) {
            args.putSerializable(ARG_PROFILE, profile);
        }
        sheet.setArguments(args);
        return sheet;
    }

    @Override
    public void onCreate(
            @Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        db = FirebaseFirestore.getInstance();
        if (getArguments() != null) {
            alertId = getArguments().getString(ARG_ALERT_ID);
            role = getArguments().getString(ARG_ROLE, "bystander");
            userProfile = (UserProfile) getArguments().getSerializable(ARG_PROFILE);
        }
        if (userProfile == null && getContext() != null) {
            userProfile = UserProfileManager.getLocalProfile(getContext());
        }

        // Full screen bottom sheet
        setStyle(STYLE_NORMAL, R.style.Theme_BFPAlert_BottomSheet);
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

        patientContainer = view.findViewById(R.id.patientContainer);

        // Role banner setup
        TextView tvRoleBannerText = view.findViewById(R.id.tvRoleBannerText);
        if ("victim".equalsIgnoreCase(role)) {
            if (tvRoleBannerText != null) {
                tvRoleBannerText.setText("🙋 Patient 1 pre-filled from your profile. Feel free to adjust.");
            }
        } else {
            if (tvRoleBannerText != null) {
                tvRoleBannerText.setText("👥 Bystander Report. Please record incident details.");
            }
        }

        // Bystander section setup
        sectionBystanderContact = view.findViewById(R.id.sectionBystanderContact);
        containerBystanderFields = view.findViewById(R.id.containerBystanderFields);
        switchIncludeBystanderInfo = view.findViewById(R.id.switchIncludeBystanderInfo);
        etBystanderName = view.findViewById(R.id.etBystanderName);
        etBystanderPhone = view.findViewById(R.id.etBystanderPhone);

        if ("bystander".equalsIgnoreCase(role)) {
            if (sectionBystanderContact != null) {
                sectionBystanderContact.setVisibility(View.VISIBLE);
            }
            if (userProfile != null) {
                if (etBystanderName != null && !userProfile.getFullName().isEmpty()) {
                    etBystanderName.setText(userProfile.getFullName());
                }
                if (etBystanderPhone != null && !userProfile.getPhone().isEmpty()) {
                    etBystanderPhone.setText(userProfile.getPhone());
                }
            }
            if (switchIncludeBystanderInfo != null) {
                switchIncludeBystanderInfo.setOnCheckedChangeListener((btn, isChecked) -> {
                    if (containerBystanderFields != null) {
                        containerBystanderFields.setVisibility(isChecked ? View.VISIBLE : View.GONE);
                    }
                });
            }
        } else {
            if (sectionBystanderContact != null) {
                sectionBystanderContact.setVisibility(View.GONE);
            }
        }

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

        // Close button in header
        View btnClose = view.findViewById(R.id.btnCloseReport);
        if (btnClose != null) {
            btnClose.setOnClickListener(v -> dismiss());
        }

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
        pv.etName = card.findViewById(R.id.etPatientName);
        pv.etAge = card.findViewById(R.id.etPatientAge);
        pv.etBirthdate = card.findViewById(R.id.etPatientBirthdate);
        pv.etAddress = card.findViewById(R.id.etPatientAddress);
        pv.etAllergies = card.findViewById(R.id.etPatientAllergies);
        pv.etEmergencyContact = card.findViewById(R.id.etEmergencyContact);
        pv.etCondition = card.findViewById(R.id.etPatientCondition);
        pv.chipGroupConsciousness = card.findViewById(R.id.chipGroupConsciousness);
        pv.switchSeizure = card.findViewById(R.id.switchSeizure);

        // Patient number label
        TextView tvNum = card.findViewById(R.id.tvPatientNumber);
        tvNum.setText("Patient " + patientCount);

        // Pre-fill Patient 1 if user is the victim
        if (patientCount == 1 && "victim".equalsIgnoreCase(role) && userProfile != null && !userProfile.isEmpty()) {
            if (!userProfile.getFullName().isEmpty()) pv.etName.setText(userProfile.getFullName());
            if (!userProfile.getAge().isEmpty()) pv.etAge.setText(userProfile.getAge());
            if (!userProfile.getBirthdate().isEmpty()) pv.etBirthdate.setText(userProfile.getBirthdate());
            if (!userProfile.getAddress().isEmpty()) pv.etAddress.setText(userProfile.getAddress());
            if (!userProfile.getAllergies().isEmpty()) pv.etAllergies.setText(userProfile.getAllergies());

            String contactInfo = userProfile.getEmergencyContact();
            if (!userProfile.getEmergencyContactName().isEmpty()) {
                contactInfo = userProfile.getEmergencyContactName() + (!contactInfo.isEmpty() ? " (" + contactInfo + ")" : "");
            }
            if (!contactInfo.isEmpty()) pv.etEmergencyContact.setText(contactInfo);

            String cond = userProfile.getMedicalConditions();
            if (!userProfile.getBloodType().isEmpty()) {
                cond = (cond.isEmpty() ? "" : cond + "\n") + "Blood Type: " + userProfile.getBloodType();
            }
            if (!cond.isEmpty()) pv.etCondition.setText(cond);
        }

        // Remove button
        TextView btnRemove = card.findViewById(R.id.btnRemovePatient);

        // Hide remove on first card
        if (patientCount == 1) {
            btnRemove.setVisibility(View.GONE);
        }

        btnRemove.setOnClickListener(v -> {
            int idx = patientViews.indexOf(pv);
            if (idx != -1) {
                patientContainer.removeView(card);
                patientViews.remove(idx);
                renumberPatients();
            }
        });

        patientViews.add(pv);
        patientContainer.addView(card);
    }

    // Renumber patient cards after removal
    private void renumberPatients() {
        patientCount = 0;
        for (int i = 0; i < patientContainer.getChildCount(); i++) {
            View card = patientContainer.getChildAt(i);
            TextView tvNum = card.findViewById(R.id.tvPatientNumber);
            if (tvNum != null) {
                patientCount++;
                tvNum.setText("Patient " + patientCount);
            }
            TextView btnRemove = card.findViewById(R.id.btnRemovePatient);
            if (btnRemove != null) {
                btnRemove.setVisibility(patientCount == 1 ? View.GONE : View.VISIBLE);
            }
        }
    }

    // ── Helper: get selected chip text ────────────
    private String getSelectedChipText(ChipGroup group) {
        int id = group.getCheckedChipId();
        if (id == View.NO_ID) return null;
        Chip chip = group.findViewById(id);
        return chip != null ? chip.getText().toString() : null;
    }

    // ── Submit report to Firestore ────────────────
    private void submitReport(View root) {
        if (alertId == null) {
            Toast.makeText(requireContext(),
                "Error: No active alert.",
                Toast.LENGTH_SHORT).show();
            dismiss();
            return;
        }

        // ── Incident fields ───────────────────────
        ChipGroup cgAccident = root.findViewById(R.id.chipGroupAccidentType);
        ChipGroup cgInjury   = root.findViewById(R.id.chipGroupNature);
        ChipGroup cgMode     = root.findViewById(R.id.chipGroupMode);
        EditText  etVictims  = root.findViewById(R.id.etVictimCount);

        String accidentType   = getSelectedChipText(cgAccident);
        String natureOfInjury = getSelectedChipText(cgInjury);
        String modeOfInjury   = getSelectedChipText(cgMode);

        String victimCountStr = etVictims.getText().toString().trim();
        int victimCount = victimCountStr.isEmpty()
            ? patientViews.size()
            : Integer.parseInt(victimCountStr);

        // ── Patient fields ────────────────────────
        List<Map<String, Object>> patients = new ArrayList<>();
        for (PatientView pv : patientViews) {
            String name             = pv.etName.getText().toString().trim();
            String age              = pv.etAge.getText().toString().trim();
            String birthdate        = pv.etBirthdate.getText().toString().trim();
            String address          = pv.etAddress.getText().toString().trim();
            String allergies        = pv.etAllergies.getText().toString().trim();
            String emergencyContact = pv.etEmergencyContact.getText().toString().trim();
            String condition        = pv.etCondition.getText().toString().trim();
            String consciousness    = getSelectedChipText(pv.chipGroupConsciousness);
            boolean seizure         = pv.switchSeizure.isChecked();

            Map<String, Object> patient = new HashMap<>();
            patient.put("seizure", seizure);

            if (!name.isEmpty()) patient.put("name", name);
            if (!age.isEmpty()) {
                try {
                    patient.put("age", Integer.parseInt(age));
                } catch (NumberFormatException ignored) {}
            }
            if (!birthdate.isEmpty()) patient.put("birthdate", birthdate);
            if (!address.isEmpty()) patient.put("address", address);
            if (!allergies.isEmpty()) patient.put("allergies", allergies);
            if (!emergencyContact.isEmpty()) patient.put("emergencyContact", emergencyContact);
            if (!condition.isEmpty()) patient.put("condition", condition);
            if (consciousness != null) patient.put("levelOfConsciousness", consciousness);

            if (patient.size() > 1 || !name.isEmpty() || !condition.isEmpty()) {
                patients.add(patient);
            }
        }

        // ── Build update map ──────────────────────
        Map<String, Object> update = new HashMap<>();
        update.put("countOfVictims", victimCount);
        if (accidentType != null) update.put("accidentType", accidentType);
        if (natureOfInjury != null) update.put("natureOfInjury", natureOfInjury);
        if (modeOfInjury != null) update.put("modeOfInjury", modeOfInjury);
        if (!patients.isEmpty()) update.put("patients", patients);

        // Role & Reporter details
        update.put("reporterRole", role);

        if ("victim".equalsIgnoreCase(role)) {
            if (userProfile != null && !userProfile.isEmpty()) {
                update.put("reporterName", userProfile.getFullName());
                update.put("reporterPhone", userProfile.getPhone());
                update.put("reporterProfile", userProfile.toMap());
            }
        } else if ("bystander".equalsIgnoreCase(role)) {
            if (switchIncludeBystanderInfo != null && switchIncludeBystanderInfo.isChecked()) {
                String bName = etBystanderName != null ? etBystanderName.getText().toString().trim() : "";
                String bPhone = etBystanderPhone != null ? etBystanderPhone.getText().toString().trim() : "";
                if (!bName.isEmpty()) update.put("reporterName", bName);
                if (!bPhone.isEmpty()) update.put("reporterPhone", bPhone);
            }
        }

        // ── Update Firestore document ─────────────
        update.put("reportSubmitted", true);
        db.collection("sos_alerts")
            .document(alertId)
            .update(update)
            .addOnSuccessListener(u -> {
                Toast.makeText(requireContext(),
                    "Report sent to BFP.",
                    Toast.LENGTH_SHORT).show();
                if (reportSubmittedListener != null) {
                    reportSubmittedListener.onReportSubmitted();
                }
                dismiss();
            })
            .addOnFailureListener(e ->
                Toast.makeText(requireContext(),
                    "Failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show());
    }
}