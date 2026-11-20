package local.v60.player;

import android.widget.*;
import java.util.*;

/** Sound Color FX one-tap master presets, analog soft-clip warmth, and per-ear L/R trims. */
final class YinStudioControls {
    private static final class ColorPreset {
        final String name, desc;
        final int color;
        final boolean eq, bass, clarity, compressor, warmth, crossfeed, surround, wide;
        final int[] gains;
        final int bassMode, bassGain, bassHz;
        final int clarityMode, clarityAmount;
        final int threshold, ratio, attack, release;
        final int warmthType, warmthAmount;
        final int crossfeedHz, crossfeedAmount;
        final int delay, surroundAmount, surroundLpHz;
        final int stereoWidth;

        ColorPreset(String name, String desc, int color,
                    boolean eq, int[] gains,
                    boolean bass, int bassMode, int bassGain, int bassHz,
                    boolean clarity, int clarityMode, int clarityAmount,
                    boolean compressor, int threshold, int ratio, int attack, int release,
                    boolean warmth, int warmthType, int warmthAmount,
                    boolean crossfeed, int crossfeedHz, int crossfeedAmount,
                    boolean surround, int delay, int surroundAmount, int surroundLpHz,
                    boolean wide, int stereoWidth) {
            this.name = name; this.desc = desc; this.color = color;
            this.eq = eq; this.gains = gains;
            this.bass = bass; this.bassMode = bassMode; this.bassGain = bassGain; this.bassHz = bassHz;
            this.clarity = clarity; this.clarityMode = clarityMode; this.clarityAmount = clarityAmount;
            this.compressor = compressor; this.threshold = threshold; this.ratio = ratio; this.attack = attack; this.release = release;
            this.warmth = warmth; this.warmthType = warmthType; this.warmthAmount = warmthAmount;
            this.crossfeed = crossfeed; this.crossfeedHz = crossfeedHz; this.crossfeedAmount = crossfeedAmount;
            this.surround = surround; this.delay = delay; this.surroundAmount = surroundAmount; this.surroundLpHz = surroundLpHz;
            this.wide = wide; this.stereoWidth = stereoWidth;
        }

        void applyTo(AudioSettings s) {
            DspSettings d = s.dsp;
            s.eq = eq;
            d.parametric = false;
            System.arraycopy(gains, 0, s.gains, 0, 10);
            for (int i = 0; i < 10; i++) d.gain[i] = gains[i];
            d.gainStored = true;
            d.bass = bass;
            if (bass) { d.bassMode = bassMode; d.bassGain = bassGain; d.bassHz = bassHz; }
            d.clarity = clarity;
            if (clarity) { d.clarityMode = clarityMode; d.clarityAmount = clarityAmount; }
            d.compressor = compressor;
            if (compressor) { d.threshold = threshold; d.ratio = ratio; d.attack = attack; d.release = release; }
            d.analogWarmth = warmth;
            if (warmth) { d.warmthType = warmthType; d.warmthAmount = warmthAmount; }
            d.crossfeed = crossfeed;
            if (crossfeed) { d.crossfeedHz = crossfeedHz; d.crossfeedAmount = crossfeedAmount; }
            d.surround = surround;
            if (surround) { d.delay = delay; d.surroundAmount = surroundAmount; d.surroundLpHz = surroundLpHz; d.surroundReverse = false; }
            d.stereoWide = wide;
            if (wide) { d.stereoWidth = stereoWidth; }
        }

        boolean matches(AudioSettings s) {
            DspSettings d = s.dsp;
            if (s.eq != eq || d.parametric || !Arrays.equals(s.gains, gains)) return false;
            if (d.bass != bass || (bass && (d.bassMode != bassMode || d.bassGain != bassGain || d.bassHz != bassHz))) return false;
            if (d.clarity != clarity || (clarity && (d.clarityMode != clarityMode || d.clarityAmount != clarityAmount))) return false;
            if (d.compressor != compressor || (compressor && (d.threshold != threshold || d.ratio != ratio || d.attack != attack || d.release != release))) return false;
            if (d.analogWarmth != warmth || (warmth && (d.warmthType != warmthType || d.warmthAmount != warmthAmount))) return false;
            if (d.crossfeed != crossfeed || (crossfeed && (d.crossfeedHz != crossfeedHz || d.crossfeedAmount != crossfeedAmount))) return false;
            if (d.surround != surround || (surround && (d.delay != delay || d.surroundAmount != surroundAmount || d.surroundLpHz != surroundLpHz || d.surroundReverse))) return false;
            return d.stereoWide == wide && (!wide || d.stereoWidth == stereoWidth);
        }
    }

