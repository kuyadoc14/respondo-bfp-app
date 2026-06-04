package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

public class ProfileFragment extends Fragment {

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_profile, container, false);

        view.findViewById(R.id.btnGoToAdmin)
                .setOnClickListener(v -> {
                    if (com.google.firebase.auth.FirebaseAuth
                            .getInstance()
                            .getCurrentUser() != null) {
                        // Already logged in
                        startActivity(new Intent(
                                requireContext(),
                                AdminDashboardActivity.class));
                    } else {
                        // Show login popup
                        startActivity(new Intent(
                                requireContext(),
                                AdminLoginActivity.class));
                    }
                });

        return view;
    }
}