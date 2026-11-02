package local.v60.player;
import android.content.Context;
import android.media.*;
import android.os.*;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.regex.*;
import org.json.JSONObject;
import org.xmlpull.v1.XmlPullParser;

/** Root helper restricted to the three ESS gain modes and driver transitions. */
public final class DacGainCommand {
    static String mix(String... args)throws Exception{
        String[] command=new String[args.length+1];command[0]="/system/bin/tinymix";System.arraycopy(args,0,command,1,args.length);
        java.lang.Process p=new ProcessBuilder(command).redirectErrorStream(true).start();
        try{if(!p.waitFor(3,TimeUnit.SECONDS))throw new IOException("El control ESS no respondió");
            StringBuilder text=new StringBuilder();BufferedReader r=new BufferedReader(new InputStreamReader(p.getInputStream()));String line;while((line=r.readLine())!=null&&text.length()<8192)text.append(line).append('\n');
            // Old driver can store HEADSET TYPE and return EINVAL in Bypass.
            if(p.exitValue()!=0&&args.length==1)throw new IOException("No se pudo leer "+args[0]);return text.toString();
        }finally{p.destroy();}
    }
    static int type()throws Exception{Matcher m=Pattern.compile(":\\s*([123])\\s").matcher(mix("Es9018 HEADSET TYPE"));if(!m.find())throw new IOException("Tipo ESS no reconocido");return Integer.parseInt(m.group(1));}
    static int volume(String control)throws Exception{Matcher m=Pattern.compile(":\\s*(\\d+)\\s").matcher(mix(control));if(!m.find())throw new IOException("No se pudo leer "+control);return Integer.parseInt(m.group(1));}
    static final class Profile{final int type,avc,master;Profile(int type,int avc,int master){this.type=type;this.avc=avc;this.master=master;}}
    static Profile profile(int type)throws Exception{
        String name=type==1?"headphones-hifi-dac":type==2?"headphones-hifi-dac-advanced":"headphones-hifi-dac-aux";
        File file=new File("/vendor/etc/mixer_paths.xml");if(file.length()>2097152)throw new IOException("Configuración de audio demasiado grande");
        int depth=-1,avc=-1,master=-1,headset=-1;
        try(InputStream input=new FileInputStream(file)){
            XmlPullParser xml=android.util.Xml.newPullParser();xml.setInput(input,"UTF-8");
            for(int event=xml.next();event!=XmlPullParser.END_DOCUMENT;event=xml.next()){
                if(event==XmlPullParser.START_TAG){
                    if(depth<0&&"path".equals(xml.getName())&&name.equals(xml.getAttributeValue(null,"name")))depth=xml.getDepth();
                    else if(depth>0&&xml.getDepth()==depth+1&&"ctl".equals(xml.getName())){
                        String ctl=xml.getAttributeValue(null,"name"),value=xml.getAttributeValue(null,"value");
                        if("Es9018 AVC Volume".equals(ctl))avc=Integer.parseInt(value);else if("Es9018 Master Volume".equals(ctl))master=Integer.parseInt(value);else if("Es9018 HEADSET TYPE".equals(ctl))headset=Integer.parseInt(value);
                    }
                }else if(event==XmlPullParser.END_TAG&&depth>0&&xml.getDepth()==depth)break;
            }
        }
        if(headset!=type||avc<0||avc>24||master<0||master>40)throw new IOException("La ROM no define un perfil ESS compatible");
        return new Profile(type,avc,master);
    }
    static String state()throws Exception{return mix("Es9018 State");}
    static boolean hifi(String state){return state.contains(">Hifi ");}
    static int amplifier()throws Exception{
        try(BufferedReader r=new BufferedReader(new FileReader("/sys/bus/i2c/devices/3-0048/registers"))){String line;while((line=r.readLine())!=null)if(line.startsWith("32_REGISTER")){Matcher m=Pattern.compile("0x([0-9A-Fa-f]{2})").matcher(line);if(m.find())return Integer.parseInt(m.group(1),16);}}return -1;
    }
    static JSONObject attenuation()throws Exception{
        JSONObject result=new JSONObject();try(BufferedReader r=new BufferedReader(new FileReader("/sys/bus/i2c/devices/3-0048/registers"))){String line;Pattern pattern=Pattern.compile("^([0-9]+)_REGISTER.*0x([0-9A-Fa-f]{2})");while((line=r.readLine())!=null){Matcher m=pattern.matcher(line);if(m.find()){int reg=Integer.parseInt(m.group(1));result.put("r"+reg,Integer.parseInt(m.group(2),16));}}}return result;
    }
    static void transition(int type,int avc,int master)throws Exception{
        mix("Es9018 State","Bypass");
        if(!state().contains(">Bypass "))throw new IOException("El DAC no entró en Bypass");
        // The driver stores these values in Bypass even when returning EINVAL.
        // They must be present before the new amplifier mode starts.
        mix("Es9018 AVC Volume",Integer.toString(avc));
        mix("Es9018 Master Volume",Integer.toString(master));
        if(volume("Es9018 AVC Volume")!=avc||volume("Es9018 Master Volume")!=master)throw new IOException("No se guardó la atenuación del perfil");
        mix("Es9018 HEADSET TYPE",Integer.toString(type));if(type()!=type)throw new IOException("La ROM no aceptó el modo");
        mix("Es9018 State","Hifi");if(!hifi(state()))throw new IOException("El DAC no volvió a HiFi");
    }
    public static void main(String[] args){
        JSONObject answer=new JSONObject();
        try{
            if(android.os.Process.myUid()!=0)throw new IOException("Se necesita root");
            if(Build.VERSION.SDK_INT!=33||!"lge/timelm/timelm:13/TKQ1.220829.002/2326319175c92:user/release-keys".equals(Build.FINGERPRINT))throw new IOException("Control de ganancia no verificado en esta ROM");
            if(args.length<1||!("status".equals(args[0])&&args.length==1||"set".equals(args[0])&&args.length==2))throw new IOException("Operación inválida");
            if("set".equals(args[0])){
                int target=Integer.parseInt(args[1]);if(target<1||target>3)throw new IOException("Modo inválido");
                Looper.prepareMainLooper();Class<?> thread=Class.forName("android.app.ActivityThread");Object system=thread.getMethod("systemMain").invoke(null);Context c=(Context)thread.getMethod("getSystemContext").invoke(system);
                boolean wired=false;for(AudioDeviceInfo d:c.getSystemService(AudioManager.class).getDevices(AudioManager.GET_DEVICES_OUTPUTS))if("wired".equals(AudioSettings.route(d)))wired=true;
                if(!wired)throw new IOException("Conecta el jack antes de cambiar la ganancia");
                if(!hifi(state()))throw new IOException("Reproduce una canción por el jack para cambiar la ganancia");
                int original=type(),oldAvc=volume("Es9018 AVC Volume"),oldMaster=volume("Es9018 Master Volume");Profile wanted=profile(target);
                try{if(original!=target||amplifier()!=(target==2?3:2)||oldAvc!=wanted.avc||oldMaster!=wanted.master)transition(target,wanted.avc,wanted.master);int reg=amplifier();JSONObject config=attenuation();if(reg!=(target==2?3:2)||(config.optInt("r46",-1)&0x80)!=(target==2?0:0x80)||volume("Es9018 AVC Volume")!=wanted.avc||volume("Es9018 Master Volume")!=wanted.master||config.optInt("r3",-1)!=0x40+wanted.avc)throw new IOException("El DAC no confirmó el perfil completo y su atenuación");}
                catch(Exception error){try{transition(original,oldAvc,oldMaster);}catch(Exception restore){throw new IOException(error.getMessage()+". No se pudo restaurar el modo anterior: "+restore.getMessage(),error);}throw error;}
            }
            boolean active=hifi(state());int current=type();Profile expected=profile(current);answer.put("type",current).put("active",active).put("amp",active?amplifier():-1).put("avc",volume("Es9018 AVC Volume")).put("master",volume("Es9018 Master Volume")).put("profileAvc",expected.avc).put("profileMaster",expected.master);if(active)answer.put("registers",attenuation());
        }catch(Exception e){try{answer.put("error",e.getMessage()==null?e.toString():e.getMessage());}catch(Exception ignored){}}
        System.out.println("V60_GAIN_JSON="+answer);System.out.flush();System.exit(0);
    }
}
