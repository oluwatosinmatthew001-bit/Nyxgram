package com.nxygram;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;

/**
 * NxygramBubbleRenderer
 * ══════════════════════════════════════════════════════════════
 * Custom message bubble drawing engine.
 * Produces rounded-corner bubbles with the current Nxygram theme colors.
 *
 * Usage — in ChatMessageCell (Telegram's message view):
 *   1. Create once per cell: renderer = new NxygramBubbleRenderer(context);
 *   2. In onDraw(): renderer.draw(canvas, bounds, isOutgoing, isSelected);
 *   3. Or get a Drawable: view.setBackground(renderer.makeDrawable(isOutgoing));
 *
 * Also provides static helpers for one-shot use.
 */
public class NxygramBubbleRenderer {

    private final Context context;
    private final Paint   paint   = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint   shadow  = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Path    path    = new Path();
    private final RectF   rect    = new RectF();

    public NxygramBubbleRenderer(Context context) {
        this.context = context;
        shadow.setShadowLayer(dp(2), 0, dp(1), 0x33000000);
        shadow.setAntiAlias(true);
    }

    // ─── Draw on Canvas ───────────────────────────────────────────────────────

    /**
     * Draw a bubble onto a Canvas at the given bounds.
     * Call from ChatMessageCell.onDraw() after the super call.
     */
    public void draw(Canvas canvas, RectF bounds, boolean isOutgoing, boolean isSelected) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        int radius = NxygramSettings.getCornerRadius();
        float r = dp(radius);

        int bgColor = isSelected
                ? adjustAlpha(isOutgoing ? t.bubbleOut : t.bubbleIn, 0.7f)
                : (isOutgoing ? t.bubbleOut : t.bubbleIn);

        paint.setColor(bgColor);
        paint.setStyle(Paint.Style.FILL);

        // Build asymmetric bubble path:
        // The corner adjacent to the tail is slightly smaller
        float tailCorner = Math.max(r * 0.3f, dp(3));

