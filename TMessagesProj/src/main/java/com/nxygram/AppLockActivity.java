package com.nxygram;

import android.content.Intent;
import android.graphics.Color;
import android.os.Bundle;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

/**
 * AppLockActivity
 * Full-screen PIN entry + biometric unlock screen.
 *
 * Register in AndroidManifest.xml:
 *   <activity
 *       android:name="com.nxygram.AppLockActivity"
 *       android:exported="false"
 *       android:windowSoftInputMode="stateHidden"
 *       android:excludeFromRecents="true"
 *       android:theme="@style/Theme.TMessages" />
 */
public class AppLockActivity extends AppCompatActivity {

    private StringBuilder pinInput = new StringBuilder();
    private TextView[]    pinDots;
    private TextView      statusText;
    private int           failedAttempts = 0;
    private static final int MAX_PIN_LENGTH = 6;
    private static final int MAX_ATTEMPTS   = 5;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SECURE);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        NxygramSettings.init(this);

        setContentView(buildUI());

        // Try biometric first if available and configured
        String lockType = NxygramSettings.getAppLockType();
        if ("biometric".equals(lockType) && AppLockManager.getInstance().isBiometricAvailable(this)) {
            AppLockManager.getInstance().showBiometricPrompt(this, new AppLockManager.BiometricCallback() {
                @Override public void onSuccess() { unlockAndFinish(); }
                @Override public void onFailed()  { statusText.setText("Biometric not recognised"); }
                @Override public void onError(String error) {
                    // Fall back to PIN silently
                    statusText.setText("Use PIN to unlock");
                }
            });
        }
    }

    @Override public void onBackPressed() {
        // Cannot dismiss lock screen with back button
        moveTaskToBack(true);
    }

    // ─── UI ───────────────────────────────────────────────────────────────────

    private View buildUI() {
        NxygramThemeManager.NxygramTheme theme = NxygramThemeManager.getCurrentTheme();

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(theme.colorBackground);
        root.setPadding(dp(32), dp(60), dp(32), dp(40));

        // App name
        TextView appName = new TextView(this);
        appName.setText("✦ Nxygram");
        appName.setTextSize(28);
        appName.setTextColor(theme.colorPrimary);
        appName.setGravity(Gravity.CENTER);
        root.addView(appName);

        // Tagline
        TextView tagline = new TextView(this);
        tagline.setText("Enter PIN to unlock");
        tagline.setTextSize(14);
        tagline.setTextColor(theme.colorTextSecondary);
        tagline.setGravity(Gravity.CENTER);
        tagline.setPadding(0, dp(8), 0, dp(32));
        root.addView(tagline);

        // PIN dots
        LinearLayout dotsRow = new LinearLayout(this);
        dotsRow.setGravity(Gravity.CENTER);
        dotsRow.setPadding(0, 0, 0, dp(32));
        pinDots = new TextView[MAX_PIN_LENGTH];
        for (int i = 0; i < MAX_PIN_LENGTH; i++) {
            TextView dot = new TextView(this);
            dot.setText("○");
            dot.setTextSize(28);
            dot.setTextColor(theme.colorTextSecondary);
            dot.setPadding(dp(10), 0, dp(10), 0);
            pinDots[i] = dot;
            dotsRow.addView(dot);
        }
        root.addView(dotsRow);

        // Status text
        statusText = new TextView(this);
        statusText.setText("");
        statusText.setTextSize(13);
        statusText.setTextColor(0xFFFF5252);
        statusText.setGravity(Gravity.CENTER);
        statusText.setPadding(0, 0, 0, dp(16));
        root.addView(statusText);

        // Number pad
        root.addView(buildNumPad(theme));

        // Biometric button (if available)
        if (AppLockManager.getInstance().isBiometricAvailable(this)) {
            Button bioBtn = makeButton("👆 Use Biometric", theme.colorPrimary);
            bioBtn.setOnClickListener(v ->
                AppLockManager.getInstance().showBiometricPrompt(this, new AppLockManager.BiometricCallback() {
                    @Override public void onSuccess() { unlockAndFinish(); }
                    @Override public void onFailed()  { statusText.setText("Try again"); }
                    @Override public void onError(String e) { statusText.setText("Use PIN"); }
                })
            );
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp(48));
            lp.topMargin = dp(16);
            bioBtn.setLayoutParams(lp);
            root.addView(bioBtn);
        }

        return root;
    }

    private GridLayout buildNumPad(NxygramThemeManager.NxygramTheme theme) {
        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(3);
        grid.setRowCount(4);

        int[] nums = {1, 2, 3, 4, 5, 6, 7, 8, 9, -1, 0, -2};
        for (int n : nums) {
            Button btn = new Button(this);
            if (n >= 0) {
                btn.setText(String.valueOf(n));
                btn.setTextSize(22);
                btn.setTextColor(theme.colorText);
                btn.setBackgroundColor(theme.colorSurface);
                final int digit = n;
                btn.setOnClickListener(v -> onDigitPressed(digit));
            } else if (n == -1) {
                btn.setText("");
                btn.setEnabled(false);
                btn.setBackgroundColor(Color.TRANSPARENT);
            } else {
                btn.setText("⌫");
                btn.setTextSize(22);
                btn.setTextColor(theme.colorTextSecondary);
                btn.setBackgroundColor(theme.colorSurface);
                btn.setOnClickListener(v -> onDelete());
            }

            GridLayout.LayoutParams lp = new GridLayout.LayoutParams();
            lp.width  = dp(90);
            lp.height = dp(72);
            lp.setMargins(dp(4), dp(4), dp(4), dp(4));
            btn.setLayoutParams(lp);
            grid.addView(btn);
        }
        return grid;
    }

    // ─── PIN Logic ────────────────────────────────────────────────────────────

    private void onDigitPressed(int digit) {
        if (pinInput.length() >= MAX_PIN_LENGTH) return;
        pinInput.append(digit);
        updateDots();
        if (pinInput.length() == MAX_PIN_LENGTH) {
            verifyPin();
        }
    }

    private void onDelete() {
        if (pinInput.length() > 0) {
            pinInput.deleteCharAt(pinInput.length() - 1);
            updateDots();
        }
    }

    private void updateDots() {
        for (int i = 0; i < MAX_PIN_LENGTH; i++) {
            pinDots[i].setText(i < pinInput.length() ? "●" : "○");
            NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
            pinDots[i].setTextColor(i < pinInput.length() ? t.colorPrimary : t.colorTextSecondary);
        }
    }

    private void verifyPin() {
        String entered = pinInput.toString();
        pinInput.setLength(0);
        updateDots();

        if (AppLockManager.getInstance().verifyPin(entered)) {
            AppLockManager.getInstance().onUnlockSuccess();
            unlockAndFinish();
        } else {
            failedAttempts++;
            vibrate();
            if (failedAttempts >= MAX_ATTEMPTS) {
                statusText.setText("Too many attempts. Try again in 30s.");
                // TODO: implement cooldown timer
            } else {
                statusText.setText("Wrong PIN — " + (MAX_ATTEMPTS - failedAttempts) + " attempts left");
            }
        }
    }

    private void unlockAndFinish() {
        finish();
        overridePendingTransition(0, android.R.anim.fade_out);
    }

    private void vibrate() {
        Vibrator v = (Vibrator) getSystemService(VIBRATOR_SERVICE);
        if (v != null && v.hasVibrator()) {
            if (android.os.Build.VERSION.SDK_INT >= 26) {
                v.vibrate(VibrationEffect.createWaveform(new long[]{0, 80, 40, 80}, -1));
            } else {
                v.vibrate(new long[]{0, 80, 40, 80}, -1);
            }
        }
    }

    private Button makeButton(String label, int bgColor) {
        Button btn = new Button(this);
        btn.setText(label);
        btn.setTextColor(Color.WHITE);
        btn.setBackgroundColor(bgColor);
        return btn;
    }

    private int dp(int dp) {
        return (int) (dp * getResources().getDisplayMetrics().density);
    }
}
