package com.bfp.alert;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
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
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.bumptech.glide.Glide;
import com.cloudinary.android.MediaManager;
import com.cloudinary.android.callback.ErrorInfo;
import com.cloudinary.android.callback.UploadCallback;
import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirstAidEditorFragment extends Fragment {

    private FirebaseFirestore db;
    private String mode, docId;

    private final List<Uri> selectedPhotoUris = new ArrayList<>();
    private final List<String> existingPhotoUrls = new ArrayList<>();
    private Uri selectedVideoUri = null;
    private String existingVideoUrl = "";

    private LinearLayout photoPreviewContainer;
    private LinearLayout stepsInputContainer;
    private TextView tvVideoName, tvUploadStatus;
    private ProgressBar uploadProgress;
    private Button btnSave;

    private final ActivityResultLauncher<Intent> photoPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != requireActivity().RESULT_OK || result.getData() == null) return;
                Intent data = result.getData();
                if (data.getClipData() != null) {
                    int count = Math.min(data.getClipData().getItemCount(), 5 - selectedPhotoUris.size() - existingPhotoUrls.size());
                    for (int i = 0; i < count; i++) {
                        selectedPhotoUris.add(data.getClipData().getItemAt(i).getUri());
                    }
                } else if (data.getData() != null) {
                    if (selectedPhotoUris.size() + existingPhotoUrls.size() < 5) {
                        selectedPhotoUris.add(data.getData());
                    }
                }
                refreshPhotoPreviews();
            });

    private final ActivityResultLauncher<Intent> videoPicker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            result -> {
                if (result.getResultCode() != requireActivity().RESULT_OK || result.getData() == null || result.getData().getData() == null) return;
                selectedVideoUri = result.getData().getData();
                tvVideoName.setText("Video selected ✓");
                tvVideoName.setTextColor(0xFF10B981);
            });

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater,
                             @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        View view = inflater.inflate(R.layout.activity_first_aid_editor, container, false);

        db = FirebaseFirestore.getInstance();
        mode = requireArguments().getString("mode", "add");
        docId = requireArguments().getString("id");

        TextView tvTitle = view.findViewById(R.id.tvEditorTitle);
        EditText etIcon = view.findViewById(R.id.etIcon);
        EditText etTitle = view.findViewById(R.id.etTitle);
        EditText etCat = view.findViewById(R.id.etCategory);
        EditText etDesc = view.findViewById(R.id.etDescription);
        EditText etVideo = view.findViewById(R.id.etVideoUrl);
        Button btnPickPhoto = view.findViewById(R.id.btnPickPhoto);
        Button btnPickVideo = view.findViewById(R.id.btnPickVideo);
        Button btnAddStep = view.findViewById(R.id.btnAddStep);
        Button btnCancel = view.findViewById(R.id.btnCancel);
        btnSave = view.findViewById(R.id.btnSave);
        stepsInputContainer = view.findViewById(R.id.stepsInputContainer);
        photoPreviewContainer = view.findViewById(R.id.photoPreviewContainer);
        tvVideoName = view.findViewById(R.id.tvVideoName);
        tvUploadStatus = view.findViewById(R.id.tvUploadStatus);
        uploadProgress = view.findViewById(R.id.uploadProgress);

        view.findViewById(R.id.btnBack).setOnClickListener(v ->
            requireActivity().getSupportFragmentManager().popBackStack());

        if ("edit".equals(mode)) {
            tvTitle.setText("Edit First Aid");
            etIcon.setText(requireArguments().getString("iconEmoji"));
            etTitle.setText(requireArguments().getString("title"));
            etCat.setText(requireArguments().getString("category"));
            etDesc.setText(requireArguments().getString("description"));
            etVideo.setText(requireArguments().getString("videoUrl"));

            ArrayList<String> steps = requireArguments().getStringArrayList("steps");
            if (steps != null && !steps.isEmpty()) {
                for (String step : steps) addStepField(step);
            } else {
                addStepField("");
            }

            ArrayList<String> photos = requireArguments().getStringArrayList("photoUrls");
            if (photos != null) {
                existingPhotoUrls.addAll(photos);
                refreshPhotoPreviews();
            }

            String sv = requireArguments().getString("storageVideoUrl");
            existingVideoUrl = sv != null ? sv : "";
            if (!existingVideoUrl.isEmpty()) {
                tvVideoName.setText("Video uploaded ✓");
                tvVideoName.setTextColor(0xFF10B981);
            }
        } else {
            addStepField("");
        }

        btnAddStep.setOnClickListener(v -> addStepField(""));

        btnPickPhoto.setOnClickListener(v -> {
            if (selectedPhotoUris.size() + existingPhotoUrls.size() >= 5) {
                Toast.makeText(requireContext(), "Maximum 5 photos allowed.", Toast.LENGTH_SHORT).show();
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

        btnCancel.setOnClickListener(v -> requireActivity().getSupportFragmentManager().popBackStack());

        btnSave.setOnClickListener(v -> {
            String icon = etIcon.getText().toString().trim();
            String title = etTitle.getText().toString().trim();
            String cat = etCat.getText().toString().trim();
            String desc = etDesc.getText().toString().trim();
            String video = etVideo.getText().toString().trim();

            if (title.isEmpty() || cat.isEmpty() || desc.isEmpty()) {
                Toast.makeText(requireContext(), "Title, category and description are required.", Toast.LENGTH_SHORT).show();
                return;
            }

            List<String> stepsList = collectSteps();
            if (stepsList.isEmpty()) {
                Toast.makeText(requireContext(), "Please add at least one step.", Toast.LENGTH_SHORT).show();
                return;
            }

            Map<String, Object> data = new HashMap<>();
            data.put("iconEmoji", icon.isEmpty() ? "🩺" : icon);
            data.put("title", title);
            data.put("category", cat);
            data.put("description", desc);
            data.put("steps", stepsList);
            data.put("videoUrl", video);

            btnSave.setEnabled(false);
            btnSave.setText("Uploading...");
            uploadMediaAndSave(data);
        });

        return view;
    }

    private void addStepField(String value) {
        int stepNumber = stepsInputContainer.getChildCount() + 1;

        LinearLayout row = new LinearLayout(requireContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(android.view.Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rowParams.setMargins(0, 0, 0, dp(10));
        row.setLayoutParams(rowParams);

        TextView numView = new TextView(requireContext());
        numView.setText(String.valueOf(stepNumber));
        numView.setTextColor(0xFFFFFFFF);
        numView.setTextSize(12);
        numView.setGravity(android.view.Gravity.CENTER);
        numView.setBackground(requireContext().getDrawable(R.drawable.bg_step_number));
        LinearLayout.LayoutParams numParams = new LinearLayout.LayoutParams(dp(32), dp(32));
        numParams.setMargins(0, 0, dp(10), 0);
        numView.setLayoutParams(numParams);

        EditText etStep = new EditText(requireContext());
        etStep.setHint("Describe step " + stepNumber);
        etStep.setHintTextColor(0xFF9CA3AF);
        etStep.setTextColor(0xFF1A1A2E);
        etStep.setTextSize(14);
        etStep.setBackground(requireContext().getDrawable(R.drawable.bg_input));
        etStep.setPadding(dp(16), dp(12), dp(16), dp(12));
        etStep.setInputType(android.text.InputType.TYPE_CLASS_TEXT | android.text.InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        etStep.setMinLines(1);
        etStep.setMaxLines(3);
        LinearLayout.LayoutParams etParams = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        etStep.setLayoutParams(etParams);

        if (!value.isEmpty()) etStep.setText(value);

        TextView removeBtn = new TextView(requireContext());
        removeBtn.setText("✕");
        removeBtn.setTextColor(0xFFFC4D4D);
        removeBtn.setTextSize(16);
        removeBtn.setGravity(android.view.Gravity.CENTER);
        removeBtn.setPadding(dp(10), dp(8), dp(4), dp(8));
        removeBtn.setClickable(true);
        removeBtn.setFocusable(true);

        removeBtn.setOnClickListener(v -> {
            if (stepsInputContainer.getChildCount() <= 1) {
                Toast.makeText(requireContext(), "At least one step is required.", Toast.LENGTH_SHORT).show();
                return;
            }
            stepsInputContainer.removeView(row);
            renumberSteps();
        });

        row.addView(numView);
        row.addView(etStep);
        row.addView(removeBtn);
        stepsInputContainer.addView(row);
    }

    private void renumberSteps() {
        for (int i = 0; i < stepsInputContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) stepsInputContainer.getChildAt(i);
            TextView numView = (TextView) row.getChildAt(0);
            numView.setText(String.valueOf(i + 1));

            EditText etStep = (EditText) row.getChildAt(1);
            etStep.setHint("Describe step " + (i + 1));
        }
    }

    private List<String> collectSteps() {
        List<String> steps = new ArrayList<>();
        for (int i = 0; i < stepsInputContainer.getChildCount(); i++) {
            LinearLayout row = (LinearLayout) stepsInputContainer.getChildAt(i);
            EditText etStep = (EditText) row.getChildAt(1);
            String val = etStep.getText().toString().trim();
            if (!val.isEmpty()) steps.add(val);
        }
        return steps;
    }

    private void uploadMediaAndSave(Map<String, Object> data) {
        showUploadProgress(true);
        List<String> finalPhotoUrls = new ArrayList<>(existingPhotoUrls);

        if (selectedPhotoUris.isEmpty() && selectedVideoUri == null) {
            data.put("photoUrls", finalPhotoUrls);
            data.put("storageVideoUrl", existingVideoUrl);
            saveToFirestore(data);
            return;
        }

        if (!selectedPhotoUris.isEmpty()) {
            uploadPhotos(new ArrayList<>(selectedPhotoUris), finalPhotoUrls, 0, data);
        } else {
            data.put("photoUrls", finalPhotoUrls);
            uploadVideo(data);
        }
    }

    private void uploadPhotos(List<Uri> uris, List<String> uploaded, int index, Map<String, Object> data) {
        if (index >= uris.size()) {
            data.put("photoUrls", uploaded);
            uploadVideo(data);
            return;
        }

        showStatus("Uploading photo " + (index + 1) + " of " + uris.size() + "...");

        MediaManager.get()
                .upload(uris.get(index))
                .option("upload_preset", "bfp_upload")
                .option("folder", "bfp_first_aid/photos")
                .option("resource_type", "image")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) {}

                    @Override public void onProgress(String id, long bytes, long total) {
                        uploadProgress.setProgress((int) (100.0 * bytes / total));
                    }

                    @Override public void onSuccess(String id, Map resultData) {
                        uploaded.add((String) resultData.get("secure_url"));
                        uploadPhotos(uris, uploaded, index + 1, data);
                    }

                    @Override public void onError(String id, ErrorInfo error) {
                        requireActivity().runOnUiThread(() -> {
                            showUploadProgress(false);
                            resetSaveButton();
                            Toast.makeText(requireContext(), "Photo upload failed: " + error.getDescription(), Toast.LENGTH_SHORT).show();
                        });
                    }

                    @Override public void onReschedule(String id, ErrorInfo error) {}
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
                .option("folder", "bfp_first_aid/videos")
                .option("resource_type", "video")
                .callback(new UploadCallback() {
                    @Override public void onStart(String id) { showStatus("Starting video upload..."); }

                    @Override public void onProgress(String id, long bytes, long total) {
                        int pct = total > 0 ? (int) (100.0 * bytes / total) : 0;
                        requireActivity().runOnUiThread(() -> {
                            uploadProgress.setProgress(pct);
                            showStatus("Uploading video... " + pct + "% (");
                        });
                    }

                    @Override public void onSuccess(String id, Map resultData) {
                        data.put("storageVideoUrl", resultData.get("secure_url"));
                        saveToFirestore(data);
                    }

                    @Override public void onError(String id, ErrorInfo error) {
                        requireActivity().runOnUiThread(() -> {
                            showUploadProgress(false);
                            resetSaveButton();
                            Toast.makeText(requireContext(), "Video upload failed: " + error.getDescription(), Toast.LENGTH_LONG).show();
                        });
                    }

                    @Override public void onReschedule(String id, ErrorInfo error) {
                        requireActivity().runOnUiThread(() -> showStatus("Retrying..."));
                    }
                })
                .dispatch();
    }

    private void saveToFirestore(Map<String, Object> data) {
        showStatus("Saving...");
        if ("add".equals(mode)) {
            data.put("createdAt", FieldValue.serverTimestamp());
            db.collection("first_aid").add(data)
                    .addOnSuccessListener(r -> {
                        Toast.makeText(requireContext(), "Added successfully!", Toast.LENGTH_SHORT).show();
                        requireActivity().getSupportFragmentManager().popBackStack();
                    })
                    .addOnFailureListener(e -> {
                        showUploadProgress(false);
                        resetSaveButton();
                        Toast.makeText(requireContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        } else {
            db.collection("first_aid").document(docId).update(data)
                    .addOnSuccessListener(u -> {
                        Toast.makeText(requireContext(), "Updated successfully!", Toast.LENGTH_SHORT).show();
                        requireActivity().getSupportFragmentManager().popBackStack();
                    })
                    .addOnFailureListener(e -> {
                        showUploadProgress(false);
                        resetSaveButton();
                        Toast.makeText(requireContext(), "Save failed: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                    });
        }
    }

    private void refreshPhotoPreviews() {
        photoPreviewContainer.removeAllViews();
        int size = dp(90);
        int margin = dp(8);

        for (int i = 0; i < existingPhotoUrls.size(); i++) {
            final int idx = i;
            addPreview(null, existingPhotoUrls.get(i), size, margin, () -> {
                existingPhotoUrls.remove(idx);
                refreshPhotoPreviews();
            });
        }

        for (int i = 0; i < selectedPhotoUris.size(); i++) {
            final int idx = i;
            addPreview(selectedPhotoUris.get(i), null, size, margin, () -> {
                selectedPhotoUris.remove(idx);
                refreshPhotoPreviews();
            });
        }
    }

    private void addPreview(Uri uri, String url, int size, int margin, Runnable onRemove) {
        FrameLayout frame = new FrameLayout(requireContext());
        LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(size, size);
        fp.setMargins(0, 0, margin, 0);
        frame.setLayoutParams(fp);

        ImageView img = new ImageView(requireContext());
        img.setLayoutParams(new ViewGroup.LayoutParams(size, size));
        img.setScaleType(ImageView.ScaleType.CENTER_CROP);
        img.setClipToOutline(true);
        img.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View view, android.graphics.Outline outline) {
                outline.setRoundRect(0, 0, view.getWidth(), view.getHeight(), dp(10));
            }
        });

        if (uri != null) {
            Glide.with(this).load(uri).into(img);
        } else {
            Glide.with(this).load(url).into(img);
        }

        TextView x = new TextView(requireContext());
        FrameLayout.LayoutParams xp = new FrameLayout.LayoutParams(dp(22), dp(22));
        xp.gravity = android.view.Gravity.TOP | android.view.Gravity.END;
        x.setLayoutParams(xp);
        x.setText("✕");
        x.setTextColor(0xFFFFFFFF);
        x.setTextSize(12);
        x.setGravity(android.view.Gravity.CENTER);
        x.setBackground(requireContext().getDrawable(R.drawable.bg_tab_selected));
        x.setOnClickListener(v -> onRemove.run());

        frame.addView(img);
        frame.addView(x);
        photoPreviewContainer.addView(frame);
    }

    private void showUploadProgress(boolean show) {
        uploadProgress.setVisibility(show ? View.VISIBLE : View.GONE);
        tvUploadStatus.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void resetSaveButton() {
        btnSave.setEnabled(true);
        btnSave.setText("Save");
    }

    private void showStatus(String text) {
        tvUploadStatus.setText(text);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
