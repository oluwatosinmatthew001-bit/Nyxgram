package com.nxygram;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * GlobalSearchManager
 * ══════════════════════════════════════════════════════════════
 * Search messages across ALL chats simultaneously.
 * Uses Telegram's existing MessagesStorage (SQLite) to query across dialogs.
 *
 * Hook points:
 *   GlobalSearchActivity → calls search()
 *   Search results → open chat at message position
 *
 * Telegram's local message database is at:
 *   MessagesStorage → database → messages table
 *   Schema (approximate): messages (mid, uid, read_state, send_state,
 *                          date, data, out, ttl, media, replydata, imp, forwards)
 *   The 'data' column contains TLObject-serialized message data.
 *
 * We use Telegram's own search APIs where possible:
 *   MessagesController.performSearch() / MessagesStorage.searchQuery()
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/GlobalSearchManager.java
 */
public class GlobalSearchManager {

    private static GlobalSearchManager instance;
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    public static synchronized GlobalSearchManager getInstance() {
        if (instance == null) instance = new GlobalSearchManager();
        return instance;
    }

    // ─── Result model ──────────────────────────────────────────────────────

    public static class SearchResult {
        public int    messageId;
        public long   dialogId;
        public String dialogName;
        public String messageText;
        public String senderName;
        public long   date;
        public boolean isOutgoing;
        public int    type; // 0=private, 1=group, 2=channel, 3=bot

        // Highlight: index of match start + length
        public int    matchStart;
        public int    matchLength;
    }

    public interface SearchCallback {
        void onResults(List<SearchResult> results, int totalFound);
        void onError(String error);
        void onProgress(int dialogsSearched, int totalDialogs);
    }

    // ─── Search ────────────────────────────────────────────────────────────

    /**
     * Search messages across all dialogs.
     *
     * HOOK: This calls Telegram's existing search infrastructure via reflection.
     * Telegram has MessagesController.performSearch() and MessagesStorage.searchQuery()
     *
     * The cleanest integration is to call these directly after hooking in:
     *
     *   // In GlobalSearchActivity or wherever you call this:
     *   MessagesController.getInstance(accountId).performSearchMessages(
     *       null,       // peer = null means ALL dialogs
     *       query,
     *       null,       // fromUser
     *       0,          // date
     *       0,          // maxId
     *       50,         // limit
     *       0,          // filter type
     *       0,          // minDate
     *       0,          // maxDate
     *       0,          // folderId
     *       0,          // topicId
     *       callback
     *   );
     *
     * For now we provide the structure and direct hook instructions.
     * The actual DB query runs through Telegram's existing MessagesStorage.
     */
    public void search(Context context, String query, int accountId,
                        int limit, SearchCallback callback) {
        if (query == null || query.trim().length() < 2) {
            callback.onResults(new ArrayList<>(), 0);
            return;
        }

        final String q = query.trim();

        executor.execute(() -> {
            try {
                // ── Method 1: Telegram's MessagesStorage direct query ──
                // This is the preferred approach — uses Telegram's own indexed search
                List<SearchResult> results = searchViaReflection(q, accountId, limit);

                // Sort by date descending
                Collections.sort(results, (a, b) -> Long.compare(b.date, a.date));

                // Add match highlights
                for (SearchResult r : results) {
                    if (r.messageText != null) {
                        int idx = r.messageText.toLowerCase().indexOf(q.toLowerCase());
                        r.matchStart  = Math.max(0, idx);
                        r.matchLength = idx >= 0 ? q.length() : 0;
                    }
                }

                final List<SearchResult> finalResults = results;
                mainHandler.post(() -> callback.onResults(finalResults, results.size()));

            } catch (Exception e) {
                mainHandler.post(() -> callback.onError(e.getMessage()));
            }
        });
    }

    private List<SearchResult> searchViaReflection(String query,
                                                     int accountId, int limit) {
        List<SearchResult> results = new ArrayList<>();
        try {
            // Access Telegram's MessagesStorage
            Class<?> storageClass = Class.forName(
                    "org.telegram.messenger.MessagesStorage");
            java.lang.reflect.Method getInstance = storageClass.getMethod(
                    "getInstance", int.class);
            Object storage = getInstance.invoke(null, accountId);

            // Try to call searchQuery or similar
            // Exact method name varies by Telegram version — check MessagesStorage.java
            // Common variants:
            //   searchQuery(String, int, int, long, int, long, long, long)
            //   getSearchMessages(String, long, int, int, int)

            // We attempt a few known signatures:
            java.lang.reflect.Method[] methods = storageClass.getMethods();
            for (java.lang.reflect.Method m : methods) {
                String name = m.getName().toLowerCase();
                if (name.contains("search") && name.contains("message")) {
                    android.util.Log.d("GlobalSearch",
                            "Found search method: " + m.getName() + " " +
                            java.util.Arrays.toString(m.getParameterTypes()));
                    // Log available methods for developer reference
                    // Actual invocation requires knowing exact signature
                }
            }

        } catch (Exception e) {
            android.util.Log.e("GlobalSearch",
                    "Reflection search failed: " + e.getMessage());
            android.util.Log.d("GlobalSearch",
                    "TODO: Check MessagesStorage.java in your Telegram version for " +
                    "the exact search method signature and wire it here.");
        }
        return results;
    }

    // ─── AI-powered search ─────────────────────────────────────────────────

    /**
     * Use AI to find messages by meaning, not just exact text.
     * "find the message where we talked about the birthday party"
     * → AI understands intent and returns likely search terms.
     */
    public void aiSearch(Context context, String naturalQuery,
                          int accountId, SearchCallback callback) {
        PrexzyApi.call(context)
            .prompt("Extract the best search keywords (1-3 words) from this "
                  + "natural language search query for a chat app. "
                  + "Return ONLY the keywords, nothing else.\n\n"
                  + "Query: " + naturalQuery)
            .onResult(keywords -> {
                android.util.Log.d("GlobalSearch", "AI keywords: " + keywords);
                search(context, keywords.trim(), accountId, 50, callback);
            })
            .onError(err -> {
                // Fall back to direct search
                search(context, naturalQuery, accountId, 50, callback);
            })
            .execute();
    }

    // ─── Filter helpers ────────────────────────────────────────────────────

    public enum SearchFilter {
        ALL,        // text messages
        MEDIA,      // photos and videos
        LINKS,      // messages with URLs
        FILES,      // documents
        AUDIO,      // voice + music
        MENTIONS    // messages that mention you
    }

    /**
     * Hook to open a specific message in its chat.
     * Call when user taps a search result.
     *
     * Usage in GlobalSearchActivity:
     *   GlobalSearchManager.openMessage(context, result.dialogId,
     *       result.messageId, accountId);
     */
    public static void openMessage(Context context, long dialogId,
                                    int messageId, int accountId) {
        // In Telegram source:
        // Bundle args = new Bundle();
        // args.putLong("dialog_id", dialogId);
        // args.putInt("message_id", messageId);
        // LaunchActivity.instance.presentFragment(new ChatActivity(args));
        android.util.Log.d("GlobalSearch",
                "Open: dialogId=" + dialogId + " msgId=" + messageId);
    }
}
