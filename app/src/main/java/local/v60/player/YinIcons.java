package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.widget.ImageView;

/** Compact vector icon set for the Yin Control hardware console. */
final class YinIcons {
    static Drawable of(Context c, String name, int color, int sizeDp) {
        return new Icon(c, name, color, sizeDp);
    }

    static ImageView view(Context c, String name, int color, int sizeDp) {
        ImageView iv = new ImageView(c);
        iv.setImageDrawable(of(c, name, color, sizeDp));
        iv.setScaleType(ImageView.ScaleType.CENTER);
        return iv;
    }

    static void tint(ImageView iv, String name, int color, int sizeDp) {
        iv.setImageDrawable(of(iv.getContext(), name, color, sizeDp));
    }

    private static final class Icon extends Drawable {
        private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final String name;
        private final int color, size;

        Icon(Context c, String name, int color, int sizeDp) {
            this.name = name;
            this.color = color;
            this.size = Math.round(sizeDp * c.getResources().getDisplayMetrics().density);
            setBounds(0, 0, this.size, this.size);
        }

        @Override public int getIntrinsicWidth() { return size; }
        @Override public int getIntrinsicHeight() { return size; }

        @Override public void draw(Canvas c) {
            c.save();
            Rect b = getBounds();
            c.translate(b.left, b.top);
            c.scale(b.width() / 24f, b.height() / 24f);
            p.setColor(color);
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(1.75f);
            p.setStrokeCap(Paint.Cap.ROUND);
            p.setStrokeJoin(Paint.Join.ROUND);
            Path q = new Path();
            switch (name) {
                case "yin":
                    c.drawCircle(12, 12, 9.2f, p);
                    q.moveTo(12, 2.8f);
                    q.cubicTo(17.2f, 2.8f, 17.2f, 12, 12, 12);
                    q.cubicTo(6.8f, 12, 6.8f, 21.2f, 12, 21.2f);
                    c.drawPath(q, p);
                    c.drawCircle(12, 16.6f, 1.45f, p);
                    p.setStyle(Paint.Style.FILL);
                    c.drawCircle(12, 7.4f, 1.55f, p);
                    break;
                case "sound":
                    for (int i = 0; i < 3; i++) {
                        float x = 6 + i * 6;
                        float knob = i == 0 ? 15f : i == 1 ? 8f : 13f;
                        c.drawLine(x, 4, x, 20, p);
                        p.setStyle(Paint.Style.FILL);
                        c.drawRoundRect(x - 2.3f, knob - 1.6f, x + 2.3f, knob + 1.6f, 1f, 1f, p);
                        p.setStyle(Paint.Style.STROKE);
                    }
                    break;
                case "route":
                    c.drawLine(6.8f, 14.5f, 10.4f, 9.5f, p);
                    c.drawLine(13.8f, 9.2f, 17.2f, 13.5f, p);
                    c.drawCircle(5, 16.5f, 2.4f, p);
                    c.drawCircle(12, 7.5f, 2.4f, p);
                    c.drawCircle(19, 15.5f, 2.4f, p);
                    break;
                case "config":
                    c.drawCircle(12, 12, 3.2f, p);
                    q.moveTo(12, 3.2f); q.lineTo(19.6f, 7.6f); q.lineTo(19.6f, 16.4f);
                    q.lineTo(12, 20.8f); q.lineTo(4.4f, 16.4f); q.lineTo(4.4f, 7.6f); q.close();
                    c.drawPath(q, p);
                    break;
                case "speaker":
                    q.moveTo(4, 9.5f); q.lineTo(8, 9.5f); q.lineTo(13, 5);
                    q.lineTo(13, 19); q.lineTo(8, 14.5f); q.lineTo(4, 14.5f); q.close();
                    c.drawPath(q, p);
                    c.drawArc(14.5f, 8.2f, 18.5f, 15.8f, -55, 110, false, p);
                    c.drawArc(16.2f, 5.8f, 21.2f, 18.2f, -55, 110, false, p);
                    break;
                case "headphones":
                    c.drawArc(4, 3.5f, 20, 19.5f, 180, 180, false, p);
                    c.drawRoundRect(3, 11.5f, 7, 20, 2, 2, p);
                    c.drawRoundRect(17, 11.5f, 21, 20, 2, 2, p);
                    break;
                case "bluetooth":
                    q.moveTo(12, 2.5f); q.lineTo(12, 21.5f); q.lineTo(18.5f, 16.5f);
                    q.lineTo(5.5f, 7.5f); q.moveTo(5.5f, 16.5f); q.lineTo(18.5f, 7.5f);
                    q.lineTo(12, 2.5f);
                    c.drawPath(q, p);
                    break;
                case "usb":
                    c.drawRoundRect(6.5f, 8, 17.5f, 16.5f, 3, 3, p);
                    c.drawRoundRect(9, 3.5f, 15, 8, 1, 1, p);
                    c.drawLine(12, 16.5f, 12, 21.5f, p);
                    break;
                case "power":
                    c.drawArc(4.8f, 5, 19.2f, 19.4f, -52, 284, false, p);
                    c.drawLine(12, 3, 12, 11.5f, p);
                    break;
                case "dac":
                    c.drawRoundRect(6.5f, 6.5f, 17.5f, 17.5f, 2, 2, p);
                    c.drawRoundRect(9.8f, 9.8f, 14.2f, 14.2f, 0.8f, 0.8f, p);
                    for (int i = 9; i <= 15; i += 3) {
                        c.drawLine(i, 3, i, 6.5f, p); c.drawLine(i, 17.5f, i, 21, p);
                        c.drawLine(3, i, 6.5f, i, p); c.drawLine(17.5f, i, 21, i, p);
                    }
                    break;
                case "refresh":
                    c.drawArc(4.5f, 4.5f, 19.5f, 19.5f, -35, 290, false, p);
                    q.moveTo(16.5f, 3.5f); q.lineTo(19.8f, 7); q.lineTo(15.5f, 8.2f);
                    c.drawPath(q, p);
                    break;
                case "copy":
                    c.drawRoundRect(8.5f, 8.5f, 19.5f, 20, 2, 2, p);
                    q.moveTo(15.5f, 8.5f); q.lineTo(15.5f, 4.5f); q.lineTo(4.5f, 4.5f);
                    q.lineTo(4.5f, 15.5f); q.lineTo(8.5f, 15.5f);
                    c.drawPath(q, p);
                    break;
                case "reset":
                    c.drawArc(4.5f, 4.5f, 19.5f, 19.5f, 215, 290, false, p);
                    q.moveTo(4, 4); q.lineTo(4, 9.5f); q.lineTo(9.5f, 9.5f);
                    c.drawPath(q, p);
                    break;
                case "import":
                    q.moveTo(4, 15); q.lineTo(4, 19.5f); q.lineTo(20, 19.5f); q.lineTo(20, 15);
                    c.drawPath(q, p);
                    c.drawLine(12, 4, 12, 15, p);
                    q.reset(); q.moveTo(8, 11); q.lineTo(12, 15); q.lineTo(16, 11);
                    c.drawPath(q, p);
                    break;
                case "drag":
                    p.setStyle(Paint.Style.FILL);
                    for (int x = 9; x <= 15; x += 6) {
                        for (int y = 6; y <= 18; y += 6) c.drawCircle(x, y, 1.3f, p);
                    }
                    break;
                default:
                    c.drawCircle(12, 12, 8, p);
                    break;
            }
            c.restore();
        }

        @Override public void setAlpha(int alpha) { p.setAlpha(alpha); }
        @Override public void setColorFilter(ColorFilter filter) { p.setColorFilter(filter); }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
