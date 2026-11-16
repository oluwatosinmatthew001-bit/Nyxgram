package com.nxygram;

import android.graphics.Color;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * NxygramThemeManager
 * Defines Nxygram's 6 built-in themes and applies them via Telegram's Theme system.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/NxygramThemeManager.java
 *
 * Hook: In LaunchActivity.onCreate() or ApplicationLoader.onCreate():
 *   NxygramThemeManager.applyCurrentTheme();
 *
 * Telegram's Theme class uses Theme.setThemeWallpaper / Theme.applyTheme.
 * We set Theme.currentColors[] values directly after Telegram loads its own theme,
 * then call Theme.refreshThemeColors() to apply.
 *
 * Key references in Telegram source:
 *   org.telegram.ui.ActionBar.Theme
 *   org.telegram.ui.ActionBar.Theme.key_* constants
 */
public class NxygramThemeManager {

    // ─── Theme Definitions ────────────────────────────────────────────────────

    public static final String THEME_MIDNIGHT = "Midnight";
    public static final String THEME_OCEAN    = "Ocean";
    public static final String THEME_ROYAL    = "Royal";
    public static final String THEME_ROSE     = "Rose";
    public static final String THEME_EMERALD  = "Emerald";
    public static final String THEME_PURE     = "Pure";

    public static class NxygramTheme {
        public final String name;
        public final String tagline;
        public final boolean isDark;

        // Core palette
        public final int colorPrimary;
        public final int colorSecondary;
        public final int colorAccent;
        public final int colorBackground;
        public final int colorSurface;
        public final int colorText;
        public final int colorTextSecondary;
        public final int colorIcon;
        public final int colorNavBar;
        public final int colorToolbar;
        public final int colorDivider;
        public final int colorInputBg;

        // Message bubbles
        public final int bubbleOut;
        public final int bubbleIn;
        public final int bubbleOutText;
        public final int bubbleInText;

        // Status bar
        public final int colorStatusBar;

        NxygramTheme(Builder b) {
            name             = b.name;
            tagline          = b.tagline;
            isDark           = b.isDark;
            colorPrimary     = b.colorPrimary;
            colorSecondary   = b.colorSecondary;
            colorAccent      = b.colorAccent;
            colorBackground  = b.colorBackground;
            colorSurface     = b.colorSurface;
            colorText        = b.colorText;
            colorTextSecondary = b.colorTextSecondary;
            colorIcon        = b.colorIcon;
            colorNavBar      = b.colorNavBar;
            colorToolbar     = b.colorToolbar;
            colorDivider     = b.colorDivider;
            colorInputBg     = b.colorInputBg;
            bubbleOut        = b.bubbleOut;
            bubbleIn         = b.bubbleIn;
            bubbleOutText    = b.bubbleOutText;
            bubbleInText     = b.bubbleInText;
            colorStatusBar   = b.colorStatusBar;
        }

        static class Builder {
            String name, tagline;
            boolean isDark;
            int colorPrimary, colorSecondary, colorAccent;
            int colorBackground, colorSurface;
            int colorText, colorTextSecondary, colorIcon;
            int colorNavBar, colorToolbar, colorDivider, colorInputBg;
            int bubbleOut, bubbleIn, bubbleOutText, bubbleInText;
            int colorStatusBar;

