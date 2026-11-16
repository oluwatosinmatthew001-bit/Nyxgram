package com.nxygram;

import android.content.Context;
import android.os.Environment;

import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * StorageAnalyzer
 * ══════════════════════════════════════════════════════════════
 * Scans Telegram's local media cache and breaks it down by:
 *   - Chat / dialog
 *   - Media type (images, videos, audio, documents, voice)
 *   - Total size
 *
 * Hook: Call from StorageAnalyzerActivity or NxygramSettingsActivity.
 *
 * Telegram stores media in:
 *   Android/data/org.telegram.messenger/cache/   (or com.nxygram.messenger after rebrand)
 *   Android/data/org.telegram.messenger/files/
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/StorageAnalyzer.java
 */
public class StorageAnalyzer {

    private static StorageAnalyzer instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    public static synchronized StorageAnalyzer getInstance() {
        if (instance == null) instance = new StorageAnalyzer();
        return instance;
    }

    // ─── Data models ──────────────────────────────────────────────────────────

    public enum MediaType {
        IMAGE, VIDEO, AUDIO, VOICE, DOCUMENT, GIF, STICKER, CACHE, OTHER
    }

    public static class MediaEntry {
        public File      file;
        public MediaType type;
        public long      sizeBytes;
        public long      chatId;    // -1 if unknown
        public long      lastModified;
    }

    public static class ChatStorageSummary {
        public long   chatId;
        public String chatName;
        public long   totalBytes;
        public Map<MediaType, Long> byType = new HashMap<>();

        public String formattedSize() { return formatBytes(totalBytes); }
    }

    public static class AnalysisResult {
        public List<ChatStorageSummary> byChat     = new ArrayList<>();
        public Map<MediaType, Long>     byType     = new HashMap<>();
        public long                     totalBytes;
        public long                     cacheBytes;
        public long                     filesBytes;

        public String formattedTotal() { return formatBytes(totalBytes); }
        public String formattedCache() { return formatBytes(cacheBytes); }
    }

    // ─── Callback ─────────────────────────────────────────────────────────────

    public interface AnalysisCallback {
        void onProgress(int percent, String currentDir);
        void onComplete(AnalysisResult result);
        void onError(String error);
    }

    // ─── Scan ─────────────────────────────────────────────────────────────────

