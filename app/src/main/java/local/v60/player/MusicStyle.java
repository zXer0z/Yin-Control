package local.v60.player;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.widget.*;
final class MusicStyle {
 static int dp(Context c,int n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
 static GradientDrawable shape(Context c,int color,int radius){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(c,radius));return d;}
 static RippleDrawable touch(Context c,int color,int radius){return new RippleDrawable(ColorStateList.valueOf(0x220a84ff),shape(c,color,radius),shape(c,0xffffffff,radius));}
    static Button icon(Context c,String name,String description,int color,int background,int size){
        IconButton b=new IconButton(c);b.setText("");b.setContentDescription(description);b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(0);b.setMinimumHeight(0);b.setPadding(0,0,0,0);b.setBackground(touch(c,background,50));b.setStateListAnimator(null);
        b.image=new Icon(c,name,color,size);return b;
    }
    static void icon(Button b,String name,int color,int size){if(b instanceof IconButton){((IconButton)b).image=new Icon(b.getContext(),name,color,size);b.invalidate();}else b.setCompoundDrawablesWithIntrinsicBounds(null,new Icon(b.getContext(),name,color,size),null,null);}
    static final class IconButton extends Button {
        Icon image;IconButton(Context c){super(c);}
        @Override protected void onDraw(Canvas canvas){super.onDraw(canvas);if(image!=null){canvas.save();canvas.translate((getWidth()-image.getIntrinsicWidth())/2f,(getHeight()-image.getIntrinsicHeight())/2f);image.draw(canvas);canvas.restore();}}
    }
    static final class Icon extends Drawable {
        final Paint p=new Paint(3);final String name;final int color,size;
        Icon(Context c,String name,int color,int size){this.name=name;this.color=color;this.size=dp(c,size);setBounds(0,0,this.size,this.size);}
        public int getIntrinsicWidth(){return size;}public int getIntrinsicHeight(){return size;}
        public void draw(Canvas c){c.save();c.translate(getBounds().left,getBounds().top);c.scale(getBounds().width()/24f,getBounds().height()/24f);p.setColor(color);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.8f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);Path q=new Path();
            switch(name){
                case "online":c.drawCircle(12,12,9,p);c.drawOval(7,3,17,21,p);c.drawLine(3,12,21,12,p);c.drawArc(3,6,21,14,15,150,false,p);break;
                case "play":p.setStyle(Paint.Style.FILL);q.moveTo(8,4);q.lineTo(21,12);q.lineTo(8,20);q.close();c.drawPath(q,p);break;
                case "pause":p.setStyle(Paint.Style.FILL);c.drawRoundRect(6,4,10,20,1,1,p);c.drawRoundRect(14,4,18,20,1,1,p);break;
                case "next":p.setStyle(Paint.Style.FILL);q.moveTo(4,5);q.lineTo(15,12);q.lineTo(4,19);q.close();c.drawPath(q,p);c.drawRoundRect(17,5,20,19,1,1,p);break;
                case "previous":c.translate(24,0);c.scale(-1,1);p.setStyle(Paint.Style.FILL);q.moveTo(4,5);q.lineTo(15,12);q.lineTo(4,19);q.close();c.drawPath(q,p);c.drawRoundRect(17,5,20,19,1,1,p);break;
                case "rewind":c.translate(24,0);c.scale(-1,1);
                case "forward":p.setStyle(Paint.Style.FILL);q.moveTo(2,5);q.lineTo(12,12);q.lineTo(2,19);q.close();q.moveTo(12,5);q.lineTo(22,12);q.lineTo(12,19);q.close();c.drawPath(q,p);break;
                case "home":q.moveTo(3,11);q.lineTo(12,3);q.lineTo(21,11);q.moveTo(5,10);q.lineTo(5,21);q.lineTo(10,21);q.lineTo(10,14);q.lineTo(14,14);q.lineTo(14,21);q.lineTo(19,21);q.lineTo(19,10);c.drawPath(q,p);break;
                case "home-fill":
                    p.setStyle(Paint.Style.FILL);
                    q.moveTo(12,2.5f);
                    q.lineTo(2.5f,10.5f);
                    q.lineTo(5,10.5f);
                    q.lineTo(5,21);
                    q.lineTo(9.5f,21);
                    q.lineTo(9.5f,14.5f);
                    q.lineTo(14.5f,14.5f);
                    q.lineTo(14.5f,21);
                    q.lineTo(19,21);
                    q.lineTo(19,10.5f);
                    q.lineTo(21.5f,10.5f);
                    q.close();
                    c.drawPath(q,p);
                    break;
                case "sparkles":
                    p.setStyle(Paint.Style.FILL);
                    q.moveTo(12,2);
                    q.quadTo(12,12,22,12);
                    q.quadTo(12,12,12,22);
                    q.quadTo(12,12,2,12);
                    q.quadTo(12,12,12,2);
                    q.close();
                    c.drawPath(q,p);
                    break;
                case "songs":c.drawLine(9,17,9,5,p);c.drawLine(9,5,20,3,p);c.drawLine(20,3,20,15,p);c.drawOval(3,16,9,21,p);c.drawOval(14,14,20,19,p);break;
                case "albums":c.drawRoundRect(3,7,21,22,3,3,p);c.drawLine(6,3,18,3,p);c.drawCircle(12,14.5f,3.5f,p);break;
                case "hifi":
                    c.drawLine(4,10,4,14,p);
                    c.drawLine(8,6,8,18,p);
                    c.drawLine(12,3,12,21,p);
                    c.drawLine(16,6,16,18,p);
                    c.drawLine(20,10,20,14,p);
                    break;
                case "dac":
                    c.drawRoundRect(6,6,18,18,2.5f,2.5f,p);
                    c.drawRoundRect(9.5f,9.5f,14.5f,14.5f,1f,1f,p);
                    for(int i=9;i<=15;i+=3){
                        c.drawLine(i,3,i,6,p);c.drawLine(i,18,i,21,p);
                        c.drawLine(3,i,6,i,p);c.drawLine(18,i,21,i,p);
                    }
                    break;
                case "theme":
                    c.drawArc(4,4,20,20,45,270,false,p);
                    q.moveTo(15.5f,4.8f);q.cubicTo(10.5f,7.5f,10.5f,16.5f,15.5f,19.2f);
                    c.drawPath(q,p);
                    break;
                case "radio":c.drawRoundRect(3,8,21,21,3,3,p);c.drawLine(6,8,18,3,p);c.drawCircle(8,15,2.5f,p);c.drawLine(14,12,18,12,p);c.drawLine(14,16,18,16,p);break;
                case "profile":c.drawCircle(12,7,4,p);c.drawArc(3,13,21,29,185,170,false,p);break;
                case "search":c.drawCircle(10,10,6.5f,p);c.drawLine(15,15,21,21,p);break;
                case "back":q.moveTo(14,5);q.lineTo(7,12);q.lineTo(14,19);c.drawPath(q,p);break;
                case "plus":q.moveTo(5,12);q.lineTo(19,12);q.moveTo(12,5);q.moveTo(12,19);c.drawPath(q,p);break;
                case "more":p.setStyle(Paint.Style.FILL);c.drawCircle(5,12,1.5f,p);c.drawCircle(12,12,1.5f,p);c.drawCircle(19,12,1.5f,p);break;
                case "queue":c.drawLine(3,5,20,5,p);c.drawLine(3,11,15,11,p);c.drawLine(3,17,11,17,p);q.moveTo(17,14);q.lineTo(22,17);q.lineTo(17,20);q.close();c.drawPath(q,p);break;
                case "drag":p.setStyle(Paint.Style.FILL);for(int x=9;x<=15;x+=6)for(int y=6;y<=18;y+=6)c.drawCircle(x,y,1.3f,p);break;
                case "up":q.moveTo(5,15);q.lineTo(12,8);q.lineTo(19,15);c.drawPath(q,p);break;
                case "down":q.moveTo(5,9);q.lineTo(12,16);q.lineTo(19,9);c.drawPath(q,p);break;
                case "heart":case "heart-fill":if(name.endsWith("fill"))p.setStyle(Paint.Style.FILL);q.moveTo(12,21);q.cubicTo(-5,10,4,-3,12,6);q.cubicTo(20,-3,29,10,12,21);c.drawPath(q,p);break;
                case "settings":for(int i=0;i<3;i++){float y=5+i*7;float x=i==1?15:8;c.drawLine(3,y,21,y,p);p.setStyle(Paint.Style.FILL);c.drawCircle(x,y,3,p);p.setStyle(Paint.Style.STROKE);}break;
                case "shuffle":q.moveTo(3,6);q.lineTo(6,6);q.cubicTo(10,6,14,18,18,18);q.lineTo(21,18);q.moveTo(17,14);q.lineTo(21,18);q.lineTo(17,22);q.moveTo(3,18);q.lineTo(6,18);q.cubicTo(10,18,14,6,18,6);q.lineTo(21,6);q.moveTo(17,2);q.lineTo(21,6);q.lineTo(17,10);c.drawPath(q,p);break;
                case "repeat":case "repeat-one":
                    q.moveTo(3,11);q.lineTo(3,9);q.arcTo(new RectF(3,5,11,13),180,90,false);q.lineTo(21,5);
                    q.moveTo(17,1);q.lineTo(21,5);q.lineTo(17,9);
                    q.moveTo(21,13);q.lineTo(21,15);q.arcTo(new RectF(13,11,21,19),0,90,false);q.lineTo(3,19);
                    q.moveTo(7,15);q.lineTo(3,19);q.lineTo(7,23);c.drawPath(q,p);
                    if(name.endsWith("-one")){q.reset();q.moveTo(10.6f,10.6f);q.lineTo(12.4f,9.4f);q.lineTo(12.4f,15);c.drawPath(q,p);}break;
                case "info":c.drawCircle(12,12,9,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,8,1.2f,p);c.drawRoundRect(11,10.5f,13,17,0.5f,0.5f,p);break;
                case "lyrics":c.drawRoundRect(3,3,21,18,4,4,p);q.moveTo(8,18);q.lineTo(6,22);q.lineTo(14,18);c.drawPath(q,p);c.drawLine(8,8,8,11,p);c.drawLine(8,11,6,13,p);c.drawLine(15,8,15,11,p);c.drawLine(15,11,13,13,p);break;
                case "lyrics-text":c.drawRoundRect(3,3,21,18,3,3,p);q.moveTo(7,18);q.lineTo(7,22);q.lineTo(12,18);c.drawPath(q,p);c.drawLine(7,7,17,7,p);c.drawLine(7,10.5f,17,10.5f,p);c.drawLine(7,14,13,14,p);break;
                case "folder":q.moveTo(3,7);q.lineTo(3,20);q.lineTo(21,20);q.lineTo(21,7);q.lineTo(12,7);q.lineTo(10,4);q.lineTo(3,4);q.close();c.drawPath(q,p);break;
                case "close":c.drawLine(6,6,18,18,p);c.drawLine(18,6,6,18,p);break;
                case "trash":c.drawLine(4,6,20,6,p);c.drawRoundRect(6,6,18,21,2,2,p);c.drawLine(9,3,15,3,p);c.drawLine(10,10,10,17,p);c.drawLine(14,10,14,17,p);break;
                case "speaker":q.moveTo(4,9);q.lineTo(8,9);q.lineTo(13,5);q.lineTo(13,19);q.lineTo(8,15);q.lineTo(4,15);q.close();c.drawPath(q,p);c.drawArc(15,8,19,16,-60,120,false,p);break;
                case "volume-low":p.setStyle(Paint.Style.FILL);q.moveTo(4,9);q.lineTo(8,9);q.lineTo(13,5);q.lineTo(13,19);q.lineTo(8,15);q.lineTo(4,15);q.close();c.drawPath(q,p);break;
                case "headphones":c.drawArc(4,3,20,20,180,180,false,p);c.drawRoundRect(3,11,7,20,2,2,p);c.drawRoundRect(17,11,21,20,2,2,p);break;
                case "bluetooth":q.moveTo(12,2);q.lineTo(12,22);q.lineTo(19,17);q.lineTo(5,7);q.lineTo(5,17);q.lineTo(19,7);q.lineTo(12,2);c.drawPath(q,p);break;
                case "usb":c.drawRoundRect(7,8,17,17,3,3,p);c.drawLine(9,4,9,8,p);c.drawLine(15,4,15,8,p);c.drawLine(12,17,12,22,p);break;
                case "reset":c.drawArc(4,4,20,20,205,295,false,p);q.moveTo(3,3);q.lineTo(3,10);q.lineTo(10,10);c.drawPath(q,p);break;
                case "check":q.moveTo(5,12);q.lineTo(10,17);q.lineTo(20,6);c.drawPath(q,p);break;
                case "chevron":q.moveTo(9,5);q.lineTo(16,12);q.lineTo(9,19);c.drawPath(q,p);break;
                case "clock":c.drawCircle(12,12,9,p);c.drawLine(12,7.5f,12,12,p);c.drawLine(12,12,16,12,p);break;
                case "share":p.setStyle(Paint.Style.FILL);c.drawCircle(18,6,2.5f,p);c.drawCircle(6,12,2.5f,p);c.drawCircle(18,18,2.5f,p);p.setStyle(Paint.Style.STROKE);c.drawLine(8.2f,10.9f,15.8f,7.1f,p);c.drawLine(8.2f,13.1f,15.8f,16.9f,p);break;
                case "playlist":c.drawLine(4,6,17,6,p);c.drawLine(4,11,14,11,p);c.drawLine(4,16,11,16,p);q.moveTo(17,13);q.lineTo(17,19);q.moveTo(14,16);q.lineTo(20,16);c.drawPath(q,p);break;
                case "sort":c.drawLine(4,6,13,6,p);c.drawLine(4,11,10,11,p);c.drawLine(4,16,7,16,p);q.moveTo(15,14);q.lineTo(18,17.5f);q.lineTo(21,14);c.drawPath(q,p);c.drawLine(18,6,18,17.5f,p);break;
                case "edit":q.moveTo(15,4);q.lineTo(19,8);q.lineTo(8,19);q.lineTo(4,20);q.lineTo(5,16);q.close();c.drawPath(q,p);break;
                case "lock":
                    c.drawRoundRect(5,10,19,21,2.5f,2.5f,p);
                    c.drawArc(8,4,16,13,180,180,false,p);
                    c.drawLine(8,9,8,11,p);
                    c.drawLine(16,9,16,11,p);
                    c.drawCircle(12,14.5f,1.5f,p);
                    c.drawLine(12,15.5f,12,18,p);
                    break;
                default:c.drawCircle(12,12,8,p);p.setStyle(Paint.Style.FILL);c.drawCircle(12,12,2,p);
            }c.restore();}
        public void setAlpha(int alpha){p.setAlpha(alpha);}public void setColorFilter(ColorFilter filter){p.setColorFilter(filter);}public int getOpacity(){return PixelFormat.TRANSLUCENT;}
    }
}