        path.reset();
        if (isOutgoing) {
            // Outgoing: tail on bottom-right
            path.moveTo(bounds.left + r, bounds.top);
            path.lineTo(bounds.right - r, bounds.top);
            path.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + r);
            path.lineTo(bounds.right, bounds.bottom - tailCorner);
            path.quadTo(bounds.right, bounds.bottom,
                    bounds.right - tailCorner, bounds.bottom);
            path.lineTo(bounds.left + r, bounds.bottom);
            path.quadTo(bounds.left, bounds.bottom, bounds.left, bounds.bottom - r);
            path.lineTo(bounds.left, bounds.top + r);
            path.quadTo(bounds.left, bounds.top, bounds.left + r, bounds.top);
        } else {
            // Incoming: tail on bottom-left
            path.moveTo(bounds.left + tailCorner, bounds.top);
            path.lineTo(bounds.right - r, bounds.top);
            path.quadTo(bounds.right, bounds.top, bounds.right, bounds.top + r);
            path.lineTo(bounds.right, bounds.bottom - r);
            path.quadTo(bounds.right, bounds.bottom, bounds.right - r, bounds.bottom);
            path.lineTo(bounds.left + r, bounds.bottom);
            path.quadTo(bounds.left, bounds.bottom, bounds.left, bounds.bottom - tailCorner);
            path.lineTo(bounds.left, bounds.top + r);
            path.quadTo(bounds.left, bounds.top, bounds.left + tailCorner, bounds.top);
        }
        path.close();

        // Draw shadow first (subtle)
        if (NxygramSettings.getBool("bubble_shadow", true)) {
            shadow.setColor(bgColor);
            shadow.setStyle(Paint.Style.FILL);
            canvas.drawPath(path, shadow);
        }

        canvas.drawPath(path, paint);
    }

    // ─── GradientDrawable (simpler, for setBackground) ───────────────────────

    /**
     * Returns a GradientDrawable that can be set as a view's background.
     * Less flexible than canvas draw but simpler to hook in.
     *
     * Usage in ChatMessageCell.bind():
     *   view.setBackground(NxygramBubbleRenderer.makeDrawable(context, isOutgoing));
     */
    public static Drawable makeDrawable(Context context, boolean isOutgoing) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        int radius = NxygramSettings.getCornerRadius();

        GradientDrawable d = new GradientDrawable();
        d.setShape(GradientDrawable.RECTANGLE);
        d.setColor(isOutgoing ? t.bubbleOut : t.bubbleIn);

        float dp = context.getResources().getDisplayMetrics().density;
        float r  = radius * dp;

        if (isOutgoing) {
            // Outgoing: sharp bottom-right (tail side), round everywhere else
            d.setCornerRadii(new float[]{
                    r, r,          // top-left
                    r, r,          // top-right
                    r * 0.3f, r * 0.3f, // bottom-right (tail)
                    r, r           // bottom-left
            });
        } else {
            // Incoming: sharp bottom-left (tail side), round everywhere else
            d.setCornerRadii(new float[]{
                    r, r,          // top-left
                    r, r,          // top-right
                    r, r,          // bottom-right
                    r * 0.3f, r * 0.3f  // bottom-left (tail)
            });
        }

        return d;
    }

    /**
     * Returns a selected/highlighted version of the bubble drawable.
     */
    public static Drawable makeSelectedDrawable(Context context, boolean isOutgoing) {
        NxygramThemeManager.NxygramTheme t = NxygramThemeManager.getCurrentTheme();
        Drawable d = makeDrawable(context, isOutgoing);
        if (d instanceof GradientDrawable) {
            int base = isOutgoing ? t.bubbleOut : t.bubbleIn;
            ((GradientDrawable) d).setColor(adjustAlphaStatic(base, 0.75f));
        }
        return d;
    }

    // ─── Telegram hook guide ──────────────────────────────────────────────────
    /*
     * IN ChatMessageCell.java (Telegram source):
     *
     * Find: the section where message backgrounds are drawn.
     * Telegram typically draws bubbles with Theme.chat_msgInDrawable etc.
     *
     * To use Nxygram bubbles, hook AFTER the existing background draw:
     *
     *   // Add field to cell:
     *   private NxygramBubbleRenderer bubbleRenderer;
     *
     *   // In constructor or bind():
     *   if (bubbleRenderer == null)
     *       bubbleRenderer = new NxygramBubbleRenderer(getContext());
     *
     *   // In onDraw(), after super.onDraw(canvas):
     *   if (NxygramSettings.getBool("custom_bubbles", true)) {
     *       RectF bounds = new RectF(backgroundDrawable.getBounds());
     *       bubbleRenderer.draw(canvas, bounds, currentMessageObject.isOutgoing(), isSelected);
     *   }
     *
     * OR simpler approach using setBackground():
     *   In bind() or setMessageObject():
     *   if (NxygramSettings.getBool("custom_bubbles", true)) {
     *       setBackground(NxygramBubbleRenderer.makeDrawable(context, msg.isOutgoing()));
     *   }
     *
     * Add to NxygramSettings:
     *   KEY: "custom_bubbles" (boolean, default true)
     *
     * Add to NxygramSettingsActivity under Theme section:
     *   Item.toggle("Custom Bubble Style",
     *       "Use Nxygram-styled message bubbles",
     *       "custom_bubbles", true)
     *
     * Add to NxygramSettings:
     *   KEY: "bubble_shadow" (boolean, default true)
     *   Item.toggle("Bubble Shadow",
     *       "Subtle drop shadow on message bubbles",
     *       "bubble_shadow", true)
     */

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private int dp(int dp) {
        return (int)(dp * context.getResources().getDisplayMetrics().density);
    }

    private int adjustAlpha(int color, float factor) {
        return adjustAlphaStatic(color, factor);
    }

    private static int adjustAlphaStatic(int color, float factor) {
        int alpha = Math.round(android.graphics.Color.alpha(color) * factor);
        return (color & 0x00FFFFFF) | (alpha << 24);
    }
}
