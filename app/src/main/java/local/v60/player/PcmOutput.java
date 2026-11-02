package local.v60.player;

import android.media.AudioTrack;
import java.io.IOException;
import java.util.LinkedHashSet;

/** Negotiate ordinary AudioTrack before configuring the decoder's PCM bytes. */
final class PcmOutput {
    static final class Opened {final AudioTrack track;final int rate;Opened(AudioTrack track){this.track=track;rate=track.getSampleRate();}}
    static Opened open(AudioSettings settings,int rate,int mask)throws IOException{
        return open(settings,rate,mask,()->false);
    }
    static Opened open(AudioSettings settings,int rate,int mask,java.util.function.BooleanSupplier cancelled)throws IOException{
        if(settings.direct){
            int[] directEncodings=new int[]{settings.encoding};String reason="";
            int attempts="wired".equals(settings.profile)&&rate==384000?4:1;
            for(int attempt=0;attempt<attempts;attempt++){
                if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Apertura cancelada");
                if(attempt>0){long until=android.os.SystemClock.elapsedRealtime()+attempt*750L;while(android.os.SystemClock.elapsedRealtime()<until){if(cancelled.getAsBoolean())throw new java.io.InterruptedIOException("Apertura cancelada");try{Thread.sleep(50);}catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new java.io.InterruptedIOException("Apertura interrumpida");}}}
                for(int encoding:directEncodings){AudioSettings candidate=settings.copy();candidate.encoding=encoding;AudioTrack track=null;
                try{track=DirectOutput.open(candidate,rate,mask);if(track.getState()!=AudioTrack.STATE_INITIALIZED||track.getAudioFormat()!=candidate.androidEncoding()||track.getChannelCount()!=(mask==android.media.AudioFormat.CHANNEL_OUT_MONO?1:2))throw new IOException("Android cambió el formato PCM directo");if(encoding!=settings.encoding)settings.compatibility("Bluetooth directo usa el contenedor PCM "+AudioSettings.encodingName(candidate.androidEncoding())+" aceptado por Android.");settings.encoding=encoding;return new Opened(track);}
                catch(IOException|RuntimeException|LinkageError rejected){if(track!=null)track.release();reason=rejected.getMessage();android.util.Log.w("ZeroPcmOutput","DIRECT rechazado: perfil="+settings.profile+", Hz="+rate+", encoding="+encoding+", intento="+(attempt+1)+": "+reason);}
                }
            }
            settings.direct=false;settings.compatibility("PCM DIRECT no se confirmó: se usa AudioTrack estándar."+(reason==null?"":" "+reason));
        }
        int compatibleRate=rate;while(compatibleRate>AudioSettings.MAX_ENGINE_RATE)compatibleRate/=2;
        LinkedHashSet<Integer> rates=new LinkedHashSet<>(),encodings=new LinkedHashSet<>();rates.add(compatibleRate);rates.add(48000);if(!"speaker".equals(settings.profile))rates.add(44100);encodings.add(settings.encoding);if(!"speaker".equals(settings.profile)&&!"bluetooth".equals(settings.profile))encodings.add(0);encodings.add(1);
        Exception last=null;
        for(int requested:rates)for(int encoding:encodings){
            AudioSettings candidate=settings.copy();candidate.encoding=encoding;candidate.validate();AudioTrack track=null;
            try{
                track=DirectOutput.open(candidate,requested,mask);
                if(track.getState()!=AudioTrack.STATE_INITIALIZED||track.getChannelCount()!=(mask==android.media.AudioFormat.CHANNEL_OUT_MONO?1:2)||track.getAudioFormat()!=candidate.androidEncoding())throw new IOException("AudioTrack no conservó el formato PCM solicitado");
                int actual=track.getSampleRate();if(actual<=0)throw new IOException("AudioTrack no informó una frecuencia válida");
                if(actual!=rate||encoding!=settings.encoding)settings.compatibility("Salida compatible: "+AudioSignalInfo.frequency(rate)+" / "+AudioSettings.encodingName(settings.androidEncoding())+" → "+AudioSignalInfo.frequency(actual)+" / "+AudioSettings.encodingName(candidate.androidEncoding())+".");
                settings.encoding=encoding;return new Opened(track);
            }catch(IOException|RuntimeException rejected){last=rejected;if(track!=null)track.release();}
        }
        throw new IOException("Android no pudo abrir una salida PCM compatible.",last);
    }
}
