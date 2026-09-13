package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.QueryDocumentSnapshot;

import java.util.ArrayList;
import java.util.List;

public class AdminFirstAidFragment extends Fragment {

    private FirebaseFirestore db;
    private final List<FirstAidItem> items = new ArrayList<>();
    private AdminFirstAidAdapter adapter;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_admin_first_aid, container, false);

        db = FirebaseFirestore.getInstance();

        view.findViewById(R.id.btnBack).setOnClickListener(v ->
                requireActivity().getSupportFragmentManager().popBackStack());

        RecyclerView recycler = view.findViewById(R.id.recyclerAdminFirstAid);
        recycler.setLayoutManager(new LinearLayoutManager(requireContext()));

        adapter = new AdminFirstAidAdapter(items,
                item -> {
                    FirstAidEditorFragment fragment = new FirstAidEditorFragment();
                    Bundle args = new Bundle();
                    args.putString("mode", "edit");
                    args.putString("id", item.id);
                    args.putString("title", item.title);
                    args.putString("category", item.category);
                    args.putString("description", item.description);
                    args.putString("videoUrl", item.videoUrl);
                    args.putString("iconEmoji", item.iconEmoji);
                    args.putString("storageVideoUrl", item.storageVideoUrl != null ? item.storageVideoUrl : "");
                    args.putStringArrayList("steps", new ArrayList<>(item.steps != null ? item.steps : new ArrayList<>()));
                    args.putStringArrayList("photoUrls", new ArrayList<>(item.photoUrls != null ? item.photoUrls : new ArrayList<>()));
                    fragment.setArguments(args);

                    requireActivity().getSupportFragmentManager()
                            .beginTransaction()
                            .replace(R.id.fragmentContainer, fragment)
                            .addToBackStack("admin_first_aid_editor")
                            .commit();
                },
                item -> db.collection("first_aid")
                        .document(item.id)
                        .delete()
                        .addOnSuccessListener(u ->
                                Toast.makeText(requireContext(), "Deleted.", Toast.LENGTH_SHORT).show())
                        .addOnFailureListener(e ->
                                Toast.makeText(requireContext(), "Failed: " + e.getMessage(), Toast.LENGTH_SHORT).show())
        );
        recycler.setAdapter(adapter);

        view.findViewById(R.id.btnAddFirstAid).setOnClickListener(v -> {
            FirstAidEditorFragment fragment = new FirstAidEditorFragment();
            Bundle args = new Bundle();
            args.putString("mode", "add");
            fragment.setArguments(args);

            requireActivity().getSupportFragmentManager()
                    .beginTransaction()
                    .replace(R.id.fragmentContainer, fragment)
                    .addToBackStack("admin_first_aid_editor")
                    .commit();
        });

        loadItems();
        return view;
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

                    TextView tvCount = requireView().findViewById(R.id.tvItemCount);
                    tvCount.setText(items.size() + " guide" + (items.size() != 1 ? "s" : ""));
                    adapter.notifyDataSetChanged();
                });
    }

    static class AdminFirstAidAdapter extends RecyclerView.Adapter<AdminFirstAidAdapter.VH> {

        interface OnEdit { void onEdit(FirstAidItem item); }
        interface OnDelete { void onDelete(FirstAidItem item); }

        private final List<FirstAidItem> items;
        private final OnEdit onEdit;
        private final OnDelete onDelete;

        AdminFirstAidAdapter(List<FirstAidItem> items, OnEdit onEdit, OnDelete onDelete) {
            this.items = items;
            this.onEdit = onEdit;
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
            h.tvAdminIcon.setText(item.iconEmoji != null ? item.iconEmoji : "🩺");
            h.tvAdminTitle.setText(item.title);
            h.tvAdminCategory.setText(item.category);
            h.btnEdit.setOnClickListener(v -> onEdit.onEdit(item));
            h.btnAdminDelete.setOnClickListener(v -> onDelete.onDelete(item));
        }

        @Override
        public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView tvAdminIcon, tvAdminTitle, tvAdminCategory;
            TextView btnEdit;
            ImageButton btnAdminDelete;

            VH(View v) {
                super(v);
                tvAdminIcon = v.findViewById(R.id.tvAdminIcon);
                tvAdminTitle = v.findViewById(R.id.tvAdminTitle);
                tvAdminCategory = v.findViewById(R.id.tvAdminCategory);
                btnEdit = v.findViewById(R.id.btnEdit);
                btnAdminDelete = v.findViewById(R.id.btnAdminDelete);
            }
        }
    }
}
