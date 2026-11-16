package com.nxygram;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Environment;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StoryToolsManager
 * ══════════════════════════════════════════════════════════════
 * Local story saving, tracking, and notification management.
 *
 * Hook points in Telegram source:
 *   StoriesController.java → when a story is loaded/displayed:
 *     StoryToolsManager.getInstance(ctx).onStoryViewed(story);
 *
 *   StoriesController.java → when stories are updated:
 *     StoryToolsManager.getInstance(ctx).onStoriesUpdated(userId, storyList);
 *
 * IMPORTANT: Story saving is only performed for content the user is
 * permitted to save (no "save restricted" flag on the story).
 * We check hasSaveRestriction() before saving any content.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/StoryToolsManager.java
 */
public class StoryToolsManager {

    private static StoryToolsManager instance;
    private final Context context;
    private final DbHelper dbHelper;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static synchronized StoryToolsManager getInstance(Context context) {
        if (instance == null)
            instance = new StoryToolsManager(context.getApplicationContext());
        return instance;
    }

    private StoryToolsManager(Context context) {
        this.context  = context;
        this.dbHelper = new DbHelper(context);
    }

    // ─── Database ─────────────────────────────────────────────────────────────

    private static class DbHelper extends SQLiteOpenHelper {
        DbHelper(Context ctx) { super(ctx, "nxygram_stories.db", null, 1); }

        @Override public void onCreate(SQLiteDatabase db) {
            db.execSQL("CREATE TABLE IF NOT EXISTS saved_stories ("
                    + "id            INTEGER PRIMARY KEY AUTOINCREMENT,"
                    + "story_id      INTEGER NOT NULL,"
                    + "user_id       INTEGER NOT NULL,"
                    + "user_name     TEXT,"
                    + "media_url     TEXT,"
                    + "local_path    TEXT,"
                    + "is_video      INTEGER DEFAULT 0,"
                    + "saved_at      INTEGER NOT NULL,"
                    + "expires_at    INTEGER DEFAULT 0,"
                    + "caption       TEXT"
                    + ")");
        }

        @Override public void onUpgrade(SQLiteDatabase db, int o, int n) {}
    }

    // ─── Data model ───────────────────────────────────────────────────────────

    public static class SavedStory {
        public long   id;
        public int    storyId;
        public long   userId;
        public String userName;
        public String mediaUrl;
        public String localPath;
        public boolean isVideo;
        public long   savedAt;
        public long   expiresAt;
        public String caption;
    }

    // ─── Hook: story viewed ───────────────────────────────────────────────────

