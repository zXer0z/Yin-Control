package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.os.Bundle;
import android.view.*;
import android.view.accessibility.AccessibilityNodeInfo;
import java.util.function.IntConsumer;

/** A real vertical EQ fader: no rotated controls or overlapping touch bounds. */
final class GainFader extends View {
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    private final YinUi u;
    private final IntConsumer changed;
    private final Runnable committed;
    private int gain;
    GainFader(Context c,int hz,int initial,IntConsumer changed,Runnable committed){super(c);u=new YinUi(c);gain=initial;this.changed=changed;this.committed=committed;setContentDescription(hz+" Hz");setFocusable(true);setClickable(true);}
    private void value(int v){int next=Math.max(-12,Math.min(12,v));if(gain==next)return;gain=next;changed.accept(gain);invalidate();}
    @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);float x=getWidth()/2f,top=u.d(14),bottom=getHeight()-u.d(14),mid=(top+bottom)/2,y=top+(12-gain)*(bottom-top)/24;
        paint.setStrokeCap(Paint.Cap.ROUND);paint.setStrokeWidth(u.d(4));paint.setColor(YinUi.LINE);canvas.drawLine(x,top,x,bottom,paint);
        paint.setColor(YinUi.ACCENT);canvas.drawLine(x,mid,x,y,paint);paint.setColor(YinUi.MUTED);paint.setStrokeWidth(u.d(1));canvas.drawLine(x-u.d(6),mid,x+u.d(6),mid,paint);
        paint.setColor(YinUi.SELECTED);canvas.drawCircle(x,y,u.d(9),paint);paint.setColor(YinUi.ACCENT);canvas.drawCircle(x,y,u.d(6),paint);
    }
    @Override public boolean onTouchEvent(MotionEvent event){if(!isEnabled())return false;int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN||action==MotionEvent.ACTION_MOVE||action==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(true);float top=u.d(14),range=Math.max(1,getHeight()-u.d(28));value(Math.round(12-24*(event.getY()-top)/range));if(action==MotionEvent.ACTION_UP){getParent().requestDisallowInterceptTouchEvent(false);performClick();committed.run();}return true;}
        if(action==MotionEvent.ACTION_CANCEL){getParent().requestDisallowInterceptTouchEvent(false);committed.run();return true;}return super.onTouchEvent(event);
    }
    @Override public boolean performClick(){super.performClick();return true;}
    @Override public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.SeekBar");info.setRangeInfo(AccessibilityNodeInfo.RangeInfo.obtain(AccessibilityNodeInfo.RangeInfo.RANGE_TYPE_INT,-12,12,gain));info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_FORWARD);info.addAction(AccessibilityNodeInfo.AccessibilityAction.ACTION_SCROLL_BACKWARD);}
    @Override public boolean performAccessibilityAction(int action,Bundle arguments){if(!isEnabled())return false;if(action==AccessibilityNodeInfo.AccessibilityAction.ACTION_SET_PROGRESS.getId()&&arguments!=null){value(Math.round(arguments.getFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE)));committed.run();return true;}if(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD||action==AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){value(gain+(action==AccessibilityNodeInfo.ACTION_SCROLL_FORWARD?1:-1));committed.run();return true;}return super.performAccessibilityAction(action,arguments);}
    @Override public boolean onKeyDown(int code,KeyEvent event){if(!isEnabled())return false;if(code==KeyEvent.KEYCODE_DPAD_UP||code==KeyEvent.KEYCODE_DPAD_DOWN){value(gain+(code==KeyEvent.KEYCODE_DPAD_UP?1:-1));committed.run();return true;}return super.onKeyDown(code,event);}
}
