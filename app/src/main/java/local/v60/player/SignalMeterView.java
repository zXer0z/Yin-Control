package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.View;
import java.util.Locale;

/** Dynamic segmented Green -> Amber -> Red mixer LED meter bridge (~12.5 fps). */
final class SignalMeterView extends View {
    private static final int GREEN_SEG = 0xFF22C55E;
    private static final int AMBER_SEG = 0xFFFF9800;
    private static final int RED_SEG = 0xFFEF4444;
    private static final int OFF_SEG = 0xFF12171F;

    private final YinUi u;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final String key;
    private final boolean compact;
    private final Runnable tick;
    private float before, after, peakBefore, peakAfter;
    private double beforeDb = -90, afterDb = -90;
    private boolean signalActive, ceilingHit;
    private String status = "SIN SEÑAL EN ESTE PERFIL";
    private int statusColor = YinUi.LED_OFF;

    private final Runnable poll = new Runnable() {
        @Override public void run() {
            Bundle b = YinService.meters();
            boolean same = key.isEmpty() || key.equals(b.getString("profileKey"));
            boolean active = same && b.getBoolean("active") && b.getBoolean("signal");
            signalActive = active;
            ceilingHit = active && b.getBoolean("ceiling");
            beforeDb = active ? b.getDouble("before", -90) : -90;
            afterDb = active ? b.getDouble("after", -90) : -90;

            float a = active ? clamp01((float) ((beforeDb + 60) / 60)) : 0f;
            float z = active ? clamp01((float) ((afterDb + 60) / 60)) : 0f;
            before += (a - before) * (a > before ? .72f : .34f);
            after += (z - after) * (z > after ? .72f : .34f);
            peakBefore = a >= peakBefore ? a : Math.max(0f, peakBefore - .018f);
            peakAfter = z >= peakAfter ? z : Math.max(0f, peakAfter - .018f);

            if (!same && b.getBoolean("active")) {
                status = "SUENA OTRA CONEXIÓN";
                statusColor = YinUi.LED_AMBER;
            } else if (!active) {
                status = "SIN SEÑAL EN ESTE PERFIL";
                statusColor = YinUi.LED_OFF;
            } else if (ceilingHit) {
                status = "CLIP / TECHO DIGITAL ALCANZADO";
                statusColor = YinUi.LED_RED;
            } else if (b.getDouble("peak") > -3) {
                status = "SEÑAL CERCA DEL LÍMITE";
                statusColor = YinUi.LED_AMBER;
            } else {
                status = "MARGEN DIGITAL ÓPTIMO";
                statusColor = GREEN_SEG;
            }
            invalidate();
            if (tick != null) tick.run();
            handler.postDelayed(this, 80);
        }
    };

    SignalMeterView(Context c, String key, Runnable tick) {
        this(c, key, false, tick);
    }

    SignalMeterView(Context c, String key, boolean compact, Runnable tick) {
        super(c);
        u = new YinUi(c);
        this.key = key == null ? "" : key;
        this.compact = compact;
        this.tick = tick;
        setContentDescription("Medidor LED dinámico de entrada y salida en dBFS.");
    }

    private static float clamp01(float v) { return Math.max(0f, Math.min(1f, v)); }

