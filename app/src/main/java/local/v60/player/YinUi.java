package local.v60.player;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.widget.*;

/** Small native view vocabulary shared by the two control screens. */
final class YinUi {
    static final int INK=0xffedf3f4,MUTED=0xff97a8b5;
    static int BG=0xff0b0e13,CARD=0xff151b23,LINE=0xff2b3843,SOFT=0xff21352f,SELECTED=0xff253b34;
    static int ACCENT=0xffa1e5cf;
    static final int[] ACCENTS={0xffa1e5cf,0xffb7a1ef,0xff9ecbff,0xffffbd9d,0xffe9a9cc,0xffe3d38f};
    static void load(Context c){int i=c.getSharedPreferences("yin_control",0).getInt("accent",0);ACCENT=ACCENTS[Math.max(0,Math.min(ACCENTS.length-1,i))];BG=mix(0xff0b0e13,ACCENT,.035f);CARD=mix(0xff151b23,ACCENT,.065f);LINE=mix(0xff2b303b,ACCENT,.20f);SOFT=mix(0xff171c24,ACCENT,.15f);SELECTED=mix(0xff171c24,ACCENT,.25f);}
    static int mix(int base,int color,float amount){return android.graphics.Color.rgb(Math.round(android.graphics.Color.red(base)*(1-amount)+android.graphics.Color.red(color)*amount),Math.round(android.graphics.Color.green(base)*(1-amount)+android.graphics.Color.green(color)*amount),Math.round(android.graphics.Color.blue(base)*(1-amount)+android.graphics.Color.blue(color)*amount));}
    void tint(Switch v){int[][] states={new int[]{android.R.attr.state_checked},new int[]{}};v.setThumbTintList(new ColorStateList(states,new int[]{ACCENT,MUTED}));v.setTrackTintList(new ColorStateList(states,new int[]{SELECTED,LINE}));v.setButtonTintList(ColorStateList.valueOf(ACCENT));}
    final Context c;
    YinUi(Context c){this.c=c;}
    int d(float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    LinearLayout column(){LinearLayout v=new LinearLayout(c);v.setOrientation(1);return v;}
    LinearLayout row(){LinearLayout v=new LinearLayout(c);v.setGravity(Gravity.CENTER_VERTICAL);return v;}
    TextView text(String value,float size,int color){TextView v=new TextView(c);v.setText(value);v.setTextSize(size);v.setTextColor(color);v.setFontFeatureSettings("tnum");return v;}
    TextView title(String value,float size){TextView v=text(value,size,INK);v.setTypeface(Typeface.create("sans-serif-medium",Typeface.NORMAL));return v;}
    GradientDrawable shape(int color,int radius){GradientDrawable v=new GradientDrawable();v.setColor(color);v.setCornerRadius(d(radius));v.setStroke(d(1),LINE);return v;}
    LinearLayout card(LinearLayout parent,String eyebrow,String name){LinearLayout v=column();v.setPadding(d(16),d(16),d(16),d(16));v.setBackground(shape(CARD,22));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=d(12);parent.addView(v,p);if(!eyebrow.isEmpty()){TextView e=text(eyebrow,10,ACCENT);e.setLetterSpacing(.14f);v.addView(e);gap(v,6);}if(!name.isEmpty()){v.addView(title(name,18));gap(v,8);}return v;}
    void gap(LinearLayout p,int n){p.addView(new android.view.View(c),new LinearLayout.LayoutParams(1,d(n)));}
    Button button(String value,Runnable click){Button b=new Button(c);b.setAllCaps(false);b.setText(value);b.setTextSize(13);b.setTextColor(ACCENT);b.setBackground(shape(SOFT,22));b.setPadding(d(14),d(12),d(14),d(12));b.setOnClickListener(v->click.run());b.setMinHeight(d(48));return b;}
    void detail(LinearLayout p,String key,String value){LinearLayout r=row();r.setPadding(0,d(7),0,d(7));TextView a=text(key,13,MUTED),b=text(value,13,INK);b.setGravity(Gravity.END);b.setTextIsSelectable(true);r.addView(a,new LinearLayout.LayoutParams(0,-2,1));r.addView(b,new LinearLayout.LayoutParams(0,-2,1.3f));p.addView(r);}
}
