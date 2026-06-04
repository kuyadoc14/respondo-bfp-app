package com.bfp.alert;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.speech.RecognitionListener;
import android.speech.RecognizerIntent;
import android.speech.SpeechRecognizer;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.view.animation.AccelerateDecelerateInterpolator;
import android.widget.TextView;

import androidx.core.content.ContextCompat;

import java.util.ArrayList;
import java.util.Locale;

public class VoiceAssistantDialog extends Dialog {

    public interface ActionListener {
        void onSendSOS();
        void onOpenFirstAid();
        void onSearchFirstAid(String query);
    }

    private ClaudeVoiceAssistant assistant;
    private SpeechRecognizer     recognizer;
    private ActionListener       actionListener;
    private Handler              handler;

    // Views
    private TextView tvResponse;
    private TextView tvUserTranscript;
    private TextView tvMicStatus;
    private TextView tvMicIcon;
    private View     btnMic;
    private View     ringOuter, ringInner;
    private View     thinkingIndicator;

    // State
    private boolean isListening  = false;
    private boolean isProcessing = false;
    private AnimatorSet pulseAnimation;

    public VoiceAssistantDialog(Context ctx,
                                ActionListener listener) {
        super(ctx,
                com.google.android.material.R.style
                        .Theme_MaterialComponents_Light_BottomSheetDialog);
        this.actionListener = listener;
        this.handler =
                new Handler(Looper.getMainLooper());
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_voice_assistant);

        // Bottom sheet positioning
        Window window = getWindow();
        if (window != null) {
            window.setLayout(
                    WindowManager.LayoutParams.MATCH_PARENT,
                    WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(
                    android.view.Gravity.BOTTOM);
            window.setBackgroundDrawableResource(
                    android.R.color.transparent);
        }

        // View refs
        tvResponse         =
                findViewById(R.id.tvAssistantResponse);
        tvUserTranscript   =
                findViewById(R.id.tvUserTranscript);
        tvMicStatus        =
                findViewById(R.id.tvMicStatus);
        tvMicIcon          =
                findViewById(R.id.tvMicIcon);
        btnMic             =
                findViewById(R.id.btnMic);
        ringOuter          =
                findViewById(R.id.ringOuter);
        ringInner          =
                findViewById(R.id.ringInner);
        thinkingIndicator  =
                findViewById(R.id.thinkingIndicator);

        // Init Claude assistant
        assistant = new ClaudeVoiceAssistant(
                getContext());

        // Init speech recognizer
        initSpeechRecognizer();

        // Mic button
        btnMic.setOnClickListener(v -> {
            if (isListening) {
                stopListening();
            } else if (!isProcessing) {
                startListening();
            }
        });

        // Close button
        findViewById(R.id.btnCloseVoice)
                .setOnClickListener(v -> dismiss());

        // Quick chips
        findViewById(R.id.chipSOS)
                .setOnClickListener(v ->
                        sendToAssistant("Send SOS alert"));

        findViewById(R.id.chipFirstAid)
                .setOnClickListener(v ->
                        sendToAssistant("Open first aid guides"));

        findViewById(R.id.chipCPR)
                .setOnClickListener(v ->
                        sendToAssistant("Show me CPR steps"));

        findViewById(R.id.chipCall)
                .setOnClickListener(v ->
                        sendToAssistant("Call the fire station"));

        // Greet on open
        handler.postDelayed(() ->
                        assistant.speak(
                                "Hello! I am your BFP AI assistant. "
                                        + "Tap the microphone and tell me "
                                        + "how I can help you.",
                                new ClaudeVoiceAssistant
                                        .AssistantCallback() {
                                    @Override
                                    public void onResponse(
                                            String text,
                                            String action,
                                            String data) {}
                                    @Override
                                    public void onError(String e) {}
                                    @Override
                                    public void onSpeakingStarted() {}
                                    @Override
                                    public void onSpeakingFinished() {}
                                }),
                400);
    }

