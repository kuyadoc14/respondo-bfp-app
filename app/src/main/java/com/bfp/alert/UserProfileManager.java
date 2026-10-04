package com.bfp.alert;

import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;

import androidx.annotation.NonNull;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.SetOptions;

import java.util.HashMap;
import java.util.Map;

public class UserProfileManager {

    private static final String TAG = "UserProfileManager";
    private static final String PREF_NAME = "bfp_user_profile";

    private static final String KEY_FULL_NAME = "full_name";
    private static final String KEY_AGE = "age";
    private static final String KEY_BIRTHDATE = "birthdate";
    private static final String KEY_ADDRESS = "address";
    private static final String KEY_PHONE = "phone";
    private static final String KEY_EMERGENCY_CONTACT = "emergency_contact";
    private static final String KEY_EMERGENCY_CONTACT_NAME = "emergency_contact_name";
    private static final String KEY_ALLERGIES = "allergies";
    private static final String KEY_BLOOD_TYPE = "blood_type";
    private static final String KEY_MEDICAL_CONDITIONS = "medical_conditions";
    private static final String KEY_PRIVACY_ACCEPTED = "privacy_consent_accepted";
    private static final String KEY_PRIVACY_DATE = "privacy_consent_date";

    public interface ProfileCallback {
        void onSuccess(UserProfile profile);
        void onFailure(Exception e);
    }

    // ── Local Storage (SharedPreferences) ──────────────────────────

    public static boolean isPrivacyConsentAccepted(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return sp.getBoolean(KEY_PRIVACY_ACCEPTED, false);
    }

    public static void setPrivacyConsentAccepted(@NonNull Context context, boolean accepted, String date) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putBoolean(KEY_PRIVACY_ACCEPTED, accepted)
                .putString(KEY_PRIVACY_DATE, date != null ? date : "")
                .apply();
    }

    public static UserProfile getLocalProfile(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        UserProfile p = new UserProfile();
        p.setFullName(sp.getString(KEY_FULL_NAME, ""));
        p.setAge(sp.getString(KEY_AGE, ""));
        p.setBirthdate(sp.getString(KEY_BIRTHDATE, ""));
        p.setAddress(sp.getString(KEY_ADDRESS, ""));
        p.setPhone(sp.getString(KEY_PHONE, ""));
        p.setEmergencyContact(sp.getString(KEY_EMERGENCY_CONTACT, ""));
        p.setEmergencyContactName(sp.getString(KEY_EMERGENCY_CONTACT_NAME, ""));
        p.setAllergies(sp.getString(KEY_ALLERGIES, ""));
        p.setBloodType(sp.getString(KEY_BLOOD_TYPE, ""));
        p.setMedicalConditions(sp.getString(KEY_MEDICAL_CONDITIONS, ""));
        p.setPrivacyConsentAccepted(sp.getBoolean(KEY_PRIVACY_ACCEPTED, false));
        p.setPrivacyConsentDate(sp.getString(KEY_PRIVACY_DATE, ""));
        return p;
    }

    public static void saveLocalProfile(@NonNull Context context, @NonNull UserProfile profile) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit()
                .putString(KEY_FULL_NAME, profile.getFullName())
                .putString(KEY_AGE, profile.getAge())
                .putString(KEY_BIRTHDATE, profile.getBirthdate())
                .putString(KEY_ADDRESS, profile.getAddress())
                .putString(KEY_PHONE, profile.getPhone())
                .putString(KEY_EMERGENCY_CONTACT, profile.getEmergencyContact())
                .putString(KEY_EMERGENCY_CONTACT_NAME, profile.getEmergencyContactName())
                .putString(KEY_ALLERGIES, profile.getAllergies())
                .putString(KEY_BLOOD_TYPE, profile.getBloodType())
                .putString(KEY_MEDICAL_CONDITIONS, profile.getMedicalConditions())
                .putBoolean(KEY_PRIVACY_ACCEPTED, profile.isPrivacyConsentAccepted())
                .putString(KEY_PRIVACY_DATE, profile.getPrivacyConsentDate())
                .apply();
    }

    public static void clearLocalProfile(@NonNull Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        sp.edit().clear().apply();
    }

    // ── Cloud Firestore Sync ──────────────────────────────────────

    public static void saveProfile(@NonNull Context context, @NonNull UserProfile profile, ProfileCallback callback) {
        saveLocalProfile(context, profile);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            // Saved locally only (offline or guest profile)
            if (callback != null) callback.onSuccess(profile);
            return;
        }

        Map<String, Object> data = profile.toMap();
        data.put("updatedAt", FieldValue.serverTimestamp());

        FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(user.getUid())
                .set(data, SetOptions.merge())
                .addOnSuccessListener(aVoid -> {
                    Log.d(TAG, "Profile saved to Firestore successfully");
                    if (callback != null) callback.onSuccess(profile);
                })
                .addOnFailureListener(e -> {
                    Log.e(TAG, "Failed to save profile to Firestore: " + e.getMessage());
                    // Still succeeds locally
                    if (callback != null) callback.onSuccess(profile);
                });
    }

    public static void loadProfile(@NonNull Context context, ProfileCallback callback) {
        // Return local first
        UserProfile local = getLocalProfile(context);

        FirebaseUser user = FirebaseAuth.getInstance().getCurrentUser();
        if (user == null) {
            if (callback != null) callback.onSuccess(local);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("user_profiles")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(snapshot -> {
                    if (snapshot != null && snapshot.exists() && snapshot.getData() != null) {
                        UserProfile cloudProfile = UserProfile.fromMap(snapshot.getData());
                        // If cloud profile has privacy consent, retain it
                        if (!cloudProfile.isPrivacyConsentAccepted() && local.isPrivacyConsentAccepted()) {
                            cloudProfile.setPrivacyConsentAccepted(true);
                            cloudProfile.setPrivacyConsentDate(local.getPrivacyConsentDate());
                        }
                        // Update local cache
                        saveLocalProfile(context, cloudProfile);
                        if (callback != null) callback.onSuccess(cloudProfile);
                    } else {
                        // Document doesn't exist yet on cloud, return local
                        if (callback != null) callback.onSuccess(local);
                    }
                })
                .addOnFailureListener(e -> {
                    Log.w(TAG, "Failed to fetch cloud profile, using local: " + e.getMessage());
                    if (callback != null) callback.onSuccess(local);
                });
    }
}
