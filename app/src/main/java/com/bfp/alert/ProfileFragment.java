package com.bfp.alert;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomsheet.BottomSheetDialog;
import com.google.android.material.button.MaterialButton;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.messaging.FirebaseMessaging;

import java.util.HashMap;
import java.util.Map;

public class ProfileFragment extends Fragment {

    private FrameLayout adminLoginOverlay;
    private FrameLayout adminLoginCardContainer;

    // Account Status Views
    private TextView tvAccountStatusTitle;
    private TextView tvAccountStatusSubtitle;
    private MaterialButton btnUserAuthAction;

    // Medical Profile Form Views
    private EditText etProfileName;
    private EditText etProfilePhone;
    private EditText etProfileAge;
    private EditText etProfileBirthdate;
    private EditText etProfileAddress;
    private EditText etProfileBloodType;
    private EditText etProfileAllergies;
    private EditText etProfileConditions;
    private EditText etProfileEmergencyName;
    private EditText etProfileEmergencyPhone;
    private MaterialButton btnSaveProfile;

    private LinearLayout cardPrivacyPrompt;
    private LinearLayout cardPrivacyAccepted;
    private LinearLayout layoutMedicalFormFields;
    private TextView tvPrivacyConsentDate;
    private MaterialButton btnReviewPrivacy;
    private TextView btnViewPrivacyPolicy;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_profile, container, false);

        adminLoginOverlay = view.findViewById(R.id.adminLoginOverlay);
        adminLoginCardContainer = view.findViewById(R.id.adminLoginCardContainer);

        // Account Views
        tvAccountStatusTitle = view.findViewById(R.id.tvAccountStatusTitle);
        tvAccountStatusSubtitle = view.findViewById(R.id.tvAccountStatusSubtitle);
        btnUserAuthAction = view.findViewById(R.id.btnUserAuthAction);

        // Medical Form Views
        etProfileName = view.findViewById(R.id.etProfileName);
        etProfilePhone = view.findViewById(R.id.etProfilePhone);
        etProfileAge = view.findViewById(R.id.etProfileAge);
        etProfileBirthdate = view.findViewById(R.id.etProfileBirthdate);
        etProfileAddress = view.findViewById(R.id.etProfileAddress);
        etProfileBloodType = view.findViewById(R.id.etProfileBloodType);
        etProfileAllergies = view.findViewById(R.id.etProfileAllergies);
        etProfileConditions = view.findViewById(R.id.etProfileConditions);
        etProfileEmergencyName = view.findViewById(R.id.etProfileEmergencyName);
        etProfileEmergencyPhone = view.findViewById(R.id.etProfileEmergencyPhone);
        btnSaveProfile = view.findViewById(R.id.btnSaveProfile);

        cardPrivacyPrompt = view.findViewById(R.id.cardPrivacyPrompt);
        cardPrivacyAccepted = view.findViewById(R.id.cardPrivacyAccepted);
        layoutMedicalFormFields = view.findViewById(R.id.layoutMedicalFormFields);
        tvPrivacyConsentDate = view.findViewById(R.id.tvPrivacyConsentDate);
        btnReviewPrivacy = view.findViewById(R.id.btnReviewPrivacy);
        btnViewPrivacyPolicy = view.findViewById(R.id.btnViewPrivacyPolicy);

        btnReviewPrivacy.setOnClickListener(v -> showPrivacyConsentDialog());
        if (btnViewPrivacyPolicy != null) {
            btnViewPrivacyPolicy.setOnClickListener(v -> showPrivacyConsentDialog());
        }

        // Load existing profile (local first, then cloud)
        loadProfileData();

        // Update Account UI
        updateAccountUI();

        // Account Auth button listener
        btnUserAuthAction.setOnClickListener(v -> {
            FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
            if (user != null) {
                // Sign out
                FirebaseAuth.getInstance().signOut();
                Toast.makeText(requireContext(), "Signed out", Toast.LENGTH_SHORT).show();
                updateAccountUI();
            } else {
                showCivilianAuthDialog();
            }
        });

        // Save Profile button listener
        btnSaveProfile.setOnClickListener(v -> saveProfileData());

        // Admin Access button listener
        view.findViewById(R.id.btnGoToAdmin)
                .setOnClickListener(v -> {
                    FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
                    if (user != null && user.getEmail() != null && user.getEmail().contains("bfp")) {
                        ((MainActivity) requireActivity()).openAdminDashboard();
                    } else {
                        showAdminLoginPopup();
                    }
                });

        adminLoginOverlay.setOnClickListener(v -> hideAdminLoginPopup());

        return view;
    }

    private void updateAccountUI() {
        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user != null) {
            String email = user.getEmail() != null ? user.getEmail() : "Authenticated User";
            tvAccountStatusTitle.setText("Account: " + email);
            tvAccountStatusSubtitle.setText("Profile synced with BFP cloud database.");
            btnUserAuthAction.setText("Sign Out");
        } else {
            tvAccountStatusTitle.setText("Guest Mode (Local Only)");
            tvAccountStatusSubtitle.setText("Profile stored locally. Sign in to sync across devices.");
            btnUserAuthAction.setText("Sign In / Register");
        }
    }

    private void loadProfileData() {
        Context context = getContext();
        if (context == null) return;

        UserProfile local = UserProfileManager.getLocalProfile(context);
        populateFields(local);
        applyPrivacyConsentState(local);

        UserProfileManager.loadProfile(context, new UserProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded()) {
                    populateFields(profile);
                    applyPrivacyConsentState(profile);
                }
            }

            @Override
            public void onFailure(Exception e) {
                // Keep local
            }
        });
    }

    private void applyPrivacyConsentState(@Nullable UserProfile profile) {
        boolean accepted = profile != null && profile.isPrivacyConsentAccepted();

        if (cardPrivacyPrompt != null) {
            cardPrivacyPrompt.setVisibility(accepted ? View.GONE : View.VISIBLE);
        }
        if (cardPrivacyAccepted != null) {
            cardPrivacyAccepted.setVisibility(accepted ? View.VISIBLE : View.GONE);
        }
        if (layoutMedicalFormFields != null) {
            layoutMedicalFormFields.setVisibility(accepted ? View.VISIBLE : View.GONE);
        }
        if (tvPrivacyConsentDate != null) {
            String date = profile != null ? profile.getPrivacyConsentDate() : "";
            if (accepted && !date.isEmpty()) {
                tvPrivacyConsentDate.setText("RA 10173 Data Privacy Consent Accepted • " + date);
            } else if (accepted) {
                tvPrivacyConsentDate.setText("RA 10173 Data Privacy Consent Accepted");
            }
        }
    }

    private void populateFields(UserProfile p) {
        if (p == null) return;
        if (!p.getFullName().isEmpty()) etProfileName.setText(p.getFullName());
        if (!p.getPhone().isEmpty()) etProfilePhone.setText(p.getPhone());
        if (!p.getAge().isEmpty()) etProfileAge.setText(p.getAge());
        if (!p.getBirthdate().isEmpty()) etProfileBirthdate.setText(p.getBirthdate());
        if (!p.getAddress().isEmpty()) etProfileAddress.setText(p.getAddress());
        if (!p.getBloodType().isEmpty()) etProfileBloodType.setText(p.getBloodType());
        if (!p.getAllergies().isEmpty()) etProfileAllergies.setText(p.getAllergies());
        if (!p.getMedicalConditions().isEmpty()) etProfileConditions.setText(p.getMedicalConditions());
        if (!p.getEmergencyContactName().isEmpty()) etProfileEmergencyName.setText(p.getEmergencyContactName());
        if (!p.getEmergencyContact().isEmpty()) etProfileEmergencyPhone.setText(p.getEmergencyContact());
    }

    private void saveProfileData() {
        Context context = getContext();
        if (context == null) return;

        if (!UserProfileManager.isPrivacyConsentAccepted(context)) {
            Toast.makeText(requireContext(),
                    "Please review and agree to the RA 10173 data privacy consent before saving your emergency profile.",
                    Toast.LENGTH_LONG).show();
            return;
        }

        UserProfile existing = UserProfileManager.getLocalProfile(context);
        UserProfile p = new UserProfile();
        p.setFullName(etProfileName.getText().toString().trim());
        p.setPhone(etProfilePhone.getText().toString().trim());
        p.setAge(etProfileAge.getText().toString().trim());
        p.setBirthdate(etProfileBirthdate.getText().toString().trim());
        p.setAddress(etProfileAddress.getText().toString().trim());
        p.setBloodType(etProfileBloodType.getText().toString().trim());
        p.setAllergies(etProfileAllergies.getText().toString().trim());
        p.setMedicalConditions(etProfileConditions.getText().toString().trim());
        p.setEmergencyContactName(etProfileEmergencyName.getText().toString().trim());
        p.setEmergencyContact(etProfileEmergencyPhone.getText().toString().trim());
        p.setPrivacyConsentAccepted(true);
        p.setPrivacyConsentDate(existing.getPrivacyConsentDate().isEmpty() ?
                new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date()) :
                existing.getPrivacyConsentDate());

        btnSaveProfile.setEnabled(false);
        btnSaveProfile.setText("Saving...");

        UserProfileManager.saveProfile(context, p, new UserProfileManager.ProfileCallback() {
            @Override
            public void onSuccess(UserProfile profile) {
                if (isAdded()) {
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setText("Save Emergency Profile");
                    Toast.makeText(requireContext(), "Emergency profile saved.", Toast.LENGTH_SHORT).show();
                }
            }

            @Override
            public void onFailure(Exception e) {
                if (isAdded()) {
                    btnSaveProfile.setEnabled(true);
                    btnSaveProfile.setText("Save Emergency Profile");
                    Toast.makeText(requireContext(), "Saved locally.", Toast.LENGTH_SHORT).show();
                }
            }
        });
    }

    private void showPrivacyConsentDialog() {
        PrivacyActDialog dialog = PrivacyActDialog.newInstance();
        dialog.setOnPrivacyConsentListener(new PrivacyActDialog.OnPrivacyConsentListener() {
            @Override
            public void onConsentGranted() {
                Context context = getContext();
                if (context == null) return;

                String date = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault()).format(new Date());
                UserProfile current = UserProfileManager.getLocalProfile(context);
                current.setPrivacyConsentAccepted(true);
                current.setPrivacyConsentDate(date);
                UserProfileManager.saveLocalProfile(context, current);
                applyPrivacyConsentState(current);
                Toast.makeText(requireContext(), "RA 10173 consent accepted.", Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onConsentDeclined() {
                Context context = getContext();
                if (context == null) return;

                UserProfile current = UserProfileManager.getLocalProfile(context);
                current.setPrivacyConsentAccepted(false);
                current.setPrivacyConsentDate("");
                UserProfileManager.saveLocalProfile(context, current);
                applyPrivacyConsentState(current);
                Toast.makeText(requireContext(), "Consent not accepted. Medical fields are hidden until you agree.", Toast.LENGTH_SHORT).show();
            }
        });
        dialog.show(getParentFragmentManager(), "privacy_consent");
    }

    // ── Optional Civilian Sign In / Register Dialog ─────────────────
    private void showCivilianAuthDialog() {
        BottomSheetDialog dialog = new BottomSheetDialog(requireContext(), R.style.Theme_BFPAlert_BottomSheet);
        View sheet = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_user_auth, null);
        dialog.setContentView(sheet);

        TextView tabSignIn = sheet.findViewById(R.id.tabSignIn);
        TextView tabRegister = sheet.findViewById(R.id.tabRegister);
        EditText etEmail = sheet.findViewById(R.id.etAuthEmail);
        EditText etPassword = sheet.findViewById(R.id.etAuthPassword);
        TextView tvError = sheet.findViewById(R.id.tvAuthError);
        MaterialButton btnSubmit = sheet.findViewById(R.id.btnAuthSubmit);
        MaterialButton btnCancel = sheet.findViewById(R.id.btnAuthCancel);

        final boolean[] isRegister = {false};

        tabSignIn.setOnClickListener(v -> {
            isRegister[0] = false;
            tabSignIn.setTextColor(getResources().getColor(R.color.text_primary));
            tabSignIn.setBackgroundResource(R.drawable.card_bg);
            tabRegister.setTextColor(getResources().getColor(R.color.text_secondary));
            tabRegister.setBackground(null);
            btnSubmit.setText("Sign In");
            tvError.setVisibility(View.GONE);
        });

        tabRegister.setOnClickListener(v -> {
            isRegister[0] = true;
            tabRegister.setTextColor(getResources().getColor(R.color.text_primary));
            tabRegister.setBackgroundResource(R.drawable.card_bg);
            tabSignIn.setTextColor(getResources().getColor(R.color.text_secondary));
            tabSignIn.setBackground(null);
            btnSubmit.setText("Create Account");
            tvError.setVisibility(View.GONE);
        });

        btnCancel.setOnClickListener(v -> dialog.dismiss());

        btnSubmit.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText("Please enter both email and password.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            if (password.length() < 6) {
                tvError.setText("Password must be at least 6 characters.");
                tvError.setVisibility(View.VISIBLE);
                return;
            }

            btnSubmit.setEnabled(false);
            btnSubmit.setText("Please wait...");
            tvError.setVisibility(View.GONE);

            FirebaseAuth auth = FirebaseAuth.getInstance();
            if (isRegister[0]) {
                // Register
                auth.createUserWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Create Account");
                            if (task.isSuccessful()) {
                                Toast.makeText(requireContext(), "Account created successfully!", Toast.LENGTH_SHORT).show();
                                updateAccountUI();
                                saveProfileData(); // Sync current profile to cloud
                                dialog.dismiss();
                            } else {
                                String msg = task.getException() != null ? task.getException().getMessage() : "Registration failed.";
                                tvError.setText(msg);
                                tvError.setVisibility(View.VISIBLE);
                            }
                        });
            } else {
                // Sign In
                auth.signInWithEmailAndPassword(email, password)
                        .addOnCompleteListener(task -> {
                            btnSubmit.setEnabled(true);
                            btnSubmit.setText("Sign In");
                            if (task.isSuccessful()) {
                                Toast.makeText(requireContext(), "Signed in successfully!", Toast.LENGTH_SHORT).show();
                                updateAccountUI();
                                loadProfileData(); // Fetch existing cloud profile
                                dialog.dismiss();
                            } else {
                                String msg = task.getException() != null ? task.getException().getMessage() : "Sign in failed.";
                                tvError.setText(msg);
                                tvError.setVisibility(View.VISIBLE);
                            }
                        });
            }
        });

        dialog.show();
    }

    // ── BFP Admin Login Popup ───────────────────────────────────────
    private void showAdminLoginPopup() {
        adminLoginCardContainer.removeAllViews();

        View loginView = LayoutInflater.from(requireContext())
                .inflate(R.layout.activity_admin_login, adminLoginCardContainer, false);
        FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT,
                Gravity.CENTER);
        params.setMargins(24, 0, 24, 0);
        loginView.setLayoutParams(params);

        bindLoginCard(loginView);
        adminLoginCardContainer.addView(loginView);
        adminLoginOverlay.setVisibility(View.VISIBLE);
    }

    private void hideAdminLoginPopup() {
        adminLoginOverlay.setVisibility(View.GONE);
        if (adminLoginCardContainer.getChildCount() > 0) {
            adminLoginCardContainer.removeAllViews();
        }
    }

    private void bindLoginCard(View loginView) {
        FirebaseAuth mAuth = FirebaseAuth.getInstance();
        EditText etEmail = loginView.findViewById(R.id.etEmail);
        EditText etPassword = loginView.findViewById(R.id.etPassword);
        MaterialButton btnLogin = loginView.findViewById(R.id.btnLogin);
        MaterialButton btnDismiss = loginView.findViewById(R.id.btnDismiss);
        TextView tvError = loginView.findViewById(R.id.tvLoginError);

        btnDismiss.setOnClickListener(v -> hideAdminLoginPopup());

        btnLogin.setOnClickListener(v -> {
            String email = etEmail.getText().toString().trim();
            String password = etPassword.getText().toString().trim();
            tvError.setText("");

            if (email.isEmpty() || password.isEmpty()) {
                tvError.setText("Please enter email and password.");
                return;
            }

            btnLogin.setEnabled(false);
            btnLogin.setText("Signing in...");

            mAuth.signInWithEmailAndPassword(email, password)
                    .addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            FirebaseMessaging.getInstance().getToken()
                                    .addOnSuccessListener(token -> {
                                        String uid = mAuth.getCurrentUser() != null
                                                ? mAuth.getCurrentUser().getUid()
                                                : "";
                                        if (!uid.isEmpty()) {
                                            Map<String, Object> data = new HashMap<>();
                                            data.put("fcmToken", token);
                                            FirebaseFirestore.getInstance()
                                                    .collection("admin_tokens")
                                                    .document(uid)
                                                    .set(data);
                                        }
                                    });

                            hideAdminLoginPopup();
                            ((MainActivity) requireActivity()).openAdminDashboard();
                        } else {
                            btnLogin.setEnabled(true);
                            btnLogin.setText("Sign In");
                            String msg = task.getException() != null
                                    ? task.getException().getMessage()
                                    : "Login failed.";

                            if (msg != null && msg.contains("password")) {
                                tvError.setText("Incorrect email or password.");
                            } else if (msg != null && msg.contains("email")) {
                                tvError.setText("Invalid email format.");
                            } else {
                                tvError.setText("Login failed. Try again.");
                            }
                        }
                    });
        });

        etPassword.setOnEditorActionListener((v, actionId, event) -> {
            btnLogin.performClick();
            return true;
        });
    }
}