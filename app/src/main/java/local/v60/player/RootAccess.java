package local.v60.player;

import android.os.Looper;
import java.io.*;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/** Optional privileged transport. Never probes su on startup or from the UI thread. */
final class RootAccess {
    enum State { UNKNOWN, GRANTED, DENIED, UNAVAILABLE }
    private static volatile State state=State.UNKNOWN;
    private static volatile long checked=-10000;
    private static volatile boolean candidate;
    static boolean granted(){return state==State.GRANTED;}
    static boolean canRequest(){
        if(granted())return true;
        long now=android.os.SystemClock.elapsedRealtime();if(now-checked<10000)return candidate;
        boolean found=false;try{String path=System.getenv("PATH");if(path!=null)for(String directory:path.split(":"))if(new File(directory,"su").canExecute())found=true;
            for(String file:new String[]{"/system/bin/su","/system/xbin/su","/sbin/su","/su/bin/su"})if(new File(file).canExecute())found=true;
        }catch(SecurityException unavailable){}candidate=found;checked=now;return found;
    }
    static String description(){return granted()?"Acceso root verificado para esta sesión":state==State.DENIED?"Acceso root no autorizado o sin confirmar":canRequest()?"Acceso root opcional · aún sin autorizar":"Consulta avanzada no disponible · datos públicos de Android";}
    static synchronized String run(String command,int seconds)throws Exception{
        if(Looper.myLooper()==Looper.getMainLooper())throw new IllegalStateException("La consulta avanzada debe ejecutarse en segundo plano");
        java.lang.Process process;
        try{process=new ProcessBuilder("su","-c","if [ \"$(/system/bin/id -u)\" != 0 ]; then exit 77; fi; printf 'ZERO_ROOT_OK\\n'; "+command).redirectErrorStream(true).start();}
        catch(IOException missing){state=State.UNAVAILABLE;throw new IOException("Acceso root no disponible para la app. Se conservan las funciones normales.");}
        ByteArrayOutputStream bytes=new ByteArrayOutputStream();AtomicReference<Exception> failure=new AtomicReference<>();
        Thread reader=new Thread(()->{try(InputStream input=process.getInputStream()){byte[] buffer=new byte[8192];int count;while((count=input.read(buffer))!=-1){if(bytes.size()+count>2*1024*1024)throw new IOException("La consulta excedió el límite de lectura");bytes.write(buffer,0,count);}}catch(Exception error){failure.set(error);}},"OptionalRootRead");reader.setDaemon(true);reader.start();
        try{
            if(!process.waitFor(seconds,TimeUnit.SECONDS)){state=State.DENIED;throw new IOException("Acceso root sin confirmar. La consulta normal sigue disponible.");}
            reader.join(1500);if(reader.isAlive())throw new IOException("La consulta no terminó de leerse");if(failure.get()!=null)throw failure.get();
            String output=bytes.toString("UTF-8");String marker="ZERO_ROOT_OK\n";int start=output.indexOf(marker);
            if(start<0){state=State.DENIED;throw new IOException("Root no autorizado para esta consulta. Se muestran solo los datos disponibles.");}
            state=State.GRANTED;if(process.exitValue()!=0)throw new IOException("Android no permitió completar esta consulta avanzada.");return output.substring(start+marker.length());
        }finally{process.destroy();}
    }
    static String quote(String value){return "'"+value.replace("'","'\\''")+"'";}
}
