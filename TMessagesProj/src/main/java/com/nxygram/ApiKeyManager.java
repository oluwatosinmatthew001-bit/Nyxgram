package com.nxygram;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * ApiKeyManager
 * Secure storage for all external API keys used by Nxygram features.
 * Keys are encrypted with AES-256-GCM using Android Keystore.
 *
 * Keys managed:
 *   - LibreTranslate API key + endpoint
 *   - DeepL API key
 *   - Google Translate API key
 *   - OpenAI API key (AI Assistant)
 *   - Custom AI endpoint
 *   - Anthropic API key (optional)
 *
 * Usage:
 *   ApiKeyManager.getInstance(context).setKey(ApiKeyManager.KEY_DEEPL, "your-key");
 *   String key = ApiKeyManager.getInstance(context).getKey(ApiKeyManager.KEY_DEEPL);
 *   boolean valid = ApiKeyManager.getInstance(context).hasKey(ApiKeyManager.KEY_DEEPL);
 */
public class ApiKeyManager {

    // ─── Key Constants ────────────────────────────────────────────────────────

    public static final String KEY_LIBRETRANSLATE_ENDPOINT = "libre_endpoint";
    public static final String KEY_LIBRETRANSLATE_KEY      = "libre_apikey";
    public static final String KEY_DEEPL_KEY               = "deepl_apikey";
    public static final String KEY_GOOGLE_TRANSLATE_KEY    = "google_translate_key";
    public static final String KEY_OPENAI_KEY              = "openai_apikey";
    public static final String KEY_OPENAI_ENDPOINT         = "openai_endpoint";
    public static final String KEY_ANTHROPIC_KEY           = "anthropic_apikey";
    public static final String KEY_CUSTOM_AI_ENDPOINT      = "custom_ai_endpoint";
    public static final String KEY_CUSTOM_AI_KEY           = "custom_ai_apikey";

    // ─── Service Descriptors (used by ApiKeyDialog) ───────────────────────────

    public static class ServiceInfo {
        public final String id;
        public final String displayName;
        public final String description;
        public final String keyPlaceholder;
        public final String keyLabel;
        public final String[] extraFieldIds;
        public final String[] extraFieldLabels;
        public final String[] extraFieldPlaceholders;
        public final String docsUrl;

        public ServiceInfo(String id, String displayName, String description,
                           String keyLabel, String keyPlaceholder,
                           String[] extraFieldIds, String[] extraFieldLabels,
                           String[] extraFieldPlaceholders, String docsUrl) {
            this.id                   = id;
            this.displayName          = displayName;
            this.description          = description;
            this.keyLabel             = keyLabel;
            this.keyPlaceholder       = keyPlaceholder;
            this.extraFieldIds        = extraFieldIds;
            this.extraFieldLabels     = extraFieldLabels;
            this.extraFieldPlaceholders = extraFieldPlaceholders;
            this.docsUrl              = docsUrl;
        }
    }

    public static final ServiceInfo[] TRANSLATION_SERVICES = {
        new ServiceInfo(
            "mymemory", "MyMemory", "Free translation, no key needed. 5000 chars/day.",
            null, null, null, null, null,
            "https://mymemory.translated.net"
        ),
        new ServiceInfo(
            "libretranslate", "LibreTranslate",
            "Open source, self-hostable. Free public instances available.",
            "API Key (optional)", "Leave blank for public instances",
            new String[]{KEY_LIBRETRANSLATE_ENDPOINT},
            new String[]{"Server URL"},
            new String[]{"https://libretranslate.com"},
            "https://libretranslate.com"
        ),
        new ServiceInfo(
            "deepl", "DeepL",
            "High quality translations. Free tier: 500,000 chars/month.",
            "DeepL API Key", "xxxxxxxx-xxxx-xxxx-xxxx-xxxxxxxxxxxx:fx",
            null, null, null,
            "https://www.deepl.com/pro-api"
        ),
        new ServiceInfo(
            "google", "Google Translate",
            "Google Cloud Translation API. Pay-per-use.",
            "Google API Key", "AIzaSy...",
            null, null, null,
            "https://cloud.google.com/translate/docs/setup"
        ),
    };

    public static final ServiceInfo[] AI_SERVICES = {
        new ServiceInfo(
            "openai", "OpenAI (ChatGPT)",
            "GPT-4o for Smart Reply, summarization, grammar, tone.",
            "OpenAI API Key", "sk-...",
            new String[]{KEY_OPENAI_ENDPOINT},
            new String[]{"API Endpoint (optional)"},
            new String[]{"https://api.openai.com/v1"},
            "https://platform.openai.com/api-keys"
        ),
        new ServiceInfo(
            "custom_ai", "Custom / Self-hosted AI",
            "Any OpenAI-compatible endpoint (Ollama, LM Studio, etc.)",
            "API Key (if required)", "sk-... or leave blank",
            new String[]{KEY_CUSTOM_AI_ENDPOINT},
            new String[]{"Endpoint URL"},
            new String[]{"http://localhost:11434/v1"},
            null
        ),
    };

