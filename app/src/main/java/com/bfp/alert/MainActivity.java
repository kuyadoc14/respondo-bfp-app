package com.bfp.alert;

import android.os.Build;
import android.os.Bundle;
import android.view.View;
import java.util.HashMap;

import androidx.annotation.NonNull;
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

        // Request BLE and Notification permissions on Android 12+ / 13+
        if (Build.VERSION.SDK_INT >= 33) {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.POST_NOTIFICATIONS,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, 300);
        } else if (Build.VERSION.SDK_INT >= 31) {
            requestPermissions(new String[]{
                    android.Manifest.permission.BLUETOOTH_SCAN,
                    android.Manifest.permission.BLUETOOTH_CONNECT,
                    android.Manifest.permission.ACCESS_FINE_LOCATION
            }, 300);
        } else {
            BLESOSService.start(this);
        }

        // Load SOS as default tab
        if (savedInstanceState == null) {
            loadFragment(sosFragment);
        }

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        SystemBarUtils.applyBottomInsetPadding(bottomNav);
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

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 300) {
            BLESOSService.start(this);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        BLESOSService.start(this);
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
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.firstAid);
        }
        loadFragment(firstAidFragment);
        if (query != null && !query.isEmpty()) {
            firstAidFragment.setSearchQuery(query);
        }
    }

    public void openFirstAidDetail(FirstAidItem item) {
        if (item == null) return;
        setBottomNavVisibility(true);
        FirstAidDetailFragment detailFragment =
                FirstAidDetailFragment.newInstance(
                        item.title != null ? item.title : "",
                        item.category != null ? item.category : "",
                        item.description != null ? item.description : "",
                        item.videoUrl != null ? item.videoUrl : "",
                        item.storageVideoUrl != null ? item.storageVideoUrl : "",
                        item.iconEmoji != null ? item.iconEmoji : "🩺",
                        new java.util.ArrayList<>(item.steps != null ? item.steps : new java.util.ArrayList<>()),
                        new java.util.ArrayList<>(item.photoUrls != null ? item.photoUrls : new java.util.ArrayList<>()));

        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, detailFragment)
                .addToBackStack("first_aid_detail")
                .commit();

        BottomNavigationView bottomNav = findViewById(R.id.bottomNav);
        if (bottomNav != null) {
            bottomNav.setSelectedItemId(R.id.firstAid);
        }
    }
}
