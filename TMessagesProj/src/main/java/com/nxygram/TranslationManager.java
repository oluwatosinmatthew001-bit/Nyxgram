package com.nxygram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.HashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * TranslationManager v2
 * ══════════════════════════════════════════════════════════════
 * All translation powered by PrexzyApi (prexzyapis.com/ai/askgpt5).
 * No external API keys required for translation — built in.
 *
 * Hook points (unchanged from v1):
 *   - Incoming: ChatMessageCell → bind() → translateIncoming()
 *   - Outgoing: ChatActivity → sendMessage() → translateOutgoing() → preview dialog
 *   - Reply:    detect reply language via detectAndCache()
 */
public class TranslationManager {

    private static TranslationManager instance;

    // Per-chat detected language cache: chatId → ISO 639-1 code
    private final Map<Long, String> chatLanguageCache = new HashMap<>();

    // Per-chat translation enabled override
    private final Map<Long, Boolean> chatTranslationEnabled = new HashMap<>();

    // Pattern: skip bot commands, URLs, mentions, code blocks
    private static final Pattern SKIP_PATTERN = Pattern.compile(
            "(^/[a-zA-Z0-9_]+)" +       // bot commands
            "|(https?://\\S+)" +         // URLs
            "|(@[a-zA-Z0-9_]{5,})" +     // mentions
            "|(```[\\s\\S]*?```)"         // code blocks
    );

    public static synchronized TranslationManager getInstance() {
        if (instance == null) instance = new TranslationManager();
        return instance;
    }

    public interface TranslationCallback {
        void onSuccess(String translatedText, String detectedSourceLang);
        void onFailure(String error);
    }

    // ─── Incoming translation ─────────────────────────────────────────────────

    /**
     * Translate an incoming message into the user's target language.
     * Skips if: translator off, chat disabled, same language, or skippable content.
     */
    public void translateIncoming(Context context, long chatId,
                                   String text, TranslationCallback callback) {
        if (!NxygramSettings.isTranslatorEnabled())     return;
        if (!NxygramSettings.isTransIncoming())         return;
        if (!isChatTranslationEnabled(chatId))          return;
        if (shouldSkip(text))                           return;

        String targetLang = NxygramSettings.getTransTargetLang();

        // First detect language, then translate if different
        PrexzyApi.detectLanguage(context, text,
            detectedLang -> {
                detectedLang = detectedLang.trim().toLowerCase();
                chatLanguageCache.put(chatId, detectedLang);

                if (detectedLang.equalsIgnoreCase(targetLang)) {
                    // Already in target language — no translation needed
                    callback.onFailure("same_language");
                    return;
                }

                final String srcLang = detectedLang;
                PrexzyApi.translate(context, text, targetLangName(targetLang),
                    translated -> callback.onSuccess(translated, srcLang),
                    error      -> callback.onFailure(error)
                );
            },
            error -> {
                // Detection failed — translate anyway with auto-detect
                PrexzyApi.translate(context, text, targetLangName(targetLang),
                    translated -> callback.onSuccess(translated, "unknown"),
                    err        -> callback.onFailure(err)
                );
            }
        );
    }

    /**
     * Translate outgoing message.
     * Per spec: ALWAYS returns to caller for preview — NEVER sends silently.
     *
     * targetSenderChatId: the chatId whose cached language is used for reply-matching.
     */
    public void translateOutgoing(Context context, long chatId,
                                   String text, Long replyToChatId,
                                   TranslationCallback callback) {
        if (!NxygramSettings.isTranslatorEnabled())  return;
        if (!NxygramSettings.isTransOutgoing())      return;

        // Determine target language
        String targetLang;
        if (NxygramSettings.isTransReplyMatch()
                && replyToChatId != null
                && chatLanguageCache.containsKey(replyToChatId)) {
            targetLang = chatLanguageCache.get(replyToChatId);
        } else {
            targetLang = NxygramSettings.getTransTargetLang();
        }

        if (targetLang == null || targetLang.isEmpty()) {
            targetLang = NxygramSettings.getString(
                    NxygramSettings.KEY_TRANS_FALLBACK_LANG, "en");
        }

        final String finalTarget = targetLang;

        PrexzyApi.detectLanguage(context, text,
            detectedLang -> {
                detectedLang = detectedLang.trim().toLowerCase();
                if (detectedLang.equalsIgnoreCase(finalTarget)) {
                    callback.onFailure("same_language");
                    return;
                }
                final String src = detectedLang;
                PrexzyApi.translate(context, text, targetLangName(finalTarget),
                    translated -> callback.onSuccess(translated, src),
                    error      -> callback.onFailure(error)
                );
            },
            error -> {
                PrexzyApi.translate(context, text, targetLangName(finalTarget),
                    translated -> callback.onSuccess(translated, "unknown"),
                    err        -> callback.onFailure(err)
                );
            }
        );
    }

    /**
     * Detect and cache the language of an incoming message.
     * Used for reply-language-matching on outgoing messages.
     */
    public void detectAndCache(Context context, long chatId, String text) {
        if (text == null || text.length() < 4) return;
        PrexzyApi.detectLanguage(context, text,
            lang -> { if (lang.length() <= 5) chatLanguageCache.put(chatId, lang.trim()); },
            err  -> { /* silent */ }
        );
    }

    // ─── Per-chat settings ────────────────────────────────────────────────────

    public void setChatTranslationEnabled(long chatId, boolean enabled) {
        chatTranslationEnabled.put(chatId, enabled);
        NxygramSettings.setBool("trans_chat_" + chatId, enabled);
    }

    public boolean isChatTranslationEnabled(long chatId) {
        if (chatTranslationEnabled.containsKey(chatId))
            return Boolean.TRUE.equals(chatTranslationEnabled.get(chatId));
        return NxygramSettings.getBool("trans_chat_" + chatId, true);
    }

    public String getCachedLanguage(long chatId) {
        return chatLanguageCache.getOrDefault(chatId, "unknown");
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private boolean shouldSkip(String text) {
        if (text == null || text.trim().length() < 3) return true;
        Matcher m = SKIP_PATTERN.matcher(text.trim());
        return m.find() && m.start() == 0 && m.end() == text.trim().length();
    }

    /**
     * Converts ISO 639-1 code to full language name for the AI prompt.
     * The AI understands both codes and names, but names are clearer.
     */
    private String targetLangName(String code) {
        if (code == null) return "English";
        switch (code.toLowerCase()) {
            case "en": return "English";
            case "fr": return "French";
            case "es": return "Spanish";
            case "de": return "German";
            case "it": return "Italian";
            case "pt": return "Portuguese";
            case "ru": return "Russian";
            case "zh": return "Chinese (Simplified)";
            case "ja": return "Japanese";
            case "ko": return "Korean";
            case "ar": return "Arabic";
            case "hi": return "Hindi";
            case "yo": return "Yoruba";
            case "ig": return "Igbo";
            case "ha": return "Hausa";
            case "sw": return "Swahili";
            case "tr": return "Turkish";
            case "nl": return "Dutch";
            case "pl": return "Polish";
            case "vi": return "Vietnamese";
            case "th": return "Thai";
            default:   return code.toUpperCase();
        }
    }
}
