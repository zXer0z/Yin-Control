package local.v60.player;

import android.bluetooth.*;
import android.content.*;
import android.os.*;
import java.lang.reflect.*;
import java.util.*;
import org.json.*;

/** Short-lived root app_process helper. Only status and validated codec preference. */
public final class BluetoothCodecCommand {
    static Object invoke(Object o,String name,Class<?>[] types,Object...args)throws Exception{
        Method m=o.getClass().getMethod(name,types);m.setAccessible(true);return m.invoke(o,args);
    }
    static Object call(Object service,String name,Object attribution,Object...args)throws Exception{
        for(Method m:Class.forName("android.bluetooth.IBluetoothA2dp").getMethods())if(m.getName().equals(name)){
            Class<?>[] types=m.getParameterTypes();if(types.length<args.length)continue;Object[] values=Arrays.copyOf(args,types.length);Object receiver=null;boolean supported=true;
            for(int i=args.length;i<types.length;i++){
                if(types[i].getName().equals("android.content.AttributionSource"))values[i]=attribution;
                else if(types[i].getName().endsWith("SynchronousResultReceiver")){receiver=types[i].getMethod("get").invoke(null);values[i]=receiver;}
                else{supported=false;break;}
            }
            if(!supported)continue;Object returned=m.invoke(service,values);
            if(receiver==null)return returned;
            Object result=receiver.getClass().getMethod("awaitResultNoInterrupt",java.time.Duration.class).invoke(receiver,java.time.Duration.ofSeconds(3));
            return result.getClass().getMethod("getValue",Object.class).invoke(result,new Object[]{null});
        }
        throw new NoSuchMethodException(name+" no disponible en esta ROM");
    }
    static int bestRate(int mask){for(int bit:new int[]{8,4,2,1})if((mask&bit)!=0)return bit;return 0;}
    static IBinder bridge(Context context)throws Exception{
        Object manager=Class.forName("android.app.ActivityManager").getMethod("getService").invoke(null);Class<?> api=Class.forName("android.app.IActivityManager");IBinder token=new Binder();String authority="local.v60.player.bluetooth.root";Object holder=null;
        for(Method method:api.getMethods())if(method.getName().equals("getContentProviderExternal")&&method.getParameterCount()==4){holder=method.invoke(manager,authority,0,token,"ZeroMusicBluetooth");break;}
        if(holder==null)throw new IllegalStateException("No se pudo abrir el puente Bluetooth de la app");
        try{
            Object provider=holder.getClass().getField("provider").get(holder);Object result=null;
            for(Method method:Class.forName("android.content.IContentProvider").getMethods())if(method.getName().equals("call")&&method.getParameterCount()==5){result=method.invoke(provider,context.getAttributionSource(),authority,"a2dp",null,null);break;}
            if(!(result instanceof Bundle)||((Bundle)result).getBinder("a2dp")==null)throw new IllegalStateException("Puente A2DP no disponible");return ((Bundle)result).getBinder("a2dp");
        }finally{try{api.getMethod("removeContentProviderExternal",String.class,IBinder.class).invoke(manager,authority,token);}catch(Exception ignored){}}
    }
    static JSONObject codec(Object c)throws Exception{
        int type=((Number)invoke(c,"getCodecType",new Class[0])).intValue();String name="Códec Android "+type;
        for(Field f:c.getClass().getFields())if(f.getName().startsWith("SOURCE_CODEC_TYPE_")&&!f.getName().endsWith("MAX")&&!f.getName().endsWith("INVALID")&&f.getType()==int.class&&f.getInt(null)==type){name=f.getName().substring(18).replace('_',' ');break;}
        return new JSONObject().put("type",type)
            .put("name",name).put("rate",invoke(c,"getSampleRate",new Class[0]))
            .put("bits",invoke(c,"getBitsPerSample",new Class[0])).put("channels",invoke(c,"getChannelMode",new Class[0]));
    }
    static void result(JSONObject value){System.out.println("V60_CODEC_JSON="+value.toString());System.out.flush();}
    public static void main(String[] args){
        if(android.os.Process.myUid()!=0){result(error("Este ayudante necesita root."));return;}
        final boolean changing=args.length==3&&"set".equals(args[0]),maximum=args.length==2&&"max".equals(args[0]);
        if(!changing&&!maximum&&!(args.length==1&&"status".equals(args[0]))){result(error("Operación inválida."));return;}
        try{
            Looper.prepareMainLooper();
            Class<?> thread=Class.forName("android.app.ActivityThread");Object system=thread.getMethod("systemMain").invoke(null);
            Context context=(Context)thread.getMethod("getSystemContext").invoke(system);
            // app_process does not install the framework's Bluetooth service registerer.
            // BluetoothDevice deserialization uses it even when the A2DP Binder is already available.
            Class<?> initializer=Class.forName("android.bluetooth.BluetoothFrameworkInitializer");
            if(initializer.getMethod("getBluetoothServiceManager").invoke(null)==null){Class<?> services=Class.forName("android.os.BluetoothServiceManager");initializer.getMethod("setBluetoothServiceManager",services).invoke(null,services.getConstructor().newInstance());}
            Object attribution=context.getAttributionSource();
            Intent intent=new Intent("android.bluetooth.IBluetoothA2dp");android.content.pm.ResolveInfo resolved=context.getPackageManager().resolveService(intent,0);
            if(resolved==null||resolved.serviceInfo==null)throw new IllegalStateException("Servicio A2DP no disponible");
            intent.setComponent(new ComponentName(resolved.serviceInfo.packageName,resolved.serviceInfo.name));
            Handler handler=new Handler(Looper.getMainLooper());
            handler.postDelayed(()->{result(error("El servicio A2DP no respondió a tiempo."));System.exit(2);},4500);
            ServiceConnection connection=new ServiceConnection(){
                public void onServiceDisconnected(ComponentName name){}
                public void onServiceConnected(ComponentName name,IBinder binder){
                    try{
                        Object service=Class.forName("android.bluetooth.IBluetoothA2dp$Stub").getMethod("asInterface",IBinder.class).invoke(null,binder);
                        BluetoothDevice device=(BluetoothDevice)call(service,"getActiveDevice",attribution);
                        if(device==null)throw new IllegalStateException("Conecta unos auriculares Bluetooth A2DP y selecciónalos como salida multimedia.");
                        Object status=call(service,"getCodecStatus",attribution,device);
                        if(status==null)throw new IllegalStateException("Android no informa el códec de estos auriculares.");
                        Object capabilities=invoke(status,"getCodecsSelectableCapabilities",new Class[0]);List<?> available=capabilities instanceof List?(List<?>)capabilities:Arrays.asList((Object[])capabilities);
                        JSONArray choices=new JSONArray();for(Object candidate:available)choices.put(codec(candidate));
                        JSONObject preference=null;
                        if(changing||maximum){
                            if(!device.getAddress().equals(maximum?args[1]:args[2]))throw new IllegalStateException("Los auriculares activos cambiaron. Actualiza los datos antes de elegir un códec.");
                            int type=maximum?4:Integer.parseInt(args[1]);Object selected=null;
                            for(Object candidate:available)if(((Number)invoke(candidate,"getCodecType",new Class[0])).intValue()==type)selected=candidate;
                            if(selected==null)throw new IllegalStateException("Este códec no está entre los compatibles con la conexión actual.");
                            Class<?> config=Class.forName("android.bluetooth.BluetoothCodecConfig");
                            int rate=0,bits=0,channels=0;
                            if(maximum){JSONObject limits=codec(selected);rate=bestRate(limits.optInt("rate"));bits=(limits.optInt("bits")&2)!=0?2:(limits.optInt("bits")&4)!=0?4:1;channels=2;
                                if(rate==0)throw new IllegalStateException("Android no informó una frecuencia LDAC compatible.");
                            }
                            Object requested;
                            try{Object builder=Class.forName("android.bluetooth.BluetoothCodecConfig$Builder").getConstructor().newInstance();invoke(builder,"setCodecType",new Class[]{int.class},type);invoke(builder,"setCodecPriority",new Class[]{int.class},1000000);if(maximum){invoke(builder,"setSampleRate",new Class[]{int.class},rate);invoke(builder,"setBitsPerSample",new Class[]{int.class},bits);invoke(builder,"setChannelMode",new Class[]{int.class},channels);}requested=invoke(builder,"build",new Class[0]);}
                            catch(ReflectiveOperationException older){requested=config.getConstructor(int.class,int.class,int.class,int.class,int.class,long.class,long.class,long.class,long.class).newInstance(type,1000000,rate,bits,channels,0L,0L,0L,0L);}
                            preference=codec(requested);call(service,"setCodecConfigPreference",attribution,device,requested);
                        }
                        String deviceName="Auriculares Bluetooth";try{String reported=device.getName();if(reported!=null)deviceName=reported;}catch(Exception ignored){}
                        JSONObject answer=new JSONObject().put("device",deviceName).put("address",device.getAddress())
                            .put("current",codec(invoke(status,"getCodecConfig",new Class[0]))).put("available",choices).put("requested",changing||maximum).put("source","Servicio A2DP");
                        if(preference!=null)answer.put("preference",preference);result(answer);
                    }catch(Throwable e){while(e instanceof InvocationTargetException)e=((InvocationTargetException)e).getTargetException();result(error(e.getMessage()==null?e.getClass().getSimpleName():e.getMessage()));}
                    finally{try{context.unbindService(this);}catch(Exception ignored){}handler.removeCallbacksAndMessages(null);System.exit(0);}
                }
            };
            connection.onServiceConnected(intent.getComponent(),bridge(context));
        }catch(Throwable e){result(error(e.toString()));System.exit(1);}
    }
    static JSONObject error(String text){try{return new JSONObject().put("error",text);}catch(JSONException e){throw new AssertionError(e);}}
}
