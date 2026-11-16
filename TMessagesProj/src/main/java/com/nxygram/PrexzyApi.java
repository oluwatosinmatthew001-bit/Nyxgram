package com.nxygram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * PrexzyApi
 * ══════════════════════════════════════════════════════════════
 * Single unified client for ALL Nxygram AI-powered features.
 * Endpoint: https://prexzyapis.com/ai/askgpt5
 *
 * Parameters:
 *   prompt     (String)  — required. The instruction or message.
 *   state      (String)  — optional. Conversation history / context.
 *   media      (String)  — optional. Base64 encoded image or file.
 *   model      (String)  — optional. Override model selection.
 *   web_search (boolean) — optional. Enable live web search in response.
 *
 * This replaces:
 *   - NxygramAiManager       (Smart Reply, Summarizer, Grammar, Tone)
 *   - TranslationManager     (all translation providers)
 *   - Any web search calls
 *
 * Usage:
 *   PrexzyApi.call(context)
 *       .prompt("Translate to French: Hello!")
 *       .onResult(text -> ...)
 *       .onError(err -> ...)
 *       .execute();
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/PrexzyApi.java
 */
public class PrexzyApi {

    private static final String ENDPOINT = "https://prexzyapis.com/ai/askgpt5";
    private static final ExecutorService executor = Executors.newFixedThreadPool(4);
    private static final Handler mainHandler = new Handler(Looper.getMainLooper());

    // ─── Callback interfaces ──────────────────────────────────────────────────

    public interface ResultCallback { void onResult(String result); }
    public interface ErrorCallback  { void onError(String error); }

    // ─── Fluent builder ───────────────────────────────────────────────────────

    public static class Request {
        private final Context context;
        private String  prompt;
        private String  state;
        private String  media;
        private String  model;
        private boolean webSearch = false;
        private ResultCallback resultCb;
        private ErrorCallback  errorCb;

        Request(Context context) { this.context = context; }

        public Request prompt(String prompt)     { this.prompt = prompt; return this; }
        public Request state(String state)       { this.state  = state;  return this; }
        public Request media(String base64Media) { this.media  = base64Media; return this; }
        public Request model(String model)       { this.model  = model;  return this; }
        public Request webSearch(boolean enable) { this.webSearch = enable; return this; }
        public Request onResult(ResultCallback cb) { this.resultCb = cb; return this; }
        public Request onError(ErrorCallback cb)   { this.errorCb  = cb; return this; }

        public void execute() {
            if (prompt == null || prompt.trim().isEmpty()) {
                if (errorCb != null) mainHandler.post(() -> errorCb.onError("Prompt is required"));
                return;
            }
            PrexzyApi.execute(this);
        }
    }

    public static Request call(Context context) {
        return new Request(context);
    }

    // ─── Core execution ───────────────────────────────────────────────────────

    private static void execute(Request req) {
        executor.execute(() -> {
            try {
                // Build JSON body
                JSONObject body = new JSONObject();
                body.put("prompt", req.prompt);
                if (req.state     != null && !req.state.isEmpty())
                    body.put("state", req.state);
                if (req.media     != null && !req.media.isEmpty())
                    body.put("media", req.media);
                if (req.model     != null && !req.model.isEmpty())
                    body.put("model", req.model);
                if (req.webSearch)
                    body.put("web_search", true);

                byte[] postData = body.toString().getBytes(StandardCharsets.UTF_8);

                HttpURLConnection conn = (HttpURLConnection)
                        new URL(ENDPOINT).openConnection();
                conn.setRequestMethod("POST");
                conn.setRequestProperty("Content-Type",   "application/json");
                conn.setRequestProperty("Accept",         "application/json");
                conn.setRequestProperty("User-Agent",     "Nxygram-Android/1.0");
                conn.setDoOutput(true);
                conn.setConnectTimeout(15000);
                conn.setReadTimeout(30000);

                try (OutputStream os = conn.getOutputStream()) {
                    os.write(postData);
                }

                int code = conn.getResponseCode();
                BufferedReader reader = new BufferedReader(new InputStreamReader(
                        code >= 200 && code < 300
                                ? conn.getInputStream()
                                : conn.getErrorStream(),
                        StandardCharsets.UTF_8));
                StringBuilder sb = new StringBuilder();
                String line;
                while ((line = reader.readLine()) != null) sb.append(line);
                reader.close();

                String raw = sb.toString();

                if (code != 200) {
                    final String errMsg = "API error " + code + ": " + raw;
                    if (req.errorCb != null)
                        mainHandler.post(() -> req.errorCb.onError(errMsg));
                    return;
                }

                // Parse response — handle both plain string and JSON object
                String result = extractResult(raw);
                final String finalResult = result;
                if (req.resultCb != null)
                    mainHandler.post(() -> req.resultCb.onResult(finalResult));

            } catch (Exception e) {
                if (req.errorCb != null)
                    mainHandler.post(() -> req.errorCb.onError(e.getMessage()));
            }
        });
    }

