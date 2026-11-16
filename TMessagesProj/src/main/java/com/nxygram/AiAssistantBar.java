package com.nxygram;

import android.content.Context;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONArray;

/**
 * AiAssistantBar v2
 * ══════════════════════════════════════════════════════════════
 * Horizontal chip bar above message input.
 * Powered entirely by PrexzyApi — no API key setup required.
 *
 * Hook in ChatActivity:
 *   AiAssistantBar aiBar = new AiAssistantBar(context, messageEditText, dialog_id);
 *   inputContainer.addView(aiBar.getView(), 0);
 *
 *   // On new incoming message:
 *   aiBar.onNewMessage(messageText);
 *
 *   // On user typing:
 *   aiBar.onUserTyping(draft);
 */
public class AiAssistantBar {

    private final Context  context;
    private final EditText inputField;
    private final long     dialogId;
    private final LinearLayout container;
    private final LinearLayout chipsRow;
    private final ProgressBar  spinner;

    public AiAssistantBar(Context context, EditText inputField, long dialogId) {
        this.context    = context;
        this.inputField = inputField;
        this.dialogId   = dialogId;

        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        container.setBackgroundColor(t.colorSurface);
        container.setVisibility(View.GONE);

        View div = new View(context);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        container.addView(div);

        spinner = new ProgressBar(context, null, android.R.attr.progressBarStyleSmall);
        spinner.setVisibility(View.GONE);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(20), dp(20));
        sp.setMargins(dp(12), dp(8), 0, dp(8));
        spinner.setLayoutParams(sp);

