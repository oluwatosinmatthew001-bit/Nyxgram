package com.nxygram;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

/**
 * ApiKeyDialog
 * Auto-prompts for API credentials when a feature is enabled but has no key.
 *
 * Usage — call before activating any keyed feature:
 *
 *   ApiKeyDialog.ensureReady(context, ApiKeyDialog.Feature.TRANSLATION, () -> {
 *       // key is now stored — proceed with translation
 *       TranslationManager.getInstance().translateIncoming(...);
 *   });
 *
 *   ApiKeyDialog.ensureReady(context, ApiKeyDialog.Feature.AI, () -> {
 *       // key is now stored — proceed with AI
 *   });
 *
 * Also call directly from Settings when user taps a provider row:
 *   ApiKeyDialog.showForService(context, ApiKeyManager.TRANSLATION_SERVICES[1], callback);
 */
public class ApiKeyDialog {

    public enum Feature { TRANSLATION, AI }

    public interface ReadyCallback { void onReady(); }

    // ─── Auto-check and prompt if needed ─────────────────────────────────────

    public static void ensureReady(Context context, Feature feature, ReadyCallback callback) {
        ApiKeyManager mgr = ApiKeyManager.getInstance(context);
        switch (feature) {
            case TRANSLATION:
                if (mgr.isTranslationReady()) { callback.onReady(); return; }
                // Find the right service info
                String provider = NxygramSettings.getTransProvider();
                ApiKeyManager.ServiceInfo transService = findService(
                        ApiKeyManager.TRANSLATION_SERVICES, provider);
                if (transService == null || transService.keyLabel == null) {
                    // No key needed (mymemory / libre without key)
                    callback.onReady(); return;
                }
                showForService(context, transService, callback);
                break;

            case AI:
                if (mgr.isAiReady()) { callback.onReady(); return; }
                String aiProvider = NxygramSettings.getAiProvider();
                ApiKeyManager.ServiceInfo aiService = findService(
                        ApiKeyManager.AI_SERVICES, aiProvider);
                if (aiService == null) {
                    showProviderPicker(context, feature, callback); return;
                }
                showForService(context, aiService, callback);
                break;
        }
    }

    // ─── Show dialog for a specific service ──────────────────────────────────

    public static void showForService(Context context,
                                       ApiKeyManager.ServiceInfo service,
                                       ReadyCallback callback) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        ApiKeyManager mgr = ApiKeyManager.getInstance(context);