            Builder name(String v)              { name = v; return this; }
            Builder tagline(String v)           { tagline = v; return this; }
            Builder dark(boolean v)             { isDark = v; return this; }
            Builder primary(int v)              { colorPrimary = v; return this; }
            Builder secondary(int v)            { colorSecondary = v; return this; }
            Builder accent(int v)               { colorAccent = v; return this; }
            Builder background(int v)           { colorBackground = v; return this; }
            Builder surface(int v)              { colorSurface = v; return this; }
            Builder text(int v)                 { colorText = v; return this; }
            Builder textSecondary(int v)        { colorTextSecondary = v; return this; }
            Builder icon(int v)                 { colorIcon = v; return this; }
            Builder navBar(int v)               { colorNavBar = v; return this; }
            Builder toolbar(int v)              { colorToolbar = v; return this; }
            Builder divider(int v)              { colorDivider = v; return this; }
            Builder inputBg(int v)              { colorInputBg = v; return this; }
            Builder bubbleOut(int v)            { bubbleOut = v; return this; }
            Builder bubbleIn(int v)             { bubbleIn = v; return this; }
            Builder bubbleOutText(int v)        { bubbleOutText = v; return this; }
            Builder bubbleInText(int v)         { bubbleInText = v; return this; }
            Builder statusBar(int v)            { colorStatusBar = v; return this; }
            NxygramTheme build()                { return new NxygramTheme(this); }
        }
    }

    // ─── Built-in Themes ──────────────────────────────────────────────────────

    private static final Map<String, NxygramTheme> THEMES = new LinkedHashMap<>();

    static {
        // 1. Midnight — deep black and violet
        THEMES.put(THEME_MIDNIGHT, new NxygramTheme.Builder()
                .name(THEME_MIDNIGHT).tagline("Deep black & violet").dark(true)
                .primary(0xFF7B2FBE).secondary(0xFF5A1F8A).accent(0xFFAB5EE8)
                .background(0xFF0A0A0F).surface(0xFF131320)
                .text(0xFFEEEEEE).textSecondary(0xFF9E9EBB).icon(0xFFAB5EE8)
                .navBar(0xFF0A0A0F).toolbar(0xFF131320).divider(0xFF2A2A45)
                .inputBg(0xFF1A1A2E)
                .bubbleOut(0xFF7B2FBE).bubbleIn(0xFF1E1E32)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFFEEEEEE)
                .statusBar(0xFF0A0A0F)
                .build());

