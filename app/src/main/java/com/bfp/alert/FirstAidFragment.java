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

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class FirstAidFragment extends Fragment {

    private FirebaseFirestore db;
    private FirstAidAdapter   adapter;

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

        // Setup RecyclerView
        RecyclerView recycler = view.findViewById(R.id.recyclerFirstAid);
        recycler.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        adapter = new FirstAidAdapter(filteredItems, item -> {
            Intent intent = new Intent(requireContext(),
                    FirstAidDetailActivity.class);
            intent.putExtra("id",              item.id);
            intent.putExtra("title",           item.title);
            intent.putExtra("category",        item.category);
            intent.putExtra("description",     item.description);
            intent.putExtra("videoUrl",
                    item.videoUrl != null ? item.videoUrl : "");
            intent.putExtra("storageVideoUrl",
                    item.storageVideoUrl != null
                            ? item.storageVideoUrl : "");
            intent.putExtra("iconEmoji",
                    item.iconEmoji != null ? item.iconEmoji : "🩺");
            intent.putStringArrayListExtra("steps",
                    new ArrayList<>(item.steps != null
                            ? item.steps : new ArrayList<>()));
            intent.putStringArrayListExtra("photoUrls",
                    new ArrayList<>(item.photoUrls != null
                            ? item.photoUrls : new ArrayList<>()));
            startActivity(intent);
        });
        recycler.setAdapter(adapter);

        // Search bar
        EditText etSearch = view.findViewById(R.id.etSearch);
        etSearch.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(
                    CharSequence s, int st, int c, int a) {}
            @Override
            public void onTextChanged(
                    CharSequence s, int st, int b, int c) {
                filterItems(s.toString());
            }
            @Override
            public void afterTextChanged(Editable s) {}
        });

        loadFirstAid(view);
        return view;
    }

    // ── Load from Firestore with real-time updates ─────────────────
    private void loadFirstAid(View view) {
        db.collection("first_aid")
                .orderBy("title")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null || snapshots == null) return;

                    allItems.clear();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        FirstAidItem item =
                                doc.toObject(FirstAidItem.class);
                        item.id = doc.getId();
                        allItems.add(item);
                    }

                    // Rebuild category filter buttons
                    buildCategoryFilters(view);

                    // Refresh list while keeping current search
                    EditText etSearch = view.findViewById(R.id.etSearch);
                    filterItems(etSearch.getText().toString());

                    // Silently pre-cache all media in background
                    preCacheMedia(allItems);
                });
    }

    // ── Category filter chips ──────────────────────────────────────
    private void buildCategoryFilters(View view) {
        LinearLayout container =
                view.findViewById(R.id.categoryFilter);
        container.removeAllViews();

        // Collect unique categories
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
                // Reset search box
                EditText etSearch =
                        view.findViewById(R.id.etSearch);
                etSearch.setText("");

                if (cat.equals("All")) {
                    filterItems("");
                } else {
                    filterByCategory(cat);
                }

                // Highlight selected button
                highlightCategoryButton(
                        container, btn, cat);
            });

            container.addView(btn);
        }
    }

    // Highlight the active category button
    private void highlightCategoryButton(LinearLayout container,
                                         Button selected,
                                         String cat) {
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof Button) {
                Button b = (Button) child;
                b.setBackgroundTintList(
                        android.content.res.ColorStateList.valueOf(
                                b == selected ? 0xFFe63946 : 0xFF333355));
            }
        }
    }

    // ── Filter by search query ─────────────────────────────────────
    private void filterItems(String query) {
        filteredItems.clear();
        String lower = query.toLowerCase().trim();

        for (FirstAidItem item : allItems) {
            if (lower.isEmpty()
                    || (item.title != null && item.title
                    .toLowerCase().contains(lower))
                    || (item.category != null && item.category
                    .toLowerCase().contains(lower))
                    || (item.description != null && item.description
                    .toLowerCase().contains(lower))) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // ── Filter by category ─────────────────────────────────────────
    private void filterByCategory(String category) {
        filteredItems.clear();
        for (FirstAidItem item : allItems) {
            if (category.equals(item.category)) {
                filteredItems.add(item);
            }
        }
        adapter.notifyDataSetChanged();
    }

    // ── Pre-cache all photos and videos silently ───────────────────
    private void preCacheMedia(List<FirstAidItem> items) {
        if (!NetworkUtils.isOnline(requireContext())) return;

        for (FirstAidItem item : items) {

            // Pre-cache all photos using Glide disk cache
            if (item.photoUrls != null) {
                for (String url : item.photoUrls) {
                    if (url != null && !url.isEmpty()) {
                        Glide.with(requireContext())
                                .load(url)
                                .diskCacheStrategy(
                                        DiskCacheStrategy.ALL)
                                .preload();
                    }
                }
            }

            // Pre-download video to local storage
            if (item.storageVideoUrl != null
                    && !item.storageVideoUrl.isEmpty()
                    && !VideoCache.isCached(
                    requireContext(),
                    item.storageVideoUrl)) {

                VideoCache.downloadVideo(
                        requireContext(),
                        item.storageVideoUrl,
                        new VideoCache.DownloadCallback() {
                            @Override
                            public void onProgress(int percent) {
                                // Silent — no UI update here
                            }

                            @Override
                            public void onComplete(String localPath) {
                                // Video is now available offline
                            }

                            @Override
                            public void onError(String error) {
                                // Silent fail — will retry next time
                            }
                        });
            }
        }
    }
}