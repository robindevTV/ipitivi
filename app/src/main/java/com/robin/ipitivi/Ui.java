package com.robin.ipitivi;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RadialGradient;
import android.graphics.Rect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** The shared Apple TV look: black, light type, rounded cards that lift and brighten when focused. */
final class Ui {
    private Ui() {}

    static final int BG = 0xFF0B0D1A;
    static final int CARD = 0xFF1C1C1E;
    static final int CARD_HI = 0xFF2C2C2E;
    static final int TEXT = 0xFFFFFFFF;
    static final int MUTED = 0xB0FFFFFF;
    static final int ACCENT = 0xFF0A84FF;
    static final int GREEN = 0xFF6DF08C;
    static final int ORANGE = 0xFFFFB340;

    /** The colourful soft "wallpaper" that the glass cards sit on: blurry blobs of colour over a dark base. */
    static final class Wallpaper extends Drawable {
        private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final int[] colors = {0xFF2F6BFF, 0xFFA23BFF, 0xFFFF7A45, 0xFF12C6C0};
        private final float[][] spots = {{0.04f, 0.00f, 0.55f}, {0.96f, 0.14f, 0.50f}, {0.46f, 1.02f, 0.48f}, {0.80f, 0.96f, 0.34f}};

        @Override public void draw(Canvas canvas) {
            Rect b = getBounds();
            int w = b.width(), h = b.height();
            if (w == 0 || h == 0) return;
            canvas.drawColor(BG);
            for (int i = 0; i < colors.length; i++) {
                float r = spots[i][2] * Math.max(w, h * 1.4f);
                paint.setShader(new RadialGradient(b.left + spots[i][0] * w, b.top + spots[i][1] * h, r,
                        new int[] {(colors[i] & 0x00FFFFFF) | 0xE6000000, (colors[i] & 0x00FFFFFF) | 0x66000000,
                                colors[i] & 0x00FFFFFF},
                        new float[] {0f, 0.45f, 1f}, Shader.TileMode.CLAMP));
                canvas.drawRect(b, paint);
            }
            paint.setShader(null);
            paint.setColor(0x4D000000);
            canvas.drawRect(b, paint);
        }

        @Override public void setAlpha(int a) {}
        @Override public void setColorFilter(ColorFilter f) {}
        @Override public int getOpacity() { return PixelFormat.OPAQUE; }
    }

    static Drawable wallpaper() { return new Wallpaper(); }

