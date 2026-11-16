package com.nxygram;

import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Color;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * TelegramThemeImporter
 * ══════════════════════════════════════════════════════════════
 * Import real Telegram .attheme files and apply them inside Nxygram.
 * Also allows switching between:
 *   - Built-in Nxygram themes (Midnight, Ocean, Royal, Rose, Emerald, Pure)
 *   - Imported real Telegram themes (.attheme format)
 *   - Official Telegram bundled themes (Day, Night, Classic, etc.)
 *
 * .attheme format:
 *   key_name=ARGB_int_value
 *   e.g. key_actionBarDefault=-14606047
 *
 * Hook points:
 *   LaunchActivity / ApplicationLoader → applyActiveTheme()
 *   ThemeStudioActivity → list + switch themes
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/TelegramThemeImporter.java
 */
public class TelegramThemeImporter {

    private static TelegramThemeImporter instance;
    private final Context context;

    // Theme types
    public static final String TYPE_NXYGRAM  = "nxygram";
    public static final String TYPE_TELEGRAM = "telegram";
    public static final String TYPE_IMPORTED = "imported";

    // Setting keys
    private static final String KEY_ACTIVE_TYPE = "theme_active_type";
    private static final String KEY_ACTIVE_NAME = "theme_active_name";
    private static final String PREFS           = "nxygram_themes";

    public static synchronized TelegramThemeImporter getInstance(Context context) {
        if (instance == null)
            instance = new TelegramThemeImporter(context.getApplicationContext());
        return instance;
    }

    private TelegramThemeImporter(Context context) {
        this.context = context;
    }

    // ─── Theme descriptor ──────────────────────────────────────────────────

    public static class ThemeEntry {
        public String type;        // TYPE_NXYGRAM / TYPE_TELEGRAM / TYPE_IMPORTED
        public String name;        // display name
        public String filePath;    // null for built-ins
        public boolean isDark;
        public int    previewColor; // accent color for preview swatch
        public boolean isActive;
    }

    // ─── Built-in Telegram themes ──────────────────────────────────────────

    private static final String[] TELEGRAM_BUILTIN = {
        "Day", "Night", "Classic", "Dark Blue", "Arctic Blue", "Night Blue"
    };

    // ─── List all available themes ─────────────────────────────────────────

    public List<ThemeEntry> getAllThemes() {
        List<ThemeEntry> list = new ArrayList<>();
        String activeType = getActiveType();
        String activeName = getActiveName();

        // 1. Nxygram built-ins
        for (Map.Entry<String, NxygramThemeManager.NxygramTheme> e :
                NxygramThemeManager.getAllThemes().entrySet()) {
            ThemeEntry te = new ThemeEntry();
            te.type         = TYPE_NXYGRAM;
            te.name         = e.getKey();
            te.isDark       = e.getValue().isDark;
            te.previewColor = e.getValue().colorPrimary;
            te.isActive     = TYPE_NXYGRAM.equals(activeType) && te.name.equals(activeName);
            list.add(te);
        }

        // 2. Telegram built-in themes
        for (String name : TELEGRAM_BUILTIN) {
            ThemeEntry te = new ThemeEntry();
            te.type         = TYPE_TELEGRAM;
            te.name         = name;
            te.isDark       = name.toLowerCase().contains("night") ||
                              name.toLowerCase().contains("dark");
            te.previewColor = getTelegramBuiltinAccent(name);
            te.isActive     = TYPE_TELEGRAM.equals(activeType) && te.name.equals(activeName);
            list.add(te);
        }

        // 3. Imported .attheme files
        File importDir = getImportDir();
        if (importDir.exists()) {
            File[] files = importDir.listFiles(f -> f.getName().endsWith(".attheme"));
            if (files != null) {
                for (File f : files) {
                    ThemeEntry te = new ThemeEntry();
                    te.type         = TYPE_IMPORTED;
                    te.name         = f.getName().replace(".attheme", "");
                    te.filePath     = f.getAbsolutePath();
                    te.isDark       = te.name.toLowerCase().contains("dark") ||
                                      te.name.toLowerCase().contains("night");
                    te.previewColor = readAccentFromAttheme(f);
                    te.isActive     = TYPE_IMPORTED.equals(activeType) && te.name.equals(te.name);
                    list.add(te);
                }
            }
        }

        return list;
    }

    // ─── Import .attheme ───────────────────────────────────────────────────

