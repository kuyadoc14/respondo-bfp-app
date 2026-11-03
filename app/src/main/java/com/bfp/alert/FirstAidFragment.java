package com.bfp.alert;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.bumptech.glide.Glide;
import com.bumptech.glide.load.engine.DiskCacheStrategy;

import java.util.ArrayList;
import java.util.List;

public class FirstAidFragment extends Fragment {

    private FirstAidAdapter   adapter;
    private final List<FirstAidItem> allItems      = new ArrayList<>();
    private final List<FirstAidItem> filteredItems = new ArrayList<>();
    private String pendingSearchQuery = "";

    private final FirstAidRepository.DataCallback dataCallback = items -> {
        if (!isAdded() || getContext() == null || getView() == null) return;
        allItems.clear();
        allItems.addAll(items);

        View currentView = getView();
        buildCategoryFilters(currentView);

        EditText etSearch = currentView.findViewById(R.id.etSearch);
        String currentQuery = pendingSearchQuery;
        if (etSearch != null && !etSearch.getText().toString().isEmpty()) {
            currentQuery = etSearch.getText().toString();
        }
        filterItems(currentQuery);

        preCacheMedia(allItems);
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_first_aid, container, false);

        SystemBarUtils.applyTopInsetPadding(view.findViewById(R.id.headerLayout));

        // Setup RecyclerView
        RecyclerView recycler = view.findViewById(R.id.recyclerFirstAid);
        recycler.setLayoutManager(
                new LinearLayoutManager(requireContext()));

