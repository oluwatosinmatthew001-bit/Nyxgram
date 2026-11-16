package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * AutoReplyManager
 * ══════════════════════════════════════════════════════════════
 * Features:
 *   1. Away mode — auto-reply to all incoming messages when busy
 *   2. First-time greeting — special message when someone DMs you for the first time
 *   3. Keyword triggers — reply with a specific message when a keyword is received
 *   4. Scheduled away — set away hours (e.g. 11pm–8am)
 *   5. Per-chat exclusions — disable auto-reply for specific chats
 *   6. AI-powered replies — use PrexzyApi to generate context-aware replies
 *
 * Hook in MessagesController.java when a new message arrives:
 *   AutoReplyManager.getInstance(context).onMessageReceived(msg, accountId);
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/AutoReplyManager.java
 */
public class AutoReplyManager {

    private static AutoReplyManager instance;
    private final Context context;
    private final DbHelper dbHelper;

    // Track who we've already sent away replies to (don't spam)
    private final Map<Long, Long> lastReplySentAt = new HashMap<>(); // userId → timestamp
    // Track first-time chatters we've greeted
    private final Set<Long> greetedUsers = new HashSet<>();

    private static final long AWAY_REPLY_COOLDOWN_MS = 60 * 60 * 1000L; // 1 hour

    public static synchronized AutoReplyManager getInstance(Context context) {
        if (instance == null)
            instance = new AutoReplyManager(context.getApplicationContext());
        return instance;
    }

    private AutoReplyManager(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
        loadGreetedUsers();
    }

    // ─── Database ──────────────────────────────────────────────────────────

    static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_autoreply.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            // Keyword rules
            db.execSQL("CREATE TABLE IF NOT EXISTS keyword_rules ("
                    + "id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "keyword     TEXT NOT NULL,"
                    + "reply       TEXT NOT NULL,"
                    + "is_regex    INTEGER DEFAULT 0,"
                    + "enabled     INTEGER DEFAULT 1,"
                    + "case_sensitive INTEGER DEFAULT 0"
                    + ")");
            // First-time greeted users
            db.execSQL("CREATE TABLE IF NOT EXISTS greeted_users ("
                    + "user_id     INTEGER PRIMARY KEY,"
                    + "greeted_at  INTEGER NOT NULL"
                    + ")");
            // Per-chat exclusions
            db.execSQL("CREATE TABLE IF NOT EXISTS excluded_chats ("
                    + "chat_id     INTEGER PRIMARY KEY,"
                    + "reason      TEXT"
                    + ")");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Data models ───────────────────────────────────────────────────────

    public static class KeywordRule {
        public long   id;
        public String keyword;
        public String reply;
        public boolean isRegex;
        public boolean enabled;
        public boolean caseSensitive;
    }

    // ─── Main hook ─────────────────────────────────────────────────────────

    /**
     * Call when a new message arrives in a private chat (not groups).
     * Returns the reply text to send, or null if no reply needed.
     *
     * HOOK IN MessagesController.java, in processUpdateArray()
     * when handling TL_updateNewMessage for private chats:
     *
     *   if (messageObject.isPrivate() && !messageObject.isOut()) {
     *       String reply = AutoReplyManager.getInstance(context)
     *           .onMessageReceived(
     *               messageObject.getFromChatId(),
     *               messageObject.messageOwner.message,
     *               messageObject.messageOwner.date,
     *               currentAccount
     *           );
     *       if (reply != null) {
     *           SendMessagesHelper.getInstance(currentAccount)
     *               .sendMessage(reply, messageObject.getFromChatId(), ...);
     *       }
     *   }
     */
    public String onMessageReceived(long fromUserId, String messageText,
                                     long timestamp, int accountId) {
        // Skip if not in a private chat context — handled by caller
        if (messageText == null) return null;

        // Skip excluded chats
        if (isChatExcluded(fromUserId)) return null;

        // 1. First-time greeting (highest priority)
        if (isFirstTimeChatter(fromUserId)) {
            String greeting = getFirstTimeGreeting();
            if (greeting != null && !greeting.isEmpty()) {
                markUserGreeted(fromUserId);
                return greeting;
            }
        }

        // 2. Keyword rules (always active, regardless of away mode)
        String keywordReply = checkKeywords(messageText);
        if (keywordReply != null) return keywordReply;

        // 3. Away mode
        if (isAwayActive()) {
            if (!hasRecentlyReplied(fromUserId)) {
                recordReplySent(fromUserId);
                String awayMsg = getAwayMessage();
                if (awayMsg != null && !awayMsg.isEmpty()) return awayMsg;
            }
        }

        return null;
    }

