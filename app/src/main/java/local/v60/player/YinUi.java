package local.v60.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.function.*;

/** Hardware DJ-mixer design system with OLED readouts, backlit LED keys and linear faders. */
final class YinUi {
    static final int INK = 0xFFF0F4F8, SUB = 0xFF94A3B8, MUTED = 0xFF64748B;
    static final int LED_GREEN = 0xFF34D399, LED_AMBER = 0xFFF5B759, LED_RED = 0xFFF87171, LED_OFF = 0xFF3A4654;
    static final int OLED = 0xFF030508, OLED_BORDER = 0xFF243040, CUE_ORANGE = 0xFFFF8C1A, FX_BLUE = 0xFF18A0FB;
    static final int[] ACCENTS = {0xFF6CE5C6, 0xFF67D4F5, 0xFFF2B857, 0xFFB7A1EF, 0xFFF4978E, 0xFFCBD5E1};

    static int ACCENT = ACCENTS[0];
    static int BG = 0xFF080A0D, PANEL = 0xFF10141A, WELL = 0xFF0A0D12, KEY = 0xFF141922, BORDER = 0xFF222A36, SELECTED = 0xFF141D24;
    static int CARD = PANEL, LINE = BORDER, SOFT = KEY;

    static void load(Context c) {
        int i = c.getSharedPreferences("yin_control", 0).getInt("accent", 0);
        ACCENT = ACCENTS[Math.max(0, Math.min(ACCENTS.length - 1, i))];
        BG = mix(0xFF080A0D, ACCENT, .02f);
        PANEL = mix(0xFF10141A, ACCENT, .04f);
        WELL = mix(0xFF0A0D12, ACCENT, .02f);
        KEY = mix(0xFF141922, ACCENT, .04f);
        BORDER = mix(0xFF222A36, ACCENT, .14f);
        SELECTED = mix(0xFF141D24, ACCENT, .22f);
        CARD = PANEL; LINE = BORDER; SOFT = KEY;
    }

    static int mix(int base, int color, float amount) {
        return Color.rgb(
            Math.round(Color.red(base) * (1 - amount) + Color.red(color) * amount),
            Math.round(Color.green(base) * (1 - amount) + Color.green(color) * amount),
            Math.round(Color.blue(base) * (1 - amount) + Color.blue(color) * amount)
        );
    }

    static int alpha(int color, float factor) {
        return Color.argb(Math.max(0, Math.min(255, Math.round(255 * factor))), Color.red(color), Color.green(color), Color.blue(color));
    }

    final Context c;
    YinUi(Context c) { this.c = c; }

    int d(float n) { return Math.round(n * c.getResources().getDisplayMetrics().density); }
    LinearLayout column() { LinearLayout v = new LinearLayout(c); v.setOrientation(LinearLayout.VERTICAL); return v; }
    LinearLayout row() { LinearLayout v = new LinearLayout(c); v.setGravity(Gravity.CENTER_VERTICAL); return v; }