    @Override protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        handler.post(poll);
    }

    @Override protected void onDetachedFromWindow() {
        handler.removeCallbacks(poll);
        super.onDetachedFromWindow();
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float r = u.d(compact ? 6 : 8);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF040609);
        c.drawRoundRect(0, 0, w, h, r, r, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(1)));
        p.setColor(0xFF222B38);
        c.drawRoundRect(.5f, .5f, w - .5f, h - .5f, r, r, p);

        if (compact) {
            drawCompact(c, w);
        } else {
            drawFull(c, w);
        }
    }

    private void drawCompact(Canvas c, float w) {
        float padX = u.d(9), labelW = u.d(28), readoutW = u.d(42);
        float barX1 = padX + labelW, barX2 = w - padX - readoutW;
        drawRow(c, u.d(7), 26, u.d(8), "IN", before, peakBefore, beforeDb, false, barX1, barX2, padX, w - padX, 8.5f);
        drawRow(c, u.d(25), 26, u.d(8), "OUT", after, peakAfter, afterDb, ceilingHit, barX1, barX2, padX, w - padX, 8.5f);
    }

    private void drawFull(Canvas c, float w) {
        float padX = u.d(12), labelW = u.d(36), readoutW = u.d(54);
        float barX1 = padX + labelW, barX2 = w - padX - readoutW;
        drawRow(c, u.d(16), 28, u.d(12), "PRE", before, peakBefore, beforeDb, false, barX1, barX2, padX, w - padX, 10f);
        drawRow(c, u.d(48), 28, u.d(12), "POST", after, peakAfter, afterDb, ceilingHit, barX1, barX2, padX, w - padX, 10f);

        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(u.d(8.5f));
        p.setColor(YinUi.MUTED);
        float tickY = u.d(80);
        float span = Math.max(1f, barX2 - barX1);
        float[] pos = {0f, .40f, .70f, .90f, 1f};
        String[] lbl = {"-60", "-36", "-18", "-6", "0 dB"};
        for (int i = 0; i < pos.length; i++) {
            float tx = barX1 + span * pos[i] - (i == pos.length - 1 ? u.d(22) : i == 0 ? 0 : u.d(9));
            c.drawText(lbl[i], tx, tickY, p);
        }

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(1)));
        p.setColor(0xFF222B38);
        c.drawLine(padX, u.d(93), w - padX, u.d(93), p);

        float dotX = padX + u.d(5), dotY = u.d(112);
        p.setStyle(Paint.Style.FILL);
        if (statusColor != YinUi.LED_OFF) {
            p.setColor(YinUi.alpha(statusColor, .30f));
            c.drawCircle(dotX, dotY, u.d(5), p);
        }
        p.setColor(statusColor);
        c.drawCircle(dotX, dotY, u.d(3), p);
        p.setTextSize(u.d(9.5f));
        p.setColor(statusColor == YinUi.LED_OFF ? YinUi.MUTED : YinUi.INK);
        c.drawText(status, padX + u.d(16), dotY + u.d(3.5f), p);
        p.setTypeface(Typeface.DEFAULT);
    }

    private void drawRow(Canvas c, float top, int segments, float barH, String tag, float level, float peak, double db, boolean clip, float x1, float x2, float leftEdge, float rightEdge, float textSp) {
        p.setStyle(Paint.Style.FILL);
        p.setTypeface(Typeface.MONOSPACE);
        p.setTextSize(u.d(textSp));
        p.setColor(YinUi.SUB);
        float textY = top + barH * .88f;
        c.drawText(tag, leftEdge, textY, p);

        float gap = u.d(compact ? 1.5f : 2f);
        float totalW = Math.max(24f, x2 - x1);
        float segW = (totalW - gap * (segments - 1)) / segments;
        int lit = Math.round(level * segments);
        int peakIdx = peak > .02f ? Math.min(segments - 1, Math.max(0, Math.round(peak * segments) - 1)) : -1;
        int amberStart = (int) Math.round(segments * .68);
        int redStart = (int) Math.round(segments * .88);

        for (int i = 0; i < segments; i++) {
            float sx = x1 + i * (segW + gap);
            boolean on = i < lit || i == peakIdx;
            int segColor = i >= redStart ? RED_SEG : i >= amberStart ? AMBER_SEG : GREEN_SEG;
            p.setColor(on ? segColor : OFF_SEG);
            c.drawRoundRect(sx, top + u.d(1), sx + segW, top + u.d(1) + barH, u.d(1.2f), u.d(1.2f), p);
        }

        String dbText;
        int dbColor;
        if (!signalActive || db <= -80) {
            dbText = compact ? "IDLE" : "-∞ dB";
            dbColor = YinUi.MUTED;
        } else if (clip) {
            dbText = "CLIP";
            dbColor = RED_SEG;
        } else {
            dbText = String.format(Locale.ROOT, "%+.1f", db);
            dbColor = db > -3 ? AMBER_SEG : YinUi.INK;
        }
        p.setTextSize(u.d(textSp));
        p.setColor(dbColor);
        float tw = p.measureText(dbText);
        c.drawText(dbText, rightEdge - tw, textY, p);
        p.setTypeface(Typeface.DEFAULT);
    }
}
