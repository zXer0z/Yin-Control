package local.v60.player;

import android.content.*;
import android.database.Cursor;
import android.net.Uri;
import android.os.*;
import java.util.concurrent.*;

/** Root-only bridge: binding must originate from the registered app, not bare app_process. */
public final class BluetoothBinderProvider extends ContentProvider {
    private final Object lock=new Object();private volatile IBinder binder;private ServiceConnection connection;
    public boolean onCreate(){return true;}
    @Override public Bundle call(String method,String arg,Bundle extras){
        if(Binder.getCallingUid()!=0)throw new SecurityException("Puente reservado al ayudante root");
        if("audio-status".equals(method))return YinService.publicStatus();
        if("enable-direct".equals(method)||"disable-direct".equals(method)){long identity=Binder.clearCallingIdentity();try{AudioSettings settings=AudioSettings.load(getContext(),"bluetooth");settings.direct="enable-direct".equals(method);if(settings.direct){settings.rate=96000;settings.encoding=2;}settings.save(getContext());Bundle result=new Bundle();result.putBoolean("direct",settings.direct);result.putBoolean("bridge",AudioCapabilities.directPcm());return result;}finally{Binder.restoreCallingIdentity(identity);}}
        if(!"a2dp".equals(method))throw new IllegalArgumentException("Operación no admitida");
        long identity=Binder.clearCallingIdentity();try{synchronized(lock){
            if(binder==null||!binder.isBinderAlive()){
                Context context=getContext();if(connection!=null){try{context.unbindService(connection);}catch(Exception ignored){}connection=null;}
                CountDownLatch ready=new CountDownLatch(1);
                ServiceConnection next=new ServiceConnection(){public void onServiceConnected(ComponentName name,IBinder value){binder=value;ready.countDown();}public void onServiceDisconnected(ComponentName name){binder=null;}public void onNullBinding(ComponentName name){ready.countDown();}};
                Intent intent=new Intent("android.bluetooth.IBluetoothA2dp");android.content.pm.ResolveInfo info=context.getPackageManager().resolveService(intent,0);
                if(info==null||info.serviceInfo==null)throw new IllegalStateException("Servicio A2DP no encontrado");intent.setComponent(new ComponentName(info.serviceInfo.packageName,info.serviceInfo.name));
                if(!context.bindService(intent,next,Context.BIND_AUTO_CREATE))throw new IllegalStateException("No se pudo enlazar A2DP");connection=next;
                try{if(!ready.await(3500,TimeUnit.MILLISECONDS)||binder==null)throw new IllegalStateException("A2DP no respondió");}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new IllegalStateException("Consulta cancelada");}
            }
            Bundle answer=new Bundle();answer.putBinder("a2dp",binder);return answer;
        }}finally{Binder.restoreCallingIdentity(identity);}
    }
    public Cursor query(Uri uri,String[] projection,String selection,String[] args,String sort){return null;}
    public String getType(Uri uri){return null;}
    public Uri insert(Uri uri,ContentValues values){throw new UnsupportedOperationException();}
    public int delete(Uri uri,String selection,String[] args){throw new UnsupportedOperationException();}
    public int update(Uri uri,ContentValues values,String selection,String[] args){throw new UnsupportedOperationException();}
}
