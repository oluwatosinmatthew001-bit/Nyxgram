package com.nxygram;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

/**
 * ReverseChatManager v2
 *
 * Reverse Chat behaviour:
 *
 *   ON  → Every message YOU SEND is displayed as if it came FROM THE RECIPIENT.
 *         - Bubble appears on the LEFT (incoming side)
 *         - Shows recipient's name and avatar
 *         - Timestamp shows normally
 *         - Only visible on YOUR device — nothing changes on the server
 *         - Messages already RECEIVED stay exactly as they are (right side, your bubble)
 *         - Wait — actually per spec: received messages stay as-is
 *           (they already show from the recipient, so no change needed)
 *         - Only YOUR outgoing messages are disguised as theirs
 *
 *   OFF → Messages you sent while ON stay displayed however they were rendered
 *         at the time (we do NOT retroactively un-flip old messages — only
 *         NEW messages after toggling OFF render normally)
 *
 * This is a LOCAL VIEW ONLY feature. Nothing is sent differently to Telegram's servers.
 * The recipient sees a normal conversation.
 *
 * Hook points (all in ChatActivity / message adapter):
 *   isOutgoingDisplayedAsIncoming(msgId, chatId) → true = render as incoming
 *   markMessageReversed(msgId, chatId)            → call when sending while ON
 *   onBindViewHolder → check isOutgoingDisplayedAsIncoming, swap render accordingly
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/ReverseChatManager.java
 */
public class ReverseChatManager {

    private static ReverseChatManager instance;

    // Set of "msgId:chatId" strings for messages that were sent while Reverse was ON
    // These are persisted so they survive app restarts
    private final Map<Long, Set<Integer>> reversedMessages = new HashMap<>();

    public static synchronized ReverseChatManager getInstance() {
        if (instance == null) instance = new ReverseChatManager();
        return instance;
    }

    // ─── Global Toggle ────────────────────────────────────────────────────────

    public boolean isEnabled(long chatId) {
        // Per-chat takes priority over global
        String perChatKey = "reverse_chat_" + chatId;
        if (NxygramSettings.getBool(perChatKey + "_set", false)) {
            return NxygramSettings.getBool(perChatKey, false);
        }
        return NxygramSettings.getBool("reverse_chat_global", false);
    }

    public void setEnabled(long chatId, boolean enabled) {
        NxygramSettings.setBool("reverse_chat_" + chatId + "_set", true);
        NxygramSettings.setBool("reverse_chat_" + chatId, enabled);
    }

    public void setGlobalEnabled(boolean enabled) {
        NxygramSettings.setBool("reverse_chat_global", enabled);
    }

    public boolean isGlobalEnabled() {
        return NxygramSettings.getBool("reverse_chat_global", false);
    }

    // ─── Message Tracking ─────────────────────────────────────────────────────

    /**
     * Call this WHEN YOU SEND a message, if Reverse Chat is currently ON for this chat.
     * Records the message ID so it will always render as incoming, even after restart.
     *
     * Hook in ChatActivity / SendMessagesHelper, right after the message is sent:
     *
     *   if (ReverseChatManager.getInstance().isEnabled(dialog_id)) {
     *       ReverseChatManager.getInstance().markMessageReversed(msgId, dialog_id);
     *   }
     */
    public void markMessageReversed(int msgId, long chatId) {
        // In-memory
        reversedMessages.computeIfAbsent(chatId, k -> new HashSet<>()).add(msgId);
        // Persist — store as comma-separated list per chat
        persistReversedSet(chatId);
    }

    /**
     * Returns true if this outgoing message should be displayed as incoming.
     *
     * Call in the message adapter's onBindViewHolder for EVERY outgoing message:
     *
     *   boolean showAsIncoming = msg.isOutgoing() &&
     *       ReverseChatManager.getInstance().isOutgoingDisplayedAsIncoming(msg.id, dialog_id);
     *
     *   if (showAsIncoming) {
     *       // Render as incoming: left side, recipient avatar, recipient name
     *       holder.setIncomingStyle(recipientName, recipientAvatarBitmap);
     *   } else {
     *       // Normal outgoing render
     *       holder.setOutgoingStyle();
     *   }
     */
    public boolean isOutgoingDisplayedAsIncoming(int msgId, long chatId) {
        // Check in-memory first
        Set<Integer> set = reversedMessages.get(chatId);
        if (set != null && set.contains(msgId)) return true;
        // Check persisted
        return loadReversedSet(chatId).contains(msgId);
    }

    // ─── Persistence ──────────────────────────────────────────────────────────

    private void persistReversedSet(long chatId) {
        Set<Integer> set = reversedMessages.get(chatId);
        if (set == null || set.isEmpty()) return;
        StringBuilder sb = new StringBuilder();
        for (Integer id : set) {
            if (sb.length() > 0) sb.append(",");
            sb.append(id);
        }
        // Keep last 500 message IDs max to avoid growing unboundedly
        String[] parts = sb.toString().split(",");
        if (parts.length > 500) {
            // Trim to last 500
            StringBuilder trimmed = new StringBuilder();
            for (int i = parts.length - 500; i < parts.length; i++) {
                if (trimmed.length() > 0) trimmed.append(",");
                trimmed.append(parts[i]);
            }
            NxygramSettings.setString("reverse_ids_" + chatId, trimmed.toString());
        } else {
            NxygramSettings.setString("reverse_ids_" + chatId, sb.toString());
        }
    }