    private static final ColorPreset[] SOUND_COLORS = {
        new ColorPreset("PURE HI-FI", "Bypass limpio · señal pura sin coloración DSP", YinUi.LED_GREEN,
            false, new int[]{0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            false, 1, 5, 75, false, 0, 4, false, -18, 3, 10, 180, false, 0, 4, false, 700, 60, false, 10, 75, 7500, false, 135),
        new ColorPreset("BASS CLUB", "Sub-Woofer punch + graves profundos + calidez valvular", YinUi.CUE_ORANGE,
            true, new int[]{5, 4, 2, 0, -1, 0, 1, 2, 3, 2},
            true, 1, 7, 70, true, 0, 3, false, -18, 3, 10, 180, true, 0, 4, false, 700, 60, false, 10, 75, 7500, false, 135),
        new ColorPreset("VOCAL STUDIO", "Voces al frente + claridad híbrida + imagen frontal", YinUi.FX_BLUE,
            true, new int[]{-1, -1, 0, 1, 3, 4, 3, 2, 1, 1},
            false, 1, 5, 75, true, 2, 5, true, -18, 2, 15, 180, false, 0, 4, true, 700, 60, false, 10, 75, 7500, false, 135),
        new ColorPreset("3D STAGE", "Differential Surround Haas + escena estéreo abierta", 0xFF67D4F5,
            true, new int[]{3, 2, 1, 0, 0, 1, 2, 3, 3, 3},
            true, 0, 4, 75, true, 1, 4, false, -18, 3, 10, 180, false, 0, 4, false, 700, 60, true, 12, 80, 7500, true, 145),
        new ColorPreset("TUBE WARMTH", "Saturación de válvula Tanh + cuerpo analógico cálido", 0xFFF2B857,
            true, new int[]{3, 3, 2, 1, 0, 0, -1, 0, 1, 2},
            true, 0, 4, 85, false, 0, 4, false, -18, 3, 10, 180, true, 0, 7, true, 700, 45, false, 10, 75, 7500, false, 135),
        new ColorPreset("NIGHT LOUNGE", "Compresión nocturna + protección binaural fuerte", 0xFFB7A1EF,
            true, new int[]{2, 2, 1, 0, 0, 0, -1, -1, 0, 1},
            true, 2, 4, 75, false, 0, 4, true, -26, 6, 5, 260, true, 1, 3, true, 650, 95, false, 10, 75, 7500, false, 135)
    };

    private final MainActivity a;
    private final YinUi u;
    private final AudioSettings s;
    private final Runnable save, redraw;

    YinStudioControls(MainActivity a, AudioSettings s, Runnable save, Runnable redraw) {
        this.a = a;
        u = new YinUi(a);
        this.s = s;
        this.save = save;
        this.redraw = redraw;
    }

    private int detectSoundColor() {
        for (int i = 0; i < SOUND_COLORS.length; i++) {
            if (SOUND_COLORS[i].matches(s)) return i;
        }
        return -1;
    }

    void soundColorPanel(LinearLayout parent) {
        LinearLayout fx = u.module(parent, "01 // SOUND COLOR FX", "Perfiles maestros de 1 toque");
        int active = detectSoundColor();
        String title = active >= 0 ? SOUND_COLORS[active].name : "PERSONALIZADO";
        String sub = active >= 0 ? "PRESET ACTIVO" : "CUSTOM";
        int color = active >= 0 ? SOUND_COLORS[active].color : YinUi.ACCENT;
        String detail = active >= 0
            ? SOUND_COLORS[active].desc
            : s.dsp.summary(!"speaker".equals(s.profile), 2);

        YinUi.OledBox oled = u.oledBox(fx, "ESTADO SOUND COLOR FX", title, sub, color);
        u.gap(oled.view, 2);
        oled.view.addView(u.mono(detail, 10, YinUi.SUB, false));

        for (int start = 0; start < SOUND_COLORS.length; start += 3) {
            LinearLayout row = u.row();
            for (int col = 0; col < 3; col++) {
                final int idx = start + col;
                ColorPreset preset = SOUND_COLORS[idx];
                Button pad = u.key(preset.name, active == idx, preset.color, () -> {
                    preset.applyTo(s);
                    save.run();
                    redraw.run();
                });
                pad.setTextSize(9.5f);
                pad.setPadding(u.d(3), u.d(9), u.d(3), u.d(6));
                pad.setMinHeight(u.d(42));
                pad.setMinimumHeight(u.d(42));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(col == 0 ? 0 : 2.5f), u.d(2.5f), u.d(col == 2 ? 0 : 2.5f), u.d(2.5f));
                row.addView(pad, lp);
            }
            fx.addView(row);
        }
    }