    /**
     * Import a .attheme file from bytes (e.g. user sent it via Telegram).
     * Saves to internal storage and adds to available themes.
     */
    public boolean importAttheme(String themeName, byte[] atthemeBytes) {
        try {
            File dir  = getImportDir();
            if (!dir.exists()) dir.mkdirs();
            String safeName = themeName.replaceAll("[^a-zA-Z0-9_\\-. ]", "_");
            if (!safeName.endsWith(".attheme")) safeName += ".attheme";
            File out = new File(dir, safeName);
            try (FileOutputStream fos = new FileOutputStream(out)) {
                fos.write(atthemeBytes);
            }
            return true;
        } catch (IOException e) {
            android.util.Log.e("ThemeImporter", "Import failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Parse a .attheme file into a color map.
     * Format: key=intValue (one per line, # for comments)
     */
    public Map<String, Integer> parseAttheme(byte[] bytes) {
        Map<String, Integer> colors = new HashMap<>();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(new ByteArrayInputStream(bytes),
                        StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                int eq = line.indexOf('=');
                if (eq < 0) continue;
                String key = line.substring(0, eq).trim();
                String val = line.substring(eq + 1).trim();
                try {
                    colors.put(key, (int) Long.parseLong(val));
                } catch (NumberFormatException ignored) {}
            }
        } catch (IOException e) {
            android.util.Log.e("ThemeImporter", "Parse error: " + e.getMessage());
        }
        return colors;
    }

    // ─── Apply theme ───────────────────────────────────────────────────────

    /**
     * Apply a theme by type + name.
     * This is the single entry point for all theme switching.
     *
     * Hook: Call after user selects a theme in ThemeStudioActivity.
     */
    public void applyTheme(String type, String name, String filePath) {
        setActiveTheme(type, name);

        switch (type) {
            case TYPE_NXYGRAM:
                NxygramThemeManager.applyThemeByName(name);
                break;

            case TYPE_TELEGRAM:
                applyTelegramBuiltin(name);
                break;

            case TYPE_IMPORTED:
                if (filePath != null) applyImportedAttheme(filePath);
                break;
        }
    }

    /**
     * Re-apply the last active theme on app start.
     * Hook in ApplicationLoader.onCreate()
     */
    public void applyActiveTheme() {
        String type = getActiveType();
        String name = getActiveName();
        if (type == null || name == null) {
            NxygramThemeManager.applyCurrentTheme();
            return;
        }
        String filePath = null;
        if (TYPE_IMPORTED.equals(type)) {
            filePath = new File(getImportDir(), name + ".attheme").getAbsolutePath();
        }
        applyTheme(type, name, filePath);
    }

    /**
     * Apply a Telegram built-in theme by name.
     *
     * HOOK: Telegram's Theme.java has applyTheme(ThemeInfo) or similar.
     * We call it by finding the matching ThemeInfo from Theme.themes list.
     *
     * In Telegram source (Theme.java or LaunchActivity.java):
     *   Theme.ThemeInfo info = getThemeByName(name);
     *   if (info != null) Theme.applyTheme(info, false);
     *
     * Since we can't call that at compile time without direct import,
     * we use reflection here — same as NxygramThemeManager.
     */
    private void applyTelegramBuiltin(String themeName) {
        try {
            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");

            // Try to find themes list
            java.lang.reflect.Field themesField = themeClass.getField("themes");
            java.util.ArrayList<?> themes = (java.util.ArrayList<?>) themesField.get(null);

            if (themes != null) {
                for (Object themeInfo : themes) {
                    java.lang.reflect.Field nameField = themeInfo.getClass().getField("name");
                    String name = (String) nameField.get(themeInfo);
                    if (themeName.equalsIgnoreCase(name)) {
                        java.lang.reflect.Method applyMethod =
                                themeClass.getMethod("applyTheme",
                                        themeInfo.getClass(), boolean.class);
                        applyMethod.invoke(null, themeInfo, false);
                        return;
                    }
                }
            }
        } catch (Exception e) {
            android.util.Log.e("ThemeImporter",
                    "Telegram theme apply failed: " + e.getMessage());
        }
    }

    private void applyImportedAttheme(String filePath) {
        try {
            File f = new File(filePath);
            if (!f.exists()) return;
            byte[] bytes = readFile(f);
            Map<String, Integer> colors = parseAttheme(bytes);

            Class<?> themeClass = Class.forName("org.telegram.ui.ActionBar.Theme");

            // Apply each color to Telegram's currentColors array
            for (Map.Entry<String, Integer> entry : colors.entrySet()) {
                setThemeColorReflect(themeClass, entry.getKey(), entry.getValue());
            }

            // Refresh
            try {
                themeClass.getMethod("refreshThemeColors").invoke(null);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            android.util.Log.e("ThemeImporter",
                    "Imported theme apply failed: " + e.getMessage());
        }
    }

    private void setThemeColorReflect(Class<?> themeClass, String keyName, int color) {
        try {
            java.lang.reflect.Field keyField = themeClass.getField(keyName);
            int keyIndex = keyField.getInt(null);
            java.lang.reflect.Field colorsField = themeClass.getField("currentColors");
            int[] colors = (int[]) colorsField.get(null);
            if (colors != null && keyIndex >= 0 && keyIndex < colors.length) {
                colors[keyIndex] = color;
            }
        } catch (Exception ignored) {}
    }

    // ─── Custom app icons ──────────────────────────────────────────────────

    /**
     * Switch the app launcher icon.
     * Android supports this via activity-alias in AndroidManifest.
     *
     * Add these aliases to AndroidManifest.xml inside <application>:
     *
     *   <!-- Default icon (always enabled) -->
     *   <activity-alias android:name=".IconDefault"
     *       android:targetActivity=".LaunchActivity"
     *       android:enabled="true"
     *       android:icon="@mipmap/ic_launcher_nxygram">
     *       <intent-filter>
     *           <action android:name="android.intent.action.MAIN"/>
     *           <category android:name="android.intent.category.LAUNCHER"/>
     *       </intent-filter>
     *   </activity-alias>
     *
     *   <!-- Midnight icon -->
     *   <activity-alias android:name=".IconMidnight"
     *       android:targetActivity=".LaunchActivity"
     *       android:enabled="false"
     *       android:icon="@mipmap/ic_launcher_midnight">
     *       <intent-filter>
     *           <action android:name="android.intent.action.MAIN"/>
     *           <category android:name="android.intent.category.LAUNCHER"/>
     *       </intent-filter>
     *   </activity-alias>
     *
     *   <!-- Telegram Classic icon -->
     *   <activity-alias android:name=".IconTelegram"
     *       android:targetActivity=".LaunchActivity"
     *       android:enabled="false"
     *       android:icon="@mipmap/ic_launcher_telegram">
     *       <intent-filter>
     *           <action android:name="android.intent.action.MAIN"/>
     *           <category android:name="android.intent.category.LAUNCHER"/>
     *       </intent-filter>
     *   </activity-alias>
     */
    public void switchAppIcon(String iconAlias) {
        android.content.pm.PackageManager pm = context.getPackageManager();
        String pkg = context.getPackageName();

        String[] allAliases = {
            pkg + ".IconDefault",
            pkg + ".IconMidnight",
            pkg + ".IconOcean",
            pkg + ".IconRoyal",
            pkg + ".IconTelegram",
            pkg + ".IconMinimal",
        };

        for (String alias : allAliases) {
            int state = alias.equals(pkg + "." + iconAlias)
                    ? android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_ENABLED
                    : android.content.pm.PackageManager.COMPONENT_ENABLED_STATE_DISABLED;
            try {
                pm.setComponentEnabledSetting(
                        new android.content.ComponentName(pkg, alias),
                        state,
                        android.content.pm.PackageManager.DONT_KILL_APP
                );
            } catch (Exception ignored) {}
        }

        NxygramSettings.setString("active_icon_alias", iconAlias);
    }

    public String getActiveIconAlias() {
        return NxygramSettings.getString("active_icon_alias", "IconDefault");
    }

    // ─── Persistence ───────────────────────────────────────────────────────

    private void setActiveTheme(String type, String name) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
               .edit()
               .putString(KEY_ACTIVE_TYPE, type)
               .putString(KEY_ACTIVE_NAME, name)
               .apply();
    }

    private String getActiveType() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                      .getString(KEY_ACTIVE_TYPE, TYPE_NXYGRAM);
    }

    private String getActiveName() {
        return context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                      .getString(KEY_ACTIVE_NAME, NxygramThemeManager.THEME_MIDNIGHT);
    }

    private File getImportDir() {
        return new File(context.getFilesDir(), "NxygramImportedThemes");
    }

    private int readAccentFromAttheme(File f) {
        try {
            byte[] bytes = readFile(f);
            Map<String, Integer> colors = parseAttheme(bytes);
            // Try common accent keys in order of preference
            for (String key : new String[]{
                    "key_actionBarDefault", "key_chats_actionBackground",
                    "key_chat_outBubble", "key_windowBackgroundWhiteBlueText"}) {
                if (colors.containsKey(key)) return colors.get(key);
            }
        } catch (Exception ignored) {}
        return 0xFF2AABEE; // Telegram blue fallback
    }

    private int getTelegramBuiltinAccent(String name) {
        switch (name) {
            case "Day":        return 0xFF2AABEE;
            case "Night":      return 0xFF2AABEE;
            case "Classic":    return 0xFF5296C5;
            case "Dark Blue":  return 0xFF2B7DE8;
            case "Arctic Blue":return 0xFF29B6E0;
            case "Night Blue": return 0xFF2B5DA1;
            default:           return 0xFF2AABEE;
        }
    }

    private byte[] readFile(File f) throws IOException {
        byte[] data = new byte[(int) f.length()];
        try (java.io.FileInputStream fis = new java.io.FileInputStream(f)) {
            fis.read(data);
        }
        return data;
    }
}
