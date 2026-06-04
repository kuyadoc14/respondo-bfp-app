package com.bfp.alert;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class ClaudeVoiceAssistant {

    // ── Replace with your Anthropic API key ───────────────────────
    private static final String API_KEY =
            "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";
    private static final String API_URL =
            "https://api.anthropic.com/v1/messages";
    private static final String MODEL =
            "claude-sonnet-4-20250514";

    // ── Callback interface ────────────────────────────────────────
    public interface AssistantCallback {
        void onResponse(String spokenText,
                        String action,
                        String actionData);
        void onError(String error);
        void onSpeakingStarted();
        void onSpeakingFinished();
    }

    // ── Actions Claude can return ─────────────────────────────────
    public static final String ACTION_SEND_SOS    = "SEND_SOS";
    public static final String ACTION_OPEN_FIRSTAID = "OPEN_FIRSTAID";
    public static final String ACTION_SEARCH_FIRSTAID = "SEARCH_FIRSTAID";
    public static final String ACTION_CALL_STATION = "CALL_STATION";
    public static final String ACTION_NONE        = "NONE";

    private final Context    ctx;
    private TextToSpeech     tts;
    private boolean          ttsReady    = false;
    private String           lastSpoken  = "";
    private final OkHttpClient httpClient;
    private final Handler    mainHandler;

    // Conversation history for context
    private final List<JSONObject> conversationHistory =
            new ArrayList<>();

    public ClaudeVoiceAssistant(Context ctx) {
        this.ctx        = ctx;
        this.httpClient = new OkHttpClient.Builder()
                .connectTimeout(30,
                        java.util.concurrent.TimeUnit.SECONDS)
                .readTimeout(30,
                        java.util.concurrent.TimeUnit.SECONDS)
                .build();
        this.mainHandler =
                new Handler(Looper.getMainLooper());
        initTTS();
    }

    // ── TTS init ──────────────────────────────────────────────────
    private void initTTS() {
        tts = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                tts.setLanguage(Locale.ENGLISH);
                tts.setSpeechRate(0.92f);
                tts.setPitch(1.0f);
                ttsReady = true;
            }
        });
    }

    // ── Speak text ────────────────────────────────────────────────
    public void speak(String text,
                      AssistantCallback callback) {
        if (!ttsReady) return;
        lastSpoken = text;
        tts.stop();

        if (callback != null) {
            tts.setOnUtteranceProgressListener(
                    new UtteranceProgressListener() {
                        @Override
                        public void onStart(String id) {
                            mainHandler.post(
                                    callback::onSpeakingStarted);
                        }
                        @Override
                        public void onDone(String id) {
                            mainHandler.post(
                                    callback::onSpeakingFinished);
                        }
                        @Override
                        public void onError(String id) {
                            mainHandler.post(
                                    callback::onSpeakingFinished);
                        }
                    });
        }

        tts.speak(text,
                TextToSpeech.QUEUE_FLUSH,
                null, "utterance_" + System.currentTimeMillis());
    }

    public void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    public void repeatLast(AssistantCallback cb) {
        if (!lastSpoken.isEmpty()) speak(lastSpoken, cb);
    }

    // ── Send to Claude API ────────────────────────────────────────
    public void processUserInput(
            String userText,
            AssistantCallback callback) {

        new Thread(() -> {
            try {
                // Add user message to history
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userText);
                conversationHistory.add(userMsg);

                // Build messages array
                JSONArray messages = new JSONArray();
                for (JSONObject msg : conversationHistory) {
                    messages.put(msg);
                }

                // Build request body
                JSONObject body = new JSONObject();
                body.put("model", MODEL);
                body.put("max_tokens", 300);
                body.put("system", getSystemPrompt());
                body.put("messages", messages);

                RequestBody requestBody =
                        RequestBody.create(
                                body.toString(),
                                MediaType.parse(
                                        "application/json"));

                Request request = new Request.Builder()
                        .url(API_URL)
                        .addHeader("x-api-key", API_KEY)
                        .addHeader("anthropic-version",
                                "2023-06-01")
                        .addHeader("content-type",
                                "application/json")
                        .post(requestBody)
                        .build();

                httpClient.newCall(request)
                        .enqueue(new Callback() {
                            @Override
                            public void onFailure(
                                    Call call,
                                    java.io.IOException e) {
                                mainHandler.post(() ->
                                        callback.onError(
                                                "Network error. "
                                                        + "Please check "
                                                        + "your connection."));
                            }

                            @Override
                            public void onResponse(
                                    Call call,
                                    Response response)
                                    throws java.io.IOException {
                                try {
                                    String responseBody =
                                            response.body()
                                                    .string();
                                    parseClaudeResponse(
                                            responseBody,
                                            callback);
                                } catch (Exception e) {
                                    mainHandler.post(() ->
                                            callback.onError(
                                                    "Failed to "
                                                            + "process "
                                                            + "response."));
                                }
                            }
                        });

            } catch (Exception e) {
                mainHandler.post(() ->
                        callback.onError(
                                "Error: " + e.getMessage()));
            }
        }).start();
    }

    // ── Parse Claude response ─────────────────────────────────────
    private void parseClaudeResponse(
            String responseBody,
            AssistantCallback callback) {
        try {
            JSONObject json =
                    new JSONObject(responseBody);
            JSONArray content =
                    json.getJSONArray("content");

            String fullText = "";
            for (int i = 0;
                 i < content.length(); i++) {
                JSONObject block =
                        content.getJSONObject(i);
                if ("text".equals(
                        block.getString("type"))) {
                    fullText = block.getString("text");
                    break;
                }
            }

            // Add assistant response to history
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", fullText);
            conversationHistory.add(assistantMsg);

            // Parse action from response
            String action     = ACTION_NONE;
            String actionData = "";
            String spokenText = fullText;

            // Claude wraps action in [ACTION:xxx]
            if (fullText.contains("[ACTION:")) {
                int start =
                        fullText.indexOf("[ACTION:") + 8;
                int end   =
                        fullText.indexOf("]", start);
                if (end > start) {
                    String actionStr =
                            fullText.substring(start, end)
                                    .trim();

                    // Parse action and optional data
                    if (actionStr.contains("|")) {
                        String[] parts =
                                actionStr.split("\\|", 2);
                        action     = parts[0].trim();
                        actionData = parts[1].trim();
                    } else {
                        action = actionStr;
                    }

                    // Remove action tag from spoken text
                    spokenText = fullText
                            .replace("[ACTION:" + actionStr
                                    + "]", "")
                            .trim();
                }
            }

            final String finalSpoken   = spokenText;
            final String finalAction   = action;
            final String finalData     = actionData;

            mainHandler.post(() ->
                    callback.onResponse(
                            finalSpoken,
                            finalAction,
                            finalData));

        } catch (Exception e) {
            mainHandler.post(() ->
                    callback.onError(
                            "Could not understand response."));
        }
    }

    // ── System prompt ─────────────────────────────────────────────
    private String getSystemPrompt() {
        return "You are the BFP (Bureau of Fire Protection) "
                + "Voice Assistant, designed to help people — "
                + "especially those with disabilities — use the "
                + "BFP emergency app. You are calm, clear, and "
                + "compassionate.\n\n"

                + "You can perform these actions by including "
                + "an action tag in your response:\n"
                + "- [ACTION:SEND_SOS] — Send emergency SOS alert\n"
                + "- [ACTION:OPEN_FIRSTAID] — Open first aid guides\n"
                + "- [ACTION:SEARCH_FIRSTAID|query] — Search first aid\n"
                + "- [ACTION:CALL_STATION] — Call BFP fire station\n"
                + "- [ACTION:NONE] — No action needed\n\n"

                + "Rules:\n"
                + "1. Always respond in SHORT, CLEAR sentences "
                + "suitable for text-to-speech. No markdown, "
                + "no bullet points, no special characters "
                + "except periods and commas.\n"
                + "2. Include ONE action tag per response if "
                + "an action is needed, placed at the END.\n"
                + "3. If the user seems to be in an emergency, "
                + "prioritize sending SOS.\n"
                + "4. For first aid questions, search the guides "
                + "rather than giving medical advice directly.\n"
                + "5. Be brief. Maximum 3 sentences before "
                + "the action tag.\n"
                + "6. Always confirm what action you are taking.\n\n"

                + "Examples:\n"
                + "User: 'There is a fire'\n"
                + "Response: 'I am sending an emergency SOS "
                + "alert to BFP right now. Help is on the way. "
                + "Please stay calm and move to safety. "
                + "[ACTION:SEND_SOS]'\n\n"

                + "User: 'How do I do CPR'\n"
                + "Response: 'Let me find the CPR first aid "
                + "guide for you right away. "
                + "[ACTION:SEARCH_FIRSTAID|CPR]'\n\n"

                + "User: 'I need help'\n"
                + "Response: 'I am here to help. I can send "
                + "an SOS alert, open first aid guides, or "
                + "call the fire station. What do you need? "
                + "[ACTION:NONE]'\n\n"

                + "User: 'Someone is choking'\n"
                + "Response: 'Opening the choking first aid "
                + "guide immediately. Follow the steps carefully. "
                + "[ACTION:SEARCH_FIRSTAID|choking]'";
    }

    // ── Clear conversation history ────────────────────────────────
    public void clearHistory() {
        conversationHistory.clear();
    }

    // ── Release resources ─────────────────────────────────────────
    public void release() {
        stopSpeaking();
        if (tts != null) {
            tts.shutdown();
            tts = null;
        }
        ttsReady = false;
    }
}