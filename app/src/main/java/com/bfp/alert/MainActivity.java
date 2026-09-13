package com.bfp.alert;

import android.os.Bundle;
import android.view.View;
import java.util.HashMap;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;
import com.google.firebase.auth.FirebaseAuth;

public class MainActivity extends AppCompatActivity {

    private final SosFragment       sosFragment       = new SosFragment();
    private final FirstAidFragment  firstAidFragment  = new FirstAidFragment();
    private final ProfileFragment   profileFragment   = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {

        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        FirebaseAuth.getInstance().signOut();

        // Request BLE permissions on Android 12+
        if (android.os.Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT
            }, 300);
        }

        // Load SOS as default tab
        if (savedInstanceState == null) {
            loadFragment(sosFragment);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setOnItemSelectedListener(item -> {
            int id = item.getItemId();

            if (id == R.id.sos) {
                loadFragment(sosFragment);
                return true;
            } else if (id == R.id.firstAid) {
                loadFragment(firstAidFragment);
                return true;
            } else if (id == R.id.profile) {
                loadFragment(profileFragment);
                return true;
            }
            return false;
        });
    }

    private void setBottomNavVisibility(boolean visible) {
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setVisibility(visible ? View.VISIBLE : View.GONE);
        }
    }

    private void loadFragment(Fragment fragment) {
        setBottomNavVisibility(true);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }

    public void openAdminLogin() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminLoginFragment())
                .addToBackStack("admin_login")
                .commit();
    }

    public void openAdminDashboard() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminDashboardFragment())
                .commit();
    }

    public void openAdminFirstAid() {
        setBottomNavVisibility(false);
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, new AdminFirstAidFragment())
                .addToBackStack("admin_first_aid")
                .commit();
    }

    public void returnToMain() {
        setBottomNavVisibility(true);
        getSupportFragmentManager().popBackStack(null, androidx.fragment.app.FragmentManager.POP_BACK_STACK_INCLUSIVE);
        loadFragment(sosFragment);
        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.sos);
    }

    public void switchToFirstAid() {
        switchToFirstAid("");
    }

    public void switchToFirstAid(String query) {
        BottomNavigationView bottomNav =
                findViewById(R.id.bottomNav);
        bottomNav.setSelectedItemId(R.id.firstAid);
        loadFragment(firstAidFragment);
        if (!query.isEmpty()) {
            firstAidFragment.setSearchQuery(query);
        }
    }
}