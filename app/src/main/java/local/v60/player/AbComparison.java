package local.v60.player;

import android.content.*;
import org.json.*;

/** Per-profile sound snapshots; matching uses aligned input/output RMS, attenuation only. */
final class AbComparison {
    static SharedPreferences prefs(Context c){return c.getSharedPreferences("yin_ab",0);}
    private static String key(AudioSettings s,String slot){return s.storageKey+":"+slot;}
    static String payload(AudioSettings s){try{JSONObject j=new JSONObject();JSONArray gains=new JSONArray();for(int g:s.gains)gains.put(g);j.put("dsp",s.dsp.json()).put("eq",s.eq).put("preamp",s.preamp).put("balance",s.balance).put("headroom",s.headroom).put("limiter",s.limiter).put("gains",gains);return j.toString();}catch(Exception e){throw new IllegalStateException(e);}}
    static boolean exists(Context c,AudioSettings s,String slot){return prefs(c).contains(key(s,slot));}
    static String active(Context c,AudioSettings s){String slot=prefs(c).getString(key(s,"active"),"");return !slot.isEmpty()&&payload(s).equals(prefs(c).getString(key(s,slot),null))?slot:"";}
    static void capture(Context c,AudioSettings s,String slot){s.save(c);prefs(c).edit().putString(key(s,slot),payload(s)).putString(key(s,"active"),slot).remove(key(s,slot+"Gain")).apply();}
    static void apply(Context c,AudioSettings s,String slot)throws Exception{String raw=prefs(c).getString(key(s,slot),null);if(raw==null)return;JSONObject j=new JSONObject(raw);AudioSettings next=AudioSettings.loadKey(c,s.profile,s.storageKey);next.dsp=DspSettings.read(j.getJSONObject("dsp"));next.eq=j.getBoolean("eq");next.preamp=j.getInt("preamp");next.balance=j.getInt("balance");next.headroom=j.getBoolean("headroom");next.limiter=j.getBoolean("limiter");JSONArray gains=j.getJSONArray("gains");for(int i=0;i<10;i++)next.gains[i]=gains.optInt(i);prefs(c).edit().putString(key(s,"active"),slot).apply();next.save(c);}
    static boolean matching(Context c,AudioSettings s){return prefs(c).getBoolean(key(s,"match"),true);}
    static void matching(Context c,AudioSettings s,boolean enabled){prefs(c).edit().putBoolean(key(s,"match"),enabled).apply();}
    static String status(Context c,AudioSettings s){String active=active(c,s);if(active.isEmpty())return "Guarda este sonido en A o B para compararlo.";if(!matching(c,s))return "Escuchando "+active+" · igualación apagada";if(!prefs(c).contains(key(s,"AGain"))||!prefs(c).contains(key(s,"BGain")))return "Escucha al menos 5 segundos de música en A y en B para estimar sus niveles.";return "Escuchando "+active+" · nivel medio igualado de forma aproximada";}
    static final class Session {
        private final Context c;private final AudioSettings s;private boolean measured;
        Session(Context c,AudioSettings s){this.c=c.getApplicationContext();this.s=s.copy();}
        void observe(PcmMeter input,PcmMeter output){String slot=active(c,s);if(slot.isEmpty()||(measured&&prefs(c).contains(key(s,slot+"Gain"))))return;long end=Math.min(input.lastComplete(),output.lastComplete());if(end<50)return;double[] before=input.level(end-49,end),after=output.level(end-49,end);if(before[0]<1e-6||after[0]<1e-10||before[2]<input.rate*input.channels*4L||after[2]<output.rate*output.channels*4L)return;double gain=10*Math.log10(after[0]/before[0]);if(Double.isFinite(gain)){prefs(c).edit().putFloat(key(s,slot+"Gain"),(float)gain).apply();measured=true;}}
        double trim(){String slot=active(c,s);if(slot.isEmpty()||!matching(c,s)||!prefs(c).contains(key(s,"AGain"))||!prefs(c).contains(key(s,"BGain")))return 0;double a=prefs(c).getFloat(key(s,"AGain"),0),b=prefs(c).getFloat(key(s,"BGain"),0),current=slot.equals("A")?a:b;return Math.max(-60,Math.min(0,Math.min(a,b)-current));}
    }
}
