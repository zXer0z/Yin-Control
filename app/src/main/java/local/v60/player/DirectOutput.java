package local.v60.player;

import android.media.AudioTrack;
import java.io.IOException;

/** Stubs are replaced by our LSPosed module in this app's own process. */
public final class DirectOutput {
    public static boolean available(){return false;}
    static boolean beginNative(){return false;}
    public static boolean highRateAvailable(){return false;}
    static boolean beginPcmRate(int rate){return false;}
    static boolean beginDop(){return false;}
    static boolean beginDop128(){return false;}
    public static String dopStatus(){return "";}
    /** Diagnostic only: packed Android ConversionResult<int32_t>, no audio I/O. */
    public static long convertFlags(int flags,boolean reverse){return -1;}
    public static boolean beginProbeFlags(int flags){return false;}
    static void endNative(){}
    public static int nativeFlags(AudioTrack track){return -1;}
    /** Never writes a portador unless the vendor flag survives framework negotiation. */
    public static AudioTrack buildDop(AudioTrack.Builder builder)throws IOException{
        return buildDop(builder,176400);
    }
    public static AudioTrack buildDop(AudioTrack.Builder builder,int rate)throws IOException{
        return buildDop(builder,rate,android.media.AudioFormat.ENCODING_PCM_32BIT);
    }
    static AudioTrack buildDop(AudioTrack.Builder builder,int rate,int encoding)throws IOException{
        if(encoding!=android.media.AudioFormat.ENCODING_PCM_32BIT&&encoding!=android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED)throw new IOException("Formato DoP no admitido");
        if(rate!=176400&&rate!=352800)throw new IOException("Portador DoP no admitido.");
        if(!available()||!(rate==352800?beginDop128():beginDop()))throw new IOException("DoP necesita el puente compatible activo en LSPosed, con alcance solo en esta app.");
        AudioTrack track;
        try{track=builder.build();}catch(RuntimeException e){throw new IOException("Esta ROM rechazó la ruta DoP de LG. No se enviaron datos DoP. Usa DSD convertido a PCM mientras adaptamos la ruta del sistema.",e);}finally{endNative();}
        int flags=nativeFlags(track);
        if(flags<0||(flags&0x800001)!=0x800001){track.release();throw new IOException("Android retiró la bandera DoP de LG. No se enviaron datos: DIRECT por sí solo no confirma DoP. Usa conversión DSD a PCM.");}
        if(track.getSampleRate()!=rate){int actual=track.getSampleRate();track.release();throw new IOException("La salida negoció "+actual+" Hz en lugar de "+rate+" Hz. No se enviaron datos.");}
        if(track.getAudioFormat()!=encoding||track.getChannelCount()!=2){track.release();throw new IOException("La salida no conservó el formato DoP estéreo solicitado. No se enviaron datos.");}
        return track;
    }
    public static AudioTrack build(AudioTrack.Builder builder,AudioSettings settings)throws IOException {
        return build(builder,settings,0);
    }
    private static AudioTrack build(AudioTrack.Builder builder,AudioSettings settings,int rate)throws IOException {
        if(!settings.direct)return builder.build();
        boolean bluetooth="bluetooth".equals(settings.profile);
        if(!bluetooth&&!"wired".equals(settings.profile))throw new IOException("La salida directa experimental solo admite Jack / AUX y Bluetooth.");
        if(bluetooth&&(rate!=96000||(settings.encoding!=2&&settings.encoding!=3)))throw new IOException("Bluetooth directo experimental requiere 96 kHz y PCM entero de 24 o 32 bits.");
        if(settings.encoding==0)throw new IOException("La salida directa requiere PCM entero de 16, 24 o 32 bits.");
        if(!available()||!(rate>192000?highRateAvailable()&&beginPcmRate(rate):beginNative()))throw new IOException("Activa Zero Music en LSPosed con alcance Zero Music y vuelve a abrir la app. Bluetooth directo necesita además la política root BT96.");
        AudioTrack track;
        try{track=builder.build();}finally{endNative();}
        int flags;
        try{flags=nativeFlags(track);}catch(RuntimeException error){track.release();throw new IOException("No se pudo comprobar la bandera de salida directa.",error);}
        if(flags<0||(flags&1)==0){track.release();throw new IOException("Android rechazó la ruta PCM directa para este formato. Prueba otra frecuencia/formato o desactiva el modo directo.");}
        if(rate>0&&track.getSampleRate()!=rate){int actual=track.getSampleRate();track.release();throw new IOException("Android negoció "+actual+" Hz en lugar de "+rate+" Hz. No se enviaron muestras PCM.");}
        return track;
    }
    /** Java validates the placeholder rate; the self-process bridge supplies the
     * real native rate. Buffer duration is calculated at the real rate. */
    public static AudioTrack open(AudioSettings settings,int rate,int mask)throws IOException{
        String problem=settings.outputProblem(rate);if(!problem.isEmpty())throw new IOException(problem);
        int javaRate=rate>192000?192000:rate;
        int minimum=AudioTrack.getMinBufferSize(javaRate,mask,settings.androidEncoding());
        if(minimum<=0)throw new IOException("Android no acepta este formato PCM. Prueba Entero 24 bits o una frecuencia menor.");
        int channels=mask==android.media.AudioFormat.CHANNEL_OUT_MONO?1:2,frameBytes=channels*settings.bytesPerSample();
        long scaled=((long)minimum*rate+javaRate-1)/javaRate;
        long requested=Math.max(scaled,(long)rate*settings.bufferMs/1000*frameBytes);
        requested=((requested+frameBytes-1)/frameBytes)*frameBytes;
        if(requested>Integer.MAX_VALUE)throw new IOException("Búfer PCM demasiado grande.");
        return build(new AudioTrack.Builder().setAudioAttributes(AudioSettings.attributes())
            .setAudioFormat(new android.media.AudioFormat.Builder().setSampleRate(javaRate).setChannelMask(mask).setEncoding(settings.androidEncoding()).build())
            .setTransferMode(AudioTrack.MODE_STREAM).setBufferSizeInBytes((int)requested),settings,rate);
    }
    static String describe(AudioTrack track,AudioSettings settings){
        if(!settings.direct)return "Salida estándar de Android (puede pasar por el mezclador)";
        int flags=nativeFlags(track);
        return flags>=0&&(flags&1)!=0?"PCM DIRECT aceptado · comprobar HAL/ALSA para confirmar formato físico":"PCM DIRECT sin confirmar";
    }
}
