package local.v60.player;
import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.JSONObject;

/** Session-only gain override. No boot scripts or mixer XML edits. */
public final class DacGain {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static final Handler main=new Handler(Looper.getMainLooper());
    private static volatile int selected=-1,revision;
    private static volatile boolean dopHeld;
    static void holdForDop(boolean held){dopHeld=held;revision++;}
    private static boolean watching;
    private static volatile String last="Pulsa Consultar para leer el modo físico del DAC.";
    private static volatile long observed;
    private static volatile JSONObject snapshot;
    private static final java.util.Set<Runnable> observers=new java.util.concurrent.CopyOnWriteArraySet<>();
    static void observe(Runnable r){observers.add(r);}
    static void unobserve(Runnable r){observers.remove(r);}
    private static void changed(){main.post(()->{for(Runnable r:observers)r.run();});}
    static boolean matches(JSONObject data){int type=data.optInt("type",-1),avc=data.optInt("profileAvc",-1),master=data.optInt("profileMaster",-1);JSONObject regs=data.optJSONObject("registers");return data.optBoolean("active")&&type>=1&&type<=3&&avc>=0&&master>=0&&data.optInt("avc",-1)==avc&&data.optInt("master",-1)==master&&data.optInt("amp",-1)==(type==2?3:2)&&regs!=null&&regs.optInt("r3",-1)==0x40+avc&&regs.optInt("r46",-1)>=0&&(regs.optInt("r46",-1)&0x80)==(type==2?0:0x80);}
    public static int confirmedMode(){JSONObject data=snapshot;return AudioCapabilities.quadDac()&&RootAccess.granted()&&data!=null&&matches(data)?data.optInt("type"):-1;}
    static String name(int mode){return mode==1?"Normal":mode==2?"Alta impedancia":mode==3?"AUX":"Sin confirmar";}
    static void outputStopped(){snapshot=null;observed=0;last="Reproducción pausada o detenida · actualiza para consultar el DAC.";revision++;changed();}
    public static String description(){return observed==0?last:last+" · lectura hace "+Math.max(0,(SystemClock.elapsedRealtime()-observed)/1000)+" s";}
    static JSONObject execute(Context c,Integer mode)throws Exception{
        if(mode!=null&&dopHeld)throw new IOException("DoP mantiene AUX durante la sesión; cambia la ganancia al terminar.");
        if(!AudioCapabilities.quadDac())throw new IOException("Este control de ganancia no está verificado para este teléfono y ROM");
        if(mode!=null&&(mode<1||mode>3))throw new IOException("Modo de ganancia inválido");
        String cmd="CLASSPATH="+RootAccess.quote(c.getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin local.v60.player.DacGainCommand "+(mode==null?"status":"set "+mode);
        String output=RootAccess.run(cmd,18),json=null;for(String line:output.split("\\r?\\n"))if(line.startsWith("V60_GAIN_JSON="))json=line.substring(14);
        if(json==null)throw new IOException("Android no expuso una lectura del DAC");JSONObject data=new JSONObject(json);if(data.has("error"))throw new IOException(data.getString("error"));return data;
    }
    static String describe(JSONObject data){int type=data.optInt("type");String name=name(type);if(!data.optBoolean("active"))return "DAC en reposo · último perfil registrado: "+name;if(!matches(data))return "Sin confirmar: el perfil y la configuración del amplificador no coinciden.";return "Perfil "+name+" confirmado"+(type==2?" · HiFi2 sin sobrescritura":" · ajuste del amplificador controlado por la ROM");}
    static synchronized void watch(Context c){if(watching)return;watching=true;c.getSystemService(AudioManager.class).registerAudioDeviceCallback(new AudioDeviceCallback(){public void onAudioDevicesRemoved(AudioDeviceInfo[] devices){for(AudioDeviceInfo d:devices)if("wired".equals(AudioSettings.route(d))){selected=-1;revision++;snapshot=null;observed=0;last="Jack desconectado · selección de ganancia liberada.";changed();}}},main);}
    static void request(Context c,Integer mode,Consumer<String> done){
        Context app=c.getApplicationContext();watch(app);int version=++revision;
        worker.execute(()->{String message;JSONObject read=null;try{if(mode!=null&&!"wired".equals(AudioSettings.current(app).profile))throw new IOException("La salida activa debe ser Jack / AUX");read=execute(app,mode);message=describe(read);if(mode!=null&&version==revision&&matches(read)&&read.optInt("type")==mode)selected=mode;}catch(Exception e){message="No se pudo confirmar: "+e.getMessage();}if(version==revision){snapshot=read;last=message;observed=SystemClock.elapsedRealtime();changed();}String value=message;main.post(()->done.accept(value));});
    }
    static void outputStarted(Context c){if(dopHeld||!AudioCapabilities.quadDac()||!RootAccess.granted())return;int mode=selected,version=revision;if(mode<1)return;Context app=c.getApplicationContext();main.postDelayed(()->worker.execute(()->{if(!RootAccess.granted()||version!=revision||selected!=mode||!"wired".equals(AudioSettings.current(app).profile))return;try{JSONObject read=execute(app,mode);if(version==revision){snapshot=read;last=describe(read);observed=SystemClock.elapsedRealtime();changed();}}catch(Exception error){if(version==revision){snapshot=null;last="No se pudo mantener la ganancia: "+error.getMessage();observed=SystemClock.elapsedRealtime();changed();}}}),600);}
}
