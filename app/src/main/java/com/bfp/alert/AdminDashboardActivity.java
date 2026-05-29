package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.Query;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

public class AdminDashboardActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private ListenerRegistration listenerReg;
    private AlertAdapter adapter;
    private List<Map<String, Object>> alertList = new ArrayList<>();
    private List<String> alertIds = new ArrayList<>();
    private TextView tvAlertCount;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_dashboard);

        // Guard: redirect if not logged in
        if (FirebaseAuth.getInstance().getCurrentUser() == null) {
            startActivity(new Intent(this, AdminLoginActivity.class));
            finish();
            return;
        }

        db = FirebaseFirestore.getInstance();
        tvAlertCount = findViewById(R.id.tvAlertCount);

        RecyclerView recyclerView = findViewById(R.id.recyclerView);
        recyclerView.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AlertAdapter(alertList, alertIds, db);
        recyclerView.setAdapter(adapter);

        Button btnLogout = findViewById(R.id.btnLogout);
        btnLogout.setOnClickListener(v -> {
            FirebaseAuth.getInstance().signOut();
            startActivity(new Intent(this, MainActivity.class));
            finish();
        });

        listenForAlerts();
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
                        if ("active".equals(doc.getString("status"))) activeCount++;
                    }

                    tvAlertCount.setText("Active alerts: " + activeCount);
                    adapter.notifyDataSetChanged();
                });
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (listenerReg != null) listenerReg.remove();
    }
}