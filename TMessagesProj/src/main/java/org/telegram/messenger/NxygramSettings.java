package org.telegram.messenger;

import android.content.Context;
import android.content.SharedPreferences;
import android.text.TextUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Persistent storage for Nxygram-only preferences. Telegram's existing settings and
 * account storage remain untouched; Nxygram keys live in their own preference file.
 * Secrets and provider credentials must not be stored here.
 */
public final class NxygramSettings {
    private static final String PREFERENCES = "nxygram_settings";
    private static final int SCHEMA_VERSION = 1;
    private static volatile NxygramSettings instance;

    public enum Feature {
        THEME_STUDIO("theme_studio"),
        FEATURE_CONTROL_CENTER("feature_control_center"),
        INCOMING_TRANSLATION("incoming_translation"),
        OUTGOING_TRANSLATION("outgoing_translation"),
        REPLY_LANGUAGE_MATCHING("reply_language_matching"),
        GHOST_READER("ghost_reader"),
        MESSAGE_HISTORY("message_history"),
        STORY_MANAGER("story_manager"),
        SMART_REPLY("smart_reply"),
        MESSAGE_BOOKMARKS("message_bookmarks"),
        CHAT_LOCK("chat_lock"),
        ANTI_SPAM("anti_spam"),
        CUSTOM_NOTIFICATIONS("custom_notifications"),
        MEDIA_MANAGER("media_manager"),
        PROFILE_DESIGNER("profile_designer"),
        AI_ASSISTANT("ai_assistant");

        private final String key;

        Feature(String key) {
            this.key = key;
        }
    }

    /** Basic palette model for Nxygram preset defaults; it does not mutate Telegram themes. */
    public static final class ThemePreset {
        public final String id;
        public final String name;
        public final int primary;
        public final int secondary;
        public final int accent;
        public final int background;
        public final int chatBackground;
        public final int incomingBubble;
        public final int outgoingBubble;
        public final int text;
        public final int radiusDp;
        public final boolean dark;

        private ThemePreset(String id, String name, int primary, int secondary, int accent,
                            int background, int chatBackground, int incomingBubble,
                            int outgoingBubble, int text, int radiusDp, boolean dark) {
            this.id = id;
            this.name = name;
            this.primary = primary;
            this.secondary = secondary;
            this.accent = accent;
            this.background = background;
            this.chatBackground = chatBackground;
            this.incomingBubble = incomingBubble;
            this.outgoingBubble = outgoingBubble;
            this.text = text;
            this.radiusDp = radiusDp;
            this.dark = dark;
        }
    }

    private static final List<ThemePreset> PRESETS;
    static {
        ArrayList<ThemePreset> presets = new ArrayList<>();
        presets.add(new ThemePreset("midnight", "Midnight", 0xFF171221, 0xFF302147, 0xFF9B6DFF, 0xFF09070E, 0xFF100D17, 0xFF211D2A, 0xFF49306D, 0xFFF7F3FF, 18, true));
        presets.add(new ThemePreset("ocean", "Ocean", 0xFF09202B, 0xFF123B4A, 0xFF24C8E5, 0xFF061117, 0xFF091820, 0xFF132630, 0xFF064B5B, 0xFFEAFBFF, 18, true));
        presets.add(new ThemePreset("royal", "Royal", 0xFF271D0A, 0xFF493612, 0xFFE8B84D, 0xFF100D07, 0xFF19140A, 0xFF282113, 0xFF594315, 0xFFFFF8E8, 18, true));
        presets.add(new ThemePreset("rose", "Rose", 0xFF572D43, 0xFF86516C, 0xFFFF8DB8, 0xFF1D1118, 0xFF25151F, 0xFF36222E, 0xFF6D3652, 0xFFFFF1F7, 18, true));
        presets.add(new ThemePreset("emerald", "Emerald", 0xFF15372B, 0xFF24634B, 0xFF55D69E, 0xFF08150F, 0xFF0D1D15, 0xFF1B2D23, 0xFF205D43, 0xFFEDFFF5, 18, true));
        presets.add(new ThemePreset("pure", "Pure", 0xFFF5F6FA, 0xFFE5E8F0, 0xFF5865D9, 0xFFFFFFFF, 0xFFF6F7FB, 0xFFFFFFFF, 0xFFE6E9FF, 0xFF171923, 18, false));
        PRESETS = Collections.unmodifiableList(presets);
    }

    private final SharedPreferences preferences;

    private NxygramSettings(Context context) {
        preferences = context.getApplicationContext().getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE);
        migrateIfNeeded();
    }

    public static NxygramSettings getInstance() {
        NxygramSettings local = instance;
        if (local == null) {
            synchronized (NxygramSettings.class) {
                local = instance;
                if (local == null) {
                    Context context = ApplicationLoader.applicationContext;
                    if (context == null) {
                        throw new IllegalStateException("Application is not initialized yet");
                    }
                    local = instance = new NxygramSettings(context);
                }
            }
        }
        return local;
    }

    private void migrateIfNeeded() {
        int version = preferences.getInt("schema_version", 0);
        if (version < SCHEMA_VERSION) {
            preferences.edit().putString("selected_theme_global", "midnight")
                    .putBoolean("outgoing_translation_preview", true)
                    .putInt("schema_version", SCHEMA_VERSION).apply();
        }
    }

    public static List<ThemePreset> getThemePresets() {
        return PRESETS;
    }

    public ThemePreset getThemePreset(String id) {
        if (TextUtils.isEmpty(id)) return PRESETS.get(0);
        for (ThemePreset preset : PRESETS) {
            if (preset.id.equals(id)) return preset;
        }
        return PRESETS.get(0);
    }

    public String getSelectedTheme(int account) {
        String key = account < 0 ? "selected_theme_global" : "account_" + account + "_selected_theme";
        return preferences.getString(key, "midnight");
    }

    public void setSelectedTheme(int account, String themeId) {
        if (getThemePreset(themeId) == null) return;
        String key = account < 0 ? "selected_theme_global" : "account_" + account + "_selected_theme";
        preferences.edit().putString(key, themeId).apply();
    }

    public boolean isFeatureEnabled(Feature feature, int account) {
        String key = featureKey(feature, account);
        return preferences.getBoolean(key, false);
    }

    public void setFeatureEnabled(Feature feature, int account, boolean enabled) {
        preferences.edit().putBoolean(featureKey(feature, account), enabled).apply();
    }

    /** The mandatory outgoing-translation preview cannot be disabled by this setting layer. */
    public boolean isOutgoingTranslationPreviewRequired() {
        return true;
    }

    public void resetFeature(Feature feature, int account) {
        preferences.edit().remove(featureKey(feature, account)).apply();
    }

    public void resetAccount(int account) {
        String prefix = "account_" + account + "_";
        SharedPreferences.Editor editor = preferences.edit();
        for (String key : preferences.getAll().keySet()) {
            if (key.startsWith(prefix)) editor.remove(key);
        }
        editor.apply();
    }

    private static String featureKey(Feature feature, int account) {
        if (feature == null) throw new IllegalArgumentException("Feature is required");
        return (account < 0 ? "global_" : "account_" + account + "_") + "feature_" + feature.key;
    }
}