    // ─── Away mode ─────────────────────────────────────────────────────────

    public boolean isAwayActive() {
        if (!NxygramSettings.getBool("autoreply_away_enabled", false)) return false;
        // Check schedule if enabled
        if (NxygramSettings.getBool("autoreply_schedule_enabled", false)) {
            return isWithinSchedule();
        }
        return true;
    }

    public void setAwayEnabled(boolean enabled) {
        NxygramSettings.setBool("autoreply_away_enabled", enabled);
    }

    public void setAwayMessage(String message) {
        NxygramSettings.setString("autoreply_away_message", message);
    }

    public String getAwayMessage() {
        return NxygramSettings.getString("autoreply_away_message",
                "I'm currently away. I'll get back to you as soon as I can!");
    }

    // ─── Schedule ──────────────────────────────────────────────────────────

    public void setSchedule(int startHour, int endHour) {
        NxygramSettings.setInt("autoreply_start_hour", startHour);
        NxygramSettings.setInt("autoreply_end_hour",   endHour);
    }

    private boolean isWithinSchedule() {
        int startHour = NxygramSettings.getInt("autoreply_start_hour", 23);
        int endHour   = NxygramSettings.getInt("autoreply_end_hour",    8);
        java.util.Calendar cal = java.util.Calendar.getInstance();
        int hour = cal.get(java.util.Calendar.HOUR_OF_DAY);
        if (startHour > endHour) {
            // Overnight schedule (e.g. 23:00 to 08:00)
            return hour >= startHour || hour < endHour;
        } else {
            return hour >= startHour && hour < endHour;
        }
    }

    // ─── First-time greeting ───────────────────────────────────────────────