    // ─── Singleton ────────────────────────────────────────────────────────────

    private static ApiKeyManager instance;
    private final Context context;
    private static final String KEYSTORE_ALIAS = "NxygramApiKeys";
    private static final String PREFS_NAME     = "nxygram_apikeys";

    public static synchronized ApiKeyManager getInstance(Context context) {
        if (instance == null) {
            instance = new ApiKeyManager(context.getApplicationContext());
        }
        return instance;
    }

    private ApiKeyManager(Context context) {
        this.context = context;
        ensureKeystoreKey();
    }

    // ─── Public API ───────────────────────────────────────────────────────────

    public void setKey(String keyId, String value) {
        if (value == null || value.isEmpty()) {
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                   .edit().remove(keyId).remove(keyId + "_iv").apply();
            return;
        }
        try {
            byte[] encrypted = encrypt(value);
            byte[] iv        = getLastIv();
            String encB64    = android.util.Base64.encodeToString(encrypted, android.util.Base64.NO_WRAP);
            String ivB64     = android.util.Base64.encodeToString(iv,        android.util.Base64.NO_WRAP);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                   .edit()
                   .putString(keyId,        encB64)
                   .putString(keyId + "_iv", ivB64)
                   .apply();
        } catch (Exception e) {
            // Fallback: store obfuscated (not ideal, but better than plaintext)
            String obf = android.util.Base64.encodeToString(
                    value.getBytes(StandardCharsets.UTF_8), android.util.Base64.NO_WRAP);
            context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
                   .edit().putString(keyId, obf).apply();
        }
    }

    public String getKey(String keyId) {
        SharedPreferences prefs = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
        String encB64 = prefs.getString(keyId, null);
        String ivB64  = prefs.getString(keyId + "_iv", null);
        if (encB64 == null) return null;
        if (ivB64 == null) {
            // Fallback decode
            return new String(android.util.Base64.decode(encB64, android.util.Base64.NO_WRAP),
                    StandardCharsets.UTF_8);
        }
        try {
            byte[] encrypted = android.util.Base64.decode(encB64, android.util.Base64.NO_WRAP);
            byte[] iv        = android.util.Base64.decode(ivB64,  android.util.Base64.NO_WRAP);
            return decrypt(encrypted, iv);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean hasKey(String keyId) {
        String v = getKey(keyId);
        return v != null && !v.isEmpty();
    }

    public void clearKey(String keyId) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().remove(keyId).remove(keyId + "_iv").apply();
    }

    public void clearAll() {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
               .edit().clear().apply();
    }

    // ─── Feature Readiness Checks ─────────────────────────────────────────────

    /**
     * Returns true if the currently selected translation provider is ready to use.
     * If not, the caller should show ApiKeyDialog.
     */
    public boolean isTranslationReady() {
        String provider = NxygramSettings.getTransProvider();
        switch (provider) {
            case "mymemory":    return true; // no key needed
            case "libretranslate": return true; // key is optional
            case "deepl":       return hasKey(KEY_DEEPL_KEY);
            case "google":      return hasKey(KEY_GOOGLE_TRANSLATE_KEY);
            default:            return false;
        }
    }

    public boolean isAiReady() {
        String provider = NxygramSettings.getAiProvider();
        switch (provider) {
            case "openai":    return hasKey(KEY_OPENAI_KEY);
            case "custom_ai": return hasKey(KEY_CUSTOM_AI_ENDPOINT);
            default:          return false;
        }
    }

    // ─── AES-256-GCM Encryption ───────────────────────────────────────────────

    private byte[] lastIv;

    private void ensureKeystoreKey() {
        try {
            KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
            ks.load(null);
            if (!ks.containsAlias(KEYSTORE_ALIAS)) {
                KeyGenerator kg = KeyGenerator.getInstance(
                        KeyProperties.KEY_ALGORITHM_AES, "AndroidKeyStore");
                kg.init(new KeyGenParameterSpec.Builder(KEYSTORE_ALIAS,
                        KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                        .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                        .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                        .setKeySize(256)
                        .build());
                kg.generateKey();
            }
        } catch (Exception e) {
            android.util.Log.e("ApiKeyManager", "Keystore init failed: " + e.getMessage());
        }
    }

    private SecretKey getSecretKey() throws Exception {
        KeyStore ks = KeyStore.getInstance("AndroidKeyStore");
        ks.load(null);
        return ((KeyStore.SecretKeyEntry) ks.getEntry(KEYSTORE_ALIAS, null)).getSecretKey();
    }

    private byte[] encrypt(String plaintext) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.ENCRYPT_MODE, getSecretKey());
        lastIv = cipher.getIV();
        return cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
    }

    private byte[] getLastIv() { return lastIv; }

    private String decrypt(byte[] ciphertext, byte[] iv) throws Exception {
        Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE, getSecretKey(), new GCMParameterSpec(128, iv));
        return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
    }
}
