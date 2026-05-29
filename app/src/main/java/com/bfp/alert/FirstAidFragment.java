package com.bfp.alert;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirstAidFragment extends Fragment {

    private FirebaseFirestore db;
    private FirstAidAdapter adapter;
    private final List<FirstAidItem> allItems      = new ArrayList<>();
    private final List<FirstAidItem> filteredItems = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_first_aid, container, false);

        db = FirebaseFirestore.getInstance();

        RecyclerView recycler = view.findViewById(R.id.recyclerFirstAid);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));
        adapter = new FirstAidAdapter(filteredItems, item -> {
            Intent intent = new Intent(requireContext(),
                    FirstAidDetailActivity.class);
            intent.putExtra("id",          item.id);
            intent.putExtra("title",       item.title);
            intent.putExtra("category",    item.category);
            intent.putExtra("description", item.description);
            intent.putExtra("videoUrl",    item.videoUrl);
            intent.putExtra("iconEmoji",   item.iconEmoji);
            intent.putStringArrayListExtra("steps",
                    new ArrayList<>(item.steps != null
                            ? item.steps : new ArrayList<>()));
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        // Search
        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(
                    CharSequence s, int st, int c, int a) {}
            @Override public void onTextChanged(
                    CharSequence s, int st, int b, int c) {
                filterItems(s.toString());
            }
            @Override public void afterTextChanged(Editable s) {}
        });

        loadFirstAid(view);
        return view;
    }

    private void loadFirstAid(View view) {
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
                    buildCategoryFilters(view);
                    filterItems("");
                });
    }

    private void buildCategoryFilters(View view) {
        LinearLayout container = view.findViewById(R.id.categoryFilter);
        container.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (FirstAidItem item : allItems) {
            if (item.category != null
                    && !categories.contains(item.category)) {
                categories.add(item.category);
            }
        }

        for (String cat : categories) {
            Button btn = new Button(requireContext());
            btn.setText(cat);
            btn.setTextSize(11);
            btn.setTextColor(0xFFFFFFFF);
            btn.setBackgroundTintList(
                    android.content.res.ColorStateList.valueOf(
                            cat.equals("All") ? 0xFFe63946 : 0xFF333355));

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams.WRAP_CONTENT,
                            LinearLayout.LayoutParams.WRAP_CONTENT);
            params.setMargins(6, 0, 6, 0);
            btn.setLayoutParams(params);
            btn.setOnClickListener(v -> {
                if (cat.equals("All")) filterItems("");
                else filterByCategory(cat);
            });
            container.addView(btn);
        }
    }

    private void filterItems(String query) {
        filteredItems.clear();
        for (FirstAidItem item : allItems) {
            if (query.isEmpty()
                    || item.title.toLowerCase().contains(query.toLowerCase())
                    || (item.category != null && item.category.toLowerCase()
                    .contains(query.toLowerCase()))) {
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