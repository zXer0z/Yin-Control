package local.v60.player;

import android.content.*;
import android.net.Uri;
import android.os.*;
import android.provider.OpenableColumns;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.concurrent.*;
import java.util.function.Consumer;
import java.util.regex.*;

/** User-selected local files only; validated imports never overwrite an active IR. */
final class EffectImports {
    private static final ExecutorService worker=Executors.newSingleThreadExecutor();
    static File ir(Context c,DspSettings d){try{File dir=new File(c.getFilesDir(),"impulses"),file=new File(d.irFile);return !d.irFile.isEmpty()&&file.isFile()&&file.getCanonicalPath().startsWith(dir.getCanonicalPath()+File.separator)?file:null;}catch(Exception e){return null;}}
    static void read(Context context,Uri uri,String profile,String key,boolean impulse,Consumer<String> done){Context c=context.getApplicationContext();worker.execute(()->{
        String message;File staged=null,converted=null;boolean saved=false;
        try{
            String name="Archivo";try(android.database.Cursor cursor=c.getContentResolver().query(uri,new String[]{OpenableColumns.DISPLAY_NAME},null,null,null)){if(cursor!=null&&cursor.moveToFirst())name=cursor.getString(0);}
            staged=File.createTempFile("yin-effect-",".input",c.getCacheDir());long cap=impulse?16*1024*1024:128*1024,total=0;
            try(InputStream in=c.getContentResolver().openInputStream(uri);OutputStream out=new FileOutputStream(staged)){if(in==null)throw new IOException("No se pudo abrir el archivo");byte[] bytes=new byte[16384];int n;while((n=in.read(bytes))>=0){total+=n;if(total>cap)throw new IOException(impulse?"El IR supera 16 MB":"El preset supera 128 KB");out.write(bytes,0,n);}}
            AudioSettings settings=AudioSettings.loadKey(c,profile,key);
            if(impulse){
                MediaInformation probe=FFprobeKit.getMediaInformation(staged.getAbsolutePath()).getMediaInformation();
                if(probe==null||probe.getFormat()==null||!probe.getFormat().contains("wav"))throw new IOException("Usa un impulso WAV o IRS con contenido WAV");
                org.json.JSONArray streams=probe.getAllProperties().optJSONArray("streams");org.json.JSONObject audio=null;if(streams!=null)for(int i=0;i<streams.length();i++){org.json.JSONObject s=streams.optJSONObject(i);if(s!=null&&"audio".equals(s.optString("codec_type"))){audio=s;break;}}
                double duration=Double.parseDouble(probe.getDuration());int ch=audio==null?0:audio.optInt("channels"),rate=audio==null?0:audio.optInt("sample_rate");
                if(!Double.isFinite(duration)||duration<=0||duration>2||ch<1||ch>2||rate<8000||rate>384000)throw new IOException("IR admitido: mono/estéreo, hasta 2 segundos y 384 kHz");
                File dir=new File(c.getFilesDir(),"impulses");if(!dir.isDirectory()&&!dir.mkdirs())throw new IOException("No se pudo guardar el IR");converted=new File(dir,UUID.randomUUID()+".wav");
                FFmpegSession result=FFmpegKit.executeWithArguments(new String[]{"-nostdin","-v","error","-i",staged.getAbsolutePath(),"-map","0:a:0","-vn","-sn","-dn","-t","2","-c:a","pcm_f32le","-f","wav","-y",converted.getAbsolutePath()});
                if(!ReturnCode.isSuccess(result.getReturnCode())||converted.length()<44)throw new IOException("No se pudo preparar el impulso");
                // Re-read preferences after native conversion, preserving edits made meanwhile.
                settings=AudioSettings.loadKey(c,profile,key);settings.dsp.irFile=converted.getAbsolutePath();settings.dsp.irName=name;settings.dsp.irChannels=ch;settings.dsp.convolver=true;settings.save(c);saved=true;message="IR cargado: "+name;
            }else{
                String text=new String(java.nio.file.Files.readAllBytes(staged.toPath()),StandardCharsets.UTF_8);
                Pattern filter=Pattern.compile("(?im)^\\s*Filter\\s*\\d+\\s*:\\s*ON\\s+(PK|LSC?|HSC?)\\s+Fc\\s+([0-9.]+)\\s*Hz\\s+Gain\\s+([+-]?[0-9.]+)\\s*dB\\s+Q\\s+([0-9.]+)\\s*$");
                Matcher match=filter.matcher(text);int count=0;Arrays.fill(settings.gains,0);Arrays.fill(settings.dsp.gain,0);
                while(match.find()){if(count==10)throw new IOException("Este EQ admite hasta 10 bandas");double hz=Double.parseDouble(match.group(2)),gain=Double.parseDouble(match.group(3)),q=Double.parseDouble(match.group(4));if(hz<20||hz>20000||gain<-12||gain>12||q<.1||q>12)throw new IOException("El preset contiene valores fuera del rango del EQ");settings.dsp.hz[count]=(int)Math.round(hz);settings.gains[count]=(int)Math.round(gain);settings.dsp.gain[count]=gain;settings.dsp.q[count]=q;settings.dsp.type[count]=match.group(1).startsWith("L")?1:match.group(1).startsWith("H")?2:0;count++;}
                if(count==0)throw new IOException("Usa texto AutoEQ / Equalizer APO con bandas PK, LS o HS y Q");
                Matcher all=Pattern.compile("(?im)^\\s*Filter\\s*\\d+\\s*:\\s*ON\\b").matcher(text);int expected=0;while(all.find())expected++;if(expected!=count)throw new IOException("Hay filtros que no se pueden importar; no se aplicó un preset parcial");
                Matcher pre=Pattern.compile("(?im)^\\s*Preamp:\\s*([+-]?[0-9.]+)\\s*dB").matcher(text);if(pre.find()){double gain=Double.parseDouble(pre.group(1));if(!Double.isFinite(gain)||gain<-24||gain>12)throw new IOException("Preamplificador fuera de rango");settings.preamp=(int)gain;settings.dsp.preampFraction=gain-settings.preamp;}
                settings.dsp.gainStored=true;settings.dsp.parametricEdited=true;settings.dsp.parametric=true;settings.eq=true;settings.save(c);message="Corrección importada · "+count+" bandas · decimales conservados";
            }
        }catch(Exception error){message=error.getMessage()==null?"No se pudo importar el archivo":error.getMessage();}
        finally{if(staged!=null)staged.delete();if(converted!=null&&!saved)converted.delete();}
        String answer=message;new Handler(Looper.getMainLooper()).post(()->done.accept(answer));
    });}
}
