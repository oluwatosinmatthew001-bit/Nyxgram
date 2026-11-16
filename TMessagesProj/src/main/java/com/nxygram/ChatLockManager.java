package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * ChatLockManager
 * ══════════════════════════════════════════════════════════════
 * Features:
 *   1. Lock individual chats with PIN or biometrics
 *   2. Hidden chats folder — locked chats disappear from chat list
 *      and only appear when you enter the folder PIN
 *   3. Hide message previews in notifications for locked chats
 *   4. Fake "No messages" state for locked chats in chat list
 *
 * Hook points:
 *   - ChatListActivity → filter locked chats from list
 *   - ChatActivity.onCreate() → check if chat is locked before opening
 *   - NotificationController → suppress preview for locked chats
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/ChatLockManager.java
 */
public class ChatLockManager {

    private static ChatLockManager instance;
    private final Context context;
    private final DbHelper dbHelper;

    // In-memory set of currently unlocked chats (this session only)
    private final Set<Long> unlockedThisSession = new HashSet<>();

    // Hidden folder unlock state
    private boolean hiddenFolderUnlocked = false;

    public static synchronized ChatLockManager getInstance(Context context) {
        if (instance == null)
            instance = new ChatLockManager(context.getApplicationContext());
        return instance;
    }

    private ChatLockManager(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
    }

    // ─── Database ──────────────────────────────────────────────────────────

    static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_chatlocks.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS locked_chats ("
                    + "chat_id     INTEGER PRIMARY KEY,"
                    + "pin_hash    TEXT,"
                    + "is_hidden   INTEGER DEFAULT 0,"
                    + "lock_type   TEXT DEFAULT 'pin'," // pin, biometric, app_lock
                    + "added_at    INTEGER NOT NULL"
                    + ")");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Lock a chat ───────────────────────────────────────────────────────

    /**
     * Lock a chat with PIN or biometric.
     * @param chatId   the dialog ID
     * @param pin      4-6 digit PIN (null = use app lock PIN)
     * @param hidden   true = hide from chat list entirely
     * @param lockType "pin" | "biometric" | "app_lock"
     */
    public void lockChat(long chatId, String pin, boolean hidden, String lockType) {
        ContentValues cv = new ContentValues();
        cv.put("chat_id",   chatId);
        cv.put("pin_hash",  pin != null ? hashPin(pin) : "");
        cv.put("is_hidden", hidden ? 1 : 0);
        cv.put("lock_type", lockType);
        cv.put("added_at",  System.currentTimeMillis() / 1000L);
        dbHelper.getWritableDatabase().insertWithOnConflict(
                "locked_chats", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
        unlockedThisSession.remove(chatId);
    }

    public void unlockChatPermanently(long chatId) {
        dbHelper.getWritableDatabase().delete("locked_chats",
                "chat_id=?", new String[]{String.valueOf(chatId)});
        unlockedThisSession.remove(chatId);
    }

    // ─── Check lock state ──────────────────────────────────────────────────

    public boolean isChatLocked(long chatId) {
        if (unlockedThisSession.contains(chatId)) return false;
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"chat_id"},
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        boolean locked = c.moveToFirst();
        c.close();
        return locked;
    }

