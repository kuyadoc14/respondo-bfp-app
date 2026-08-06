package com.bfp.alert;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class PhotoViewerFragment extends Fragment {

    private int currentIndex;
    private ArrayList<String> urls;

    public static PhotoViewerFragment newInstance(
            int startIndex, ArrayList<String> allUrls) {
        PhotoViewerFragment fragment = new PhotoViewerFragment();
        Bundle args = new Bundle();
        args.putInt("startIndex", startIndex);
        args.putStringArrayList("allUrls", allUrls);
        fragment.setArguments(args);
        return fragment;
    }

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(
                R.layout.fragment_photo_viewer, container, false);

        Bundle args = getArguments();
        if (args != null) {
            urls         = args.getStringArrayList("allUrls");
            currentIndex = args.getInt("startIndex", 0);
        }

        ImageView imgView = view.findViewById(R.id.fullScreenImage);
        TextView  tvClose = view.findViewById(R.id.btnClose);
        TextView  tvCount = view.findViewById(R.id.tvPhotoCount);
        TextView  btnPrev = view.findViewById(R.id.btnPrev);
        TextView  btnNext = view.findViewById(R.id.btnNext);

        tvClose.setOnClickListener(v ->
                requireActivity().getOnBackPressedDispatcher()
                        .onBackPressed());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                loadPhoto(imgView, tvCount);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (urls != null && currentIndex < urls.size() - 1) {
                currentIndex++;
                loadPhoto(imgView, tvCount);
            }
        });

        loadPhoto(imgView, tvCount);

        return view;
    }

    private void loadPhoto(ImageView img, TextView tvCount) {
        if (urls == null || urls.isEmpty()) return;
        Glide.with(this).load(urls.get(currentIndex)).into(img);
        tvCount.setText(
                (currentIndex + 1) + " / " + urls.size());
    }
}
