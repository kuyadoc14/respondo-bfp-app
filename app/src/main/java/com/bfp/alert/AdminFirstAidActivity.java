package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminFirstAidActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private final List<FirstAidItem> items = new ArrayList<>();
    private AdminFirstAidAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_admin_first_aid);

        db = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recyclerAdminFirstAid);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new AdminFirstAidAdapter(items,
                // Edit
                item -> {
                    Intent intent = new Intent(this, FirstAidEditorActivity.class);
                    intent.putExtra("mode",        "edit");
                    intent.putExtra("id",          item.id);
                    intent.putExtra("title",       item.title);
                    intent.putExtra("category",    item.category);
                    intent.putExtra("description", item.description);
                    intent.putExtra("videoUrl",    item.videoUrl);
                    intent.putExtra("iconEmoji",   item.iconEmoji);
                    intent.putStringArrayListExtra("steps",
                            new ArrayList<>(item.steps != null ? item.steps : new ArrayList<>()));
                    startActivity(intent);
                },
                // Delete
                item -> db.collection("first_aid").document(item.id)
                        .delete()
                        .addOnSuccessListener(u ->
                                Toast.makeText(this, "Deleted.", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(this, "Failed: " + e.getMessage(),
                                        Toast.LENGTH_SHORT).show())
        );
        recycler.setAdapter(adapter);

        findViewById(R.id.btnAddFirstAid).setOnClickListener(v -> {
            Intent intent = new Intent(this, FirstAidEditorActivity.class);
            intent.putExtra("mode", "add");
            startActivity(intent);
        });

        loadItems();
    }

    private void loadItems() {
        db.collection("first_aid")
                .orderBy("title")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    items.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        FirstAidItem item = doc.toObject(FirstAidItem.class);
                        item.id = doc.getId();
                        items.add(item);
                    }
                    adapter.notifyDataSetChanged();
                });
    }

    // Inner adapter for admin list (with edit/delete buttons)
    static class AdminFirstAidAdapter
            extends RecyclerView.Adapter<AdminFirstAidAdapter.VH> {

        interface OnEdit   { void onEdit(FirstAidItem item); }
        interface OnDelete { void onDelete(FirstAidItem item); }

        private final List<FirstAidItem> items;
        private final OnEdit   onEdit;
        private final OnDelete onDelete;

        AdminFirstAidAdapter(List<FirstAidItem> items,
                             OnEdit onEdit, OnDelete onDelete) {
            this.items    = items;
            this.onEdit   = onEdit;
            this.onDelete = onDelete;
        }

        @NonNull
        @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_first_aid_admin, parent, false);
            return new VH(v);
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int position) {
            FirstAidItem item = items.get(position);
            h.tvIcon.setText(item.iconEmoji != null ? item.iconEmoji : "🩺");
            h.tvTitle.setText(item.title);
            h.tvCategory.setText(item.category);
            h.btnEdit.setOnClickListener(v   -> onEdit.onEdit(item));
            h.btnDelete.setOnClickListener(v -> onDelete.onDelete(item));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvIcon, tvTitle, tvCategory;
            Button btnEdit, btnDelete;
            VH(View v) {
                super(v);
                tvIcon    = v.findViewById(R.id.tvAdminIcon);
                tvTitle   = v.findViewById(R.id.tvAdminTitle);
                tvCategory = v.findViewById(R.id.tvAdminCategory);
                btnEdit   = v.findViewById(R.id.btnEdit);
                btnDelete = v.findViewById(R.id.btnAdminDelete);
            }
        }
    }
}