package com.bfp.alert;

import android.Manifest;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.media.MediaPlayer;
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

    // ── Action listener interface ─────────────────────────────────
    public interface ActionListener {
        void onSendSOS();
        void onOpenFirstAid();
        void onSearchFirstAid(String query);
    }

    // ── Fields ────────────────────────────────────────────────────
    private ClaudeVoiceAssistant assistant;
    private SpeechRecognizer     recognizer;
    private final ActionListener actionListener;
    private final Handler        handler;
    private final boolean        handsFreeMode;
    private final Runnable       finalizeSpeechRunnable =
            this::finalizeSpeechAfterSilence;

    // Views
    private TextView tvResponse;
    private TextView tvUserTranscript;
    private TextView tvMicStatus;
    private TextView tvMicIcon;
    private View     btnMic;
    private View     ringOuter;
    private View     ringInner;
    private View     thinkingIndicator;

    // State
    private boolean    isListening  = false;
    private boolean    isProcessing = false;
    private boolean    manuallyStopped = false;
    private boolean    speechFinalizationRequested = false;
    private AnimatorSet pulseAnimation;
    private MediaPlayer listeningPlayer;

    // ── Constructor ───────────────────────────────────────────────
    public VoiceAssistantDialog(Context ctx,
                                ActionListener listener) {
        this(ctx, listener, false);
    }

    public VoiceAssistantDialog(Context ctx,
                                ActionListener listener,
                                boolean handsFreeMode) {
        super(ctx);
        this.actionListener = listener;
        this.handsFreeMode = handsFreeMode;
        this.handler =
                new Handler(Looper.getMainLooper());
    }

    // ── onCreate ──────────────────────────────────────────────────
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        setContentView(R.layout.dialog_voice_assistant);

        // Bottom sheet style
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

        // Bind views
        tvResponse        =
                findViewById(R.id.tvAssistantResponse);
        tvUserTranscript  =
                findViewById(R.id.tvUserTranscript);
        tvMicStatus       =
                findViewById(R.id.tvMicStatus);
        tvMicIcon         =
                findViewById(R.id.tvMicIcon);
        btnMic            =
                findViewById(R.id.btnMic);
        ringOuter         =
                findViewById(R.id.ringOuter);
        ringInner         =
                findViewById(R.id.ringInner);
        thinkingIndicator =
                findViewById(R.id.thinkingIndicator);

        // Init Groq AI assistant
        assistant = new ClaudeVoiceAssistant(
                getContext());

        // Init speech recognizer
        initSpeechRecognizer();

        // Mic button tap
        btnMic.setOnClickListener(v -> {
            if (isListening) {
                manuallyStopped = true;
                stopListening();
            } else if (!isProcessing) {
                manuallyStopped = false;
                startListening();
            }
        });

        // Close button
        findViewById(R.id.btnCloseVoice)
                .setOnClickListener(v -> dismiss());

        // Quick command chips
        findViewById(R.id.chipSOS)
                .setOnClickListener(v ->
                        sendToAssistant("Send SOS alert"));

        findViewById(R.id.chipFirstAid)
                .setOnClickListener(v ->
                        sendToAssistant(
                                "Open first aid guides"));

        findViewById(R.id.chipCPR)
                .setOnClickListener(v ->
                        sendToAssistant(
                                "Show me CPR steps"));

        findViewById(R.id.chipCall)
                .setOnClickListener(v ->
                        sendToAssistant(
                                "Call the fire station"));

        setStatus(handsFreeMode
                        ? "Listening hands-free... say a command."
                        : "Listening... tell me how I can help.",
                0xFFFC4D4D);
        handler.postDelayed(this::startListening, 400);
    }

    // ════════════════════════════════════════════════════════════
    //  Speech Recognizer
    // ════════════════════════════════════════════════════════════
    private void initSpeechRecognizer() {
        if (!SpeechRecognizer
                .isRecognitionAvailable(getContext())) {
            setStatus(
                    "Speech recognition not available.",
                    0xFFFC4D4D);
            return;
        }

        recognizer = SpeechRecognizer
                .createSpeechRecognizer(getContext());

        recognizer.setRecognitionListener(
                new RecognitionListener() {

                    @Override
                    public void onReadyForSpeech(
                            Bundle params) {
                        setStatus("Listening...",
                                0xFFFC4D4D);
                    }

                    @Override
                    public void onBeginningOfSpeech() {
                        setStatus("I hear you...",
                                0xFFFC4D4D);
                        scheduleSpeechFinalization(1800);
                    }

                    @Override
                    public void onRmsChanged(float rmsdB) {
                        if (isListening && rmsdB > 2f) {
                            scheduleSpeechFinalization(1800);
                        }
                    }

                    @Override
                    public void onBufferReceived(
                            byte[] buffer) {}

                    @Override
                    public void onEndOfSpeech() {
                        setStatus("Finishing your command...",
                                0xFF6B7280);
                        scheduleSpeechFinalization(700);
                    }

                    @Override
                    public void onError(int error) {
                        if (!isShowing()) return;
                        handler.removeCallbacks(finalizeSpeechRunnable);
                        speechFinalizationRequested = false;
                        stopListeningUI();
                        isListening  = false;
                        isProcessing = false;

                        boolean canRetry = error ==
                                SpeechRecognizer.ERROR_NO_MATCH
                                || error ==
                                SpeechRecognizer.ERROR_SPEECH_TIMEOUT;
                        if (canRetry) {
                            setStatus(
                                    handsFreeMode
                                            ? "Listening will resume shortly."
                                            : "Tap mic to try again.",
                                    0xFF6B7280);
                        } else if (error ==
                                SpeechRecognizer
                                        .ERROR_AUDIO) {
                            setStatus(
                                    handsFreeMode
                                            ? "Audio error. Check microphone access."
                                            : "Audio error. Try again.",
                                    0xFFFC4D4D);
                        } else {
                            setStatus(
                                    handsFreeMode
                                            ? "Voice recognition stopped. Tap mic to retry."
                                            : "Tap mic to speak.",
                                    0xFF6B7280);
                        }

                        if (handsFreeMode && canRetry && !manuallyStopped) {
                            scheduleListeningRestart(700);
                        }
                    }

                    @Override
                    public void onResults(Bundle results) {
                        if (!isShowing()) return;
                        handler.removeCallbacks(finalizeSpeechRunnable);
                        speechFinalizationRequested = false;
                        ArrayList<String> matches =
                                results.getStringArrayList(
                                        SpeechRecognizer
                                                .RESULTS_RECOGNITION);
                        if (matches != null
                                && !matches.isEmpty()) {
                            manuallyStopped = false;
                            String text = matches.get(0);
                            showUserTranscript(text);
                            sendToAssistant(text);
                        } else {
                            isProcessing = false;
                            setStatus(
                                    "Could not hear you. "
                                            + "Try again.",
                                    0xFF6B7280);
                            if (handsFreeMode && !manuallyStopped) {
                                scheduleListeningRestart(700);
                            }
                        }
                    }

                    @Override
                    public void onPartialResults(
                            Bundle partialResults) {
                        if (!isShowing()) return;
                        ArrayList<String> partial =
                                partialResults
                                        .getStringArrayList(
                                                SpeechRecognizer
                                                        .RESULTS_RECOGNITION);
                        if (partial != null
                                && !partial.isEmpty()) {
                            showUserTranscript(
                                    partial.get(0) + "...");
                            scheduleSpeechFinalization(1800);
                        }
                    }

                    @Override
                    public void onEvent(int eventType,
                                        Bundle params) {}
                });
    }

    private void scheduleListeningRestart(long delayMillis) {
        handler.postDelayed(() -> {
            if (handsFreeMode && !manuallyStopped && !isProcessing
                    && isShowing()) {
                startListening();
            }
        }, delayMillis);
    }

    private void scheduleSpeechFinalization(long delayMillis) {
        handler.removeCallbacks(finalizeSpeechRunnable);
        handler.postDelayed(finalizeSpeechRunnable, delayMillis);
    }

    private void finalizeSpeechAfterSilence() {
        if (!isListening || isProcessing || speechFinalizationRequested
                || recognizer == null || !isShowing()) {
            return;
        }

        speechFinalizationRequested = true;
        setStatus("Sending your command...",
                0xFF6B7280);
        try {
            recognizer.stopListening();
        } catch (IllegalStateException e) {
            speechFinalizationRequested = false;
            setStatus("Could not finish listening. Tap mic to try again.",
                    0xFFFC4D4D);
        }
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
        manuallyStopped = false;
        speechFinalizationRequested = false;
        handler.removeCallbacks(finalizeSpeechRunnable);
        assistant.stopSpeaking();

        // Show rings
        ringOuter.setVisibility(View.VISIBLE);
        ringInner.setVisibility(View.VISIBLE);
        startPulseAnimation();
        playListeningSound();

        tvMicIcon.setText("⏹");
        setStatus(handsFreeMode
                        ? "Listening hands-free... tap mic to pause."
                        : "Listening... tap to stop.",
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
        intent.putExtra(
                RecognizerIntent
                        .EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1500L);
        intent.putExtra(
                RecognizerIntent
                        .EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1000L);

        try {
            recognizer.startListening(intent);
        } catch (Exception e) {
            stopListeningUI();
            isListening = false;
            setStatus("Could not start mic.",
                    0xFFFC4D4D);
        }
    }

    private void stopListening() {
        handler.removeCallbacks(finalizeSpeechRunnable);
        if (recognizer != null) {
            recognizer.stopListening();
        }
        stopListeningUI();
    }

    private void stopListeningUI() {
        handler.post(() -> {
            isListening = false;
            stopListeningSound();
            ringOuter.setVisibility(View.GONE);
            ringInner.setVisibility(View.GONE);
            stopPulseAnimation();
            tvMicIcon.setText("🎙");
        });
    }

    // ════════════════════════════════════════════════════════════
    //  Send to AI
    // ════════════════════════════════════════════════════════════
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
                    public void onResponse(
                            String text,
                            String action,
                            String data) {
                        handler.post(() -> {
                            if (!isShowing()) return;
                            showThinking(false);
                            isProcessing = false;
                            btnMic.setEnabled(true);

                            // Show response text
                            tvResponse.setText(text);
                            setStatus(
                                    handsFreeMode
                                            ? "I am listening for your next command."
                                            : "Tap mic to speak",
                                    0xFF6B7280);

                            // Speak response then
                            // execute action
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
                                            handler.post(() -> {
                                                executeAction(action, data);
                                                if (handsFreeMode
                                                        && (action == null
                                                        || ClaudeVoiceAssistant
                                                        .ACTION_NONE.equals(action))) {
                                                    scheduleListeningRestart(500);
                                                }
                                            });
                                        }
                                    });
                        });
                    }

                    @Override
                    public void onError(String error) {
                        handler.post(() -> {
                            if (!isShowing()) return;
                            showThinking(false);
                            isProcessing = false;
                            btnMic.setEnabled(true);
                            tvResponse.setText(error);
                            setStatus(
                                    handsFreeMode
                                            ? "I am listening. Please try again."
                                            : "Tap mic to try again.",
                                    0xFF6B7280);
                            assistant.speak(error,
                                    new ClaudeVoiceAssistant.AssistantCallback() {
                                        @Override
                                        public void onResponse(
                                                String t, String a, String d) {}
                                        @Override
                                        public void onError(String e) {}
                                        @Override
                                        public void onSpeakingStarted() {}
                                        @Override
                                        public void onSpeakingFinished() {
                                            if (handsFreeMode) {
                                                scheduleListeningRestart(500);
                                            }
                                        }
                                    });
                        });
                    }

                    @Override
                    public void onSpeakingStarted() {}

                    @Override
                    public void onSpeakingFinished() {}
                });
    }

    // ════════════════════════════════════════════════════════════
    //  Execute action
    // ════════════════════════════════════════════════════════════
    private void executeAction(String action,
                               String data) {
        if (action == null
                || ClaudeVoiceAssistant
                .ACTION_NONE.equals(action)) {
            return;
        }

        switch (action) {

            case ClaudeVoiceAssistant.ACTION_SEND_SOS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onSendSOS();
                    }
                    dismiss();
                }, 600);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_OPEN_FIRSTAID:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onOpenFirstAid();
                    }
                    dismiss();
                }, 600);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_SEARCH_FIRSTAID:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onSearchFirstAid(
                                data != null ? data : "");
                    }
                    dismiss();
                }, 600);
                break;

            case ClaudeVoiceAssistant
                         .ACTION_CALL_STATION:
                handler.postDelayed(
                        this::callBFP, 600);
                break;

            default:
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
                    "Could not open the phone app.");
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Pulse animation
    // ════════════════════════════════════════════════════════════
    private void startPulseAnimation() {
        isListening = true;
        if (ringOuter != null) {
            ringOuter.setVisibility(View.VISIBLE);
        }
        if (ringInner != null) {
            ringInner.setVisibility(View.VISIBLE);
        }
        runPulse();
    }

    private void runPulse() {
        if (!isListening) return;

        if (pulseAnimation != null) {
            pulseAnimation.cancel();
            pulseAnimation = null;
        }

        // Outer ring expansion & fade
        ObjectAnimator sx1 = ObjectAnimator.ofFloat(ringOuter, "scaleX", 1.0f, 1.45f);
        ObjectAnimator sy1 = ObjectAnimator.ofFloat(ringOuter, "scaleY", 1.0f, 1.45f);
        ObjectAnimator a1  = ObjectAnimator.ofFloat(ringOuter, "alpha", 0.45f, 0.05f);

        // Inner ring expansion & fade
        ObjectAnimator sx2 = ObjectAnimator.ofFloat(ringInner, "scaleX", 1.0f, 1.25f);
        ObjectAnimator sy2 = ObjectAnimator.ofFloat(ringInner, "scaleY", 1.0f, 1.25f);
        ObjectAnimator a2  = ObjectAnimator.ofFloat(ringInner, "alpha", 0.60f, 0.15f);

        // Subtle organic breathing on mic button
        ObjectAnimator sxMic = ObjectAnimator.ofFloat(btnMic, "scaleX", 1.0f, 1.06f);
        ObjectAnimator syMic = ObjectAnimator.ofFloat(btnMic, "scaleY", 1.0f, 1.06f);

        ObjectAnimator[] anims = new ObjectAnimator[]{sx1, sy1, a1, sx2, sy2, a2, sxMic, syMic};
        for (ObjectAnimator anim : anims) {
            anim.setDuration(900);
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.setRepeatMode(ValueAnimator.REVERSE);
            anim.setInterpolator(new AccelerateDecelerateInterpolator());
        }

        pulseAnimation = new AnimatorSet();
        pulseAnimation.playTogether(sx1, sy1, a1, sx2, sy2, a2, sxMic, syMic);
        pulseAnimation.start();
    }

    private void stopPulseAnimation() {
        isListening = false;
        if (pulseAnimation != null) {
            pulseAnimation.cancel();
            pulseAnimation = null;
        }
        if (ringOuter != null) {
            ringOuter.setScaleX(1f);
            ringOuter.setScaleY(1f);
            ringOuter.setAlpha(0.3f);
            ringOuter.setVisibility(View.GONE);
        }
        if (ringInner != null) {
            ringInner.setScaleX(1f);
            ringInner.setScaleY(1f);
            ringInner.setAlpha(0.45f);
            ringInner.setVisibility(View.GONE);
        }
        if (btnMic != null) {
            btnMic.setScaleX(1f);
            btnMic.setScaleY(1f);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  UI helpers
    // ════════════════════════════════════════════════════════════
    private void showUserTranscript(String text) {
        handler.post(() -> {
            if (!isShowing()) return;
            tvUserTranscript.setText(
                    "You: \"" + text + "\"");
            tvUserTranscript.setVisibility(
                    View.VISIBLE);
        });
    }

    private void showThinking(boolean show) {
        handler.post(() -> {
            if (isShowing()) {
                thinkingIndicator.setVisibility(
                        show ? View.VISIBLE : View.GONE);
            }
        });
    }

    private void setStatus(String status, int color) {
        handler.post(() -> {
            if (!isShowing()) return;
            tvMicStatus.setText(status);
            tvMicStatus.setTextColor(color);
        });
    }

    // ════════════════════════════════════════════════════════════
    //  Cleanup
    // ════════════════════════════════════════════════════════════
    private void playListeningSound() {
        try {
            stopListeningSound();
            listeningPlayer = MediaPlayer.create(getContext(), R.raw.ai_listening_sound);
            if (listeningPlayer != null) {
                listeningPlayer.setOnCompletionListener(mp -> {
                    try {
                        mp.release();
                    } catch (Exception ignored) {}
                    if (listeningPlayer == mp) {
                        listeningPlayer = null;
                    }
                });
                listeningPlayer.start();
            }
        } catch (Exception e) {
            android.util.Log.e("VoiceAssistantDialog", "Error playing listening sound", e);
        }
    }

    private void stopListeningSound() {
        if (listeningPlayer != null) {
            try {
                if (listeningPlayer.isPlaying()) {
                    listeningPlayer.stop();
                }
                listeningPlayer.release();
            } catch (Exception ignored) {}
            listeningPlayer = null;
        }
    }

    @Override
    public void dismiss() {
        stopListeningSound();
        handler.removeCallbacks(finalizeSpeechRunnable);
        stopPulseAnimation();
        if (assistant != null) {
            assistant.stopSpeaking();
            assistant.release();
            assistant = null;
        }
        if (recognizer != null) {
            recognizer.destroy();
            recognizer = null;
        }
        super.dismiss();
    }
}