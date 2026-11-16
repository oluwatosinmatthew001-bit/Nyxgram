package com.nxygram;

import android.content.Context;

import java.util.ArrayList;
import java.util.List;

/**
 * MultiAccountManager
 * ══════════════════════════════════════════════════════════════
 * Extends Telegram's existing multi-account system with:
 *   - Custom label per account (Work, Personal, Gaming, etc.)
 *   - Custom accent color per account
 *   - Per-account Nxygram settings (theme, translation language, etc.)
 *   - Quick-switch badge in notification bar
 *   - Account-specific AI personalities
 *
 * Telegram already supports up to 4 accounts internally.
 * We enhance the switching UX and add per-account customization.
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/MultiAccountManager.java
 */
public class MultiAccountManager {

    private static MultiAccountManager instance;

    public static synchronized MultiAccountManager getInstance() {
        if (instance == null) instance = new MultiAccountManager();
        return instance;
    }

    // ─── Account profile ───────────────────────────────────────────────────

    public static class AccountProfile {
        public int    accountId;
        public String label;        // "Work", "Personal", "Gaming"
        public int    color;        // accent color for this account
        public String themeName;    // override theme for this account
        public String transLang;    // override translation language
        public boolean notifEnabled;
        public String aiPersonality; // AI prompt prefix for this account
    }

    // ─── Per-account settings keys ─────────────────────────────────────────

    private static String key(int accountId, String suffix) {
        return "account_" + accountId + "_" + suffix;
    }

    // ─── Label + color ─────────────────────────────────────────────────────

    public void setLabel(int accountId, String label) {
        NxygramSettings.setString(key(accountId, "label"), label);
    }

    public String getLabel(int accountId, String fallback) {
        String v = NxygramSettings.getString(key(accountId, "label"), "");
        return v.isEmpty() ? fallback : v;
    }

    public void setColor(int accountId, int color) {
        NxygramSettings.setInt(key(accountId, "color"), color);
    }

    public int getColor(int accountId) {
        int stored = NxygramSettings.getInt(key(accountId, "color"), 0);
        if (stored != 0) return stored;
        // Default colors per slot
        int[] defaults = {0xFF2AABEE, 0xFF7B2FBE, 0xFF4CAF50, 0xFFFF6B35};
        return defaults[accountId % defaults.length];
    }

    // ─── Per-account theme ─────────────────────────────────────────────────

    public void setAccountTheme(int accountId, String themeName) {
        NxygramSettings.setString(key(accountId, "theme"), themeName);
    }

    public String getAccountTheme(int accountId) {
        return NxygramSettings.getString(key(accountId, "theme"), "");
    }

    /**
     * Apply the theme for the given account.
     * Call when switching accounts.
     *
     * HOOK IN: LaunchActivity or wherever account switching happens:
     *   UserConfig.selectedAccount = newAccount;
     *   MultiAccountManager.getInstance().onAccountSwitched(newAccount);
     */
    public void onAccountSwitched(int accountId) {
        String themeName = getAccountTheme(accountId);
        if (!themeName.isEmpty()) {
            NxygramThemeManager.applyThemeByName(themeName);
        }
        String transLang = getAccountTransLang(accountId);
        if (!transLang.isEmpty()) {
            NxygramSettings.setTransTargetLang(transLang);
        }
    }

    // ─── Per-account translation language ─────────────────────────────────

    public void setAccountTransLang(int accountId, String lang) {
        NxygramSettings.setString(key(accountId, "trans_lang"), lang);
    }

    public String getAccountTransLang(int accountId) {
        return NxygramSettings.getString(key(accountId, "trans_lang"), "");
    }

    // ─── Per-account AI personality ────────────────────────────────────────

    public void setAiPersonality(int accountId, String personality) {
        NxygramSettings.setString(key(accountId, "ai_personality"), personality);
    }

    public String getAiPersonality(int accountId) {
        return NxygramSettings.getString(key(accountId, "ai_personality"), "");
    }

    /**
     * Get AI prompt prefix for current account.
     * Prepended to every PrexzyApi call when AI personality is set.
     */
    public String getAiPromptPrefix(int accountId) {
        String personality = getAiPersonality(accountId);
        if (personality.isEmpty()) return "";
        return "You are helping someone with the following context: "
                + personality + "\n\n";
    }

    // ─── List all configured accounts ─────────────────────────────────────

    public List<AccountProfile> getConfiguredAccounts(int maxAccounts) {
        List<AccountProfile> list = new ArrayList<>();
        for (int i = 0; i < maxAccounts; i++) {
            AccountProfile p = new AccountProfile();
            p.accountId     = i;
            p.label         = getLabel(i, "Account " + (i + 1));
            p.color         = getColor(i);
            p.themeName     = getAccountTheme(i);
            p.transLang     = getAccountTransLang(i);
            p.aiPersonality = getAiPersonality(i);
            list.add(p);
        }
        return list;
    }

    // ─── Badge indicator ───────────────────────────────────────────────────

    /**
     * Returns a small colored dot drawable spec for the account badge.
     * Used in the account switcher to show which account is active.
     *
     * Usage in DrawerLayoutAdapter or account header:
     *   int color = MultiAccountManager.getInstance().getColor(accountId);
     *   // Draw a filled circle of this color as the account indicator dot
     */
    public static android.graphics.drawable.GradientDrawable makeAccountDot(int color, int sizePx) {
        android.graphics.drawable.GradientDrawable d =
                new android.graphics.drawable.GradientDrawable();
        d.setShape(android.graphics.drawable.GradientDrawable.OVAL);
        d.setColor(color);
        d.setSize(sizePx, sizePx);
        return d;
    }
}
