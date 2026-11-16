package com.nxygram;

import android.content.Context;
import android.os.Bundle;
import android.text.TextWatcher;
import android.text.Editable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * NxygramAiChatActivity
 * ══════════════════════════════════════════════════════════════
 * Full in-app AI assistant chat screen.
 * Features: conversation memory (state), web search toggle, media input.
 * Powered entirely by PrexzyApi.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.NxygramAiChatActivity" android:exported="false"/>
 *
 * Launch from NxygramSettingsActivity or a floating AI button in ChatActivity.
 */
public class NxygramAiChatActivity extends AppCompatActivity {

    private static class Message {
        static final int ROLE_USER      = 0;
        static final int ROLE_ASSISTANT = 1;
        static final int ROLE_SYSTEM    = 2;
        int    role;
        String text;
        Message(int role, String text) { this.role = role; this.text = text; }
    }

    private final List<Message> messages  = new ArrayList<>();
    private final StringBuilder stateLog  = new StringBuilder();
    private RecyclerView        rv;
    private MessageAdapter      adapter;
    private EditText            inputEdit;
    private ProgressBar         spinner;
    private Switch              webSearchSwitch;
    private boolean             isThinking = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);

        setContentView(buildUI());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("✦ Nxygram AI");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Welcome message
        addMessage(Message.ROLE_SYSTEM,
            "Hi! I'm your Nxygram AI assistant.\n"
          + "I can help you with:\n"
          + "• Answering questions\n"
          + "• Live web search (toggle below)\n"
          + "• Translating text\n"
          + "• Grammar & writing\n"
          + "• Summarizing conversations\n\n"
          + "What can I help you with?");
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private View buildUI() {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorBackground);

        // Message list
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new MessageAdapter(this, messages);
        rv.setAdapter(adapter);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);
        root.addView(rv);

        // Web search toggle bar
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setBackgroundColor(t.colorSurface);
        searchBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView searchLabel = new TextView(this);
        searchLabel.setText("🔍 Web Search");
        searchLabel.setTextSize(13);
        searchLabel.setTextColor(t.colorTextSecondary);
        LinearLayout.LayoutParams slp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        searchLabel.setLayoutParams(slp);

        webSearchSwitch = new Switch(this);
        webSearchSwitch.setChecked(false);

        searchBar.addView(searchLabel);
        searchBar.addView(webSearchSwitch);
        root.addView(searchBar);

        // Thinking indicator
        LinearLayout thinkingRow = new LinearLayout(this);
        thinkingRow.setOrientation(LinearLayout.HORIZONTAL);
        thinkingRow.setPadding(dp(12), dp(4), dp(12), dp(4));
        thinkingRow.setGravity(android.view.Gravity.CENTER_VERTICAL);
        thinkingRow.setVisibility(View.GONE);
        thinkingRow.setId(android.R.id.progress);

        spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        LinearLayout.LayoutParams spLp = new LinearLayout.LayoutParams(dp(18), dp(18));
        spLp.setMargins(0, 0, dp(8), 0);
        spinner.setLayoutParams(spLp);

        TextView thinking = new TextView(this);
        thinking.setText("AI is thinking…");
        thinking.setTextSize(12);
        thinking.setTextColor(t.colorTextSecondary);

        thinkingRow.addView(spinner);
        thinkingRow.addView(thinking);
        root.addView(thinkingRow);

        // Input bar
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        inputBar.setBackgroundColor(t.colorSurface);
        inputBar.setPadding(dp(8), dp(8), dp(8), dp(8));
        inputBar.setGravity(android.view.Gravity.CENTER_VERTICAL);

        inputEdit = new EditText(this);
        inputEdit.setHint("Ask me anything…");
        inputEdit.setTextSize(15);
        inputEdit.setTextColor(t.colorText);
        inputEdit.setHintTextColor(t.colorTextSecondary);
        inputEdit.setBackgroundColor(t.colorInputBg);
        inputEdit.setPadding(dp(12), dp(10), dp(12), dp(10));
        inputEdit.setMaxLines(4);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        editLp.setMargins(0, 0, dp(8), 0);
        inputEdit.setLayoutParams(editLp);

        TextView sendBtn = new TextView(this);
        sendBtn.setText("➤");
        sendBtn.setTextSize(22);
        sendBtn.setTextColor(t.colorPrimary);
        sendBtn.setPadding(dp(4), dp(4), dp(4), dp(4));
        sendBtn.setOnClickListener(v -> sendMessage());

        // Also send on keyboard action
        inputEdit.setOnEditorActionListener((v, actionId, event) -> {
            sendMessage(); return true;
        });

        inputBar.addView(inputEdit);
        inputBar.addView(sendBtn);
        root.addView(inputBar);

        return root;
    }

    // ─── Send message ─────────────────────────────────────────────────────────

    private void sendMessage() {
        String text = inputEdit.getText().toString().trim();
        if (text.isEmpty() || isThinking) return;

        inputEdit.setText("");
        addMessage(Message.ROLE_USER, text);

        // Update state log for context
        stateLog.append("User: ").append(text).append("\n");

        setThinking(true);

        boolean useWebSearch = webSearchSwitch.isChecked();

        PrexzyApi.Request req = PrexzyApi.call(this)
            .prompt(text)
            .state(stateLog.length() > 2000
                    ? stateLog.substring(stateLog.length() - 2000)
                    : stateLog.toString())
            .webSearch(useWebSearch)
            .onResult(result -> {
                setThinking(false);
                stateLog.append("Assistant: ").append(result).append("\n");
                addMessage(Message.ROLE_ASSISTANT, result);
            })
            .onError(err -> {
                setThinking(false);
                addMessage(Message.ROLE_SYSTEM, "⚠️ Error: " + err);
            });

        req.execute();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void addMessage(int role, String text) {
        messages.add(new Message(role, text));
        adapter.notifyItemInserted(messages.size() - 1);
        rv.smoothScrollToPosition(messages.size() - 1);
    }

    private void setThinking(boolean thinking) {
        isThinking = thinking;
        View thinkingRow = findViewById(android.R.id.progress);
        if (thinkingRow != null)
            thinkingRow.setVisibility(thinking ? View.VISIBLE : View.GONE);
    }

    // ─── Adapter ──────────────────────────────────────────────────────────────

    static class MessageAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
        private final Context       ctx;
        private final List<Message> items;

        MessageAdapter(Context ctx, List<Message> items) {
            this.ctx = ctx; this.items = items;
        }

        @Override public int getItemViewType(int pos) { return items.get(pos).role; }

        @NonNull @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int type) {
            return new MsgVH(makeBubble(ctx, type));
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int pos) {
            Message m = items.get(pos);
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
            TextView tv = (TextView) ((LinearLayout) holder.itemView).getChildAt(0);
            tv.setText(m.text);
            switch (m.role) {
                case Message.ROLE_USER:
                    tv.setBackgroundColor(t.bubbleOut);
                    tv.setTextColor(t.bubbleOutText);
                    ((LinearLayout) holder.itemView).setGravity(Gravity.END);
                    break;
                case Message.ROLE_ASSISTANT:
                    tv.setBackgroundColor(t.bubbleIn);
                    tv.setTextColor(t.bubbleInText);
                    ((LinearLayout) holder.itemView).setGravity(Gravity.START);
                    break;
                case Message.ROLE_SYSTEM:
                    tv.setBackgroundColor(t.colorSurface);
                    tv.setTextColor(t.colorTextSecondary);
                    ((LinearLayout) holder.itemView).setGravity(Gravity.CENTER);
                    break;
            }
        }

        @Override public int getItemCount() { return items.size(); }

        static class MsgVH extends RecyclerView.ViewHolder {
            MsgVH(View v) { super(v); }
        }

        private static View makeBubble(Context ctx, int type) {
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
            int density = (int) ctx.getResources().getDisplayMetrics().density;
            LinearLayout row = new LinearLayout(ctx);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            rowLp.setMargins(0, 4 * density, 0, 4 * density);
            row.setLayoutParams(rowLp);

            TextView tv = new TextView(ctx);
            tv.setTextSize(14);
            tv.setPadding(12 * density, 8 * density, 12 * density, 8 * density);
            int maxWidthDp = 280;
            tv.setMaxWidth(maxWidthDp * density);
            LinearLayout.LayoutParams tvLp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            tvLp.setMargins(16 * density, 0, 16 * density, 0);
            tv.setLayoutParams(tvLp);

            row.addView(tv);
            return row;
        }
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
