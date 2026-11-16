package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.media.MediaScannerConnection;
import android.os.Environment;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * ViewOnceBypass
 * ══════════════════════════════════════════════════════════════
 * Intercepts Telegram view-once media BEFORE it is deleted after
 * the first open, copies it to a local private directory, and
 * allows the user to:
 *   - Re-open it unlimited times
 *   - Save it to gallery
 *   - Screenshot it (FLAG_SECURE is not set in Nxygram)
 *
 * HOW IT WORKS (client-side only):
 *   Telegram downloads view-once media to its cache before displaying.
 *   When the user opens it, Telegram schedules deletion.
 *   We intercept at display time — BEFORE deletion — and copy the file.
 *   The sender's server still records "opened once". We only affect
 *   what happens on THIS device with the already-downloaded file.
 *
 * Hook points:
 *   MediaController / PhotoViewer / VideoPlayer in Telegram source:
 *   When openning a view-once message call:
 *     ViewOnceBypass.getInstance(ctx).onViewOnceOpened(msgId, chatId, filePath, isVideo);
 *   This copies the file before Telegram deletes it.
 *
 * To show saved view-once items:
 *   List<ViewOnceItem> items = ViewOnceBypass.getInstance(ctx).getSaved();
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/ViewOnceBypass.java
 */
public class ViewOnceBypass {

    private static ViewOnceBypass instance;
    private final Context context;
    private final DbHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static synchronized ViewOnceBypass getInstance(Context context) {
        if (instance == null)
            instance = new ViewOnceBypass(context.getApplicationContext());
        return instance;
    }

    private ViewOnceBypass(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
    }

