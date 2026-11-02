package local.v60.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.widget.TextView;

/** Shared tokens and dynamic Dark/Light theme engine; text follows the system/theme font. */
final class SoundStyle {
    static final int THEME_DARK = 0, THEME_LIGHT = 1, THEME_AUTO = 2;

    static int BG = 0xff0a0a0d,
        SURFACE = 0xff16161a,
        INK = 0xffffffff,
        MUTED = 0xffa6a6b0,
        LINE = 0x1affffff,
        ACCENT = 0xff0a84ff,
        SOFT = 0x2e0a84ff,
        DARK = 0xff16161a;

    private static boolean dark = true;

    static boolean isDark() {
        return dark;
    }

    static int themeMode(Context c) {
        return c.getSharedPreferences("appearance", 0).getInt("theme_mode", THEME_DARK);
    }

    static void setThemeMode(Context c, int mode) {
        c.getSharedPreferences("appearance", 0).edit().putInt("theme_mode", mode).apply();
        applyTheme(c);
    }

    static String themeName(int mode) {
        if (mode == THEME_LIGHT) return "Claro";
        if (mode == THEME_AUTO) return "Automático (según el sistema)";
        return "Oscuro";
    }

    static void applyTheme(Context c) {
        int mode = themeMode(c);
        boolean useDark;
        if (mode == THEME_AUTO) {
            int night = c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK;
            useDark = (night == Configuration.UI_MODE_NIGHT_YES);
        } else {
            useDark = (mode == THEME_DARK);
        }
        dark = useDark;
        if (useDark) {
            BG = 0xff0a0a0d;
            SURFACE = 0xff16161a;
            INK = 0xffffffff;
            MUTED = 0xffa6a6b0;
            LINE = 0x1affffff;
            ACCENT = 0xff0a84ff;
            SOFT = 0x2e0a84ff;
            DARK = 0xff16161a;
        } else {
            BG = 0xffffffff;
            SURFACE = 0xfff5f5f7;
            INK = 0xff000000;
            MUTED = 0xff6c6c74;
            LINE = 0x14000000;
            ACCENT = 0xff0071e3;
            SOFT = 0x140071e3;
            DARK = 0xfff5f5f7;
        }
    }

    static Typeface font(Context c, int weight) {
        Typeface system = new TextView(c).getTypeface();
        return Typeface.create(system != null ? system : Typeface.DEFAULT, weight, false);
    }

    static TextView text(Context c, String value, float size, int color, int weight) {
        TextView v = new TextView(c);
        v.setText(value);
        v.setTextSize(size);
        v.setTextColor(color);
        v.setTypeface(font(c, weight));
        v.setIncludeFontPadding(true);
        v.setLineSpacing(MusicStyle.dp(c, 2), 1.05f);
        return v;
    }

    static GradientDrawable surface(Context c, int color, int radius) {
        return MusicStyle.shape(c, color, radius);
    }

    static RippleDrawable ripple(Context c, int color, int radius) {
        int rippleColor = dark ? 0x22ffffff : 0x14000000;
        return new RippleDrawable(ColorStateList.valueOf(rippleColor), surface(c, color, radius), surface(c, 0xffffffff, radius));
    }
}
