package com.nxygram;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.media.AudioAttributes;
import android.net.Uri;
import android.os.Build;

import java.util.HashMap;
import java.util.Map;

/**
 * CustomNotificationManager
 * ══════════════════════════════════════════════════════════════
 * Per-contact/chat custom notification settings:
 *   - Custom sound (from device ringtones or picked file)
 *   - Custom vibration pattern
 *   - LED color (where hardware supports it)
 *   - Priority override (silent, low, default, high, urgent)
 *   - Custom popup style
 *   - Mute schedule per chat
 *
 * Hook in Telegram's NotificationController.java when building
 * a notification for a specific dialog.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/CustomNotificationManager.java
 */
public class CustomNotificationManager {

    private static CustomNotificationManager instance;
    private final Context context;
    private final DbHelper dbHelper;
    private final Map<Long, ChatNotifSettings> cache = new HashMap<>();

    public static synchronized CustomNotificationManager getInstance(Context context) {
        if (instance == null)
            instance = new CustomNotificationManager(context.getApplicationContext());
        return instance;
    }

    private CustomNotificationManager(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
    }

    // ─── Database ──────────────────────────────────────────────────────────

    static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_notif.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS chat_notif_settings ("
                    + "chat_id         INTEGER PRIMARY KEY,"
                    + "sound_uri       TEXT,"        // null = default
                    + "vibration       TEXT,"        // JSON array e.g. [0,250,100,250]
                    + "led_color       INTEGER DEFAULT 0,"   // 0 = default
                    + "priority        INTEGER DEFAULT 0,"   // -2..2
                    + "mute_until      INTEGER DEFAULT 0,"   // unix timestamp, 0 = not muted
                    + "show_preview    INTEGER DEFAULT 1,"
                    + "popup_style     TEXT DEFAULT 'default'"
                    + ")");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Settings model ────────────────────────────────────────────────────

    public static class ChatNotifSettings {
        public long    chatId;
        public String  soundUri;       // null = use system default
        public long[]  vibrationPattern; // null = use system default
        public int     ledColor;        // 0 = default
        public int     priority;        // NotificationManager.IMPORTANCE_*
        public long    muteUntil;       // 0 = not muted
        public boolean showPreview;
        public String  popupStyle;

        public boolean isMuted() {
            return muteUntil > 0 && muteUntil > System.currentTimeMillis() / 1000L;
        }

        public boolean isMutedForever() {
            return muteUntil == Long.MAX_VALUE;
        }
    }

    // ─── CRUD ──────────────────────────────────────────────────────────────

    public void setSettings(ChatNotifSettings s) {
        ContentValues cv = new ContentValues();
        cv.put("chat_id",      s.chatId);
        cv.put("sound_uri",    s.soundUri);
        cv.put("vibration",    vibrationToString(s.vibrationPattern));
        cv.put("led_color",    s.ledColor);
        cv.put("priority",     s.priority);
        cv.put("mute_until",   s.muteUntil);
        cv.put("show_preview", s.showPreview ? 1 : 0);
        cv.put("popup_style",  s.popupStyle);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "chat_notif_settings", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        cache.put(s.chatId, s);
    }

