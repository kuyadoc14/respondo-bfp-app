package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirstAidActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private FirstAidAdapter adapter;
    private final List<FirstAidItem> allItems      = new ArrayList<>();
    private final List<FirstAidItem> filteredItems = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid);

        db = FirebaseFirestore.getInstance();

        findViewById(R.id.btnBack).setOnClickListener(v -> finish());

        RecyclerView recycler = findViewById(R.id.recyclerFirstAid);
        recycler.setLayoutManager(new LinearLayoutManager(this));
        adapter = new FirstAidAdapter(filteredItems, item -> {
            Intent intent = new Intent(this, FirstAidDetailActivity.class);
            intent.putExtra("id",          item.id);
            intent.putExtra("title",       item.title);
            intent.putExtra("category",    item.category);
            intent.putExtra("description", item.description);
            intent.putExtra("videoUrl",    item.videoUrl);
            intent.putExtra("iconEmoji",   item.iconEmoji);
            intent.putStringArrayListExtra("steps",
                    new ArrayList<>(item.steps != null ? item.steps : new ArrayList<>()));
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        // Search
        EditText etSearch = findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(CharSequence s, int st, int b, int c) {
                filterItems(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadFirstAid();
    }

    private void loadFirstAid() {
        db.collection("first_aid")
                .orderBy("title")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;
                    allItems.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        FirstAidItem item = doc.toObject(FirstAidItem.class);
                        item.id = doc.getId();
                        allItems.add(item);
                    }
                    buildCategoryFilters();
                    filterItems("");
                });
    }

    private void buildCategoryFilters() {
        LinearLayout container = findViewById(R.id.categoryFilter);
        container.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (FirstAidItem item : allItems) {
            if (item.category != null && !categories.contains(item.category)) {
                categories.add(item.category);
            }
        }

        for (String cat : categories) {
            Button btn = new Button(this);
            btn.setText(cat);
            btn.setTextSize(11);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            cat.equals("All") ? 0xFFe63946 : 0xFF333355));

            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(6, 0, 6, 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (cat.equals("All")) {
                    filterItems("");
                    ((EditText) findViewById(R.id.etSearch)).setText("");
                } else {
                    filterByCategory(cat);
                }
            });
            container.addView(btn);
        }
    }

    private void filterItems(String query) {
        filteredItems.clear();
        for (FirstAidItem item : allItems) {
            if (query.isEmpty() ||
                    item.title.toLowerCase().contains(query.toLowerCase()) ||
                    (item.category != null &&
                            item.category.toLowerCase().contains(query.toLowerCase()))) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    private void filterByCategory(String category) {
        filteredItems.clear();
        for (FirstAidItem item : allItems) {
            if (category.equals(item.category)) filteredItems.add(item);
        }
        adapter.notifyDataSetChanged();
    }
}