    void warmthPanel(LinearLayout parent) {
        LinearLayout warmth = u.module(parent, "05 // CALIDEZ ANALÓGICA", "Saturación armónica Soft-Clip");
        u.toggleRow(warmth, "Activar calidez analógica", "Añade cuerpo armónico con oversampling 2×", s.dsp.analogWarmth, on -> {
            s.dsp.analogWarmth = on;
            save.run();
            redraw.run();
        });
        if (s.dsp.analogWarmth) {
            u.gap(warmth, 4);
            String[] types = {"Válvula (Tanh)", "Cinta (Atan)", "Consola (Cubic)"};
            LinearLayout row = u.row();
            for (int i = 0; i < types.length; i++) {
                final int idx = i;
                Button b = u.key(types[i], s.dsp.warmthType == i, () -> {
                    s.dsp.warmthType = idx;
                    save.run();
                    redraw.run();
                });
                b.setTextSize(10.5f);
                b.setPadding(u.d(3), u.d(9), u.d(3), u.d(6));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(i == 0 ? 0 : 2.5f), u.d(3), u.d(i == types.length - 1 ? 0 : 2.5f), u.d(3));
                row.addView(b, lp);
            }
            warmth.addView(row);
            u.sliderRow(warmth, "Cuerpo armónico", s.dsp.warmthAmount, 1, 10,
                v -> s.dsp.warmthAmount = v, v -> v + " / 10", () -> {
                    save.run();
                    redraw.run();
                });
        }
        warmth.addView(u.text("Cadena fija optimizada: Preamp → IR → EQ → Graves → Claridad → Compresor → Calidez → Espacial → L/R → Limitador.", 10, YinUi.MUTED));
    }

    void channelsPanel(LinearLayout parent) {
        LinearLayout channels = u.module(parent, "05 // CALIBRACIÓN L / R", "Cada oído por separado");
        u.toggleRow(channels, "Activar ganancia y retardo por canal", "Solo en audio estéreo", s.dsp.channelsEnabled, on -> {
            s.dsp.channelsEnabled = on;
            save.run();
            redraw.run();
        });
        if (s.dsp.channelsEnabled) {
            u.sliderRow(channels, "Volumen izquierdo", (int) Math.round(s.dsp.leftGain * 10), -240, 60,
                v -> s.dsp.leftGain = v / 10.0, v -> String.format(Locale.ROOT, "%+.1f dB", v / 10.0), save);
            u.sliderRow(channels, "Volumen derecho", (int) Math.round(s.dsp.rightGain * 10), -240, 60,
                v -> s.dsp.rightGain = v / 10.0, v -> String.format(Locale.ROOT, "%+.1f dB", v / 10.0), save);
            u.sliderRow(channels, "Retardo izquierdo", s.dsp.leftDelay, 0, 200,
                v -> s.dsp.leftDelay = v, v -> String.format(Locale.ROOT, "%.1f ms", v / 10.0), save);
            u.sliderRow(channels, "Retardo derecho", s.dsp.rightDelay, 0, 200,
                v -> s.dsp.rightDelay = v, v -> String.format(Locale.ROOT, "%.1f ms", v / 10.0), save);
            u.gap(channels, 6);
            channels.addView(u.actionKey("Igualar ambos oídos", "reset", false, () -> {
                s.dsp.leftGain = s.dsp.rightGain = 0;
                s.dsp.leftDelay = s.dsp.rightDelay = 0;
                save.run();
                redraw.run();
            }), new LinearLayout.LayoutParams(-1, -2));
        }
    }
}