        ScrollView scroll = new ScrollView(context);
        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorSurface);
        root.setPadding(dp(context,20), dp(context,20), dp(context,20), dp(context,8));
        scroll.addView(root);

        // Header
        TextView title = new TextView(context);
        title.setText(service.displayName + " Setup");
        title.setTextSize(18);
        title.setTextColor(t.colorText);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(title);

        // Description
        TextView desc = new TextView(context);
        desc.setText(service.description);
        desc.setTextSize(13);
        desc.setTextColor(t.colorTextSecondary);
        desc.setPadding(0, dp(context,6), 0, dp(context,16));
        root.addView(desc);

        // Docs link
        if (service.docsUrl != null) {
            TextView docsLink = new TextView(context);
            docsLink.setText("📖 Get API key: " + service.docsUrl);
            docsLink.setTextSize(12);
            docsLink.setTextColor(t.colorPrimary);
            docsLink.setPadding(0, 0, 0, dp(context,16));
            docsLink.setOnClickListener(v -> {
                try {
                    context.startActivity(new Intent(Intent.ACTION_VIEW,
                            Uri.parse(service.docsUrl)));
                } catch (Exception ignored) {}
            });
            root.addView(docsLink);
        }

        // Extra fields first (e.g. endpoint URL)
        EditText[] extraEdits = null;
        if (service.extraFieldIds != null && service.extraFieldIds.length > 0) {
            extraEdits = new EditText[service.extraFieldIds.length];
            for (int i = 0; i < service.extraFieldIds.length; i++) {
                TextView label = new TextView(context);
                label.setText(service.extraFieldLabels[i]);
                label.setTextSize(12);
                label.setTextColor(t.colorTextSecondary);
                label.setAllCaps(true);
                label.setLetterSpacing(0.08f);
                root.addView(label);

                EditText edit = new EditText(context);
                String existing = mgr.getKey(service.extraFieldIds[i]);
                edit.setText(existing != null ? existing : "");
                edit.setHint(service.extraFieldPlaceholders[i]);
                edit.setTextSize(14);
                edit.setTextColor(t.colorText);
                edit.setHintTextColor(t.colorTextSecondary);
                edit.setBackgroundColor(t.colorInputBg);
                edit.setPadding(dp(context,12), dp(context,10),
                                dp(context,12), dp(context,10));
                edit.setInputType(InputType.TYPE_CLASS_TEXT |
                                  InputType.TYPE_TEXT_VARIATION_URI);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT,
                        LinearLayout.LayoutParams.WRAP_CONTENT);
                lp.topMargin    = dp(context,4);
                lp.bottomMargin = dp(context,14);
                edit.setLayoutParams(lp);
                root.addView(edit);
                extraEdits[i] = edit;
            }
        }

        // Main API key field
        EditText keyEdit = null;
        if (service.keyLabel != null) {
            TextView keyLabel = new TextView(context);
            keyLabel.setText(service.keyLabel);
            keyLabel.setTextSize(12);
            keyLabel.setTextColor(t.colorTextSecondary);
            keyLabel.setAllCaps(true);
            keyLabel.setLetterSpacing(0.08f);
            root.addView(keyLabel);

            keyEdit = new EditText(context);
            String existingKey = mgr.getKey(getKeyIdForService(service));
            keyEdit.setText(existingKey != null ? existingKey : "");
            keyEdit.setHint(service.keyPlaceholder);
            keyEdit.setTextSize(14);
            keyEdit.setTextColor(t.colorText);
            keyEdit.setHintTextColor(t.colorTextSecondary);
            keyEdit.setBackgroundColor(t.colorInputBg);
            keyEdit.setPadding(dp(context,12), dp(context,10),
                               dp(context,12), dp(context,10));
            // Password input style — dots, but can be toggled
            keyEdit.setInputType(InputType.TYPE_CLASS_TEXT |
                                 InputType.TYPE_TEXT_VARIATION_PASSWORD);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT);
            lp.topMargin    = dp(context,4);
            lp.bottomMargin = dp(context,6);
            keyEdit.setLayoutParams(lp);
            root.addView(keyEdit);

            // Show/hide toggle
            TextView toggle = new TextView(context);
            toggle.setText("Show key");
            toggle.setTextSize(12);
            toggle.setTextColor(t.colorPrimary);
            toggle.setPadding(0, 0, 0, dp(context,16));
            final EditText finalKeyEdit = keyEdit;
            toggle.setOnClickListener(v -> {
                if (finalKeyEdit.getInputType() ==
                        (InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_PASSWORD)) {
                    finalKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT |
                                              InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
                    toggle.setText("Hide key");
                } else {
                    finalKeyEdit.setInputType(InputType.TYPE_CLASS_TEXT |
                                              InputType.TYPE_TEXT_VARIATION_PASSWORD);
                    toggle.setText("Show key");
                }
                finalKeyEdit.setSelection(finalKeyEdit.getText().length());
            });
            root.addView(toggle);
        }

        // Privacy note
        TextView privacy = new TextView(context);
        privacy.setText("🔒 Your key is encrypted on-device using Android Keystore " +
                        "and never sent anywhere except the API endpoint you configure.");
        privacy.setTextSize(11);
        privacy.setTextColor(t.colorTextSecondary);
        privacy.setBackgroundColor(t.colorBackground);
        privacy.setPadding(dp(context,10), dp(context,8), dp(context,10), dp(context,8));
        root.addView(privacy);

        // Buttons
        LinearLayout btns = new LinearLayout(context);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btns.setGravity(Gravity.END);
        btns.setPadding(0, dp(context,16), 0, 0);

        Button skip = new Button(context);
        skip.setText("Skip");
        skip.setTextColor(t.colorTextSecondary);
        skip.setBackgroundColor(android.graphics.Color.TRANSPARENT);

        Button save = new Button(context);
        save.setText("Save & Continue");
        save.setTextColor(android.graphics.Color.WHITE);
        save.setBackgroundColor(t.colorPrimary);

        btns.addView(skip);
        btns.addView(save);
        root.addView(btns);

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setView(scroll)
                .setCancelable(false)
                .create();

        if (dialog.getWindow() != null)
            dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);

        final EditText[] finalExtraEdits = extraEdits;
        final EditText   finalKeyEdit2   = keyEdit;

        skip.setOnClickListener(v -> {
            dialog.dismiss();
            // Skip: use free fallback if possible, otherwise abort
            if ("deepl".equals(service.id) || "google".equals(service.id) ||
                "openai".equals(service.id)) {
                Toast.makeText(context,
                        "Skipped — switching to free provider (MyMemory)",
                        Toast.LENGTH_SHORT).show();
                NxygramSettings.setTransProvider("mymemory");
            }
        });

        save.setOnClickListener(v -> {
            // Save extra fields
            if (finalExtraEdits != null && service.extraFieldIds != null) {
                for (int i = 0; i < service.extraFieldIds.length; i++) {
                    String val = finalExtraEdits[i].getText().toString().trim();
                    if (!val.isEmpty()) mgr.setKey(service.extraFieldIds[i], val);
                }
            }
            // Save main key
            if (finalKeyEdit2 != null) {
                String key = finalKeyEdit2.getText().toString().trim();
                if (key.isEmpty() && service.keyLabel != null) {
                    Toast.makeText(context, "Please enter your " + service.keyLabel,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                if (!key.isEmpty()) mgr.setKey(getKeyIdForService(service), key);
            }
            dialog.dismiss();
            Toast.makeText(context, service.displayName + " configured ✓",
                    Toast.LENGTH_SHORT).show();
            if (callback != null) callback.onReady();
        });

        dialog.show();
    }

    // ─── Provider Picker ──────────────────────────────────────────────────────

    public static void showProviderPicker(Context context, Feature feature,
                                           ReadyCallback callback) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();

        ApiKeyManager.ServiceInfo[] services = feature == Feature.TRANSLATION
                ? ApiKeyManager.TRANSLATION_SERVICES
                : ApiKeyManager.AI_SERVICES;

        String title = feature == Feature.TRANSLATION
                ? "Choose Translation Provider"
                : "Choose AI Provider";

        LinearLayout root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(t.colorSurface);
        root.setPadding(dp(context,8), dp(context,8), dp(context,8), dp(context,8));

        for (ApiKeyManager.ServiceInfo svc : services) {
            LinearLayout row = new LinearLayout(context);
            row.setOrientation(LinearLayout.VERTICAL);
            row.setPadding(dp(context,16), dp(context,14),
                           dp(context,16), dp(context,14));
            row.setBackgroundColor(t.colorSurface);

            TextView name = new TextView(context);
            name.setText(svc.displayName);
            name.setTextSize(15);
            name.setTextColor(t.colorText);

            TextView desc = new TextView(context);
            desc.setText(svc.description);
            desc.setTextSize(12);
            desc.setTextColor(t.colorTextSecondary);

            row.addView(name);
            row.addView(desc);

            // Divider
            View div = new View(context);
            div.setBackgroundColor(t.colorDivider);
            div.setLayoutParams(new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 1));

            root.addView(row);
            root.addView(div);

            AlertDialog[] holder = new AlertDialog[1];
            row.setOnClickListener(v -> {
                // Save selection
                if (feature == Feature.TRANSLATION) {
                    NxygramSettings.setTransProvider(svc.id);
                } else {
                    NxygramSettings.setAiProvider(svc.id);
                }
                if (holder[0] != null) holder[0].dismiss();
                // If service needs a key, show key dialog
                if (svc.keyLabel != null) {
                    showForService(context, svc, callback);
                } else {
                    if (callback != null) callback.onReady();
                }
            });

            // Store dialog ref for dismissal
        }

        AlertDialog dialog = new AlertDialog.Builder(context)
                .setTitle(title)
                .setView(root)
                .setNegativeButton("Cancel", null)
                .create();
        dialog.show();
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static ApiKeyManager.ServiceInfo findService(
            ApiKeyManager.ServiceInfo[] services, String id) {
        for (ApiKeyManager.ServiceInfo s : services) {
            if (s.id.equals(id)) return s;
        }
        return null;
    }

    private static String getKeyIdForService(ApiKeyManager.ServiceInfo service) {
        switch (service.id) {
            case "libretranslate": return ApiKeyManager.KEY_LIBRETRANSLATE_KEY;
            case "deepl":         return ApiKeyManager.KEY_DEEPL_KEY;
            case "google":        return ApiKeyManager.KEY_GOOGLE_TRANSLATE_KEY;
            case "openai":        return ApiKeyManager.KEY_OPENAI_KEY;
            case "custom_ai":     return ApiKeyManager.KEY_CUSTOM_AI_KEY;
            default:              return service.id + "_key";
        }
    }

    private static int dp(Context ctx, int dp) {
        return (int)(dp * ctx.getResources().getDisplayMetrics().density);
    }
}
