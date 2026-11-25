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
        default void onSendSOSWithRole(String role) {
            onSendSOS();
        }
        default void onSendSOSWithDetails(String incidentNotes, String role) {
            onSendSOSWithRole(role);
        }
        void onOpenFirstAid();
        void onSearchFirstAid(String query);
        default void onOpenFirstAidDetail(FirstAidItem item) {
            onOpenFirstAidDetail(item, false);
        }
        default void onOpenFirstAidDetail(FirstAidItem item, boolean autoPlayVideo) {
            if (item != null && item.title != null) {
                onSearchFirstAid(item.title);
            }
        }
        default void onPlayVideo() {}
        default void onPauseVideo() {}
        default void onOpenProfile() {}
        default void onOpenSOS() {}
        default void onCheckStatus() {}
        default void onCancelSOS() {}
        default void onUpdateCondition(String conditionNotes) {}
        default String getAppContextSummary() { return ""; }
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
    private String     lastUserTranscript = "";
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
            if (handsFreeMode) {
                window.clearFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
                window.setFlags(
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL,
                        WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL);
            }
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
        lastUserTranscript = userText;

        stopListeningUI();
        showUserTranscript(userText);
        showThinking(true);
        setStatus("Thinking...", 0xFF6B7280);
        btnMic.setEnabled(false);

        String contextSummary = actionListener != null ? actionListener.getAppContextSummary() : "";
        assistant.processUserInput(userText, contextSummary,
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

                            // Fallback: if action is NONE or null, check if user speech matches a first aid guide
                            String effectiveAction = action;
                            String effectiveData = data;
                            if (effectiveAction == null || ClaudeVoiceAssistant.ACTION_NONE.equals(effectiveAction)) {
                                FirstAidItem guide = FirstAidRepository.getInstance().findMatchingItem(lastUserTranscript);
                                if (guide != null) {
                                    effectiveAction = ClaudeVoiceAssistant.ACTION_SEARCH_FIRSTAID;
                                    effectiveData = guide.title + (lastUserTranscript != null && (lastUserTranscript.toLowerCase().contains("video") || lastUserTranscript.toLowerCase().contains("play") || lastUserTranscript.toLowerCase().contains("watch")) ? "|play" : "");
                                }
                            }

                            // Execute action IMMEDIATELY so screen updates right away!
                            executeAction(effectiveAction, effectiveData);

                            speakAndRestart(text);
                        });
                    }

                    @Override
                    public void onMultiToolResponse(
                            String text,
                            java.util.List<ClaudeVoiceAssistant.ToolCall> toolCalls) {
                        handler.post(() -> {
                            if (!isShowing()) return;
                            showThinking(false);
                            isProcessing = false;
                            btnMic.setEnabled(true);

                            tvResponse.setText(text);
                            setStatus(
                                    handsFreeMode
                                            ? "I am listening for your next command."
                                            : "Tap mic to speak",
                                    0xFF6B7280);

                            if (toolCalls != null && !toolCalls.isEmpty()) {
                                executeToolCalls(toolCalls);
                            } else {
                                // Fallback: check if user speech matches a first aid guide
                                FirstAidItem guide = FirstAidRepository.getInstance().findMatchingItem(lastUserTranscript);
                                if (guide != null) {
                                    boolean play = lastUserTranscript != null && (lastUserTranscript.toLowerCase().contains("video") || lastUserTranscript.toLowerCase().contains("play") || lastUserTranscript.toLowerCase().contains("watch"));
                                    executeAction(ClaudeVoiceAssistant.ACTION_SEARCH_FIRSTAID, guide.title + (play ? "|play" : ""));
                                }
                            }

                            speakAndRestart(text);
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

    private void speakAndRestart(String text) {
        assistant.speak(text,
                new ClaudeVoiceAssistant.AssistantCallback() {
                    @Override
                    public void onResponse(String t, String a, String d) {}
                    @Override
                    public void onError(String e) {}
                    @Override
                    public void onSpeakingStarted() {}
                    @Override
                    public void onSpeakingFinished() {
                        handler.post(() -> {
                            if (handsFreeMode) {
                                scheduleListeningRestart(500);
                            }
                        });
                    }
                });
    }

    // ════════════════════════════════════════════════════════════
    //  Execute Multi-Tool Calls
    // ════════════════════════════════════════════════════════════
    private void executeToolCalls(java.util.List<ClaudeVoiceAssistant.ToolCall> toolCalls) {
        boolean shouldDismiss = false;

        for (ClaudeVoiceAssistant.ToolCall tc : toolCalls) {
            if (tc == null || tc.name == null) continue;
            org.json.JSONObject args = tc.args != null ? tc.args : new org.json.JSONObject();

            switch (tc.name) {
                case "send_sos": {
                    String role = args.optString("role", "victim");
                    String notes = args.optString("incident_notes", "");
                    if (notes.isEmpty()) notes = lastUserTranscript;
                    final String finalRole = role;
                    final String finalNotes = notes;
                    handler.postDelayed(() -> {
                        if (actionListener != null) {
                            actionListener.onSendSOSWithDetails(finalNotes, finalRole);
                        }
                    }, 300);
                    shouldDismiss = true;
                    break;
                }
                case "cancel_sos":
                    handler.postDelayed(() -> {
                        if (actionListener != null) actionListener.onCancelSOS();
                    }, 200);
                    break;
                case "check_sos_status":
                    handler.postDelayed(() -> {
                        if (actionListener != null) actionListener.onCheckStatus();
                    }, 200);
                    break;
                case "update_condition": {
                    String notes = args.optString("condition_notes", lastUserTranscript);
                    if (notes != null && !notes.isEmpty()) {
                        handler.postDelayed(() -> {
                            if (actionListener != null) actionListener.onUpdateCondition(notes);
                        }, 200);
                    }
                    break;
                }
                case "navigate_to": {
                    String screen = args.optString("screen", "").toLowerCase();
                    handler.postDelayed(() -> {
                        if (actionListener == null) return;
                        if ("first_aid".equals(screen)) {
                            actionListener.onOpenFirstAid();
                        } else if ("profile".equals(screen)) {
                            actionListener.onOpenProfile();
                        } else if ("sos".equals(screen)) {
                            actionListener.onOpenSOS();
                        }
                    }, 150);
                    shouldDismiss = true;
                    break;
                }
                case "open_first_aid": {
                    String query = args.optString("query", "");
                    boolean autoPlay = args.optBoolean("auto_play_video", false);
                    if (!autoPlay && lastUserTranscript != null) {
                        String lu = lastUserTranscript.toLowerCase();
                        autoPlay = lu.contains("video") || lu.contains("play") || lu.contains("watch");
                    }
                    final String finalQuery = query;
                    final boolean finalPlay = autoPlay;
                    handler.postDelayed(() -> {
                        if (actionListener == null) return;
                        FirstAidItem match = FirstAidRepository.getInstance().findMatchingItem(finalQuery);
                        if (match == null && lastUserTranscript != null && !lastUserTranscript.isEmpty()) {
                            match = FirstAidRepository.getInstance().findMatchingItem(lastUserTranscript);
                        }
                        if (match != null) {
                            actionListener.onOpenFirstAidDetail(match, finalPlay);
                        } else {
                            actionListener.onSearchFirstAid(finalQuery);
                        }
                    }, 150);
                    shouldDismiss = true;
                    break;
                }
                case "control_video": {
                    String cmd = args.optString("command", "play").toLowerCase();
                    handler.postDelayed(() -> {
                        if (actionListener == null) return;
                        if ("pause".equals(cmd)) {
                            actionListener.onPauseVideo();
                        } else {
                            actionListener.onPlayVideo();
                        }
                    }, 100);
                    break;
                }
                case "control_flashlight":
                    setFlashlight(args.optBoolean("enabled", true));
                    break;
                case "control_siren":
                    if (args.optBoolean("enabled", true)) {
                        playSiren();
                    } else {
                        stopSiren();
                    }
                    break;
                case "call_bfp_station":
                    handler.postDelayed(this::callBFP, 500);
                    break;
                default:
                    break;
            }
        }

        if (shouldDismiss && !handsFreeMode) {
            handler.postDelayed(this::dismiss, 700);
        } else if (handsFreeMode) {
            scheduleListeningRestart(1000);
        }
    }

    // ════════════════════════════════════════════════════════════
    //  Execute action (legacy fallback)
    // ════════════════════════════════════════════════════════════
    private void executeAction(String action,
                               String data) {
        if (action == null
                || ClaudeVoiceAssistant
                .ACTION_NONE.equals(action)) {
            return;
        }

        switch (action) {

            case ClaudeVoiceAssistant.ACTION_SEND_SOS_INCIDENT:
            case ClaudeVoiceAssistant.ACTION_SEND_SOS_VICTIM:
            case ClaudeVoiceAssistant.ACTION_SEND_SOS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        String notes = (data != null && !data.isEmpty() && !"victim".equalsIgnoreCase(data))
                                ? data : lastUserTranscript;
                        actionListener.onSendSOSWithDetails(notes, "victim");
                    }
                    if (!handsFreeMode) {
                        dismiss();
                    } else {
                        scheduleListeningRestart(1200);
                    }
                }, 600);
                break;

            case ClaudeVoiceAssistant.ACTION_OPEN_FIRSTAID:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        FirstAidItem guide = FirstAidRepository.getInstance().findMatchingItem(lastUserTranscript);
                        if (guide != null) {
                            boolean play = lastUserTranscript != null && (lastUserTranscript.toLowerCase().contains("video") || lastUserTranscript.toLowerCase().contains("play") || lastUserTranscript.toLowerCase().contains("watch"));
                            actionListener.onOpenFirstAidDetail(guide, play);
                        } else {
                            actionListener.onOpenFirstAid();
                        }
                    }
                    if (!handsFreeMode) {
                        dismiss();
                    } else {
                        scheduleListeningRestart(800);
                    }
                }, 50);
                break;

            case ClaudeVoiceAssistant.ACTION_SEARCH_FIRSTAID:
                boolean autoPlay = false;
                String guideQuery = data;
                if (guideQuery != null && guideQuery.contains("|play")) {
                    autoPlay = true;
                    guideQuery = guideQuery.replace("|play", "").trim();
                } else if (lastUserTranscript != null && (lastUserTranscript.toLowerCase().contains("video") || lastUserTranscript.toLowerCase().contains("play") || lastUserTranscript.toLowerCase().contains("watch"))) {
                    autoPlay = true;
                }

                final boolean finalAutoPlay = autoPlay;
                final String finalQuery = guideQuery;
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        FirstAidItem match = FirstAidRepository.getInstance().findMatchingItem(finalQuery);
                        if (match == null && lastUserTranscript != null && !lastUserTranscript.isEmpty()) {
                            match = FirstAidRepository.getInstance().findMatchingItem(lastUserTranscript);
                        }
                        if (match != null) {
                            actionListener.onOpenFirstAidDetail(match, finalAutoPlay);
                        } else {
                            actionListener.onSearchFirstAid(
                                    finalQuery != null ? finalQuery : "");
                        }
                    }
                    if (!handsFreeMode) {
                        dismiss();
                    }
                }, 50);
                break;

            case ClaudeVoiceAssistant.ACTION_PLAY_VIDEO:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onPlayVideo();
                    }
                }, 100);
                break;

            case ClaudeVoiceAssistant.ACTION_PAUSE_VIDEO:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onPauseVideo();
                    }
                }, 100);
                break;

            case ClaudeVoiceAssistant.ACTION_OPEN_PROFILE:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onOpenProfile();
                    }
                    if (!handsFreeMode) {
                        dismiss();
                    } else {
                        scheduleListeningRestart(800);
                    }
                }, 400);
                break;

            case ClaudeVoiceAssistant.ACTION_OPEN_SOS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onOpenSOS();
                    }
                    if (!handsFreeMode) {
                        dismiss();
                    } else {
                        scheduleListeningRestart(800);
                    }
                }, 400);
                break;

            case ClaudeVoiceAssistant.ACTION_CHECK_STATUS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onCheckStatus();
                    }
                    if (handsFreeMode) {
                        scheduleListeningRestart(800);
                    }
                }, 400);
                break;

            case ClaudeVoiceAssistant.ACTION_CANCEL_SOS:
                handler.postDelayed(() -> {
                    if (actionListener != null) {
                        actionListener.onCancelSOS();
                    }
                    if (handsFreeMode) {
                        scheduleListeningRestart(800);
                    }
                }, 400);
                break;

            case ClaudeVoiceAssistant.ACTION_FLASHLIGHT_ON:
                setFlashlight(true);
                if (handsFreeMode) scheduleListeningRestart(800);
                break;

            case ClaudeVoiceAssistant.ACTION_FLASHLIGHT_OFF:
                setFlashlight(false);
                if (handsFreeMode) scheduleListeningRestart(800);
                break;

            case ClaudeVoiceAssistant.ACTION_PLAY_SIREN:
                playSiren();
                if (handsFreeMode) scheduleListeningRestart(800);
                break;

            case ClaudeVoiceAssistant.ACTION_STOP_SIREN:
                stopSiren();
                if (handsFreeMode) scheduleListeningRestart(800);
                break;

            case ClaudeVoiceAssistant.ACTION_UPDATE_CONDITION:
                handler.postDelayed(() -> {
                    if (actionListener != null && data != null && !data.isEmpty()) {
                        actionListener.onUpdateCondition(data);
                    }
                    if (handsFreeMode) {
                        scheduleListeningRestart(800);
                    }
                }, 400);
                break;

            case ClaudeVoiceAssistant.ACTION_CALL_STATION:
                handler.postDelayed(this::callBFP, 600);
                if (handsFreeMode) scheduleListeningRestart(1500);
                break;

            default:
                if (handsFreeMode) scheduleListeningRestart(800);
                break;
        }
    }

    private android.media.Ringtone sirenRingtone;

    private void setFlashlight(boolean enable) {
        try {
            android.hardware.camera2.CameraManager cm =
                    (android.hardware.camera2.CameraManager) getContext().getSystemService(Context.CAMERA_SERVICE);
            if (cm != null) {
                String[] ids = cm.getCameraIdList();
                if (ids.length > 0) {
                    cm.setTorchMode(ids[0], enable);
                }
            }
        } catch (Exception e) {
            android.util.Log.e("VoiceAssistantDialog", "Flashlight error", e);
        }
    }

    private void playSiren() {
        try {
            if (sirenRingtone == null) {
                Uri alert = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_ALARM);
                if (alert == null) {
                    alert = android.media.RingtoneManager.getDefaultUri(android.media.RingtoneManager.TYPE_RINGTONE);
                }
                sirenRingtone = android.media.RingtoneManager.getRingtone(getContext(), alert);
            }
            if (sirenRingtone != null && !sirenRingtone.isPlaying()) {
                sirenRingtone.play();
            }
        } catch (Exception e) {
            android.util.Log.e("VoiceAssistantDialog", "Siren error", e);
        }
    }

    private void stopSiren() {
        if (sirenRingtone != null && sirenRingtone.isPlaying()) {
            sirenRingtone.stop();
        }
    }



    private void callBFP() {
        try {
            Intent intent = new Intent(
                    Intent.ACTION_DIAL,
                    Uri.parse("tel:09399337795"));
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
        stopSiren();
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