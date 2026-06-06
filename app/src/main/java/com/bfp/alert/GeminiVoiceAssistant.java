package com.bfp.alert;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

public class GeminiVoiceAssistant {

    private static final String TAG = "GeminiVoice";

    // ── Paste your API key from https://aistudio.google.com/apikey ──
    private static final String API_KEY =
            "AQ.Ab8RN6Lwmdl5HhWX-aPZi6wy_a8xZdWkak3D8G-qg8gu-MM8gw";
    private static final String MODEL =
            "gemini-2.0-flash-lite";
    private static final String API_URL =
            "https://generativelanguage.googleapis.com/v1beta/models/"
                    + MODEL + ":generateContent?key=" + API_KEY;

    // ── Rate-limit / retry config ──────────────────────────────────
    private static final int MAX_RETRIES          = 3;
    private static final long INITIAL_BACKOFF_MS  = 5000;  // 5 seconds
    private static final long MIN_REQUEST_GAP_MS  = 4000;  // 4s between calls
    private static final int  MAX_HISTORY_PAIRS   = 3;     // keep last 3 exchanges

    // ── Actions ───────────────────────────────────────────────────
    public static final String ACTION_SEND_SOS      = "SEND_SOS";
    public static final String ACTION_OPEN_FIRSTAID = "OPEN_FIRSTAID";
    public static final String ACTION_SEARCH_FIRSTAID
            = "SEARCH_FIRSTAID";
    public static final String ACTION_CALL_STATION  = "CALL_STATION";
    public static final String ACTION_NONE          = "NONE";

    // ── Callback ──────────────────────────────────────────────────
    public interface AssistantCallback {
        void onResponse(String spokenText,
                        String action,
                        String actionData);
        void onError(String error);
        void onSpeakingStarted();
        void onSpeakingFinished();
    }

    private final Context      ctx;
    private       TextToSpeech tts;
    private       boolean      ttsReady   = false;
    private       String       lastSpoken = "";
    private final OkHttpClient httpClient;
    private final Handler      mainHandler;

    // Keep conversation history for context
    private final List<JSONObject> history =
            new ArrayList<>();
    private long lastRequestTime = 0;

