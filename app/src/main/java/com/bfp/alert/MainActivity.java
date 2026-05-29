package com.bfp.alert;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;

import com.google.android.material.bottomnavigation.BottomNavigationView;

public class MainActivity extends AppCompatActivity {

    private final SosFragment       sosFragment       = new SosFragment();
    private final FirstAidFragment  firstAidFragment  = new FirstAidFragment();
    private final ProfileFragment   profileFragment   = new ProfileFragment();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

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

    private void loadFragment(Fragment fragment) {
        getSupportFragmentManager()
                .beginTransaction()
                .replace(R.id.fragmentContainer, fragment)
                .commit();
    }
}