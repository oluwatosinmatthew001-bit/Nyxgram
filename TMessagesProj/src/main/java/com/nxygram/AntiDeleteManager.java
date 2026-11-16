package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * AntiDeleteManager
 * Preserves deleted and edited messages locally in a separate SQLite database.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/AntiDeleteManager.java
 *
 * Hook points in Telegram source:
 *   - MessagesController.java → deleteMessages() → call onMessagesDeleted()
 *   - MessagesController.java → processUpdateArray() on TL_updateEditMessage → call onMessageEdited()
 *   - On new message receive → call onMessageReceived() to cache before it can be deleted
 */
public class AntiDeleteManager {

    private static AntiDeleteManager instance;
    private final DbHelper dbHelper;
    private SQLiteDatabase db;

    // ─── Singleton ────────────────────────────────────────────────────────────

    public static synchronized AntiDeleteManager getInstance(Context context) {
        if (instance == null) {
            instance = new AntiDeleteManager(context.getApplicationContext());
        }
        return instance;
    }

    private AntiDeleteManager(Context context) {
        dbHelper = new DbHelper(context);
        db = dbHelper.getWritableDatabase();
    }

    // ─── Database Schema ─────────────────────────────────────────────────────

    private static final String DB_NAME    = "nxygram_antidelete.db";
    private static final int    DB_VERSION = 2;

    private static final String TABLE_DELETED = "deleted_messages";
    private static final String TABLE_EDITED  = "edited_messages";

    private static class DbHelper extends SQLiteOpenHelper {

        DbHelper(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_DELETED + " (" +
                    "id              INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "msg_id          INTEGER NOT NULL," +
                    "chat_id         INTEGER NOT NULL," +
                    "sender_id       INTEGER NOT NULL," +
                    "sender_name     TEXT," +
                    "message_text    TEXT," +
                    "media_type      TEXT," +
                    "media_path      TEXT," +
                    "date_sent       INTEGER," +
                    "date_deleted    INTEGER," +
                    "account_id      INTEGER DEFAULT 0" +
                    ")");

