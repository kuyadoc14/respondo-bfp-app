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
        populateDefaultItemsIfEmpty();
        startListening();
    }

    private void populateDefaultItemsIfEmpty() {
        mergeWithDefaults(null);
    }

    private void mergeWithDefaults(List<FirstAidItem> liveItems) {
        synchronized (cachedItems) {
            cachedItems.clear();
            if (liveItems != null) {
                cachedItems.addAll(liveItems);
            }
            ensureDefaultItem("Burns", "Emergency Care", "Immediate cooling and protective care for thermal, electrical, and chemical burns.", "🔥",
                    java.util.Arrays.asList(
                            "Cool the burn immediately under cool, gently running water for 10 to 20 minutes. Do not use ice, ice water, or greasy ointments.",
                            "Remove restrictive items like rings, watches, or tight clothing around the burned area before swelling starts.",
                            "Cover the burned area loosely with a sterile, non-stick dressing or clean cloth.",
                            "Do not pop any blisters. Protect the skin from infection.",
                            "Seek medical care or call BFP immediately for deep, large, chemical, or electrical burns."
                    ));
            ensureDefaultItem("CPR", "Life Support", "Cardiopulmonary resuscitation steps for an unresponsive person not breathing normally.", "❤️",
                    java.util.Arrays.asList(
                            "Check scene safety and check the victim for responsiveness.",
                            "Call for emergency help or BFP immediately and get an AED if available.",
                            "Place hands in the center of the chest and push hard and fast (100 to 120 compressions per minute).",
                            "Allow the chest to fully recoil between compressions.",
                            "If trained, deliver 2 rescue breaths after every 30 chest compressions. Continue until medical responders take over."
                    ));
            ensureDefaultItem("Choking", "Airway Obstruction", "Heimlich maneuver and back blows for someone who cannot breathe, cough, or speak.", "🫁",
                    java.util.Arrays.asList(
                            "Encourage coughing if the victim can still cough or speak.",
                            "If airway is fully blocked, deliver 5 firm back blows between the shoulder blades.",
                            "Perform 5 quick abdominal thrusts (Heimlich maneuver) just above the navel.",
                            "Alternate 5 back blows and 5 abdominal thrusts until the object is expelled.",
                            "If the victim becomes unconscious, lower to ground and begin CPR immediately."
                    ));
        }
    }

    private void ensureDefaultItem(String title, String category, String desc, String emoji, java.util.List<String> steps) {
        for (FirstAidItem item : cachedItems) {
            if (item.title != null && item.title.trim().equalsIgnoreCase(title.trim())) {
                return;
            }
        }
        FirstAidItem item = new FirstAidItem();
        item.id = "default_" + title.toLowerCase();
        item.title = title;
        item.category = category;
        item.description = desc;
        item.iconEmoji = emoji;
        item.steps = new ArrayList<>(steps);
        cachedItems.add(item);
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

                    mergeWithDefaults(newItems);

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

    private String cleanQuery(String s) {
        if (s == null) return "";
        return s.toLowerCase()
                .replaceAll("['\".,?!;:()\\/\\-]", " ")
                .replaceAll("\\b(open|show|display|search|find|view|watch|read|go to|the|a|an|please|can you|i want to|guide|guides|tutorial|first aid for|first aid|how to treat|how to do|how to|treatment for|treatment|and|play|video|buksan|ipakita|tingnan|panoorin|paano|gamutin|lunas|para sa|yung|ang|ng|sa|po|naman|kung|may|ako|siya)\\b", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private String mapTagalogMedicalSynonyms(String q) {
        if (q == null || q.isEmpty()) return "";
        String lower = q.toLowerCase();
        if (lower.contains("paso") || lower.contains("napaso") || lower.contains("lapnos") || lower.contains("banli")) {
            return lower + " burn burns";
        }
        if (lower.contains("nabulunan") || lower.contains("nasamid") || lower.contains("nabilaukan") || lower.contains("bara sa lalamunan")) {
            return lower + " choking";
        }
        if (lower.contains("hindi humihinga") || lower.contains("walang pulso") || lower.contains("atake sa puso") || lower.contains("huminto ang paghinga")) {
            return lower + " cpr cardiac";
        }
        if (lower.contains("dugo") || lower.contains("dumudugo") || lower.contains("sugat") || lower.contains("hiwa")) {
            return lower + " bleeding wound";
        }
        if (lower.contains("bali") || lower.contains("nabali") || lower.contains("pilay")) {
            return lower + " fracture sprain";
        }
        if (lower.contains("kagat") || lower.contains("ahas") || lower.contains("nakagat")) {
            return lower + " bite snake";
        }
        if (lower.contains("nahimatay") || lower.contains("walang malay") || lower.contains("hinimatay")) {
            return lower + " fainting unconscious";
        }
        if (lower.contains("nalunod") || lower.contains("nalulunod")) {
            return lower + " drowning";
        }
        if (lower.contains("lagnat")) {
            return lower + " fever";
        }
        if (lower.contains("kombulsyon")) {
            return lower + " seizure";
        }
        return lower;
    }

    private String stemWord(String w) {
        if (w == null) return "";
        w = w.toLowerCase().trim();
        if (w.endsWith("ing") && w.length() > 4) return w.substring(0, w.length() - 3);
        if (w.endsWith("es") && w.length() > 3) return w.substring(0, w.length() - 2);
        if (w.endsWith("s") && w.length() > 3) return w.substring(0, w.length() - 1);
        if (w.endsWith("ed") && w.length() > 3) return w.substring(0, w.length() - 2);
        return w;
    }

    @Nullable
    public FirstAidItem findMatchingItem(String query) {
        if (query == null || query.trim().isEmpty()) return null;
        String rawLower = query.trim().toLowerCase();
        String cleaned = cleanQuery(query);
        String q = mapTagalogMedicalSynonyms(cleaned.isEmpty() ? rawLower : cleaned);

        synchronized (cachedItems) {
            // 1. Exact match on title with cleaned or raw query
            for (FirstAidItem item : cachedItems) {
                if (item.title != null) {
                    String t = item.title.trim().toLowerCase();
                    if (t.equalsIgnoreCase(q) || t.equalsIgnoreCase(rawLower)) {
                        return item;
                    }
                }
            }

            // 2. Title contains query or query contains title
            for (FirstAidItem item : cachedItems) {
                if (item.title != null) {
                    String t = item.title.trim().toLowerCase();
                    if (t.contains(q) || q.contains(t) || rawLower.contains(t)) {
                        return item;
                    }
                }
            }

            // 3. Stemming check (e.g. "burns" vs "burn", "choking" vs "choke")
            String stemQ = stemWord(q);
            for (FirstAidItem item : cachedItems) {
                if (item.title != null) {
                    String t = item.title.trim().toLowerCase();
                    String stemT = stemWord(t);
                    if ((!stemT.isEmpty() && (stemT.contains(stemQ) || stemQ.contains(stemT)))
                            || (t.contains(stemQ) || stemT.contains(q))) {
                        return item;
                    }
                }
            }

            // 4. Token overlap (match any significant word >= 3 letters from query to title)
            String[] tokens = q.split("\\s+");
            for (String token : tokens) {
                if (token.length() >= 3) {
                    String stemToken = stemWord(token);
                    for (FirstAidItem item : cachedItems) {
                        if (item.title != null) {
                            String t = item.title.trim().toLowerCase();
                            if (t.contains(token) || (!stemToken.isEmpty() && t.contains(stemToken))) {
                                return item;
                            }
                        }
                    }
                }
            }

            // 5. Category match
            for (FirstAidItem item : cachedItems) {
                if (item.category != null && (item.category.trim().toLowerCase().contains(q) || item.category.trim().toLowerCase().contains(rawLower))) {
                    return item;
                }
            }

            // 6. Description match
            for (FirstAidItem item : cachedItems) {
                if (item.description != null && (item.description.toLowerCase().contains(q) || item.description.toLowerCase().contains(rawLower))) {
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
