package local.v60.player;

import android.app.*;
import android.graphics.Color;
import android.view.*;
import java.util.*;

/** Keeps each visible surface and the activity sampled by LG's QS on the same palette. */
final class AppDialog extends Dialog {
    private static final Map<Activity,List<AppDialog>> visible=new WeakHashMap<>();
    private static final Map<Activity,int[]> bases=new WeakHashMap<>();
    static void base(Activity owner,int top,int bottom){bases.put(owner,new int[]{top,bottom});sync(owner);}
    final Activity owner;final boolean immersive,fullScreen;
    int top=SoundStyle.BG,bottom=SoundStyle.BG;
    private boolean hideStatus;
    private boolean revealingUnderlay;
    void revealUnderlay(boolean enabled){if(revealingUnderlay==enabled)return;revealingUnderlay=enabled;sync(owner);}
    private java.util.function.Consumer<Runnable> dismissTransition;
    private boolean dismissRequested;
    void dismissTransition(java.util.function.Consumer<Runnable> transition){dismissTransition=transition;}
    private java.util.function.IntConsumer touchObserver;
    private java.util.function.Consumer<Boolean> focusObserver;
    void observeInput(java.util.function.IntConsumer touch,java.util.function.Consumer<Boolean> focus){touchObserver=touch;focusObserver=focus;}
    @Override public boolean dispatchTouchEvent(MotionEvent event){try{return super.dispatchTouchEvent(event);}finally{if(touchObserver!=null)touchObserver.accept(event.getActionMasked());}}
    void hideStatusBar(boolean hidden){hideStatus=hidden;if(isShowing())apply();}
    AppDialog(Activity owner){this(owner,false,false);}
    AppDialog(Activity owner,boolean immersive){this(owner,immersive,immersive);}
    AppDialog(Activity owner,boolean immersive,boolean fullScreen){
        super(owner,fullScreen?owner.getResources().getIdentifier(immersive?"PlayerSheetDialog":"AppFullScreenDialog","style",owner.getPackageName()):0);this.owner=owner;this.immersive=immersive;this.fullScreen=fullScreen;
        if(fullScreen){Window w=getWindow();if(w!=null){w.setLayout(-1,-1);w.setDecorFitsSystemWindows(false);WindowManager.LayoutParams p=w.getAttributes();p.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;w.setAttributes(p);}}
        if(android.os.Build.VERSION.SDK_INT>=33){try{getOnBackInvokedDispatcher().registerOnBackInvokedCallback(android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT,this::onBackPressed);}catch(Throwable ignored){}}
    }
    void palette(int top,int bottom){this.top=top;this.bottom=bottom;if(isShowing()){apply();sync(owner);}}
    @Override public void show(){super.show();List<AppDialog> stack=visible.get(owner);if(stack==null){stack=new ArrayList<>();visible.put(owner,stack);}stack.remove(this);stack.add(this);apply();sync(owner);}
    @Override public void dismiss(){
        if(dismissTransition!=null&&isShowing()&&!owner.isDestroyed()&&!owner.isFinishing()){
            if(dismissRequested)return;dismissRequested=true;dismissTransition.accept(this::finishDismiss);return;
        }
        finishDismiss();
    }
    private void finishDismiss(){try{super.dismiss();}finally{remove();}}
    @Override protected void onStop(){super.onStop();remove();}
    @Override public void onWindowFocusChanged(boolean focus){super.onWindowFocusChanged(focus);if(focus&&isShowing()){apply();sync(owner);}if(focusObserver!=null)focusObserver.accept(focus);}
    private void remove(){List<AppDialog> stack=visible.get(owner);if(stack!=null){stack.remove(this);if(stack.isEmpty())visible.remove(owner);}sync(owner);}
    private void apply(){if(!immersive){Window w=getWindow();paint(w,top,bottom);if(fullScreen&&w!=null){w.setDecorFitsSystemWindows(false);w.setStatusBarColor(Color.TRANSPARENT);w.setNavigationBarColor(Color.TRANSPARENT);}}}
    static void library(Activity owner){sync(owner);}
    private static void sync(Activity owner){if(owner.isDestroyed())return;List<AppDialog> stack=visible.get(owner);AppDialog surface=null;if(stack!=null)for(int i=stack.size()-1;i>=0;i--)if(!stack.get(i).revealingUnderlay){surface=stack.get(i);break;}int[] base=bases.get(owner);paint(owner.getWindow(),surface==null?(base==null?SoundStyle.BG:base[0]):surface.top,surface==null?(base==null?SoundStyle.BG:base[1]):surface.bottom);}
    private static boolean light(int color){return Color.red(color)*.2126+Color.green(color)*.7152+Color.blue(color)*.0722>155;}
    private static void paint(Window w,int top,int bottom){
        if(w==null)return;
        WindowManager.LayoutParams attributes=w.getAttributes();
        if(attributes!=null&&attributes.layoutInDisplayCutoutMode!=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS){
            attributes.layoutInDisplayCutoutMode=WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS;
            w.setAttributes(attributes);
        }
        w.clearFlags(WindowManager.LayoutParams.FLAG_TRANSLUCENT_STATUS|WindowManager.LayoutParams.FLAG_TRANSLUCENT_NAVIGATION);
        w.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS);
        w.setStatusBarContrastEnforced(false);
        w.setNavigationBarContrastEnforced(false);
        w.setStatusBarColor(top);
        w.setNavigationBarColor(bottom);
        View decor=w.getDecorView();
        WindowInsetsController c=decor.getWindowInsetsController();
        if(c!=null){
            c.show(WindowInsets.Type.systemBars());
            c.setSystemBarsAppearance((light(top)?WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS:0)|(light(bottom)?WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS:0),WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS|WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS);
        }else{
            int mask=View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
            int clearMask=View.SYSTEM_UI_FLAG_FULLSCREEN|View.SYSTEM_UI_FLAG_HIDE_NAVIGATION|View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY;
            decor.setSystemUiVisibility((decor.getSystemUiVisibility()&~clearMask&~mask)|(light(top)?View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR:0)|(light(bottom)?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        }
    }
}
