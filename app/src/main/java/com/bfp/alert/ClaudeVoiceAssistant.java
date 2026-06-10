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

import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;


public class ClaudeVoiceAssistant {

    private static final String TAG = "BFPVoice";

    // ── Groq API config (free) ────────────────────────────────────
    private static final String API_KEY =
            BuildConfig.GROQ_API_KEY;
    private static final String API_URL  =
            "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    =
            "llama-3.3-70b-versatile";

    // ── Actions ───────────────────────────────────────────────────
    public static final String ACTION_SEND_SOS        = "SEND_SOS";
    public static final String ACTION_OPEN_FIRSTAID   = "OPEN_FIRSTAID";
    public static final String ACTION_SEARCH_FIRSTAID = "SEARCH_FIRSTAID";
    public static final String ACTION_CALL_STATION    = "CALL_STATION";
    public static final String ACTION_NONE            = "NONE";

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

    // Conversation history
    private final List<JSONObject> history =
            new ArrayList<>();

    public ClaudeVoiceAssistant(Context ctx) {
        this.ctx         = ctx;
        this.mainHandler =
                new Handler(Looper.getMainLooper());
        this.httpClient  =
                new OkHttpClient.Builder()
                        .connectTimeout(30,
                                java.util.concurrent.TimeUnit.SECONDS)
                        .readTimeout(30,
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
                int result =
                        tts.setLanguage(Locale.ENGLISH);
                if (result == TextToSpeech.LANG_MISSING_DATA
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

    // ── Send to Groq ──────────────────────────────────────────────
    public void processUserInput(
            String userText,
            AssistantCallback callback) {

        new Thread(() -> {
            try {
                // Build messages array
                // Always start with system message
                JSONArray messages = new JSONArray();

                // System message
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", getSystemPrompt());
                messages.put(sysMsg);

                // Add conversation history
                for (JSONObject msg : history) {
                    messages.put(msg);
                }

                // Add current user message
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userText);
                messages.put(userMsg);

                // Build request body
                // Groq uses OpenAI-compatible format
                JSONObject requestBody =
                        new JSONObject();
                requestBody.put("model",      MODEL);
                requestBody.put("max_tokens", 300);
                requestBody.put("temperature", 0.3);
                requestBody.put("messages",   messages);

                String bodyStr = requestBody.toString();
                Log.d(TAG, "Request: " + bodyStr);

                RequestBody body = RequestBody.create(
                        bodyStr,
                        MediaType.get(
                                "application/json; charset=utf-8"));

                Request request =
                        new Request.Builder()
                                .url(API_URL)
                                .header("Authorization",
                                        "Bearer " + API_KEY)
                                .header("Content-Type",
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

                    Log.d(TAG, "HTTP: "
                            + response.code());
                    Log.d(TAG, "Body: "
                            + responseStr);

                    if (!response.isSuccessful()) {
                        handleApiError(
                                response.code(),
                                responseStr,
                                callback);
                        return;
                    }

                    // Save to history on success
                    JSONObject saved =
                            new JSONObject();
                    saved.put("role", "user");
                    saved.put("content", userText);
                    history.add(saved);

                    parseAndRespond(
                            responseStr, callback);
                }

            } catch (java.io.IOException e) {
                Log.e(TAG, "IO: " + e.getMessage());
                mainHandler.post(() ->
                        callback.onError(
                                "Network error. Please check "
                                        + "your internet connection."));
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage());
                mainHandler.post(() ->
                        callback.onError(
                                "Something went wrong. "
                                        + "Please try again."));
            }
        }).start();
    }

    // ── Parse Groq response ───────────────────────────────────────
    // Groq uses OpenAI format:
    // choices[0].message.content
    private void parseAndRespond(
            String responseStr,
            AssistantCallback callback) {

        try {
            JSONObject json =
                    new JSONObject(responseStr);

            // Get content from
            // choices[0].message.content
            String fullText = "";

            JSONArray choices =
                    json.optJSONArray("choices");
            if (choices != null
                    && choices.length() > 0) {
                JSONObject choice =
                        choices.getJSONObject(0);
                JSONObject message =
                        choice.optJSONObject("message");
                if (message != null) {
                    fullText = message.optString(
                            "content", "");
                }
            }

            Log.d(TAG, "AI text: " + fullText);

            if (fullText.isEmpty()) {
                mainHandler.post(() ->
                        callback.onError(
                                "No response received. "
                                        + "Please try again."));
                return;
            }

            // Save assistant response to history
            JSONObject assistantMsg =
                    new JSONObject();
            assistantMsg.put("role", "assistant");
            assistantMsg.put("content", fullText);
            history.add(assistantMsg);

            // Parse action tag
            String action     = ACTION_NONE;
            String actionData = "";
            String spokenText = fullText;

            int actionStart =
                    fullText.indexOf("[ACTION:");
            if (actionStart >= 0) {
                int actionEnd =
                        fullText.indexOf("]", actionStart);
                if (actionEnd > actionStart) {
                    String actionContent =
                            fullText.substring(
                                    actionStart + 8,
                                    actionEnd).trim();

                    if (actionContent.contains("|")) {
                        String[] parts =
                                actionContent.split(
                                        "\\|", 2);
                        action     =
                                parts[0].trim()
                                        .toUpperCase();
                        actionData =
                                parts[1].trim();
                    } else {
                        action =
                                actionContent.trim()
                                        .toUpperCase();
                    }

                    // Remove action tag
                    // from spoken text
                    spokenText = fullText
                            .substring(0, actionStart)
                            .trim();
                }
            }

            // Fallback: if AI didn't include
            // action tag, detect from text
            if (ACTION_NONE.equals(action)) {
                action = detectActionFromText(
                        fullText);
            }

            if (!isValidAction(action)) {
                action = ACTION_NONE;
            }

            Log.d(TAG, "Action: " + action
                    + " Data: " + actionData);

            final String fa = action;
            final String fd = actionData;
            final String ft = spokenText.isEmpty()
                    ? fullText : spokenText;

            mainHandler.post(() ->
                    callback.onResponse(ft, fa, fd));

        } catch (Exception e) {
            Log.e(TAG, "Parse: " + e.getMessage());
            mainHandler.post(() ->
                    callback.onError(
                            "Could not process response. "
                                    + "Please try again."));
        }
    }

    // Fallback detection if AI forgets action tag
    private String detectActionFromText(
            String text) {
        String lower = text.toLowerCase();

        if (lower.contains("sending sos")
                || lower.contains("sos alert")
                || lower.contains("emergency alert")) {
            return ACTION_SEND_SOS;
        }
        if (lower.contains("calling")
                || lower.contains("fire station")
                || lower.contains("dialing")) {
            return ACTION_CALL_STATION;
        }
        if (lower.contains("first aid guide")
                || lower.contains("opening the guide")
                || lower.contains("first aid guides")) {
            return ACTION_OPEN_FIRSTAID;
        }
        if (lower.contains("cpr guide")
                || lower.contains("choking guide")
                || lower.contains("burns guide")) {
            return ACTION_SEARCH_FIRSTAID;
        }
        return ACTION_NONE;
    }

    private boolean isValidAction(String action) {
        return ACTION_SEND_SOS.equals(action)
                || ACTION_OPEN_FIRSTAID.equals(action)
                || ACTION_SEARCH_FIRSTAID.equals(action)
                || ACTION_CALL_STATION.equals(action)
                || ACTION_NONE.equals(action);
    }

    // ── Handle API errors ─────────────────────────────────────────
    private void handleApiError(int code,
                                String body,
                                AssistantCallback cb) {
        Log.e(TAG, "API Error " + code
                + " body: " + body);

        String msg =
                "Error " + code + ". Please try again.";

        try {
            JSONObject err =
                    new JSONObject(body);
            JSONObject error =
                    err.optJSONObject("error");
            String message = error != null
                    ? error.optString("message", "")
                    : body;

            Log.e(TAG, "Error message: " + message);

            if (code == 400) {
                msg = "Bad request: " + message;
            } else if (code == 401) {
                msg = "Invalid API key. "
                        + "Please check your Groq key.";
            } else if (code == 429) {
                msg = "Too many requests. "
                        + "Please wait and try again.";
            } else if (code == 503) {
                msg = "Service unavailable. "
                        + "Please try again shortly.";
            } else {
                msg = "Error " + code
                        + ". Please try again.";
            }
        } catch (Exception e) {
            Log.e(TAG, "Error parsing: "
                    + e.getMessage());
        }

        final String finalMsg = msg;
        mainHandler.post(() -> cb.onError(finalMsg));
    }

    // ── System prompt ─────────────────────────────────────────────
    private String getSystemPrompt() {
        return "You are the BFP (Bureau of Fire "
                + "Protection) Voice Assistant app. "
                + "Help people — especially those with "
                + "disabilities — use the BFP emergency app. "
                + "Be calm, clear, and very brief.\n\n"

                + "RESPONSE FORMAT RULES:\n"
                + "1. Plain text only. No markdown, no "
                + "asterisks, no bullet points, no headers.\n"
                + "2. Maximum 2 short sentences.\n"
                + "3. Always end with exactly one action "
                + "tag from this list:\n"
                + "   [ACTION:SEND_SOS]\n"
                + "   [ACTION:OPEN_FIRSTAID]\n"
                + "   [ACTION:SEARCH_FIRSTAID|topic]\n"
                + "   [ACTION:CALL_STATION]\n"
                + "   [ACTION:NONE]\n"
                + "4. For SEARCH_FIRSTAID, replace topic "
                + "with one word like CPR, choking, burns, "
                + "bleeding, fracture.\n"
                + "5. Never skip the action tag.\n\n"

                + "ACTION RULES:\n"
                + "- Fire, emergency, danger, help, "
                + "accident = SEND_SOS\n"
                + "- First aid, medical guide, "
                + "open guides = OPEN_FIRSTAID\n"
                + "- CPR, choking, burns, bleeding, "
                + "wound, fracture, specific condition "
                + "= SEARCH_FIRSTAID|topic\n"
                + "- Call, phone, dial, "
                + "fire station = CALL_STATION\n"
                + "- Greetings, questions, "
                + "help menu = NONE\n\n"

                + "EXAMPLES:\n"
                + "User: Send SOS\n"
                + "You: Sending SOS alert to BFP now. "
                + "Stay safe. [ACTION:SEND_SOS]\n\n"

                + "User: CPR steps\n"
                + "You: Opening CPR guide for you now. "
                + "[ACTION:SEARCH_FIRSTAID|CPR]\n\n"

                + "User: Someone is choking\n"
                + "You: Opening choking first aid guide "
                + "immediately. [ACTION:SEARCH_FIRSTAID|choking]\n\n"

                + "User: Open first aid\n"
                + "You: Opening first aid guides now. "
                + "[ACTION:OPEN_FIRSTAID]\n\n"

                + "User: Call fire station\n"
                + "You: Calling BFP fire station now. "
                + "[ACTION:CALL_STATION]\n\n"

                + "User: Hello\n"
                + "You: Hello! I can send SOS, open first "
                + "aid guides, or call the fire station. "
                + "[ACTION:NONE]";
    }

    public void clearHistory() {
        history.clear();
    }

    public void release() {
        stopSpeaking();
        if (tts != null) {
            tts.shutdown();
            tts     = null;
            ttsReady = false;
        }
    }
}