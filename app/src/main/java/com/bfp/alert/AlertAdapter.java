package com.bfp.alert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.Timestamp;
import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.AlertViewHolder> {

    private final List<Map<String, Object>> alerts;
    private final List<String> alertIds;
    private final FirebaseFirestore db;

    public AlertAdapter(List<Map<String, Object>> alerts,
                        List<String> alertIds,
                        FirebaseFirestore db) {
        this.alerts   = alerts;
        this.alertIds = alertIds;
        this.db       = db;
    }

    @NonNull
    @Override
    public AlertViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_alert, parent, false);
        return new AlertViewHolder(view);
    }

    @Override
    public void onBindViewHolder(@NonNull AlertViewHolder holder, int position) {
        Map<String, Object> alert = alerts.get(position);
        String alertId = alertIds.get(position);
        String status  = (String) alert.get("status");
        boolean isActive = "active".equals(status);

        // --- Status badge ---
        holder.tvStatus.setText(isActive ? "ACTIVE" : "RESOLVED");
        holder.tvStatus.setBackgroundColor(isActive ? 0xFFe63946 : 0xFF2a9d8f);

        // --- Timestamp ---
        Object ts = alert.get("timestamp");
        if (ts instanceof Timestamp) {
            Date date = ((Timestamp) ts).toDate();
            String formatted = new SimpleDateFormat(
                    "MMM dd, yyyy hh:mm a", Locale.getDefault()).format(date);
            holder.tvTime.setText(formatted);
        } else {
            holder.tvTime.setText("Just now");
        }

        // --- Location ---
        Object lat = alert.get("latitude");
        Object lng = alert.get("longitude");
        holder.tvLocation.setText("📍 " + lat + ", " + lng);

        // --- User ID ---
        holder.tvUserId.setText("User: " + alert.get("userId"));

        // --- Show/hide buttons based on status ---
        if (isActive) {
            // Active alert: show Resolve, hide Delete
            holder.btnResolve.setVisibility(View.VISIBLE);
            holder.btnDelete.setVisibility(View.GONE);

            holder.btnResolve.setOnClickListener(v ->
                    db.collection("sos_alerts")
                            .document(alertId)
                            .update("status", "resolved"));

        } else {
            // Resolved alert: hide Resolve, show Delete (X)
            holder.btnResolve.setVisibility(View.GONE);
            holder.btnDelete.setVisibility(View.VISIBLE);

            holder.btnDelete.setOnClickListener(v ->
                    db.collection("sos_alerts")
                            .document(alertId)
                            .delete());
        }
    }

    @Override
    public int getItemCount() {
        return alerts.size();
    }

    static class AlertViewHolder extends RecyclerView.ViewHolder {
        TextView tvStatus, tvTime, tvLocation, tvUserId;
        Button btnResolve, btnDelete;

        AlertViewHolder(View itemView) {
            super(itemView);
            tvStatus   = itemView.findViewById(R.id.tvStatus);
            tvTime     = itemView.findViewById(R.id.tvTime);
            tvLocation = itemView.findViewById(R.id.tvLocation);
            tvUserId   = itemView.findViewById(R.id.tvUserId);
            btnResolve = itemView.findViewById(R.id.btnResolve);
            btnDelete  = itemView.findViewById(R.id.btnDelete);
        }
    }
}