package local.v60.player;

import android.content.Context;
import android.graphics.*;
import android.view.*;
import java.util.function.IntConsumer;

/** Analytic RBJ response of the configured EQ only, not a microphone measurement. */
final class EqResponseView extends View {
    private final Paint p=new Paint(3);private final YinUi u;private final AudioSettings s;private final int selected;private final IntConsumer pick;
    private final double[][] response=new double[2][241];private double scale=12;private final double rate,maxFrequency;
    EqResponseView(Context c,AudioSettings settings,int selected,IntConsumer pick){super(c);u=new YinUi(c);s=settings.copy();android.os.Bundle live=YinService.meters();rate=live.getString("profileKey","").equals(s.storageKey)?Math.max(8000,YinService.publicStatus().getInt("rate",48000)):(s.rate>0?s.rate:48000);maxFrequency=Math.min(20000,rate*.49);this.selected=selected;this.pick=pick;setContentDescription("Curva de ecualización: graves a la izquierda, agudos a la derecha. Toca para seleccionar una banda.");setClickable(true);
        for(int ch=0;ch<2;ch++)for(int k=0;k<241;k++){double hz=20*Math.pow(maxFrequency/20,k/240.0);double db=0;for(int band=0;band<10;band++)if(!s.dsp.parametric||s.dsp.channel[band]==2||s.dsp.channel[band]==ch)if(s.dsp.frequency(band)<rate/2)db+=response(s.dsp.frequency(band),s.dsp.gain(band,s.gains[band]),s.dsp.parametric?s.dsp.q[band]:1.41421356,s.dsp.parametric?s.dsp.type[band]:0,hz);response[ch][k]=db;scale=Math.min(48,Math.max(scale,Math.ceil(Math.abs(db)/6)*6));}
    }
    private double response(double hz,double gain,double q,int type,double at){double w=2*Math.PI*hz/rate,cos=Math.cos(w),a=Math.pow(10,gain/40),alpha=Math.sin(w)/(2*q),b0,b1,b2,a0,a1,a2,beta=2*Math.sqrt(a)*alpha;
        if(type==1){b0=a*((a+1)-(a-1)*cos+beta);b1=2*a*((a-1)-(a+1)*cos);b2=a*((a+1)-(a-1)*cos-beta);a0=(a+1)+(a-1)*cos+beta;a1=-2*((a-1)+(a+1)*cos);a2=(a+1)+(a-1)*cos-beta;}
        else if(type==2){b0=a*((a+1)+(a-1)*cos+beta);b1=-2*a*((a-1)+(a+1)*cos);b2=a*((a+1)+(a-1)*cos-beta);a0=(a+1)-(a-1)*cos+beta;a1=2*((a-1)-(a+1)*cos);a2=(a+1)-(a-1)*cos-beta;}
        else{b0=1+alpha*a;b1=-2*cos;b2=1-alpha*a;a0=1+alpha/a;a1=-2*cos;a2=1-alpha/a;}
        double x=2*Math.PI*at/rate,c1=Math.cos(x),c2=Math.cos(2*x),si1=Math.sin(x),si2=Math.sin(2*x);double nr=b0+b1*c1+b2*c2,ni=b1*si1+b2*si2,dr=a0+a1*c1+a2*c2,di=a1*si1+a2*si2;return 10*Math.log10(Math.max(1e-20,(nr*nr+ni*ni)/Math.max(1e-20,dr*dr+di*di)));
    }
    private float x(double f){return u.d(28)+(getWidth()-u.d(42))*(float)(Math.log(f/20)/Math.log(maxFrequency/20));}
    private float y(double db){return u.d(12)+(getHeight()-u.d(44))*(float)(.5-db/(2*scale));}
    @Override protected void onDraw(Canvas c){super.onDraw(c);p.setTextSize(u.d(10));p.setStrokeWidth(u.d(1));p.setStyle(Paint.Style.STROKE);p.setColor(YinUi.LINE);for(int hz:new int[]{20,100,1000,10000})c.drawLine(x(hz),y(scale),x(hz),y(-scale),p);c.drawLine(x(20),y(0),x(maxFrequency),y(0),p);
        p.setStyle(Paint.Style.FILL);p.setColor(YinUi.MUTED);c.drawText("+"+(int)scale,0,y(scale)+u.d(4),p);c.drawText("0",u.d(10),y(0)+u.d(3),p);c.drawText("−"+(int)scale,0,y(-scale),p);c.drawText("Graves",x(20),getHeight()-u.d(8),p);c.drawText("Medios",x(700),getHeight()-u.d(8),p);c.drawText("Agudos",x(8000),getHeight()-u.d(8),p);
        c.save();c.clipRect(x(20),y(scale),x(maxFrequency),y(-scale));for(int ch=0;ch<2;ch++){Path path=new Path();for(int k=0;k<241;k++){float px=x(20*Math.pow(maxFrequency/20,k/240.0)),py=y(response[ch][k]);if(k==0)path.moveTo(px,py);else path.lineTo(px,py);}p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(u.d(ch==0?2:1.5f));p.setColor(!s.eq?YinUi.MUTED:ch==0?YinUi.ACCENT:0xffb7a1ef);if(ch==1)p.setPathEffect(new DashPathEffect(new float[]{u.d(5),u.d(3)},0));c.drawPath(path,p);p.setPathEffect(null);}
        p.setStyle(Paint.Style.FILL);for(int band=0;band<10;band++){double f=s.dsp.frequency(band);if(f>maxFrequency)continue;int k=(int)Math.round(Math.log(f/20)/Math.log(maxFrequency/20)*240);p.setColor(band==selected?YinUi.INK:YinUi.ACCENT);c.drawCircle(x(f),y(response[0][Math.max(0,Math.min(240,k))]),u.d(band==selected?4:2.5f),p);}c.restore();
    }
    @Override public boolean onTouchEvent(MotionEvent e){if(e.getActionMasked()==MotionEvent.ACTION_UP){int band=0;double distance=Double.MAX_VALUE;for(int i=0;i<10;i++){double d=Math.abs(x(s.dsp.frequency(i))-e.getX());if(d<distance){distance=d;band=i;}}pick.accept(band);performClick();}return true;}
    @Override public boolean performClick(){super.performClick();return true;}
}
