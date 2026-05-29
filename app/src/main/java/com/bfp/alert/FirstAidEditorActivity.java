package com.bfp.alert;

import android.os.Build;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.google.firebase.firestore.FieldValue;
import com.google.firebase.firestore.FirebaseFirestore;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FirstAidEditorActivity extends AppCompatActivity {

    private FirebaseFirestore db;
    private String mode;
    private String docId;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_first_aid_editor);

        db   = FirebaseFirestore.getInstance();
        mode = getIntent().getStringExtra("mode");
        docId = getIntent().getStringExtra("id");

        TextView tvTitle   = findViewById(R.id.tvEditorTitle);
        EditText etIcon    = findViewById(R.id.etIcon);
        EditText etTitle   = findViewById(R.id.etTitle);
        EditText etCat     = findViewById(R.id.etCategory);
        EditText etDesc    = findViewById(R.id.etDescription);
        EditText etSteps   = findViewById(R.id.etSteps);
        EditText etVideo   = findViewById(R.id.etVideoUrl);
        Button   btnSave   = findViewById(R.id.btnSave);
        Button   btnCancel = findViewById(R.id.btnCancel);

        if ("edit".equals(mode)) {
            tvTitle.setText("Edit First Aid");

            // Pre-fill fields
            etIcon.setText(getIntent().getStringExtra("iconEmoji"));
            etTitle.setText(getIntent().getStringExtra("title"));
            etCat.setText(getIntent().getStringExtra("category"));
            etDesc.setText(getIntent().getStringExtra("description"));
            etVideo.setText(getIntent().getStringExtra("videoUrl"));

            ArrayList<String> steps =
                    getIntent().getStringArrayListExtra("steps");
            if (steps != null) {
                etSteps.setText(android.text.TextUtils.join("\n", steps));
            }
        }

        btnCancel.setOnClickListener(v -> finish());

        btnSave.setOnClickListener(v -> {
            String icon  = etIcon.getText().toString().trim();
            String title = etTitle.getText().toString().trim();
            String cat   = etCat.getText().toString().trim();
            String desc  = etDesc.getText().toString().trim();
            String video = etVideo.getText().toString().trim();
            String stepsRaw = etSteps.getText().toString().trim();

            if (title.isEmpty() || cat.isEmpty() || desc.isEmpty()) {
                Toast.makeText(this,
                        "Title, category, and description are required.",
                        Toast.LENGTH_SHORT).show();
                return;
            }

            // Split steps by newline
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
            btnSave.setText("Saving...");

            if ("add".equals(mode)) {
                data.put("createdAt", FieldValue.serverTimestamp());
                db.collection("first_aid").add(data)
                        .addOnSuccessListener(ref -> {
                            Toast.makeText(this,
                                    "Added successfully.", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save");
                            Toast.makeText(this,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            } else {
                db.collection("first_aid").document(docId).update(data)
                        .addOnSuccessListener(u -> {
                            Toast.makeText(this,
                                    "Updated successfully.", Toast.LENGTH_SHORT).show();
                            finish();
                        })
                        .addOnFailureListener(e -> {
                            btnSave.setEnabled(true);
                            btnSave.setText("Save");
                            Toast.makeText(this,
                                    "Failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show();
                        });
            }
        });
    }
}