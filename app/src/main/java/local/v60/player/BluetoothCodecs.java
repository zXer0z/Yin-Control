package local.v60.player;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import org.json.*;

/** User-triggered root operations; no background polling or persistent system changes. */
public final class BluetoothCodecs {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    private static volatile Snapshot last;
    public static final class Snapshot {
        public final JSONObject data;
        public final long observed=SystemClock.elapsedRealtime();
        Snapshot(JSONObject data){this.data=data;}
        public String description(){
            JSONObject codec=data.optJSONObject("current");if(codec==null)return "Códec no informado";
            return codec.optString("name","Desconocido")+" · "+rate(codec.optInt("rate"))+" · "+bits(codec.optInt("bits"));
        }
        public String device(){return data.optString("device","Auriculares Bluetooth");}
        String mixerDescription(){JSONObject mixer=data.optJSONObject("mixer");return mixer==null?"Ruta PCM Bluetooth de esta app: sin sesión activa medida":"Ruta PCM Bluetooth de esta app · "+(mixer.optBoolean("direct")?"DIRECT":"mezclador")+": "+AudioSignalInfo.frequency(mixer.optInt("rate"))+" · "+mixer.optString("format")+"\nEl PCM y la configuración del códec son etapas distintas.";}
        public int type(){return data.optJSONObject("current")==null?-1:data.optJSONObject("current").optInt("type",-1);}
    }
    public static Snapshot latest(){return RootAccess.granted()?last:null;}
    static int frequency(int mask){int[] values={44100,48000,88200,96000,176400,192000,16000,24000};return Integer.bitCount(mask)==1&&Integer.numberOfTrailingZeros(mask)<values.length?values[Integer.numberOfTrailingZeros(mask)]:0;}
    public static String signalDescription(){
        Snapshot s=latest();if(s==null)return "Códec Bluetooth negociado: no disponible para la app. La reproducción Bluetooth sigue disponible.";
        long seconds=Math.max(0,(SystemClock.elapsedRealtime()-s.observed)/1000);
        return "Última consulta Bluetooth: "+s.device()+"\n"+s.description()+"\n"+s.mixerDescription()+"\nHace "+seconds+" s; puede cambiar al reconectar. Actualiza en Sonido.";
    }
    static String rate(int mask){
        int[] values={44100,48000,88200,96000,176400,192000,16000,24000};
        if(Integer.bitCount(mask)!=1)return "frecuencia no fijada";
        for(int i=0;i<values.length;i++)if(mask==(1<<i))return String.format(java.util.Locale.ROOT,"%s kHz",values[i]/1000.0);
        return "frecuencia no reconocida";
    }
    static String bits(int mask){return mask==1?"16 bits":mask==2?"24 bits":mask==4?"32 bits":"profundidad no informada";}
    public static JSONObject execute(Context c,Integer type,String address)throws Exception{
        if(type!=null&&(type<0||type>255||address==null||!address.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}")))throw new IOException("Selección Bluetooth inválida.");
        JSONObject data;
        try{data=command(c,type==null?"status":"set "+type+" "+RootAccess.quote(address));}
        catch(Exception unavailable){if(type!=null)throw unavailable;data=fromDump(RootAccess.run("dumpsys bluetooth_manager",12));}
        if(type==null){try{JSONObject mixer=mixer(RootAccess.run("dumpsys media.audio_flinger",10),android.os.Process.myPid());if(mixer!=null)data.put("mixer",mixer);}catch(Exception unavailable){}
            android.media.AudioDeviceInfo device=AudioSettings.predictedDevice(c);if(device!=null&&"bluetooth".equals(AudioSettings.route(device))&&device.getAddress().equalsIgnoreCase(data.optString("address")))data.put("device",device.getProductName().toString());
        }return data;
    }
    private static JSONObject command(Context c,String operation)throws Exception{
        String command="CLASSPATH="+RootAccess.quote(c.getApplicationInfo().sourceDir)+" /system/bin/app_process /system/bin local.v60.player.BluetoothCodecCommand "+operation;
        String output=RootAccess.run(command,25),json=null;for(String line:output.split("\\r?\\n"))if(line.startsWith("V60_CODEC_JSON="))json=line.substring(15);
        if(json==null)throw new IOException("Android no expuso el códec negociado.");JSONObject answer=new JSONObject(json);if(answer.has("error"))throw new IOException(answer.getString("error"));return answer;
    }
    /** Read only the active state machine's live fields, never history or another headset. */
    private static JSONObject fromDump(String dump)throws Exception{
        int start=dump.indexOf("Profile: A2dpService");if(start<0)throw new IOException("Android no expuso el estado A2DP.");String section=dump.substring(start);int end=section.indexOf("\nProfile:");if(end>=0)section=section.substring(0,end);
        java.util.regex.Matcher active=java.util.regex.Pattern.compile("(?m)^\\s*mActiveDevice:\\s*([0-9A-Fa-f:]{17})\\s*$").matcher(section);
        if(!active.find())throw new IOException("No hay un dispositivo A2DP activo.");String address=active.group(1).toUpperCase(java.util.Locale.ROOT);
        java.util.regex.Matcher machine=java.util.regex.Pattern.compile("(?m)^\\s*=== A2dpStateMachine for "+java.util.regex.Pattern.quote(address)+"(?: \\(Active\\))? ===\\s*$",java.util.regex.Pattern.CASE_INSENSITIVE).matcher(section);
        if(!machine.find())throw new IOException("No se pudo aislar el auricular activo.");String block=section.substring(machine.end());int next=block.indexOf("=== A2dpStateMachine");if(next>=0)block=block.substring(0,next);int history=block.indexOf("StateMachine:");if(history>=0)block=block.substring(0,history);
        if(!block.contains("mConnectionState: CONNECTED,"))throw new IOException("El dispositivo A2DP activo no está conectado.");
        java.util.regex.Matcher current=java.util.regex.Pattern.compile("(?m)^\\s*mCodecConfig:\\s*(\\{[^}]+\\})").matcher(block);if(!current.find())throw new IOException("Códec activo no informado.");
        JSONObject codec=dumpCodec(current.group(1));JSONArray choices=new JSONArray();int selectable=block.indexOf("mCodecsSelectableCapabilities:");
        if(selectable>=0){java.util.regex.Matcher candidates=java.util.regex.Pattern.compile("(?m)^\\s*(\\{codecName:[^}]+\\})\\s*$").matcher(block.substring(selectable));while(candidates.find())choices.put(dumpCodec(candidates.group(1)));}
        return new JSONObject().put("device","Auriculares Bluetooth").put("address",address).put("current",codec).put("available",choices).put("source","Estado A2DP del sistema");
    }
    private static JSONObject dumpCodec(String text)throws Exception{
        JSONObject codec=new JSONObject();java.util.regex.Matcher name=java.util.regex.Pattern.compile("codecName:([^,}]+)").matcher(text);codec.put("name",name.find()?name.group(1):"Códec Android");
        String[][] fields={{"type","mCodecType"},{"rate","mSampleRate"},{"bits","mBitsPerSample"},{"channels","mChannelMode"}};
        for(String[] field:fields){java.util.regex.Matcher number=java.util.regex.Pattern.compile(field[1]+":(0x[0-9a-fA-F]+|[0-9]+)").matcher(text);if(!number.find())throw new IOException("Formato A2DP incompleto.");codec.put(field[0],Integer.decode(number.group(1)));}return codec;
    }
    private static JSONObject mixer(String dump,int client)throws Exception{
        java.util.regex.Matcher threads=java.util.regex.Pattern.compile("(?m)^Output thread [^\\n]+\\n").matcher(dump);java.util.ArrayList<Integer> starts=new java.util.ArrayList<>();while(threads.find())starts.add(threads.start());JSONObject found=null;
        for(int i=0;i<starts.size();i++){String block=dump.substring(starts.get(i),i+1<starts.size()?starts.get(i+1):dump.length());java.util.regex.Matcher device=java.util.regex.Pattern.compile("(?m)^  Output devices: [^\\n]*AUDIO_DEVICE_OUT_BLUETOOTH_A2DP[^\\n]*$").matcher(block);if(!device.find())continue;
            if(!java.util.regex.Pattern.compile("(?m)^  Standby: no\\s*$").matcher(block).find()||!java.util.regex.Pattern.compile("(?m)^\\s*(?:\\S+\\s+)?[0-9]+\\s+yes\\s+"+client+"\\s+").matcher(block).find())continue;
            java.util.regex.Matcher rate=java.util.regex.Pattern.compile("(?m)^  Sample rate: ([0-9]+) Hz\\s*$").matcher(block),format=java.util.regex.Pattern.compile("(?m)^  HAL format: [^(]+\\(([^)]+)\\)\\s*$").matcher(block);if(!rate.find()||!format.find())continue;
            JSONObject candidate=new JSONObject().put("rate",Integer.parseInt(rate.group(1))).put("format",format.group(1).replace("AUDIO_FORMAT_PCM_","").replace("_BIT"," bits").replace('_',' ')).put("direct",block.substring(0,block.indexOf('\n')).contains("(DIRECT)"));
            if(found!=null&&(found.optInt("rate")!=candidate.optInt("rate")||!found.optString("format").equals(candidate.optString("format"))))return null;found=candidate;
        }return found;
    }
    static void maximum(Context context,String address,Consumer<String> completed){
        Context app=context.getApplicationContext();watch(app);Handler main=new Handler(Looper.getMainLooper());
        worker.execute(()->{String message;last=null;try{
            if(address==null||!address.matches("[0-9A-F]{2}(:[0-9A-F]{2}){5}"))throw new IOException("Vuelve a consultar los auriculares activos.");
            JSONObject requested=command(app,"max "+RootAccess.quote(address)).getJSONObject("preference");
            SystemClock.sleep(1800);JSONObject confirmed=execute(app,null,null);Snapshot snapshot=new Snapshot(confirmed);
            JSONObject codec=confirmed.optJSONObject("current");boolean accepted=address.equals(confirmed.optString("address"))&&codec!=null&&codec.optInt("type")==4&&codec.optInt("rate")==requested.optInt("rate")&&codec.optInt("bits")==requested.optInt("bits");
            confirmed.put("maximumConfirmed",accepted);
            last=snapshot;
            if(accepted){AudioSettings settings=AudioSettings.load(app,"bluetooth");settings.rate=frequency(codec.optInt("rate"));settings.encoding=codec.optInt("bits")==1?1:codec.optInt("bits")==2?2:3;settings.save(app);message="LDAC confirmado: "+snapshot.description()+". PCM de la app adaptado; el mezclador de Android puede remuestrear.";}
            else message="Android no confirmó la configuración solicitada. Lectura actual: "+snapshot.description()+". No se modificó el formato PCM de la app.";
        }catch(Exception error){message=error.getMessage()==null?"No se pudo solicitar LDAC.":error.getMessage();}final String answer=message;main.post(()->completed.accept(answer));});
    }
    private static boolean watching;
    private static synchronized void watch(Context c){if(watching)return;watching=true;c.getSystemService(android.media.AudioManager.class).registerAudioDeviceCallback(new android.media.AudioDeviceCallback(){public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] devices){last=null;}public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] devices){last=null;}},new Handler(Looper.getMainLooper()));}
    static void request(Context c,Integer type,String address,Consumer<String> completed){
        Context app=c.getApplicationContext();watch(app);Handler main=new Handler(Looper.getMainLooper());
        worker.execute(()->{
            String message;
            try{
                JSONObject result=execute(app,type,address);
                if(type==null){last=new Snapshot(result);message="Datos leídos de Android.";}
                else{
                    SystemClock.sleep(1800);JSONObject confirmed=execute(app,null,null);last=new Snapshot(confirmed);
                    message=last.type()==type&&address.equals(confirmed.optString("address"))?"Códec activo confirmado: "+last.description():"Android mantuvo otra configuración: "+last.description()+". La selección no se confirmó.";
                }
            }catch(Exception e){last=null;message=e.getMessage()==null?"No se pudo consultar Bluetooth.":e.getMessage();}
            final String value=message;main.post(()->completed.accept(value));
        });
    }
}
