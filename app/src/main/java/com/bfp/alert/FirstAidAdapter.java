package com.bfp.alert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import java.util.List;

public class FirstAidAdapter extends RecyclerView.Adapter<FirstAidAdapter.ViewHolder> {

    public interface OnItemClickListener {
        void onItemClick(FirstAidItem item);
    }

    private List<FirstAidItem> items;
    private final OnItemClickListener listener;

    public FirstAidAdapter(List<FirstAidItem> items, OnItemClickListener listener) {
        this.items    = items;
        this.listener = listener;
    }

    public void updateList(List<FirstAidItem> newItems) {
        this.items = newItems;
        notifyDataSetChanged();
    }

    @NonNull
    @Override
    public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View v = LayoutInflater.from(parent.getContext())
                .inflate(R.layout.item_first_aid_card, parent, false);
        return new ViewHolder(v);
    }

    @Override
    public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
        FirstAidItem item = items.get(position);
        holder.tvIcon.setText(item.iconEmoji != null ? item.iconEmoji : "🩺");
        holder.tvTitle.setText(item.title);
        holder.tvCategory.setText(item.category);
        holder.tvDescription.setText(item.description);
        holder.itemView.setOnClickListener(v -> listener.onItemClick(item));
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView tvIcon, tvTitle, tvCategory, tvDescription;
        ViewHolder(View v) {
            super(v);
            tvIcon        = v.findViewById(R.id.tvIcon);
            tvTitle       = v.findViewById(R.id.tvTitle);
            tvCategory    = v.findViewById(R.id.tvCategory);
            tvDescription = v.findViewById(R.id.tvDescription);
        }
    }
}