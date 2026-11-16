package com.nxygram;

import android.content.Context;
import android.os.Bundle;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextWatcher;
import android.text.style.BackgroundColorSpan;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * GlobalSearchActivity
 * Search across ALL chats with text, AI semantic search, and filters.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.GlobalSearchActivity"
 *             android:exported="false"
 *             android:windowSoftInputMode="stateAlwaysVisible"/>
 *
 * Launch from main search bar or Nxygram settings.
 */
public class GlobalSearchActivity extends AppCompatActivity {

    private EditText searchInput;
    private RecyclerView rv;
    private ResultAdapter adapter;
    private ProgressBar spinner;
    private TextView statusText;
    private final List<GlobalSearchManager.SearchResult> results = new ArrayList<>();
    private Runnable searchDebounce;
    private static final int ACCOUNT_ID = 0; // current account
    private NxygramThemeManager.NxygramTheme t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        t = NxygramThemeManager.getCurrentTheme();
        getWindow().getDecorView().setBackgroundColor(t.colorBackground);

        setContentView(buildUI());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🔍 Global Search");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        // Auto-focus search
        searchInput.requestFocus();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ─── UI ────────────────────────────────────────────────────────────────

    private View buildUI() {
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorBackground);

        // Search bar
        LinearLayout searchBar = new LinearLayout(this);
        searchBar.setOrientation(LinearLayout.HORIZONTAL);
        searchBar.setBackgroundColor(t.colorSurface);
        searchBar.setPadding(dp(12), dp(8), dp(12), dp(8));
        searchBar.setGravity(Gravity.CENTER_VERTICAL);

        searchInput = new EditText(this);
        searchInput.setHint("Search all chats…");
        searchInput.setHintTextColor(t.colorTextSecondary);
        searchInput.setTextColor(t.colorText);
        searchInput.setTextSize(16);
        searchInput.setBackgroundColor(t.colorInputBg);
        searchInput.setPadding(dp(12), dp(10), dp(12), dp(10));
        searchInput.setSingleLine(true);
        LinearLayout.LayoutParams inputLp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputLp.setMargins(0, 0, dp(8), 0);
        searchInput.setLayoutParams(inputLp);

        // AI search button
        TextView aiBtn = new TextView(this);
        aiBtn.setText("AI");
        aiBtn.setTextSize(13);
        aiBtn.setTextColor(t.colorPrimary);
        aiBtn.setBackgroundColor(t.colorSurface);
        aiBtn.setPadding(dp(8), dp(6), dp(8), dp(6));
        aiBtn.setOnClickListener(v -> triggerAiSearch());

        searchBar.addView(searchInput);
        searchBar.addView(aiBtn);
        root.addView(searchBar);

        // Filter chips
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        chips.setPadding(dp(8), dp(8), dp(8), dp(8));
        chips.setBackgroundColor(t.colorSurface);

        for (String filter : new String[]{"All", "Media", "Links", "Files", "Audio", "Mentions"}) {
            chips.addView(makeFilterChip(filter));
        }
        hsv.addView(chips);
        root.addView(hsv);

        // Status bar
        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setPadding(dp(16), dp(8), dp(16), dp(4));
        statusBar.setGravity(Gravity.CENTER_VERTICAL);

        spinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(18), dp(18));
        sp.setMargins(0, 0, dp(8), 0);
        spinner.setLayoutParams(sp);

        statusText = new TextView(this);
        statusText.setTextSize(12);
        statusText.setTextColor(t.colorTextSecondary);
        statusText.setText("Type to search across all your chats");

        statusBar.addView(spinner);
        statusBar.addView(statusText);
        root.addView(statusBar);

        // Results list
        rv = new RecyclerView(this);
        rv.setLayoutManager(new LinearLayoutManager(this));
        adapter = new ResultAdapter(this, results);
        rv.setAdapter(adapter);
        LinearLayout.LayoutParams rvLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        rv.setLayoutParams(rvLp);
        root.addView(rv);

        // Debounced search on text change
        searchInput.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int i, int c, int a) {}
            public void afterTextChanged(Editable s) {}
            public void onTextChanged(CharSequence s, int i, int b, int c) {
                if (searchDebounce != null)
                    searchInput.removeCallbacks(searchDebounce);
                searchDebounce = () -> performSearch(s.toString());
                searchInput.postDelayed(searchDebounce, 400);
            }
        });

        return root;
    }

    private TextView makeFilterChip(String label) {
        TextView chip = new TextView(this);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(t.colorPrimary);
        chip.setBackgroundColor(t.colorInputBg);
        chip.setPadding(dp(10), dp(5), dp(10), dp(5));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> {
            // Set active filter and re-search
            performSearch(searchInput.getText().toString());
        });
        return chip;
    }

    // ─── Search ────────────────────────────────────────────────────────────

    private void performSearch(String query) {
        if (query == null || query.trim().length() < 2) {
            results.clear();
            adapter.notifyDataSetChanged();
            statusText.setText("Type to search across all your chats");
            spinner.setVisibility(View.GONE);
            return;
        }

        spinner.setVisibility(View.VISIBLE);
        statusText.setText("Searching…");

        GlobalSearchManager.getInstance().search(this, query, ACCOUNT_ID, 50,
                new GlobalSearchManager.SearchCallback() {
                    @Override
                    public void onResults(List<GlobalSearchManager.SearchResult> found,
                                          int total) {
                        spinner.setVisibility(View.GONE);
                        results.clear();
                        results.addAll(found);
                        adapter.setQuery(query);
                        adapter.notifyDataSetChanged();
                        statusText.setText(total > 0
                                ? total + " result" + (total == 1 ? "" : "s")
                                : "No results for \"" + query + "\"");
                    }

                    @Override public void onError(String error) {
                        spinner.setVisibility(View.GONE);
                        statusText.setText("Search unavailable — wire MessagesStorage hook");
                        Toast.makeText(GlobalSearchActivity.this,
                                "See patches/phase7-hooks.md for GlobalSearch setup",
                                Toast.LENGTH_LONG).show();
                    }

                    @Override public void onProgress(int done, int total) {
                        statusText.setText("Searching… " + done + "/" + total);
                    }
                });
    }

    private void triggerAiSearch() {
        String query = searchInput.getText().toString().trim();
        if (query.isEmpty()) {
            Toast.makeText(this, "Type a natural language query first", Toast.LENGTH_SHORT).show();
            return;
        }
        spinner.setVisibility(View.VISIBLE);
        statusText.setText("AI extracting keywords…");
        GlobalSearchManager.getInstance().aiSearch(this, query, ACCOUNT_ID,
                new GlobalSearchManager.SearchCallback() {
                    @Override
                    public void onResults(List<GlobalSearchManager.SearchResult> found, int total) {
                        spinner.setVisibility(View.GONE);
                        results.clear();
                        results.addAll(found);
                        adapter.setQuery(query);
                        adapter.notifyDataSetChanged();
                        statusText.setText("AI search: " + total + " result(s)");
                    }
                    @Override public void onError(String e) {
                        spinner.setVisibility(View.GONE);
                        statusText.setText("AI search error");
                    }
                    @Override public void onProgress(int d, int t) {}
                });
    }

    // ─── Adapter ───────────────────────────────────────────────────────────

    static class ResultAdapter extends RecyclerView.Adapter<ResultAdapter.VH> {
        private final Context ctx;
        private final List<GlobalSearchManager.SearchResult> items;
        private String query = "";

        ResultAdapter(Context ctx, List<GlobalSearchManager.SearchResult> items) {
            this.ctx = ctx; this.items = items;
        }

        void setQuery(String q) { this.query = q; }

        @NonNull @Override
        public VH onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            return new VH(makeItemView(ctx));
        }

        @Override
        public void onBindViewHolder(@NonNull VH h, int pos) {
            GlobalSearchManager.SearchResult r = items.get(pos);
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

            h.chatName.setText(r.dialogName != null ? r.dialogName : "Chat " + r.dialogId);
            h.sender.setText(r.senderName != null ? r.senderName : "");
            h.time.setText(r.date > 0
                    ? android.text.format.DateUtils.getRelativeTimeSpanString(
                            r.date * 1000L, System.currentTimeMillis(),
                            android.text.format.DateUtils.MINUTE_IN_MILLIS).toString()
                    : "");

            // Highlight matched text
            if (r.messageText != null) {
                if (r.matchLength > 0 && r.matchStart >= 0
                        && r.matchStart + r.matchLength <= r.messageText.length()) {
                    SpannableString ss = new SpannableString(r.messageText);
                    ss.setSpan(new BackgroundColorSpan(
                                    adjustAlpha(t.colorPrimary, 0.3f)),
                            r.matchStart, r.matchStart + r.matchLength,
                            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
                    h.message.setText(ss);
                } else {
                    h.message.setText(r.messageText);
                }
            }

            h.chatName.setTextColor(t.colorPrimary);
            h.sender.setTextColor(t.colorTextSecondary);
            h.message.setTextColor(t.colorText);
            h.time.setTextColor(t.colorTextSecondary);

            h.itemView.setOnClickListener(v ->
                    GlobalSearchManager.openMessage(ctx, r.dialogId, r.messageId, 0));
        }

        @Override public int getItemCount() { return items.size(); }

        static class VH extends RecyclerView.ViewHolder {
            TextView chatName, sender, message, time;
            VH(View v) {
                super(v);
                chatName = v.findViewWithTag("chatName");
                sender   = v.findViewWithTag("sender");
                message  = v.findViewWithTag("message");
                time     = v.findViewWithTag("time");
            }
        }

        private static View makeItemView(Context ctx) {
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
            int d = (int)(ctx.getResources().getDisplayMetrics().density);

            LinearLayout card = new LinearLayout(ctx);
            card.setOrientation(LinearLayout.VERTICAL);
            card.setBackgroundColor(t.colorSurface);
            card.setPadding(16*d, 10*d, 16*d, 10*d);

            LinearLayout header = new LinearLayout(ctx);
            header.setOrientation(LinearLayout.HORIZONTAL);

            TextView chatName = new TextView(ctx); chatName.setTag("chatName");
            chatName.setTextSize(14);
            chatName.setTypeface(null, android.graphics.Typeface.BOLD);
            LinearLayout.LayoutParams cnp = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            chatName.setLayoutParams(cnp);

            TextView time = new TextView(ctx); time.setTag("time");
            time.setTextSize(11);

            header.addView(chatName);
            header.addView(time);

            TextView sender = new TextView(ctx); sender.setTag("sender");
            sender.setTextSize(12);
            sender.setPadding(0, 2*d, 0, 2*d);

            TextView message = new TextView(ctx); message.setTag("message");
            message.setTextSize(14);
            message.setMaxLines(2);
            message.setEllipsize(android.text.TextUtils.TruncateAt.END);

            View div = new View(ctx);
            div.setBackgroundColor(t.colorDivider);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));

            card.addView(header);
            card.addView(sender);
            card.addView(message);
            card.addView(div);
            return card;
        }

        private static int adjustAlpha(int color, float f) {
            return (color & 0x00FFFFFF) | ((int)(android.graphics.Color.alpha(color) * f) << 24);
        }
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