        HorizontalScrollView hsv = new HorizontalScrollView(context);
        hsv.setHorizontalScrollBarEnabled(false);
        chipsRow = new LinearLayout(context);
        chipsRow.setOrientation(LinearLayout.HORIZONTAL);
        chipsRow.setPadding(dp(8), dp(6), dp(8), dp(6));
        hsv.addView(chipsRow);

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(spinner);
        row.addView(hsv);
        container.addView(row);
    }

    public View getView() { return container; }

    // ─── Events ───────────────────────────────────────────────────────────────

    public void onNewMessage(String messageText) {
        if (!NxygramSettings.isAiAssistant()) return;
        if (!NxygramSettings.isSmartReply())  return;
        if (messageText == null || messageText.trim().isEmpty()) return;

        showSpinner(true);
        show();

        PrexzyApi.smartReply(context, messageText, null,
            result -> {
                showSpinner(false);
                try {
                    String cleaned = result.trim();
                    int s = cleaned.indexOf('['), e = cleaned.lastIndexOf(']');
                    if (s >= 0 && e > s) cleaned = cleaned.substring(s, e + 1);
                    JSONArray arr = new JSONArray(cleaned);
                    String[] suggestions = new String[arr.length()];
                    for (int i = 0; i < arr.length(); i++) suggestions[i] = arr.getString(i);
                    showSmartReplies(suggestions);
                } catch (Exception ex) {
                    hide();
                }
            },
            err -> { showSpinner(false); hide(); }
        );
    }

    public void onUserTyping(String draftText) {
        if (!NxygramSettings.isAiAssistant()) return;
        if (draftText == null || draftText.trim().length() < 10) { hide(); return; }
        showDraftActionChips(draftText);
    }

    // ─── Chip views ───────────────────────────────────────────────────────────

    private void showSmartReplies(String[] suggestions) {
        chipsRow.removeAllViews();
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        chipsRow.addView(makeLabel("💬", t));
        for (String s : suggestions) {
            chipsRow.addView(makeReplyChip(s, t));
        }
        chipsRow.addView(makeSeparator(t));
        addAiActionChips(inputField.getText().toString(), t);
        show();
    }

    private void showDraftActionChips(String draft) {
        chipsRow.removeAllViews();
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        chipsRow.addView(makeLabel("✨", t));
        addAiActionChips(draft, t);
        show();
    }

    private void addAiActionChips(String draft,
                                   NxygramThemeManager.NxygramTheme t) {
        chipsRow.addView(makeActionChip("Fix Grammar", t, () -> {
            if (draft.isEmpty()) return;
            showSpinner(true);
            PrexzyApi.fixGrammar(context, draft,
                r -> { showSpinner(false); setInput(r); },
                e -> { showSpinner(false); toast("AI error"); }
            );
        }));
        chipsRow.addView(makeActionChip("Formal",   t, () -> rewrite(draft, "formal and polite")));
        chipsRow.addView(makeActionChip("Casual",   t, () -> rewrite(draft, "casual and relaxed")));
        chipsRow.addView(makeActionChip("Shorter",  t, () -> rewrite(draft, "shorter and more concise")));
        chipsRow.addView(makeActionChip("Friendlier", t, () -> rewrite(draft, "warm and friendly")));
        chipsRow.addView(makeActionChip("🔍 Search", t, () -> {
            if (draft.isEmpty()) return;
            showSpinner(true);
            PrexzyApi.webSearch(context, draft,
                r -> { showSpinner(false); showSearchResult(r); },
                e -> { showSpinner(false); toast("Search error"); }
            );
        }));
    }

    private void rewrite(String text, String toneDesc) {
        if (text.isEmpty()) return;
        showSpinner(true);
        PrexzyApi.changeTone(context, text, toneDesc,
            r -> { showSpinner(false); setInput(r); },
            e -> { showSpinner(false); toast("AI error"); }
        );
    }

    private void showSearchResult(String result) {
        // Show result in a small floating dialog above the bar
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        android.widget.ScrollView sv = new android.widget.ScrollView(context);
        TextView tv = new TextView(context);
        tv.setText(result);
        tv.setTextColor(t.colorText);
        tv.setTextSize(13);
        tv.setPadding(dp(16), dp(12), dp(16), dp(12));
        sv.addView(tv);

        new androidx.appcompat.app.AlertDialog.Builder(context)
            .setTitle("🔍 Search Result")
            .setView(sv)
            .setPositiveButton("Use as reply", (d, w) -> {
                setInput(result.length() > 500 ? result.substring(0, 500) + "…" : result);
            })
            .setNegativeButton("Close", null)
            .show();
    }

    // ─── Chip factories ───────────────────────────────────────────────────────

    private TextView makeReplyChip(String text, NxygramThemeManager.NxygramTheme t) {
        TextView chip = new TextView(context);
        chip.setText(text);
        chip.setTextSize(13);
        chip.setTextColor(t.colorText);
        chip.setBackgroundColor(t.colorInputBg);
        chip.setPadding(dp(12), dp(6), dp(12), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> { setInput(text); hide(); });
        return chip;
    }

    private TextView makeActionChip(String label,
                                     NxygramThemeManager.NxygramTheme t,
                                     Runnable action) {
        TextView chip = new TextView(context);
        chip.setText(label);
        chip.setTextSize(12);
        chip.setTextColor(t.colorPrimary);
        chip.setBackgroundColor(0x22000000);
        chip.setPadding(dp(10), dp(6), dp(10), dp(6));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.setMargins(0, 0, dp(6), 0);
        chip.setLayoutParams(lp);
        chip.setOnClickListener(v -> action.run());
        return chip;
    }

    private TextView makeLabel(String text, NxygramThemeManager.NxygramTheme t) {
        TextView tv = new TextView(context);
        tv.setText(text);
        tv.setTextSize(14);
        tv.setTextColor(t.colorTextSecondary);
        tv.setPadding(dp(4), dp(6), dp(8), dp(6));
        return tv;
    }

    private View makeSeparator(NxygramThemeManager.NxygramTheme t) {
        View v = new View(context);
        v.setBackgroundColor(t.colorDivider);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(1, dp(20));
        lp.setMargins(dp(4), dp(4), dp(8), dp(4));
        v.setLayoutParams(lp);
        return v;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private void setInput(String text) {
        inputField.setText(text);
        inputField.setSelection(text.length());
    }

    public void show() { container.setVisibility(View.VISIBLE); }
    public void hide() { container.setVisibility(View.GONE); }

    private void showSpinner(boolean show) {
        spinner.setVisibility(show ? View.VISIBLE : View.GONE);
    }

    private void toast(String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }
}
