package local.v60.player;

import java.util.*;
import org.json.*;

/** All effects are opt-in. Values are finite, bounded and persisted per output. */
final class DspSettings {
    boolean parametric,convolver,bass,compressor,crossfeed,surround,comfort,protection;
    final int[] hz=AudioSettings.BANDS.clone(),type=new int[10];
    final double[] q={1,1,1,1,1,1,1,1,1,1};
    final double[] gain=new double[10];
    boolean gainStored,parametricEdited;
    double preampFraction;
    static final String[] STAGES={"convolver","eq","bass","crossfeed","surround","comfort","compressor","channels"};
    String[] order=STAGES.clone();
    final int[] channel={2,2,2,2,2,2,2,2,2,2};
    boolean channelsEnabled;
    double leftGain,rightGain;
    int leftDelay,rightDelay;
    int bassGain=3,bassHz=80,threshold=-18,ratio=3,attack=10,release=180;
    int crossfeedAmount=25,delay=8,surroundAmount=25,comfortAmount=2,comfortHz=3500,ceiling=-6,outputTrim=-3;
    int irMix=100,irGain=-6,irChannels=1;
    String irFile="",irName="";
    DspSettings copy(){return read(json());}
    static DspSettings read(JSONObject j){DspSettings d=new DspSettings();if(j==null)return d;
        d.parametric=j.optBoolean("parametric");d.convolver=j.optBoolean("convolver");d.bass=j.optBoolean("bass");d.compressor=j.optBoolean("compressor");d.crossfeed=j.optBoolean("crossfeed");d.surround=j.optBoolean("surround");d.comfort=j.optBoolean("comfort");d.protection=j.optBoolean("protection");
        d.parametricEdited=j.optBoolean("parametricEdited",d.parametric);
        d.channelsEnabled=j.optBoolean("channelsEnabled");d.leftGain=j.optDouble("leftGain");d.rightGain=j.optDouble("rightGain");d.leftDelay=j.optInt("leftDelay");d.rightDelay=j.optInt("rightDelay");
        JSONArray order=j.optJSONArray("order"),ch=j.optJSONArray("channel");LinkedHashSet<String> stages=new LinkedHashSet<>();if(order!=null)for(int i=0;i<order.length();i++)if(Arrays.asList(STAGES).contains(order.optString(i)))stages.add(order.optString(i));stages.addAll(Arrays.asList(STAGES));d.order=stages.toArray(new String[0]);for(int i=0;i<10;i++)if(ch!=null)d.channel[i]=Math.max(0,Math.min(2,ch.optInt(i,2)));
        d.bassGain=j.optInt("bassGain",3);d.bassHz=j.optInt("bassHz",80);d.threshold=j.optInt("threshold",-18);d.ratio=j.optInt("ratio",3);d.attack=j.optInt("attack",10);d.release=j.optInt("release",180);
        d.crossfeedAmount=j.optInt("crossfeedAmount",25);d.delay=j.optInt("delay",8);d.surroundAmount=j.optInt("surroundAmount",25);d.comfortAmount=j.optInt("comfortAmount",2);d.comfortHz=j.optInt("comfortHz",3500);d.ceiling=j.optInt("ceiling",-6);d.outputTrim=j.optInt("outputTrim",-3);
        d.irFile=j.optString("irFile","");d.irName=j.optString("irName","");d.irMix=j.optInt("irMix",100);d.irGain=j.optInt("irGain",-6);d.irChannels=j.optInt("irChannels",1);
        JSONArray f=j.optJSONArray("hz"),width=j.optJSONArray("q"),types=j.optJSONArray("type"),g=j.optJSONArray("gain");d.gainStored=g!=null;d.preampFraction=j.optDouble("preampFraction",0);for(int i=0;i<10;i++){if(f!=null)d.hz[i]=f.optInt(i,AudioSettings.BANDS[i]);if(width!=null)d.q[i]=width.optDouble(i,1);if(types!=null)d.type[i]=types.optInt(i);if(g!=null)d.gain[i]=g.optDouble(i,0);}
        d.validate();return d;
    }
    JSONObject json(){validate();validatePrecision();JSONObject j=new JSONObject();try{
        j.put("parametricEdited",parametricEdited);
        j.put("channelsEnabled",channelsEnabled).put("leftGain",leftGain).put("rightGain",rightGain).put("leftDelay",leftDelay).put("rightDelay",rightDelay);JSONArray order=new JSONArray(),channel=new JSONArray();for(String id:this.order)order.put(id);for(int c:this.channel)channel.put(c);j.put("order",order).put("channel",channel);
        j.put("parametric",parametric).put("convolver",convolver).put("bass",bass).put("compressor",compressor).put("crossfeed",crossfeed).put("surround",surround).put("comfort",comfort).put("protection",protection);
        j.put("bassGain",bassGain).put("bassHz",bassHz).put("threshold",threshold).put("ratio",ratio).put("attack",attack).put("release",release).put("crossfeedAmount",crossfeedAmount).put("delay",delay).put("surroundAmount",surroundAmount).put("comfortAmount",comfortAmount).put("comfortHz",comfortHz).put("ceiling",ceiling).put("outputTrim",outputTrim).put("irFile",irFile).put("irName",irName).put("irMix",irMix).put("irGain",irGain).put("irChannels",irChannels);
        JSONArray f=new JSONArray(),w=new JSONArray(),t=new JSONArray(),g=new JSONArray();for(int i=0;i<10;i++){f.put(hz[i]);w.put(q[i]);t.put(type[i]);g.put(gain[i]);}j.put("hz",f).put("q",w).put("type",t).put("gain",g).put("preampFraction",preampFraction);
    }catch(JSONException error){throw new IllegalStateException(error);}return j;}
    void validate(){for(int i=0;i<10;i++){hz[i]=clamp(hz[i],20,20000);q[i]=Double.isFinite(q[i])?Math.max(.1,Math.min(12,q[i])):1;type[i]=clamp(type[i],0,2);}bassGain=clamp(bassGain,1,6);bassHz=clamp(bassHz,40,160);threshold=clamp(threshold,-40,-6);ratio=clamp(ratio,1,10);attack=clamp(attack,1,100);release=clamp(release,40,1000);crossfeedAmount=clamp(crossfeedAmount,5,60);delay=clamp(delay,1,25);surroundAmount=clamp(surroundAmount,5,60);comfortAmount=clamp(comfortAmount,1,6);comfortHz=clamp(comfortHz,2000,6000);ceiling=clamp(ceiling,-18,-1);outputTrim=clamp(outputTrim,-18,0);irMix=clamp(irMix,0,100);irGain=clamp(irGain,-24,0);irChannels=clamp(irChannels,1,2);}
    void seed(int[] graphic){if(!gainStored){for(int i=0;i<10;i++)gain[i]=graphic[i];gainStored=true;}}
    void validatePrecision(){preampFraction=Double.isFinite(preampFraction)?Math.max(-.999,Math.min(.999,preampFraction)):0;leftGain=Double.isFinite(leftGain)?Math.max(-24,Math.min(6,leftGain)):0;rightGain=Double.isFinite(rightGain)?Math.max(-24,Math.min(6,rightGain)):0;leftDelay=clamp(leftDelay,0,200);rightDelay=clamp(rightDelay,0,200);for(int i=0;i<10;i++)gain[i]=Double.isFinite(gain[i])?Math.max(-12,Math.min(12,gain[i])):0;}
    double gain(int band,int graphic){return parametric?gain[band]:graphic;}
    int frequency(int band){return parametric?hz[band]:AudioSettings.BANDS[band];}
    String eqFilter(int band,double gain){int f=frequency(band);if(!parametric||type[band]==0)return "equalizer=f="+f+":width_type=q:width="+decimal(parametric?q[band]:1.41421356)+":g="+decimal(gain)+":precision=f64";return (type[band]==1?"bass":"treble")+"=f="+f+":width_type=q:width="+decimal(q[band])+":g="+decimal(gain)+":precision=f64";}
    String eqFilter(int band,double gain,int channels){return eqFilter(band,gain)+(channels==2&&parametric&&channel[band]!=2?":channels="+(channel[band]==0?"FL":"FR"):"");}
    boolean applies(int band,int channels){return !parametric||channels==2||channel[band]!=1;}
    void stage(String id,List<String> filters,int rate,int channels,boolean headphones){
        ArrayList<String> all=new ArrayList<>();filters(all,rate,channels,headphones);
        for(String f:all){if(id.equals("bass")&&f.startsWith("adynamicequalizer="))filters.add(f);else if(id.equals("crossfeed")&&f.startsWith("crossfeed="))filters.add(f);else if(id.equals("surround")&&f.startsWith("haas="))filters.add(f);else if(id.equals("comfort")&&(f.startsWith("equalizer=")||f.startsWith("treble=")))filters.add(f);else if(id.equals("compressor")&&f.startsWith("acompressor="))filters.add(f);}
        if(id.equals("channels")&&channelsEnabled&&channels==2){if(leftGain!=0||rightGain!=0)filters.add("pan=stereo|c0="+decimal(Math.pow(10,leftGain/20))+"*c0|c1="+decimal(Math.pow(10,rightGain/20))+"*c1");if(leftDelay!=0||rightDelay!=0)filters.add("adelay=delays="+decimal(leftDelay/10.0)+"|"+decimal(rightDelay/10.0));}
    }
    static String name(String id){switch(id){case "eq":return "Ecualizador";case "convolver":return "Impulso IR";case "bass":return "Graves dinámicos";case "crossfeed":return "Crossfeed";case "surround":return "Surround diferencial";case "comfort":return "Antifatiga";case "compressor":return "Compresor";default:return "Canales izquierdo/derecho";}}
    boolean enabled(String id,boolean eq){switch(id){case "eq":return eq;case "convolver":return convolver;case "bass":return bass;case "crossfeed":return crossfeed;case "surround":return surround;case "comfort":return comfort;case "compressor":return compressor;default:return channelsEnabled;}}
    void enable(String id,boolean value){switch(id){case "convolver":convolver=value;break;case "bass":bass=value;break;case "crossfeed":crossfeed=value;break;case "surround":surround=value;break;case "comfort":comfort=value;break;case "compressor":compressor=value;break;case "channels":channelsEnabled=value;break;}}
    void filters(List<String> filters,int rate,int channels,boolean headphones){
        if(bass)filters.add("adynamicequalizer=dfrequency="+bassHz+":tfrequency="+bassHz+":mode=boostbelow:auto=adaptive:tftype=lowshelf:ratio=1.5:range="+bassGain);
        if(headphones&&channels==2&&crossfeed)filters.add("crossfeed=strength="+decimal(crossfeedAmount/100.0)+":range=0.5:slope=0.5:level_in=1:level_out=1");
        if(headphones&&channels==2&&surround)filters.add("haas=left_delay=0:right_delay="+delay+":side_gain="+decimal(surroundAmount/100.0)+":level_in=0.85:level_out=1");
        if(headphones&&comfort){filters.add("equalizer=f="+Math.min(comfortHz,rate*.4)+":width_type=q:width=0.8:g=-"+comfortAmount+":precision=f64");if(rate>16000)filters.add("treble=f=7500:width_type=q:width=0.7:g="+decimal(-comfortAmount*.5)+":precision=f64");}
        if(compressor)filters.add("acompressor=threshold="+decimal(Math.pow(10,threshold/20.0))+":ratio="+ratio+":attack="+attack+":release="+release+":makeup=1:knee=2.828:link=maximum:detection=rms");
        if(protection&&outputTrim<0)filters.add("volume="+outputTrim+"dB:precision=double");
    }
    boolean active(boolean headphones,int channels){return bass||compressor||protection||(headphones&&(comfort||(channels==2&&(surround||crossfeed))));}
    String summary(boolean headphones,int channels){ArrayList<String> s=new ArrayList<>();if(convolver&&!irFile.isEmpty())s.add("Convolver IR");if(bass)s.add("Graves dinámicos");if(compressor)s.add("Compresor");if(headphones&&channels==2&&crossfeed)s.add("Crossfeed");if(headphones&&channels==2&&surround)s.add("Surround diferencial");if(headphones&&comfort)s.add("Antifatiga");if(protection)s.add("Límite digital");return s.isEmpty()?"Sin efectos adicionales":String.join(" · ",s);}
    static String decimal(double n){return String.format(Locale.ROOT,"%.6f",n);}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
