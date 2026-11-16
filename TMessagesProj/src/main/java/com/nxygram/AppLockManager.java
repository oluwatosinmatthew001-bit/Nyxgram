package com.nxygram;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;

import androidx.biometric.BiometricManager;
import androidx.biometric.BiometricPrompt;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.FragmentActivity;

import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.concurrent.Executor;

/**
 * AppLockManager
 * Handles PIN hashing, biometric auth, and lock-state tracking.
 *
 * Hook in LaunchActivity / MainActivity:
 *   onResume()  → AppLockManager.getInstance().checkLock(activity, callback)
 *   onPause()   → AppLockManager.getInstance().onAppBackgrounded()
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/AppLockManager.java
 * Also add to AndroidManifest.xml:
 *   <activity android:name="com.nxygram.AppLockActivity" android:exported="false"/>
 */
public class AppLockManager {

    private static AppLockManager instance;
    private long backgroundedAt = -1;
    private boolean isUnlocked  = false;

    public static synchronized AppLockManager getInstance() {
        if (instance == null) instance = new AppLockManager();
        return instance;
    }

    public interface LockCallback {
        void onUnlocked();
        void onLockRequired();
    }

    // ─── Lock State ───────────────────────────────────────────────────────────

    public void onAppBackgrounded() {
        if (NxygramSettings.isAppLockEnabled()) {
            backgroundedAt = System.currentTimeMillis();
        }
    }

    public void checkLock(Activity activity, LockCallback cb) {
        if (!NxygramSettings.isAppLockEnabled()) {
            cb.onUnlocked(); return;
        }
        if (isUnlocked && !isTimerExpired()) {
            cb.onUnlocked(); return;
        }
        // Lock required
        cb.onLockRequired();
        Intent intent = new Intent(activity, AppLockActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        activity.startActivity(intent);
    }

    private boolean isTimerExpired() {
        if (backgroundedAt < 0) return false;
        long elapsed = (System.currentTimeMillis() - backgroundedAt) / 1000;
        return elapsed >= NxygramSettings.getAppLockTimer();
    }

    public void onUnlockSuccess() {
        isUnlocked = true;
        backgroundedAt = -1;
    }

    public void lock() {
        isUnlocked = false;
        backgroundedAt = 0;
    }

    // ─── PIN Management ───────────────────────────────────────────────────────

    private static final String SALT_KEY = "app_lock_salt";

    public void setPin(String pin) {
        String salt = generateSalt();
        NxygramSettings.setString(SALT_KEY, salt);
        NxygramSettings.setString(NxygramSettings.KEY_APP_LOCK_PIN, hashPin(pin, salt));
    }

    public boolean verifyPin(String pin) {
        String salt  = NxygramSettings.getString(SALT_KEY, "");
        String stored = NxygramSettings.getString(NxygramSettings.KEY_APP_LOCK_PIN, "");
        return !stored.isEmpty() && stored.equals(hashPin(pin, salt));
    }

    public boolean hasPin() {
        return !NxygramSettings.getString(NxygramSettings.KEY_APP_LOCK_PIN, "").isEmpty();
    }

    private String hashPin(String pin, String salt) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            md.update((salt + pin).getBytes("UTF-8"));
            byte[] digest = md.digest();
            StringBuilder sb = new StringBuilder();
            for (byte b : digest) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            return pin; // fallback (should never happen)
        }
    }

    private String generateSalt() {
        SecureRandom sr = new SecureRandom();
        byte[] salt = new byte[16];
        sr.nextBytes(salt);
        StringBuilder sb = new StringBuilder();
        for (byte b : salt) sb.append(String.format("%02x", b));
        return sb.toString();
    }

    // ─── Biometric Auth ───────────────────────────────────────────────────────

    public boolean isBiometricAvailable(Context context) {
        BiometricManager bm = BiometricManager.from(context);
        return bm.canAuthenticate(BiometricManager.Authenticators.BIOMETRIC_STRONG)
                == BiometricManager.BIOMETRIC_SUCCESS;
    }

    public void showBiometricPrompt(FragmentActivity activity, BiometricCallback callback) {
        Executor executor = ContextCompat.getMainExecutor(activity);

        BiometricPrompt prompt = new BiometricPrompt(activity, executor,
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(BiometricPrompt.AuthenticationResult result) {
                        onUnlockSuccess();
                        callback.onSuccess();
                    }
                    @Override
                    public void onAuthenticationFailed() {
                        callback.onFailed();
                    }
                    @Override
                    public void onAuthenticationError(int errorCode, CharSequence errString) {
                        callback.onError(errString.toString());
                    }
                });

        BiometricPrompt.PromptInfo info = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Unlock Nxygram")
                .setSubtitle("Confirm your identity to continue")
                .setNegativeButtonText("Use PIN")
                .build();

        prompt.authenticate(info);
    }

    public interface BiometricCallback {
        void onSuccess();
        void onFailed();
        void onError(String error);
    }
}