    /**
     * Extracts the text result from the API response.
     * Handles: plain string, {"result":"..."}, {"response":"..."}, {"text":"..."},
     *          {"choices":[{"message":{"content":"..."}}]} (OpenAI format)
     */
    private static String extractResult(String raw) {
        raw = raw.trim();
        // Plain string (not JSON)
        if (!raw.startsWith("{") && !raw.startsWith("[")) return raw;

        try {
            JSONObject json = new JSONObject(raw);
            if (json.has("result"))   return json.getString("result");
            if (json.has("response")) return json.getString("response");
            if (json.has("text"))     return json.getString("text");
            if (json.has("output"))   return json.getString("output");
            if (json.has("message"))  return json.getString("message");
            // OpenAI-style
            if (json.has("choices")) {
                return json.getJSONArray("choices")
                        .getJSONObject(0)
                        .getJSONObject("message")
                        .getString("content");
            }
            // Fallback: return full JSON as string
            return raw;
        } catch (Exception e) {
            return raw; // return raw if parsing fails
        }
    }

    // ─── Convenience methods ──────────────────────────────────────────────────
    // These are pre-built prompts for every Nxygram feature.
    // All powered by the same single endpoint.

    /**
     * TRANSLATION — incoming message
     * Replaces TranslationManager entirely.
     */
    public static void translate(Context ctx,
                                  String text,
                                  String targetLanguage,
                                  ResultCallback onResult,
                                  ErrorCallback  onError) {
        call(ctx)
            .prompt("Translate the following message to " + targetLanguage + ".\n"
                  + "Return ONLY the translated text, nothing else.\n"
                  + "If the message is already in " + targetLanguage
                  + ", return it unchanged.\n\n"
                  + "Message: " + text)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * LANGUAGE DETECTION
     */
    public static void detectLanguage(Context ctx,
                                       String text,
                                       ResultCallback onResult,
                                       ErrorCallback  onError) {
        call(ctx)
            .prompt("Detect the language of this text. "
                  + "Return ONLY the ISO 639-1 language code (e.g. en, fr, es, de, zh). "
                  + "Nothing else.\n\nText: " + text)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * SMART REPLY SUGGESTIONS
     * Returns a JSON array of 3 short replies.
     */
    public static void smartReply(Context ctx,
                                   String lastMessage,
                                   String conversationContext,
                                   ResultCallback onResult,
                                   ErrorCallback  onError) {
        String context = conversationContext != null
                ? "Conversation context:\n" + conversationContext + "\n\n"
                : "";
        call(ctx)
            .prompt(context
                  + "Given this message: \"" + lastMessage + "\"\n"
                  + "Suggest exactly 3 short, natural reply options.\n"
                  + "Return ONLY a JSON array of 3 strings.\n"
                  + "Example: [\"Sounds good!\", \"Can't make it\", \"Tell me more\"]")
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * CONVERSATION SUMMARIZER
     */
    public static void summarize(Context ctx,
                                  String conversationText,
                                  ResultCallback onResult,
                                  ErrorCallback  onError) {
        call(ctx)
            .prompt("Summarize this conversation in 3-5 bullet points. "
                  + "Be concise. Use • for bullets. No intro text.\n\n"
                  + conversationText)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * GRAMMAR CORRECTION
     */
    public static void fixGrammar(Context ctx,
                                   String text,
                                   ResultCallback onResult,
                                   ErrorCallback  onError) {
        call(ctx)
            .prompt("Fix any grammar, spelling, or punctuation errors in this message. "
                  + "Return ONLY the corrected text, nothing else. "
                  + "If already correct, return it unchanged.\n\n"
                  + "Message: " + text)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * TONE CHANGER
     */
    public static void changeTone(Context ctx,
                                   String text,
                                   String toneDescription,
                                   ResultCallback onResult,
                                   ErrorCallback  onError) {
        call(ctx)
            .prompt("Rewrite this message to be " + toneDescription + ". "
                  + "Keep the same meaning. Return ONLY the rewritten message.\n\n"
                  + "Message: " + text)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * WEB SEARCH — live search results baked into AI response
     */
    public static void webSearch(Context ctx,
                                  String query,
                                  ResultCallback onResult,
                                  ErrorCallback  onError) {
        call(ctx)
            .prompt(query)
            .webSearch(true)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * MEDIA ANALYSIS — describe or analyse an image
     * Pass base64-encoded image string as media param.
     */
    public static void analyzeMedia(Context ctx,
                                     String base64Image,
                                     String instruction,
                                     ResultCallback onResult,
                                     ErrorCallback  onError) {
        call(ctx)
            .prompt(instruction != null ? instruction : "Describe this image in detail.")
            .media(base64Image)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * GENERAL AI CHAT with conversation state
     * Pass prior messages as state to maintain context.
     */
    public static void chat(Context ctx,
                             String userMessage,
                             String conversationState,
                             ResultCallback onResult,
                             ErrorCallback  onError) {
        call(ctx)
            .prompt(userMessage)
            .state(conversationState)
            .onResult(onResult)
            .onError(onError)
            .execute();
    }

    /**
     * MESSAGE IMPROVEMENT — rewrite/improve a draft
     */
    public static void improve(Context ctx,
                                String text,
                                String instruction,
                                ResultCallback onResult,
                                ErrorCallback  onError) {
        call(ctx)
            .prompt((instruction != null ? instruction + "\n\n" : "Improve this message:\n\n")
                  + text + "\n\nReturn ONLY the improved text.")
            .onResult(onResult)
            .onError(onError)
            .execute();
    }
}