    public boolean isChatHidden(long chatId) {
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"is_hidden"},
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        boolean hidden = false;
        if (c.moveToFirst()) hidden = c.getInt(0) == 1;
        c.close();
        return hidden;
    }

    public String getLockType(long chatId) {
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"lock_type"},
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        String type = "pin";
        if (c.moveToFirst()) type = c.getString(0);
        c.close();
        return type;
    }

    // ─── Verify PIN ────────────────────────────────────────────────────────

    public boolean verifyChatPin(long chatId, String enteredPin) {
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"pin_hash"},
                "chat_id=?", new String[]{String.valueOf(chatId)},
                null, null, null);
        String storedHash = null;
        if (c.moveToFirst()) storedHash = c.getString(0);
        c.close();

        if (storedHash == null || storedHash.isEmpty()) {
            // Use app lock PIN
            return AppLockManager.getInstance().verifyPin(enteredPin);
        }
        return storedHash.equals(hashPin(enteredPin));
    }

    public void markUnlockedThisSession(long chatId) {
        unlockedThisSession.add(chatId);
    }

    public void lockAllChatsThisSession() {
        unlockedThisSession.clear();
    }

    // ─── Hidden folder ─────────────────────────────────────────────────────

    /**
     * Returns true if hidden chats should be visible right now.
     * Only true after user has unlocked the hidden folder this session.
     */
    public boolean isHiddenFolderVisible() {
        return hiddenFolderUnlocked;
    }

    public void setHiddenFolderUnlocked(boolean unlocked) {
        hiddenFolderUnlocked = unlocked;
    }

    public List<Long> getHiddenChatIds() {
        List<Long> ids = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"chat_id"},
                "is_hidden=1", null, null, null, null);
        while (c.moveToNext()) ids.add(c.getLong(0));
        c.close();
        return ids;
    }

    public List<Long> getAllLockedChatIds() {
        List<Long> ids = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "locked_chats", new String[]{"chat_id"},
                null, null, null, null, null);
        while (c.moveToNext()) ids.add(c.getLong(0));
        c.close();
        return ids;
    }

    // ─── Hidden folder PIN ─────────────────────────────────────────────────

    private static final String HIDDEN_FOLDER_PIN_KEY = "hidden_folder_pin_hash";

    public void setHiddenFolderPin(String pin) {
        NxygramSettings.setString(HIDDEN_FOLDER_PIN_KEY, hashPin(pin));
    }

    public boolean verifyHiddenFolderPin(String pin) {
        String stored = NxygramSettings.getString(HIDDEN_FOLDER_PIN_KEY, "");
        if (stored.isEmpty()) {
            // Fall back to app lock PIN
            return AppLockManager.getInstance().verifyPin(pin);
        }
        return stored.equals(hashPin(pin));
    }

    public boolean hasHiddenFolderPin() {
        return !NxygramSettings.getString(HIDDEN_FOLDER_PIN_KEY, "").isEmpty();
    }

    // ─── Notification suppression ──────────────────────────────────────────

    /**
     * Returns true if the notification preview for this chat should be hidden.
     * Hook in Telegram's NotificationController where notification text is set:
     *
     *   String notifText = ChatLockManager.getInstance(context)
     *       .isChatLocked(dialogId)
     *           ? "New message"  // hide content
     *           : originalMessageText;
     */
    public String getNotificationText(long chatId, String originalText) {
        if (isChatLocked(chatId)) return "New message";
        return originalText;
    }

    // ─── Chat list filtering ───────────────────────────────────────────────

    /**
     * Returns true if this chat should appear in the main chat list.
     *
     * HOOK IN DialogsActivity.java (the main chat list):
     * When building the list of dialogs to display, filter with:
     *
     *   List<TLRPC.Dialog> visible = new ArrayList<>();
     *   for (TLRPC.Dialog dialog : allDialogs) {
     *       if (ChatLockManager.getInstance(context)
     *               .shouldShowInChatList(dialog.id)) {
     *           visible.add(dialog);
     *       }
     *   }
     *
     * Also add a "🔒 Hidden chats" row at the top when there are hidden chats:
     *   if (ChatLockManager.getInstance(context).getHiddenChatIds().size() > 0) {
     *       // Show a special row that opens hidden folder after PIN
     *   }
     */
    public boolean shouldShowInChatList(long chatId) {
        if (!isChatHidden(chatId)) return true;
        // Hidden chats only show if folder is unlocked
        return hiddenFolderUnlocked;
    }

    // ─── Hash ──────────────────────────────────────────────────────────────

    private String hashPin(String pin) {
        try {
            java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
            byte[] digest = md.digest(("nxygram_chat_" + pin).getBytes("UTF-8"));
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin;
        }
    }
}
