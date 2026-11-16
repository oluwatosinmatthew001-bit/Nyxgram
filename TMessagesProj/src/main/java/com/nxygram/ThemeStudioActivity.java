package com.nxygram;

import android.content.Context;
import android.graphics.Color;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * ThemeStudioActivity
 * Visual theme picker with live preview cards for all 6 built-in themes.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.ThemeStudioActivity" android:exported="false"/>
 *
 * Launch from NxygramSettingsActivity or Settings screen.
 */
public class ThemeStudioActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);

        setContentView(buildUI());

        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("🎨 Theme Studio");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private View buildUI() {
        NxygramThemeManager.NxygramTheme current = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(current.colorBackground);

        // Current theme label
        TextView currentLabel = new TextView(this);
        currentLabel.setText("Active Theme: " + current.name);
        currentLabel.setTextSize(13);
        currentLabel.setTextColor(current.colorTextSecondary);
        currentLabel.setPadding(dp(16), dp(16), dp(16), dp(8));
        root.addView(currentLabel);

        // Theme description
        TextView desc = new TextView(this);
        desc.setText(current.tagline);
        desc.setTextSize(16);
        desc.setTextColor(current.colorPrimary);
        desc.setTypeface(null, android.graphics.Typeface.BOLD);
        desc.setPadding(dp(16), 0, dp(16), dp(16));
        root.addView(desc);

        // Live preview panel
        root.addView(buildPreviewPanel(current));

        // Section: Choose Theme
        TextView chooseLabel = new TextView(this);
        chooseLabel.setText("CHOOSE THEME");
        chooseLabel.setTextSize(11);
        chooseLabel.setTextColor(current.colorTextSecondary);
        chooseLabel.setAllCaps(true);
        chooseLabel.setLetterSpacing(0.1f);
        chooseLabel.setPadding(dp(16), dp(20), dp(16), dp(8));
        root.addView(chooseLabel);

        // Theme cards (horizontal scroll)
        HorizontalScrollView hsv = new HorizontalScrollView(this);
        hsv.setHorizontalScrollBarEnabled(false);
        LinearLayout cards = new LinearLayout(this);
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.setPadding(dp(12), dp(4), dp(12), dp(16));

        for (Map.Entry<String, NxygramThemeManager.NxygramTheme> entry :
                NxygramThemeManager.getAllThemes().entrySet()) {
            cards.addView(buildThemeCard(entry.getValue(), root, currentLabel, desc));
        }

        hsv.addView(cards);
        root.addView(hsv);

        // AMOLED toggle row
        root.addView(buildAmoledRow(current));

        // Corner radius row
        root.addView(buildCornerRow(current));

        return root;
    }

    // ─── Preview Panel ────────────────────────────────────────────────────────

    private View buildPreviewPanel(NxygramThemeManager.NxygramTheme t) {
        LinearLayout panel = new LinearLayout(this);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(t.colorSurface);
        panel.setPadding(dp(16), dp(12), dp(16), dp(12));

        // Fake toolbar
        LinearLayout toolbar = new LinearLayout(this);
        toolbar.setBackgroundColor(t.colorToolbar);
        toolbar.setPadding(dp(12), dp(10), dp(12), dp(10));
        toolbar.setOrientation(LinearLayout.HORIZONTAL);
        TextView toolbarTitle = new TextView(this);
        toolbarTitle.setText("John Doe");
        toolbarTitle.setTextColor(t.colorText);
        toolbarTitle.setTextSize(15);
        LinearLayout.LayoutParams tl = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        toolbarTitle.setLayoutParams(tl);
        TextView toolbarIcon = new TextView(this);
        toolbarIcon.setText("📞  ⋮");
        toolbarIcon.setTextColor(t.colorIcon);
        toolbar.addView(toolbarTitle);
        toolbar.addView(toolbarIcon);
        panel.addView(toolbar);

        // Fake messages
        panel.addView(fakeBubble(t, "Hey! What's up? 👋", false, "10:14"));
        panel.addView(fakeBubble(t, "Nothing much, just testing Nxygram 😄", true, "10:15"));
        panel.addView(fakeBubble(t, "Looks great! Love the theme 🔥", false, "10:15"));

        // Fake input bar
        LinearLayout inputBar = new LinearLayout(this);
        inputBar.setBackgroundColor(t.colorInputBg);
        inputBar.setPadding(dp(10), dp(8), dp(10), dp(8));
        inputBar.setOrientation(LinearLayout.HORIZONTAL);
        TextView inputField = new TextView(this);
        inputField.setText("Message...");
        inputField.setTextColor(t.colorTextSecondary);
        inputField.setTextSize(13);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        inputField.setLayoutParams(il);
        TextView sendBtn = new TextView(this);
        sendBtn.setText("➤");
        sendBtn.setTextColor(t.colorPrimary);
        sendBtn.setTextSize(18);
        inputBar.addView(inputField);
        inputBar.addView(sendBtn);
        panel.addView(inputBar);

        return panel;
    }

    private View fakeBubble(NxygramThemeManager.NxygramTheme t,
                             String text, boolean outgoing, String time) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(outgoing ? Gravity.END : Gravity.START);
        LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        rp.topMargin = dp(4);
        row.setLayoutParams(rp);

        LinearLayout bubble = new LinearLayout(this);
        bubble.setOrientation(LinearLayout.VERTICAL);
        int bg = outgoing ? t.bubbleOut : t.bubbleIn;
        bubble.setBackgroundColor(bg);
        bubble.setPadding(dp(10), dp(6), dp(10), dp(6));

        int radius = NxygramSettings.getCornerRadius();
        // Note: For real rounded corners use a GradientDrawable in production
        // This is a simplified preview

        TextView msgText = new TextView(this);
        msgText.setText(text);
        msgText.setTextColor(outgoing ? t.bubbleOutText : t.bubbleInText);
        msgText.setTextSize(13);

        TextView msgTime = new TextView(this);
        msgTime.setText(time);
        msgTime.setTextColor(outgoing ?
                adjustAlpha(t.bubbleOutText, 0.6f) :
                adjustAlpha(t.bubbleInText, 0.6f));
        msgTime.setTextSize(10);
        msgTime.setGravity(Gravity.END);

        bubble.addView(msgText);
        bubble.addView(msgTime);

        LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        bp.setMargins(outgoing ? dp(60) : dp(4), 0, outgoing ? dp(4) : dp(60), 0);
        bubble.setLayoutParams(bp);

        row.addView(bubble);
        return row;
    }

    // ─── Theme Card ───────────────────────────────────────────────────────────

    private View buildThemeCard(NxygramThemeManager.NxygramTheme t,
                                 LinearLayout root,
                                 TextView currentLabel,
                                 TextView desc) {
        boolean isActive = t.name.equals(NxygramSettings.getThemeName());

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(t.colorSurface);
        card.setPadding(dp(2), dp(2), dp(2), dp(2));

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(dp(120), dp(160));
        cp.setMargins(dp(4), 0, dp(4), 0);
        card.setLayoutParams(cp);

        // Mini preview
        LinearLayout mini = new LinearLayout(this);
        mini.setOrientation(LinearLayout.VERTICAL);
        mini.setBackgroundColor(t.colorBackground);
        mini.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(100)));

        LinearLayout miniToolbar = new LinearLayout(this);
        miniToolbar.setBackgroundColor(t.colorToolbar);
        miniToolbar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(20)));
        mini.addView(miniToolbar);

        // Mini bubble out
        View bubbleOut = new View(this);
        bubbleOut.setBackgroundColor(t.bubbleOut);
        LinearLayout.LayoutParams bop = new LinearLayout.LayoutParams(dp(70), dp(14));
        bop.setMargins(dp(40), dp(6), dp(4), 0);
        bubbleOut.setLayoutParams(bop);
        mini.addView(bubbleOut);

        // Mini bubble in
        View bubbleIn = new View(this);
        bubbleIn.setBackgroundColor(t.bubbleIn);
        LinearLayout.LayoutParams bip = new LinearLayout.LayoutParams(dp(55), dp(14));
        bip.setMargins(dp(4), dp(4), 0, 0);
        bubbleIn.setLayoutParams(bip);
        mini.addView(bubbleIn);

        card.addView(mini);

        // Theme name
        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextSize(12);
        name.setTextColor(t.colorText);
        name.setGravity(Gravity.CENTER);
        name.setPadding(0, dp(6), 0, 0);
        card.addView(name);

        // Active indicator
        if (isActive) {
            TextView active = new TextView(this);
            active.setText("✓ Active");
            active.setTextSize(10);
            active.setTextColor(t.colorPrimary);
            active.setGravity(Gravity.CENTER);
            card.addView(active);
        }

        // Click to apply
        card.setOnClickListener(v -> {
            NxygramThemeManager.applyThemeByName(t.name);
            currentLabel.setText("Active Theme: " + t.name);
            desc.setText(t.tagline);
            desc.setTextColor(t.colorPrimary);
            root.setBackgroundColor(t.colorBackground);
            Toast.makeText(this, t.name + " theme applied", Toast.LENGTH_SHORT).show();
        });

        return card;
    }

    // ─── AMOLED Row ───────────────────────────────────────────────────────────

    private View buildAmoledRow(NxygramThemeManager.NxygramTheme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));
        row.setBackgroundColor(t.colorSurface);

        LinearLayout text = new LinearLayout(this);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        text.setLayoutParams(tp);

        TextView title = new TextView(this);
        title.setText("AMOLED Black Mode");
        title.setTextSize(15); title.setTextColor(t.colorText);

        TextView sub = new TextView(this);
        sub.setText("True black background (saves battery on OLED screens)");
        sub.setTextSize(12); sub.setTextColor(t.colorTextSecondary);

        text.addView(title); text.addView(sub);

        android.widget.Switch sw = new android.widget.Switch(this);
        sw.setChecked(NxygramSettings.isAmoledMode());
        sw.setOnCheckedChangeListener((btn, checked) -> {
            NxygramSettings.setAmoledMode(checked);
            NxygramThemeManager.applyCurrentTheme();
            Toast.makeText(this, "AMOLED mode " + (checked ? "on" : "off"), Toast.LENGTH_SHORT).show();
        });

        row.addView(text); row.addView(sw);
        return row;
    }

    // ─── Corner Radius Row ────────────────────────────────────────────────────

    private View buildCornerRow(NxygramThemeManager.NxygramTheme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(dp(16), dp(14), dp(16), dp(14));

        TextView title = new TextView(this);
        title.setText("Message Bubble Corner Radius");
        title.setTextSize(15); title.setTextColor(t.colorText);

        TextView current = new TextView(this);
        current.setText("Current: " + NxygramSettings.getCornerRadius() + "dp");
        current.setTextSize(12); current.setTextColor(t.colorTextSecondary);

        // Preset buttons
        LinearLayout presets = new LinearLayout(this);
        presets.setOrientation(LinearLayout.HORIZONTAL);
        presets.setPadding(0, dp(8), 0, 0);

        int[] radii = {0, 4, 8, 12, 18, 24};
        String[] labels = {"Sharp", "Slight", "Medium", "Default", "Round", "Pill"};
        for (int i = 0; i < radii.length; i++) {
            final int r = radii[i];
            TextView btn = new TextView(this);
            btn.setText(labels[i]);
            btn.setTextSize(11);
            btn.setTextColor(NxygramSettings.getCornerRadius() == r ?
                    t.colorPrimary : t.colorTextSecondary);
            btn.setBackgroundColor(t.colorInputBg);
            btn.setPadding(dp(10), dp(6), dp(10), dp(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.setMargins(0, 0, dp(6), 0);
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                NxygramSettings.setCornerRadius(r);
                current.setText("Current: " + r + "dp");
            });
            presets.addView(btn);
        }

        row.addView(title); row.addView(current); row.addView(presets);
        return row;
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int adjustAlpha(int color, float alpha) {
        return Color.argb((int)(Color.alpha(color) * alpha),
                Color.red(color), Color.green(color), Color.blue(color));
    }

    private int dp(int dp) { return (int)(dp * getResources().getDisplayMetrics().density); }
}
