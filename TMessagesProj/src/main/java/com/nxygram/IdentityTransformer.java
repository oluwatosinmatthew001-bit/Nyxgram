package com.nxygram;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.Rect;
import android.graphics.RectF;

import java.io.File;
import java.io.FileOutputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * IdentityTransformer
 * ══════════════════════════════════════════════════════════════
 * LOCAL display transformation — changes how YOU appear on YOUR screen only.
 * Nothing is sent to Telegram's servers. The other person sees your real profile.
 *
 * Features:
 *   - Overlay a custom display name on outgoing message sender labels
 *   - Overlay a custom avatar on your profile picture rendering
 *   - Apply a color tint or frame to your avatar
 *   - Switch between multiple "identity profiles" (work/personal/gaming)
 *   - Show a different bio in self-view
 *
 * Also handles: transform OTHER users' display (rename contacts locally,
 *   custom avatars for contacts — only on your device).
 *
 * Drop into: TMessagesProj/src/main/java/com/nxygram/IdentityTransformer.java
 */
public class IdentityTransformer {

    private static IdentityTransformer instance;
    private final Context context;

    public static synchronized IdentityTransformer getInstance(Context context) {
        if (instance == null)
            instance = new IdentityTransformer(context.getApplicationContext());
        return instance;
    }

    private IdentityTransformer(Context context) {
        this.context = context;
    }

    // ─── Identity Profile model ────────────────────────────────────────────

    public static class IdentityProfile {
        public String id;           // unique key
        public String displayName;  // local override name
        public String bio;          // local override bio
        public String avatarPath;   // path to custom avatar image
        public int    avatarColor;  // tint color overlay
        public boolean isActive;
    }

    // ─── Self identity profiles ────────────────────────────────────────────

    private static final String KEY_ACTIVE_PROFILE = "identity_active_profile";
    private static final String KEY_CUSTOM_NAME    = "identity_custom_name";
    private static final String KEY_CUSTOM_BIO     = "identity_custom_bio";
    private static final String KEY_CUSTOM_AVATAR  = "identity_custom_avatar_path";

    public void setCustomDisplayName(String name) {
        NxygramSettings.setString(KEY_CUSTOM_NAME, name);
    }

    public String getCustomDisplayName(String originalName) {
        if (!NxygramSettings.getBool("identity_transform_enabled", false))
            return originalName;
        String custom = NxygramSettings.getString(KEY_CUSTOM_NAME, "");
        return custom.isEmpty() ? originalName : custom;
    }

    public void setCustomBio(String bio) {
        NxygramSettings.setString(KEY_CUSTOM_BIO, bio);
    }

    public String getCustomBio(String originalBio) {
        if (!NxygramSettings.getBool("identity_transform_enabled", false))
            return originalBio;
        String custom = NxygramSettings.getString(KEY_CUSTOM_BIO, "");
        return custom.isEmpty() ? originalBio : custom;
    }

    // ─── Contact rename (local) ────────────────────────────────────────────

    /**
     * Rename a contact locally — only shows on your screen.
     *
     * HOOK IN: UserObject.getUserName() / formatName() calls throughout Telegram.
     * Anywhere a user's display name is built:
     *
     *   String name = UserObject.getUserName(user);
     *   name = IdentityTransformer.getInstance(context).getLocalName(user.id, name);
     */
    public void setLocalName(long userId, String localName) {
        NxygramSettings.setString("local_name_" + userId, localName);
    }

    public String getLocalName(long userId, String originalName) {
        if (!NxygramSettings.getBool("local_rename_enabled", true))
            return originalName;
        String local = NxygramSettings.getString("local_name_" + userId, "");
        return local.isEmpty() ? originalName : local;
    }

    public void clearLocalName(long userId) {
        NxygramSettings.setString("local_name_" + userId, "");
    }

    // ─── Avatar processing ─────────────────────────────────────────────────

