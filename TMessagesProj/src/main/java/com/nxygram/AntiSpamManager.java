package com.nxygram;

import android.content.Context;
import android.content.SharedPreferences;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * AntiSpamManager
 * Local spam filtering, suspicious message detection, link safety checks.
 *
 * Hook in MessagesController / ChatActivity when a new message arrives:
 *   AntiSpamManager.SpamResult result =
 *       AntiSpamManager.getInstance(context).check(message, senderId, chatId);
 *   if (result.isSpam) {
 *       // hide message, show warning badge, or move to spam folder
 *   }
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/AntiSpamManager.java
 */
public class AntiSpamManager {

    private static AntiSpamManager instance;
    private final Context context;

    // ─── Suspicious link patterns ──────────────────────────────────────────────
    private static final Pattern URL_PATTERN = Pattern.compile(
            "https?://[\\w\\-\\.]+\\.[a-z]{2,}(/[^\\s]*)?",
            Pattern.CASE_INSENSITIVE);

    private static final Set<String> SUSPICIOUS_TLDS = new HashSet<>(Arrays.asList(
            ".tk", ".ml", ".ga", ".cf", ".gq", ".xyz", ".top", ".click",
            ".download", ".loan", ".win", ".racing", ".date", ".review"
    ));

    // Keywords that frequently appear in spam/scam messages
    private static final String[] SPAM_KEYWORDS = {
        "you have been selected", "click here to claim",
        "free crypto", "bitcoin giveaway", "send btc",
        "investment opportunity", "double your money",
        "you won", "congratulations you have",
        "verify your account immediately", "your account will be suspended",
        "click this link now", "limited time offer expires",
        "work from home earn", "make money fast"
    };

    // ─── Result ───────────────────────────────────────────────────────────────

    public static class SpamResult {
        public final boolean isSpam;
        public final SpamReason reason;
        public final float confidence; // 0.0 - 1.0
        public final String detail;

        SpamResult(boolean isSpam, SpamReason reason, float confidence, String detail) {
            this.isSpam     = isSpam;
            this.reason     = reason;
            this.confidence = confidence;
            this.detail     = detail;
        }
        public static SpamResult clean() {
            return new SpamResult(false, SpamReason.NONE, 0f, null);
        }
    }

    public enum SpamReason {
        NONE, KEYWORD, SUSPICIOUS_LINK, BLOCKLISTED_SENDER,
        EXCESSIVE_LINKS, KNOWN_SCAM_PATTERN
    }

    // ─── Singleton ────────────────────────────────────────────────────────────

    public static synchronized AntiSpamManager getInstance(Context context) {
        if (instance == null)
            instance = new AntiSpamManager(context.getApplicationContext());
        return instance;
    }

    private AntiSpamManager(Context context) { this.context = context; }

    // ─── Main Check ───────────────────────────────────────────────────────────

