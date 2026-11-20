package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.IntConsumer;

/** Linear hardware mixer fader with graduated tick marks and machined rectangular cap. */
final class MixerFaderView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final YinUi u;
    private final int min, max;
    private final boolean bipolar;
    private final IntConsumer onChange;
    private final Runnable onCommit;
    private int value;
    private boolean dragging;

    MixerFaderView(Context c, String name, int initial, int min, int max, boolean bipolar, IntConsumer onChange, Runnable onCommit) {
        super(c);
        this.u = new YinUi(c);
        this.min = min;
        this.max = Math.max(min + 1, max);
        this.bipolar = bipolar;
        this.value = clamp(initial);
        this.onChange = onChange;
        this.onCommit = onCommit;
        setContentDescription(name);
        setFocusable(true);
        setClickable(true);
    }

    private int clamp(int v) { return Math.max(min, Math.min(max, v)); }

    private void updateValue(int next) {
        int clamped = clamp(next);
        if (value == clamped) return;
        value = clamped;
        onChange.accept(value);
        invalidate();
    }

    @Override protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = resolveSize(u.d(42), hSpec);
        setMeasuredDimension(w, h);
    }

    @Override protected void onDraw(Canvas c) {
        super.onDraw(c);
        float w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;

        float padX = u.d(16);
        float left = padX, right = Math.max(left + 1, w - padX);
        float cy = h / 2f;
        boolean on = isEnabled();

        // Graduated mixer scale ticks above and below the fader slot
        for (int i = 0; i <= 20; i++) {
            float tx = left + (right - left) * (i / 20f);
            boolean end = i == 0 || i == 20;
            boolean mid = i == 10;
            boolean quarter = i % 5 == 0;
            float inner = u.d(5);
            float outer = mid && bipolar ? u.d(14.5f) : (end || quarter) ? u.d(11.5f) : u.d(8.5f);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(Math.max(1, u.d(mid && bipolar ? 1.4f : .9f)));
            p.setColor(mid && bipolar ? YinUi.alpha(YinUi.INK, .75f) : (end || quarter) ? YinUi.alpha(YinUi.SUB, .55f) : YinUi.alpha(YinUi.MUTED, .38f));
            c.drawLine(tx, cy - outer, tx, cy - inner, p);
            c.drawLine(tx, cy + inner, tx, cy + outer, p);
        }

        // Sunken fader slot
        float grooveHalf = u.d(2.2f);
        p.setStyle(Paint.Style.FILL);
        p.setColor(0xFF040609);
        c.drawRoundRect(left, cy - grooveHalf, right, cy + grooveHalf, grooveHalf, grooveHalf, p);
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(.9f)));
        p.setColor(YinUi.BORDER);
        c.drawRoundRect(left, cy - grooveHalf, right, cy + grooveHalf, grooveHalf, grooveHalf, p);

        // Active LED trace inside slot
        float norm = (value - min) / (float) (max - min);
        float knobX = left + (right - left) * norm;
        float anchorX = bipolar ? left + (right - left) * ((0 - min) / (float) (max - min)) : left;
        if (Math.abs(knobX - anchorX) > 1f) {
            p.setStyle(Paint.Style.FILL);
            p.setColor(on ? YinUi.ACCENT : YinUi.MUTED);
            c.drawRoundRect(Math.min(anchorX, knobX), cy - grooveHalf * .8f, Math.max(anchorX, knobX), cy + grooveHalf * .8f, grooveHalf, grooveHalf, p);
        }

        // Machined rectangular mixer fader cap
        float capHalfW = u.d(13), capHalfH = u.d(10);
        RectF shadow = new RectF(knobX - capHalfW, cy - capHalfH + u.d(1.5f), knobX + capHalfW, cy + capHalfH + u.d(1.5f));
        p.setStyle(Paint.Style.FILL);
        p.setColor(0x70000000);
        c.drawRoundRect(shadow, u.d(3), u.d(3), p);

        RectF cap = new RectF(knobX - capHalfW, cy - capHalfH, knobX + capHalfW, cy + capHalfH);
        p.setColor(on ? 0xFF1A202B : 0xFF141820);
        c.drawRoundRect(cap, u.d(3), u.d(3), p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeWidth(Math.max(1, u.d(1)));
        p.setColor(on && dragging ? YinUi.ACCENT : 0xFF3E4A5C);
        c.drawRoundRect(cap, u.d(3), u.d(3), p);

        // Grip lines & bright center indicator line
        p.setColor(0xFF0A0D12);
        p.setStrokeWidth(Math.max(1, u.d(1)));
        c.drawLine(knobX - u.d(6), cy - capHalfH * .55f, knobX - u.d(6), cy + capHalfH * .55f, p);
        c.drawLine(knobX + u.d(6), cy - capHalfH * .55f, knobX + u.d(6), cy + capHalfH * .55f, p);

        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(u.d(2));
        p.setColor(on ? (dragging ? YinUi.ACCENT : YinUi.INK) : YinUi.MUTED);
        c.drawLine(knobX, cy - capHalfH * .72f, knobX, cy + capHalfH * .72f, p);
    }

    @Override public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        float padX = u.d(16);
        float span = Math.max(1f, getWidth() - 2 * padX);
        if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE || action == MotionEvent.ACTION_UP) {
            getParent().requestDisallowInterceptTouchEvent(true);
            dragging = action != MotionEvent.ACTION_UP;
            float ratio = Math.max(0f, Math.min(1f, (event.getX() - padX) / span));
            updateValue(Math.round(min + ratio * (max - min)));
            invalidate();
            if (action == MotionEvent.ACTION_UP) {
                getParent().requestDisallowInterceptTouchEvent(false);
                performClick();
                if (onCommit != null) onCommit.run();
            }
            return true;
        }
        if (action == MotionEvent.ACTION_CANCEL) {
            dragging = false;
            invalidate();
            getParent().requestDisallowInterceptTouchEvent(false);
            if (onCommit != null) onCommit.run();
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
        info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT, min, max, value));
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);
        info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);
    }

    @Override public boolean performAccessibilityAction(int action, Bundle arguments) {
        if (!isEnabled()) return false;
        int step = Math.max(1, (max - min) / 20);
        if (action == AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId() && arguments != null) {
            updateValue(Math.round(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));
            if (onCommit != null) onCommit.run();
            return true;
        }
        if (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD || action == AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD) {
            updateValue(value + (action == AccessibilityNodeInfo.ACTION_SCROLL_FORWARD ? step : -step));
            if (onCommit != null) onCommit.run();
            return true;
        }
        return super.performAccessibilityAction(action, arguments);
    }

    @Override public boolean onKeyDown(int code, KeyEvent event) {
        if (!isEnabled()) return false;
        int step = Math.max(1, (max - min) / 20);
        if (code == KeyEvent.KEYCODE_DPAD_LEFT || code == KeyEvent.KEYCODE_DPAD_RIGHT) {
            updateValue(value + (code == KeyEvent.KEYCODE_DPAD_RIGHT ? step : -step));
            if (onCommit != null) onCommit.run();
            return true;
        }
        return super.onKeyDown(code, event);
    }
}