        // 2. Ocean — black and cyan
        THEMES.put(THEME_OCEAN, new NxygramTheme.Builder()
                .name(THEME_OCEAN).tagline("Black & cyan").dark(true)
                .primary(0xFF00BCD4).secondary(0xFF0097A7).accent(0xFF4DD0E1)
                .background(0xFF050D12).surface(0xFF0D1F26)
                .text(0xFFE0F7FA).textSecondary(0xFF80CBC4).icon(0xFF4DD0E1)
                .navBar(0xFF050D12).toolbar(0xFF0D1F26).divider(0xFF1A3A45)
                .inputBg(0xFF102030)
                .bubbleOut(0xFF00838F).bubbleIn(0xFF102030)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFFE0F7FA)
                .statusBar(0xFF050D12)
                .build());

        // 3. Royal — black and gold
        THEMES.put(THEME_ROYAL, new NxygramTheme.Builder()
                .name(THEME_ROYAL).tagline("Black & gold").dark(true)
                .primary(0xFFFFD700).secondary(0xFFDAA520).accent(0xFFFFEC40)
                .background(0xFF080600).surface(0xFF121008)
                .text(0xFFFFF8DC).textSecondary(0xFFCDB87A).icon(0xFFFFD700)
                .navBar(0xFF080600).toolbar(0xFF121008).divider(0xFF2A2010)
                .inputBg(0xFF1A1600)
                .bubbleOut(0xFF8B6914).bubbleIn(0xFF1A1600)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFFFFF8DC)
                .statusBar(0xFF080600)
                .build());

        // 4. Rose — elegant pink tones
        THEMES.put(THEME_ROSE, new NxygramTheme.Builder()
                .name(THEME_ROSE).tagline("Elegant pink").dark(true)
                .primary(0xFFE91E63).secondary(0xFFC2185B).accent(0xFFF48FB1)
                .background(0xFF120610).surface(0xFF1E0E1A)
                .text(0xFFFCE4EC).textSecondary(0xFFCF9BAE).icon(0xFFF48FB1)
                .navBar(0xFF120610).toolbar(0xFF1E0E1A).divider(0xFF3A1A28)
                .inputBg(0xFF2A1020)
                .bubbleOut(0xFFAD1457).bubbleIn(0xFF2A1020)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFFFCE4EC)
                .statusBar(0xFF120610)
                .build());

        // 5. Emerald — green tones
        THEMES.put(THEME_EMERALD, new NxygramTheme.Builder()
                .name(THEME_EMERALD).tagline("Rich greens").dark(true)
                .primary(0xFF4CAF50).secondary(0xFF388E3C).accent(0xFF80CBC4)
                .background(0xFF050E05).surface(0xFF0D1A0D)
                .text(0xFFE8F5E9).textSecondary(0xFF81C784).icon(0xFF80CBC4)
                .navBar(0xFF050E05).toolbar(0xFF0D1A0D).divider(0xFF1A3020)
                .inputBg(0xFF102010)
                .bubbleOut(0xFF2E7D32).bubbleIn(0xFF102010)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFFE8F5E9)
                .statusBar(0xFF050E05)
                .build());

        // 6. Pure — clean light theme
        THEMES.put(THEME_PURE, new NxygramTheme.Builder()
                .name(THEME_PURE).tagline("Clean & light").dark(false)
                .primary(0xFF5C6BC0).secondary(0xFF3949AB).accent(0xFF7E57C2)
                .background(0xFFFAFAFA).surface(0xFFFFFFFF)
                .text(0xFF212121).textSecondary(0xFF757575).icon(0xFF5C6BC0)
                .navBar(0xFFFAFAFA).toolbar(0xFF5C6BC0).divider(0xFFE0E0E0)
                .inputBg(0xFFF5F5F5)
                .bubbleOut(0xFF5C6BC0).bubbleIn(0xFFFFFFFF)
                .bubbleOutText(0xFFFFFFFF).bubbleInText(0xFF212121)
                .statusBar(0xFF3949AB)
                .build());
    }

    // ─── Apply Theme ──────────────────────────────────────────────────────────

    /**
     * Apply the currently selected Nxygram theme.
     * Call after Telegram's own theme system has initialized.
     *
     * In Telegram source, hook into:
     *   org.telegram.ui.ActionBar.Theme → applyTheme() or after setCurrentTheme()
     */
    public static void applyCurrentTheme() {
        String themeName = NxygramSettings.getThemeName();
        NxygramTheme theme = THEMES.get(themeName);
        if (theme == null) theme = THEMES.get(THEME_MIDNIGHT);
        applyTheme(theme);
    }

    public static void applyTheme(NxygramTheme theme) {
        if (theme == null) return;
        boolean amoled = NxygramSettings.isAmoledMode();
        applyToTelegramTheme(theme, amoled);
    }

    public static void applyThemeByName(String name) {
        NxygramSettings.setThemeName(name);
        applyCurrentTheme();
    }

    /**
     * Maps Nxygram theme palette onto Telegram's Theme.currentColors array.
     *
     * Telegram's Theme class has static int[] currentColors indexed by Theme.key_* constants.
     * We call Theme.setThemeWallpaper or directly set Theme.currentColors.
     *
     * NOTE: Exact key names depend on the Telegram version in your repo.
     * Common keys: key_actionBarDefault, key_windowBackgroundWhite, key_chat_outBubble, etc.
     * Verify against TMessagesProj/src/main/java/org/telegram/ui/ActionBar/Theme.java
     */
    private static void applyToTelegramTheme(NxygramTheme t, boolean amoled) {
        int bg = amoled && t.isDark ? Color.BLACK : t.colorBackground;
        int surface = amoled && t.isDark ? 0xFF0A0A0A : t.colorSurface;

        try {
            // Use reflection to set Telegram's Theme.currentColors
            // so we don't require a direct compile-time dependency on Theme.java
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");

            setThemeColor(themeClass, "key_windowBackgroundWhite",          surface);
            setThemeColor(themeClass, "key_windowBackgroundGray",           bg);
            setThemeColor(themeClass, "key_actionBarDefault",               t.colorToolbar);
            setThemeColor(themeClass, "key_actionBarDefaultIcon",           t.colorIcon);
            setThemeColor(themeClass, "key_actionBarDefaultTitle",          t.colorText);
            setThemeColor(themeClass, "key_actionBarDefaultSubtitle",       t.colorTextSecondary);
            setThemeColor(themeClass, "key_chat_outBubble",                 t.bubbleOut);
            setThemeColor(themeClass, "key_chat_outBubbleSelected",        adjustBrightness(t.bubbleOut, 0.85f));
            setThemeColor(themeClass, "key_chat_inBubble",                  t.bubbleIn);
            setThemeColor(themeClass, "key_chat_inBubbleSelected",         adjustBrightness(t.bubbleIn, 1.1f));
            setThemeColor(themeClass, "key_chat_outBubbleShadow",          adjustBrightness(t.bubbleOut, 0.7f));
            setThemeColor(themeClass, "key_chat_outMessageText",            t.bubbleOutText);
            setThemeColor(themeClass, "key_chat_inMessageText",             t.bubbleInText);
            setThemeColor(themeClass, "key_chat_messagePanelBackground",    surface);
            setThemeColor(themeClass, "key_chat_messagePanelText",          t.colorText);
            setThemeColor(themeClass, "key_chat_messagePanelIcons",         t.colorIcon);
            setThemeColor(themeClass, "key_chats_unreadCounter",            t.colorAccent);
            setThemeColor(themeClass, "key_chats_unreadCounterText",        t.colorText);
            setThemeColor(themeClass, "key_chats_name",                     t.colorText);
            setThemeColor(themeClass, "key_chats_message",                  t.colorTextSecondary);
            setThemeColor(themeClass, "key_chats_date",                     t.colorTextSecondary);
            setThemeColor(themeClass, "key_chats_actionBackground",         t.colorPrimary);
            setThemeColor(themeClass, "key_chats_actionIcon",               Color.WHITE);
            setThemeColor(themeClass, "key_divider",                        t.colorDivider);
            setThemeColor(themeClass, "key_graySection",                    bg);
            setThemeColor(themeClass, "key_graySectionText",                t.colorTextSecondary);

            // Trigger Telegram to redraw with new colors
            try {
                themeClass.getMethod("refreshThemeColors").invoke(null);
            } catch (Exception ignored) {
                // Method name may differ — look for similar in Theme.java
            }
        } catch (ClassNotFoundException e) {
            // Fallback: Theme class not found with this package — log for debugging
            android.util.Log.e("NxygramTheme", "Theme class not found: " + e.getMessage());
        }
    }

    private static void setThemeColor(Class<?> themeClass, String keyName, int color) {
        try {
            java.lang.reflect.Field keyField = themeClass.getField(keyName);
            int keyIndex = keyField.getInt(null);

            java.lang.reflect.Field colorsField = themeClass.getField("currentColors");
            int[] colors = (int[]) colorsField.get(null);
            if (colors != null && keyIndex >= 0 && keyIndex < colors.length) {
                colors[keyIndex] = color;
            }
        } catch (Exception ignored) {
            // Key may not exist in this version — safe to ignore
        }
    }

    private static int adjustBrightness(int color, float factor) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);
        hsv[2] = Math.min(1f, Math.max(0f, hsv[2] * factor));
        return Color.HSVToColor(Color.alpha(color), hsv);
    }

    // ─── Public Accessors ─────────────────────────────────────────────────────

    public static Map<String, NxygramTheme> getAllThemes() {
        return THEMES;
    }

    public static NxygramTheme getTheme(String name) {
        return THEMES.getOrDefault(name, THEMES.get(THEME_MIDNIGHT));
    }

    public static NxygramTheme getCurrentTheme() {
        return getTheme(NxygramSettings.getThemeName());
    }
}
