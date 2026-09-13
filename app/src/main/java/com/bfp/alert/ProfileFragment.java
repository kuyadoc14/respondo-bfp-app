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
                        ((MainActivity) requireActivity())
                                .openAdminDashboard();
                    } else {
                        Bundle args = new Bundle();
                        args.putBoolean("fromLogout", false);
                        AdminLoginFragment fragment = new AdminLoginFragment();
                        fragment.setArguments(args);
                        requireActivity().getSupportFragmentManager()
                                .beginTransaction()
                                .replace(R.id.fragmentContainer, fragment)
                                .addToBackStack("admin_login")
                                .commit();
                    }
                });

        return view;
    }
}