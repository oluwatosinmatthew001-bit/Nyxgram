package com.nxygram;

import android.content.Context;

/**
 * GhostModeManager
 * ══════════════════════════════════════════════════════════════
 * Suppresses outgoing status signals:
 *   - Typing indicator
 *   - Online/last seen status
 *   - Recording (voice) indicator
 *   - Upload indicator
 *   - Read receipts (where API allows)
 *
 * HOW IT WORKS:
 *   These are all CLIENT-SIDE calls to the Telegram API.
 *   We intercept them before they are sent.
 *   The server never receives the status update → others never see it.
 *
 * LIMITATIONS (honest):
 *   - Online status: Telegram's server auto-sets you online when you
 *     send a message. We suppress the explicit setOnline() call but
 *     Telegram's server may still briefly mark you online on message send.
 *   - Read receipts: Telegram only supports disabling in Settings for
 *     one-on-one chats. Groups always show read counts server-side.
 *   - These controls affect what YOUR client sends, not what the server infers.
 *
 * Hook points: all in MessagesController.java
 */
public class GhostModeManager {

    private static GhostModeManager instance;

    public static synchronized GhostModeManager getInstance() {
        if (instance == null) instance = new GhostModeManager();
        return instance;
    }

    // ─── Master switch ────────────────────────────────────────────────────────

    public boolean isEnabled() {
        return NxygramSettings.isGhostMode();
    }

    // ─── Individual feature checks ────────────────────────────────────────────

    public boolean shouldSuppressTyping() {
        return isEnabled() && NxygramSettings.isGhostHideTyping();
    }

    public boolean shouldSuppressOnline() {
        return isEnabled() && NxygramSettings.isGhostHideOnline();
    }

    public boolean shouldSuppressRecording() {
        return isEnabled() && NxygramSettings.getBool(
                NxygramSettings.KEY_GHOST_RECORDING, false);
    }

    public boolean shouldSuppressReadReceipts() {
        return isEnabled() && NxygramSettings.getBool(
                NxygramSettings.KEY_GHOST_READ_RECEIPTS, false);
    }

    // ─── Per-chat override ────────────────────────────────────────────────────

    public boolean shouldSuppressTypingForChat(long chatId) {
        // Per-chat override: if explicitly set, use it; else use global
        boolean perChatSet = NxygramSettings.getBool("ghost_typing_chat_" + chatId + "_set", false);
        if (perChatSet) {
            return NxygramSettings.getBool("ghost_typing_chat_" + chatId, false);
        }
        return shouldSuppressTyping();
    }

    public void setChatGhostOverride(long chatId, boolean suppress) {
        NxygramSettings.setBool("ghost_typing_chat_" + chatId + "_set", true);
        NxygramSettings.setBool("ghost_typing_chat_" + chatId, suppress);
    }

    public void clearChatGhostOverride(long chatId) {
        NxygramSettings.setBool("ghost_typing_chat_" + chatId + "_set", false);
    }

    // ─── Telegram hook guide ──────────────────────────────────────────────────
    /*
     * ALL HOOKS ARE IN MessagesController.java
     * ════════════════════════════════════════
     *
     * 1. TYPING INDICATOR
     *    Find: sendTyping(long dialogId, int action, ...)
     *    or:   TL_messages_setTyping request construction
     *    Wrap with:
     *      if (!GhostModeManager.getInstance().shouldSuppressTypingForChat(dialogId)) {
     *          // original typing send code
     *      }
     *
     * 2. ONLINE STATUS
     *    Find: updateStatus() or setOnlineStatus() or TL_account_updateStatus
     *    Wrap with:
     *      if (!GhostModeManager.getInstance().shouldSuppressOnline()) {
     *          // original online update code
     *      }
     *
     * 3. RECORDING INDICATOR
     *    Find: where TLRPC.TL_sendMessageRecordAudioAction or
     *          TL_sendMessageRecordVideoAction is sent
     *    Wrap with:
     *      if (!GhostModeManager.getInstance().shouldSuppressRecording()) {
     *          // original recording indicator code
     *      }
     *
     * 4. UPLOAD INDICATOR
     *    Find: where TL_sendMessageUploadPhotoAction /
     *          TL_sendMessageUploadDocumentAction are sent
     *    Wrap with:
     *      if (!GhostModeManager.getInstance().shouldSuppressRecording()) {
     *          // original upload indicator code
     *      }
     *
     * 5. READ RECEIPTS
     *    Find: markDialogAsRead() or TL_messages_readHistory
     *    For read receipts specifically (1-on-1 chats):
     *      if (!GhostModeManager.getInstance().shouldSuppressReadReceipts()) {
     *          // original read receipt code
     *      }
     *    NOTE: This prevents the server from recording that you read it.
     *    Messages will re-appear as unread on next launch if not carefully handled.
     *    Recommend: mark read locally but delay/skip the server call.
     *
     * 6. STORY VIEW
     *    Find: where TL_stories_incrementStoryViews is called
     *    Wrap with:
     *      if (!NxygramSettings.getBool("ghost_story_views", false)) {
     *          // original story view report code
     *      }
     */
}
