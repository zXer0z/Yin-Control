package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.IntConsumer;

/** Vertical Pioneer-style channel fader with dB graduation ticks and machined cap. */
final class GainFader extends View {
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final YinUi u;
    private final IntConsumer changed;
    private final Runnable committed;
    private int gain;
    private boolean bandSelected;

    GainFader(Context c, int hz, int initial, IntConsumer changed, Runnable committed) {
        super(c);
        u = new YinUi(c);
        gain = initial;
        this.changed = changed;
        this.committed = committed;
        setContentDescription(hz + " Hz");
        setFocusable(true);
        setClickable(true);
    }

    void setBandSelected(boolean selected) {
        if (bandSelected == selected) return;
        bandSelected = selected;
        invalidate();
    }

    private void value(int v) {
        int next = Math.max(-12, Math.min(12, v));
        if (gain == next) return;
        gain = next;
        changed.accept(gain);
        invalidate();
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float x = w / 2f;
        float top = u.d(12), bottom = h - u.d(12);
        float mid = (top + bottom) / 2f;
        float y = top + (12 - gain) * (bottom - top) / 24f;
        boolean on = isEnabled();

        // Graduated mixer scale ticks on both sides of the fader channel
        for (int step = -12; step <= 12; step += 3) {
            float ty = top + (12 - step) * (bottom - top) / 24f;
            boolean zero = step == 0;
            boolean major = step % 6 == 0;
            float inner = u.d(4.5f);
            float outer = Math.min(w * .45f, zero ? u.d(12) : major ? u.d(9.5f) : u.d(7));
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Math.max(1, u.d(zero ? 1.3f : .85f)));
            paint.setColor(zero ? YinUi.alpha(YinUi.INK, .72f) : major ? YinUi.alpha(YinUi.SUB, .55f) : YinUi.alpha(YinUi.MUTED, .38f));
            canvas.drawLine(x - outer, ty, x - inner, ty, paint);
            canvas.drawLine(x + inner, ty, x + outer, ty, paint);
        }

        // Sunken slot
        float grooveHalf = u.d(2f);
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0xFF040609);
        canvas.drawRoundRect(x - grooveHalf, top, x + grooveHalf, bottom, grooveHalf, grooveHalf, paint);
        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, u.d(.9f)));
        paint.setColor(bandSelected ? YinUi.alpha(YinUi.ACCENT, .55f) : 0xFF242E3B);
        canvas.drawRoundRect(x - grooveHalf, top, x + grooveHalf, bottom, grooveHalf, grooveHalf, paint);

        if (gain != 0) {
            paint.setStyle(Paint.Style.FILL);
            paint.setColor(!on ? YinUi.MUTED : Math.abs(gain) >= 9 ? YinUi.LED_AMBER : YinUi.ACCENT);
            float barTop = Math.min(mid, y), barBot = Math.max(mid, y);
            canvas.drawRoundRect(x - grooveHalf * .8f, barTop, x + grooveHalf * .8f, barBot, grooveHalf, grooveHalf, paint);
        }

        // Machined horizontal mixer cap
        float capW = Math.min(u.d(11), w * .45f), capH = u.d(6);
        RectF shadow = new RectF(x - capW, y - capH + u.d(1.2f), x + capW, y + capH + u.d(1.2f));
        paint.setStyle(Paint.Style.FILL);
        paint.setColor(0x70000000);
        canvas.drawRoundRect(shadow, u.d(2.5f), u.d(2.5f), paint);

        RectF cap = new RectF(x - capW, y - capH, x + capW, y + capH);
        paint.setColor(on ? 0xFF1C232E : 0xFF141820);
        canvas.drawRoundRect(cap, u.d(2.5f), u.d(2.5f), paint);

        paint.setStyle(Paint.Style.STROKE);
        paint.setStrokeWidth(Math.max(1, u.d(1)));
        paint.setColor(on && bandSelected ? YinUi.ACCENT : 0xFF435166);
        canvas.drawRoundRect(cap, u.d(2.5f), u.d(2.5f), paint);

        paint.setStrokeWidth(u.d(1.9f));
        paint.setStrokeCap(Paint.Cap.ROUND);
        paint.setColor(on ? YinUi.INK : YinUi.MUTED);
        canvas.drawLine(x - capW * .65f, y, x + capW * .65f, y, paint);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(true);
            float top = u.d(12), range = Math.max(1, getHeight() - u.d(24));
            value(Math.round(12 - 24 * (event.getY() - top) / range));
            if (action == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                committed.run();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            getParent().requestDisallowInterceptTouchEvent(false);
            committed.run();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override public boolean performClick() {
        super.performClick();
        return true;
    }

    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setClassName("android.widget.SeekBar");
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, -12, 12, gain));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (!isEnabled()) return false;
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments != null) {
            value(Math.round(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));
            committed.run();
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD
                || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            value(gain + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? 1 : -1));
            committed.run();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (!isEnabled()) return false;
        if (code == KeyEvent.KEYCODE_DPAD_UP || code == KeyEvent.KEYCODE_DPAD_DOWN) {
            value(gain + (code == KeyEvent.KEYCODE_DPAD_UP ? 1 : -1));
            committed.run();
            return true;
        }
        return super.onKeyDown(code, event);
    }
}