    /**
     * Call when a story is displayed/viewed.
     * Params mirror TLRPC.StoryItem fields — adapt to match your Telegram version.
     *
     * @param storyId   story ID
     * @param userId    story author's user ID
     * @param userName  story author's display name
     * @param mediaUrl  URL of the media (photo or video)
     * @param isVideo   true if video story
     * @param expiresAt Unix timestamp when story expires (0 = unknown)
     * @param caption   story caption text (may be null)
     * @param hasSaveRestriction true if the story author disabled saving
     */
    public void onStoryViewed(int storyId, long userId, String userName,
                               String mediaUrl, boolean isVideo,
                               long expiresAt, String caption,
                               boolean hasSaveRestriction) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_STORY_TOOLS, true)) return;

        // Track view in DB regardless of save setting
        trackView(storyId, userId, userName);

        // Auto-save if enabled and not restricted
        if (NxygramSettings.getBool(NxygramSettings.KEY_STORY_SAVE, false)
                && !hasSaveRestriction
                && mediaUrl != null) {
            saveStoryMedia(storyId, userId, userName, mediaUrl,
                    isVideo, expiresAt, caption);
        }
    }

    /**
     * Manual save triggered by the user (e.g. long-press on story).
     * Shows error if save is restricted.
     */
    public void saveStoryManual(int storyId, long userId, String userName,
                                 String mediaUrl, boolean isVideo,
                                 long expiresAt, String caption,
                                 boolean hasSaveRestriction,
                                 Runnable onDone, Runnable onRestricted) {
        if (hasSaveRestriction) {
            if (onRestricted != null) onRestricted.run();
            return;
        }
        saveStoryMedia(storyId, userId, userName, mediaUrl,
                isVideo, expiresAt, caption);
        if (onDone != null) onDone.run();
    }

    private void trackView(int storyId, long userId, String userName) {
        // Just log the view — no media download
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        // Check if already tracked
        Cursor c = db.query("saved_stories", new String[]{"id"},
                "story_id=? AND user_id=?",
                new String[]{String.valueOf(storyId), String.valueOf(userId)},
                null, null, null);
        boolean exists = c.moveToFirst();
        c.close();
        if (exists) return;

        ContentValues cv = new ContentValues();
        cv.put("story_id",   storyId);
        cv.put("user_id",    userId);
        cv.put("user_name",  userName);
        cv.put("saved_at",   System.currentTimeMillis() / 1000L);
        cv.put("local_path", ""); // no media saved yet
        db.insertWithOnConflict("saved_stories", null, cv, SQLiteDatabase.CONFLICT_IGNORE);
    }

    // ─── Media save ───────────────────────────────────────────────────────────

    private void saveStoryMedia(int storyId, long userId, String userName,
                                 String mediaUrl, boolean isVideo,
                                 long expiresAt, String caption) {
        executor.execute(() -> {
            try {
                // Download to Nxygram's stories folder
                File dir = new File(context.getExternalFilesDir(null), "NxygramStories");
                if (!dir.exists()) dir.mkdirs();

                String ext = isVideo ? ".mp4" : ".jpg";
                File outFile = new File(dir, userId + "_" + storyId + ext);

                if (!outFile.exists()) {
                    HttpURLConnection conn = (HttpURLConnection)
                            new URL(mediaUrl).openConnection();
                    conn.setConnectTimeout(10000);
                    conn.setReadTimeout(15000);
                    conn.connect();

                    try (InputStream is = conn.getInputStream();
                         FileOutputStream fos = new FileOutputStream(outFile)) {
                        byte[] buf = new byte[4096];
                        int read;
                        while ((read = is.read(buf)) != -1) fos.write(buf, 0, read);
                    }
                    conn.disconnect();
                }

                // Store in DB
                SQLiteDatabase db = dbHelper.getWritableDatabase();
                ContentValues cv = new ContentValues();
                cv.put("story_id",   storyId);
                cv.put("user_id",    userId);
                cv.put("user_name",  userName);
                cv.put("media_url",  mediaUrl);
                cv.put("local_path", outFile.getAbsolutePath());
                cv.put("is_video",   isVideo ? 1 : 0);
                cv.put("saved_at",   System.currentTimeMillis() / 1000L);
                cv.put("expires_at", expiresAt);
                cv.put("caption",    caption);
                db.insertWithOnConflict("saved_stories", null, cv,
                        SQLiteDatabase.CONFLICT_REPLACE);

                android.util.Log.d("StoryTools", "Saved story: " + outFile.getAbsolutePath());

            } catch (Exception e) {
                android.util.Log.e("StoryTools", "Save failed: " + e.getMessage());
            }
        });
    }

    // ─── Queries ──────────────────────────────────────────────────────────────

    public List<SavedStory> getSavedStories() {
        List<SavedStory> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase()
                .query("saved_stories", null,
                        "local_path != ''", null,
                        null, null, "saved_at DESC", "100");
        while (c.moveToNext()) list.add(fromCursor(c));
        c.close();
        return list;
    }

    public List<SavedStory> getSavedStoriesForUser(long userId) {
        List<SavedStory> list = new ArrayList<>();
        Cursor c = dbHelper.getReadableDatabase()
                .query("saved_stories", null,
                        "user_id=? AND local_path != ''",
                        new String[]{String.valueOf(userId)},
                        null, null, "saved_at DESC");
        while (c.moveToNext()) list.add(fromCursor(c));
        c.close();
        return list;
    }

    public void deleteSavedStory(long id) {
        Cursor c = dbHelper.getReadableDatabase()
                .query("saved_stories", new String[]{"local_path"},
                        "id=?", new String[]{String.valueOf(id)}, null, null, null);
        if (c.moveToFirst()) {
            String path = c.getString(0);
            if (path != null && !path.isEmpty()) new File(path).delete();
        }
        c.close();
        dbHelper.getWritableDatabase().delete("saved_stories",
                "id=?", new String[]{String.valueOf(id)});
    }

    public void clearAll() {
        List<SavedStory> stories = getSavedStories();
        for (SavedStory s : stories) {
            if (s.localPath != null) new File(s.localPath).delete();
        }
        dbHelper.getWritableDatabase().execSQL("DELETE FROM saved_stories");
    }

    // ─── Story notifications ──────────────────────────────────────────────────

    /**
     * Call when a user posts a new story — shows a notification if enabled.
     * Hook in StoriesController when a new story update arrives.
     */
    public void onNewStoryAvailable(long userId, String userName) {
        if (!NxygramSettings.getBool(NxygramSettings.KEY_STORY_TOOLS, true)) return;
        // Story notification is handled by Telegram's existing system.
        // Nxygram can enhance it with custom priority/sound in NotificationManager.
        android.util.Log.d("StoryTools", "New story from: " + userName);
    }

    // ─── Helper ───────────────────────────────────────────────────────────────

    private SavedStory fromCursor(Cursor c) {
        SavedStory s   = new SavedStory();
        s.id           = c.getLong(c.getColumnIndexOrThrow("id"));
        s.storyId      = c.getInt(c.getColumnIndexOrThrow("story_id"));
        s.userId       = c.getLong(c.getColumnIndexOrThrow("user_id"));
        s.userName     = c.getString(c.getColumnIndexOrThrow("user_name"));
        s.mediaUrl     = c.getString(c.getColumnIndexOrThrow("media_url"));
        s.localPath    = c.getString(c.getColumnIndexOrThrow("local_path"));
        s.isVideo      = c.getInt(c.getColumnIndexOrThrow("is_video")) == 1;
        s.savedAt      = c.getLong(c.getColumnIndexOrThrow("saved_at"));
        s.expiresAt    = c.getLong(c.getColumnIndexOrThrow("expires_at"));
        s.caption      = c.getString(c.getColumnIndexOrThrow("caption"));
        return s;
    }
}
