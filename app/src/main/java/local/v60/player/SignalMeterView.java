package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.os.*;
import android.view.View;

/** Friendly PCM levels. Never estimates acoustic volume or hearing safety. */
final class SignalMeterView extends View {
    private final YinUi u;private final Paint p=new Paint(3);private final Handler handler=new Handler(Looper.getMainLooper());private final String key;private final Runnable tick;
    private float before,after;private String status="Sin música";private int color=YinUi.MUTED;
    private final Runnable poll=new Runnable(){public void run(){Bundle b=YinService.meters();boolean same=key.equals(b.getString("profileKey")),active=same&&b.getBoolean("active")&&b.getBoolean("signal");float a=active?(float)Math.max(0,Math.min(1,(b.getDouble("before")+60)/60)):0,z=active?(float)Math.max(0,Math.min(1,(b.getDouble("after")+60)/60)):0;before+=(a-before)*.5f;after+=(z-after)*.5f;color=active?(b.getBoolean("ceiling")?0xfff5a08a:b.getDouble("peak")>-3?0xfff5c58a:YinUi.ACCENT):YinUi.MUTED;status=!same&&b.getBoolean("active")?"Suena otra conexión":!active?"Sin señal en este perfil":b.getBoolean("ceiling")?"La señal toca el techo digital":b.getDouble("peak")>-3?"Señal cerca del límite":"Hay margen en la señal";invalidate();if(tick!=null)tick.run();handler.postDelayed(this,200);}};
    SignalMeterView(Context c,String key,Runnable tick){super(c);u=new YinUi(c);this.key=key;this.tick=tick;setContentDescription("Nivel de audio antes y después de los efectos. No mide el volumen acústico.");}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();handler.post(poll);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacks(poll);super.onDetachedFromWindow();}
    @Override protected void onDraw(Canvas c){super.onDraw(c);p.setTextSize(u.d(12));p.setColor(YinUi.MUTED);c.drawText("Antes de los efectos",0,u.d(17),p);bar(c,u.d(28),before,YinUi.ACCENT);p.setColor(YinUi.MUTED);c.drawText("Después de los efectos",0,u.d(68),p);bar(c,u.d(79),after,color);p.setColor(color);c.drawText(status,0,u.d(119),p);}
    private void bar(Canvas c,int top,float value,int color){p.setColor(0xff2b3843);c.drawRoundRect(0,top,getWidth(),top+u.d(10),u.d(5),u.d(5),p);p.setColor(color);c.drawRoundRect(0,top,getWidth()*value,top+u.d(10),u.d(5),u.d(5),p);}
}
