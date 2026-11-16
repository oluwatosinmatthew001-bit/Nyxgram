package com.nxygram;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * ChatLockActivity
 * Manage chat locks, hidden folder, and per-chat PIN settings.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.ChatLockActivity"
 *             android:exported="false"/>
 */
public class ChatLockActivity extends AppCompatActivity {

    private ChatLockManager lockMgr;
    private LinearLayout lockedListLayout;
    private LinearLayout hiddenListLayout;
    private NxygramThemeManager.NxygramTheme t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        lockMgr = ChatLockManager.getInstance(this);
        t       = NxygramThemeManager.getCurrentTheme();
        getWindow().getDecorView().setBackgroundColor(t.colorBackground);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root);
        setContentView(sv);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🔐 Chat Lock");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        buildUI(root);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private void buildUI(LinearLayout root) {

        // ── How to lock a chat ─────────────────────────────────────────────
        addInfoBanner(root,
                "To lock a chat: long-press any chat in the main list, " +
                "then tap 'Lock Chat'. Or use the ⋮ menu inside any chat.");

        // ── Locked Chats ───────────────────────────────────────────────────
        addSection(root, "🔒 Locked Chats");

        lockedListLayout = new LinearLayout(this);
        lockedListLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(lockedListLayout);
        refreshLockedList();

        // ── Hidden Folder ──────────────────────────────────────────────────
        addSection(root, "👁️ Hidden Chats Folder");

        addInfoBanner(root,
                "Hidden chats disappear from the main chat list entirely. " +
                "They only appear after you enter the hidden folder PIN.");

        // Set/change hidden folder PIN
        addActionRow(root, "Set Hidden Folder PIN",
                lockMgr.hasHiddenFolderPin() ? "PIN is set" : "No PIN set — uses App Lock PIN",
                this::showSetHiddenPinDialog);

        // Open hidden folder
        addActionRow(root, "View Hidden Chats",
                lockMgr.getHiddenChatIds().size() + " hidden chat(s)",
                this::showHiddenFolderUnlock);

        // Hidden chats list (only shown when folder is unlocked)
        hiddenListLayout = new LinearLayout(this);
        hiddenListLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(hiddenListLayout);

        // ── Lock all on background ─────────────────────────────────────────
        addSection(root, "Options");

        addActionRow(root, "Lock All Chats Now",
                "Re-lock all chats that are unlocked this session",
                () -> {
                    lockMgr.lockAllChatsThisSession();
                    lockMgr.setHiddenFolderUnlocked(false);
                    toast("All chats locked");
                });
    }

    // ─── Locked list ───────────────────────────────────────────────────────

    private void refreshLockedList() {
        lockedListLayout.removeAllViews();
        List<Long> ids = lockMgr.getAllLockedChatIds();

        if (ids.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No locked chats. Long-press a chat to lock it.");
            empty.setTextColor(t.colorTextSecondary);
            empty.setTextSize(13);
            empty.setPadding(dp(16), dp(12), dp(16), dp(12));
            lockedListLayout.addView(empty);
            return;
        }

        for (long chatId : ids) {
            addLockedChatRow(chatId);
        }
    }

    private void addLockedChatRow(long chatId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView chatName = new TextView(this);
        chatName.setText("Chat " + chatId);  // Replace with actual name via Telegram API
        chatName.setTextSize(14);
        chatName.setTextColor(t.colorText);

        TextView details = new TextView(this);
        details.setText(lockMgr.getLockType(chatId)
                + (lockMgr.isChatHidden(chatId) ? " • hidden" : " • visible in list"));
        details.setTextSize(12);
        details.setTextColor(t.colorTextSecondary);

        info.addView(chatName);
        info.addView(details);

        TextView unlockBtn = new TextView(this);
        unlockBtn.setText("Unlock");
        unlockBtn.setTextSize(12);
        unlockBtn.setTextColor(0xFFFF5252);
        unlockBtn.setPadding(dp(10), dp(4), dp(4), dp(4));
        unlockBtn.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Unlock Chat")
                .setMessage("Remove lock from this chat permanently?")
                .setPositiveButton("Unlock", (d, w) -> {
                    lockMgr.unlockChatPermanently(chatId);
                    refreshLockedList();
                    toast("Chat unlocked");
                })
                .setNegativeButton("Cancel", null)
                .show());

        row.addView(info);
        row.addView(unlockBtn);
        addDivider(row);
        lockedListLayout.addView(row);
    }

    // ─── Hidden folder ─────────────────────────────────────────────────────

    private void showHiddenFolderUnlock() {
        if (lockMgr.isHiddenFolderVisible()) {
            // Already unlocked — show list
            showHiddenChatList();
            return;
        }

        // Show PIN entry
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));
        layout.setBackgroundColor(t.colorSurface);

        TextView label = new TextView(this);
        label.setText("Enter hidden folder PIN:");
        label.setTextColor(t.colorTextSecondary);
        label.setTextSize(13);
        layout.addView(label);

        EditText pinEdit = new EditText(this);
        pinEdit.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                             android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pinEdit.setHint("PIN");
        pinEdit.setTextColor(t.colorText);
        pinEdit.setHintTextColor(t.colorTextSecondary);
        layout.addView(pinEdit);

        new AlertDialog.Builder(this)
                .setTitle("🔒 Hidden Chats")
                .setView(layout)
                .setPositiveButton("Unlock", (d, w) -> {
                    String pin = pinEdit.getText().toString().trim();
                    if (lockMgr.verifyHiddenFolderPin(pin)) {
                        lockMgr.setHiddenFolderUnlocked(true);
                        toast("Folder unlocked");
                        showHiddenChatList();
                    } else {
                        toast("Wrong PIN");
                    }
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showHiddenChatList() {
        hiddenListLayout.removeAllViews();
        List<Long> ids = lockMgr.getHiddenChatIds();
        if (ids.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No hidden chats. When locking a chat, enable 'Hide from list'.");
            empty.setTextColor(t.colorTextSecondary);
            empty.setTextSize(13);
            empty.setPadding(dp(16), dp(12), dp(16), dp(12));
            hiddenListLayout.addView(empty);
            return;
        }
        for (long chatId : ids) {
            addHiddenChatRow(chatId);
        }
    }

    private void addHiddenChatRow(long chatId) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView name = new TextView(this);
        name.setText("Hidden Chat " + chatId);
        name.setTextSize(14);
        name.setTextColor(t.colorPrimary);
        name.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView openBtn = new TextView(this);
        openBtn.setText("Open →");
        openBtn.setTextSize(12);
        openBtn.setTextColor(t.colorPrimary);
        openBtn.setOnClickListener(v -> {
            lockMgr.markUnlockedThisSession(chatId);
            GlobalSearchManager.openMessage(this, chatId, 0, 0);
        });

        row.addView(name);
        row.addView(openBtn);
        addDivider(row);
        hiddenListLayout.addView(row);
    }

    private void showSetHiddenPinDialog() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(16), dp(20), dp(8));
        layout.setBackgroundColor(t.colorSurface);

        EditText pin1 = new EditText(this);
        pin1.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                          android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin1.setHint("Enter new PIN (4-6 digits)");
        pin1.setTextColor(t.colorText);

        EditText pin2 = new EditText(this);
        pin2.setInputType(android.text.InputType.TYPE_CLASS_NUMBER |
                          android.text.InputType.TYPE_NUMBER_VARIATION_PASSWORD);
        pin2.setHint("Confirm PIN");
        pin2.setTextColor(t.colorText);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(10);
        pin2.setLayoutParams(lp);

        layout.addView(pin1);
        layout.addView(pin2);

        new AlertDialog.Builder(this)
                .setTitle("Set Hidden Folder PIN")
                .setView(layout)
                .setPositiveButton("Set PIN", (d, w) -> {
                    String p1 = pin1.getText().toString().trim();
                    String p2 = pin2.getText().toString().trim();
                    if (p1.length() < 4) { toast("PIN must be at least 4 digits"); return; }
                    if (!p1.equals(p2))  { toast("PINs don't match"); return; }
                    lockMgr.setHiddenFolderPin(p1);
                    toast("Hidden folder PIN set ✓");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── View helpers ───────────────────────────────────────────────────────

    private void addSection(LinearLayout parent, String title) {
        TextView tv = new TextView(this);
        tv.setText(title.toUpperCase());
        tv.setTextSize(11);
        tv.setTextColor(t.colorTextSecondary);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(dp(16), dp(20), dp(16), dp(6));
        parent.addView(tv);
    }

    private void addInfoBanner(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(t.colorTextSecondary);
        tv.setBackgroundColor(t.colorBackground);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        parent.addView(tv);
    }

    private void addActionRow(LinearLayout parent, String title,
                               String sub, Runnable action) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setOnClickListener(v -> action.run());

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(15);
        titleTv.setTextColor(t.colorText);
        text.addView(titleTv);

        if (sub != null && !sub.isEmpty()) {
            TextView subTv = new TextView(this);
            subTv.setText(sub);
            subTv.setTextSize(12);
            subTv.setTextColor(t.colorTextSecondary);
            text.addView(subTv);
        }

        TextView arrow = new TextView(this);
        arrow.setText("›");
        arrow.setTextSize(22);
        arrow.setTextColor(t.colorTextSecondary);

        row.addView(text);
        row.addView(arrow);
        addDivider(row);
        parent.addView(row);
    }

    private void addDivider(ViewGroup parent) {
        View div = new View(this);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(div);
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