    /**
     * Apply a circular crop and optional color overlay to an avatar bitmap.
     * Returns a new bitmap ready to use as a profile picture display.
     *
     * Usage in Telegram's AvatarDrawable or ImageReceiver:
     *   Bitmap processed = IdentityTransformer.getInstance(context)
     *       .processAvatar(originalBitmap, userId, size);
     */
    public Bitmap processAvatar(Bitmap original, long userId, int size) {
        if (original == null) return null;
        try {
            Bitmap result = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(result);

            // Scale original to size
            Bitmap scaled = Bitmap.createScaledBitmap(original, size, size, true);

            // Circular clip
            Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
            canvas.drawARGB(0, 0, 0, 0);
            canvas.drawCircle(size / 2f, size / 2f, size / 2f, paint);
            paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_IN));
            canvas.drawBitmap(scaled, 0, 0, paint);

            // Check for color overlay
            int overlayColor = getAvatarOverlayColor(userId);
            if (overlayColor != 0) {
                Paint overlay = new Paint();
                overlay.setColor(overlayColor);
                overlay.setAlpha(80); // semi-transparent overlay
                canvas.drawCircle(size / 2f, size / 2f, size / 2f, overlay);
            }

            // Check for frame
            int frameColor = getAvatarFrameColor(userId);
            if (frameColor != 0) {
                Paint frame = new Paint(Paint.ANTI_ALIAS_FLAG);
                frame.setColor(frameColor);
                frame.setStyle(Paint.Style.STROKE);
                frame.setStrokeWidth(size * 0.04f);
                canvas.drawCircle(size / 2f, size / 2f, size / 2f - size * 0.02f, frame);
            }

            return result;
        } catch (Exception e) {
            return original;
        }
    }

    public void setAvatarOverlayColor(long userId, int color) {
        NxygramSettings.setInt("avatar_overlay_" + userId, color);
    }

    public int getAvatarOverlayColor(long userId) {
        return NxygramSettings.getInt("avatar_overlay_" + userId, 0);
    }

    public void setAvatarFrameColor(long userId, int color) {
        NxygramSettings.setInt("avatar_frame_" + userId, color);
    }

    public int getAvatarFrameColor(long userId) {
        return NxygramSettings.getInt("avatar_frame_" + userId, 0);
    }

    // ─── Multiple account display labels ──────────────────────────────────

    /**
     * Custom label + color per Telegram account slot.
     * Shows in the account switcher UI.
     */
    public void setAccountLabel(int accountId, String label, int color) {
        NxygramSettings.setString("account_label_" + accountId, label);
        NxygramSettings.setInt("account_color_" + accountId, color);
    }

    public String getAccountLabel(int accountId, String defaultLabel) {
        String label = NxygramSettings.getString("account_label_" + accountId, "");
        return label.isEmpty() ? defaultLabel : label;
    }

    public int getAccountColor(int accountId) {
        return NxygramSettings.getInt("account_color_" + accountId, 0xFF2AABEE);
    }

    // ─── Hook guide ────────────────────────────────────────────────────────
    /*
     * DISPLAY NAME HOOK — anywhere names are built:
     *   UserObject.java → getUserName() method:
     *     public static String getUserName(TLRPC.User user) {
     *         String original = ...; // existing logic
     *         return IdentityTransformer.getInstance(ctx).getLocalName(user.id, original);
     *     }
     *
     * SELF NAME HOOK — where "You" or your own name is shown:
     *   String myName = IdentityTransformer.getInstance(ctx)
     *       .getCustomDisplayName(originalMyName);
     *
     * AVATAR HOOK — in ImageReceiver or AvatarImageView:
     *   After loading avatar bitmap:
     *     bitmap = IdentityTransformer.getInstance(ctx)
     *         .processAvatar(bitmap, userId, targetSize);
     *
     * ACCOUNT SWITCHER — in DrawerLayoutAdapter or account switch UI:
     *   String label = IdentityTransformer.getInstance(ctx)
     *       .getAccountLabel(accountId, originalName);
     *   int color = IdentityTransformer.getInstance(ctx)
     *       .getAccountColor(accountId);
     */
}
