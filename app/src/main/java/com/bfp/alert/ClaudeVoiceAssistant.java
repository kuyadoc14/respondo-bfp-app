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

    // ── Groq API config (secured via ApiSecurityConfig) ───
    private static final String MODEL    =
            "llama-3.3-70b-versatile";

    // ── Actions ───────────────────────────────────────────────────
    public static final String ACTION_SEND_SOS          = "SEND_SOS";
    public static final String ACTION_SEND_SOS_VICTIM   = "SEND_SOS_VICTIM";
    public static final String ACTION_SEND_SOS_INCIDENT = "SEND_SOS_INCIDENT";
    public static final String ACTION_OPEN_FIRSTAID     = "OPEN_FIRSTAID";
    public static final String ACTION_SEARCH_FIRSTAID   = "SEARCH_FIRSTAID";
    public static final String ACTION_OPEN_PROFILE      = "OPEN_PROFILE";
    public static final String ACTION_OPEN_SOS          = "OPEN_SOS";
    public static final String ACTION_CALL_STATION      = "CALL_STATION";
    public static final String ACTION_CHECK_STATUS      = "CHECK_STATUS";
    public static final String ACTION_CANCEL_SOS        = "CANCEL_SOS";
    public static final String ACTION_FLASHLIGHT_ON     = "FLASHLIGHT_ON";
    public static final String ACTION_FLASHLIGHT_OFF    = "FLASHLIGHT_OFF";
    public static final String ACTION_PLAY_SIREN        = "PLAY_SIREN";
    public static final String ACTION_STOP_SIREN        = "STOP_SIREN";
    public static final String ACTION_PLAY_VIDEO        = "PLAY_VIDEO";
    public static final String ACTION_PAUSE_VIDEO       = "PAUSE_VIDEO";
    public static final String ACTION_UPDATE_CONDITION  = "UPDATE_CONDITION";
    public static final String ACTION_NONE              = "NONE";

    // ── Tool Call Model ───────────────────────────────────────────
    public static class ToolCall {
        public final String name;
        public final JSONObject args;

        public ToolCall(String name, JSONObject args) {
            this.name = name != null ? name : "";
            this.args = args != null ? args : new JSONObject();
        }
    }

    // ── Callback ──────────────────────────────────────────────────
    public interface AssistantCallback {
        void onResponse(String spokenText,
                        String action,
                        String actionData);
        default void onMultiToolResponse(String spokenText,
                                         List<ToolCall> toolCalls) {
            onResponse(spokenText, ACTION_NONE, "");
        }
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
    private String             lastDetectedGuideTitle = null;

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
        applyBestTtsLocale(text);

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

    private void applyBestTtsLocale(String text) {
        if (tts == null || text == null) return;
        String lower = text.toLowerCase(Locale.ROOT);
        boolean looksFilipino = lower.contains(" ang ") || lower.contains(" mga ")
                || lower.contains(" po") || lower.contains(" na ")
                || lower.contains(" sa ") || lower.contains("tulong")
                || lower.contains("sunog") || lower.contains("bumbero")
                || lower.contains("kumusta") || lower.contains("kamusta");
        if (looksFilipino) {
            Locale fil = new Locale("fil", "PH");
            int res = tts.isLanguageAvailable(fil);
            if (res >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(fil);
                return;
            }
            Locale tl = new Locale("tl", "PH");
            if (tts.isLanguageAvailable(tl) >= TextToSpeech.LANG_AVAILABLE) {
                tts.setLanguage(tl);
                return;
            }
        }
        Locale def = Locale.getDefault();
        if (tts.isLanguageAvailable(def) >= TextToSpeech.LANG_AVAILABLE) {
            tts.setLanguage(def);
        } else {
            tts.setLanguage(Locale.ENGLISH);
        }
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
        processUserInput(userText, "", callback);
    }

    public void processUserInput(
            String userText,
            String appContextSummary,
            AssistantCallback callback) {

        new Thread(() -> {
            try {
                // Build messages array
                // Always start with system message
                JSONArray messages = new JSONArray();

                // System message
                JSONObject sysMsg = new JSONObject();
                sysMsg.put("role", "system");
                sysMsg.put("content", getSystemPrompt(appContextSummary));
                messages.put(sysMsg);

                // Keep history bounded to recent turns
                while (history.size() > 10) {
                    history.remove(0);
                }
                for (JSONObject msg : history) {
                    messages.put(msg);
                }

                // Add current user message
                JSONObject userMsg = new JSONObject();
                userMsg.put("role", "user");
                userMsg.put("content", userText);
                messages.put(userMsg);

                // Build request body with native multi-tool calling
                JSONObject requestBody =
                        new JSONObject();
                requestBody.put("model",       MODEL);
                requestBody.put("max_tokens",  450);
                requestBody.put("temperature", 0.2);
                requestBody.put("messages",    messages);
                requestBody.put("tools",       buildToolsArray());
                requestBody.put("tool_choice", "auto");

                String bodyStr = requestBody.toString();
                Log.d(TAG, "Request: " + bodyStr);

                RequestBody body = RequestBody.create(
                        bodyStr,
                        MediaType.get(
                                "application/json; charset=utf-8"));

                String groqKey = ApiSecurityConfig.getGroqApiKey();
                if (groqKey != null) {
                    groqKey = groqKey.replaceAll("[^a-zA-Z0-9_\\-]", "").trim();
                }

                if (groqKey == null || groqKey.isEmpty()) {
                    Log.d(TAG, "No Groq API key configured; using local assistant logic");
                    fallbackLocalResponse(userText, callback);
                    return;
                }

                Request request =
                        new Request.Builder()
                                .url(ApiSecurityConfig.getGroqUrl())
                                .header("Authorization",
                                        "Bearer " + groqKey)
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
                        Log.w(TAG, "Groq API HTTP " + response.code() + ", falling back to local intent parser");
                        if (!fallbackLocalResponse(userText, callback)) {
                            handleApiError(
                                    response.code(),
                                    responseStr,
                                    callback);
                        }
                        return;
                    }

                    // Save to history on success
                    JSONObject saved =
                            new JSONObject();
                    saved.put("role", "user");
                    saved.put("content", userText);
                    history.add(saved);

                    parseAndRespond(
                            userText, responseStr, callback);
                }

            } catch (java.io.IOException e) {
                Log.e(TAG, "IO: " + e.getMessage());
                if (!fallbackLocalResponse(userText, callback)) {
                    mainHandler.post(() ->
                            callback.onError(
                                    "Network error. Please check "
                                            + "your internet connection."));
                }
            } catch (Exception e) {
                Log.e(TAG, "Error: " + e.getMessage(), e);
                if (!fallbackLocalResponse(userText, callback)) {
                    mainHandler.post(() ->
                            callback.onError(
                                    "Something went wrong. "
                                            + "Please try again."));
                }
            }
        }).start();
    }

    // ── Parse Groq response ───────────────────────────────────────
    private void parseAndRespond(
            String userText,
            String responseStr,
            AssistantCallback callback) {

        try {
            JSONObject json =
                    new JSONObject(responseStr);

            String fullText = "";
            JSONArray rawToolCalls = null;
            JSONObject message = null;

            JSONArray choices =
                    json.optJSONArray("choices");
            if (choices != null
                    && choices.length() > 0) {
                JSONObject choice =
                        choices.getJSONObject(0);
                message =
                        choice.optJSONObject("message");
                if (message != null) {
                    fullText = message.optString("content", "");
                    if ("null".equals(fullText)) fullText = "";
                    rawToolCalls = message.optJSONArray("tool_calls");
                }
            }

            Log.d(TAG, "AI text: " + fullText + " | tool_calls: "
                    + (rawToolCalls != null ? rawToolCalls.length() : 0));

            // ── 1. Handle native multi-tool calls ─────────────────
            if (rawToolCalls != null && rawToolCalls.length() > 0) {
                List<ToolCall> toolCalls = new ArrayList<>();
                for (int i = 0; i < rawToolCalls.length(); i++) {
                    JSONObject tc = rawToolCalls.optJSONObject(i);
                    if (tc == null) continue;
                    JSONObject fn = tc.optJSONObject("function");
                    if (fn == null) continue;
                    String fnName = fn.optString("name", "");
                    String argsRaw = fn.optString("arguments", "{}");
                    JSONObject argsJson;
                    try {
                        argsJson = new JSONObject(argsRaw);
                    } catch (Exception ignored) {
                        argsJson = new JSONObject();
                    }
                    if (!fnName.isEmpty()) {
                        toolCalls.add(new ToolCall(fnName, argsJson));
                    }
                }

                // Store valid assistant + tool messages in history so next turn stays schema-valid
                if (message != null) {
                    JSONObject assistantMsg = new JSONObject();
                    assistantMsg.put("role", "assistant");
                    assistantMsg.put("content", fullText.isEmpty() ? JSONObject.NULL : fullText);
                    assistantMsg.put("tool_calls", rawToolCalls);
                    history.add(assistantMsg);

                    for (int i = 0; i < rawToolCalls.length(); i++) {
                        JSONObject tc = rawToolCalls.optJSONObject(i);
                        if (tc == null) continue;
                        String tcId = tc.optString("id", "call_" + i);
                        JSONObject toolResultMsg = new JSONObject();
                        toolResultMsg.put("role", "tool");
                        toolResultMsg.put("tool_call_id", tcId);
                        toolResultMsg.put("content", "{\"status\":\"executed\"}");
                        history.add(toolResultMsg);
                    }
                }

                String spoken = stripActionTags(fullText);
                if (spoken.isEmpty()) {
                    spoken = buildSpokenSummaryFromTools(toolCalls);
                }

                final String finalSpoken = spoken;
                final List<ToolCall> finalTools = toolCalls;
                mainHandler.post(() -> {
                    callback.onMultiToolResponse(finalSpoken, finalTools);
                });
                return;
            }

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

            // Parse legacy [ACTION:...] tag if present
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

                    spokenText = fullText
                            .substring(0, actionStart)
                            .trim();
                }
            }

            // Normalise SEND_SOS|victim to ACTION_SEND_SOS_VICTIM
            if (ACTION_SEND_SOS.equals(action) && "victim".equalsIgnoreCase(actionData)) {
                action = ACTION_SEND_SOS_VICTIM;
                actionData = userText;
            }

            // Fallback: if AI didn't include action tag or returned NONE, detect from user & AI context
            if (ACTION_NONE.equals(action) || !isValidAction(action)) {
                action = detectActionFromContext(userText, fullText);
            }

            // If action is SOS and actionData is empty, use userText as incident detail
            if ((ACTION_SEND_SOS.equals(action) || ACTION_SEND_SOS_INCIDENT.equals(action) || ACTION_SEND_SOS_VICTIM.equals(action))
                    && (actionData == null || actionData.trim().isEmpty())) {
                actionData = userText;
            }

            // If action is SEARCH_FIRSTAID and actionData is empty, use lastDetectedGuideTitle
            if (ACTION_SEARCH_FIRSTAID.equals(action) && (actionData == null || actionData.trim().isEmpty())) {
                actionData = (lastDetectedGuideTitle != null) ? lastDetectedGuideTitle : userText;
            }
            if (ACTION_SEARCH_FIRSTAID.equals(action)) {
                String lu = userText.toLowerCase();
                if ((lu.contains("video") || lu.contains("play") || lu.contains("watch")) && actionData != null && !actionData.contains("|play")) {
                    actionData = actionData + "|play";
                }
            }

            if (!isValidAction(action)) {
                action = ACTION_NONE;
            }

            Log.d(TAG, "Action: " + action
                    + " Data: " + actionData);

            final List<ToolCall> convertedTools = new ArrayList<>();
            if (!ACTION_NONE.equals(action)) {
                convertedTools.add(legacyActionToToolCall(action, actionData, userText));
            }
            final String ft = spokenText.isEmpty()
                    ? fullText : spokenText;

            mainHandler.post(() ->
                    callback.onMultiToolResponse(ft, convertedTools));

        } catch (Exception e) {
            Log.e(TAG, "Parse: " + e.getMessage());
            mainHandler.post(() ->
                    callback.onError(
                            "Could not process response. "
                                    + "Please try again."));
        }
    }

    // Fallback detection if AI forgets action tag
    private String detectActionFromContext(
            String userText,
            String text) {
        String lowerUser = userText.toLowerCase().trim();
        String lowerAi   = text.toLowerCase();
        this.lastDetectedGuideTitle = null;

        if (lowerUser.contains("flashlight on") || lowerUser.contains("turn on flashlight") || lowerUser.contains("open flashlight")
                || lowerUser.contains("buksan ang ilaw") || lowerUser.contains("buksan ang flashlight") || lowerUser.contains("ilaw on")) {
            return ACTION_FLASHLIGHT_ON;
        }
        if (lowerUser.contains("flashlight off") || lowerUser.contains("turn off flashlight")
                || lowerUser.contains("patayin ang ilaw") || lowerUser.contains("patayin ang flashlight") || lowerUser.contains("isara ang ilaw")) {
            return ACTION_FLASHLIGHT_OFF;
        }
        if (lowerUser.contains("siren") || lowerUser.contains("sirena") || lowerUser.contains("alarm")) {
            if (lowerUser.contains("stop") || lowerUser.contains("off") || lowerUser.contains("silence")
                    || lowerUser.contains("patayin") || lowerUser.contains("itigil") || lowerUser.contains("ihinto")) {
                return ACTION_STOP_SIREN;
            }
            return ACTION_PLAY_SIREN;
        }
        if (lowerUser.contains("cancel") || lowerUser.contains("false alarm") || lowerUser.contains("kanselahin") || lowerUser.contains("ikansela")) {
            return ACTION_CANCEL_SOS;
        }
        if (lowerUser.contains("responder") || lowerUser.contains("status") || lowerUser.contains("en route") || lowerUser.contains("deployed")
                || lowerUser.contains("nasaan") || lowerUser.contains("papunta na")) {
            return ACTION_CHECK_STATUS;
        }
        if ((lowerUser.contains("call") || lowerUser.contains("tawagan") || lowerUser.contains("tawag"))
                && (lowerUser.contains("station") || lowerUser.contains("bfp") || lowerUser.contains("bumbero") || lowerUser.contains("911") || lowerUser.contains("dial"))) {
            return ACTION_CALL_STATION;
        }

        // 0. Video playback controls
        if (lowerUser.contains("pause video") || lowerUser.contains("pause the video") || lowerUser.contains("stop video")
                || lowerUser.contains("ihinto ang video") || lowerUser.contains("itigil ang video") || lowerUser.contains("i-pause")) {
            return ACTION_PAUSE_VIDEO;
        }
        if (lowerUser.contains("play video") || lowerUser.contains("play the video") || lowerUser.contains("start video")
                || lowerUser.contains("watch video") || lowerUser.contains("resume video")
                || lowerUser.contains("i-play ang video") || lowerUser.contains("panoorin ang video") || lowerUser.contains("patugtugin ang video")) {
            FirstAidItem guideMatch = FirstAidRepository.getInstance().findMatchingItem(lowerUser);
            if (guideMatch != null) {
                this.lastDetectedGuideTitle = guideMatch.title + "|play";
                return ACTION_SEARCH_FIRSTAID;
            }
            return ACTION_PLAY_VIDEO;
        }

        // 1. Navigation intents
        if (lowerUser.contains("open first aid") || lowerUser.contains("first aid panel") || lowerUser.equals("first aid")
                || lowerUser.contains("first aid guides") || lowerUser.contains("buksan ang first aid")) {
            return ACTION_OPEN_FIRSTAID;
        }
        if (lowerUser.contains("open profile") || lowerUser.contains("go to profile") || lowerUser.contains("my profile")
                || lowerUser.contains("profile panel") || lowerUser.contains("buksan ang profile")) {
            return ACTION_OPEN_PROFILE;
        }
        if (lowerUser.contains("open sos") || lowerUser.contains("go to sos") || lowerUser.contains("back to sos")
                || lowerUser.contains("radar screen") || lowerUser.contains("sos panel") || lowerUser.contains("bumalik sa sos")) {
            return ACTION_OPEN_SOS;
        }

        // 2. Specific First Aid Guides (e.g. "open burn", "burns", "cpr", "choking", "snake bite")
        FirstAidItem match = FirstAidRepository.getInstance().findMatchingItem(lowerUser);
        if (match != null) {
            if (lowerUser.contains("video") || lowerUser.contains("play") || lowerUser.contains("watch")) {
                this.lastDetectedGuideTitle = match.title + "|play";
            } else {
                this.lastDetectedGuideTitle = match.title;
            }
            return ACTION_SEARCH_FIRSTAID;
        }

        // 3. Emergency SOS check (fires, leaks, burning house, crashes, acute danger)
        boolean hasEmergencyIndicator = lowerUser.contains("gas leak")
                || lowerUser.contains("leak")
                || lowerUser.contains("house is burning")
                || lowerUser.contains("house is on fire")
                || lowerUser.contains("building fire")
                || lowerUser.contains("sunog")
                || lowerUser.contains("nasusunog")
                || lowerUser.contains("trapped")
                || lowerUser.contains("naipit")
                || lowerUser.contains("crash")
                || lowerUser.contains("accident")
                || lowerUser.contains("banggaan")
                || lowerUser.contains("explosion")
                || lowerUser.contains("sumabog")
                || lowerUser.contains("drowning")
                || lowerUser.contains("nalulunod")
                || lowerUser.contains("bleeding heavily")
                || lowerUser.contains("heart attack")
                || lowerUser.contains("stroke")
                || lowerUser.contains("help")
                || lowerUser.contains("saklolo")
                || lowerUser.contains("tulong")
                || lowerUser.contains("emergency")
                || lowerUser.contains("sos")
                || lowerAi.contains("help is on the way")
                || lowerAi.contains("dispatching")
                || lowerAi.contains("sending sos");

        boolean isJustQuestion = (lowerUser.contains("what is") || lowerUser.contains("how to") || lowerUser.contains("number") || lowerUser.contains("hotline"))
                && !lowerUser.contains("my house") && !lowerUser.contains("burning") && !lowerUser.contains("leak") && !lowerUser.contains("sunog");

        if (hasEmergencyIndicator && !isJustQuestion) {
            if (lowerUser.contains("victim") || lowerUser.contains("biktima") || lowerUser.contains("my house") || lowerUser.contains("i am") || lowerUser.contains("ako") || lowerUser.contains("tulong")) {
                return ACTION_SEND_SOS_VICTIM;
            }
            return ACTION_SEND_SOS_INCIDENT;
        }

        return ACTION_NONE;
    }

    private boolean isValidAction(String action) {
        return ACTION_SEND_SOS.equals(action)
                || ACTION_SEND_SOS_VICTIM.equals(action)
                || ACTION_SEND_SOS_INCIDENT.equals(action)
                || ACTION_OPEN_FIRSTAID.equals(action)
                || ACTION_SEARCH_FIRSTAID.equals(action)
                || ACTION_OPEN_PROFILE.equals(action)
                || ACTION_OPEN_SOS.equals(action)
                || ACTION_CALL_STATION.equals(action)
                || ACTION_CHECK_STATUS.equals(action)
                || ACTION_CANCEL_SOS.equals(action)
                || ACTION_FLASHLIGHT_ON.equals(action)
                || ACTION_FLASHLIGHT_OFF.equals(action)
                || ACTION_PLAY_SIREN.equals(action)
                || ACTION_STOP_SIREN.equals(action)
                || ACTION_PLAY_VIDEO.equals(action)
                || ACTION_PAUSE_VIDEO.equals(action)
                || ACTION_UPDATE_CONDITION.equals(action)
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

    /**
     * Fallback offline intent processor when cloud AI is unreachable or API key is not configured.
     * Ensures all emergency, first aid, siren, flashlight, and call features work 100% reliably.
     */
    private boolean fallbackLocalResponse(String userText, AssistantCallback callback) {
        if (userText == null || userText.trim().isEmpty()) {
            return false;
        }

        String action = detectActionFromContext(userText, "");
        String actionData = (lastDetectedGuideTitle != null) ? lastDetectedGuideTitle : "";
        String responseText;

        switch (action) {
            case ACTION_SEND_SOS:
            case ACTION_SEND_SOS_INCIDENT:
            case ACTION_SEND_SOS_VICTIM:
                actionData = userText;
                responseText = "Sending emergency SOS alert to BFP right now. Please stay calm and stay safe.";
                break;
            case ACTION_SEARCH_FIRSTAID:
                String topic = (actionData != null && !actionData.isEmpty()) ? actionData : userText;
                String lu = userText.toLowerCase();
                if ((lu.contains("video") || lu.contains("play") || lu.contains("watch")) && !topic.contains("|play")) {
                    actionData = topic + "|play";
                    responseText = "Opening first aid guide for " + topic.replace("|play", "") + ".";
                } else {
                    responseText = "Opening first aid guide for " + topic.replace("|play", "") + ".";
                }
                break;
            case ACTION_OPEN_FIRSTAID:
                responseText = "Opening first aid guides now.";
                break;
            case ACTION_CALL_STATION:
                responseText = "Calling the BFP fire station now.";
                break;
            case ACTION_FLASHLIGHT_ON:
                responseText = "Turning on flashlight.";
                break;
            case ACTION_FLASHLIGHT_OFF:
                responseText = "Turning off flashlight.";
                break;
            case ACTION_PLAY_SIREN:
                responseText = "Activating emergency siren.";
                break;
            case ACTION_STOP_SIREN:
                responseText = "Silencing emergency siren.";
                break;
            case ACTION_CANCEL_SOS:
                responseText = "Cancelling emergency alert.";
                break;
            case ACTION_CHECK_STATUS:
                responseText = "Checking responder deployment status.";
                break;
            case ACTION_PLAY_VIDEO:
                responseText = "Resuming video playback.";
                break;
            case ACTION_PAUSE_VIDEO:
                responseText = "Pausing video.";
                break;
            case ACTION_OPEN_PROFILE:
                responseText = "Opening profile settings.";
                break;
            case ACTION_OPEN_SOS:
                responseText = "Returning to SOS screen.";
                break;
            default:
                String lower = userText.toLowerCase().trim();
                if (lower.contains("hello") || lower.contains("hi") || lower.contains("kamusta") || lower.contains("help") || lower.contains("tulong")) {
                    responseText = "Hello! I am your BFP assistant. I can send an SOS alert, open first aid guides, turn on the flashlight or siren, and call the station. How can I assist you?";
                } else {
                    responseText = "I am ready to help. You can tell me to send SOS, open first aid guides, turn on flashlight, or call the fire station.";
                }
                action = ACTION_NONE;
                break;
        }

        final List<ToolCall> finalTools = new ArrayList<>();
        if (!ACTION_NONE.equals(action)) {
            finalTools.add(legacyActionToToolCall(action, actionData, userText));
        }

        // Also check secondary offline hardware/guide intents in compound sentences
        String lu = userText.toLowerCase();
        if (lu.contains("flashlight") && !ACTION_FLASHLIGHT_ON.equals(action) && !ACTION_FLASHLIGHT_OFF.equals(action)) {
            boolean off = lu.contains("off") || lu.contains("patayin");
            JSONObject fArgs = new JSONObject();
            try { fArgs.put("enabled", !off); } catch (Exception ignored) {}
            finalTools.add(new ToolCall("control_flashlight", fArgs));
        }
        if ((lu.contains("siren") || lu.contains("alarm")) && !ACTION_PLAY_SIREN.equals(action) && !ACTION_STOP_SIREN.equals(action)) {
            boolean stop = lu.contains("stop") || lu.contains("off") || lu.contains("silence");
            JSONObject sArgs = new JSONObject();
            try { sArgs.put("enabled", !stop); } catch (Exception ignored) {}
            finalTools.add(new ToolCall("control_siren", sArgs));
        }

        final String finalResponse = responseText;

        mainHandler.post(() -> callback.onMultiToolResponse(finalResponse, finalTools));
        return true;
    }

    // ── Tool Schema Definition for Groq Function Calling ──────────
    private JSONArray buildToolsArray() {
        JSONArray tools = new JSONArray();
        try {
            tools.put(makeTool("send_sos",
                    "Dispatch an emergency SOS alert to BFP responders immediately.",
                    new JSONObject()
                            .put("role", new JSONObject()
                                    .put("type", "string")
                                    .put("enum", new JSONArray().put("victim").put("bystander"))
                                    .put("description", "Whether the user is the affected victim or a bystander witness."))
                            .put("incident_notes", new JSONObject()
                                    .put("type", "string")
                                    .put("description", "Brief description of the emergency, hazards, injuries, or location details.")),
                    new JSONArray().put("role").put("incident_notes")));

            tools.put(makeTool("cancel_sos",
                    "Cancel the currently active SOS alert or mark it as a false alarm.",
                    new JSONObject(), null));

            tools.put(makeTool("check_sos_status",
                    "Check the live status of the active SOS alert and whether BFP responders are en route.",
                    new JSONObject(), null));

            tools.put(makeTool("update_condition",
                    "Update the active SOS alert with new injury, trapped status, or hazard notes.",
                    new JSONObject()
                            .put("condition_notes", new JSONObject()
                                    .put("type", "string")
                                    .put("description", "Condition or situation update to attach to the active alert.")),
                    new JSONArray().put("condition_notes")));

            tools.put(makeTool("navigate_to",
                    "Navigate to a main screen/tab in the app.",
                    new JSONObject()
                            .put("screen", new JSONObject()
                                    .put("type", "string")
                                    .put("enum", new JSONArray().put("sos").put("first_aid").put("profile"))
                                    .put("description", "Target screen: sos, first_aid, or profile.")),
                    new JSONArray().put("screen")));

            tools.put(makeTool("open_first_aid",
                    "Search for or open a specific First Aid guide from the BFP database, optionally playing its video.",
                    new JSONObject()
                            .put("query", new JSONObject()
                                    .put("type", "string")
                                    .put("description", "Exact guide title or medical topic query."))
                            .put("auto_play_video", new JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "Set true if the user asked to watch or play the instructional video.")),
                    new JSONArray().put("query")));

            tools.put(makeTool("control_video",
                    "Play or pause the currently open First Aid instructional video.",
                    new JSONObject()
                            .put("command", new JSONObject()
                                    .put("type", "string")
                                    .put("enum", new JSONArray().put("play").put("pause"))   
                                    .put("description", "Video playback command.")),
                    new JSONArray().put("command")));

            tools.put(makeTool("control_flashlight",
                    "Turn the device camera flashlight/torch on or off.",
                    new JSONObject()
                            .put("enabled", new JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "True to turn flashlight on, false to turn off.")),
                    new JSONArray().put("enabled")));

            tools.put(makeTool("control_siren",
                    "Activate or stop the loud emergency locator siren.",
                    new JSONObject()
                            .put("enabled", new JSONObject()
                                    .put("type", "boolean")
                                    .put("description", "True to sound siren, false to stop siren.")),
                    new JSONArray().put("enabled")));

            tools.put(makeTool("call_bfp_station",
                    "Open the phone dialer to call the BFP fire station emergency hotline.",
                    new JSONObject(), null));
        } catch (Exception e) {
            Log.e(TAG, "Error building tools schema", e);
        }
        return tools;
    }

    private JSONObject makeTool(String name,
                                String description,
                                JSONObject properties,
                                JSONArray required) throws Exception {
        JSONObject params = new JSONObject();
        params.put("type", "object");
        params.put("properties", properties != null ? properties : new JSONObject());
        if (required != null && required.length() > 0) {
            params.put("required", required);
        }
        JSONObject fn = new JSONObject();
        fn.put("name", name);
        fn.put("description", description);
        fn.put("parameters", params);

        JSONObject tool = new JSONObject();
        tool.put("type", "function");
        tool.put("function", fn);
        return tool;
    }

    private ToolCall legacyActionToToolCall(String action, String actionData, String userText) {
        JSONObject args = new JSONObject();
        try {
            switch (action) {
                case ACTION_SEND_SOS_INCIDENT:
                    args.put("role", "bystander");
                    args.put("incident_notes", (actionData != null && !actionData.isEmpty()) ? actionData : userText);
                    return new ToolCall("send_sos", args);
                case ACTION_SEND_SOS_VICTIM:
                case ACTION_SEND_SOS:
                    args.put("role", "victim");
                    args.put("incident_notes", (actionData != null && !actionData.isEmpty() && !"victim".equalsIgnoreCase(actionData)) ? actionData : userText);
                    return new ToolCall("send_sos", args);
                case ACTION_OPEN_FIRSTAID:
                    args.put("screen", "first_aid");
                    return new ToolCall("navigate_to", args);
                case ACTION_OPEN_PROFILE:
                    args.put("screen", "profile");
                    return new ToolCall("navigate_to", args);
                case ACTION_OPEN_SOS:
                    args.put("screen", "sos");
                    return new ToolCall("navigate_to", args);
                case ACTION_SEARCH_FIRSTAID:
                    boolean play = actionData != null && actionData.contains("|play");
                    String q = actionData != null ? actionData.replace("|play", "").trim() : userText;
                    args.put("query", q);
                    args.put("auto_play_video", play);
                    return new ToolCall("open_first_aid", args);
                case ACTION_PLAY_VIDEO:
                    args.put("command", "play");
                    return new ToolCall("control_video", args);
                case ACTION_PAUSE_VIDEO:
                    args.put("command", "pause");
                    return new ToolCall("control_video", args);
                case ACTION_FLASHLIGHT_ON:
                    args.put("enabled", true);
                    return new ToolCall("control_flashlight", args);
                case ACTION_FLASHLIGHT_OFF:
                    args.put("enabled", false);
                    return new ToolCall("control_flashlight", args);
                case ACTION_PLAY_SIREN:
                    args.put("enabled", true);
                    return new ToolCall("control_siren", args);
                case ACTION_STOP_SIREN:
                    args.put("enabled", false);
                    return new ToolCall("control_siren", args);
                case ACTION_CHECK_STATUS:
                    return new ToolCall("check_sos_status", args);
                case ACTION_CANCEL_SOS:
                    return new ToolCall("cancel_sos", args);
                case ACTION_UPDATE_CONDITION:
                    args.put("condition_notes", actionData != null ? actionData : userText);
                    return new ToolCall("update_condition", args);
                case ACTION_CALL_STATION:
                    return new ToolCall("call_bfp_station", args);
                default:
                    return new ToolCall(action.toLowerCase(), args);
            }
        } catch (Exception ignored) {}
        return new ToolCall("none", args);
    }

    private String stripActionTags(String text) {
        if (text == null) return "";
        return text.replaceAll("\\[ACTION:[^\\]]*\\]", "").trim();
    }

    private String buildSpokenSummaryFromTools(List<ToolCall> toolCalls) {
        if (toolCalls == null || toolCalls.isEmpty()) {
            return "Done.";
        }
        List<String> parts = new ArrayList<>();
        for (ToolCall tc : toolCalls) {
            switch (tc.name) {
                case "send_sos":
                    parts.add("Sending emergency SOS alert to BFP now");
                    break;
                case "cancel_sos":
                    parts.add("Cancelling active SOS alert");
                    break;
                case "check_sos_status":
                    parts.add("Checking responder status");
                    break;
                case "update_condition":
                    parts.add("Updating your condition for responders");
                    break;
                case "navigate_to":
                    parts.add("Opening " + tc.args.optString("screen", "screen").replace("_", " "));
                    break;
                case "open_first_aid":
                    parts.add("Opening first aid guide for " + tc.args.optString("query", "your request"));
                    break;
                case "control_video":
                    parts.add("play".equals(tc.args.optString("command")) ? "Playing video" : "Pausing video");
                    break;
                case "control_flashlight":
                    parts.add(tc.args.optBoolean("enabled", true) ? "Turning on flashlight" : "Turning off flashlight");
                    break;
                case "control_siren":
                    parts.add(tc.args.optBoolean("enabled", true) ? "Activating siren" : "Stopping siren");
                    break;
                case "call_bfp_station":
                    parts.add("Calling the BFP station");
                    break;
            }
        }
        if (parts.isEmpty()) return "Done.";
        return String.join(". ", parts) + ".";
    }

    // ── System prompt ─────────────────────────────────────────────
    private String getSystemPrompt(String appContextSummary) {
        String availableGuides = FirstAidRepository.getInstance().getGuideListSummary();
        String ctxInfo = (appContextSummary != null && !appContextSummary.trim().isEmpty())
                ? "\nCURRENT APP STATE:\n" + appContextSummary + "\n"
                : "";

        return "You are the BFP (Bureau of Fire Protection) Emergency Voice Assistant with full multi-tool control over the app.\n"
                + "You assist citizens and accident victims in emergencies and help them operate the entire app hands-free.\n"
                + "Be calm, reassuring, clear, and very brief.\n"
                + "LANGUAGE FLEXIBILITY:\n"
                + "- Understand ANY language or dialect the user speaks (English, Filipino/Tagalog, Taglish, Bisaya/Cebuano, Ilocano, Hiligaynon, Spanish, etc.).\n"
                + "- ALWAYS reply in the SAME language or dialect the user used (for example, reply in Tagalog if they spoke Tagalog, Bisaya if they spoke Bisaya, English if they spoke English, Taglish if they mixed both).\n"
                + ctxInfo + "\n"
                + "RESPONSE RULES:\n"
                + "1. Plain text only. Do not use asterisks, markdown, bullet points, or emoji.\n"
                + "2. Keep spoken text very short: maximum 2 sentences.\n"
                + "3. Use the provided tools to control the app directly. You can call MULTIPLE tools in a single turn whenever the user asks for multiple things (for example: send_sos + control_flashlight + open_first_aid).\n"
                + "4. Do NOT output [ACTION:...] tags when using tool calls.\n\n"

                + "CURRENT AVAILABLE FIRST AID GUIDES IN BFP DATABASE:\n"
                + availableGuides + "\n\n"

                + "EMERGENCY & APP CONTROL RULES:\n"
                + "- ANY fire, gas leak, burning house, smoke, crash, injury, trapped person, 'help', 'saklolo', 'tulong', 'nasusunog', or 'emergency': ALWAYS call `send_sos` immediately with `role` ('victim' or 'bystander') and extracted `incident_notes`.\n"
                + "- If user asks for first aid / medical help (e.g. burn, CPR, choking) AND a matching guide exists in the list above: call `open_first_aid` with the exact matching title (and `auto_play_video: true` if they asked for video), and give 1 short life-saving tip.\n"
                + "- If NO guide matches their medical question: state clearly 'No corresponding first aid data found for that condition. Please contact emergency services or call the BFP hotline.' and optionally offer `call_bfp_station`.\n"
                + "- Hardware & media: use `control_flashlight`, `control_siren`, `control_video`, `navigate_to`, `check_sos_status`, `update_condition`, `cancel_sos`, or `call_bfp_station` as needed.\n"
                + "- BFP hotline is 09399337795 and national emergency is 911.";
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
