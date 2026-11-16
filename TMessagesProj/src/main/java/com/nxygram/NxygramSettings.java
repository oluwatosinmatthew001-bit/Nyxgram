package com.nxygram;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * NxygramSettings
 * Central persistent settings manager for all Nxygram features.
 * Drop into: TMessagesProj/src/main/java/com/nxygram/NxygramSettings.java
 *
 * Usage: NxygramSettings.init(context);
 *        NxygramSettings.isAntiDeleteEnabled();
 */
public class NxygramSettings {

    private static final String PREFS_NAME = "nxygram_settings";
    private static SharedPreferences prefs;

    // ─── Feature Keys ────────────────────────────────────────────────────────

    // Anti-Delete / Anti-Edit
    public static final String KEY_ANTI_DELETE          = "anti_delete_enabled";
    public static final String KEY_ANTI_EDIT            = "anti_edit_enabled";
    public static final String KEY_ANTI_DELETE_NOTIFY   = "anti_delete_notify";

    // Translation
    public static final String KEY_TRANSLATOR           = "translator_enabled";
    public static final String KEY_TRANS_INCOMING       = "translator_incoming";
    public static final String KEY_TRANS_OUTGOING       = "translator_outgoing";
    public static final String KEY_TRANS_PREVIEW        = "translator_preview_before_send";
    public static final String KEY_TRANS_REPLY_MATCH    = "translator_reply_language_match";
    public static final String KEY_TRANS_GROUP          = "translator_group_enabled";
    public static final String KEY_TRANS_PROVIDER       = "translator_provider";
    public static final String KEY_TRANS_TARGET_LANG    = "translator_target_language";
    public static final String KEY_TRANS_FALLBACK_LANG  = "translator_fallback_language";

    // Ghost Mode
    public static final String KEY_GHOST_MODE           = "ghost_mode_enabled";
    public static final String KEY_GHOST_READ_RECEIPTS  = "ghost_hide_read_receipts";
    public static final String KEY_GHOST_TYPING         = "ghost_hide_typing";
    public static final String KEY_GHOST_ONLINE         = "ghost_hide_online";
    public static final String KEY_GHOST_RECORDING      = "ghost_hide_recording";

    // App Lock
    public static final String KEY_APP_LOCK             = "app_lock_enabled";
    public static final String KEY_APP_LOCK_TYPE        = "app_lock_type"; // pin, biometric
    public static final String KEY_APP_LOCK_TIMER       = "app_lock_timer_seconds";
    public static final String KEY_APP_LOCK_PIN         = "app_lock_pin_hash";

    // Theme
    public static final String KEY_THEME_NAME           = "theme_name";
    public static final String KEY_THEME_AMOLED         = "theme_amoled";
    public static final String KEY_THEME_CUSTOM_FONT    = "theme_custom_font";
    public static final String KEY_THEME_CORNER_RADIUS  = "theme_corner_radius";
    public static final String KEY_THEME_DENSITY        = "theme_density";

    // Smart Messaging
    public static final String KEY_SMART_REPLY          = "smart_reply_enabled";
    public static final String KEY_MSG_SCHEDULER        = "msg_scheduler_enabled";
    public static final String KEY_DRAFT_MANAGER        = "draft_manager_enabled";
    public static final String KEY_QUICK_FORWARD        = "quick_forward_enabled";

    // AI Assistant
    public static final String KEY_AI_ASSISTANT         = "ai_assistant_enabled";
    public static final String KEY_AI_PROVIDER          = "ai_provider";
    public static final String KEY_AI_SUMMARIZER        = "ai_summarizer_enabled";
    public static final String KEY_AI_GRAMMAR           = "ai_grammar_enabled";
    public static final String KEY_AI_TONE              = "ai_tone_enabled";

    // Anti-Spam
    public static final String KEY_ANTI_SPAM            = "anti_spam_enabled";
    public static final String KEY_ANTI_SPAM_LINKS      = "anti_spam_filter_links";

    // Media
    public static final String KEY_MEDIA_AUTO_DOWNLOAD  = "media_auto_download_enabled";
    public static final String KEY_MEDIA_COMPRESS_VIDEO = "media_compress_video";

    // Story Tools
    public static final String KEY_STORY_TOOLS          = "story_tools_enabled";
    public static final String KEY_STORY_SAVE           = "story_save_enabled";

    // ─── Defaults ────────────────────────────────────────────────────────────

    public static void init(Context context) {
        if (prefs == null) {
            prefs = context.getApplicationContext()
                    .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        }
    }

    private static SharedPreferences getPrefs() {
        if (prefs == null) {
            throw new IllegalStateException("NxygramSettings.init(context) must be called first");
        }
        return prefs;
    }

    // ─── Generic Getters/Setters ─────────────────────────────────────────────

    public static boolean getBool(String key, boolean defaultValue) {
        return getPrefs().getBoolean(key, defaultValue);
    }

    public static void setBool(String key, boolean value) {
        getPrefs().edit().putBoolean(key, value).apply();
    }

    public static String getString(String key, String defaultValue) {
        return getPrefs().getString(key, defaultValue);
    }

    public static void setString(String key, String value) {
        getPrefs().edit().putString(key, value).apply();
    }

    public static int getInt(String key, int defaultValue) {
        return getPrefs().getInt(key, defaultValue);
    }

    public static void setInt(String key, int value) {
        getPrefs().edit().putInt(key, value).apply();
    }

    // ─── Feature-Specific Accessors ──────────────────────────────────────────

