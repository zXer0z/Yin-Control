package local.v60.player;
import android.os.Build;
final class AudioCapabilities {
    static boolean directPcm(){try{return DirectOutput.available();}catch(LinkageError|RuntimeException unavailable){return false;}}
    static boolean highRate(){try{return directPcm()&&DirectOutput.highRateAvailable();}catch(LinkageError|RuntimeException unavailable){return false;}}
    static boolean quadDac(){return Build.VERSION.SDK_INT==33&&"lge/timelm/timelm:13/TKQ1.220829.002/2326319175c92:user/release-keys".equals(Build.FINGERPRINT);}
    // PCM bridge does not transport native DSD/DoP. Never enable an unvalidated carrier.
    static boolean dop(){return false;}
    static String summary(){return directPcm()?"Puente PCM directo activo en Yin":"AudioTrack disponible · puente LSPosed sin confirmar";}
}
