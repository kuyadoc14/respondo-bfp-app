package com.bfp.alert;

import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
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
    private int lastAnimatedPosition = -1;

    public FirstAidAdapter(List<FirstAidItem> items, OnItemClickListener listener) {
        this.items    = items;
        this.listener = listener;
    }

    public void updateList(List<FirstAidItem> newItems) {
        this.items = newItems;
        this.lastAnimatedPosition = -1;
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
    public void onBindViewHolder(
            @NonNull ViewHolder holder, int position) {
        FirstAidItem item = items.get(position);
        holder.tvIcon.setText(
                item.iconEmoji != null ? item.iconEmoji : "🩺");
        holder.tvTitle.setText(item.title);
        holder.tvCategory.setText(item.category);
        holder.tvDescription.setText(item.description);

        // Set click on the inner container not the card
        holder.itemContainer.setOnClickListener(
                v -> listener.onItemClick(item));

        // Subtle cascade entrance animation
        if (position > lastAnimatedPosition) {
            holder.itemView.setAlpha(0f);
            holder.itemView.setTranslationY(24f);
            holder.itemView.animate()
                    .alpha(1f)
                    .translationY(0f)
                    .setDuration(280)
                    .setStartDelay(Math.min(position * 35L, 300L))
                    .setInterpolator(new android.view.animation.DecelerateInterpolator())
                    .start();
            lastAnimatedPosition = position;
        }
    }

    @Override
    public int getItemCount() { return items.size(); }

    static class ViewHolder extends RecyclerView.ViewHolder {
        TextView     tvIcon, tvTitle,
                tvCategory, tvDescription;
        LinearLayout itemContainer;

        ViewHolder(View v) {
            super(v);
            tvIcon        = v.findViewById(R.id.tvIcon);
            tvTitle       = v.findViewById(R.id.tvTitle);
            tvCategory    = v.findViewById(R.id.tvCategory);
            tvDescription = v.findViewById(R.id.tvDescription);
            itemContainer = v.findViewById(R.id.itemContainer);
        }
    }
}