    public SpamResult check(String messageText, long senderId, long chatId) {
        if (!NxygramSettings.isAntiSpam())     return SpamResult.clean();
        if (messageText == null || messageText.isEmpty()) return SpamResult.clean();

        // 1. Blocklist check
        if (isBlocklisted(senderId)) {
            return new SpamResult(true, SpamReason.BLOCKLISTED_SENDER, 1.0f,
                    "Sender is on your blocklist");
        }

        String lower = messageText.toLowerCase();

        // 2. Known scam keyword patterns
        for (String kw : SPAM_KEYWORDS) {
            if (lower.contains(kw)) {
                return new SpamResult(true, SpamReason.KEYWORD, 0.85f,
                        "Suspicious phrase: \"" + kw + "\"");
            }
        }

        // 3. Suspicious link check
        if (NxygramSettings.getBool(NxygramSettings.KEY_ANTI_SPAM_LINKS, true)) {
            Matcher m = URL_PATTERN.matcher(messageText);
            int linkCount = 0;
            while (m.find()) {
                linkCount++;
                String url = m.group().toLowerCase();
                for (String tld : SUSPICIOUS_TLDS) {
                    if (url.contains(tld)) {
                        return new SpamResult(true, SpamReason.SUSPICIOUS_LINK, 0.75f,
                                "Suspicious domain in link: " + m.group());
                    }
                }
            }
            if (linkCount > 3) {
                return new SpamResult(true, SpamReason.EXCESSIVE_LINKS, 0.65f,
                        linkCount + " links in one message");
            }
        }

        // 4. All-caps shouting + multiple exclamation marks (weak signal)
        if (messageText.length() > 20) {
            long upperCount = messageText.chars().filter(Character::isUpperCase).count();
            long totalLetters = messageText.chars().filter(Character::isLetter).count();
            long exclamations = messageText.chars().filter(c -> c == '!').count();
            if (totalLetters > 10 && (float) upperCount / totalLetters > 0.75f
                    && exclamations > 2) {
                return new SpamResult(true, SpamReason.KNOWN_SCAM_PATTERN, 0.55f,
                        "Excessive caps and exclamation marks");
            }
        }

        return SpamResult.clean();
    }

    // ─── Blocklist Management ─────────────────────────────────────────────────

    private static final String PREF_BLOCKLIST = "antispam_blocklist";

    public void blockSender(long senderId) {
        Set<String> list = getBlocklist();
        list.add(String.valueOf(senderId));
        saveBlocklist(list);
    }

    public void unblockSender(long senderId) {
        Set<String> list = getBlocklist();
        list.remove(String.valueOf(senderId));
        saveBlocklist(list);
    }

    public boolean isBlocklisted(long senderId) {
        return getBlocklist().contains(String.valueOf(senderId));
    }

    private Set<String> getBlocklist() {
        return new HashSet<>(context.getSharedPreferences("nxygram_settings",
                Context.MODE_PRIVATE).getStringSet(PREF_BLOCKLIST, new HashSet<>()));
    }

    private void saveBlocklist(Set<String> list) {
        context.getSharedPreferences("nxygram_settings", Context.MODE_PRIVATE)
               .edit().putStringSet(PREF_BLOCKLIST, list).apply();
    }

    // ─── Allowlist ────────────────────────────────────────────────────────────

    private static final String PREF_ALLOWLIST = "antispam_allowlist";

    public void allowSender(long senderId) {
        Set<String> list = getAllowlist();
        list.add(String.valueOf(senderId));
        context.getSharedPreferences("nxygram_settings", Context.MODE_PRIVATE)
               .edit().putStringSet(PREF_ALLOWLIST, list).apply();
    }

    private Set<String> getAllowlist() {
        return new HashSet<>(context.getSharedPreferences("nxygram_settings",
                Context.MODE_PRIVATE).getStringSet(PREF_ALLOWLIST, new HashSet<>()));
    }

    public boolean isAllowlisted(long senderId) {
        return getAllowlist().contains(String.valueOf(senderId));
    }

    // ─── Link Safety Check (for tap-to-open) ─────────────────────────────────

    /**
     * Call before opening a URL — returns true if the URL looks safe.
     * Hook in Telegram's browser opener / link handler.
     */
    public boolean isLinkSafe(String url) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_ANTI_SPAM_LINKS, true)) return true;
        if (url == null || url.isEmpty()) return true;
        String lower = url.toLowerCase();
        for (String tld : SUSPICIOUS_TLDS) {
            if (lower.contains(tld)) return false;
        }
        return true;
    }

    /**
     * Returns a warning message if the link looks suspicious, null if safe.
     * Use to show a confirmation dialog before opening.
     */
    public String getLinkWarning(String url) {
        if (isLinkSafe(url)) return null;
        return "⚠️ This link uses a domain associated with spam or phishing.\n\n"
                + url + "\n\nAre you sure you want to open it?";
    }
}
