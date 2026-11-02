package local.v60.player;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import com.yang.bridge.YinProtocol;
import java.security.MessageDigest;
import java.util.Arrays;
import java.util.concurrent.Executors;

/** Root audio broker. Exported operations authenticate both package and signing certificate. */
public final class YinService extends Service {
    private static volatile YinService instance;
    private volatile PcmSession session;
    private volatile boolean checkingRoot;
    private volatile boolean started;
    private volatile String message="Yin en espera de Yang";
    private PowerManager.WakeLock wake;
    private final Object sessionLock=new Object();
    private final Handler main=new Handler(Looper.getMainLooper());
    private final java.util.concurrent.ExecutorService rootWorker=Executors.newSingleThreadExecutor();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener profileListener=(prefs,key)->settingsChanged();
    private final android.content.SharedPreferences.OnSharedPreferenceChangeListener modeListener=(prefs,key)->{if("device_profiles".equals(key))settingsChanged();};
    private final android.media.AudioDeviceCallback routes=new android.media.AudioDeviceCallback(){public void onAudioDevicesAdded(android.media.AudioDeviceInfo[] d){settingsChanged();}public void onAudioDevicesRemoved(android.media.AudioDeviceInfo[] d){settingsChanged();}};
    private final Runnable applyProfile=()->{
        PcmSession current=session;if(current==null||current.cancelled)return;
        AudioSettings next=AudioSettings.current(this);String saved=AudioSettings.signature(this,next);
        if(next.storageKey.equals(current.settings.storageKey)&&java.util.Objects.equals(saved,current.preferenceSignature))return;
        current.requestReconfigure();message="Aplicando ajustes · conservando el procesado anterior";
    };
    public static Bundle publicStatus(){
        YinService service=instance;
        if(service==null){Bundle b=new Bundle();b.putBoolean("ready",false);b.putString("message","Servicio en reposo · activa Yin para conectar Yang");return b;}
        PcmSession current=service.session;Bundle b=current==null?new Bundle():current.snapshot();
        b.putBoolean("enabled",service.enabled());b.putBoolean("ready",service.enabled()&&RootAccess.granted()&&!service.checkingRoot);b.putInt("version",YinProtocol.VERSION);b.putBoolean("root",RootAccess.granted());b.putBoolean("bridge",AudioCapabilities.directPcm());
        if(current==null)b.putString("message",service.message);return b;
    }
    static Bundle meters(){YinService service=instance;PcmSession current=service==null?null:service.session;return current==null?new Bundle():current.meters();}
    static void settingsChanged(){YinService service=instance;if(service!=null){service.main.removeCallbacks(service.applyProfile);service.main.postDelayed(service.applyProfile,120);}}
    static void disable(){YinService service=instance;if(service!=null){service.closeSession("Yin desactivado");service.stopForeground(STOP_FOREGROUND_REMOVE);}}
    static void sessionEnded(){YinService service=instance;if(service!=null)service.main.post(service::updateWake);}
    boolean enabled(){return getSharedPreferences("yin_control",0).getBoolean("enabled",false);}
    @Override public void onCreate(){
        super.onCreate();instance=this;
        wake=getSystemService(PowerManager.class).newWakeLock(PowerManager.PARTIAL_WAKE_LOCK,"YinControl:audio");wake.setReferenceCounted(false);
        getSharedPreferences("audio_settings",0).registerOnSharedPreferenceChangeListener(profileListener);
        getSharedPreferences("yin_control",0).registerOnSharedPreferenceChangeListener(modeListener);getSystemService(android.media.AudioManager.class).registerAudioDeviceCallback(routes,main);
    }
    @Override public int onStartCommand(Intent intent,int flags,int startId){
        if(!enabled()){stopSelf(startId);return START_NOT_STICKY;}
        started=true;foreground();verifyRoot();return START_STICKY;
    }
    private void verifyRoot(){
        if(checkingRoot)return;checkingRoot=true;message="Verificando root…";
        rootWorker.execute(()->{try{RootAccess.run("true",15);message="Yin listo · esperando PCM de Yang";}catch(Exception error){message="Root pendiente: "+error.getMessage();}finally{checkingRoot=false;main.post(this::foreground);}});
    }
    private void foreground(){
        if(!enabled()||!started)return;
        NotificationManager manager=getSystemService(NotificationManager.class);manager.createNotificationChannel(new NotificationChannel("yin_control","Motor Yin Control",NotificationManager.IMPORTANCE_LOW));
        PendingIntent panel=PendingIntent.getActivity(this,0,new Intent(this,MainActivity.class),PendingIntent.FLAG_IMMUTABLE|PendingIntent.FLAG_UPDATE_CURRENT);
        Notification n=new Notification.Builder(this,"yin_control").setSmallIcon(android.R.drawable.ic_lock_silent_mode_off).setContentTitle("Yin Control").setContentText(message).setContentIntent(panel).setOngoing(true).build();
        if(Build.VERSION.SDK_INT>=34)startForeground(145,n,ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);else startForeground(145,n);
    }
    private void updateWake(){PcmSession current=session;boolean playing=current!=null&&!current.cancelled&&!current.paused&&!current.drained&&current.error.isEmpty();if(playing&&!wake.isHeld())wake.acquire();else if(!playing&&wake.isHeld())wake.release();}
    private void closeSession(String reason){synchronized(sessionLock){PcmSession current=session;session=null;if(current!=null)current.cancel(reason);}updateWake();}
    private void authenticate(){
        int uid=Binder.getCallingUid();if(uid==android.os.Process.myUid())return;
        String[] packages=getPackageManager().getPackagesForUid(uid);
        if(packages==null||!Arrays.asList(packages).contains("com.yang.player"))throw new SecurityException("Cliente de Yin no autorizado");
        try{
            PackageInfo info=getPackageManager().getPackageInfo("com.yang.player",PackageManager.GET_SIGNING_CERTIFICATES);
            boolean match=false;for(Signature signature:info.signingInfo.getApkContentsSigners()){
                byte[] digest=MessageDigest.getInstance("SHA-256").digest(signature.toByteArray());StringBuilder hex=new StringBuilder();for(byte value:digest)hex.append(String.format(java.util.Locale.ROOT,"%02x",value&255));
                if(BuildConfig.YANG_CERT_SHA256.equals(hex.toString()))match=true;
            }if(!match)throw new SecurityException("La firma de Yang no coincide");
        }catch(PackageManager.NameNotFoundException|java.security.NoSuchAlgorithmException unavailable){throw new SecurityException("No se pudo verificar Yang");}
    }
    private final Binder binder=new Binder(){
        @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException{
            if(code==INTERFACE_TRANSACTION){reply.writeString(YinProtocol.DESCRIPTOR);return true;}
            authenticate();data.enforceInterface(YinProtocol.DESCRIPTOR);
            Bundle args=data.readBundle(YinProtocol.class.getClassLoader());if(args==null)args=new Bundle();Bundle answer;
            long identity=Binder.clearCallingIdentity();
            try {
            try{
                if(code==YinProtocol.HELLO)answer=publicStatus();
                else if(code==YinProtocol.OPEN){
                    if(!enabled()||!RootAccess.granted()||checkingRoot)throw new IllegalStateException("Activa Yin y autoriza root antes de reproducir");
                    if(args.getInt("version")!=YinProtocol.VERSION)throw new IllegalArgumentException("Protocolo PCM incompatible");
                    synchronized(sessionLock){closeSession("Nueva sesión de Yang");RootAccess.run("true",5);PcmSession created=new PcmSession(YinService.this,args,args.getBinder("client"));session=created;answer=created.snapshot();answer.putParcelable("fd",created.sender);}
                    message="Motor conectado a Yang";main.post(()->{foreground();updateWake();});
                }else{
                    PcmSession current=session;
                    if(current==null||!current.id.equals(args.getString("session")))throw new IllegalStateException("La sesión de Yin ya no está disponible");
                    if(code==YinProtocol.STATE){current.state(args.getBoolean("playing"),args.getFloat("volume",1f));updateWake();answer=current.snapshot();}
                    else if(code==YinProtocol.STATUS){current.source(args.getBundle("source"));answer=current.snapshot();}
                    else if(code==YinProtocol.EOS){answer=current.snapshot();}
                    else if(code==YinProtocol.CLOSE){closeSession("Sesión finalizada");message="Yin listo · esperando PCM de Yang";answer=publicStatus();}
                    else return false;
                }
            }catch(Exception error){answer=new Bundle();answer.putBoolean("ok",false);answer.putString("message",error.getMessage()==null?"No se pudo abrir el motor Yin":error.getMessage());}
            reply.writeNoException();reply.writeBundle(answer);
            if(code==YinProtocol.OPEN){ParcelFileDescriptor sent=answer.getParcelable("fd");if(sent!=null)try{sent.close();}catch(Exception ignored){}}
            return true;
            } finally { Binder.restoreCallingIdentity(identity); }
        }
    };
    @Override public IBinder onBind(Intent intent){if(enabled()&&!RootAccess.granted())verifyRoot();return binder;}
    @Override public void onDestroy(){main.removeCallbacks(applyProfile);getSystemService(android.media.AudioManager.class).unregisterAudioDeviceCallback(routes);getSharedPreferences("yin_control",0).unregisterOnSharedPreferenceChangeListener(modeListener);closeSession("Servicio Yin detenido");rootWorker.shutdownNow();getSharedPreferences("audio_settings",0).unregisterOnSharedPreferenceChangeListener(profileListener);instance=null;super.onDestroy();}
}
