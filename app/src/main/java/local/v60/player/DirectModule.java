package local.v60.player;

import android.media.AudioTrack;
import android.os.Build;
import de.robv.android.xposed.*;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/** Self-scope only: never hooks Android/system_server or another player. */
public final class DirectModule implements IXposedHookLoadPackage {
    private static final ThreadLocal<Boolean> pcmConstruction=new ThreadLocal<>();
    private static volatile boolean bridgeFailed;
    private static native int nativeArm(int enabled);
    private static native int nativeReady();
    private static native int nativeArmDop();
    private static native int nativeArmDop128();
    private static native int nativeArmPcmRate(int rate);
    private static native long nativeConvertFlags(int flags,int reverse);
    private static native int nativeArmProbe(int flags);
    @Override public void handleLoadPackage(XC_LoadPackage.LoadPackageParam p) throws Throwable {
        try{install(p);}catch(Throwable unavailable){bridgeFailed=true;XposedBridge.log("ZeroMusic: optional PCM bridge unavailable: "+unavailable.getClass().getSimpleName());}
    }
    private void install(XC_LoadPackage.LoadPackageParam p)throws Throwable{
        if(!"local.v60.player".equals(p.packageName)||!"local.v60.player".equals(p.processName))return;
        // Stock build inspected: identical AudioTrack::set ABI and flags in w6.
        boolean stock=Build.VERSION.SDK_INT==33&&"lge/timelm/timelm:13/TKQ1.220829.002/2326319175c92:user/release-keys".equals(Build.FINGERPRINT);
        if((Build.VERSION.SDK_INT!=36&&!stock)||!Build.DEVICE.startsWith("timelm")||!"arm64-v8a".equals(Build.SUPPORTED_ABIS[0]))return;
        // Vector's module class loader searches inside the APK, while this APK
        // extracts its native libraries. Load the verified self-app path.
        System.load(p.appInfo.nativeLibraryDir+"/libv60direct.so");
        Class<?> bridge=XposedHelpers.findClass("local.v60.player.DirectOutput",p.classLoader);
        if(stock)XposedHelpers.findAndHookMethod(AudioTrack.class,"shouldEnablePowerSaving",android.media.AudioAttributes.class,android.media.AudioFormat.class,int.class,int.class,new XC_MethodHook(){
            protected void beforeHookedMethod(MethodHookParam args){if(Boolean.TRUE.equals(pcmConstruction.get()))args.setResult(false);}
        });
        XposedHelpers.findAndHookMethod(bridge,"available",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return !bridgeFailed&&nativeReady()==1;}
        });
        XposedHelpers.findAndHookMethod(bridge,"beginNative",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){boolean ready=nativeArm(1)==1;if(ready)pcmConstruction.set(true);return ready;}
        });
        if(stock){
            XposedHelpers.findAndHookMethod(bridge,"highRateAvailable",new XC_MethodReplacement(){
                protected Object replaceHookedMethod(MethodHookParam args){return !bridgeFailed&&nativeReady()==1;}
            });
            XposedHelpers.findAndHookMethod(bridge,"beginPcmRate",int.class,new XC_MethodReplacement(){
                protected Object replaceHookedMethod(MethodHookParam args){boolean ready=nativeArmPcmRate((Integer)args.args[0])==1;if(ready)pcmConstruction.set(true);return ready;}
            });
        }
        XposedHelpers.findAndHookMethod(bridge,"beginDop",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return nativeArmDop()==1;}
        });
        if(stock)XposedHelpers.findAndHookMethod(bridge,"beginDop128",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return nativeArmDop128()==1;}
        });
        XposedHelpers.findAndHookMethod(bridge,"dopStatus",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){
                try{return XposedHelpers.callStaticMethod(XposedHelpers.findClass("android.media.AudioSystem",p.classLoader),"getParameters","IS_DOP_PLAYING");}catch(Throwable error){return "";}
            }
        });
        XposedHelpers.findAndHookMethod(bridge,"convertFlags",int.class,boolean.class,new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return nativeConvertFlags((Integer)args.args[0],(Boolean)args.args[1]?1:0);}
        });
        XposedHelpers.findAndHookMethod(bridge,"beginProbeFlags",int.class,new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return nativeArmProbe((Integer)args.args[0])==1;}
        });
        XposedHelpers.findAndHookMethod(bridge,"endNative",new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){pcmConstruction.remove();nativeArm(0);return null;}
        });
        XposedHelpers.findAndHookMethod(bridge,"nativeFlags",AudioTrack.class,new XC_MethodReplacement(){
            protected Object replaceHookedMethod(MethodHookParam args){return (Integer)XposedHelpers.callMethod(args.args[0],"native_get_flags");}
        });
        XposedBridge.log("V60Direct: self-scope bridge loaded");
    }
}
