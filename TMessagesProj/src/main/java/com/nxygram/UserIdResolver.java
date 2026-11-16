package com.nxygram;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.graphics.Typeface;
import android.view.GestureDetector;
import android.view.MotionEvent;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

/**
 * UserIdResolver
 * ══════════════════════════════════════════════════════════════
 * Always show user/channel/bot ID on profile screens.
 * Double-tap the ID to copy it to clipboard.
 * Single-tap to copy the @username.
 *
 * Hook into ProfileActivity / UserInfoActivity:
 *   LinearLayout infoLayout = ...; // the profile info container
 *   UserIdResolver.inject(context, infoLayout, userId, username);
 *
 * Also patches the chat info toolbar subtitle to always show ID.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/UserIdResolver.java
 */
public class UserIdResolver {

    // ─── Inject ID row into any profile layout ─────────────────────────────

    /**
     * Adds a persistent User ID row to a profile info layout.
     *
     * HOOK IN TELEGRAM SOURCE — ProfileActivity.java:
     *   Find where "phone number" and "username" rows are added.
     *   After the last info row, add:
     *
     *   UserIdResolver.inject(context, infoContainer,
     *       userFull.user.id,
     *       userFull.user.username,
     *       false  // isChannel
     *   );
     *
     * For channels/groups (ChatActivity / ProfileActivity for channels):
     *   UserIdResolver.inject(context, infoContainer,
     *       -chatId,       // negative for channels
     *       chat.username,
     *       true           // isChannel
     *   );
     */
    public static View inject(Context context, long userId,
                               String username, boolean isChannel) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        int d = (int) context.getResources().getDisplayMetrics().density;

        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(t.colorSurface);
        container.setPadding(72*d, 10*d, 16*d, 10*d); // match Telegram's info row indent

        // Label
        TextView label = new TextView(context);
        label.setText(isChannel ? "Channel ID" : "User ID");
        label.setTextSize(13);
        label.setTextColor(t.colorTextSecondary);
        label.setTypeface(null, Typeface.NORMAL);
        container.addView(label);

        // ID value — monospace for readability
        String idText = formatId(userId);
        TextView idView = new TextView(context);
        idView.setText(idText);
        idView.setTextSize(16);
        idView.setTextColor(t.colorText);
        idView.setTypeface(Typeface.MONOSPACE);
        idView.setPadding(0, 2*d, 0, 0);
        container.addView(idView);

        // Hint text
        TextView hint = new TextView(context);
        hint.setText("Double-tap to copy ID • Tap to copy @username");
        hint.setTextSize(11);
        hint.setTextColor(t.colorTextSecondary);
        hint.setPadding(0, 2*d, 0, 0);
        container.addView(hint);

        // Divider
        View div = new View(context);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);

        // Touch: single tap = copy @username, double tap = copy ID
        final long finalUserId = userId;
        final String finalUsername = username;

        GestureDetector detector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {

            @Override
            public boolean onSingleTapConfirmed(MotionEvent e) {
                if (finalUsername != null && !finalUsername.isEmpty()) {
                    copyToClipboard(context, "@" + finalUsername, "@" + finalUsername + " copied");
                } else {
                    copyToClipboard(context, String.valueOf(finalUserId),
                            "User ID " + formatId(finalUserId) + " copied");
                }
                return true;
            }

            @Override
            public boolean onDoubleTap(MotionEvent e) {
                copyToClipboard(context, String.valueOf(finalUserId),
                        "User ID " + formatId(finalUserId) + " copied ✓");
                // Flash the ID view
                idView.setTextColor(t.colorPrimary);
                idView.postDelayed(() -> idView.setTextColor(t.colorText), 400);
                return true;
            }
        });

        container.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return true;
        });
        idView.setOnTouchListener((v, event) -> {
            detector.onTouchEvent(event);
            return true;
        });

        return container;
    }

    // ─── Patch chat info subtitle ──────────────────────────────────────────

    /**
     * Returns a subtitle string that includes the user/chat ID.
     * Use instead of the normal "last seen" or "members" subtitle
     * when NxygramSettings has show_user_id enabled.
     *
     * HOOK IN ChatActivity.java or ProfileActivity.java:
     *   String subtitle = UserIdResolver.buildSubtitle(
     *       existingSubtitle, userId, isChannel);
     *   actionBar.setSubtitle(subtitle);
     */
    public static String buildSubtitle(String existing, long userId, boolean isChannel) {
        if (!NxygramSettings.getBool("show_user_id", true)) return existing;
        String idPart = "ID: " + formatId(userId);
        if (existing == null || existing.isEmpty()) return idPart;
        return existing + "  •  " + idPart;
    }

    // ─── Double-tap-to-copy on any message text ────────────────────────────

    /**
     * Attach a double-tap listener to a message text view to copy the text.
     *
     * HOOK IN ChatMessageCell.java:
     *   UserIdResolver.attachDoubleTapCopy(context, messageTextView, messageText);
     */
    public static void attachDoubleTapCopy(Context context, View view, String text) {
        if (!NxygramSettings.getBool("double_tap_copy", true)) return;

        GestureDetector detector = new GestureDetector(context,
                new GestureDetector.SimpleOnGestureListener() {
            @Override
            public boolean onDoubleTap(MotionEvent e) {
                copyToClipboard(context, text, "Copied ✓");
                return true;
            }
        });
        view.setOnTouchListener((v, event) -> detector.onTouchEvent(event));
    }

    // ─── Helpers ───────────────────────────────────────────────────────────

    /**
     * Format a user ID with a copy hint.
     * e.g. 1234567890 → "1,234,567,890"
     */
    public static String formatId(long userId) {
        // Format with separators for readability
        return String.format("%,d", Math.abs(userId));
    }

    private static void copyToClipboard(Context context, String text, String toast) {
        ClipboardManager cm = (ClipboardManager)
                context.getSystemService(Context.CLIPBOARD_SERVICE);
        if (cm != null) {
            cm.setPrimaryClip(ClipData.newPlainText("Nxygram", text));
        }
        Toast.makeText(context, toast, Toast.LENGTH_SHORT).show();
    }
}