    public GeminiVoiceAssistant(Context ctx) {
        this.ctx        = ctx;
        this.mainHandler =
                new Handler(Looper.getMainLooper());
        this.httpClient  =
                new OkHttpClient.Builder()
                        .connectTimeout(30,
                                java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(60,
                                java.util.concurrent.TimeUnit.SECONDS)
                        .writeTimeout(30,
                                java.util.concurrent.TimeUnit.SECONDS)
                        .build();
        initTTS();
    }

    // ── TTS ───────────────────────────────────────────────────────
    private void initTTS() {
        tts = new TextToSpeech(ctx, status -> {
            if (status == TextToSpeech.SUCCESS) {
                int result = tts.setLanguage(
                        Locale.ENGLISH);
                if (result == TextToSpeech
                        .LANG_MISSING_DATA
                        || result == TextToSpeech
                        .LANG_NOT_SUPPORTED) {
                    tts.setLanguage(Locale.getDefault());
                }
                tts.setSpeechRate(0.92f);
                tts.setPitch(1.0f);
                ttsReady = true;
            }
        });
    }

    public void speak(String text,
                      AssistantCallback callback) {
        if (!ttsReady || text == null
                || text.isEmpty()) return;
        lastSpoken = text;
        tts.stop();

        tts.setOnUtteranceProgressListener(
                new UtteranceProgressListener() {
                    @Override
                    public void onStart(String id) {
                        if (callback != null)
                            mainHandler.post(
                                    callback::onSpeakingStarted);
                    }
                    @Override
                    public void onDone(String id) {
                        if (callback != null)
                            mainHandler.post(
                                    callback::onSpeakingFinished);
                    }
                    @Override
                    public void onError(String id) {
                        if (callback != null)
                            mainHandler.post(
                                    callback::onSpeakingFinished);
                    }
                });

        tts.speak(text,
                TextToSpeech.QUEUE_FLUSH,
                null,
                "utt_" + System.currentTimeMillis());
    }

    public void stopSpeaking() {
        if (tts != null) tts.stop();
    }

    public void repeatLast(AssistantCallback cb) {
        if (!lastSpoken.isEmpty())
            speak(lastSpoken, cb);
    }

    // ── Send to Gemini ────────────────────────────────────────────
    public void processUserInput(
            String userText,
            AssistantCallback callback) {
        processUserInputWithRetry(userText, callback, 0);
    }

    private void processUserInputWithRetry(
            String userText,
            AssistantCallback callback,
            int retryCount) {

        new Thread(() -> {
            try {
                // ── Throttle: enforce minimum gap ─────
                long now  = System.currentTimeMillis();
                long wait = MIN_REQUEST_GAP_MS
                        - (now - lastRequestTime);
                if (wait > 0) {
                    Log.d(TAG, "Throttling " + wait + "ms");
                    Thread.sleep(wait);
                }
                lastRequestTime =
                        System.currentTimeMillis();

                // ── Trim history to avoid large payloads ──
                while (history.size()
                        > MAX_HISTORY_PAIRS * 2) {
                    history.remove(0);
                }

                // Build contents array (Gemini format)
                JSONArray contents = new JSONArray();

                // Add conversation history
                for (JSONObject msg : history) {
                    contents.put(msg);
                }

                // Add current user message
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                JSONArray userParts = new JSONArray();
                JSONObject userPart = new JSONObject();
                userPart.put("text", userText);
                userParts.put(userPart);
                userMsg.put("parts", userParts);
                contents.put(userMsg);

                // Build system instruction
                JSONObject systemInstruction =
                        new JSONObject();
                JSONArray sysParts = new JSONArray();
                JSONObject sysPart = new JSONObject();
                sysPart.put("text", getSystemPrompt());
                sysParts.put(sysPart);
                systemInstruction.put("parts", sysParts);

                // Build generation config
                JSONObject genConfig = new JSONObject();
                genConfig.put("maxOutputTokens", 400);
                genConfig.put("temperature", 0.7);

                // Build request body
                JSONObject requestBody = new JSONObject();
                requestBody.put("contents", contents);
                requestBody.put("systemInstruction",
                        systemInstruction);
                requestBody.put("generationConfig",
                        genConfig);

                String bodyStr = requestBody.toString();
                Log.d(TAG, "Sending to Gemini"
                        + (retryCount > 0
                        ? " (retry " + retryCount + ")"
                        : "")
                        + ": " + bodyStr);

                RequestBody body = RequestBody.create(
                        bodyStr,
                        MediaType.get(
                                "application/json; charset=utf-8"));

                Request request = new Request.Builder()
                        .url(API_URL)
                        .header("content-type",
                                "application/json")
                        .post(body)
                        .build();

                try (Response response =
                             httpClient.newCall(request)
                                     .execute()) {

                    String responseStr =
                            response.body() != null
                                    ? response.body().string()
                                    : "";

                    Log.d(TAG, "HTTP code: "
                            + response.code());
                    Log.d(TAG, "Response: " + responseStr);

                    if (!response.isSuccessful()) {
                        // Auto-retry on 429 rate limit
                        if (response.code() == 429
                                && retryCount < MAX_RETRIES) {
                            long backoff = INITIAL_BACKOFF_MS
                                    * (long) Math.pow(2, retryCount);
                            Log.w(TAG, "Rate limited (429)."
                                    + " Retrying in " + backoff
                                    + "ms (attempt "
                                    + (retryCount + 1)
                                    + "/" + MAX_RETRIES + ")");

                            // Notify user we're retrying
                            mainHandler.post(() ->
                                    callback.onError(
                                            "Rate limited. Retrying"
                                                    + " in "
                                                    + (backoff / 1000)
                                                    + " seconds..."));

                            Thread.sleep(backoff);
                            processUserInputWithRetry(
                                    userText, callback,
                                    retryCount + 1);
                            return;
                        }

                        handleApiError(
                                response.code(),
                                responseStr,
                                callback);
                        return;
                    }

                    // Add user message to history
                    // only after successful call
                    JSONObject saved = new JSONObject();
                    saved.put("role", "user");
                    JSONArray savedParts = new JSONArray();
                    JSONObject savedPart = new JSONObject();
                    savedPart.put("text", userText);
                    savedParts.put(savedPart);
                    saved.put("parts", savedParts);
                    history.add(saved);

                    parseAndRespond(responseStr, callback);
                }

            } catch (java.io.IOException e) {
                Log.e(TAG, "IO Error: " + e.getMessage());
                mainHandler.post(() ->
                        callback.onError(
                                "Network error. Check your "
                                        + "internet connection."));
            } catch (InterruptedException e) {
                Log.e(TAG, "Retry interrupted: "
                        + e.getMessage());
                Thread.currentThread().interrupt();
                mainHandler.post(() ->
                        callback.onError(
                                "Request was cancelled."));
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                mainHandler.post(() ->
                        callback.onError(
                                "Something went wrong. "
                                        + "Please try again."));
            }
        }).start();
    }

    // ── Parse Gemini response ─────────────────────────────────────
    private void parseAndRespond(
            String responseStr,
            AssistantCallback callback) {

        try {
            JSONObject json =
                    new JSONObject(responseStr);

            // Get text from candidates array
            // Gemini format:
            // { "candidates": [ { "content": {
            //     "parts": [ { "text": "..." } ],
            //     "role": "model" } } ] }
            String fullText = "";

            if (json.has("candidates")) {
                JSONArray candidates =
                        json.getJSONArray("candidates");
                if (candidates.length() > 0) {
                    JSONObject candidate =
                            candidates.getJSONObject(0);
                    JSONObject content =
                            candidate.optJSONObject(
                                    "content");
                    if (content != null
                            && content.has("parts")) {
                        JSONArray parts =
                                content.getJSONArray(
                                        "parts");
                        StringBuilder sb =
                                new StringBuilder();
                        for (int i = 0;
                             i < parts.length(); i++) {
                            JSONObject part =
                                    parts.getJSONObject(i);
                            String text =
                                    part.optString(
                                            "text", "");
                            sb.append(text);
                        }
                        fullText = sb.toString().trim();
                    }
                }
            }

            Log.d(TAG, "Gemini text: " + fullText);

            if (fullText.isEmpty()) {
                // Check for blocked content
                if (json.has("promptFeedback")) {
                    JSONObject feedback =
                            json.getJSONObject(
                                    "promptFeedback");
                    String blockReason =
                            feedback.optString(
                                    "blockReason", "");
                    if (!blockReason.isEmpty()) {
                        mainHandler.post(() ->
                                callback.onError(
                                        "Response was blocked. "
                                                + "Please rephrase "
                                                + "your question."));
                        return;
                    }
                }
                mainHandler.post(() ->
                        callback.onError(
                                "I did not get a response. "
                                        + "Please try again."));
                return;
            }

            // Add to history (Gemini uses "model" role)
            JSONObject assistantMsg = new JSONObject();
            assistantMsg.put("role", "model");
            JSONArray assistParts = new JSONArray();
            JSONObject assistPart = new JSONObject();
            assistPart.put("text", fullText);
            assistParts.put(assistPart);
            assistantMsg.put("parts", assistParts);
            history.add(assistantMsg);

            // Parse action tag
            String action     = ACTION_NONE;
            String actionData = "";
            String spokenText = fullText;

            // Look for [ACTION:xxx] or [ACTION:xxx|data]
            int actionStart =
                    fullText.indexOf("[ACTION:");
            if (actionStart >= 0) {
                int actionEnd =
                        fullText.indexOf("]", actionStart);
                if (actionEnd > actionStart) {
                    String actionContent =
                            fullText.substring(
                                            actionStart + 8, actionEnd)
                                    .trim();

                    if (actionContent.contains("|")) {
                        String[] parts =
                                actionContent.split("\\|", 2);
                        action     = parts[0].trim()
                                .toUpperCase();
                        actionData = parts[1].trim();
                    } else {
                        action = actionContent.trim()
                                .toUpperCase();
                    }

                    // Remove action tag from spoken text
                    spokenText = fullText
                            .substring(0, actionStart)
                            .trim();

                    // Clean up any trailing punctuation
                    if (spokenText.endsWith("..")
                            || spokenText.endsWith(".."))
                        spokenText = spokenText
                                .substring(0,
                                        spokenText.length() - 1);
                }
            }

            // Validate action
            if (!isValidAction(action)) {
                action = ACTION_NONE;
            }

            Log.d(TAG, "Action: " + action
                    + " | Data: " + actionData
                    + " | Text: " + spokenText);

            final String finalText   = spokenText;
            final String finalAction = action;
            final String finalData   = actionData;

            mainHandler.post(() ->
                    callback.onResponse(
                            finalText,
                            finalAction,
                            finalData));

        } catch (Exception e) {
            Log.e(TAG, "JSON parse error: "
                    + e.getMessage());
            mainHandler.post(() ->
                    callback.onError(
                            "Could not understand the response. "
                                    + "Please try again."));
        }
    }

    private boolean isValidAction(String action) {
        return ACTION_SEND_SOS.equals(action)
                || ACTION_OPEN_FIRSTAID.equals(action)
                || ACTION_SEARCH_FIRSTAID.equals(action)
                || ACTION_CALL_STATION.equals(action)
                || ACTION_NONE.equals(action);
    }

    private void handleApiError(int code,
                                String body,
                                AssistantCallback cb) {
        Log.e(TAG, "API Error " + code + " body: " + body);

        String msg = "Error " + code + ". Please try again.";

        try {
            JSONObject err = new JSONObject(body);
            JSONObject error = err.optJSONObject("error");
            String message = error != null
                    ? error.optString("message", "") : body;
            int errCode = error != null
                    ? error.optInt("code", code) : code;

            Log.e(TAG, "Error message: " + message);

            if (errCode == 400) {
                if (message.contains("API key")) {
                    msg = "Invalid API key. "
                            + "Please check your Gemini "
                            + "API key.";
                } else {
                    msg = "Bad request: " + message;
                }
            } else if (errCode == 403) {
                msg = "API key not authorized. "
                        + "Please check your Gemini "
                        + "API key permissions.";
            } else if (errCode == 429) {
                msg = "Rate limited: " + message
                        + " Please wait a moment "
                        + "and try again.";
            } else if (errCode == 500
                    || errCode == 503) {
                msg = "Server error. Please try again "
                        + "in a moment.";
            } else {
                msg = "Error " + errCode + ": " + message;
            }

        } catch (Exception e) {
            Log.e(TAG, "Error parsing error body: "
                    + e.getMessage());
        }

        final String finalMsg = msg;
        mainHandler.post(() -> cb.onError(finalMsg));
    }

    // ── System prompt ─────────────────────────────────────────────
    private String getSystemPrompt() {
        return "You are the BFP (Bureau of Fire "
                + "Protection) Voice Assistant. You help "
                + "people — especially those with "
                + "disabilities — use the BFP emergency app. "
                + "Be calm, clear, and brief.\n\n"

                + "IMPORTANT RESPONSE FORMAT:\n"
                + "Your response must be plain text only — "
                + "no markdown, no bullet points, no asterisks, "
                + "no special formatting. Write in simple "
                + "short sentences suitable for text-to-speech.\n\n"

                + "At the END of your response, add ONE "
                + "action tag using EXACTLY this format:\n"
                + "[ACTION:SEND_SOS]\n"
                + "[ACTION:OPEN_FIRSTAID]\n"
                + "[ACTION:SEARCH_FIRSTAID|topic]\n"
                + "[ACTION:CALL_STATION]\n"
                + "[ACTION:NONE]\n\n"

                + "RULES:\n"
                + "1. Keep response under 3 sentences.\n"
                + "2. Always end with exactly one action tag.\n"
                + "3. For emergencies or fire, use SEND_SOS.\n"
                + "4. For first aid questions, use "
                + "SEARCH_FIRSTAID with the topic.\n"
                + "5. For calling BFP, use CALL_STATION.\n"
                + "6. If no action needed, use NONE.\n"
                + "7. The topic in SEARCH_FIRSTAID should be "
                + "a single word or short phrase.\n\n"

                + "EXAMPLES:\n"
                + "Input: Send SOS alert\n"
                + "Output: Sending emergency SOS alert to BFP "
                + "right now. Please stay calm and stay safe. "
                + "[ACTION:SEND_SOS]\n\n"

                + "Input: How do I do CPR\n"
                + "Output: Opening the CPR first aid guide "
                + "for you now. Follow each step carefully. "
                + "[ACTION:SEARCH_FIRSTAID|CPR]\n\n"

                + "Input: Someone is choking\n"
                + "Output: Opening the choking first aid "
                + "guide immediately. Act quickly and follow "
                + "the steps. "
                + "[ACTION:SEARCH_FIRSTAID|choking]\n\n"

                + "Input: Open first aid guides\n"
                + "Output: Opening the first aid guides now. "
                + "[ACTION:OPEN_FIRSTAID]\n\n"

                + "Input: Call fire station\n"
                + "Output: Calling the BFP fire station now. "
                + "[ACTION:CALL_STATION]\n\n"

                + "Input: Hello\n"
                + "Output: Hello! I am your BFP assistant. "
                + "I can send SOS alerts, open first aid "
                + "guides, or call the fire station. "
                + "How can I help you? "
                + "[ACTION:NONE]";
    }

    public void clearHistory() {
        history.clear();
    }

    public void release() {
        stopSpeaking();
        if (tts != null) {
            tts.shutdown();
            tts = null;
            ttsReady = false;
        }
    }
}
