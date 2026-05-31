package com.bfp.alert;

import android.os.Bundle;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;

import java.util.ArrayList;

public class PhotoViewerActivity extends AppCompatActivity {

    private int currentIndex;
    private ArrayList<String> urls;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().setFlags(
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN,
                android.view.WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_photo_viewer);

        urls         = getIntent().getStringArrayListExtra("allUrls");
        currentIndex = getIntent().getIntExtra("startIndex", 0);

        ImageView imgView = findViewById(R.id.fullScreenImage);
        TextView  tvClose = findViewById(R.id.btnClose);
        TextView  tvCount = findViewById(R.id.tvPhotoCount);
        TextView  btnPrev = findViewById(R.id.btnPrev);
        TextView  btnNext = findViewById(R.id.btnNext);

        tvClose.setOnClickListener(v -> finish());

        btnPrev.setOnClickListener(v -> {
            if (currentIndex > 0) {
                currentIndex--;
                loadPhoto(imgView, tvCount);
            }
        });

        btnNext.setOnClickListener(v -> {
            if (currentIndex < urls.size() - 1) {
                currentIndex++;
                loadPhoto(imgView, tvCount);
            }
        });

        loadPhoto(imgView, tvCount);
    }

    private void loadPhoto(ImageView img, TextView tvCount) {
        Glide.with(this).load(urls.get(currentIndex)).into(img);
        tvCount.setText(
                (currentIndex + 1) + " / " + urls.size());
    }
}