    public ChatNotifSettings getSettings(long chatId) {
        if (cache.containsKey(chatId)) return cache.get(chatId);
        Cursor c = dbHelper.getReadableDatabase().query(
                "chat_notif_settings", null,
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        ChatNotifSettings s = null;
        if (c.moveToFirst()) {
            s = new ChatNotifSettings();
            s.chatId           = chatId;
            s.soundUri         = c.getString(c.getColumnIndexOrThrow("sound_uri"));
            s.vibrationPattern = stringToVibration(c.getString(c.getColumnIndexOrThrow("vibration")));
            s.ledColor         = c.getInt(c.getColumnIndexOrThrow("led_color"));
            s.priority         = c.getInt(c.getColumnIndexOrThrow("priority"));
            s.muteUntil        = c.getLong(c.getColumnIndexOrThrow("mute_until"));
            s.showPreview      = c.getInt(c.getColumnIndexOrThrow("show_preview")) == 1;
            s.popupStyle       = c.getString(c.getColumnIndexOrThrow("popup_style"));
            cache.put(chatId, s);
        }
        c.close();
        return s; // null if no custom settings → use defaults
    }

    public boolean hasCustomSettings(long chatId) {
        return getSettings(chatId) != null;
    }

    public void clearSettings(long chatId) {
        dbHelper.getWritableDatabase().delete("chat_notif_settings",
                "chat_id=?", new String[]{String.valueOf(chatId)});
        cache.remove(chatId);
    }

    // ─── Mute helpers ──────────────────────────────────────────────────────

    public void muteForHours(long chatId, int hours) {
        long until = hours <= 0
                ? Long.MAX_VALUE  // forever
                : (System.currentTimeMillis() / 1000L) + hours * 3600L;
        ChatNotifSettings s = getOrDefault(chatId);
        s.muteUntil = until;
        setSettings(s);
    }

    public void unmute(long chatId) {
        ChatNotifSettings s = getSettings(chatId);
        if (s != null) {
            s.muteUntil = 0;
            setSettings(s);
        }
    }

    public boolean isMuted(long chatId) {
        ChatNotifSettings s = getSettings(chatId);
        return s != null && s.isMuted();
    }

    // ─── Notification channel per chat ─────────────────────────────────────

    /**
     * Create a per-chat notification channel (Android 8+).
     * Each chat with custom settings gets its own channel with its sound.
     *
     * HOOK IN NotificationController.java:
     *   String channelId = CustomNotificationManager.getInstance(context)
     *       .getOrCreateChannel(context, dialogId, chatName);
     *   builder.setChannelId(channelId);
     */
    public String getOrCreateChannel(Context ctx, long chatId, String chatName) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return "default";

        ChatNotifSettings s = getSettings(chatId);
        if (s == null) return "default"; // use Telegram's default channel

        String channelId = "nxygram_chat_" + chatId;
        NotificationManager nm = (NotificationManager)
                ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm == null) return channelId;

        // Check if channel already exists
        if (nm.getNotificationChannel(channelId) != null) return channelId;

        // Map priority to importance
        int importance;
        switch (s.priority) {
            case -2: importance = NotificationManager.IMPORTANCE_MIN;     break;
            case -1: importance = NotificationManager.IMPORTANCE_LOW;     break;
            case  1: importance = NotificationManager.IMPORTANCE_HIGH;    break;
            case  2: importance = NotificationManager.IMPORTANCE_MAX;     break;
            default: importance = NotificationManager.IMPORTANCE_DEFAULT; break;
        }

        NotificationChannel channel = new NotificationChannel(
                channelId,
                chatName + " — Nxygram",
                importance
        );

        // Custom sound
        if (s.soundUri != null && !s.soundUri.isEmpty()) {
            AudioAttributes attrs = new AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_NOTIFICATION)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                    .build();
            channel.setSound(Uri.parse(s.soundUri), attrs);
        } else if (s.soundUri != null && s.soundUri.isEmpty()) {
            // Explicitly silent
            channel.setSound(null, null);
        }

        // Vibration
        if (s.vibrationPattern != null) {
            channel.enableVibration(true);
            channel.setVibrationPattern(s.vibrationPattern);
        }

        // LED
        if (s.ledColor != 0) {
            channel.enableLights(true);
            channel.setLightColor(s.ledColor);
        }

        nm.createNotificationChannel(channel);
        return channelId;
    }

    // ─── Vibration patterns ────────────────────────────────────────────────

    public static final long[] PATTERN_DEFAULT  = null;
    public static final long[] PATTERN_SILENT   = new long[]{};
    public static final long[] PATTERN_SHORT    = {0, 100};
    public static final long[] PATTERN_DOUBLE   = {0, 100, 100, 100};
    public static final long[] PATTERN_LONG     = {0, 500};
    public static final long[] PATTERN_SOS      = {0,100,50,100,50,100,50,300,50,300,50,300,50,100,50,100,50,100};

    private String vibrationToString(long[] pattern) {
        if (pattern == null) return null;
        StringBuilder sb = new StringBuilder();
        for (long v : pattern) {
            if (sb.length() > 0) sb.append(",");
            sb.append(v);
        }
        return sb.toString();
    }

    private long[] stringToVibration(String str) {
        if (str == null || str.isEmpty()) return null;
        try {
            String[] parts = str.split(",");
            long[] pattern = new long[parts.length];
            for (int i = 0; i < parts.length; i++)
                pattern[i] = Long.parseLong(parts[i].trim());
            return pattern;
        } catch (Exception e) {
            return null;
        }
    }

    private ChatNotifSettings getOrDefault(long chatId) {
        ChatNotifSettings s = getSettings(chatId);
        if (s == null) {
            s = new ChatNotifSettings();
            s.chatId      = chatId;
            s.showPreview = true;
            s.popupStyle  = "default";
        }
        return s;
    }
}