        adapter = new FirstAidAdapter(filteredItems, item -> {
            FirstAidDetailFragment detailFragment =
                    FirstAidDetailFragment.newInstance(
                            item.title != null ? item.title : "",
                            item.category != null ? item.category : "",
                            item.description != null ? item.description : "",
                            item.videoUrl != null ? item.videoUrl : "",
                            item.storageVideoUrl != null ? item.storageVideoUrl : "",
                            item.iconEmoji != null ? item.iconEmoji : "🩺",
                            new ArrayList<>(item.steps != null
                                    ? item.steps : new ArrayList<>()),
                            new ArrayList<>(item.photoUrls != null
                                    ? item.photoUrls : new ArrayList<>()));

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, detailFragment)
                    .addToBackStack("first_aid_detail")
                    .commit();
        });
        recycler.setAdapter(adapter);

        // Search bar
        EditText etSearch = view.findViewById(R.id.etSearch);
        if (etSearch != null) {
            if (!pendingSearchQuery.isEmpty()) {
                etSearch.setText(pendingSearchQuery);
            }
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
        }

        FirstAidRepository.getInstance().addListener(dataCallback);
        return view;
    }

    @Override
    public void onDestroyView() {
        FirstAidRepository.getInstance().removeListener(dataCallback);
        super.onDestroyView();
    }

    // ── Category filter chips ──────────────────────────────────────
    private void buildCategoryFilters(View view) {
        if (view == null || getContext() == null) return;
        LinearLayout container = view.findViewById(R.id.categoryFilter);
        if (container == null) return;
        container.removeAllViews();

        List<String> categories = new ArrayList<>();
        categories.add("All");
        for (FirstAidItem item : allItems) {
            if (item.category != null
                    && !categories.contains(
                    item.category)) {
                categories.add(item.category);
            }
        }

        Context ctx = getContext();
        if (ctx == null) return;

        for (String cat : categories) {
            com.google.android.material.button.MaterialButton btn =
                    new com.google.android.material.button
                            .MaterialButton(ctx);

            btn.setText(cat);
            btn.setTextSize(12);
            btn.setTextColor(0xFFFFFFFF);
            btn.setTypeface(null,
                    android.graphics.Typeface.BOLD);

            btn.setCornerRadius(dp(50));
            btn.setElevation(0);
            btn.setStateListAnimator(null);

            btn.setPadding(
                    dp(18), dp(6), dp(18), dp(6));
            btn.setMinWidth(0);
            btn.setMinimumWidth(0);
            btn.setMinHeight(0);
            btn.setMinimumHeight(0);
            btn.setInsetTop(0);
            btn.setInsetBottom(0);

            btn.setBackgroundTintList(
                    android.content.res.ColorStateList
                            .valueOf(cat.equals("All")
                                    ? 0xFFFF3B30   // red for All
                                    : 0xFF3A3A3C));// dark for others

            LinearLayout.LayoutParams params =
                    new LinearLayout.LayoutParams(
                            LinearLayout.LayoutParams
                                    .WRAP_CONTENT,
                            LinearLayout.LayoutParams
                                    .WRAP_CONTENT);
            params.setMargins(0, 0, dp(8), 0);
            btn.setLayoutParams(params);

            btn.setOnClickListener(v -> {
                if (getView() == null) return;
                EditText etSearch =
                        getView().findViewById(R.id.etSearch);
                if (etSearch != null)
                    etSearch.setText("");

                if (cat.equals("All")) {
                    filterItems("");
                } else {
                    filterByCategory(cat);
                }

                highlightCategoryButton(
                        container, btn, cat);
            });

            container.addView(btn);
        }
    }

    private void highlightCategoryButton(
            LinearLayout container,
            com.google.android.material.button
                    .MaterialButton selected,
            String cat) {
        if (container == null) return;
        for (int i = 0; i < container.getChildCount(); i++) {
            View child = container.getChildAt(i);
            if (child instanceof
                    com.google.android.material.button
                            .MaterialButton) {
                com.google.android.material.button
                        .MaterialButton b =
                        (com.google.android.material.button
                                .MaterialButton) child;
                b.setBackgroundTintList(
                        android.content.res.ColorStateList
                                .valueOf(b == selected
                                        ? 0xFFFF3B30   // selected
                                        : 0xFF3A3A3C));// unselected
            }
        }
    }

    private int dp(int val) {
        Context ctx = getContext();
        if (ctx == null) return val * 2;
        return (int)(val * ctx.getResources().getDisplayMetrics().density);
    }

    // ── Filter by search query ─────────────────────────────────────
    private void filterItems(String query) {
        filteredItems.clear();
        String lower = query != null ? query.toLowerCase().trim() : "";

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
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    public void setSearchQuery(String query) {
        pendingSearchQuery = query != null ? query : "";
        if (getView() == null) return;
        EditText etSearch =
                getView().findViewById(R.id.etSearch);
        if (etSearch != null) {
            etSearch.setText(pendingSearchQuery);
        }
        filterItems(pendingSearchQuery);
    }

    // ── Filter by category ─────────────────────────────────────────
    private void filterByCategory(String category) {
        filteredItems.clear();
        for (FirstAidItem item : allItems) {
            if (category != null && category.equals(item.category)) {
                filteredItems.add(item);
            }
        }
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
    }

    // ── Pre-cache all photos and videos silently ───────────────────
    private void preCacheMedia(List<FirstAidItem> items) {
        Context ctx = getContext();
        if (ctx == null || !isAdded()) return;
        if (!NetworkUtils.isOnline(ctx)) return;

        for (FirstAidItem item : items) {
            if (item == null) continue;

            // Pre-cache all photos using Glide disk cache
            if (item.photoUrls != null) {
                for (String url : item.photoUrls) {
                    if (url != null && !url.isEmpty()) {
                        Context currentCtx = getContext();
                        if (currentCtx != null && isAdded()) {
                            Glide.with(currentCtx)
                                    .load(url)
                                    .diskCacheStrategy(DiskCacheStrategy.ALL)
                                    .preload();
                        }
                    }
                }
            }

            // Pre-download video to local storage
            if (item.storageVideoUrl != null
                    && !item.storageVideoUrl.isEmpty()) {
                Context currentCtx = getContext();
                if (currentCtx != null && isAdded() && !VideoCache.isCached(currentCtx, item.storageVideoUrl)) {
                    VideoCache.downloadVideo(
                            currentCtx,
                            item.storageVideoUrl,
                            new VideoCache.DownloadCallback() {
                                @Override public void onProgress(int percent) {}
                                @Override public void onComplete(String localPath) {}
                                @Override public void onError(String error) {}
                            });
                }
            }
        }
    }
}
