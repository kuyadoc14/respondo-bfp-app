package com.bfp.alert;

import android.content.Context;
import android.content.Intent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;

import java.text.SimpleDateFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class AlertAdapter extends RecyclerView.Adapter<AlertAdapter.VH> {

    private final List<Map<String, Object>> items;
    private final List<String>              ids;
    private final FirebaseFirestore         db;

    public AlertAdapter(
            List<Map<String, Object>> items,
            List<String> ids,
            FirebaseFirestore db) {
        this.items = items;
        this.ids   = ids;
        this.db    = db;
    }

    @NonNull
    @Override
    public VH onCreateViewHolder(
            @NonNull ViewGroup parent,
            int viewType) {
        View v = LayoutInflater.from(
            parent.getContext()).inflate(
                R.layout.item_alert_card,
                parent, false);
        return new VH(v);
    }

    @Override
    public void onBindViewHolder(
            @NonNull VH h, int pos) {
        Map<String, Object> data = items.get(pos);
        String alertId = ids.get(pos);
        boolean isActive = "active".equals(
            data.get("status"));

        // ── Status dot color ──────────────────────
        h.statusDot.setBackgroundResource(
            isActive
                ? R.drawable.circle_dot
                : R.drawable.circle_dot_green);

        // ── Status label ──────────────────────────
        h.tvStatus.setText(
            isActive ? "ACTIVE" : "RESOLVED");
        h.tvStatus.setTextColor(
            isActive ? 0xFFFF3B30 : 0xFF34C759);

        // ── Accident type ─────────────────────────
        Object type = data.get("accidentType");
        if (type != null) {
            h.tvAccidentType.setText(
                type.toString().toUpperCase());
            h.tvAccidentType.setVisibility(
                View.VISIBLE);
            h.dotSeparator.setVisibility(
                View.VISIBLE);
        } else {
            h.tvAccidentType.setVisibility(
                View.GONE);
            h.dotSeparator.setVisibility(View.GONE);
        }

        // ── User / device ─────────────────────────
        String userId = data.get("userId") != null
            ? data.get("userId").toString()
            : "Unknown";
        h.tvUser.setText(userId);

        // ── Timestamp ─────────────────────────────
        Object ts = data.get("timestamp");
        if (ts instanceof
                com.google.firebase.Timestamp) {
            String time = new SimpleDateFormat(
                "MMM dd  hh:mm a",
                Locale.getDefault()).format(
                    ((com.google.firebase.Timestamp) ts)
                        .toDate());
            h.tvTime.setText(time);
        } else {
            h.tvTime.setText("—");
        }

        // ── Victim count ──────────────────────────
        Object victims =
            data.get("countOfVictims");
        if (victims != null) {
            h.rowVictims.setVisibility(View.VISIBLE);
            h.tvVictimCount.setText(
                victims.toString());
        } else {
            h.rowVictims.setVisibility(View.GONE);
        }

        // ── Nature + Mode of injury ───────────────
        String nature = data.get("natureOfInjury")
            != null
            ? data.get("natureOfInjury").toString()
            : null;
        String mode = data.get("modeOfInjury")
            != null
            ? data.get("modeOfInjury").toString()
            : null;

        if (nature != null || mode != null) {
            StringBuilder sb = new StringBuilder();
            if (nature != null) sb.append(nature);
            if (nature != null && mode != null)
                sb.append("  ·  ");
            if (mode != null) sb.append(mode);
            h.tvNatureMode.setText(sb.toString());
            h.tvNatureMode.setVisibility(
                View.VISIBLE);
        } else {
            h.tvNatureMode.setVisibility(View.GONE);
        }

        // ── Click → alert detail ──────────────────
        h.itemView.setOnClickListener(v -> {
            Context ctx = v.getContext();
            if (ctx instanceof
                    AdminDashboardActivity) {
                ((AdminDashboardActivity) ctx)
                    .openAlertDetail(
                        alertId, data);
            }
        });
    }

    @Override
    public int getItemCount() {
        return items.size();
    }

    static class VH extends RecyclerView.ViewHolder {
        View         statusDot, dotSeparator;
        TextView     tvStatus, tvAccidentType,
                     tvUser, tvTime,
                     tvVictimCount, tvNatureMode;
        LinearLayout rowVictims;

        VH(View v) {
            super(v);
            statusDot       =
                v.findViewById(R.id.statusDot);
            dotSeparator    =
                v.findViewById(R.id.dotSeparator);
            tvStatus        =
                v.findViewById(R.id.tvAlertStatus);
            tvAccidentType  =
                v.findViewById(R.id.tvAccidentType);
            tvUser          =
                v.findViewById(R.id.tvAlertUser);
            tvTime          =
                v.findViewById(R.id.tvAlertTime);
            rowVictims      =
                v.findViewById(R.id.rowVictims);
            tvVictimCount   =
                v.findViewById(R.id.tvVictimCount);
            tvNatureMode    =
                v.findViewById(R.id.tvNatureMode);
        }
    }
}