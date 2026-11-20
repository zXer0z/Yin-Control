package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.Locale;
import java.util.function.IntConsumer;

/** Hardware oscilloscope-style RBJ analytic response of the configured EQ. */
final class EqResponseView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final YinUi u;
    private final AudioSettings s;
    private final int selected;
    private final IntConsumer pick;
    private final double[][] response = new double[2][241];
    private double scale = 12;
    private final double rate, maxFrequency;
    private Shader fillShader;

    EqResponseView(Context c, AudioSettings settings, int selected, IntConsumer pick) {
        super(c);
        u = new YinUi(c);
        s = settings.copy();
        android.os.Bundle live = YinService.meters();
        rate = live.getString("profileKey", "").equals(s.storageKey)
            ? Math.max(8000, YinService.publicStatus().getInt("rate", 48000))
            : (s.rate > 0 ? s.rate : 48000);
        maxFrequency = Math.min(20000, rate * .49);
        this.selected = Math.max(0, Math.min(9, selected));
        this.pick = pick;
        setContentDescription("Curva de ecualización: graves a la izquierda, agudos a la derecha. Toca para seleccionar una banda.");
        setClickable(true);
        for (int ch = 0; ch < 2; ch++) {
            for (int k = 0; k < 241; k++) {
                double hz = 20 * Math.pow(maxFrequency / 20, k / 240.0);
                double db = 0;
                for (int band = 0; band < 10; band++) {
                    if (!s.dsp.parametric || s.dsp.channel[band] == 2 || s.dsp.channel[band] == ch) {
                        if (s.dsp.frequency(band) < rate / 2) {
                            db += response(
                                s.dsp.frequency(band),
                                s.dsp.gain(band, s.gains[band]),
                                s.dsp.parametric ? s.dsp.q[band] : 1.41421356,
                                s.dsp.parametric ? s.dsp.type[band] : 0,
                                hz
                            );
                        }
                    }
                }
                response[ch][k] = db;
                scale = Math.min(48, Math.max(scale, Math.ceil(Math.abs(db) / 6) * 6));
            }
        }
    }

    private double response(double hz, double gain, double q, int type, double at) {
        double w = 2 * Math.PI * hz / rate, cos = Math.cos(w), a = Math.pow(10, gain / 40), alpha = Math.sin(w) / (2 * q);
        double b0, b1, b2, a0, a1, a2, beta = 2 * Math.sqrt(a) * alpha;
        if (type == 1) {
            b0 = a * ((a + 1) - (a - 1) * cos + beta); b1 = 2 * a * ((a - 1) - (a + 1) * cos); b2 = a * ((a + 1) - (a - 1) * cos - beta);
            a0 = (a + 1) + (a - 1) * cos + beta; a1 = -2 * ((a - 1) + (a + 1) * cos); a2 = (a + 1) + (a - 1) * cos - beta;
        } else if (type == 2) {
            b0 = a * ((a + 1) + (a - 1) * cos + beta); b1 = -2 * a * ((a - 1) + (a + 1) * cos); b2 = a * ((a + 1) + (a - 1) * cos - beta);
            a0 = (a + 1) - (a - 1) * cos + beta; a1 = 2 * ((a - 1) - (a + 1) * cos); a2 = (a + 1) - (a - 1) * cos - beta;
        } else {
            b0 = 1 + alpha * a; b1 = -2 * cos; b2 = 1 - alpha * a;
            a0 = 1 + alpha / a; a1 = -2 * cos; a2 = 1 - alpha / a;
        }
        double x = 2 * Math.PI * at / rate, c1 = Math.cos(x), c2 = Math.cos(2 * x), si1 = Math.sin(x), si2 = Math.sin(2 * x);
        double nr = b0 + b1 * c1 + b2 * c2, ni = b1 * si1 + b2 * si2, dr = a0 + a1 * c1 + a2 * c2, di = a1 * si1 + a2 * si2;
        return 10 * Math.log10(Math.max(1e-20, (nr * nr + ni * ni) / Math.max(1e-20, dr * dr + di * di)));
    }

    private float x(double f) {
        return u.d(28) + (getWidth() - u.d(40)) * (float) (Math.log(Math.max(20, f) / 20) / Math.log(maxFrequency / 20));
    }

    private float y(double db) {
        return u.d(14) + (getHeight() - u.d(36)) * (float) (.5 - db / (2 * scale));
    }

    @Override protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        fillShader = new LinearGradient(0, y(scale), 0, y(-scale),
            new int[]{YinUi.alpha(YinUi.ACCENT, .28f), YinUi.alpha(YinUi.ACCENT, .03f), YinUi.alpha(YinUi.ACCENT, .22f)},
            new float[]{0f, .5f, 1f}, Shader.TileMode.CLAMP);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = u.d(8);
        p.setStyle(Paint.Style.FILL);
        p.setColor(YinUi.WELL);
        c.drawRoundRect(0, 0, w, h, r, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(1)));
        p.setColor(YinUi.BORDER);
        c.drawRoundRect(.5f, .5f, w - .5f, h - .5f, r, r, p);

        float left = x(20), right = x(maxFrequency), top = y(scale), bottom = y(-scale), zeroY = y(0);

        p.setStrokeWidth(Math.max(1, u.d(1)));
        for (int hz : new int[]{31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000}) {
            if (hz > maxFrequency) continue;
            boolean major = hz == 125 || hz == 1000 || hz == 8000;
            p.setColor(YinUi.alpha(YinUi.BORDER, major ? .85f : .45f));
            float gx = x(hz);
            c.drawLine(gx, top, gx, bottom, p);
        }
        for (double db : new double[]{scale, scale / 2, -scale / 2, -scale}) {
            p.setColor(YinUi.alpha(YinUi.BORDER, .55f));
            float gy = y(db);
            c.drawLine(left, gy, right, gy, p);
        }
        p.setColor(YinUi.alpha(YinUi.SUB, .42f));
        c.drawLine(left, zeroY, right, zeroY, p);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(u.d(8.5f));
        p.setColor(YinUi.MUTED);
        c.drawText("+" + (int) scale, u.d(5), top + u.d(3), p);
        c.drawText("0", u.d(13), zeroY + u.d(3), p);
        c.drawText("-" + (int) scale, u.d(5), bottom + u.d(2), p);

        int[] axisHz = {31, 125, 1000, 4000, 16000};
        String[] axisTxt = {"31", "125", "1k", "4k", "16k"};
        for (int i = 0; i < axisHz.length; i++) {
            if (axisHz[i] > maxFrequency) continue;
            c.drawText(axisTxt[i], x(axisHz[i]) - u.d(7), h - u.d(6), p);
        }

        c.save();
        c.clipRect(left, top - u.d(2), right, bottom + u.d(2));

        if (s.eq && fillShader != null) {
            Path area = new Path();
            area.moveTo(left, zeroY);
            for (int k = 0; k < 241; k++) {
                float px = x(20 * Math.pow(maxFrequency / 20, k / 240.0));
                float py = y(response[0][k]);
                area.lineTo(px, py);
            }
            area.lineTo(right, zeroY);
            area.close();
            p.setStyle(Paint.Style.FILL);
            p.setShader(fillShader);
            c.drawPath(area, p);
            p.setShader(null);
        }

        boolean stereoSplit = false;
        for (int k = 0; k < 241; k++) {
            if (Math.abs(response[0][k] - response[1][k]) > 0.05) { stereoSplit = true; break; }
        }

        for (int ch = 1; ch >= 0; ch--) {
            if (ch == 1 && !stereoSplit) continue;
            Path path = new Path();
            for (int k = 0; k < 241; k++) {
                float px = x(20 * Math.pow(maxFrequency / 20, k / 240.0));
                float py = y(response[ch][k]);
                if (k == 0) path.moveTo(px, py); else path.lineTo(px, py);
            }
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setStrokeWidth(u.d(ch == 0 ? 2f : 1.4f));
            p.setColor(!s.eq ? YinUi.MUTED : ch == 0 ? YinUi.ACCENT : 0xFFB7A1EF);
            if (ch == 1) p.setPathEffect(new DashPathEffect(new float[]{u.d(5), u.d(3)}, 0));
            c.drawPath(path, p);
            p.setPathEffect(null);
        }

        for (int band = 0; band < 10; band++) {
            double f = s.dsp.frequency(band);
            if (f > maxFrequency) continue;
            int k = Math.max(0, Math.min(240, (int) Math.round(Math.log(f / 20) / Math.log(maxFrequency / 20) * 240)));
            float cx = x(f), cy = y(response[0][k]);
            if (band == selected) {
                p.setStyle(Paint.Style.FILL);
                p.setColor(YinUi.alpha(YinUi.ACCENT, .30f));
                c.drawCircle(cx, cy, u.d(7), p);
                p.setColor(YinUi.INK);
                c.drawCircle(cx, cy, u.d(3.4f), p);
            } else {
                p.setStyle(Paint.Style.FILL);
                p.setColor(s.eq ? YinUi.ACCENT : YinUi.MUTED);
                c.drawCircle(cx, cy, u.d(2.1f), p);
            }
        }
        c.restore();

        int fSel = s.dsp.frequency(selected);
        String freqStr = fSel >= 1000
            ? String.format(Locale.ROOT, "%.1fk", fSel / 1000.0).replace(".0k", "k")
            : fSel + "Hz";
        double gSel = s.dsp.gain(selected, s.gains[selected]);
        String badge = String.format(Locale.ROOT, "B%d · %s · %+.1fdB%s",
            selected + 1, freqStr, gSel, s.dsp.parametric ? String.format(Locale.ROOT, " · Q%.2f", s.dsp.q[selected]) : "");
        p.setTextSize(u.d(9));
        p.setTypeface(Typeface.MONOSPACE);
        float tw = p.measureText(badge);
        float bx2 = w - u.d(8), bx1 = bx2 - tw - u.d(12), by1 = u.d(7), by2 = by1 + u.d(17);
        p.setStyle(Paint.Style.FILL);
        p.setColor(YinUi.alpha(YinUi.BG, .85f));
        c.drawRoundRect(bx1, by1, bx2, by2, u.d(4), u.d(4), p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(.8f)));
        p.setColor(YinUi.BORDER);
        c.drawRoundRect(bx1, by1, bx2, by2, u.d(4), u.d(4), p);
        p.setStyle(Paint.Style.FILL);
        p.setColor(s.eq ? YinUi.ACCENT : YinUi.SUB);
        c.drawText(badge, bx1 + u.d(6), by2 - u.d(5), p);
        p.setTypeface(Typeface.DEFAULT);
    }

    @Override public boolean onTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_UP) {
            int band = 0;
            double distance = Double.MAX_VALUE;
            for (int i = 0; i < 10; i++) {
                double d = Math.abs(x(s.dsp.frequency(i)) - e.getX());
                if (d < distance) { distance = d; band = i; }
            }
            pick.accept(band);
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }
}