    // ── Speech recognizer ─────────────────────────────────────────
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(getContext()))
            return;

        recognizer = SpeechRecognizer
                .createSpeechRecognizer(getContext());

        recognizer.setRecognitionListener(
                new RecognitionListener() {
                    @Override
                    public void onReadyForSpeech(Bundle p) {
                        setStatus("Listening...",
                                0xFFFC4D4D);
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        setStatus("I hear you...",
                                0xFFFC4D4D);
                    }

                    @Override
                    public void onRmsChanged(float v) {}

                    @Override
                    public void onBufferReceived(byte[] b) {}

                    @Override
                    public void onEndOfSpeech() {
                        stopListeningUI();
                        setStatus("Processing...",
                                0xFF6B7280);
                    }

                    @Override
                    public void onError(int error) {
                        stopListeningUI();
                        if (error ==
                                SpeechRecognizer
                                        .ERROR_NO_MATCH ||
                                error ==
                                        SpeechRecognizer
                                                .ERROR_SPEECH_TIMEOUT) {
                            setStatus(
                                    "Tap mic to try again",
                                    0xFF6B7280);
                        } else {
                            setStatus(
                                    "Error. Tap mic to retry.",
                                    0xFF6B7280);
                        }
                        isListening   = false;
                        isProcessing  = false;
                    }

                    @Override
                    public void onResults(Bundle results) {
                        ArrayList<String> matches =
                                results.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION);
                        if (matches != null
                                && !matches.isEmpty()) {
                            String text = matches.get(0);
                            showUserTranscript(text);
                            sendToAssistant(text);
                        }
                    }

                    @Override
                    public void onPartialResults(
                            Bundle partial) {
                        ArrayList<String> partial2 =
                                partial.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION);
                        if (partial2 != null
                                && !partial2.isEmpty()) {
                            showUserTranscript(
                                    partial2.get(0) + "...");
                        }
                    }

                    @Override
                    public void onEvent(int t, Bundle e) {}
                });
    }

    private void startListening() {
        if (ContextCompat.checkSelfPermission(
                getContext(),
                Manifest.permission.RECORD_AUDIO)
                != PackageManager.PERMISSION_GRANTED) {
            setStatus(
                    "Microphone permission required.",
                    0xFFFC4D4D);
            return;
        }

        isListening = true;
        assistant.stopSpeaking();

        // Show rings
        ringOuter.setVisibility(View.VISIBLE);
        ringInner.setVisibility(View.VISIBLE);
        startPulseAnimation();

        tvMicIcon.setText("⏹");
        setStatus("Listening... tap to stop",
                0xFFFC4D4D);
        tvUserTranscript.setVisibility(View.GONE);

        Intent intent = new Intent(
                RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE_MODEL,
                RecognizerIntent
                        .LANGUAGE_MODEL_FREE_FORM);
        intent.putExtra(
                RecognizerIntent.EXTRA_LANGUAGE,
                Locale.ENGLISH);
        intent.putExtra(
                RecognizerIntent.EXTRA_PARTIAL_RESULTS,
                true);
        intent.putExtra(
                RecognizerIntent.EXTRA_MAX_RESULTS, 1);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            stopListeningUI();
            setStatus("Could not start mic.",
                    0xFFFC4D4D);
        }
    }

    private void stopListening() {
        if (recognizer != null) {
            recognizer.stopListening();
        }
        stopListeningUI();
    }

    private void stopListeningUI() {
        isListening = false;
        ringOuter.setVisibility(View.GONE);
        ringInner.setVisibility(View.GONE);
        stopPulseAnimation();
        tvMicIcon.setText("🎙");
    }

    // ── Send text to Claude ───────────────────────────────────────
    private void sendToAssistant(String userText) {
        if (isProcessing) return;
        isProcessing = true;

        stopListeningUI();
        showUserTranscript(userText);
        showThinking(true);
        setStatus("Thinking...", 0xFF6B7280);
        btnMic.setEnabled(false);

        assistant.processUserInput(userText,
                new ClaudeVoiceAssistant.AssistantCallback() {
                    @Override
                    public void onResponse(String text,
                                           String action,
                                           String data) {
                        handler.post(() -> {
                            showThinking(false);
                            isProcessing = false;
                            btnMic.setEnabled(true);

                            // Show response
                            tvResponse.setText(text);
                            setStatus(
                                    "Tap mic to speak",
                                    0xFF6B7280);

                            // Speak the response
                            assistant.speak(text,
                                    new ClaudeVoiceAssistant
                                            .AssistantCallback() {
                                        @Override
                                        public void onResponse(
                                                String t, String a,
                                                String d) {}
                                        @Override
                                        public void onError(
                                                String e) {}
                                        @Override
                                        public void onSpeakingStarted() {}
                                        @Override
                                        public void onSpeakingFinished() {
                                            // Execute action
                                            // after speaking
                                            handler.postDelayed(
                                                    () -> executeAction(
                                                            action, data),
                                                    300);
                                        }
                                    });
                        });
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            showThinking(false);
                            isProcessing = false;
                            btnMic.setEnabled(true);
                            tvResponse.setText(error);
                            setStatus(
                                    "Tap mic to try again",
                                    0xFF6B7280);
                            assistant.speak(error, null);
                        });
                    }

                    @Override
                    public void onSpeakingStarted() {}

                    @Override
                    public void onSpeakingFinished() {}
                });
    }

    // ── Execute action from Claude ────────────────────────────────
    private void executeAction(String action,
                               String data) {
        if (action == null) return;

        switch (action) {
            case ClaudeVoiceAssistant.ACTION_SEND_SOS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onSendSOS();
                    }
                    dismiss();
                }, 800);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_OPEN_FIRSTAID:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onOpenFirstAid();
                    }
                    dismiss();
                }, 800);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_SEARCH_FIRSTAID:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener
                                .onSearchFirstAid(
                                        data != null
                                                ? data : "");
                    }
                    dismiss();
                }, 800);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_CALL_STATION:
                handler.postDelayed(() -> {
                    callBFP();
                }, 800);
                break;

            case ClaudeVoiceAssistant.ACTION_NONE:
            default:
                // No action — just spoken response
                break;
        }
    }

    private void callBFP() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:911"));
            getContext().startActivity(intent);
        } catch (Exception e) {
            tvResponse.setText(
                    "Could not open phone app.");
        }
    }

    // ── Pulse animation ───────────────────────────────────────────
    private void startPulseAnimation() {
        isListening = true;
        runPulse();
    }

    private void runPulse() {
        if (!isListening) return;

        AnimatorSet pulse = new AnimatorSet();

        ObjectAnimator scaleX1 = ObjectAnimator.ofFloat(
                ringOuter, "scaleX", 1f, 1.3f);
        ObjectAnimator scaleY1 = ObjectAnimator.ofFloat(
                ringOuter, "scaleY", 1f, 1.3f);
        ObjectAnimator alpha1  = ObjectAnimator.ofFloat(
                ringOuter, "alpha",  0.2f, 0.5f);
        ObjectAnimator scaleX2 = ObjectAnimator.ofFloat(
                ringInner, "scaleX", 1f, 1.15f);
        ObjectAnimator scaleY2 = ObjectAnimator.ofFloat(
                ringInner, "scaleY", 1f, 1.15f);

        pulse.playTogether(
                scaleX1, scaleY1, alpha1,
                scaleX2, scaleY2);
        pulse.setDuration(600);
        pulse.setInterpolator(
                new AccelerateDecelerateInterpolator());

        pulse.addListener(
                new android.animation.AnimatorListenerAdapter() {
                    @Override
                    public void onAnimationEnd(
                            android.animation.Animator a) {
                        if (isListening) {
                            // Reverse back
                            AnimatorSet reverse =
                                    new AnimatorSet();
                            ObjectAnimator rx1 =
                                    ObjectAnimator.ofFloat(
                                            ringOuter, "scaleX",
                                            1.3f, 1f);
                            ObjectAnimator ry1 =
                                    ObjectAnimator.ofFloat(
                                            ringOuter, "scaleY",
                                            1.3f, 1f);
                            ObjectAnimator ra1 =
                                    ObjectAnimator.ofFloat(
                                            ringOuter, "alpha",
                                            0.5f, 0.2f);
                            ObjectAnimator rx2 =
                                    ObjectAnimator.ofFloat(
                                            ringInner, "scaleX",
                                            1.15f, 1f);
                            ObjectAnimator ry2 =
                                    ObjectAnimator.ofFloat(
                                            ringInner, "scaleY",
                                            1.15f, 1f);
                            reverse.playTogether(
                                    rx1, ry1, ra1, rx2, ry2);
                            reverse.setDuration(600);
                            reverse.setInterpolator(
                                    new AccelerateDecelerateInterpolator());
                            reverse.addListener(
                                    new android.animation
                                            .AnimatorListenerAdapter() {
                                        @Override
                                        public void onAnimationEnd(
                                                android.animation
                                                        .Animator a2) {
                                            runPulse();
                                        }
                                    });
                            reverse.start();
                        }
                    }
                });

        pulseAnimation = pulse;
        pulse.start();
    }

    private void stopPulseAnimation() {
        isListening = false;
        if (pulseAnimation != null) {
            pulseAnimation.cancel();
            pulseAnimation = null;
        }
        // Reset ring scale
        if (ringOuter != null) {
            ringOuter.setScaleX(1f);
            ringOuter.setScaleY(1f);
            ringOuter.setAlpha(0.2f);
        }
        if (ringInner != null) {
            ringInner.setScaleX(1f);
            ringInner.setScaleY(1f);
        }
    }

    // ── UI helpers ────────────────────────────────────────────────
    private void showUserTranscript(String text) {
        handler.post(() -> {
            tvUserTranscript.setText(
                    "You said: \"" + text + "\"");
            tvUserTranscript.setVisibility(
                    View.VISIBLE);
        });
    }

    private void showThinking(boolean show) {
        handler.post(() ->
                thinkingIndicator.setVisibility(
                        show ? View.VISIBLE : View.GONE));
    }

    private void setStatus(String status,
                           int color) {
        handler.post(() -> {
            tvMicStatus.setText(status);
            tvMicStatus.setTextColor(color);
        });
    }

    @Override
    public void dismiss() {
        if (assistant != null) {
            assistant.stopSpeaking();
            assistant.release();
        }
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.dismiss();
    }
}