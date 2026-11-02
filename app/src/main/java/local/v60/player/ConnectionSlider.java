package local.v60.player;

import android.content.Context;
import android.view.*;
import android.widget.*;
import java.util.function.IntConsumer;

/** Four connection cards, snapped one page at a time without changing the audio route. */
final class ConnectionSlider extends HorizontalScrollView {
    final LinearLayout pages;
    private final IntConsumer selected;
    private int page,dragPage,flingTarget;
    private boolean flung;
    int selectedPage(){return page;}
    ConnectionSlider(Context context,int initial,IntConsumer selected){
        super(context);this.page=initial;this.selected=selected;
        setHorizontalScrollBarEnabled(false);setOverScrollMode(OVER_SCROLL_NEVER);setFillViewport(true);
        pages=new LinearLayout(context);pages.setOrientation(LinearLayout.HORIZONTAL);addView(pages,new LayoutParams(-2,-2));
        setContentDescription("Perfiles de conexión. Desliza para cambiar el perfil que editas.");
    }
    void select(int index,boolean animate){
        int next=Math.max(0,Math.min(pages.getChildCount()-1,index));boolean changed=page!=next;page=next;
        post(()->{if(page!=next)return;if(animate)smoothScrollTo(next*getWidth(),0);else scrollTo(next*getWidth(),0);});
        // A fling can be initiated by the framework while drawing. Rebuild only
        // the settings pane on the next UI turn, never inside dispatchDraw.
        if(changed)post(()->{if(page==next)selected.accept(next);});
    }
    @Override protected void onMeasure(int width,int height){
        int size=MeasureSpec.getSize(width);
        for(int i=0;i<pages.getChildCount();i++){android.view.ViewGroup.LayoutParams lp=pages.getChildAt(i).getLayoutParams();if(lp.width!=size){lp.width=size;pages.getChildAt(i).setLayoutParams(lp);}}
        super.onMeasure(width,height);
    }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);post(()->scrollTo(page*getWidth(),0));}
    private void beginGesture(){dragPage=getWidth()>0?Math.round(getScrollX()/(float)getWidth()):page;flingTarget=dragPage;flung=false;}
    @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN)beginGesture();return super.onInterceptTouchEvent(event);}
    @Override public boolean onTouchEvent(MotionEvent event){
        int action=event.getActionMasked();
        if(action==MotionEvent.ACTION_DOWN)beginGesture();
        boolean handled=super.onTouchEvent(event);
        if(action==MotionEvent.ACTION_UP){int delta=getScrollX()-dragPage*getWidth();select(flung?flingTarget:dragPage+(Math.abs(delta)>getWidth()*.18f?(delta>0?1:-1):0),true);}
        else if(action==MotionEvent.ACTION_CANCEL)select(getWidth()>0?Math.round(getScrollX()/(float)getWidth()):page,true);
        return handled;
    }
    @Override public void fling(int velocity){flung=true;flingTarget=dragPage+(velocity>0?1:velocity<0?-1:0);}
    @Override public boolean performAccessibilityAction(int action,android.os.Bundle args){
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_FORWARD){select(page+1,true);return true;}
        if(action==android.view.accessibility.AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD){select(page-1,true);return true;}
        return super.performAccessibilityAction(action,args);
    }
    @Override public boolean executeKeyEvent(KeyEvent event){
        if(event.getAction()==KeyEvent.ACTION_DOWN&&(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_LEFT||event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT)){select(page+(event.getKeyCode()==KeyEvent.KEYCODE_DPAD_RIGHT?1:-1),true);return true;}
        return super.executeKeyEvent(event);
    }
}
