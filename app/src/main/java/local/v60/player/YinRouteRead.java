package local.v60.player;

import android.os.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** Explicit read-only diagnostic. Never assigns a system-wide ALSA stream to Yin. */
final class YinRouteRead {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static String key(Bundle b){return b.getString("session","")+":"+b.getInt("sessionId")+":"+b.getInt("deviceId")+":"+b.getInt("rate")+":"+b.getInt("encoding")+":"+b.getString("route","");}
    static boolean live(Bundle b){return b.getBoolean("ok")&&b.getBoolean("playing")&&!b.getBoolean("drained")&&b.getInt("deviceId")>0;}
    static final class Result {
        String key,thread="",devices="",format="",halRate="No expuesta",threadRate="",io="",raw="",alsa="",error="";
        long observed;
    }
    static void request(Bundle state,Consumer<Result> done){
        final Bundle captured=new Bundle(state);
        worker.execute(()->{
            Result result=new Result();result.key=key(captured);
            try{
                if(!live(captured))throw new Exception("Reproduce una canción en Yang antes de consultar.");
                String dump=RootAccess.run("dumpsys media.audio_flinger; printf '\\nYIN_ALSA_BEGIN\\n'; for f in /proc/asound/card[0-9]*/pcm[0-9]*p/sub[0-9]*/hw_params; do if [ -r \"$f\" ]; then printf '\\n%s\\n' \"$f\"; cat \"$f\"; fi; done",20);
                int split=dump.indexOf("\nYIN_ALSA_BEGIN\n");String af=split<0?dump:dump.substring(0,split);
                result.alsa=split<0?"La consulta no devolvió ALSA.":activeAlsa(dump.substring(split+16));
                parse(af,captured,result);
            }catch(Exception error){result.error=error.getMessage()==null?"Consulta no disponible":error.getMessage();}
            result.observed=SystemClock.elapsedRealtime();
            new Handler(Looper.getMainLooper()).post(()->done.accept(result));
        });
    }
    private static String field(String text,String regex,String fallback){Matcher m=Pattern.compile(regex,Pattern.MULTILINE).matcher(text);return m.find()?m.group(1).trim():fallback;}
    private static void parse(String dump,Bundle b,Result result)throws Exception{
        ArrayList<String> found=new ArrayList<>();
        Pattern row=Pattern.compile("^\\s*(?:[A-Za-z]\\w*\\s+)?\\d+\\s+(yes|no)\\s+(\\d+)(?:/\\d+)?\\s+(\\d+)\\s+.*$");
        for(String part:dump.split("(?m)(?=^Output thread )")){
            if(!part.startsWith("Output thread "))continue;
            int log=part.indexOf("  Local log:");String live=log<0?part:part.substring(0,log);boolean table=false,match=false;
            for(String line:live.split("\\r?\\n")){
                if(line.contains("Active")&&line.contains("Client")&&line.contains("Session")){table=true;continue;}
                if(table){Matcher m=row.matcher(line);if(m.matches()){if(m.group(1).equals("yes")&&Integer.parseInt(m.group(2))==android.os.Process.myPid()&&Integer.parseInt(m.group(3))==b.getInt("sessionId"))match=true;}else if(!line.trim().isEmpty()&&!line.trim().matches("[- ]+"))table=false;}
            }
            if(match&&field(live,"^  Standby: (yes|no)\\s*$","").equals("no"))found.add(live);
        }
        if(found.size()!=1)throw new Exception(found.isEmpty()?"AudioFlinger no expuso una salida activa identificable con el PID y la sesión de Yin.":"La sesión tiene varias salidas activas; no se puede afirmar una única ruta.");
        String live=found.get(0);int start=live.indexOf("  Hal stream dump:");String config=start<0?live:live.substring(0,start);
        result.thread=live.substring(0,live.indexOf('\n')<0?live.length():live.indexOf('\n'));
        result.threadRate=field(config,"^  Sample rate: (\\d+) Hz\\s*$","No expuesta");
        result.halRate=field(config,"^  HAL sample rate: (\\d+)(?: Hz)?\\s*$","No expuesta");
        result.format=field(config,"^  HAL format: (.+)$","No expuesto");
        result.devices=field(config,"^  Output devices: (.+)$","No expuestos");
        result.io=field(config,"^  I/O handle: (\\d+)\\s*$","No expuesto");
        // Keep only the matched thread's configuration and HAL dump, not other apps' tracks.
        int tracks=config.indexOf("Tracks");String header=tracks<0?config:config.substring(0,tracks);
        String hal=start<0?"":live.substring(start);int end=hal.indexOf("  AudioMixer");if(end>=0)hal=hal.substring(0,end);
        result.raw=(header+"\n"+hal);if(result.raw.length()>18000)result.raw=result.raw.substring(0,18000)+"\n…";
    }
    private static String activeAlsa(String text){StringBuilder out=new StringBuilder();for(String block:text.split("(?=/proc/asound/)")){if(!block.contains("/proc/asound/")||block.contains("closed")||!block.contains("rate:"))continue;if(out.length()<14000)out.append(block.trim()).append("\n\n");}return out.length()==0?"No se expusieron parámetros ALSA activos y legibles.":out.toString().trim();}
}
