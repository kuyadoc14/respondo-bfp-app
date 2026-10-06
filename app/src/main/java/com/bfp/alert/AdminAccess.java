package com.bfp.alert;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreException;

public final class AdminAccess {

    public interface Callback {
        void onResult(boolean isAdmin, @Nullable Exception error);
    }

    private AdminAccess() {
    }

    public static void check(@Nullable FirebaseUser user,
                             @NonNull Callback callback) {
        if (user == null) {
            callback.onResult(false, null);
            return;
        }

        FirebaseFirestore.getInstance()
                .collection("admin_users")
                .document(user.getUid())
                .get()
                .addOnSuccessListener(document ->
                        callback.onResult(document.exists(), null))
                .addOnFailureListener(error ->
                        callback.onResult(false, error));
    }

    @NonNull
    public static String getVerificationErrorMessage(@NonNull Exception error) {
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.PERMISSION_DENIED) {
            return "Firestore denied the account-type check. Deploy the latest firestore.rules and try again.";
        }
        if (error instanceof FirebaseFirestoreException
                && ((FirebaseFirestoreException) error).getCode()
                == FirebaseFirestoreException.Code.UNAVAILABLE) {
            return "Firestore is unavailable. Check your connection and try again.";
        }
        String details = error.getLocalizedMessage();
        return details != null
                ? "Unable to verify account type: " + details
                : "Unable to verify account type. Please try again.";
    }
}
