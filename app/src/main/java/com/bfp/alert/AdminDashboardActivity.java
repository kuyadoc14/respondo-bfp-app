package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    private FirebaseFirestore db;
    private ListenerRegistration listenerReg;
    private AlertAdapter adapter;
    private final List<Map<String, Object>> alertList = new ArrayList<>();
    private final List<String> alertIds = new ArrayList<>();
    private TextView tvAlertCount;

    // Map
    private GoogleMap googleMap;
    private final Map<String, Marker> markerMap = new HashMap<>();

    // Views
    private RecyclerView recyclerView;
    private View mapContainer;
    private Button btnTabList, btnTabMap;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Guard
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
            return;
        }

        db           = FirebaseFirestore.getInstance();
        tvAlertCount = findViewById(R.id.tvAlertCount);
        recyclerView = findViewById(R.id.recyclerView);
        mapContainer = findViewById(R.id.mapFragment);
        btnTabList   = findViewById(R.id.btnTabList);
        btnTabMap    = findViewById(R.id.btnTabMap);

        // List setup
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertAdapter(alertList, alertIds, db);
        recyclerView.setAdapter(adapter);

        // Map setup
        SupportMapFragment mapFragment = (SupportMapFragment)
                getSupportFragmentManager().findFragmentById(R.id.mapFragment);
        if (mapFragment != null) mapFragment.getMapAsync(this);

        // Tab switching
        btnTabList.setOnClickListener(v -> showListView());
        btnTabMap.setOnClickListener(v  -> showMapView());

        // Logout
        findViewById(R.id.btnLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        listenForAlerts();
    }

    // ── Tab switching ──────────────────────────────────────────────
    private void showListView() {
        recyclerView.setVisibility(View.VISIBLE);
        mapContainer.setVisibility(View.GONE);
        btnTabList.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFe63946));
        btnTabMap.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF444466));
    }

    private void showMapView() {
        recyclerView.setVisibility(View.GONE);
        mapContainer.setVisibility(View.VISIBLE);
        btnTabList.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFF444466));
        btnTabMap.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(0xFFe63946));
    }

    // ── Firestore real-time listener ───────────────────────────────
    private void listenForAlerts() {
        listenerReg = db.collection("sos_alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    alertList.clear();
                    alertIds.clear();

                    int activeCount = 0;
                    for (DocumentSnapshot doc : snapshots.getDocuments()) {
                        alertList.add(doc.getData());
                        alertIds.add(doc.getId());
                        if ("active".equals(doc.getString("status"))) activeCount++;
                    }

                    tvAlertCount.setText("Active alerts: " + activeCount);
                    adapter.notifyDataSetChanged();
                    updateMapMarkers(snapshots.getDocuments());
                });
    }

    // ── Map ────────────────────────────────────────────────────────
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Default camera: Philippines
        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(12.8797, 121.7740), 6f));

        // Marker tap → open detail screen
        googleMap.setOnMarkerClickListener(marker -> {
            String alertId = (String) marker.getTag();
            if (alertId == null) return false;

            // Find the alert data by ID
            for (int i = 0; i < alertIds.size(); i++) {
                if (alertIds.get(i).equals(alertId)) {
                    Map<String, Object> data = alertList.get(i);
                    openAlertDetail(alertId, data);
                    break;
                }
            }
            return true;
        });
    }

    private void updateMapMarkers(List<DocumentSnapshot> docs) {
        if (googleMap == null) return;

        // Track which IDs are still in the list
        List<String> currentIds = new ArrayList<>();

        for (DocumentSnapshot doc : docs) {
            String alertId = doc.getId();
            currentIds.add(alertId);

            Double lat    = doc.getDouble("latitude");
            Double lng    = doc.getDouble("longitude");
            String status = doc.getString("status");
            if (lat == null || lng == null) continue;

            LatLng position = new LatLng(lat, lng);
            boolean isActive = "active".equals(status);

            if (markerMap.containsKey(alertId)) {
                // Update existing marker
                Marker existing = markerMap.get(alertId);
                if (existing != null) {
                    existing.setPosition(position);
                    existing.setIcon(BitmapDescriptorFactory.defaultMarker(
                            isActive
                                    ? BitmapDescriptorFactory.HUE_RED
                                    : BitmapDescriptorFactory.HUE_GREEN));
                    existing.setTitle(isActive ? "ACTIVE ALERT" : "Resolved");
                    existing.setSnippet("Tap for details");
                }
            } else {
                // Add new marker
                Marker marker = googleMap.addMarker(new MarkerOptions()
                        .position(position)
                        .title(isActive ? "ACTIVE ALERT" : "Resolved")
                        .snippet("Tap for details")
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                isActive
                                        ? BitmapDescriptorFactory.HUE_RED
                                        : BitmapDescriptorFactory.HUE_GREEN)));
                if (marker != null) {
                    marker.setTag(alertId);
                    markerMap.put(alertId, marker);
                }
            }
        }

        // Remove markers for deleted alerts
        List<String> toRemove = new ArrayList<>();
        for (String id : markerMap.keySet()) {
            if (!currentIds.contains(id)) toRemove.add(id);
        }
        for (String id : toRemove) {
            Marker m = markerMap.get(id);
            if (m != null) m.remove();
            markerMap.remove(id);
        }

        // Auto-zoom to active alerts if any exist
        for (DocumentSnapshot doc : docs) {
            if ("active".equals(doc.getString("status"))) {
                Double lat = doc.getDouble("latitude");
                Double lng = doc.getDouble("longitude");
                if (lat != null && lng != null) {
                    googleMap.animateCamera(
                            CameraUpdateFactory.newLatLngZoom(
                                    new LatLng(lat, lng), 14f));
                    break;
                }
            }
        }
    }

    private void openAlertDetail(String alertId, Map<String, Object> data) {
        Intent intent = new Intent(this, AlertDetailActivity.class);
        intent.putExtra("alertId", alertId);
        intent.putExtra("status",  (String) data.get("status"));
        intent.putExtra("userId",  (String) data.get("userId"));

        Double lat = (Double) data.get("latitude");
        Double lng = (Double) data.get("longitude");
        intent.putExtra("latitude",  lat != null ? lat : 0.0);
        intent.putExtra("longitude", lng != null ? lng : 0.0);

        com.google.firebase.Timestamp ts =
                (com.google.firebase.Timestamp) data.get("timestamp");
        intent.putExtra("timestamp", ts != null ? ts.toDate().getTime() : 0L);

        startActivity(intent);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) listenerReg.remove();
    }
}