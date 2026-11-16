package com.nxygram;

import android.content.Context;

import org.json.JSONArray;

import java.util.List;

/**
 * NxygramAiManager v2
 * ══════════════════════════════════════════════════════════════
 * All AI features powered by PrexzyApi — no external API keys needed.
 * Endpoint: https://prexzyapis.com/ai/askgpt5
 *
 * Features:
 *   - Smart Reply
 *   - Conversation Summarizer
 *   - Grammar Correction
 *   - Tone Changer
 *   - Writing Assistant / Improvement
 *   - Web Search (passes web_search=true to API)
 *   - Media / Image Analysis
 *   - In-chat AI assistant
 */
public class NxygramAiManager {

    private static NxygramAiManager instance;

    public static synchronized NxygramAiManager getInstance() {
        if (instance == null) instance = new NxygramAiManager();
        return instance;
    }

    public interface AiCallback {
        void onResult(String result);
        void onError(String error);
    }

    // ─── Smart Reply ──────────────────────────────────────────────────────────

    /**
     * Returns a JSON array string of 3 reply suggestions.
     * e.g. ["Sure!", "On my way", "Can't make it"]
     */
    public void suggestReplies(Context context,
                                String lastMessage,
                                String chatContext,
                                AiCallback callback) {
        if (!NxygramSettings.isSmartReply()) return;

        PrexzyApi.smartReply(context, lastMessage, chatContext,
            result -> {
                // Parse and validate JSON array
                try {
                    String cleaned = result.trim();
                    int start = cleaned.indexOf('[');
                    int end   = cleaned.lastIndexOf(']');
                    if (start >= 0 && end > start)
                        cleaned = cleaned.substring(start, end + 1);
                    new JSONArray(cleaned); // validate
                    callback.onResult(cleaned);
                } catch (Exception e) {
                    // Fallback: wrap plain text into array
                    callback.onResult("[\"" + result.trim()
                            .replace("\"", "\\\"")
                            .replace("\n", " ") + "\"]");
                }
            },
            callback::onError
        );
    }

    // ─── Summarizer ───────────────────────────────────────────────────────────

    /**
     * Summarize a list of messages ("Name: text") into bullet points.
     */
    public void summarizeConversation(Context context,
                                       List<String> messages,
                                       AiCallback callback) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_AI_SUMMARIZER, false)) return;
        if (messages == null || messages.isEmpty()) {
            callback.onError("No messages"); return;
        }

        // Last 50 messages max
        int start = Math.max(0, messages.size() - 50);
        StringBuilder sb = new StringBuilder();
        for (int i = start; i < messages.size(); i++) sb.append(messages.get(i)).append("\n");

        PrexzyApi.summarize(context, sb.toString(), callback::onResult, callback::onError);
    }

    // ─── Grammar Correction ───────────────────────────────────────────────────

    public void correctGrammar(Context context, String text, AiCallback callback) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_AI_GRAMMAR, false)) return;
        PrexzyApi.fixGrammar(context, text, callback::onResult, callback::onError);
    }

    // ─── Tone Changer ─────────────────────────────────────────────────────────

    public enum Tone { FORMAL, CASUAL, FRIENDLY, PROFESSIONAL, CONCISE, ELABORATE }

    public void changeTone(Context context, String text, Tone tone, AiCallback callback) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_AI_TONE, false)) return;
        String desc;
        switch (tone) {
            case FORMAL:       desc = "formal and polite";           break;
            case CASUAL:       desc = "casual and relaxed";          break;
            case FRIENDLY:     desc = "warm and friendly";           break;
            case PROFESSIONAL: desc = "professional and clear";      break;
            case CONCISE:      desc = "shorter and more concise";    break;
            case ELABORATE:    desc = "more detailed and elaborate"; break;
            default:           desc = "natural";
        }
        PrexzyApi.changeTone(context, text, desc, callback::onResult, callback::onError);
    }

    // ─── Writing Assistant ────────────────────────────────────────────────────

    public void improveText(Context context, String text,
                             String instruction, AiCallback callback) {
        PrexzyApi.improve(context, text, instruction, callback::onResult, callback::onError);
    }

    // ─── Web Search ───────────────────────────────────────────────────────────

    /**
     * Ask the AI a question with live web search enabled.
     * Results are AI-synthesized from live web content.
     */
    public void searchWeb(Context context, String query, AiCallback callback) {
        PrexzyApi.webSearch(context, query, callback::onResult, callback::onError);
    }

    // ─── Media Analysis ───────────────────────────────────────────────────────

    /**
     * Analyse an image — describe it, extract text, answer questions about it.
     * imageBase64: base64-encoded image string (from Bitmap.compress → Base64.encode)
     */
    public void analyzeImage(Context context, String imageBase64,
                              String instruction, AiCallback callback) {
        PrexzyApi.analyzeMedia(context, imageBase64, instruction,
                callback::onResult, callback::onError);
    }

    // ─── In-chat AI Assistant ─────────────────────────────────────────────────

    /**
     * General conversational AI with memory (state = prior conversation).
     * state format: "User: ...\nAssistant: ...\nUser: ..."
     */
    public void chat(Context context, String message,
                     String state, AiCallback callback) {
        if (!NxygramSettings.isAiAssistant()) return;
        PrexzyApi.chat(context, message, state, callback::onResult, callback::onError);
    }
}