    /** A pane of glass: translucent white gradient, a fine bright rim and a diagonal highlight. */
    static Drawable glassPane(Context c, int topArgb, int bottomArgb, float radiusDp, int rimArgb, float rimDp) {
        GradientDrawable base = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[] {topArgb, bottomArgb});
        base.setCornerRadius(dp(c, radiusDp));
        base.setStroke(Math.max(1, dp(c, rimDp)), rimArgb);
        GradientDrawable shine = new GradientDrawable(GradientDrawable.Orientation.TL_BR,
                new int[] {0x38FFFFFF, 0x00FFFFFF, 0x12FFFFFF});
        shine.setCornerRadius(dp(c, radiusDp));
        return new LayerDrawable(new Drawable[] {base, shine});
    }

    static Drawable glassRest(Context c, float radiusDp) {
        return glassPane(c, 0x38FFFFFF, 0x14FFFFFF, radiusDp, 0x47FFFFFF, 1);
    }

    static Drawable glassFocus(Context c, float radiusDp) {
        return glassPane(c, 0x80FFFFFF, 0x47FFFFFF, radiusDp, 0xCCFFFFFF, 2);
    }
    static final Typeface LIGHT = Typeface.create("sans-serif-light", Typeface.NORMAL);
    static final Typeface MEDIUM = Typeface.create("sans-serif-medium", Typeface.NORMAL);

    /** Text size multiplier chosen in Réglages (1, 1.25 or 1.5). */
    static float scale = 1f;

    static void init(Context c) { scale = new Prefs(c).textScale(); }

    static int dp(Context c, float v) { return Math.round(v * c.getResources().getDisplayMetrics().density); }

    /** A size that grows with the text size setting. */
    static int sdp(Context c, float v) { return dp(c, v * scale); }

    static GradientDrawable rect(Context c, int color, float radiusDp, int strokeColor, float strokeDp) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        if (strokeDp > 0) g.setStroke(dp(c, strokeDp), strokeColor);
        return g;
    }

    static GradientDrawable gradient(Context c, int top, int bottom, float radiusDp) {
        GradientDrawable g = new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM, new int[] {top, bottom});
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    /** Background for a big card: soft gradient at rest, frosted white with a fine rim when focused. */
    static StateListDrawable cardBg(Context c, int top, int bottom, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, glassFocus(c, radiusDp));
        s.addState(new int[] {}, glassRest(c, radiusDp));
        return s;
    }

    /** List-row background: glass at rest, brighter when selected, bright and rimmed when focused. */
    static StateListDrawable glass(Context c, int normalColor, float radiusDp) {
        StateListDrawable s = new StateListDrawable();
        s.addState(new int[] {android.R.attr.state_focused}, glassFocus(c, radiusDp));
        s.addState(new int[] {android.R.attr.state_selected},
                glassPane(c, 0x52FFFFFF, 0x2BFFFFFF, radiusDp, 0x66FFFFFF, 1));
        s.addState(new int[] {}, (normalColor >>> 24) == 0
                ? (Drawable) rect(c, 0x00000000, radiusDp, 0, 0) : glassRest(c, radiusDp));
        return s;
    }

    /** tvOS focus: the item grows a little and lifts off the screen. */
    static void focusAnim(View v, boolean focused, float grow) {
        float s = focused ? grow : 1f;
        v.animate().scaleX(s).scaleY(s).translationZ(focused ? dp(v.getContext(), 14) : 0f).setDuration(140).start();
    }

    static TextView text(Context c, String s, float sp, int color) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp * scale);
        t.setTextColor(color);
        return t;
    }

    /** Apple-style button: dark pill that turns white (with black text) when focused. */
    static TextView button(Context c, String label, boolean primary) {
        TextView b = new TextView(c);
        b.setText(label);
        b.setTextSize(21 * scale);
        b.setTypeface(MEDIUM);
        b.setGravity(Gravity.CENTER);
        b.setFocusable(true);
        b.setClickable(true);
        StateListDrawable bg = new StateListDrawable();
        bg.addState(new int[] {android.R.attr.state_focused}, rect(c, 0xF2FFFFFF, 40, 0, 0));
        bg.addState(new int[] {}, primary
                ? glassPane(c, 0xC00A84FF, 0x730A84FF, 40, 0x66FFFFFF, 1)
                : glassRest(c, 40));
        b.setBackground(bg);
        b.setTextColor(new ColorStateList(new int[][] {{android.R.attr.state_focused}, {}},
                new int[] {0xFF000000, 0xFFFFFFFF}));
        b.setPadding(sdp(c, 30), sdp(c, 14), sdp(c, 30), sdp(c, 14));
        b.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { focusAnim(v, f, 1.06f); }
        });
        return b;
    }

    /** A standard sub-screen: big light title, a Retour button, the content, a plain-words hint. */
    static LinearLayout page(Context c, String title, String hint, View content, View.OnClickListener back) {
        LinearLayout p = new LinearLayout(c);
        p.setOrientation(LinearLayout.VERTICAL);
        p.setBackground(wallpaper());
        p.setPadding(dp(c, 64), dp(c, 36), dp(c, 64), dp(c, 28));
        p.setClipChildren(false);
        p.setClipToPadding(false);

        LinearLayout head = new LinearLayout(c);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);
        head.setClipChildren(false);
        if (back != null) {
            TextView b = button(c, "‹  Retour", false);
            b.setOnClickListener(back);
            head.addView(b);
        }
        TextView t = text(c, title, 38, TEXT);
        t.setTypeface(LIGHT);
        LinearLayout.LayoutParams tp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        tp.leftMargin = back != null ? dp(c, 32) : 0;
        head.addView(t, tp);
        p.addView(head);

        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        cp.topMargin = dp(c, 22);
        p.addView(content, cp);

        if (hint != null) {
            TextView h = text(c, hint, 17, MUTED);
            LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            hp.topMargin = dp(c, 14);
            p.addView(h, hp);
        }
        return p;
    }

    /** A vertical stack for a short scrolling screen (lists, forms). Does not clip, so focused items can grow. */
    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        l.setPadding(dp(c, 14), dp(c, 14), dp(c, 14), dp(c, 14));
        l.setClipChildren(false);
        l.setClipToPadding(false);
        return l;
    }

    static ScrollView scroller(Context c, LinearLayout col) {
        ScrollView s = new ScrollView(c);
        s.setClipChildren(false);
        s.setClipToPadding(false);
        s.setVerticalScrollBarEnabled(false);
        s.addView(col, new ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT));
        return s;
    }

    /** A full-width focusable card row (add children yourself). */
    static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setBackground(cardBg(c, CARD_HI, CARD, 30));
        r.setPadding(sdp(c, 30), sdp(c, 20), sdp(c, 30), sdp(c, 20));
        r.setFocusable(true);
        r.setClickable(true);
        r.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override public void onFocusChange(View v, boolean f) { focusAnim(v, f, 1.03f); }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.bottomMargin = dp(c, 14);
        r.setLayoutParams(lp);
        return r;
    }

    static LinearLayout.LayoutParams fullWidth(Context c, int topMarginDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
        lp.topMargin = dp(c, topMarginDp);
        return lp;
    }
}
