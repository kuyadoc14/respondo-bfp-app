package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.LatLngBounds;
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
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity
        implements OnMapReadyCallback {

    // ── Firebase ──────────────────────────────────────────────────
    private FirebaseFirestore    db;
    private ListenerRegistration listenerReg;

    // ── Alert list ────────────────────────────────────────────────
    private AlertAdapter                    adapter;
    private final List<Map<String, Object>> alertList = new ArrayList<>();
    private final List<String>              alertIds  = new ArrayList<>();

    // ── Map ───────────────────────────────────────────────────────
    private GoogleMap               googleMap;
    private final Map<String, Marker> markerMap   = new HashMap<>();
    private Polyline                currentRoute  = null;
    private Marker                  stationMarker = null;
    private boolean                 mapInitialized = false;

    // ── Views ─────────────────────────────────────────────────────
    private RecyclerView recyclerView;
    private View         mapContainer;
    private TextView     tvAlertCount;
    private TextView btnTabList, btnTabMap;

    // ── Replace with your actual Maps API key ─────────────────────
    private static final String MAPS_API_KEY = "AIzaSyA_ga7boLqIPNUI0xWsCrAh4AxoCSTNzPg";

    // ════════════════════════════════════════════════════════════
    //  onCreate
    // ════════════════════════════════════════════════════════════
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Guard — not logged in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
            return;
        }

        db           = FirebaseFirestore.getInstance();
        tvAlertCount = findViewById(R.id.tvAlertCount);
        recyclerView = findViewById(R.id.recyclerView);
        mapContainer = findViewById(R.id.mapFragment);
        btnTabList = findViewById(R.id.btnTabList);
        btnTabMap  = findViewById(R.id.btnTabMap);

        // RecyclerView
        recyclerView.setLayoutManager(
                new LinearLayoutManager(this));
        adapter = new AlertAdapter(alertList, alertIds, db);
        recyclerView.setAdapter(adapter);

        // Map fragment
        SupportMapFragment mapFragment =
                (SupportMapFragment) getSupportFragmentManager()
                        .findFragmentById(R.id.mapFragment);
        if (mapFragment != null) {
            mapFragment.getMapAsync(this);
        }

        // Tabs
        btnTabList.setOnClickListener(v -> showListView());
        btnTabMap.setOnClickListener(v  -> showMapView());

        // Header buttons
        // Change from Button to LinearLayout
        findViewById(R.id.btnManageFirstAid)
                .setOnClickListener(v -> startActivity(
                        new Intent(this, AdminFirstAidActivity.class)));

        findViewById(R.id.btnAdminLogout).setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();

            Intent intent = new Intent(
                    this, MainActivity.class);
            intent.addFlags(
                    Intent.FLAG_ACTIVITY_CLEAR_TASK |
                            Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(intent);
            finish();
        });

        listenForAlerts();
    }

    // ════════════════════════════════════════════════════════════
    //  Tab switching
    // ════════════════════════════════════════════════════════════
    private void showListView() {
        recyclerView.setVisibility(View.VISIBLE);
        mapContainer.setVisibility(View.GONE);
        btnTabList.setBackground(
                getDrawable(R.drawable.bg_tab_selected));
        btnTabList.setTextColor(0xFFFFFFFF);
        btnTabMap.setBackground(
                getDrawable(R.drawable.bg_tab_unselected));
        btnTabMap.setTextColor(0xFF6B7280);
    }

    private void showMapView() {
        recyclerView.setVisibility(View.GONE);
        mapContainer.setVisibility(View.VISIBLE);
        btnTabList.setBackground(
                getDrawable(R.drawable.bg_tab_unselected));
        btnTabList.setTextColor(0xFF6B7280);
        btnTabMap.setBackground(
                getDrawable(R.drawable.bg_tab_selected));
        btnTabMap.setTextColor(0xFFFFFFFF);

        if (!mapInitialized && googleMap != null) {
            mapInitialized = true;
            updateMapMarkers();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Firestore real-time listener
    // ════════════════════════════════════════════════════════════
    private void listenForAlerts() {
        listenerReg = db.collection("sos_alerts")
                .orderBy("timestamp", Query.Direction.DESCENDING)
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    alertList.clear();
                    alertIds.clear();

                    int activeCount = 0;
                    for (DocumentSnapshot doc :
                            snapshots.getDocuments()) {
                        alertList.add(doc.getData());
                        alertIds.add(doc.getId());
                        if ("active".equals(
                                doc.getString("status")))
                            activeCount++;
                    }

                    tvAlertCount.setText(
                            "Active alerts: " + activeCount);
                    adapter.notifyDataSetChanged();

                    if (mapInitialized && googleMap != null) {
                        updateMapMarkers();
                    }
                });
    }

    // ════════════════════════════════════════════════════════════
    //  Map ready
    // ════════════════════════════════════════════════════════════
    @Override
    public void onMapReady(GoogleMap map) {
        googleMap = map;
        googleMap.setMapType(GoogleMap.MAP_TYPE_NORMAL);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        googleMap.getUiSettings().setCompassEnabled(true);

        // Default camera — Philippines
        googleMap.moveCamera(
                CameraUpdateFactory.newLatLngZoom(
                        new LatLng(12.8797, 121.7740), 6f));

        // Marker tap → show info dialog
        googleMap.setOnMarkerClickListener(marker -> {
            // Skip station marker
            if ("station".equals(marker.getTag())) {
                marker.showInfoWindow();
                return true;
            }

            String alertId = (String) marker.getTag();
            if (alertId == null) return false;

            for (int i = 0; i < alertIds.size(); i++) {
                if (alertIds.get(i).equals(alertId)) {
                    showAlertDialog(alertId,
                            alertList.get(i));
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

    // ════════════════════════════════════════════════════════════
    //  Update map markers
    // ════════════════════════════════════════════════════════════
    private void updateMapMarkers() {
        if (googleMap == null) return;

        List<String> currentIds = new ArrayList<>();

        for (int i = 0; i < alertList.size(); i++) {
            Map<String, Object> alert = alertList.get(i);
            String alertId = alertIds.get(i);
            currentIds.add(alertId);

            Object latObj = alert.get("latitude");
            Object lngObj = alert.get("longitude");
            if (latObj == null || lngObj == null) continue;

            double  lat      = ((Number) latObj).doubleValue();
            double  lng      = ((Number) lngObj).doubleValue();
            if (lat == 0 && lng == 0) continue;

            boolean isActive =
                    "active".equals(alert.get("status"));
            LatLng  position = new LatLng(lat, lng);

            if (markerMap.containsKey(alertId)) {
                Marker existing = markerMap.get(alertId);
                if (existing != null) {
                    existing.setPosition(position);
                    existing.setIcon(
                            BitmapDescriptorFactory.defaultMarker(
                                    isActive
                                            ? BitmapDescriptorFactory.HUE_RED
                                            : BitmapDescriptorFactory
                                            .HUE_GREEN));
                    existing.setTitle(isActive
                            ? "🔴 ACTIVE ALERT"
                            : "🟢 Resolved");
                }
            } else {
                Marker marker = googleMap.addMarker(
                        new MarkerOptions()
                                .position(position)
                                .title(isActive
                                        ? "🔴 ACTIVE ALERT"
                                        : "🟢 Resolved")
                                .snippet("Tap for options")
                                .icon(BitmapDescriptorFactory
                                        .defaultMarker(isActive
                                                ? BitmapDescriptorFactory.HUE_RED
                                                : BitmapDescriptorFactory
                                                .HUE_GREEN)));
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

        // Auto-zoom to latest active alert
        for (int i = 0; i < alertList.size(); i++) {
            if ("active".equals(alertList.get(i)
                    .get("status"))) {
                Object latObj = alertList.get(i).get("latitude");
                Object lngObj = alertList.get(i).get("longitude");
                if (latObj != null && lngObj != null) {
                    double lat =
                            ((Number) latObj).doubleValue();
                    double lng =
                            ((Number) lngObj).doubleValue();
                    if (lat != 0 || lng != 0) {
                        googleMap.animateCamera(
                                CameraUpdateFactory.newLatLngZoom(
                                        new LatLng(lat, lng), 14f));
                    }
                }
                break;
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Alert info dialog with routing options
    // ════════════════════════════════════════════════════════════
    private void showAlertDialog(String alertId,
                                 Map<String, Object> data) {
        Object latObj = data.get("latitude");
        Object lngObj = data.get("longitude");
        double alertLat = latObj != null
                ? ((Number) latObj).doubleValue() : 0;
        double alertLng = lngObj != null
                ? ((Number) lngObj).doubleValue() : 0;

        boolean isActive =
                "active".equals(data.get("status"));

        // Format timestamp
        String timeStr = "—";
        Object ts = data.get("timestamp");
        if (ts instanceof com.google.firebase.Timestamp) {
            timeStr = new SimpleDateFormat(
                    "MMM dd, yyyy hh:mm a", Locale.getDefault())
                    .format(((com.google.firebase.Timestamp) ts)
                            .toDate());
        }

        String message =
                "⏰ " + timeStr + "\n"
                        + "📍 " + String.format(Locale.getDefault(),
                        "%.5f, %.5f", alertLat, alertLng) + "\n"
                        + "👤 " + data.get("userId") + "\n"
                        + "📌 Status: "
                        + (isActive ? "ACTIVE" : "Resolved");

        android.app.AlertDialog.Builder builder =
                new android.app.AlertDialog.Builder(this)
                        .setTitle(isActive
                                ? "🔴 Active Alert"
                                : "🟢 Resolved Alert")
                        .setMessage(message);

        // ── Positive: Navigate from my location ──────────────────
        builder.setPositiveButton(
                "🗺 Navigate",
                (dialog, which) -> openGoogleMapsApp(
                        alertLat, alertLng));

        // ── Neutral: Route from BFP station on map ───────────────
        builder.setNeutralButton(
                "🚒 Route from Station",
                (dialog, which) ->
                        loadStationAndDrawRoute(alertLat, alertLng));

        // ── Negative: Resolve or delete ──────────────────────────
        if (isActive) {
            builder.setNegativeButton(
                    "✓ Resolve",
                    (dialog, which) ->
                            db.collection("sos_alerts")
                                    .document(alertId)
                                    .update("status", "resolved")
                                    .addOnSuccessListener(u ->
                                            Toast.makeText(this,
                                                    "Alert resolved.",
                                                    Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this,
                                                    "Failed: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show()));
        } else {
            builder.setNegativeButton(
                    "✕ Delete",
                    (dialog, which) ->
                            db.collection("sos_alerts")
                                    .document(alertId)
                                    .delete()
                                    .addOnSuccessListener(u ->
                                            Toast.makeText(this,
                                                    "Alert deleted.",
                                                    Toast.LENGTH_SHORT).show())
                                    .addOnFailureListener(e ->
                                            Toast.makeText(this,
                                                    "Failed: " + e.getMessage(),
                                                    Toast.LENGTH_SHORT).show()));
        }

        builder.show();
    }

    // ════════════════════════════════════════════════════════════
    //  Open Google Maps app for turn-by-turn navigation
    // ════════════════════════════════════════════════════════════
    private void openGoogleMapsApp(double destLat,
                                   double destLng) {
        // This opens Google Maps with navigation from
        // the device's current location to the alert
        String uri = "google.navigation:q="
                + destLat + "," + destLng + "&mode=d";

        Intent intent = new Intent(
                Intent.ACTION_VIEW, Uri.parse(uri));
        intent.setPackage("com.google.android.apps.maps");

        if (intent.resolveActivity(
                getPackageManager()) != null) {
            startActivity(intent);
        } else {
            // Fallback — open in browser
            String browserUri =
                    "https://www.google.com/maps/dir/?api=1"
                            + "&destination=" + destLat + "," + destLng
                            + "&travelmode=driving";
            startActivity(new Intent(
                    Intent.ACTION_VIEW,
                    Uri.parse(browserUri)));
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Load BFP station from Firestore then draw route
    // ════════════════════════════════════════════════════════════
    private void loadStationAndDrawRoute(double destLat,
                                         double destLng) {
        Toast.makeText(this,
                "Loading station...",
                Toast.LENGTH_SHORT).show();

        db.collection("bfp_stations")
                .limit(1)
                .get()
                .addOnSuccessListener(snap -> {
                    if (snap.isEmpty()) {
                        Toast.makeText(this,
                                "No BFP station found.\n"
                                        + "Add one in Firestore "
                                        + "bfp_stations collection.",
                                Toast.LENGTH_LONG).show();
                        return;
                    }

                    DocumentSnapshot stationDoc =
                            snap.getDocuments().get(0);

                    Double sLat =
                            stationDoc.getDouble("latitude");
                    Double sLng =
                            stationDoc.getDouble("longitude");
                    String sName =
                            stationDoc.getString("name");

                    if (sLat == null || sLng == null) {
                        Toast.makeText(this,
                                "Station coordinates not set.",
                                Toast.LENGTH_SHORT).show();
                        return;
                    }

                    // Show map tab
                    showMapView();

                    // Add blue station marker
                    addStationMarker(
                            sLat, sLng,
                            sName != null ? sName : "BFP Station");

                    // Draw route line
                    drawRouteLine(
                            sLat, sLng, destLat, destLng);
                })
                .addOnFailureListener(e ->
                        Toast.makeText(this,
                                "Failed to load station: "
                                        + e.getMessage(),
                                Toast.LENGTH_SHORT).show());
    }

    // ════════════════════════════════════════════════════════════
    //  Add station marker (blue)
    // ════════════════════════════════════════════════════════════
    private void addStationMarker(double lat, double lng,
                                  String name) {
        // Remove old station marker
        if (stationMarker != null) {
            stationMarker.remove();
            stationMarker = null;
        }

        stationMarker = googleMap.addMarker(
                new MarkerOptions()
                        .position(new LatLng(lat, lng))
                        .title(name)
                        .snippet("BFP Station")
                        .icon(BitmapDescriptorFactory.defaultMarker(
                                BitmapDescriptorFactory.HUE_BLUE)));

        if (stationMarker != null) {
            stationMarker.setTag("station");
            stationMarker.showInfoWindow();
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Draw route line using Directions API
    // ════════════════════════════════════════════════════════════
    private void drawRouteLine(double originLat,
                               double originLng,
                               double destLat,
                               double destLng) {
        // Remove previous route
        if (currentRoute != null) {
            currentRoute.remove();
            currentRoute = null;
        }

        Toast.makeText(this,
                "Calculating route...",
                Toast.LENGTH_SHORT).show();

        // Build Directions API URL
        String url =
                "https://maps.googleapis.com/maps/api"
                        + "/directions/json"
                        + "?origin="
                        + originLat + "," + originLng
                        + "&destination="
                        + destLat + "," + destLng
                        + "&mode=driving"
                        + "&key=" + MAPS_API_KEY;

        // Run on background thread
        new Thread(() -> {
            try {
                // Make HTTP request
                HttpURLConnection conn =
                        (HttpURLConnection)
                                new URL(url).openConnection();
                conn.setRequestMethod("GET");
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(15000);
                conn.connect();

                int responseCode =
                        conn.getResponseCode();
                if (responseCode != 200) {
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Directions API error: "
                                            + responseCode,
                                    Toast.LENGTH_SHORT).show());
                    return;
                }

                // Read response
                BufferedReader reader =
                        new BufferedReader(
                                new InputStreamReader(
                                        conn.getInputStream()));
                StringBuilder response =
                        new StringBuilder();
                String line;
                while ((line = reader.readLine())
                        != null) {
                    response.append(line);
                }
                reader.close();
                conn.disconnect();

                // Parse JSON
                JSONObject json =
                        new JSONObject(response.toString());

                // Check API status
                String status =
                        json.getString("status");
                if (!status.equals("OK")) {
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "Route not found: " + status,
                                    Toast.LENGTH_SHORT).show());
                    return;
                }

                JSONArray routes =
                        json.getJSONArray("routes");
                if (routes.length() == 0) {
                    runOnUiThread(() ->
                            Toast.makeText(this,
                                    "No routes available.",
                                    Toast.LENGTH_SHORT).show());
                    return;
                }

                // Get encoded polyline
                String encodedPolyline =
                        routes.getJSONObject(0)
                                .getJSONObject("overview_polyline")
                                .getString("points");

                // Get distance and duration
                JSONObject leg =
                        routes.getJSONObject(0)
                                .getJSONArray("legs")
                                .getJSONObject(0);
                String distance =
                        leg.getJSONObject("distance")
                                .getString("text");
                String duration =
                        leg.getJSONObject("duration")
                                .getString("text");

                // Decode polyline to LatLng list
                List<LatLng> routePoints =
                        decodePolyline(encodedPolyline);

                // Update UI on main thread
                runOnUiThread(() -> {
                    // Draw red polyline on map
                    PolylineOptions polylineOptions =
                            new PolylineOptions()
                                    .addAll(routePoints)
                                    .width(12)
                                    .color(0xFFFC4D4D)
                                    .geodesic(true);
                    currentRoute = googleMap
                            .addPolyline(polylineOptions);

                    // Show distance and ETA toast
                    Toast.makeText(this,
                            "🚒 Distance: " + distance
                                    + "\n⏱ ETA: " + duration,
                            Toast.LENGTH_LONG).show();

                    // Zoom map to show full route
                    zoomToFitRoute(routePoints);
                });

            } catch (Exception e) {
                runOnUiThread(() ->
                        Toast.makeText(this,
                                "Route error: " + e.getMessage(),
                                Toast.LENGTH_LONG).show());
            }
        }).start();
    }

    // ════════════════════════════════════════════════════════════
    //  Zoom map to fit the entire route
    // ════════════════════════════════════════════════════════════
    private void zoomToFitRoute(List<LatLng> points) {
        if (points.isEmpty()) return;

        LatLngBounds.Builder builder =
                new LatLngBounds.Builder();
        for (LatLng point : points) {
            builder.include(point);
        }

        try {
            LatLngBounds bounds = builder.build();
            // 120dp padding around the route
            googleMap.animateCamera(
                    CameraUpdateFactory.newLatLngBounds(
                            bounds, 120));
        } catch (Exception e) {
            // Fallback if bounds calculation fails
            if (!points.isEmpty()) {
                googleMap.animateCamera(
                        CameraUpdateFactory.newLatLngZoom(
                                points.get(0), 12f));
            }
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Decode Google Maps encoded polyline string
    // ════════════════════════════════════════════════════════════
    private List<LatLng> decodePolyline(String encoded) {
        List<LatLng> points = new ArrayList<>();
        int index = 0;
        int len   = encoded.length();
        int lat   = 0;
        int lng   = 0;

        while (index < len) {
            // Decode latitude
            int b, shift = 0, result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift  += 5;
            } while (b >= 0x20);
            int dlat = ((result & 1) != 0
                    ? ~(result >> 1) : (result >> 1));
            lat += dlat;

            // Decode longitude
            shift  = 0;
            result = 0;
            do {
                b = encoded.charAt(index++) - 63;
                result |= (b & 0x1f) << shift;
                shift  += 5;
            } while (b >= 0x20);
            int dlng = ((result & 1) != 0
                    ? ~(result >> 1) : (result >> 1));
            lng += dlng;

            points.add(new LatLng(
                    lat / 1e5, lng / 1e5));
        }
        return points;
    }

    // ════════════════════════════════════════════════════════════
    //  Open AlertDetailActivity from list
    // ════════════════════════════════════════════════════════════
    public void openAlertDetail(String alertId,
                                Map<String, Object> data) {
        Intent intent = new Intent(
                this, AlertDetailActivity.class);
        intent.putExtra("alertId", alertId);
        intent.putExtra("status",
                (String) data.get("status"));
        intent.putExtra("userId",
                (String) data.get("userId"));

        double lat = data.get("latitude") != null
                ? ((Number) data.get("latitude"))
                .doubleValue() : 0.0;
        double lng = data.get("longitude") != null
                ? ((Number) data.get("longitude"))
                .doubleValue() : 0.0;
        intent.putExtra("latitude",  lat);
        intent.putExtra("longitude", lng);

        com.google.firebase.Timestamp ts =
                (com.google.firebase.Timestamp)
                        data.get("timestamp");
        intent.putExtra("timestamp",
                ts != null ? ts.toDate().getTime() : 0L);

        startActivity(intent);
    }

    // ════════════════════════════════════════════════════════════
    //  Lifecycle
    // ════════════════════════════════════════════════════════════
    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) {
            listenerReg.remove();
        }
    }
}