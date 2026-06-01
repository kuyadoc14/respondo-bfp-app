package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AppCompatActivity;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirstAidEditorActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String mode, docId;

    private final List<Uri>    selectedPhotoUris = new ArrayList<>();
    private final List<String> existingPhotoUrls = new ArrayList<>();
    private Uri    selectedVideoUri  = null;
    private String existingVideoUrl  = "";

    private LinearLayout photoPreviewContainer;
    private TextView     tvVideoName, tvUploadStatus;
    private ProgressBar  uploadProgress;
    private Button       btnSave;

    // Photo picker
    private final ActivityResultLauncher<Intent> photoPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK
                                || result.getData() == null) return;
                        Intent data = result.getData();
                        if (data.getClipData() != null) {
                            int count = Math.min(
                                    data.getClipData().getItemCount(),
                                    5 - selectedPhotoUris.size()
                                            - existingPhotoUrls.size());
                            for (int i = 0; i < count; i++) {
                                selectedPhotoUris.add(
                                        data.getClipData().getItemAt(i).getUri());
                            }
                        } else if (data.getData() != null) {
                            if (selectedPhotoUris.size()
                                    + existingPhotoUrls.size() < 5) {
                                selectedPhotoUris.add(data.getData());
                            }
                        }
                        refreshPhotoPreviews();
                    });

    // Video picker
    private final ActivityResultLauncher<Intent> videoPicker =
            registerForActivityResult(
                    new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        if (result.getResultCode() != RESULT_OK
                                || result.getData() == null
                                || result.getData().getData() == null) return;
                        selectedVideoUri = result.getData().getData();
                        tvVideoName.setText("Video selected ✓");
                        tvVideoName.setTextColor(0xFF2a9d8f);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_editor);

        db   = FirebaseFirestore.getInstance();
        mode = getIntent().getStringExtra("mode");
        docId = getIntent().getStringExtra("id");

        TextView tvTitle       = findViewById(R.id.tvEditorTitle);
        EditText etIcon        = findViewById(R.id.etIcon);
        EditText etTitle       = findViewById(R.id.etTitle);
        EditText etCat         = findViewById(R.id.etCategory);
        EditText etDesc        = findViewById(R.id.etDescription);
        EditText etSteps       = findViewById(R.id.etSteps);
        EditText etVideo       = findViewById(R.id.etVideoUrl);
        Button   btnPickPhoto  = findViewById(R.id.btnPickPhoto);
        Button   btnPickVideo  = findViewById(R.id.btnPickVideo);
        Button   btnCancel     = findViewById(R.id.btnCancel);
        btnSave               = findViewById(R.id.btnSave);
        photoPreviewContainer = findViewById(R.id.photoPreviewContainer);
        tvVideoName           = findViewById(R.id.tvVideoName);
        tvUploadStatus        = findViewById(R.id.tvUploadStatus);
        uploadProgress        = findViewById(R.id.uploadProgress);

        if ("edit".equals(mode)) {
            tvTitle.setText("Edit First Aid");
            etIcon.setText(getIntent().getStringExtra("iconEmoji"));
            etTitle.setText(getIntent().getStringExtra("title"));
            etCat.setText(getIntent().getStringExtra("category"));
            etDesc.setText(getIntent().getStringExtra("description"));
            etVideo.setText(getIntent().getStringExtra("videoUrl"));

            ArrayList<String> steps =
                    getIntent().getStringArrayListExtra("steps");
            if (steps != null)
                etSteps.setText(
                        android.text.TextUtils.join("\n", steps));

            ArrayList<String> photos =
                    getIntent().getStringArrayListExtra("photoUrls");
            if (photos != null) {
                existingPhotoUrls.addAll(photos);
                refreshPhotoPreviews();
            }

            String sv = getIntent().getStringExtra("storageVideoUrl");
            existingVideoUrl = sv != null ? sv : "";
            if (!existingVideoUrl.isEmpty()) {
                tvVideoName.setText("Video uploaded ✓");
                tvVideoName.setTextColor(0xFF2a9d8f);
            }
        }

        btnPickPhoto.setOnClickListener(v -> {
            if (selectedPhotoUris.size()
                    + existingPhotoUrls.size() >= 5) {
                Toast.makeText(this,
                        "Maximum 5 photos allowed.",
                        Toast.LENGTH_SHORT).show();
                return;
            }
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("image/*");
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);
            photoPicker.launch(intent);
        });

        btnPickVideo.setOnClickListener(v -> {
            Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
            intent.setType("video/*");
            videoPicker.launch(intent);
        });

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String icon     = etIcon.getText().toString().trim();
            String title    = etTitle.getText().toString().trim();
            String cat      = etCat.getText().toString().trim();
            String desc     = etDesc.getText().toString().trim();
            String video    = etVideo.getText().toString().trim();
            String stepsRaw = etSteps.getText().toString().trim();

            if (title.isEmpty() || cat.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this,
                        "Title, category and description are required.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> stepsList = new ArrayList<>(
                    Arrays.asList(stepsRaw.split("\n")));
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stepsList.removeIf(String::isEmpty);
            }

            Map<String, Object> data = new HashMap<>();
            data.put("iconEmoji",   icon.isEmpty() ? "🩺" : icon);
            data.put("title",       title);
            data.put("category",    cat);
            data.put("description", desc);
            data.put("steps",       stepsList);
            data.put("videoUrl",    video);

            btnSave.setEnabled(false);
            btnSave.setText("Uploading...");

            uploadMediaAndSave(data);
        });
    }

    // ── Upload photos then video then save ─────────────────────────
    private void uploadMediaAndSave(Map<String, Object> data) {
        showUploadProgress(true);
        List<String> finalPhotoUrls = new ArrayList<>(existingPhotoUrls);

        if (selectedPhotoUris.isEmpty() && selectedVideoUri == null) {
            data.put("photoUrls",       finalPhotoUrls);
            data.put("storageVideoUrl", existingVideoUrl);
            saveToFirestore(data);
            return;
        }

        if (!selectedPhotoUris.isEmpty()) {
            uploadPhotos(new ArrayList<>(selectedPhotoUris),
                    finalPhotoUrls, 0, data);
        } else {
            data.put("photoUrls", finalPhotoUrls);
            uploadVideo(data);
        }
    }

    private void uploadPhotos(List<Uri> uris,
                              List<String> uploaded,
                              int index,
                              Map<String, Object> data) {
        if (index >= uris.size()) {
            data.put("photoUrls", uploaded);
            uploadVideo(data);
            return;
        }

        showStatus("Uploading photo "
                + (index + 1) + " of " + uris.size() + "...");

        MediaManager.get()
                .upload(uris.get(index))
                .option("upload_preset", "bfp_upload")
                .option("folder",        "bfp_first_aid/photos")
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}
                    @Override public void onProgress(String id,
                                                     long bytes, long total) {
                        uploadProgress.setProgress(
                                (int)(100.0 * bytes / total));
                    }
                    @Override public void onSuccess(String id,
                                                    Map resultData) {
                        uploaded.add((String) resultData.get("secure_url"));
                        uploadPhotos(uris, uploaded, index + 1, data);
                    }
                    @Override public void onError(String id,
                                                  ErrorInfo error) {
                        showUploadProgress(false);
                        resetSaveButton();
                        Toast.makeText(FirstAidEditorActivity.this,
                                "Photo upload failed: "
                                        + error.getDescription(),
                                Toast.LENGTH_SHORT).show();
                    }
                    @Override public void onReschedule(String id,
                                                       ErrorInfo error) {}
                })
                .dispatch();
    }

    private void uploadVideo(Map<String, Object> data) {
        if (selectedVideoUri == null) {
            data.put("storageVideoUrl", existingVideoUrl);
            saveToFirestore(data);
            return;
        }

        showStatus("Uploading video... 0%");

        MediaManager.get()
                .upload(selectedVideoUri)
                .option("upload_preset", "bfp_upload")
                .option("folder",        "bfp_first_aid/videos")
                .option("resource_type", "video")
                .callback(new UploadCallback() {
                    @Override
                    public void onStart(String id) {
                        showStatus("Starting video upload...");
                    }

                    @Override
                    public void onProgress(String id,
                                           long bytes, long total) {
                        int pct = total > 0
                                ? (int)(100.0 * bytes / total) : 0;
                        runOnUiThread(() -> {
                            uploadProgress.setProgress(pct);
                            showStatus("Uploading video... " + pct + "%"
                                    + " (" + formatSize(bytes)
                                    + " / " + formatSize(total) + ")");
                        });
                    }

                    @Override
                    public void onSuccess(String id, Map resultData) {
                        String url = (String) resultData.get("secure_url");
                        data.put("storageVideoUrl", url);
                        saveToFirestore(data);
                    }

                    @Override
                    public void onError(String id, ErrorInfo error) {
                        runOnUiThread(() -> {
                            showUploadProgress(false);
                            resetSaveButton();
                            Toast.makeText(FirstAidEditorActivity.this,
                                    "Video upload failed: "
                                            + error.getDescription(),
                                    Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override
                    public void onReschedule(String id, ErrorInfo error) {
                        runOnUiThread(() ->
                                showStatus("Retrying upload..."));
                    }
                })
                .dispatch();
    }

    // Helper to show readable file size
    private String formatSize(long bytes) {
        if (bytes < 1024 * 1024)
            return (bytes / 1024) + " KB";
        return String.format("%.1f MB", bytes / (1024.0 * 1024.0));
    }

    private void saveToFirestore(Map<String, Object> data) {
        showStatus("Saving...");
        if ("add".equals(mode)) {
            data.put("createdAt", FieldValue.serverTimestamp());
            db.collection("first_aid").add(data)
                    .addOnSuccessListener(r -> {
                        Toast.makeText(this,
                                "Added!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        showUploadProgress(false);
                        resetSaveButton();
                        Toast.makeText(this,
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        } else {
            db.collection("first_aid").document(docId).update(data)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(this,
                                "Updated!", Toast.LENGTH_SHORT).show();
                        finish();
                    })
                    .addOnFailureListener(e -> {
                        showUploadProgress(false);
                        resetSaveButton();
                        Toast.makeText(this,
                                "Save failed: " + e.getMessage(),
                                Toast.LENGTH_SHORT).show();
                    });
        }
    }

    // ── Photo previews ─────────────────────────────────────────────
    private void refreshPhotoPreviews() {
        photoPreviewContainer.removeAllViews();
        int size   = dp(90);
        int margin = dp(8);

        for (int i = 0; i < existingPhotoUrls.size(); i++) {
            final int idx = i;
            addPreview(null, existingPhotoUrls.get(i),
                    size, margin, () -> {
                        existingPhotoUrls.remove(idx);
                        refreshPhotoPreviews();
                    });
        }
        for (int i = 0; i < selectedPhotoUris.size(); i++) {
            final int idx = i;
            addPreview(selectedPhotoUris.get(i), null,
                    size, margin, () -> {
                        selectedPhotoUris.remove(idx);
                        refreshPhotoPreviews();
                    });
        }
    }

    private void addPreview(Uri uri, String url,
                            int size, int margin,
                            Runnable onRemove) {
        FrameLayout frame = new FrameLayout(this);
        LinearLayout.LayoutParams fp =
                new LinearLayout.LayoutParams(size, size);
        fp.setMargins(0, 0, margin, 0);
        frame.setLayoutParams(fp);

        ImageView img = new ImageView(this);
        img.setLayoutParams(
                new ViewGroup.LayoutParams(size, size));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);

        if (uri != null) Glide.with(this).load(uri).into(img);
        else             Glide.with(this).load(url).into(img);

        TextView x = new TextView(this);
        FrameLayout.LayoutParams xp =
                new FrameLayout.LayoutParams(
                        FrameLayout.LayoutParams.WRAP_CONTENT,
                        FrameLayout.LayoutParams.WRAP_CONTENT);
        xp.gravity =
                android.view.Gravity.TOP | android.view.Gravity.END;
        x.setLayoutParams(xp);
        x.setText("✕");
        x.setTextColor(0xFFFFFFFF);
        x.setTextSize(12);
        x.setPadding(6, 2, 6, 2);
        x.setBackgroundColor(0xAAe63946);
        x.setOnClickListener(v -> onRemove.run());

        frame.addView(img);
        frame.addView(x);
        photoPreviewContainer.addView(frame);
    }

    // ── Helpers ────────────────────────────────────────────────────
    private void showUploadProgress(boolean show) {
        uploadProgress.setVisibility(
                show ? View.VISIBLE : View.GONE);
        tvUploadStatus.setVisibility(
                show ? View.VISIBLE : View.GONE);
    }

    private void showStatus(String msg) {
        tvUploadStatus.setVisibility(View.VISIBLE);
        tvUploadStatus.setText(msg);
    }

    private void resetSaveButton() {
        btnSave.setEnabled(true);
        btnSave.setText("Save");
    }

    private int dp(int val) {
        return (int)(val *
                getResources().getDisplayMetrics().density);
    }
}