    public void analyze(Context context, AnalysisCallback callback) {
        executor.execute(() -> {
            try {
                AnalysisResult result = new AnalysisResult();

                // Telegram's data directories
                File extDir = context.getExternalFilesDir(null);
                File intDir = context.getFilesDir();
                File cacheDir = context.getCacheDir();
                File extCache = context.getExternalCacheDir();

                List<File> roots = new ArrayList<>();
                if (extDir   != null) roots.add(extDir.getParentFile());
                if (intDir   != null) roots.add(intDir);
                if (cacheDir != null) roots.add(cacheDir);
                if (extCache != null) roots.add(extCache);

                List<MediaEntry> allEntries = new ArrayList<>();
                int[] progress = {0};

                for (File root : roots) {
                    if (root == null || !root.exists()) continue;
                    callback.onProgress(progress[0], root.getName());
                    scanDir(root, allEntries, result, callback, progress);
                }

                // Aggregate by type
                for (MediaEntry e : allEntries) {
                    result.byType.merge(e.type, e.sizeBytes, Long::sum);
                    result.totalBytes += e.sizeBytes;
                }

                // Sort chats by size descending
                Collections.sort(result.byChat,
                        (a, b) -> Long.compare(b.totalBytes, a.totalBytes));

                callback.onComplete(result);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    private void scanDir(File dir, List<MediaEntry> entries,
                          AnalysisResult result, AnalysisCallback callback,
                          int[] progress) {
        if (dir == null || !dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles();
        if (files == null) return;

        for (File f : files) {
            if (f.isDirectory()) {
                scanDir(f, entries, result, callback, progress);
            } else {
                MediaEntry entry = new MediaEntry();
                entry.file         = f;
                entry.sizeBytes    = f.length();
                entry.lastModified = f.lastModified();
                entry.type         = detectType(f);
                entry.chatId       = extractChatId(f);

                entries.add(entry);

                // Aggregate cache vs files
                String path = f.getAbsolutePath();
                if (path.contains("cache")) {
                    result.cacheBytes += entry.sizeBytes;
                } else {
                    result.filesBytes += entry.sizeBytes;
                }

                // Per-chat aggregation
                if (entry.chatId > 0) {
                    ChatStorageSummary chat = findOrCreateChat(result, entry.chatId);
                    chat.totalBytes += entry.sizeBytes;
                    chat.byType.merge(entry.type, entry.sizeBytes, Long::sum);
                }

                progress[0] = Math.min(99, progress[0] + 1);
                if (progress[0] % 50 == 0) {
                    callback.onProgress(progress[0], f.getParent());
                }
            }
        }
    }

    private MediaType detectType(File f) {
        String name = f.getName().toLowerCase();
        if (name.endsWith(".jpg") || name.endsWith(".jpeg")
                || name.endsWith(".png") || name.endsWith(".webp"))
            return MediaType.IMAGE;
        if (name.endsWith(".mp4") || name.endsWith(".mov") || name.endsWith(".avi"))
            return MediaType.VIDEO;
        if (name.endsWith(".gif"))   return MediaType.GIF;
        if (name.endsWith(".mp3") || name.endsWith(".m4a") || name.endsWith(".ogg"))
            return MediaType.AUDIO;
        if (name.endsWith(".oga") || name.contains("voice"))
            return MediaType.VOICE;
        if (name.endsWith(".webp") && name.contains("sticker"))
            return MediaType.STICKER;
        if (name.endsWith(".pdf") || name.endsWith(".doc")
                || name.endsWith(".docx") || name.endsWith(".zip"))
            return MediaType.DOCUMENT;
        if (name.endsWith(".0") || name.endsWith(".1") || f.getParent().contains("cache"))
            return MediaType.CACHE;
        return MediaType.OTHER;
    }

    /**
     * Telegram names files with dialog IDs embedded, e.g.:
     * -1001234567890_12345.jpg  or  100123456_thumb.jpg
     * We extract what we can.
     */
    private long extractChatId(File f) {
        String name = f.getName();
        try {
            String[] parts = name.split("_");
            if (parts.length > 1) return Long.parseLong(parts[0]);
        } catch (NumberFormatException ignored) {}
        // Try parent dir name
        try {
            return Long.parseLong(f.getParentFile().getName());
        } catch (Exception ignored) {}
        return -1;
    }

    private ChatStorageSummary findOrCreateChat(AnalysisResult result, long chatId) {
        for (ChatStorageSummary s : result.byChat) {
            if (s.chatId == chatId) return s;
        }
        ChatStorageSummary s = new ChatStorageSummary();
        s.chatId   = chatId;
        s.chatName = "Chat " + chatId;
        result.byChat.add(s);
        return s;
    }

    // ─── Cleanup ──────────────────────────────────────────────────────────────

    /** Delete all cache files. Returns bytes freed. */
    public long clearCache(Context context) {
        long freed = 0;
        freed += deleteDir(context.getCacheDir());
        File extCache = context.getExternalCacheDir();
        if (extCache != null) freed += deleteDir(extCache);
        return freed;
    }

    /** Delete all media for a specific chat folder. Returns bytes freed. */
    public long clearChatMedia(Context context, long chatId) {
        long freed = 0;
        File extDir = context.getExternalFilesDir(null);
        if (extDir == null) return 0;
        File chatDir = new File(extDir.getParentFile(), String.valueOf(chatId));
        if (chatDir.exists()) freed += deleteDir(chatDir);
        return freed;
    }

    private long deleteDir(File dir) {
        if (dir == null || !dir.exists()) return 0;
        long freed = 0;
        File[] files = dir.listFiles();
        if (files != null) {
            for (File f : files) {
                if (f.isDirectory()) freed += deleteDir(f);
                else { freed += f.length(); f.delete(); }
            }
        }
        return freed;
    }

    // ─── Formatting ───────────────────────────────────────────────────────────

    public static String formatBytes(long bytes) {
        if (bytes < 1024)             return bytes + " B";
        if (bytes < 1024 * 1024)      return String.format("%.1f KB", bytes / 1024f);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024f * 1024));
        return String.format("%.2f GB", bytes / (1024f * 1024 * 1024));
    }
}
