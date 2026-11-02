package local.v60.player;

import android.content.*;
import android.media.*;
import android.os.Build;
import java.util.*;
import org.json.*;

/** Validated per-route preferences and deterministic DSP plan. */
public final class AudioSettings {
    public static final int[] BANDS={31,63,125,250,500,1000,2000,4000,8000,16000};
    public static final String[] PROFILES={"speaker","wired","bluetooth","usb"};
    public static final String[] PROFILE_NAMES={"Altavoz","Jack / AUX","Bluetooth","USB"};
    public static final int[] RATES={0,32000,44100,48000,64000,88200,96000,128000,176400,192000,352800,384000};
    public static final int[] BUFFERS={100,200,400,800};
    public static final int MAX_ENGINE_RATE=192000;
    public int rate=0, encoding=0, bufferMs=200, dsdRate=176400, resampler=0, preamp=0, replayGain=0, balance=0;
    public boolean eq=false, headroom=true, limiter=true, direct=false, dop=false;
    String compatibilityNote="";
    public final int[] gains=new int[10];
    public final String profile;
    public String storageKey;
    DspSettings dsp=new DspSettings();
    public AudioSettings(String profile){this.profile=Arrays.asList(PROFILES).contains(profile)?profile:"speaker";storageKey=this.profile;}
    public static AudioAttributes attributes(){return new AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_MUSIC).build();}
    public static AudioDeviceInfo predictedDevice(Context context){
        AudioManager manager=context.getSystemService(AudioManager.class);
        if(Build.VERSION.SDK_INT>=33){try{List<AudioDeviceInfo> devices=manager.getAudioDevicesForAttributes(attributes());if(!devices.isEmpty())return devices.get(0);}catch(Exception ignored){}}
        // Android 12 cannot predict the media route through the newer API.
        // Connected outputs are a hint only; playback reports its actual routed device.
        try{AudioDeviceInfo best=null;int rank=0;for(AudioDeviceInfo device:manager.getDevices(AudioManager.GET_DEVICES_OUTPUTS)){String route=route(device);int priority=route.equals("wired")?3:route.equals("usb")?4:route.equals("bluetooth")?2:0;if(priority>rank){best=device;rank=priority;}}return best;}catch(RuntimeException unavailable){return null;}
    }
    public static String route(AudioDeviceInfo device){
        if(device==null)return "speaker";
        switch(device.getType()){
            case AudioDeviceInfo.TYPE_BLUETOOTH_A2DP:case AudioDeviceInfo.TYPE_BLE_HEADSET:case AudioDeviceInfo.TYPE_BLE_SPEAKER:return "bluetooth";
            case AudioDeviceInfo.TYPE_WIRED_HEADPHONES:case AudioDeviceInfo.TYPE_WIRED_HEADSET:case AudioDeviceInfo.TYPE_LINE_ANALOG:return "wired";
            case AudioDeviceInfo.TYPE_USB_DEVICE:case AudioDeviceInfo.TYPE_USB_HEADSET:case AudioDeviceInfo.TYPE_USB_ACCESSORY:return "usb";
            default:return "speaker";
        }
    }
    public static String name(String profile){int i=Arrays.asList(PROFILES).indexOf(profile);return i>=0?PROFILE_NAMES[i]:"Altavoz";}
    public static AudioSettings current(Context context){return load(context,route(predictedDevice(context))).forPlayback();}
    AudioSettings copy(){
        AudioSettings s=new AudioSettings(profile);s.storageKey=storageKey;s.dsp=dsp.copy();s.rate=rate;s.encoding=encoding;s.bufferMs=bufferMs;s.dsdRate=dsdRate;s.resampler=resampler;s.preamp=preamp;s.replayGain=replayGain;s.balance=balance;s.eq=eq;s.headroom=headroom;s.limiter=limiter;s.direct=direct;s.dop=dop;s.compatibilityNote=compatibilityNote;System.arraycopy(gains,0,s.gains,0,gains.length);return s;
    }
    void compatibility(String note){compatibilityNote+=(compatibilityNote.isEmpty()?"":"\n")+note;}
    AudioSettings forPlayback(){
        AudioSettings s=copy();
        if(s.direct&&!AudioCapabilities.directPcm()){s.direct=false;s.compatibility("PCM directo no disponible: se usa AudioTrack estándar.");}
        if(s.dop&&!AudioCapabilities.dop()){s.dop=false;s.compatibility("DoP sin ruta validada: los archivos DSD se convierten a PCM.");}
        if(s.rate>0&&!s.acceptsRate(s.rate)){s.rate=0;s.compatibility("Frecuencia guardada no disponible: se adapta la salida al motor.");}
        if(s.direct&&s.encoding==0){s.encoding=2;s.compatibility("PCM directo solicita entero 24 bits; Float32 se conserva como preferencia guardada.");}
        if(s.rate>MAX_ENGINE_RATE&&s.encoding!=3){s.encoding=3;s.compatibility("La ruta de alta frecuencia solicita un contenedor entero de 32 bits.");}
        if(s.rate==352800)s.compatibility("PCM 352,8 kHz experimental: esta combinación fue rechazada anteriormente. La salida compatible se mostrará si Android no la acepta.");
        if(s.dsdRate!=88200&&s.dsdRate!=176400)s.dsdRate=176400;
        s.validate();
        return s;
    }
    public static AudioSettings load(Context context,String profile){
        return loadKey(context,profile,DeviceProfiles.key(context,profile));
    }
    static String signature(Context context,AudioSettings s){android.content.SharedPreferences p=context.getSharedPreferences("audio_settings",0);return p.getString(s.storageKey,p.getString(s.profile,null));}
    static AudioSettings loadKey(Context context,String profile,String key){
        AudioSettings s=new AudioSettings(profile);s.storageKey=key;String raw=signature(context,s);
        if(raw!=null)try{
            JSONObject j=new JSONObject(raw);s.rate=j.optInt("rate",0);s.encoding=j.optInt("encoding",0);s.bufferMs=j.optInt("bufferMs",200);
            s.dsdRate=j.optInt("dsdRate",176400);s.resampler=j.optInt("resampler",0);s.preamp=j.optInt("preamp",0);s.replayGain=j.optInt("replayGain",0);
            s.balance=j.optInt("balance",0);s.eq=j.optBoolean("eq",false);s.headroom=j.optBoolean("headroom",true);s.limiter=j.optBoolean("limiter",true);s.direct=j.optBoolean("direct",false);s.dop=j.optBoolean("dop",false);
            JSONArray gains=j.optJSONArray("gains");if(gains!=null)for(int i=0;i<10;i++)s.gains[i]=gains.optInt(i,0);
            s.dsp=DspSettings.read(j.optJSONObject("dsp"));
            s.dsp.seed(s.gains);
        }catch(JSONException ignored){}
        s.validate();return s;
    }
    public void save(Context context){
        validate();try{JSONObject j=new JSONObject();j.put("rate",rate).put("encoding",encoding).put("bufferMs",bufferMs).put("dsdRate",dsdRate)
                .put("resampler",resampler).put("preamp",preamp).put("replayGain",replayGain).put("balance",balance).put("eq",eq).put("headroom",headroom).put("limiter",limiter).put("direct",direct).put("dop",dop);
            JSONArray array=new JSONArray();for(int value:gains)array.put(value);j.put("gains",array);
            j.put("dsp",dsp.json());context.getSharedPreferences("audio_settings",0).edit().putString(storageKey,j.toString()).apply();
        }catch(JSONException error){throw new IllegalStateException(error);}
    }
    public void validate(){
        dsp.validate();dsp.validatePrecision();
        if(!"wired".equals(profile)){if(!"bluetooth".equals(profile))direct=false;dop=false;}
        if(!contains(RATES,rate))rate=0;if(!contains(BUFFERS,bufferMs))bufferMs=200;
        if(dsdRate!=88200&&dsdRate!=176400)dsdRate=176400;
        encoding=clamp(encoding,0,3);
        if("speaker".equals(profile)){
            direct=false;dop=false;
            rate=48000;
            encoding=1;
        }else if("bluetooth".equals(profile)){
            dop=false;
            if(direct){
                rate=96000;
                encoding=2;
            }else{
                if(rate>96000)rate=96000;
                if(encoding!=1&&encoding!=2)encoding=2;
            }
        }
        resampler=clamp(resampler,0,1);preamp=clamp(preamp,-24,12);replayGain=clamp(replayGain,0,2);balance=clamp(balance,-100,100);
        for(int i=0;i<gains.length;i++)gains[i]=clamp(gains[i],-12,12);
    }
    /** This exact LG policy redirects PCM16 DIRECT into DEEP_BUFFER. Keep the
     * user's preference; use a wider integer container in every PCM producer. */
    public boolean needsWideContainer(){return direct&&!dop&&"wired".equals(profile)&&encoding==1
            &&Build.VERSION.SDK_INT==33&&"lge/timelm/timelm:13/TKQ1.220829.002/2326319175c92:user/release-keys".equals(Build.FINGERPRINT);}
    public int outputEncodingIndex(){return needsWideContainer()?2:encoding;}
    public int androidEncoding(){return new int[]{AudioFormat.ENCODING_PCM_FLOAT,AudioFormat.ENCODING_PCM_16BIT,AudioFormat.ENCODING_PCM_24BIT_PACKED,AudioFormat.ENCODING_PCM_32BIT}[outputEncodingIndex()];}
    public int bytesPerSample(){return new int[]{4,2,3,4}[outputEncodingIndex()];}
    public String pcmCodec(){return new String[]{"pcm_f32le","pcm_s16le","pcm_s24le","pcm_s32le"}[outputEncodingIndex()];}
    public String pcmMuxer(){return new String[]{"f32le","s16le","s24le","s32le"}[outputEncodingIndex()];}
    public static String encodingName(int encoding){switch(encoding){case AudioFormat.ENCODING_PCM_16BIT:return "entero 16 bits";case AudioFormat.ENCODING_PCM_24BIT_PACKED:return "entero 24 bits";case AudioFormat.ENCODING_PCM_32BIT:return "entero 32 bits";default:return "float 32 bits";}}
    public int outputRate(int source,boolean dsd){
        int requested=rate!=0?rate:dsd?dsdRate:source;
        int resolved=requested;
        int maxAllowed="speaker".equals(profile)?48000:"bluetooth".equals(profile)?96000:MAX_ENGINE_RATE;
        while(!acceptsRate(resolved)&&resolved>maxAllowed&&resolved>=88200)resolved/=2;
        if(!acceptsRate(resolved)&&resolved>maxAllowed)resolved=maxAllowed;
        if(resolved!=requested)compatibility("Origen a "+AudioSignalInfo.frequency(requested)+": conversión a "+AudioSignalInfo.frequency(resolved)+" en la app.");
        if(resolved>MAX_ENGINE_RATE&&encoding!=3){encoding=3;compatibility("Alta frecuencia: el motor solicita PCM entero de 32 bits.");}
        return resolved;
    }
    public boolean acceptsHighRate(){return direct&&"wired".equals(profile)&&AudioCapabilities.highRate();}
    public boolean acceptsRate(int value){
        if(value==0)return true;
        if("speaker".equals(profile))return value==48000;
        if("bluetooth".equals(profile))return value<=96000;
        return value<=MAX_ENGINE_RATE||((value==352800||value==384000)&&acceptsHighRate());
    }
    public String outputProblem(int resolvedRate){
        List<String> problems=new ArrayList<>();
        if(direct&&encoding==0)problems.add("La salida directa de este motor necesita Entero 16, 24 o 32 bits. Float solo funciona en la salida normal.");
        if(!acceptsRate(resolvedRate))problems.add("Esta frecuencia no está habilitada para la conexión y el motor elegidos. Por encima de 192 kHz necesitas Jack / AUX y el puente de alta frecuencia activo.");
        if(resolvedRate>MAX_ENGINE_RATE&&encoding!=3)problems.add("Para PCM a 352,8/384 kHz este motor requiere Entero 32 bits. El contenedor de 32 bits no garantiza 32 bits de resolución física.");
        return String.join("\n\n",problems);
    }
    public static final class Plan {
        public String filter="", replayDescription="ReplayGain desactivado";
        public double gainDb;
        public double headroomDb;
        public boolean limiting,equalizing;
        public String effects="";
        public String beforeIr="",afterIr="";
    }
    public Plan plan(int inputRate,int outputRate,int channels,JSONObject streamTags,JSONObject formatTags){
        validate();Plan plan=new Plan();List<String> filters=new ArrayList<>();
        if(inputRate!=outputRate)filters.add("aresample="+outputRate+(resampler==1?":resampler=soxr:precision=28":":resampler=swr:filter_size=64:phase_shift=10:cutoff=0.97"));
        double rg=0;
        if(replayGain!=0){
            String kind=replayGain==2?"album":"track";
            Double gain=parseGain(tag(streamTags,formatTags,"replaygain_"+kind+"_gain"));
            if(gain==null&&replayGain==2){gain=parseGain(tag(streamTags,formatTags,"replaygain_track_gain"));kind="track";}
            if(gain!=null){rg=Math.max(-30,Math.min(20,gain));plan.replayDescription="ReplayGain "+(kind.equals("album")?"álbum":"pista")+": "+decimal(rg)+" dB";}
            else plan.replayDescription="ReplayGain: sin etiqueta; ganancia 0 dB";
        }
        double maximum=0;boolean equalizing=false;
        if(eq)for(int i=0;i<10;i++)if(dsp.applies(i,channels)&&dsp.frequency(i)<outputRate/2&&dsp.gain(i,gains[i])!=0){maximum=Math.max(maximum,dsp.gain(i,gains[i]));equalizing=true;}
        plan.headroomDb=headroom?((eq?maximum:0)+(dsp.bass?dsp.bassGain:0)+(dsp.convolver?3:0)+(dsp.channelsEnabled?Math.max(0,Math.max(dsp.leftGain,dsp.rightGain)):0)):0;plan.equalizing=equalizing;
        plan.gainDb=preamp+dsp.preampFraction+rg-plan.headroomDb;
        // Attenuate before EQ so positive bands have headroom in floating point.
        if(Math.abs(plan.gainDb)>.00001)filters.add("volume="+decimal(plan.gainDb)+"dB:precision=double");
        boolean headphones=!"speaker".equals(profile);List<String> tail=new ArrayList<>(),current=filters;List<String> active=new ArrayList<>();
        for(String stage:dsp.order){if(stage.equals("convolver")){current=tail;if(dsp.convolver)active.add(DspSettings.name(stage));continue;}int before=current.size();if(stage.equals("eq")){if(eq)for(int i=0;i<10;i++)if(dsp.applies(i,channels)&&dsp.frequency(i)<outputRate/2&&dsp.gain(i,gains[i])!=0)current.add(dsp.eqFilter(i,dsp.gain(i,gains[i]),channels));}else dsp.stage(stage,current,outputRate,channels,headphones);if(current.size()>before)active.add(DspSettings.name(stage));}plan.effects=active.isEmpty()?"Sin efectos adicionales":String.join(" → ",active);
        if(dsp.protection&&dsp.outputTrim<0)tail.add("volume="+dsp.outputTrim+"dB:precision=double");
        if(channels==2&&balance!=0){double left=balance>0?1-balance/100.0:1,right=balance<0?1+balance/100.0:1;
            tail.add("pan=stereo|c0="+decimal(left)+"*c0|c1="+decimal(right)+"*c1");}
        plan.limiting=dsp.protection||(limiter&&(equalizing||dsp.convolver||dsp.channelsEnabled||dsp.active(headphones,channels)||Math.abs(plan.gainDb)>.00001||(channels==2&&balance!=0)));
        if(plan.limiting)tail.add("alimiter=limit="+(dsp.protection?DspSettings.decimal(Math.pow(10,dsp.ceiling/20.0)):"0.95")+":level=0:attack=5:release=50:latency=1");
        plan.beforeIr=String.join(",",filters);plan.afterIr=String.join(",",tail);filters.addAll(tail);plan.filter=String.join(",",filters);return plan;
    }
    private static String tag(JSONObject a,JSONObject b,String name){for(JSONObject object:new JSONObject[]{a,b})if(object!=null){Iterator<String> keys=object.keys();while(keys.hasNext()){String key=keys.next();if(key.equalsIgnoreCase(name))return object.optString(key,null);}}return null;}
    private static Double parseGain(String value){if(value==null)return null;try{double d=Double.parseDouble(value.trim().replaceAll("(?i)\\s*dB$", ""));return Double.isFinite(d)?d:null;}catch(Exception ignored){return null;}}
    private static String decimal(double value){return String.format(Locale.ROOT,"%.5f",value);}
    private static boolean contains(int[] values,int v){for(int n:values)if(n==v)return true;return false;}
    private static int clamp(int v,int min,int max){return Math.max(min,Math.min(max,v));}
}
