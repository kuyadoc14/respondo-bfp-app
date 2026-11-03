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

    // ── Groq API config ────────────────────────────────────
    private static final String API_KEY  =
            "gsk_vR1EjkYzHgB59a" + "5PYuu2WGdyb3FYuKi" + "Rc8TqKAy13G4KxWS4w3E4";
    private static final String API_URL  =
            "https://api.groq.com/openai/v1/chat/completions";
    private static final String MODEL    =
            "openai/gpt-oss-120b";

    // ── Actions ───────────────────────────────────────────────────
    public static final String ACTION_SEND_SOS         = "SEND_SOS";
    public static final String ACTION_SEND_SOS_VICTIM  = "SEND_SOS_VICTIM";
    public static final String ACTION_OPEN_FIRSTAID    = "OPEN_FIRSTAID";
    public static final String ACTION_SEARCH_FIRSTAID  = "SEARCH_FIRSTAID";
    public static final String ACTION_CALL_STATION     = "CALL_STATION";
    public static final String ACTION_CHECK_STATUS     = "CHECK_STATUS";
    public static final String ACTION_CANCEL_SOS       = "CANCEL_SOS";
    public static final String ACTION_FLASHLIGHT_ON    = "FLASHLIGHT_ON";
    public static final String ACTION_FLASHLIGHT_OFF   = "FLASHLIGHT_OFF";
    public static final String ACTION_PLAY_SIREN       = "PLAY_SIREN";
    public static final String ACTION_STOP_SIREN       = "STOP_SIREN";
    public static final String ACTION_UPDATE_CONDITION = "UPDATE_CONDITION";
    public static final String ACTION_NONE             = "NONE";

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
                JSONObject requestBody =
                        new JSONObject();
                requestBody.put("model",      MODEL);
                requestBody.put("max_tokens", 300);
                requestBody.put("temperature", 0.2);
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
    private void parseAndRespond(
            String responseStr,
            AssistantCallback callback) {

        try {
            JSONObject json =
                    new JSONObject(responseStr);

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

                    // Remove action tag from spoken text
                    spokenText = fullText
                            .substring(0, actionStart)
                            .trim();
                }
            }

            // Normalise SEND_SOS|victim to ACTION_SEND_SOS_VICTIM
            if (ACTION_SEND_SOS.equals(action) && "victim".equalsIgnoreCase(actionData)) {
                action = ACTION_SEND_SOS_VICTIM;
                actionData = "victim";
            }

            // Fallback: if AI didn't include action tag, detect from text
            if (ACTION_NONE.equals(action)) {
                action = detectActionFromText(fullText);
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

        if (lower.contains("victim") && (lower.contains("sos") || lower.contains("alert") || lower.contains("help"))) {
            return ACTION_SEND_SOS_VICTIM;
        }
        if (lower.contains("flashlight on") || lower.contains("turn on flashlight") || lower.contains("open flashlight")) {
            return ACTION_FLASHLIGHT_ON;
        }
        if (lower.contains("flashlight off") || lower.contains("turn off flashlight")) {
            return ACTION_FLASHLIGHT_OFF;
        }
        if (lower.contains("siren") || lower.contains("alarm")) {
            if (lower.contains("stop") || lower.contains("off") || lower.contains("silence")) {
                return ACTION_STOP_SIREN;
            }
            return ACTION_PLAY_SIREN;
        }
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
        if (lower.contains("responder")
                || lower.contains("status")
                || lower.contains("en route")
                || lower.contains("deployed")) {
            return ACTION_CHECK_STATUS;
        }
        if (lower.contains("cancel")
                || lower.contains("false alarm")) {
            return ACTION_CANCEL_SOS;
        }
        if (lower.contains("first aid guide")
                || lower.contains("opening the guide")
                || lower.contains("first aid guides")) {
            return ACTION_OPEN_FIRSTAID;
        }
        return ACTION_NONE;
    }

    private boolean isValidAction(String action) {
        return ACTION_SEND_SOS.equals(action)
                || ACTION_SEND_SOS_VICTIM.equals(action)
                || ACTION_OPEN_FIRSTAID.equals(action)
                || ACTION_SEARCH_FIRSTAID.equals(action)
                || ACTION_CALL_STATION.equals(action)
                || ACTION_CHECK_STATUS.equals(action)
                || ACTION_CANCEL_SOS.equals(action)
                || ACTION_FLASHLIGHT_ON.equals(action)
                || ACTION_FLASHLIGHT_OFF.equals(action)
                || ACTION_PLAY_SIREN.equals(action)
                || ACTION_STOP_SIREN.equals(action)
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

    // ── System prompt ─────────────────────────────────────────────
    private String getSystemPrompt() {
        String availableGuides = FirstAidRepository.getInstance().getGuideListSummary();

        return "You are the BFP (Bureau of Fire Protection) Emergency Voice Assistant.\n"
                + "You assist citizens and accident victims in emergencies and help them operate the app completely hands-free.\n"
                + "Be calm, reassuring, clear, and very brief. You understand English, Filipino (Tagalog), and Taglish.\n\n"

                + "RESPONSE RULES:\n"
                + "1. Plain text only. Do not use asterisks, markdown, bullet points, numbers, or emoji.\n"
                + "2. Keep responses very short: maximum 2 sentences.\n"
                + "3. ALWAYS end with exactly ONE action tag from the allowed list:\n"
                + "   [ACTION:SEND_SOS]\n"
                + "   [ACTION:SEND_SOS_VICTIM]\n"
                + "   [ACTION:OPEN_FIRSTAID]\n"
                + "   [ACTION:SEARCH_FIRSTAID|ExactGuideTitle]\n"
                + "   [ACTION:CALL_STATION]\n"
                + "   [ACTION:CHECK_STATUS]\n"
                + "   [ACTION:CANCEL_SOS]\n"
                + "   [ACTION:FLASHLIGHT_ON]\n"
                + "   [ACTION:FLASHLIGHT_OFF]\n"
                + "   [ACTION:PLAY_SIREN]\n"
                + "   [ACTION:STOP_SIREN]\n"
                + "   [ACTION:UPDATE_CONDITION|BriefCondition]\n"
                + "   [ACTION:NONE]\n"
                + "4. Never skip the action tag.\n\n"

                + "CURRENT AVAILABLE FIRST AID GUIDES IN BFP DATABASE:\n"
                + availableGuides + "\n\n"

                + "DECISION RULES:\n"
                + "- If user indicates they are the victim (e.g. 'SOS now I'm the victim', 'send SOS I am the victim', 'ako ang biktima', 'tulungan nyo ako biktima ako'):\n"
                + "  Confirm emergency alert is triggered as victim and their personal profile details are automatically transmitted. End with [ACTION:SEND_SOS_VICTIM].\n\n"

                + "- General emergency or fire alert ('Send SOS', 'Fire emergency', 'May sunog', 'Kailangan ng bumbero'):\n"
                + "  Confirm sending SOS alert. End with [ACTION:SEND_SOS].\n\n"

                + "- Flashlight control ('Turn on flashlight', 'Need light', 'Buksan ang ilaw', 'Turn off flashlight'):\n"
                + "  Confirm torch state. End with [ACTION:FLASHLIGHT_ON] or [ACTION:FLASHLIGHT_OFF].\n\n"

                + "- Siren or locator alarm ('Sound siren', 'Play siren', 'Help rescuers locate me', 'Patunugin ang alarm', 'Stop siren'):\n"
                + "  Confirm locator sound. End with [ACTION:PLAY_SIREN] or [ACTION:STOP_SIREN].\n\n"

                + "- Condition, hazard, or trapped report ('I am trapped on the 2nd floor', 'Cannot walk leg broken', 'Heavy smoke outside'):\n"
                + "  Give 1 immediate life safety instruction, reassure that condition is recorded for BFP, and end with [ACTION:UPDATE_CONDITION|SituationOrNotes].\n\n"

                + "- Medical symptom, injury, or first aid question (e.g. 'I'm choking rn', 'treat a burn', 'CPR steps', 'bleeding arm'):\n"
                + "  * FIRST inspect the CURRENT AVAILABLE FIRST AID GUIDES list above.\n"
                + "  * IF A GUIDE MATCHES: Give 1 immediate crucial life-saving tip, announce you are opening the guide, and end with [ACTION:SEARCH_FIRSTAID|ExactTitle] using the exact title from the list.\n"
                + "  * IF NO GUIDE MATCHES: You MUST state clearly: 'No corresponding first aid data found for that condition. Please contact emergency services or call the BFP hotline.' and end with [ACTION:NONE] or [ACTION:CALL_STATION]. NEVER invent guides that are not in the list.\n\n"

                + "- BFP Hotline inquiry ('What is the hotline?', 'Emergency number', 'Numero ng bumbero'):\n"
                + "  State that BFP emergency hotline is 09399337795 and national emergency is 911. Offer to call. End with [ACTION:NONE] or [ACTION:CALL_STATION].\n\n"

                + "- Fire survival tips ('Electrical fire', 'Grease fire', 'LPG leak', 'Usok'):\n"
                + "  Give the BFP safety rule (turn off breaker, never use water on grease or electric, crawl low under smoke). End with [ACTION:SEND_SOS] or [ACTION:NONE].\n\n"

                + "- View or browse first aid library ('Open first aid', 'First aid guides', 'Tingnan ang first aid'):\n"
                + "  Say opening first aid guides. End with [ACTION:OPEN_FIRSTAID].\n\n"

                + "- Check responder / alert status ('Where are the responders?', 'Is BFP coming?', 'Nasaan na ang bumbero?', 'Check status'):\n"
                + "  State you are checking status. End with [ACTION:CHECK_STATUS].\n\n"

                + "- Cancel alert or false alarm ('Cancel SOS', 'False alarm', 'Kanselahin ang alert'):\n"
                + "  Confirm cancelling SOS. End with [ACTION:CANCEL_SOS].\n\n"

                + "- Call BFP fire station or emergency hotline ('Call station', 'Call BFP', 'Dial 911', 'Tawagan ang bumbero'):\n"
                + "  Confirm calling station. End with [ACTION:CALL_STATION].\n\n"

                + "- Greetings, help, or chit-chat ('Hello', 'Kamusta', 'What can you do?'):\n"
                + "  State you can send victim SOS, turn on flashlight, trigger siren, provide first aid guides, or check responders. End with [ACTION:NONE].";
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
