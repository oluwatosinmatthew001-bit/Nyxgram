package com.nxygram;

import android.content.Context;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.Map;

/**
 * StorageAnalyzerActivity
 * Visual storage breakdown: total, by type, by chat, with clear buttons.
 *
 * Register in AndroidManifest:
 *   <activity android:name="com.nxygram.StorageAnalyzerActivity" android:exported="false"/>
 */
public class StorageAnalyzerActivity extends AppCompatActivity {

    private LinearLayout contentLayout;
    private ProgressBar  scanSpinner;
    private TextView     statusText;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        NxygramSettings.init(this);
        setContentView(buildUI());
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle("📦 Storage Analyzer");
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        startScan();
    }

    @Override public boolean onSupportNavigateUp() { finish(); return true; }

    private View buildUI() {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorBackground);

        // Status bar
        LinearLayout statusBar = new LinearLayout(this);
        statusBar.setOrientation(LinearLayout.HORIZONTAL);
        statusBar.setBackgroundColor(t.colorSurface);
        statusBar.setPadding(dp(16), dp(10), dp(16), dp(10));
        statusBar.setGravity(Gravity.CENTER_VERTICAL);

        scanSpinner = new ProgressBar(this, null, android.R.attr.progressBarStyleSmall);
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(dp(20), dp(20));
        sp.setMargins(0, 0, dp(10), 0);
        scanSpinner.setLayoutParams(sp);
        scanSpinner.setVisibility(View.VISIBLE);

        statusText = new TextView(this);
        statusText.setText("Scanning storage…");
        statusText.setTextSize(13);
        statusText.setTextColor(t.colorTextSecondary);
        LinearLayout.LayoutParams stp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        statusText.setLayoutParams(stp);

        statusBar.addView(scanSpinner);
        statusBar.addView(statusText);
        root.addView(statusBar);

        // Scrollable content
        ScrollView sv = new ScrollView(this);
        contentLayout = new LinearLayout(this);
        contentLayout.setOrientation(LinearLayout.VERTICAL);
        sv.addView(contentLayout);
        LinearLayout.LayoutParams svLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f);
        sv.setLayoutParams(svLp);
        root.addView(sv);

        return root;
    }

    private void startScan() {
        StorageAnalyzer.getInstance().analyze(this, new StorageAnalyzer.AnalysisCallback() {
            @Override public void onProgress(int pct, String dir) {
                runOnUiThread(() -> statusText.setText("Scanning… " + dir));
            }

            @Override public void onComplete(StorageAnalyzer.AnalysisResult result) {
                runOnUiThread(() -> {
                    scanSpinner.setVisibility(View.GONE);
                    statusText.setText("Scan complete — " + result.formattedTotal() + " total");
                    renderResult(result);
                });
            }

            @Override public void onError(String error) {
                runOnUiThread(() -> {
                    scanSpinner.setVisibility(View.GONE);
                    statusText.setText("Scan error: " + error);
                });
            }
        });
    }

    private void renderResult(StorageAnalyzer.AnalysisResult result) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        contentLayout.removeAllViews();

        // ── Summary cards ──
        addSection("📊 Summary");
        addStatRow("Total storage used", result.formattedTotal(), t);
        addStatRow("Media files",         StorageAnalyzer.formatBytes(result.filesBytes), t);
        addStatRow("Cache files",         StorageAnalyzer.formatBytes(result.cacheBytes), t);

        // Clear cache button
        addClearButton("🗑️ Clear Cache (" +
                StorageAnalyzer.formatBytes(result.cacheBytes) + ")", () -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear Cache")
                    .setMessage("Delete all cached files? Media will re-download as needed.")
                    .setPositiveButton("Clear", (d, w) -> {
                        long freed = StorageAnalyzer.getInstance().clearCache(this);
                        Toast.makeText(this,
                                "Freed " + StorageAnalyzer.formatBytes(freed),
                                Toast.LENGTH_SHORT).show();
                        startScan();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        }, t);

        // ── By media type ──
        addSection("🗂️ By Type");
        String[] typeLabels = {"🖼️ Images","🎬 Videos","🎵 Audio",
                               "🎤 Voice","📄 Documents","✨ GIFs",
                               "😀 Stickers","⚙️ Cache","❓ Other"};
        StorageAnalyzer.MediaType[] types = StorageAnalyzer.MediaType.values();
        for (int i = 0; i < types.length; i++) {
            Long size = result.byType.get(types[i]);
            if (size != null && size > 0) {
                addBarRow(typeLabels[i], size, result.totalBytes, t);
            }
        }

        // ── By chat (top 10) ──
        if (!result.byChat.isEmpty()) {
            addSection("💬 By Chat (top 10)");
            int shown = 0;
            for (StorageAnalyzer.ChatStorageSummary chat : result.byChat) {
                if (shown++ >= 10) break;
                addChatRow(chat, result.totalBytes, t);
            }
        }
    }

    // ─── View helpers ─────────────────────────────────────────────────────────

    private void addSection(String title) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        TextView tv = new TextView(this);
        tv.setText(title);
        tv.setTextSize(12);
        tv.setTextColor(t.colorTextSecondary);
        tv.setAllCaps(true);
        tv.setLetterSpacing(0.1f);
        tv.setPadding(dp(16), dp(20), dp(16), dp(6));
        contentLayout.addView(tv);
    }

    private void addStatRow(String label, String value,
                             NxygramThemeManager.NxygramTheme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));

        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextSize(14);
        lv.setTextColor(t.colorText);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lv.setLayoutParams(lp);

        TextView rv = new TextView(this);
        rv.setText(value);
        rv.setTextSize(14);
        rv.setTextColor(t.colorPrimary);
        rv.setTypeface(null, android.graphics.Typeface.BOLD);

        row.addView(lv); row.addView(rv);
        addDivider(row, t);
        contentLayout.addView(row);
    }

    private void addBarRow(String label, long bytes, long total,
                            NxygramThemeManager.NxygramTheme t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundColor(t.colorSurface);
        card.setPadding(dp(16), dp(10), dp(16), dp(10));

        LinearLayout header = new LinearLayout(this);
        header.setOrientation(LinearLayout.HORIZONTAL);

        TextView lv = new TextView(this);
        lv.setText(label);
        lv.setTextSize(13);
        lv.setTextColor(t.colorText);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lv.setLayoutParams(lp);

        TextView rv = new TextView(this);
        rv.setText(StorageAnalyzer.formatBytes(bytes));
        rv.setTextSize(12);
        rv.setTextColor(t.colorTextSecondary);

        header.addView(lv); header.addView(rv);
        card.addView(header);

        // Progress bar
        float fraction = total > 0 ? (float) bytes / total : 0;
        LinearLayout bar = new LinearLayout(this);
        bar.setBackgroundColor(t.colorDivider);
        LinearLayout.LayoutParams barLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(4));
        barLp.topMargin = dp(6);
        bar.setLayoutParams(barLp);

        View fill = new View(this);
        fill.setBackgroundColor(t.colorPrimary);
        fill.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), fraction));
        bar.addView(fill);

        View rest = new View(this);
        rest.setBackgroundColor(t.colorDivider);
        rest.setLayoutParams(new LinearLayout.LayoutParams(0, dp(4), 1f - fraction));
        bar.addView(rest);

        card.addView(bar);
        addDivider(card, t);
        contentLayout.addView(card);
    }

    private void addChatRow(StorageAnalyzer.ChatStorageSummary chat,
                             long total, NxygramThemeManager.NxygramTheme t) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBackgroundColor(t.colorSurface);
        row.setPadding(dp(16), dp(12), dp(16), dp(12));
        row.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams ip = new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        info.setLayoutParams(ip);

        TextView name = new TextView(this);
        name.setText(chat.chatName);
        name.setTextSize(14);
        name.setTextColor(t.colorText);

        TextView size = new TextView(this);
        size.setText(chat.formattedSize());
        size.setTextSize(12);
        size.setTextColor(t.colorTextSecondary);

        info.addView(name); info.addView(size);

        TextView clearBtn = new TextView(this);
        clearBtn.setText("Clear");
        clearBtn.setTextSize(12);
        clearBtn.setTextColor(0xFFFF5252);
        clearBtn.setPadding(dp(8), dp(4), dp(4), dp(4));
        clearBtn.setOnClickListener(v -> {
            new AlertDialog.Builder(this)
                    .setTitle("Clear " + chat.chatName)
                    .setMessage("Delete all media for this chat? (" + chat.formattedSize() + ")")
                    .setPositiveButton("Clear", (d, w) -> {
                        long freed = StorageAnalyzer.getInstance()
                                .clearChatMedia(this, chat.chatId);
                        Toast.makeText(this,
                                "Freed " + StorageAnalyzer.formatBytes(freed),
                                Toast.LENGTH_SHORT).show();
                        startScan();
                    })
                    .setNegativeButton("Cancel", null)
                    .show();
        });

        row.addView(info); row.addView(clearBtn);
        addDivider(row, t);
        contentLayout.addView(row);
    }

    private void addClearButton(String label, Runnable action,
                                 NxygramThemeManager.NxygramTheme t) {
        TextView btn = new TextView(this);
        btn.setText(label);
        btn.setTextSize(13);
        btn.setTextColor(0xFFFF5252);
        btn.setBackgroundColor(t.colorSurface);
        btn.setGravity(Gravity.CENTER);
        btn.setPadding(dp(16), dp(14), dp(16), dp(14));
        btn.setOnClickListener(v -> action.run());
        contentLayout.addView(btn);
    }

    private void addDivider(ViewGroup parent, NxygramThemeManager.NxygramTheme t) {
        View div = new View(this);
        div.setBackgroundColor(t.colorDivider);
        div.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1));
        parent.addView(div);
    }

    private int dp(int dp) {
        return (int)(dp * getResources().getDisplayMetrics().density);
    }
}