            db.execSQL("CREATE TABLE IF NOT EXISTS " + TABLE_EDITED + " (" +
                    "id              INTEGER PRIMARY KEY AUTOINCREMENT," +
                    "msg_id          INTEGER NOT NULL," +
                    "chat_id         INTEGER NOT NULL," +
                    "sender_id       INTEGER NOT NULL," +
                    "sender_name     TEXT," +
                    "original_text   TEXT," +
                    "edited_text     TEXT," +
                    "date_sent       INTEGER," +
                    "date_edited     INTEGER," +
                    "edit_version    INTEGER DEFAULT 1," +
                    "account_id      INTEGER DEFAULT 0" +
                    ")");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_deleted_chat ON " + TABLE_DELETED + "(chat_id, date_deleted)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_edited_chat  ON " + TABLE_EDITED  + "(chat_id, date_edited)");
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            if (oldVersion < 2) {
                db.execSQL("ALTER TABLE " + TABLE_DELETED + " ADD COLUMN account_id INTEGER DEFAULT 0");
                db.execSQL("ALTER TABLE " + TABLE_EDITED  + " ADD COLUMN account_id INTEGER DEFAULT 0");
            }
        }
    }

    // ─── Data Models ─────────────────────────────────────────────────────────

    public static class DeletedMessage {
        public long   id;
        public int    msgId;
        public long   chatId;
        public long   senderId;
        public String senderName;
        public String messageText;
        public String mediaType;
        public String mediaPath;
        public long   dateSent;
        public long   dateDeleted;
        public int    accountId;
    }

    public static class EditedMessage {
        public long   id;
        public int    msgId;
        public long   chatId;
        public long   senderId;
        public String senderName;
        public String originalText;
        public String editedText;
        public long   dateSent;
        public long   dateEdited;
        public int    editVersion;
        public int    accountId;
    }

    // ─── Write Operations ─────────────────────────────────────────────────────

    /**
     * Call this BEFORE Telegram processes a delete — cache the message first.
     * Hook: MessagesController.deleteMessages() at the top, before actual deletion.
     */
    public void onMessageReceived(
            int msgId, long chatId, long senderId, String senderName,
            String text, String mediaType, String mediaPath,
            long dateSent, int accountId) {

        if (!NxygramSettings.isAntiDeleteEnabled()) return;

        // Store in deleted table as "pending" — will be marked if actually deleted
        // We upsert by msg_id + chat_id to avoid duplicates
        ContentValues cv = new ContentValues();
        cv.put("msg_id",       msgId);
        cv.put("chat_id",      chatId);
        cv.put("sender_id",    senderId);
        cv.put("sender_name",  senderName);
        cv.put("message_text", text);
        cv.put("media_type",   mediaType);
        cv.put("media_path",   mediaPath);
        cv.put("date_sent",    dateSent);
        cv.put("date_deleted", 0L); // 0 = not deleted yet
        cv.put("account_id",   accountId);

        db.insertWithOnConflict(TABLE_DELETED, null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    /**
     * Call when a delete event arrives from Telegram.
     * Marks the cached message as deleted with current timestamp.
     */
    public void onMessagesDeleted(List<Integer> msgIds, long chatId, int accountId) {
        if (!NxygramSettings.isAntiDeleteEnabled()) return;

        long now = System.currentTimeMillis() / 1000L;
        db.beginTransaction();
        try {
            for (int msgId : msgIds) {
                ContentValues cv = new ContentValues();
                cv.put("date_deleted", now);
                db.update(TABLE_DELETED, cv,
                        "msg_id=? AND chat_id=? AND account_id=? AND date_deleted=0",
                        new String[]{String.valueOf(msgId),
                                String.valueOf(chatId),
                                String.valueOf(accountId)});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    /**
     * Call when an edit event arrives.
     * Saves the ORIGINAL text before the edit is applied.
     * Hook: MessagesController processUpdateArray() on TL_updateEditMessage
     */
    public void onMessageEdited(
            int msgId, long chatId, long senderId, String senderName,
            String originalText, String newText,
            long dateSent, int accountId) {

        if (!NxygramSettings.isAntiEditEnabled()) return;

        // Find previous edit version
        int version = 1;
        Cursor c = db.rawQuery(
                "SELECT MAX(edit_version) FROM " + TABLE_EDITED +
                        " WHERE msg_id=? AND chat_id=? AND account_id=?",
                new String[]{String.valueOf(msgId),
                        String.valueOf(chatId),
                        String.valueOf(accountId)});
        if (c.moveToFirst() && !c.isNull(0)) {
            version = c.getInt(0) + 1;
        }
        c.close();

        ContentValues cv = new ContentValues();
        cv.put("msg_id",        msgId);
        cv.put("chat_id",       chatId);
        cv.put("sender_id",     senderId);
        cv.put("sender_name",   senderName);
        cv.put("original_text", originalText);
        cv.put("edited_text",   newText);
        cv.put("date_sent",     dateSent);
        cv.put("date_edited",   System.currentTimeMillis() / 1000L);
        cv.put("edit_version",  version);
        cv.put("account_id",    accountId);

        db.insert(TABLE_EDITED, null, cv);
    }

    // ─── Read Operations ──────────────────────────────────────────────────────

    public List<DeletedMessage> getDeletedMessages(long chatId, int accountId) {
        List<DeletedMessage> list = new ArrayList<>();
        Cursor c = db.query(TABLE_DELETED, null,
                "chat_id=? AND account_id=? AND date_deleted>0",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)},
                null, null, "date_deleted DESC", "200");
        while (c.moveToNext()) {
            DeletedMessage m = new DeletedMessage();
            m.id          = c.getLong(c.getColumnIndexOrThrow("id"));
            m.msgId       = c.getInt(c.getColumnIndexOrThrow("msg_id"));
            m.chatId      = c.getLong(c.getColumnIndexOrThrow("chat_id"));
            m.senderId    = c.getLong(c.getColumnIndexOrThrow("sender_id"));
            m.senderName  = c.getString(c.getColumnIndexOrThrow("sender_name"));
            m.messageText = c.getString(c.getColumnIndexOrThrow("message_text"));
            m.mediaType   = c.getString(c.getColumnIndexOrThrow("media_type"));
            m.mediaPath   = c.getString(c.getColumnIndexOrThrow("media_path"));
            m.dateSent    = c.getLong(c.getColumnIndexOrThrow("date_sent"));
            m.dateDeleted = c.getLong(c.getColumnIndexOrThrow("date_deleted"));
            m.accountId   = c.getInt(c.getColumnIndexOrThrow("account_id"));
            list.add(m);
        }
        c.close();
        return list;
    }

    public List<EditedMessage> getEditHistory(int msgId, long chatId, int accountId) {
        List<EditedMessage> list = new ArrayList<>();
        Cursor c = db.query(TABLE_EDITED, null,
                "msg_id=? AND chat_id=? AND account_id=?",
                new String[]{String.valueOf(msgId),
                        String.valueOf(chatId),
                        String.valueOf(accountId)},
                null, null, "edit_version ASC");
        while (c.moveToNext()) {
            EditedMessage m = new EditedMessage();
            m.id           = c.getLong(c.getColumnIndexOrThrow("id"));
            m.msgId        = c.getInt(c.getColumnIndexOrThrow("msg_id"));
            m.chatId       = c.getLong(c.getColumnIndexOrThrow("chat_id"));
            m.senderId     = c.getLong(c.getColumnIndexOrThrow("sender_id"));
            m.senderName   = c.getString(c.getColumnIndexOrThrow("sender_name"));
            m.originalText = c.getString(c.getColumnIndexOrThrow("original_text"));
            m.editedText   = c.getString(c.getColumnIndexOrThrow("edited_text"));
            m.dateSent     = c.getLong(c.getColumnIndexOrThrow("date_sent"));
            m.dateEdited   = c.getLong(c.getColumnIndexOrThrow("date_edited"));
            m.editVersion  = c.getInt(c.getColumnIndexOrThrow("edit_version"));
            m.accountId    = c.getInt(c.getColumnIndexOrThrow("account_id"));
            list.add(m);
        }
        c.close();
        return list;
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    /** Delete records older than retentionDays for a given chat */
    public void pruneOldRecords(long chatId, int retentionDays, int accountId) {
        long cutoff = (System.currentTimeMillis() / 1000L) - ((long) retentionDays * 86400L);
        db.delete(TABLE_DELETED,
                "chat_id=? AND account_id=? AND date_deleted>0 AND date_deleted<?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId), String.valueOf(cutoff)});
        db.delete(TABLE_EDITED,
                "chat_id=? AND account_id=? AND date_edited<?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId), String.valueOf(cutoff)});
    }

    public void clearAll(int accountId) {
        db.delete(TABLE_DELETED, "account_id=?", new String[]{String.valueOf(accountId)});
        db.delete(TABLE_EDITED,  "account_id=?", new String[]{String.valueOf(accountId)});
    }

    public void clearChat(long chatId, int accountId) {
        db.delete(TABLE_DELETED, "chat_id=? AND account_id=?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)});
        db.delete(TABLE_EDITED, "chat_id=? AND account_id=?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)});
    }

    private static boolean isAntiDeleteEnabled() { return NxygramSettings.isAntiDeleteEnabled(); }
    private static boolean isAntiEditEnabled()   { return NxygramSettings.isAntiEditEnabled(); }
}