    TextView text(String value, float size, int color) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setFontFeatureSettings("tnum");
        return v;
    }

    TextView title(String value, float size) {
        TextView v = text(value, size, INK);
        v.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        return v;
    }

    TextView mono(String value, float size, int color, boolean bold) {
        TextView v = text(value, size, color);
        v.setTypeface(Typeface.MONOSPACE, bold ? Typeface.BOLD : Typeface.NORMAL);
        return v;
    }

    TextView eyebrow(String value) {
        TextView v = mono(value, 9.5f, ACCENT, true);
        v.setLetterSpacing(.16f);
        return v;
    }

    GradientDrawable shape(int fill, int stroke, int radiusDp) {
        GradientDrawable v = new GradientDrawable();
        v.setColor(fill);
        v.setCornerRadius(d(radiusDp));
        if (stroke != 0) v.setStroke(Math.max(1, d(1)), stroke);
        return v;
    }

    GradientDrawable shape(int fill, int radiusDp) { return shape(fill, BORDER, radiusDp); }

    Drawable ripple(int fill, int stroke, int radiusDp) {
        return new RippleDrawable(ColorStateList.valueOf(alpha(ACCENT, .18f)), shape(fill, stroke, radiusDp), shape(0xFFFFFFFF, 0, radiusDp));
    }

    Drawable mixerKeyBg(boolean active, int activeColor) {
        int fill = active ? mix(PANEL, activeColor, .20f) : 0xFF141922;
        int stroke = active ? activeColor : BORDER;
        GradientDrawable base = shape(fill, stroke, 6);

        GradientDrawable ledBar = new GradientDrawable();
        ledBar.setColor(active ? activeColor : 0xFF262F3D);
        ledBar.setCornerRadius(d(1.5f));

        LayerDrawable layered = new LayerDrawable(new Drawable[]{base, ledBar});
        layered.setLayerGravity(1, Gravity.TOP | Gravity.FILL_HORIZONTAL);
        layered.setLayerHeight(1, Math.max(2, d(2.5f)));
        layered.setLayerInset(1, d(10), d(3), d(10), 0);

        return new RippleDrawable(ColorStateList.valueOf(alpha(activeColor, .24f)), layered, shape(0xFFFFFFFF, 0, 6));
    }

    void tint(Switch v) {
        int[][] states = {new int[]{android.R.attr.state_checked}, new int[]{}};
        v.setThumbTintList(new ColorStateList(states, new int[]{ACCENT, SUB}));
        v.setTrackTintList(new ColorStateList(states, new int[]{SELECTED, KEY}));
        v.setButtonTintList(ColorStateList.valueOf(ACCENT));
        v.setShowText(false);
    }

    LinearLayout module(LinearLayout parent, String code, String heading) {
        LinearLayout v = column();
        v.setPadding(d(13), d(12), d(13), d(12));
        v.setBackground(shape(PANEL, BORDER, 10));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = d(10);
        parent.addView(v, lp);
        if (!code.isEmpty()) {
            v.addView(eyebrow(code));
            gap(v, heading.isEmpty() ? 6 : 2);
        }
        if (!heading.isEmpty()) {
            v.addView(title(heading, 14.5f));
            gap(v, 8);
        }
        return v;
    }

    LinearLayout wellBox() {
        LinearLayout v = column();
        v.setPadding(d(10), d(8), d(10), d(8));
        v.setBackground(shape(OLED, OLED_BORDER, 6));
        return v;
    }

    LinearLayout well(LinearLayout parent) {
        LinearLayout v = wellBox();
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = d(8);
        parent.addView(v, lp);
        return v;
    }

    static final class OledBox {
        final LinearLayout view;
        final TextView label, sub, value;
        OledBox(LinearLayout view, TextView label, TextView sub, TextView value) {
            this.view = view;
            this.label = label;
            this.sub = sub;
            this.value = value;
        }
    }

    OledBox oledBox(LinearLayout parent, String label, String value, String sub, int valueColor) {
        LinearLayout box = column();
        box.setPadding(d(10), d(7), d(10), d(7));
        box.setBackground(shape(OLED, OLED_BORDER, 6));

        LinearLayout top = row();
        TextView lbl = mono(label, 8.5f, MUTED, true);
        lbl.setLetterSpacing(.14f);
        lbl.setSingleLine(true);
        top.addView(lbl, new LinearLayout.LayoutParams(0, -2, 1));
        TextView sTv = mono(sub == null ? "" : sub, 8.5f, ACCENT, true);
        sTv.setSingleLine(true);
        top.addView(sTv);
        box.addView(top);

        gap(box, 2);
        TextView val = mono(value, 13, valueColor, true);
        val.setMaxLines(2);
        val.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(val);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = d(8);
        parent.addView(box, lp);
        return new OledBox(box, lbl, sTv, val);
    }

    LinearLayout oledScreen(LinearLayout parent, String label, String value, String sub, int valueColor) {
        return oledBox(parent, label, value, sub, valueColor).view;
    }

    static final class LedDot extends View {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private int color;
        private final int sizePx;

        LedDot(Context c, int color, int sizeDp) {
            super(c);
            this.color = color;
            this.sizePx = Math.round(sizeDp * c.getResources().getDisplayMetrics().density);
        }

        void setColor(int next) {
            if (color == next) return;
            color = next;
            invalidate();
        }

        @Override protected void onMeasure(int w, int h) { setMeasuredDimension(sizePx, sizePx); }

        @Override protected void onDraw(Canvas c) {
            float cx = getWidth() / 2f, cy = getHeight() / 2f, r = Math.min(cx, cy);
            if (color != LED_OFF) {
                p.setColor(alpha(color, .28f));
                c.drawCircle(cx, cy, r, p);
            }
            p.setColor(color);
            c.drawCircle(cx, cy, r * .62f, p);
        }
    }

    static final class LedChip {
        final LinearLayout view;
        final LedDot dot;
        final TextView label;
        private final YinUi ui;

        LedChip(YinUi ui, LinearLayout view, LedDot dot, TextView label) {
            this.ui = ui;
            this.view = view;
            this.dot = dot;
            this.label = label;
        }

        void update(String text, int ledColor, int textColor, boolean highlightBorder) {
            label.setText(text);
            label.setTextColor(textColor);
            dot.setColor(ledColor);
            view.setBackground(ui.shape(OLED, highlightBorder ? alpha(ledColor, .60f) : OLED_BORDER, 5));
        }
    }

    LedChip ledChip(String text, int ledColor, int textColor) {
        LinearLayout box = row();
        box.setPadding(d(7), d(3.5f), d(8), d(3.5f));
        box.setBackground(shape(OLED, OLED_BORDER, 5));
        LedDot dot = new LedDot(c, ledColor, 7);
        box.addView(dot, new LinearLayout.LayoutParams(d(7), d(7)));
        box.addView(new View(c), new LinearLayout.LayoutParams(d(5), 1));
        TextView tv = mono(text, 9.5f, textColor, true);
        tv.setSingleLine(true);
        tv.setEllipsize(TextUtils.TruncateAt.END);
        box.addView(tv);
        return new LedChip(this, box, dot, tv);
    }

    void styleKey(Button b, boolean active, int activeColor) {
        b.setTextColor(active ? INK : SUB);
        b.setBackground(mixerKeyBg(active, activeColor));
    }

    Button key(String value, boolean active, Runnable click) {
        return key(value, active, ACCENT, click);
    }

    Button key(String value, boolean active, int activeColor, Runnable click) {
        Button b = new Button(c);
        b.setAllCaps(false);
        b.setText(value);
        b.setTextSize(11);
        b.setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        b.setPadding(d(8), d(9), d(8), d(6));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(d(40));
        b.setMinimumHeight(d(40));
        b.setStateListAnimator(null);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        styleKey(b, active, activeColor);
        b.setOnClickListener(v -> click.run());
        return b;
    }

    Button button(String value, Runnable click) { return key(value, false, click); }

    Button actionKey(String value, String iconName, boolean primary, Runnable click) {
        Button b = key(value, primary, ACCENT, click);
        if (iconName != null && !iconName.isEmpty()) {
            Drawable icon = YinIcons.of(c, iconName, primary ? ACCENT : SUB, 14);
            b.setCompoundDrawablesRelative(icon, null, null, null);
            b.setCompoundDrawablePadding(d(6));
            b.setGravity(Gravity.CENTER);
        }
        return b;
    }

    Switch toggleRow(LinearLayout p, String name, String note, boolean checked, Consumer<Boolean> change) {
        LinearLayout box = row();
        box.setPadding(d(8), d(6), d(6), d(6));
        box.setBackground(shape(WELL, BORDER, 7));
        LedDot dot = new LedDot(c, checked ? LED_GREEN : LED_OFF, 7);
        box.addView(dot, new LinearLayout.LayoutParams(d(7), d(7)));
        box.addView(new View(c), new LinearLayout.LayoutParams(d(8), 1));

        LinearLayout left = column();
        left.addView(text(name, 12.5f, INK));
        if (note != null && !note.isEmpty()) {
            gap(left, 1);
            left.addView(text(note, 10, MUTED));
        }
        box.addView(left, new LinearLayout.LayoutParams(0, -2, 1));
        Switch sw = new Switch(c);
        tint(sw);
        sw.setChecked(checked);
        sw.setPadding(d(6), d(2), 0, d(2));
        sw.setOnCheckedChangeListener((b, on) -> {
            dot.setColor(on ? LED_GREEN : LED_OFF);
            change.accept(on);
        });
        box.addView(sw);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = d(6);
        p.addView(box, lp);
        return sw;
    }

    void sliderRow(LinearLayout p, String name, int initial, int min, int max, IntConsumer set, IntFunction<String> display, Runnable commit) {
        LinearLayout head = row();
        head.setPadding(0, d(5), 0, d(2));
        TextView label = text(name, 11.5f, INK);
        head.addView(label, new LinearLayout.LayoutParams(0, -2, 1));
        TextView badge = mono(display.apply(initial), 11, ACCENT, true);
        badge.setPadding(d(8), d(3), d(8), d(3));
        badge.setBackground(shape(OLED, OLED_BORDER, 5));
        head.addView(badge);
        p.addView(head);

        boolean bipolar = min < 0 && max > 0;
        MixerFaderView fader = new MixerFaderView(c, name, initial, min, max, bipolar, v -> {
            set.accept(v);
            badge.setText(display.apply(v));
        }, commit);
        p.addView(fader, new LinearLayout.LayoutParams(-1, d(42)));
    }

    void telemetryPair(LinearLayout p, String k1, String v1, String k2, String v2) {
        LinearLayout pair = row();
        pair.setPadding(0, d(3), 0, d(3));
        addCell(pair, k1, v1, 0, 3);
        addCell(pair, k2, v2, 3, 0);
        p.addView(pair);
    }

    private void addCell(LinearLayout row, String k, String v, int leftDp, int rightDp) {
        LinearLayout cell = wellBox();
        cell.setPadding(d(9), d(6), d(9), d(6));
        TextView tk = mono(k, 8.5f, MUTED, true);
        tk.setLetterSpacing(.10f);
        cell.addView(tk);
        gap(cell, 2);
        TextView tv = mono(v, 11.5f, INK, false);
        tv.setTextIsSelectable(true);
        cell.addView(tv);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(d(leftDp), 0, d(rightDp), 0);
        row.addView(cell, lp);
    }

    void detail(LinearLayout p, String key, String value) {
        LinearLayout r = row();
        r.setPadding(0, d(5), 0, d(5));
        TextView a = text(key, 11.5f, SUB);
        TextView b = mono(value, 11.5f, INK, false);
        b.setGravity(Gravity.END);
        b.setTextIsSelectable(true);
        r.addView(a, new LinearLayout.LayoutParams(0, -2, 1));
        r.addView(b, new LinearLayout.LayoutParams(0, -2, 1.3f));
        p.addView(r);
    }

    void divider(LinearLayout p) {
        View line = new View(c);
        line.setBackgroundColor(BORDER);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, Math.max(1, d(1)));
        lp.setMargins(0, d(6), 0, d(6));
        p.addView(line, lp);
    }

    void gap(LinearLayout p, int n) { p.addView(new View(c), new LinearLayout.LayoutParams(1, d(n))); }
}
