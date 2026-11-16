package com.nxygram;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.format.DateUtils;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * DeletedMessagesActivity
 * Shows locally preserved deleted + edited messages for a given chat.
 *
 * Launch from chat context menu:
 *   Intent i = new Intent(context, DeletedMessagesActivity.class);
 *   i.putExtra("chat_id", chatId);
 *   i.putExtra("account_id", accountId);
 *   context.startActivity(i);
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.DeletedMessagesActivity" android:exported="false"/>
 */
public class DeletedMessagesActivity extends AppCompatActivity {

    private static final int TAB_DELETED = 0;
    private static final int TAB_EDITED  = 1;

    private long chatId;
    private int  accountId;
    private int  currentTab = TAB_DELETED;
    private RecyclerView rv;
    private TextView tabDeleted, tabEdited;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);

        chatId    = getIntent().getLongExtra("chat_id", 0);
        accountId = getIntent().getIntExtra("account_id", 0);

        setContentView(buildUI());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("Preserved Messages");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        loadTab(TAB_DELETED);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private View buildUI() {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorBackground);

        // Tab bar
        LinearLayout tabs = new LinearLayout(this);
        tabs.setOrientation(LinearLayout.HORIZONTAL);
        tabs.setBackgroundColor(t.colorSurface);

        tabDeleted = makeTab("🗑️ Deleted", t);
        tabEdited  = makeTab("✏️ Edited", t);

        tabDeleted.setOnClickListener(v -> loadTab(TAB_DELETED));
        tabEdited.setOnClickListener(v -> loadTab(TAB_EDITED));

        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tabDeleted.setLayoutParams(tp);
        tabEdited.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        tabs.addView(tabDeleted);
        tabs.addView(tabEdited);
        root.addView(tabs);

        // Divider
        View div = new View(this);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(1)));
        root.addView(div);

        // List
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        rv.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));
        root.addView(rv);

        // Clear button
        TextView clearBtn = new TextView(this);
        clearBtn.setText("Clear preserved messages for this chat");
        clearBtn.setTextColor(0xFFFF5252);
        clearBtn.setTextSize(13);
        clearBtn.setGravity(Gravity.CENTER);
        clearBtn.setPadding(0, dp(14), 0, dp(14));
        clearBtn.setBackgroundColor(t.colorSurface);
        clearBtn.setOnClickListener(v -> confirmClear());
        root.addView(clearBtn);

        return root;
    }

    private TextView makeTab(String label, NxygramThemeManager.NxygramTheme t) {
        TextView tv = new TextView(this);
        tv.setText(label);
        tv.setTextSize(14);
        tv.setGravity(Gravity.CENTER);
        tv.setPadding(0, dp(14), 0, dp(14));
        tv.setTextColor(t.colorText);
        return tv;
    }

    // ─── Tab Loading ──────────────────────────────────────────────────────────

    private void loadTab(int tab) {
        currentTab = tab;
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        tabDeleted.setTextColor(tab == TAB_DELETED ? t.colorPrimary : t.colorTextSecondary);
        tabEdited.setTextColor(tab == TAB_EDITED   ? t.colorPrimary : t.colorTextSecondary);

        AntiDeleteManager mgr = AntiDeleteManager.getInstance(this);

        if (tab == TAB_DELETED) {
            List<AntiDeleteManager.DeletedMessage> msgs = mgr.getDeletedMessages(chatId, accountId);
            rv.setAdapter(new DeletedAdapter(this, msgs));
        } else {
            // For edited tab, show all edit history across all messages in this chat
            // We load a combined view by querying edited messages — a real implementation
            // would query across all msg_ids for this chatId
            List<AntiDeleteManager.EditedMessage> edited = new ArrayList<>();
            // Real hook: add getEditedMessagesForChat(chatId, accountId) to AntiDeleteManager
            rv.setAdapter(new EditedAdapter(this, edited));
        }
    }

    // ─── Adapters ─────────────────────────────────────────────────────────────

    static class DeletedAdapter extends RecyclerView.Adapter<DeletedAdapter.VH> {
        private final Context ctx;
        private final List<AntiDeleteManager.DeletedMessage> items;

        DeletedAdapter(Context ctx, List<AntiDeleteManager.DeletedMessage> items) {
            this.ctx = ctx; this.items = items;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(makeItemView(ctx));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AntiDeleteManager.DeletedMessage m = items.get(pos);
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

            h.sender.setText(m.senderName != null && !m.senderName.isEmpty()
                    ? m.senderName : "User " + m.senderId);
            h.text.setText(m.messageText != null ? m.messageText : "[Media message]");
            h.time.setText("Deleted: " + formatTime(m.dateDeleted)
                    + "  •  Sent: " + formatTime(m.dateSent));
            h.badge.setText("DELETED");

            h.sender.setTextColor(t.colorPrimary);
            h.text.setTextColor(t.colorText);
            h.time.setTextColor(t.colorTextSecondary);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView sender, text, time, badge;
            VH(View v) {
                super(v);
                sender = v.findViewWithTag("sender");
                text   = v.findViewWithTag("text");
                time   = v.findViewWithTag("time");
                badge  = v.findViewWithTag("badge");
            }
        }
    }

    static class EditedAdapter extends RecyclerView.Adapter<EditedAdapter.VH> {
        private final Context ctx;
        private final List<AntiDeleteManager.EditedMessage> items;

        EditedAdapter(Context ctx, List<AntiDeleteManager.EditedMessage> items) {
            this.ctx = ctx; this.items = items;
        }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(makeItemView(ctx));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            AntiDeleteManager.EditedMessage m = items.get(pos);
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

            h.sender.setText(m.senderName != null ? m.senderName : "User " + m.senderId);
            h.text.setText("Original: " + m.originalText + "\n→ Edited: " + m.editedText);
            h.time.setText("Edited: " + formatTime(m.dateEdited) + "  v" + m.editVersion);
            h.badge.setText("EDITED");

            h.sender.setTextColor(t.colorAccent);
            h.text.setTextColor(t.colorText);
            h.time.setTextColor(t.colorTextSecondary);
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView sender, text, time, badge;
            VH(View v) {
                super(v);
                sender = v.findViewWithTag("sender");
                text   = v.findViewWithTag("text");
                time   = v.findViewWithTag("time");
                badge  = v.findViewWithTag("badge");
            }
        }
    }

    // ─── Shared item view ─────────────────────────────────────────────────────

    private static View makeItemView(Context ctx) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout card = new LinearLayout(ctx);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(dp2(ctx,16), dp2(ctx,12), dp2(ctx,16), dp2(ctx,12));
        card.setBackgroundColor(t.colorSurface);

        LinearLayout header = new LinearLayout(ctx);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView sender = new TextView(ctx); sender.setTag("sender");
        sender.setTextSize(14); sender.setTextColor(t.colorPrimary);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        sender.setLayoutParams(sp);

        TextView badge = new TextView(ctx); badge.setTag("badge");
        badge.setTextSize(10); badge.setTextColor(0xFFFF5252);
        badge.setBackgroundColor(0x22FF5252);
        badge.setPadding(dp2(ctx,6), dp2(ctx,2), dp2(ctx,6), dp2(ctx,2));

        header.addView(sender); header.addView(badge);

        TextView text = new TextView(ctx); text.setTag("text");
        text.setTextSize(14); text.setTextColor(t.colorText);
        text.setPadding(0, dp2(ctx,4), 0, dp2(ctx,4));

        TextView time = new TextView(ctx); time.setTag("time");
        time.setTextSize(11); time.setTextColor(t.colorTextSecondary);

        // Divider at bottom
        View div = new View(ctx);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));

        card.addView(header);
        card.addView(text);
        card.addView(time);
        card.addView(div);
        return card;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void confirmClear() {
        new AlertDialog.Builder(this)
                .setTitle("Clear Preserved Messages")
                .setMessage("Remove all locally saved deleted and edited messages for this chat?")
                .setPositiveButton("Clear", (d, w) -> {
                    AntiDeleteManager.getInstance(this).clearChat(chatId, accountId);
                    loadTab(currentTab);
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private static String formatTime(long unixSeconds) {
        if (unixSeconds <= 0) return "unknown";
        return DateUtils.getRelativeTimeSpanString(
                unixSeconds * 1000L,
                System.currentTimeMillis(),
                DateUtils.MINUTE_IN_MILLIS
        ).toString();
    }

    private int dp(int dp) { return (int)(dp * getResources().getDisplayMetrics().density); }
    private static int dp2(Context ctx, int dp) {
        return (int)(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
