package com.nxygram;

import android.content.Context;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

/**
 * TranslationPreviewDialog
 * Shows the translated text before sending, lets user edit or revert.
 * Per spec: NEVER send without showing this first.
 *
 * Usage in ChatActivity, before sendMessage():
 *
 *   TranslationPreviewDialog.show(context,
 *       originalText,
 *       translatedText,
 *       detectedSourceLang,
 *       targetLang,
 *       new TranslationPreviewDialog.Callback() {
 *           public void onConfirm(String textToSend) { sendMessage(textToSend); }
 *           public void onCancel() { }
 *       });
 */
public class TranslationPreviewDialog {

    public interface Callback {
        void onConfirm(String textToSend);
        void onCancel();
    }

    public static void show(Context context,
                             String originalText,
                             String translatedText,
                             String fromLang,
                             String toLang,
                             Callback callback) {

        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorSurface);
        root.setPadding(dp(context, 20), dp(context, 20), dp(context, 20), dp(context, 20));

        // Header
        TextView header = new TextView(context);
        header.setText("🌐 Translation Preview");
        header.setTextSize(16);
        header.setTextColor(t.colorText);
        header.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(header);

        // Lang pair
        TextView langRow = new TextView(context);
        langRow.setText(fromLang.toUpperCase() + "  →  " + toLang.toUpperCase());
        langRow.setTextSize(12);
        langRow.setTextColor(t.colorPrimary);
        langRow.setPadding(0, dp(context, 4), 0, dp(context, 16));
        root.addView(langRow);

        // Original
        TextView origLabel = new TextView(context);
        origLabel.setText("Original (" + fromLang.toUpperCase() + ")");
        origLabel.setTextSize(11);
        origLabel.setTextColor(t.colorTextSecondary);
        origLabel.setAllCaps(true);
        origLabel.setLetterSpacing(0.08f);
        root.addView(origLabel);

        TextView origText = new TextView(context);
        origText.setText(originalText);
        origText.setTextSize(14);
        origText.setTextColor(t.colorTextSecondary);
        origText.setPadding(dp(context,12), dp(context,10), dp(context,12), dp(context,10));
        origText.setBackgroundColor(t.colorBackground);
        origText.setPadding(dp(context,12), dp(context,8), dp(context,12), dp(context,8));
        LinearLayout.LayoutParams origLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        origLp.topMargin = dp(context, 4);
        origLp.bottomMargin = dp(context, 16);
        origText.setLayoutParams(origLp);
        root.addView(origText);

        // Translated (editable)
        TextView transLabel = new TextView(context);
        transLabel.setText("Translation (" + toLang.toUpperCase() + ") — tap to edit");
        transLabel.setTextSize(11);
        transLabel.setTextColor(t.colorTextSecondary);
        transLabel.setAllCaps(true);
        transLabel.setLetterSpacing(0.08f);
        root.addView(transLabel);

        EditText transEdit = new EditText(context);
        transEdit.setText(translatedText);
        transEdit.setTextSize(15);
        transEdit.setTextColor(t.colorText);
        transEdit.setBackgroundColor(t.colorInputBg);
        transEdit.setPadding(dp(context,12), dp(context,10), dp(context,12), dp(context,10));
        transEdit.setMinLines(2);
        LinearLayout.LayoutParams editLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        editLp.topMargin = dp(context, 4);
        editLp.bottomMargin = dp(context, 8);
        transEdit.setLayoutParams(editLp);
        root.addView(transEdit);

        // Revert link
        TextView revert = new TextView(context);
        revert.setText("↩ Send original instead");
        revert.setTextSize(12);
        revert.setTextColor(t.colorAccent);
        revert.setPadding(0, 0, 0, dp(context, 12));
        revert.setOnClickListener(v -> {
            transEdit.setText(originalText);
        });
        root.addView(revert);

        // Buttons
        LinearLayout buttons = new LinearLayout(context);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setGravity(Gravity.END);

        Button cancel = new Button(context);
        cancel.setText("Cancel");
        cancel.setTextColor(t.colorTextSecondary);
        cancel.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        Button send = new Button(context);
        send.setText("Send Translation →");
        send.setTextColor(android.graphics.Color.WHITE);
        send.setBackgroundColor(t.colorPrimary);

        buttons.addView(cancel);
        buttons.addView(send);
        root.addView(buttons);

        // Build dialog
        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(root)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null) {
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        }

        cancel.setOnClickListener(v -> {
            dialog.dismiss();
            callback.onCancel();
        });

        send.setOnClickListener(v -> {
            String textToSend = transEdit.getText().toString().trim();
            if (textToSend.isEmpty()) textToSend = translatedText;
            dialog.dismiss();
            callback.onConfirm(textToSend);
        });

        dialog.show();
    }

    private static int dp(Context ctx, int dp) {
        return (int)(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