    // ─── Enabled check ────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return NxygramSettings.getBool("view_once_bypass", true);
    }

    // ─── Database ─────────────────────────────────────────────────────────────

    static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_viewonce.db", null, 1); }
        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS view_once_items ("
                    + "id          INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "msg_id      INTEGER NOT NULL,"
                    + "chat_id     INTEGER NOT NULL,"
                    + "local_path  TEXT NOT NULL,"
                    + "is_video    INTEGER DEFAULT 0,"
                    + "saved_at    INTEGER NOT NULL,"
                    + "sender_id   INTEGER DEFAULT 0,"
                    + "sender_name TEXT"
                    + ")");
        }
        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    public static class ViewOnceItem {
        public long   id;
        public int    msgId;
        public long   chatId;
        public String localPath;
        public boolean isVideo;
        public long   savedAt;
        public long   senderId;
        public String senderName;
    }

    // ─── Core: intercept before Telegram deletes ──────────────────────────────

    /**
     * Call this when a view-once message is about to be opened/displayed.
     * Pass the local file path that Telegram downloaded the media to.
     *
     * HOOK IN TELEGRAM SOURCE:
     *
     * Option A — PhotoViewer.java
     *   In openPhoto() or similar, when messageObject.messageOwner.media is
     *   TL_messageMediaPhoto with ttl_seconds > 0 (view-once):
     *
     *     if (ViewOnceBypass.getInstance(context).isEnabled()) {
     *         String filePath = FileLoader.getInstance(currentAccount)
     *             .getPathToMessage(messageObject.messageOwner).getAbsolutePath();
     *         ViewOnceBypass.getInstance(context).onViewOnceOpened(
     *             messageObject.getId(),
     *             messageObject.getDialogId(),
     *             filePath,
     *             false,  // isVideo
     *             messageObject.getSenderId(),
     *             messageObject.getSenderName()
     *         );
     *     }
     *
     * Option B — VideoPlayer / SecretMediaViewer
     *   Same pattern for video view-once messages.
     *   ttl_seconds > 0 on TL_messageMediaDocument = view-once video.
     */
    public void onViewOnceOpened(int msgId, long chatId,
                                  String originalPath, boolean isVideo,
                                  long senderId, String senderName) {
        if (!isEnabled()) return;
        if (originalPath == null || originalPath.isEmpty()) return;

        // Check if already saved
        if (isAlreadySaved(msgId, chatId)) return;

        executor.execute(() -> {
            try {
                File src = new File(originalPath);
                if (!src.exists() || !src.isFile()) {
                    android.util.Log.w("ViewOnce", "Source file not found: " + originalPath);
                    return;
                }

                // Copy to Nxygram private storage BEFORE Telegram deletes it
                File destDir = new File(context.getFilesDir(), "NxygramViewOnce");
                if (!destDir.exists()) destDir.mkdirs();

                String ext = isVideo ? ".mp4" : ".jpg";
                File dest = new File(destDir, chatId + "_" + msgId + ext);

                copyFile(src, dest);

                // Store in DB
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("msg_id",      msgId);
                cv.put("chat_id",     chatId);
                cv.put("local_path",  dest.getAbsolutePath());
                cv.put("is_video",    isVideo ? 1 : 0);
                cv.put("saved_at",    System.currentTimeMillis() / 1000L);
                cv.put("sender_id",   senderId);
                cv.put("sender_name", senderName);
                db.insert("view_once_items", null, cv);

                android.util.Log.d("ViewOnce",
                        "Saved view-once: " + dest.getAbsolutePath());

            } catch (Exception e) {
                android.util.Log.e("ViewOnce", "Failed to save: " + e.getMessage());
            }
        });
    }

    // ─── Re-open ──────────────────────────────────────────────────────────────

    /**
     * Returns the local path to re-open a saved view-once item.
     * Returns null if not saved.
     */
    public String getLocalPath(int msgId, long chatId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.query("view_once_items",
                new String[]{"local_path"},
                "msg_id=? AND chat_id=?",
                new String[]{String.valueOf(msgId), String.valueOf(chatId)},
                null, null, null);
        String path = null;
        if (c.moveToFirst()) path = c.getString(0);
        c.close();
        return (path != null && new File(path).exists()) ? path : null;
    }

    public boolean isAlreadySaved(int msgId, long chatId) {
        return getLocalPath(msgId, chatId) != null;
    }

    // ─── Save to gallery ──────────────────────────────────────────────────────

    /**
     * Save a view-once item to the public gallery (Pictures/Nxygram).
     * Returns the saved file path, or null on failure.
     */
    public String saveToGallery(ViewOnceItem item) {
        if (item.localPath == null) return null;
        File src = new File(item.localPath);
        if (!src.exists()) return null;

        try {
            File galleryDir = new File(
                    Environment.getExternalStoragePublicDirectory(
                            Environment.DIRECTORY_PICTURES),
                    "Nxygram");
            if (!galleryDir.exists()) galleryDir.mkdirs();

            String ext = item.isVideo ? ".mp4" : ".jpg";
            File dest = new File(galleryDir,
                    "viewonce_" + item.chatId + "_" + item.msgId + ext);
            copyFile(src, dest);

            // Notify MediaScanner so it appears in gallery
            MediaScannerConnection.scanFile(context,
                    new String[]{dest.getAbsolutePath()},
                    null, null);

            return dest.getAbsolutePath();
        } catch (Exception e) {
            android.util.Log.e("ViewOnce", "Gallery save failed: " + e.getMessage());
            return null;
        }
    }

    // ─── Screenshot enabler ───────────────────────────────────────────────────

    /**
     * Call in any Activity where Telegram sets FLAG_SECURE to block screenshots.
     * This clears that flag so screenshots work normally.
     *
     * HOOK IN TELEGRAM SOURCE:
     *
     * Search for FLAG_SECURE in:
     *   LaunchActivity.java
     *   PhotoViewer.java
     *   SecretChatHelper.java
     *   Any Activity that calls:
     *     getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
     *
     * Either remove those calls entirely, or wrap with:
     *   if (!NxygramSettings.getBool("allow_screenshots", true)) {
     *       getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
     *   }
     *   // If allow_screenshots = true, FLAG_SECURE is never set
     *
     * This applies globally to ALL content including secret chats and view-once.
     */
    public static void clearSecureFlag(android.app.Activity activity) {
        if (NxygramSettings.getBool("allow_screenshots", true)) {
            activity.getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    /**
     * Hook this in onCreate() and onResume() of every Activity in Telegram.
     * Most importantly: LaunchActivity, PhotoViewer, SecretChatActivity.
     *
     * Usage:
     *   @Override protected void onResume() {
     *       super.onResume();
     *       ViewOnceBypass.applyScreenshotPolicy(this);
     *   }
     */
    public static void applyScreenshotPolicy(android.app.Activity activity) {
        if (NxygramSettings.getBool("allow_screenshots", true)) {
            // Clear secure flag — screenshots allowed
            activity.getWindow().clearFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        } else {
            // Respect original Telegram behavior
            activity.getWindow().addFlags(
                    android.view.WindowManager.LayoutParams.FLAG_SECURE);
        }
    }

    // ─── Forward restriction bypass ───────────────────────────────────────────

    /**
     * Telegram sets message.noforwards = true for channels/chats that
     * disable forwarding. This is enforced CLIENT-SIDE only.
     * As a custom client, we can ignore it.
     *
     * HOOK IN TELEGRAM SOURCE:
     *
     * Search for "noforwards" in:
     *   ChatActivity.java
     *   MessageObject.java
     *   ActionBarMenu / message context menu builders
     *
     * Find checks like:
     *   if (messageObject.messageOwner.noforwards) { hideForwardOption(); }
     * or:
     *   if (chat.noforwards) { disableCopyOption(); }
     *
     * Wrap each with:
     *   if (!NxygramSettings.getBool("bypass_forward_restriction", true)) {
     *       // original restriction code
     *   }
     *   // If bypass = true, forward/copy options always shown
     *
     * Also search for COPY text being disabled in secret chats or restricted chats.
     */
    public static boolean shouldBypassForwardRestriction() {
        return NxygramSettings.getBool("bypass_forward_restriction", true);
    }

    public static boolean shouldBypassCopyRestriction() {
        return NxygramSettings.getBool("bypass_copy_restriction", true);
    }

    // ─── List saved items ─────────────────────────────────────────────────────

    public List<ViewOnceItem> getSaved() {
        List<ViewOnceItem> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase().query(
                "view_once_items", null, null, null,
                null, null, "saved_at DESC", "200");
        while (c.moveToNext()) {
            ViewOnceItem item  = new ViewOnceItem();
            item.id            = c.getLong(c.getColumnIndexOrThrow("id"));
            item.msgId         = c.getInt(c.getColumnIndexOrThrow("msg_id"));
            item.chatId        = c.getLong(c.getColumnIndexOrThrow("chat_id"));
            item.localPath     = c.getString(c.getColumnIndexOrThrow("local_path"));
            item.isVideo       = c.getInt(c.getColumnIndexOrThrow("is_video")) == 1;
            item.savedAt       = c.getLong(c.getColumnIndexOrThrow("saved_at"));
            item.senderId      = c.getLong(c.getColumnIndexOrThrow("sender_id"));
            item.senderName    = c.getString(c.getColumnIndexOrThrow("sender_name"));
            // Only include items whose file still exists
            if (item.localPath != null && new File(item.localPath).exists())
                list.add(item);
        }
        c.close();
        return list;
    }

    public void delete(long id, String localPath) {
        if (localPath != null) new File(localPath).delete();
        dbHelper.getWritableDatabase().delete("view_once_items",
                "id=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        for (ViewOnceItem item : getSaved()) delete(item.id, item.localPath);
    }

    // ─── File copy ────────────────────────────────────────────────────────────

    private void copyFile(File src, File dest) throws IOException {
        try (FileInputStream fis = new FileInputStream(src);
             FileOutputStream fos = new FileOutputStream(dest)) {
            byte[] buf = new byte[8192];
            int read;
            while ((read = fis.read(buf)) != -1) fos.write(buf, 0, read);
        }
    }
}