    private Set<Integer> loadReversedSet(long chatId) {
        // Load into memory if not already there
        if (!reversedMessages.containsKey(chatId)) {
            String stored = NxygramSettings.getString("reverse_ids_" + chatId, "");
            Set<Integer> set = new HashSet<>();
            if (!stored.isEmpty()) {
                for (String part : stored.split(",")) {
                    try { set.add(Integer.parseInt(part.trim())); }
                    catch (NumberFormatException ignored) {}
                }
            }
            reversedMessages.put(chatId, set);
        }
        return reversedMessages.getOrDefault(chatId, new HashSet<>());
    }

    // ─── Clear ────────────────────────────────────────────────────────────────

    /**
     * Clear reversed state for a chat (e.g. user turns off and wants a fresh start).
     * Existing messages stay as rendered — only future sends are affected.
     */
    public void clearReversedHistory(long chatId) {
        reversedMessages.remove(chatId);
        NxygramSettings.setString("reverse_ids_" + chatId, "");
    }

    // ─── Adapter Helper ───────────────────────────────────────────────────────

    /**
     * All-in-one check for the adapter. Returns the render mode for a message.
     *
     * Usage in onBindViewHolder:
     *   RenderMode mode = ReverseChatManager.getInstance().getRenderMode(msg, dialog_id);
     *   switch (mode) {
     *       case NORMAL_OUTGOING:   // your message, right side, your bubble
     *       case NORMAL_INCOMING:   // their message, left side, their bubble (unchanged)
     *       case REVERSED_OUTGOING: // your message disguised as theirs, left side, their bubble
     *   }
     */
    public enum RenderMode {
        NORMAL_OUTGOING,
        NORMAL_INCOMING,
        REVERSED_OUTGOING
    }

    public RenderMode getRenderMode(boolean isOutgoing, int msgId, long chatId) {
        if (!isOutgoing) return RenderMode.NORMAL_INCOMING;
        if (isOutgoingDisplayedAsIncoming(msgId, chatId)) return RenderMode.REVERSED_OUTGOING;
        return RenderMode.NORMAL_OUTGOING;
    }

    // ─── Telegram Adapter Hook Guide ──────────────────────────────────────────
    /*
     * IN TELEGRAM SOURCE: ChatMessageCell.java (or the message ViewHolder)
     *
     * Find the section that decides incoming vs outgoing layout.
     * It usually looks like:
     *
     *   boolean isOutgoing = currentMessage.isOutgoing();
     *   if (isOutgoing) {
     *       // position bubble on right, use outgoing colors
     *   } else {
     *       // position bubble on left, show sender name/avatar
     *   }
     *
     * REPLACE or EXTEND with:
     *
     *   boolean isOutgoing = currentMessage.isOutgoing();
     *   ReverseChatManager.RenderMode renderMode = ReverseChatManager.getInstance()
     *       .getRenderMode(isOutgoing, currentMessage.id, dialog_id);
     *
     *   boolean showAsIncoming = (renderMode == ReverseChatManager.RenderMode.NORMAL_INCOMING)
     *                         || (renderMode == ReverseChatManager.RenderMode.REVERSED_OUTGOING);
     *
     *   if (showAsIncoming) {
     *       // LEFT side layout
     *       // If REVERSED_OUTGOING: use recipient's name + avatar instead of your own
     *       String displayName = (renderMode == ReverseChatManager.RenderMode.REVERSED_OUTGOING)
     *           ? recipientName   // the other person's name
     *           : senderName;     // normal incoming sender
     *       // set avatar to recipient's avatar
     *       // set bubble color to incoming bubble color
     *   } else {
     *       // RIGHT side layout (normal outgoing)
     *   }
     *
     * IN SendMessagesHelper.java or ChatActivity.java, find where a message is enqueued/sent:
     *
     *   // After messageObject is created but before it's inserted into the DB / sent:
     *   if (ReverseChatManager.getInstance().isEnabled(dialog_id)) {
     *       ReverseChatManager.getInstance().markMessageReversed(messageObject.getId(), dialog_id);
     *   }
     *
     * IN ChatActivity — toolbar toggle button:
     *
     *   boolean currentlyOn = ReverseChatManager.getInstance().isEnabled(dialog_id);
     *   ReverseChatManager.getInstance().setEnabled(dialog_id, !currentlyOn);
     *   chatListView.getAdapter().notifyDataSetChanged(); // re-render all messages
     *   // Show toast:
     *   Toast.makeText(context,
     *       currentlyOn ? "Reverse Chat OFF" : "Reverse Chat ON — your messages will appear as theirs",
     *       Toast.LENGTH_SHORT).show();
     */
}
