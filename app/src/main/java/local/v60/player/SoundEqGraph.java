package local.v60.player;
import android.content.Context;
import android.graphics.*;
import android.view.View;

/** Draws the user's configured EQ gains, never a simulated audio measurement. */
final class SoundEqGraph extends View {
    private final AudioSettings settings;
    private final Paint paint=new Paint(Paint.ANTI_ALIAS_FLAG);
    SoundEqGraph(Context c,AudioSettings settings){super(c);this.settings=settings;setContentDescription("Curva de las diez bandas del ecualizador");}
    @Override protected void onDraw(Canvas c){
        super.onDraw(c);float density=getResources().getDisplayMetrics().density,l=9*density,r=getWidth()-9*density,t=12*density,b=getHeight()-24*density;
        paint.setStrokeWidth(density);paint.setColor(SoundStyle.LINE);paint.setStyle(Paint.Style.STROKE);
        for(int i=0;i<3;i++){float y=t+(b-t)*i/2;c.drawLine(l,y,r,y,paint);}
        Path curve=new Path();for(int i=0;i<10;i++){float x=l+(r-l)*i/9,y=t+(b-t)*(.5f-(settings.eq?settings.gains[i]:0)/24f);if(i==0)curve.moveTo(x,y);else curve.lineTo(x,y);}
        Path fill=new Path(curve);fill.lineTo(r,b);fill.lineTo(l,b);fill.close();paint.setStyle(Paint.Style.FILL);paint.setShader(new LinearGradient(0,t,0,b,0x38e95b70,0x00e95b70,Shader.TileMode.CLAMP));c.drawPath(fill,paint);paint.setShader(null);
        paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(2.2f*density);paint.setStrokeJoin(Paint.Join.ROUND);paint.setColor(settings.eq?SoundStyle.ACCENT:SoundStyle.MUTED);c.drawPath(curve,paint);
        paint.setStyle(Paint.Style.FILL);paint.setTypeface(SoundStyle.font(getContext(),500));paint.setTextSize(9*density);paint.setColor(SoundStyle.MUTED);c.drawText("31 Hz",l,getHeight()-3*density,paint);paint.setTextAlign(Paint.Align.CENTER);c.drawText("1 kHz",l+(r-l)*5/9,getHeight()-3*density,paint);paint.setTextAlign(Paint.Align.RIGHT);c.drawText("16 kHz",r,getHeight()-3*density,paint);paint.setTextAlign(Paint.Align.LEFT);
    }
}
