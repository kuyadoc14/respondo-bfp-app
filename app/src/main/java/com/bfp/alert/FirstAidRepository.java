package com.bfp.alert;

import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.ListenerRegistration;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Centralized repository for First Aid guides.
 * Caches items in real-time from Firestore and avoids memory leaks or detached fragment crashes.
 */
public class FirstAidRepository {

    private static final String TAG = "FirstAidRepo";
    private static volatile FirstAidRepository instance;

    private final List<FirstAidItem> cachedItems = Collections.synchronizedList(new ArrayList<>());
    private final List<DataCallback> callbacks = Collections.synchronizedList(new ArrayList<>());
    private ListenerRegistration registration;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private boolean isListening = false;

    public interface DataCallback {
        void onDataLoaded(List<FirstAidItem> items);
    }

    public static FirstAidRepository getInstance() {
        if (instance == null) {
            synchronized (FirstAidRepository.class) {
                if (instance == null) {
                    instance = new FirstAidRepository();
                }
            }
        }
        return instance;
    }

    private FirstAidRepository() {
        startListening();
    }

    public synchronized void startListening() {
        if (isListening && registration != null) return;
        isListening = true;

        FirebaseFirestore db = FirebaseFirestore.getInstance();
        registration = db.collection("first_aid")
                .orderBy("title")
                .addSnapshotListener((snapshots, e) -> {
                    if (e != null) {
                        Log.e(TAG, "Listen failed: " + e.getMessage());
                        return;
                    }
                    if (snapshots == null) return;

                    List<FirstAidItem> newItems = new ArrayList<>();
                    for (QueryDocumentSnapshot doc : snapshots) {
                        try {
                            FirstAidItem item = doc.toObject(FirstAidItem.class);
                            if (item != null) {
                                item.id = doc.getId();
                                newItems.add(item);
                            }
                        } catch (Exception ex) {
                            Log.e(TAG, "Error deserializing first aid item", ex);
                        }
                    }

                    synchronized (cachedItems) {
                        cachedItems.clear();
                        cachedItems.addAll(newItems);
                    }

                    List<FirstAidItem> snapshotCopy = new ArrayList<>(newItems);
                    mainHandler.post(() -> {
                        synchronized (callbacks) {
                            for (DataCallback cb : callbacks) {
                                try {
                                    cb.onDataLoaded(snapshotCopy);
                                } catch (Exception cbEx) {
                                    Log.e(TAG, "Callback error", cbEx);
                                }
                            }
                        }
                    });
                });
    }

    public void addListener(@NonNull DataCallback callback) {
        synchronized (callbacks) {
            if (!callbacks.contains(callback)) {
                callbacks.add(callback);
            }
        }
        synchronized (cachedItems) {
            if (!cachedItems.isEmpty()) {
                List<FirstAidItem> snapshotCopy = new ArrayList<>(cachedItems);
                mainHandler.post(() -> callback.onDataLoaded(snapshotCopy));
            }
        }
    }

    public void removeListener(@NonNull DataCallback callback) {
        synchronized (callbacks) {
            callbacks.remove(callback);
        }
    }

    public List<FirstAidItem> getCachedItems() {
        synchronized (cachedItems) {
            return new ArrayList<>(cachedItems);
        }
    }

    @Nullable
    public FirstAidItem findMatchingItem(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String q = query.trim().toLowerCase();

        synchronized (cachedItems) {
            // 1. Exact match on title
            for (FirstAidItem item : cachedItems) {
                if (item.title != null && item.title.trim().equalsIgnoreCase(q)) {
                    return item;
                }
            }
            // 2. Title contains or query contains title
            for (FirstAidItem item : cachedItems) {
                if (item.title != null) {
                    String t = item.title.trim().toLowerCase();
                    if (t.contains(q) || q.contains(t)) {
                        return item;
                    }
                }
            }
            // 3. Category match
            for (FirstAidItem item : cachedItems) {
                if (item.category != null && item.category.trim().toLowerCase().contains(q)) {
                    return item;
                }
            }
            // 4. Description match
            for (FirstAidItem item : cachedItems) {
                if (item.description != null && item.description.toLowerCase().contains(q)) {
                    return item;
                }
            }
        }
        return null;
    }

    public boolean hasMatchingGuide(String query) {
        return findMatchingItem(query) != null;
    }

    @NonNull
    public String getGuideListSummary() {
        StringBuilder sb = new StringBuilder();
        synchronized (cachedItems) {
            if (cachedItems.isEmpty()) {
                return "No guides currently loaded from database.";
            }
            for (FirstAidItem item : cachedItems) {
                if (item.title != null && !item.title.trim().isEmpty()) {
                    sb.append("- ").append(item.title.trim());
                    if (item.category != null && !item.category.trim().isEmpty()) {
                        sb.append(" (Category: ").append(item.category.trim()).append(")");
                    }
                    sb.append("\n");
                }
            }
        }
        return sb.toString().trim();
    }
}