    // Anti-Delete
    public static boolean isAntiDeleteEnabled()      { return getBool(KEY_ANTI_DELETE, true); }
    public static void    setAntiDelete(boolean v)   { setBool(KEY_ANTI_DELETE, v); }

    public static boolean isAntiEditEnabled()        { return getBool(KEY_ANTI_EDIT, true); }
    public static void    setAntiEdit(boolean v)     { setBool(KEY_ANTI_EDIT, v); }

    public static boolean isAntiDeleteNotify()       { return getBool(KEY_ANTI_DELETE_NOTIFY, true); }
    public static void    setAntiDeleteNotify(boolean v) { setBool(KEY_ANTI_DELETE_NOTIFY, v); }

    // Translation
    public static boolean isTranslatorEnabled()      { return getBool(KEY_TRANSLATOR, false); }
    public static void    setTranslator(boolean v)   { setBool(KEY_TRANSLATOR, v); }

    public static boolean isTransIncoming()          { return getBool(KEY_TRANS_INCOMING, true); }
    public static void    setTransIncoming(boolean v){ setBool(KEY_TRANS_INCOMING, v); }

    public static boolean isTransOutgoing()          { return getBool(KEY_TRANS_OUTGOING, false); }
    public static void    setTransOutgoing(boolean v){ setBool(KEY_TRANS_OUTGOING, v); }

    public static boolean isTransPreview()           { return getBool(KEY_TRANS_PREVIEW, true); }
    public static void    setTransPreview(boolean v) { setBool(KEY_TRANS_PREVIEW, v); }

    public static boolean isTransReplyMatch()        { return getBool(KEY_TRANS_REPLY_MATCH, true); }
    public static void    setTransReplyMatch(boolean v){ setBool(KEY_TRANS_REPLY_MATCH, v); }

    public static boolean isTransGroup()             { return getBool(KEY_TRANS_GROUP, true); }
    public static void    setTransGroup(boolean v)   { setBool(KEY_TRANS_GROUP, v); }

    public static String  getTransProvider()         { return getString(KEY_TRANS_PROVIDER, "libretranslate"); }
    public static void    setTransProvider(String v) { setString(KEY_TRANS_PROVIDER, v); }

    public static String  getTransTargetLang()       { return getString(KEY_TRANS_TARGET_LANG, "en"); }
    public static void    setTransTargetLang(String v){ setString(KEY_TRANS_TARGET_LANG, v); }

    // Ghost Mode
    public static boolean isGhostMode()              { return getBool(KEY_GHOST_MODE, false); }
    public static void    setGhostMode(boolean v)    { setBool(KEY_GHOST_MODE, v); }

    public static boolean isGhostHideTyping()        { return getBool(KEY_GHOST_TYPING, false); }
    public static void    setGhostHideTyping(boolean v){ setBool(KEY_GHOST_TYPING, v); }

    public static boolean isGhostHideOnline()        { return getBool(KEY_GHOST_ONLINE, false); }
    public static void    setGhostHideOnline(boolean v){ setBool(KEY_GHOST_ONLINE, v); }

    // App Lock
    public static boolean isAppLockEnabled()         { return getBool(KEY_APP_LOCK, false); }
    public static void    setAppLock(boolean v)      { setBool(KEY_APP_LOCK, v); }

    public static String  getAppLockType()           { return getString(KEY_APP_LOCK_TYPE, "biometric"); }
    public static void    setAppLockType(String v)   { setString(KEY_APP_LOCK_TYPE, v); }

    public static int     getAppLockTimer()          { return getInt(KEY_APP_LOCK_TIMER, 60); }
    public static void    setAppLockTimer(int secs)  { setInt(KEY_APP_LOCK_TIMER, secs); }

    // Theme
    public static String  getThemeName()             { return getString(KEY_THEME_NAME, "Midnight"); }
    public static void    setThemeName(String v)     { setString(KEY_THEME_NAME, v); }

    public static boolean isAmoledMode()             { return getBool(KEY_THEME_AMOLED, false); }
    public static void    setAmoledMode(boolean v)   { setBool(KEY_THEME_AMOLED, v); }

    public static int     getCornerRadius()          { return getInt(KEY_THEME_CORNER_RADIUS, 12); }
    public static void    setCornerRadius(int dp)    { setInt(KEY_THEME_CORNER_RADIUS, dp); }

    // AI
    public static boolean isAiAssistant()            { return getBool(KEY_AI_ASSISTANT, false); }
    public static void    setAiAssistant(boolean v)  { setBool(KEY_AI_ASSISTANT, v); }

    public static String  getAiProvider()            { return getString(KEY_AI_PROVIDER, ""); }
    public static void    setAiProvider(String v)    { setString(KEY_AI_PROVIDER, v); }

    // Anti-Spam
    public static boolean isAntiSpam()               { return getBool(KEY_ANTI_SPAM, true); }
    public static void    setAntiSpam(boolean v)     { setBool(KEY_ANTI_SPAM, v); }

    // Smart Messaging
    public static boolean isSmartReply()             { return getBool(KEY_SMART_REPLY, true); }
    public static void    setSmartReply(boolean v)   { setBool(KEY_SMART_REPLY, v); }

    public static boolean isMsgScheduler()           { return getBool(KEY_MSG_SCHEDULER, true); }
    public static void    setMsgScheduler(boolean v) { setBool(KEY_MSG_SCHEDULER, v); }

    // Reset everything
    public static void resetAll() {
        getPrefs().edit().clear().apply();
    }
}
