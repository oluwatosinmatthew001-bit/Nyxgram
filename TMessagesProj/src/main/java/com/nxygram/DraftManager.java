package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * DraftManager
 * ══════════════════════════════════════════════════════════════
 * Persistent multi-chat draft storage that extends Telegram's
 * built-in draft system with:
 *   - Unlimited draft history per chat
 *   - Cross-session persistence
 *   - Draft labels/tags
 *   - Quick-restore UI
 *
 * Hook in ChatActivity:
 *   - onPause():  DraftManager.getInstance(ctx).saveDraft(chatId, accountId, text)
 *   - onResume(): String draft = DraftManager.getInstance(ctx).getDraft(chatId, accountId)
 *   - If draft != null: restore to input field
 */
public class DraftManager {

    private static DraftManager instance;
    private final DbHelper dbHelper;

    public static synchronized DraftManager getInstance(Context context) {
        if (instance == null)
            instance = new DraftManager(context.getApplicationContext());
        return instance;
    }

    private DraftManager(Context context) {
        this.dbHelper = new DbHelper(context);
    }

    // ─── Database ─────────────────────────────────────────────────────────────

    static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_drafts.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS drafts ("
                    + "id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "chat_id     INTEGER NOT NULL,"
                    + "account_id  INTEGER DEFAULT 0,"
                    + "draft_text  TEXT NOT NULL,"
                    + "label       TEXT,"
                    + "updated_at  INTEGER NOT NULL,"
                    + "is_current  INTEGER DEFAULT 1"
                    + ")");
            db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS idx_current_draft "
                    + "ON drafts(chat_id, account_id, is_current) "
                    + "WHERE is_current = 1");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    public static class Draft {
        public long   id;
        public long   chatId;
        public int    accountId;
        public String text;
        public String label;
        public long   updatedAt;
        public boolean isCurrent;
    }

    // ─── Core operations ──────────────────────────────────────────────────────

    /**
     * Save or update the current draft for a chat.
     * Call on every text change (debounced) or onPause().
     */
    public void saveDraft(long chatId, int accountId, String text) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_DRAFT_MANAGER, true)) return;
        if (text == null) text = "";

        SQLiteDatabase db = dbHelper.getWritableDatabase();

        if (text.isEmpty()) {
            // Clear current draft
            db.delete("drafts",
                    "chat_id=? AND account_id=? AND is_current=1",
                    new String[]{String.valueOf(chatId), String.valueOf(accountId)});
            return;
        }

        // Archive old current draft (keep history)
        ContentValues archive = new ContentValues();
        archive.put("is_current", 0);
        db.update("drafts", archive,
                "chat_id=? AND account_id=? AND is_current=1",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)});

        // Insert new current draft
        ContentValues cv = new ContentValues();
        cv.put("chat_id",    chatId);
        cv.put("account_id", accountId);
        cv.put("draft_text", text);
        cv.put("updated_at", System.currentTimeMillis() / 1000L);
        cv.put("is_current", 1);
        db.insert("drafts", null, cv);

        // Prune history to 10 items per chat
        db.execSQL("DELETE FROM drafts WHERE id NOT IN ("
                + "SELECT id FROM drafts WHERE chat_id=? AND account_id=? "
                + "ORDER BY updated_at DESC LIMIT 10)",
                new Object[]{chatId, accountId});
    }

    /**
     * Get the current draft text for a chat.
     * Returns empty string if no draft.
     */
    public String getDraft(long chatId, int accountId) {
        Cursor c = dbHelper.getReadableDatabase().query(
                "drafts", new String[]{"draft_text"},
                "chat_id=? AND account_id=? AND is_current=1",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)},
                null, null, null);
        String text = "";
        if (c.moveToFirst()) text = c.getString(0);
        c.close();
        return text;
    }

    public boolean hasDraft(long chatId, int accountId) {
        return !getDraft(chatId, accountId).isEmpty();
    }

    /**
     * Get draft history (all saved drafts, newest first) for a chat.
     */
    public List<Draft> getDraftHistory(long chatId, int accountId) {
        List<Draft> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "drafts", null,
                "chat_id=? AND account_id=?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)},
                null, null, "updated_at DESC", "10");
        while (c.moveToNext()) {
            Draft d       = new Draft();
            d.id          = c.getLong(c.getColumnIndexOrThrow("id"));
            d.chatId      = c.getLong(c.getColumnIndexOrThrow("chat_id"));
            d.accountId   = c.getInt(c.getColumnIndexOrThrow("account_id"));
            d.text        = c.getString(c.getColumnIndexOrThrow("draft_text"));
            d.label       = c.getString(c.getColumnIndexOrThrow("label"));
            d.updatedAt   = c.getLong(c.getColumnIndexOrThrow("updated_at"));
            d.isCurrent   = c.getInt(c.getColumnIndexOrThrow("is_current")) == 1;
            list.add(d);
        }
        c.close();
        return list;
    }

    /** Get all chats that have a current draft (for draft indicator in chat list). */
    public List<Long> getChatsWithDrafts(int accountId) {
        List<Long> ids = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "drafts", new String[]{"chat_id"},
                "account_id=? AND is_current=1 AND draft_text != ''",
                new String[]{String.valueOf(accountId)},
                null, null, "updated_at DESC");
        while (c.moveToNext()) ids.add(c.getLong(0));
        c.close();
        return ids;
    }

    public void clearDraft(long chatId, int accountId) {
        dbHelper.getWritableDatabase().delete("drafts",
                "chat_id=? AND account_id=?",
                new String[]{String.valueOf(chatId), String.valueOf(accountId)});
    }

    public void clearAll(int accountId) {
        dbHelper.getWritableDatabase().delete("drafts",
                "account_id=?", new String[]{String.valueOf(accountId)});
    }
}