    public boolean isFirstTimeChatter(long userId) {
        if (!NxygramSettings.getBool("autoreply_greeting_enabled", true)) return false;
        if (greetedUsers.contains(userId)) return false;
        // Check DB
        Cursor c = dbHelper.getReadableDatabase().query(
                "greeted_users", new String[]{"user_id"},
                "user_id=?", new String[]{String.valueOf(userId)},
                null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        return !exists;
    }

    public void setFirstTimeGreeting(String message) {
        NxygramSettings.setString("autoreply_greeting_message", message);
    }

    public String getFirstTimeGreeting() {
        return NxygramSettings.getString("autoreply_greeting_message",
                "Hey! 👋 Thanks for reaching out. I'll reply as soon as I can.");
    }

    private void markUserGreeted(long userId) {
        greetedUsers.add(userId);
        ContentValues cv = new ContentValues();
        cv.put("user_id",    userId);
        cv.put("greeted_at", System.currentTimeMillis() / 1000L);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "greeted_users", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void loadGreetedUsers() {
        Cursor c = dbHelper.getReadableDatabase().query(
                "greeted_users", new String[]{"user_id"},
                null, null, null, null, null);
        while (c.moveToNext()) greetedUsers.add(c.getLong(0));
        c.close();
    }

    // ─── Keyword rules ─────────────────────────────────────────────────────

    public long addKeywordRule(String keyword, String reply,
                                boolean isRegex, boolean caseSensitive) {
        ContentValues cv = new ContentValues();
        cv.put("keyword",        keyword);
        cv.put("reply",          reply);
        cv.put("is_regex",       isRegex ? 1 : 0);
        cv.put("enabled",        1);
        cv.put("case_sensitive", caseSensitive ? 1 : 0);
        return dbHelper.getWritableDatabase().insert("keyword_rules", null, cv);
    }

    public void deleteKeywordRule(long id) {
        dbHelper.getWritableDatabase().delete("keyword_rules",
                "id=?", new String[]{String.valueOf(id)});
    }

    public void setKeywordRuleEnabled(long id, boolean enabled) {
        ContentValues cv = new ContentValues();
        cv.put("enabled", enabled ? 1 : 0);
        dbHelper.getWritableDatabase().update("keyword_rules", cv,
                "id=?", new String[]{String.valueOf(id)});
    }

    public List<KeywordRule> getKeywordRules() {
        List<KeywordRule> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "keyword_rules", null, null, null, null, null, "id ASC");
        while (c.moveToNext()) {
            KeywordRule r    = new KeywordRule();
            r.id             = c.getLong(c.getColumnIndexOrThrow("id"));
            r.keyword        = c.getString(c.getColumnIndexOrThrow("keyword"));
            r.reply          = c.getString(c.getColumnIndexOrThrow("reply"));
            r.isRegex        = c.getInt(c.getColumnIndexOrThrow("is_regex")) == 1;
            r.enabled        = c.getInt(c.getColumnIndexOrThrow("enabled")) == 1;
            r.caseSensitive  = c.getInt(c.getColumnIndexOrThrow("case_sensitive")) == 1;
            list.add(r);
        }
        c.close();
        return list;
    }

    private String checkKeywords(String message) {
        List<KeywordRule> rules = getKeywordRules();
        for (KeywordRule rule : rules) {
            if (!rule.enabled) continue;
            try {
                boolean matched;
                if (rule.isRegex) {
                    int flags = rule.caseSensitive ? 0 : Pattern.CASE_INSENSITIVE;
                    matched = Pattern.compile(rule.keyword, flags).matcher(message).find();
                } else {
                    matched = rule.caseSensitive
                            ? message.contains(rule.keyword)
                            : message.toLowerCase().contains(rule.keyword.toLowerCase());
                }
                if (matched) return rule.reply;
            } catch (Exception ignored) {}
        }
        return null;
    }

    // ─── AI-powered auto-reply ─────────────────────────────────────────────

    /**
     * Generate a smart auto-reply using PrexzyApi.
     * Call this instead of a static away message if AI is enabled.
     *
     * Usage:
     *   AutoReplyManager.getInstance(ctx).generateAiReply(
     *       context, incomingMessage, senderName,
     *       reply -> SendMessagesHelper...sendMessage(reply, chatId, ...)
     *   );
     */
    public void generateAiReply(Context ctx, String incomingMessage,
                                  String senderName, AiReplyCallback cb) {
        if (!NxygramSettings.isAiAssistant()) {
            cb.onReply(getAwayMessage());
            return;
        }
        PrexzyApi.call(ctx)
            .prompt("Generate a brief, polite auto-reply to this message from "
                  + senderName + ": \"" + incomingMessage + "\"\n\n"
                  + "Context: I am currently away/busy. Keep reply under 2 sentences. "
                  + "Sound natural, not like a bot. Reply only with the message text.")
            .onResult(cb::onReply)
            .onError(e -> cb.onReply(getAwayMessage()))
            .execute();
    }

    public interface AiReplyCallback { void onReply(String replyText); }

    // ─── Exclusions ────────────────────────────────────────────────────────

    public void excludeChat(long chatId, String reason) {
        ContentValues cv = new ContentValues();
        cv.put("chat_id", chatId);
        cv.put("reason",  reason);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "excluded_chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    public void includeChat(long chatId) {
        dbHelper.getWritableDatabase().delete("excluded_chats",
                "chat_id=?", new String[]{String.valueOf(chatId)});
    }

    public boolean isChatExcluded(long chatId) {
        Cursor c = dbHelper.getReadableDatabase().query(
                "excluded_chats", new String[]{"chat_id"},
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        boolean ex = c.moveToFirst(); c.close(); return ex;
    }

    // ─── Cooldown ──────────────────────────────────────────────────────────

    private boolean hasRecentlyReplied(long userId) {
        Long last = lastReplySentAt.get(userId);
        if (last == null) return false;
        return (System.currentTimeMillis() - last) < AWAY_REPLY_COOLDOWN_MS;
    }

    private void recordReplySent(long userId) {
        lastReplySentAt.put(userId, System.currentTimeMillis());
    }

    public void resetCooldown(long userId) {
        lastReplySentAt.remove(userId);
    }

    public void resetAllCooldowns() {
        lastReplySentAt.clear();
    }
}
