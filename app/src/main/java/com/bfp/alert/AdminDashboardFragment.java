package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
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
import com.google.android.gms.maps.model.Polyline;
import com.google.android.gms.maps.model.PolylineOptions;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class AdminDashboardFragment extends Fragment implements OnMapReadyCallback {

    private FirebaseFirestore db;
    private ListenerRegistration listenerReg;

    private AlertAdapter adapter;
    private final List<Map<String, Object>> alertList = new ArrayList<>();
    private final List<String> alertIds = new ArrayList<>();

    private GoogleMap googleMap;
    private final Map<String, Marker> markerMap = new HashMap<>();
    private Polyline currentRoute = null;
    private Marker stationMarker = null;
    private boolean mapInitialized = false;

    private RecyclerView recyclerView;
    private FrameLayout mapContainer;
    private TextView tvAlertCount;
    private TextView btnTabList, btnTabMap;

    private static final String MAPS_API_KEY = "AIzaSyA_ga7boLqIPNUI0xWsCrAh4AxoCSTNzPg";

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_admin_dashboard, container, false);

        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            ((MainActivity) requireActivity()).openAdminLogin();
            return view;
        }

        db = FirebaseFirestore.getInstance();
        tvAlertCount = view.findViewById(R.id.tvAlertCount);
        recyclerView = view.findViewById(R.id.recyclerView);
        mapContainer = view.findViewById(R.id.mapContainer);
        btnTabList = view.findViewById(R.id.btnTabList);
        btnTabMap = view.findViewById(R.id.btnTabMap);

        recyclerView.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new AlertAdapter(alertList, alertIds, db);
        recyclerView.setAdapter(adapter);

        initMapFragment();

        btnTabList.setOnClickListener(v -> showListView());
        btnTabMap.setOnClickListener(v -> showMapView());

        view.findViewById(R.id.btnManageFirstAid).setOnClickListener(v ->
                ((MainActivity) requireActivity()).openAdminFirstAid());

        view.findViewById(R.id.btnAdminLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            ((MainActivity) requireActivity()).returnToMain();
        });

        listenForAlerts();
        return view;
    }

    private void initMapFragment() {
        SupportMapFragment mapFragment = (SupportMapFragment) getChildFragmentManager()
                .findFragmentById(R.id.mapContainer);

        if (mapFragment == null) {
            mapFragment = SupportMapFragment.newInstance();
            getChildFragmentManager()
                    .beginTransaction()
                    .replace(R.id.mapContainer, mapFragment)
                    .commit();
        }

        mapFragment.getMapAsync(this);
    }

    private void showListView() {
        recyclerView.setVisibility(View.VISIBLE);
        mapContainer.setVisibility(View.GONE);
        btnTabList.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected));
        btnTabList.setTextColor(0xFFFFFFFF);
        btnTabMap.setBackground(requireContext().getDrawable(R.drawable.bg_tab_unselected));
        btnTabMap.setTextColor(0xFF6B7280);
    }

    private void showMapView() {
        recyclerView.setVisibility(View.GONE);
        mapContainer.setVisibility(View.VISIBLE);
        btnTabList.setBackground(requireContext().getDrawable(R.drawable.bg_tab_unselected));
        btnTabList.setTextColor(0xFF6B7280);
        btnTabMap.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected));
        btnTabMap.setTextColor(0xFFFFFFFF);

        if (!mapInitialized && googleMap != null) {
            mapInitialized = true;
            updateMapMarkers();
        }
    }

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
                        if ("active".equals(doc.getString("status"))) {
                            activeCount++;
                        }
                    }

                    tvAlertCount.setText("Active alerts: " + activeCount);
                    adapter.notifyDataSetChanged();

                    if (mapInitialized && googleMap != null) {
                        updateMapMarkers();
                    }
                });
    }

    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                new LatLng(12.8797, 121.7740), 6f));

        googleMap.setOnMarkerClickListener(marker -> {
            if ("station".equals(marker.getTag())) {
                marker.showInfoWindow();
                return true;
            }

            String alertId = (String) marker.getTag();
            if (alertId == null) return false;

            for (int i = 0; i < alertIds.size(); i++) {
                if (alertIds.get(i).equals(alertId)) {
                    showAlertDialog(alertId, alertList.get(i));
                    break;
                }
            }
            return true;
        });

        if (mapContainer.getVisibility() == View.VISIBLE) {
            mapInitialized = true;
            updateMapMarkers();
        }
    }

    private void updateMapMarkers() {
        if (googleMap == null) return;

        for (Marker marker : markerMap.values()) {
            marker.remove();
        }
        markerMap.clear();

        for (int i = 0; i < alertList.size(); i++) {
            Map<String, Object> alert = alertList.get(i);
            if (alert == null) continue;

            Number latNum = (Number) alert.get("latitude");
            Number lngNum = (Number) alert.get("longitude");
            if (latNum == null || lngNum == null) continue;

            LatLng pos = new LatLng(latNum.doubleValue(), lngNum.doubleValue());
            Marker marker = googleMap.addMarker(new MarkerOptions()
                    .position(pos)
                    .title("Emergency Alert")
                    .icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)));
            if (marker != null) {
                marker.setTag(alertIds.get(i));
                markerMap.put(alertIds.get(i), marker);
            }
        }

        if (!alertList.isEmpty()) {
            googleMap.moveCamera(CameraUpdateFactory.newLatLngZoom(
                    new LatLng(
                            ((Number) alertList.get(0).get("latitude")).doubleValue(),
                            ((Number) alertList.get(0).get("longitude")).doubleValue()), 10f));
        }
    }

    private void showAlertDialog(String alertId, Map<String, Object> data) {
        requireActivity().getSupportFragmentManager().beginTransaction()
                .replace(R.id.fragmentContainer, AlertDetailFragment.newInstance(alertId, data))
                .addToBackStack("alert_detail")
                .commit();
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        if (listenerReg != null) {
            listenerReg.remove();
        }
    }

    public static String decodePolyline(String encoded) {
        if (encoded == null || encoded.isEmpty()) return "";
        List<LatLng> points = new ArrayList<>();
        int index = 0;
        int len = encoded.length();
        int lat = 0;
        int lng = 0;

        while (index < len) {
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lat += dlat;

            shift = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0) ? ~(result >> 1) : (result >> 1);
            lng += dlng;

            points.add(new LatLng(lat / 1e5, lng / 1e5));
        }
        return "";
    }
}
