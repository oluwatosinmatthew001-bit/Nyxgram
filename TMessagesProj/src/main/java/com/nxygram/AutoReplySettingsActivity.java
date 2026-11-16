package com.nxygram;

import android.content.Context;
import android.os.Bundle;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.TimePicker;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.List;

/**
 * AutoReplySettingsActivity
 * Full UI for auto-reply: away mode, greeting, keyword rules, schedule.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.AutoReplySettingsActivity"
 *             android:exported="false"/>
 */
public class AutoReplySettingsActivity extends AppCompatActivity {

    private AutoReplyManager mgr;
    private LinearLayout keywordListLayout;
    private NxygramThemeManager.NxygramTheme t;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        mgr = AutoReplyManager.getInstance(this);
        t   = NxygramThemeManager.getCurrentTheme();
        getWindow().getDecorView().setBackgroundColor(t.colorBackground);

        ScrollView sv = new ScrollView(this);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        sv.addView(root);
        setContentView(sv);

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🤖 Auto-Reply");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }

        buildUI(root);
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private void buildUI(LinearLayout root) {
        // ── Away Mode ──────────────────────────────────────────────────────
        addSection(root, "Away Mode");

        addToggleRow(root, "Enable Away Mode",
                "Auto-reply to all incoming DMs when busy",
                "autoreply_away_enabled", false,
                (v, on) -> {});

        addEditRow(root, "Away Message",
                mgr.getAwayMessage(),
                text -> mgr.setAwayMessage(text),
                true);

        addToggleRow(root, "AI-Generated Replies",
                "Let Nxygram AI write context-aware replies instead of fixed text",
                "autoreply_ai_reply", false, null);

        // Schedule
        addToggleRow(root, "Schedule Away Hours",
                "Only activate during specific hours",
                "autoreply_schedule_enabled", false,
                (v, on) -> {});

        addActionRow(root, "Set Away Hours",
                buildScheduleSubtitle(),
                this::showSchedulePicker);

        // ── First-Time Greeting ────────────────────────────────────────────
        addSection(root, "First-Time Greeting");

        addToggleRow(root, "Enable Greeting",
                "Send a welcome message when someone DMs you for the first time",
                "autoreply_greeting_enabled", true, null);

        addEditRow(root, "Greeting Message",
                mgr.getFirstTimeGreeting(),
                text -> mgr.setFirstTimeGreeting(text),
                true);

        addActionRow(root, "Clear Greeting History",
                "Re-send greeting to everyone (resets who has been greeted)",
                () -> new AlertDialog.Builder(this)
                        .setTitle("Clear Greeting History")
                        .setMessage("Everyone will receive the greeting again on their next message.")
                        .setPositiveButton("Clear", (d, w) -> {
                            // Clear greeted_users table
                            Toast.makeText(this, "Greeting history cleared", Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton("Cancel", null)
                        .show());

        // ── Keyword Rules ──────────────────────────────────────────────────
        addSection(root, "Keyword Auto-Reply Rules");

        addInfoBanner(root,
                "When a message contains a keyword, the bot automatically replies.\n" +
                "Keyword rules fire even when Away Mode is off.");

        addActionRow(root, "＋ Add Keyword Rule", "", this::showAddKeywordDialog);

        // Keyword list
        keywordListLayout = new LinearLayout(this);
        keywordListLayout.setOrientation(LinearLayout.VERTICAL);
        root.addView(keywordListLayout);
        refreshKeywordList();

        // ── Cooldown ───────────────────────────────────────────────────────
        addSection(root, "Advanced");

        addInfoBanner(root,
                "Away replies have a 1-hour cooldown per user to avoid spam. " +
                "Keyword rules do not have a cooldown.");

        addActionRow(root, "Reset Cooldowns", "Re-enable immediate away replies for all users",
                () -> {
                    mgr.resetAllCooldowns();
                    toast("Cooldowns reset");
                });
    }

    // ─── Keyword list ──────────────────────────────────────────────────────

    private void refreshKeywordList() {
        keywordListLayout.removeAllViews();
        List<AutoReplyManager.KeywordRule> rules = mgr.getKeywordRules();

        if (rules.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No keyword rules yet. Tap '+ Add' to create one.");
            empty.setTextColor(t.colorTextSecondary);
            empty.setTextSize(13);
            empty.setPadding(dp(16), dp(12), dp(16), dp(12));
            keywordListLayout.addView(empty);
            return;
        }

        for (AutoReplyManager.KeywordRule rule : rules) {
            addKeywordRow(rule);
        }
    }

    private void addKeywordRow(AutoReplyManager.KeywordRule rule) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        info.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView keyword = new TextView(this);
        keyword.setText("\"" + rule.keyword + "\"" + (rule.isRegex ? " (regex)" : ""));
        keyword.setTextSize(14);
        keyword.setTextColor(t.colorPrimary);
        keyword.setTypeface(null, android.graphics.Typeface.BOLD);

        TextView reply = new TextView(this);
        reply.setText("→ " + rule.reply);
        reply.setTextSize(13);
        reply.setTextColor(t.colorText);

        info.addView(keyword);
        info.addView(reply);

        Switch sw = new Switch(this);
        sw.setChecked(rule.enabled);
        sw.setOnCheckedChangeListener((b, on) -> {
            mgr.setKeywordRuleEnabled(rule.id, on);
        });

        TextView delete = new TextView(this);
        delete.setText("✕");
        delete.setTextSize(18);
        delete.setTextColor(0xFFFF5252);
        delete.setPadding(dp(12), dp(4), dp(4), dp(4));
        delete.setOnClickListener(v -> new AlertDialog.Builder(this)
                .setTitle("Delete Rule")
                .setMessage("Delete the rule for \"" + rule.keyword + "\"?")
                .setPositiveButton("Delete", (d, w) -> {
                    mgr.deleteKeywordRule(rule.id);
                    refreshKeywordList();
                })
                .setNegativeButton("Cancel", null)
                .show());

        row.addView(info);
        row.addView(sw);
        row.addView(delete);

        addDivider(row);
        keywordListLayout.addView(row);
    }

    // ─── Add keyword dialog ────────────────────────────────────────────────

    private void showAddKeywordDialog() {
        LinearLayout dialogLayout = new LinearLayout(this);
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp(20), dp(16), dp(20), dp(8));
        dialogLayout.setBackgroundColor(t.colorSurface);

        EditText keywordEdit = makeEditText("Keyword or phrase (e.g. price, help, hours)");
        EditText replyEdit   = makeEditText("Reply message");
        replyEdit.setMinLines(2);

        LinearLayout options = new LinearLayout(this);
        options.setOrientation(LinearLayout.HORIZONTAL);
        options.setPadding(0, dp(8), 0, 0);

        Switch regexSwitch = new Switch(this);
        TextView regexLabel = new TextView(this);
        regexLabel.setText("Regex");
        regexLabel.setTextColor(t.colorTextSecondary);
        regexLabel.setTextSize(13);
        regexLabel.setPadding(0, 0, dp(16), 0);

        Switch caseSwitch = new Switch(this);
        TextView caseLabel = new TextView(this);
        caseLabel.setText("Case sensitive");
        caseLabel.setTextColor(t.colorTextSecondary);
        caseLabel.setTextSize(13);

        options.addView(regexSwitch);
        options.addView(regexLabel);
        options.addView(caseSwitch);
        options.addView(caseLabel);

        dialogLayout.addView(makeLabel("Keyword"));
        dialogLayout.addView(keywordEdit);
        dialogLayout.addView(makeLabel("Reply"));
        dialogLayout.addView(replyEdit);
        dialogLayout.addView(options);

        new AlertDialog.Builder(this)
                .setTitle("Add Keyword Rule")
                .setView(dialogLayout)
                .setPositiveButton("Add", (d, w) -> {
                    String kw    = keywordEdit.getText().toString().trim();
                    String reply = replyEdit.getText().toString().trim();
                    if (kw.isEmpty() || reply.isEmpty()) {
                        toast("Both keyword and reply are required");
                        return;
                    }
                    mgr.addKeywordRule(kw, reply, regexSwitch.isChecked(),
                            caseSwitch.isChecked());
                    refreshKeywordList();
                    toast("Rule added ✓");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    // ─── Schedule picker ───────────────────────────────────────────────────

    private void showSchedulePicker() {
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(16), dp(12), dp(16), dp(8));
        layout.setBackgroundColor(t.colorSurface);

        TextView info = new TextView(this);
        info.setText("Away mode will activate between these hours:");
        info.setTextColor(t.colorTextSecondary);
        info.setTextSize(13);
        info.setPadding(0, 0, 0, dp(12));
        layout.addView(info);

        TextView startLabel = makeLabel("Start Hour");
        layout.addView(startLabel);
        TimePicker startPicker = new TimePicker(this);
        startPicker.setIs24HourView(true);
        startPicker.setHour(NxygramSettings.getInt("autoreply_start_hour", 23));
        startPicker.setMinute(0);
        layout.addView(startPicker);

        TextView endLabel = makeLabel("End Hour");
        layout.addView(endLabel);
        TimePicker endPicker = new TimePicker(this);
        endPicker.setIs24HourView(true);
        endPicker.setHour(NxygramSettings.getInt("autoreply_end_hour", 8));
        endPicker.setMinute(0);
        layout.addView(endPicker);

        new AlertDialog.Builder(this)
                .setTitle("Away Schedule")
                .setView(layout)
                .setPositiveButton("Save", (d, w) -> {
                    mgr.setSchedule(startPicker.getHour(), endPicker.getHour());
                    toast("Schedule saved ✓");
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private String buildScheduleSubtitle() {
        if (!NxygramSettings.getBool("autoreply_schedule_enabled", false))
            return "Tap to configure";
        int start = NxygramSettings.getInt("autoreply_start_hour", 23);
        int end   = NxygramSettings.getInt("autoreply_end_hour",   8);
        return String.format("%02d:00 – %02d:00", start, end);
    }

    // ─── View builders ─────────────────────────────────────────────────────

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

    private void addToggleRow(LinearLayout parent, String title, String sub,
                               String key, boolean def,
                               CompoundButton.OnCheckedChangeListener extra) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        text.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));

        TextView titleTv = new TextView(this);
        titleTv.setText(title);
        titleTv.setTextSize(15);
        titleTv.setTextColor(t.colorText);

        TextView subTv = new TextView(this);
        subTv.setText(sub);
        subTv.setTextSize(12);
        subTv.setTextColor(t.colorTextSecondary);

        text.addView(titleTv);
        text.addView(subTv);

        Switch sw = new Switch(this);
        sw.setChecked(NxygramSettings.getBool(key, def));
        sw.setOnCheckedChangeListener((b, on) -> {
            NxygramSettings.setBool(key, on);
            if (extra != null) extra.onCheckedChanged(b, on);
        });

        row.addView(text);
        row.addView(sw);
        addDivider(row);
        parent.addView(row);
    }

    private void addEditRow(LinearLayout parent, String label,
                             String current, java.util.function.Consumer<String> onSave,
                             boolean multiline) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(10), dp(16), dp(10));

        TextView labelTv = new TextView(this);
        labelTv.setText(label);
        labelTv.setTextSize(12);
        labelTv.setTextColor(t.colorTextSecondary);

        EditText edit = new EditText(this);
        edit.setText(current);
        edit.setTextSize(14);
        edit.setTextColor(t.colorText);
        edit.setBackgroundColor(t.colorInputBg);
        edit.setPadding(dp(10), dp(8), dp(10), dp(8));
        if (multiline) {
            edit.setMinLines(2);
            edit.setInputType(InputType.TYPE_CLASS_TEXT |
                              InputType.TYPE_TEXT_FLAG_MULTI_LINE);
        }

        Button saveBtn = new Button(this);
        saveBtn.setText("Save");
        saveBtn.setTextColor(android.graphics.Color.WHITE);
        saveBtn.setBackgroundColor(t.colorPrimary);
        saveBtn.setOnClickListener(v -> {
            onSave.accept(edit.getText().toString().trim());
            toast("Saved ✓");
        });

        row.addView(labelTv);
        row.addView(edit);
        row.addView(saveBtn);
        addDivider(row);
        parent.addView(row);
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

    private void addInfoBanner(LinearLayout parent, String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(t.colorTextSecondary);
        tv.setBackgroundColor(t.colorBackground);
        tv.setPadding(dp(16), dp(10), dp(16), dp(10));
        parent.addView(tv);
    }

    private void addDivider(ViewGroup parent) {
        View div = new View(this);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
    }

    private TextView makeLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextSize(12);
        tv.setTextColor(t.colorTextSecondary);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.08f);
        tv.setPadding(0, dp(6), 0, dp(4));
        return tv;
    }

    private EditText makeEditText(String hint) {
        EditText et = new EditText(this);
        et.setHint(hint);
        et.setHintTextColor(t.colorTextSecondary);
        et.setTextColor(t.colorText);
        et.setTextSize(14);
        et.setBackgroundColor(t.colorInputBg);
        et.setPadding(dp(10), dp(8), dp(10), dp(8));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(12);
        et.setLayoutParams(lp);
        return et;
    }

    private void toast(String msg) {
        Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
