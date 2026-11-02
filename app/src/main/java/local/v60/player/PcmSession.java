package local.v60.player;

import android.content.Context;
import android.media.AudioFormat;
import android.media.AudioTrack;
import android.os.*;
import android.system.Os;
import android.system.OsConstants;
import com.arthenica.ffmpegkit.*;
import java.io.*;
import java.nio.*;
import java.util.*;

/** One authenticated Yang session. The only device output and DSP live in Yin. */
final class PcmSession {
    final String id=UUID.randomUUID().toString();
    final int inputRate,inputChannels,inputEncoding,rate,channels,encoding,frameBytes;
    final int requestedRate,requestedEncoding;
    final AudioSettings settings;
    final String preferenceSignature;
    final AudioSettings.Plan plan;
    final AudioTrack track;
    final ParcelFileDescriptor receiver,sender;
    final String inputPipe,outputPipe;
    final ParcelFileDescriptor inputPipeFd,outputPipeFd;
    final OutputStream pipeWriter;
    final InputStream pipeReader;
    final IBinder client;
    final IBinder.DeathRecipient death;
    final FFmpegSession ffmpeg;
    volatile boolean cancelled,paused=true,inputEnded,complete,drained,reconfigure;
    volatile String error="";
    volatile long receivedBytes,submittedBytes;
    private long lastHead,wraps;
    private boolean trackPlaying;
    private Bundle source=new Bundle();
    final PcmMeter inputMeter,outputMeter;
    private final AbComparison.Session comparison;
    private volatile float sourceVolume=1;
    private volatile double trimDb;
    private long matchAt;

    PcmSession(Context context,Bundle config,IBinder client) throws Exception {
        inputRate=config.getInt("rate");inputChannels=config.getInt("channels");inputEncoding=config.getInt("encoding");
        if(inputRate<8000||inputRate>768000||inputChannels<1||inputChannels>8||!Arrays.asList(2,3,4,21,22).contains(inputEncoding))throw new IOException("PCM de entrada no compatible");
        if(client==null||!client.isBinderAlive())throw new IOException("Yang no está conectado");
        this.client=client;
        source(config.getBundle("source"));
        settings=AudioSettings.current(context);
        preferenceSignature=AudioSettings.signature(context,settings);
        java.io.File impulse=EffectImports.ir(context,settings.dsp);
        if(settings.dsp.convolver&&impulse==null)throw new IOException("El impulso IR no está disponible. Se mantiene silencio hasta corregirlo.");
        if(settings.dsp.irMix==0)settings.dsp.convolver=false;
        FFmpegKitConfig.setSessionHistorySize(10);
        int requested=settings.outputRate(inputRate,false);requestedRate=requested;requestedEncoding=settings.androidEncoding();channels=inputChannels==1?1:2;
        PcmOutput.Opened opened=PcmOutput.open(settings,requested,channels==1?AudioFormat.CHANNEL_OUT_MONO:AudioFormat.CHANNEL_OUT_STEREO);
        track=opened.track;rate=opened.rate;encoding=track.getAudioFormat();frameBytes=channels*settings.bytesPerSample();
        inputMeter=new PcmMeter(inputRate,inputChannels,inputEncoding);outputMeter=new PcmMeter(rate,channels,encoding);comparison=new AbComparison.Session(context,settings);trimDb=comparison.trim();
        ParcelFileDescriptor[] sockets=null;String in=null,out=null;ParcelFileDescriptor inFd=null,outFd=null;FFmpegSession running=null;
        try {
            sockets=ParcelFileDescriptor.createReliableSocketPair();
            in=FFmpegKitConfig.registerNewFFmpegPipe(context);out=FFmpegKitConfig.registerNewFFmpegPipe(context);
            if(in==null||out==null)throw new IOException("No se pudo crear el flujo PCM de Yin");
            inFd=ParcelFileDescriptor.open(new File(in),ParcelFileDescriptor.MODE_READ_WRITE);
            outFd=ParcelFileDescriptor.open(new File(out),ParcelFileDescriptor.MODE_READ_WRITE);
            final String source=in,destination=out;
            plan=settings.plan(inputRate,rate,channels,null,null);
            List<String> args=new ArrayList<>(Arrays.asList("-nostdin","-v","error","-filter_threads","1","-threads","1",
                "-probesize","4096","-analyzeduration","0","-f",inputMuxer(inputEncoding),"-ar",String.valueOf(inputRate),"-ac",String.valueOf(inputChannels),"-i",source));
            if(settings.dsp.convolver&&impulse!=null){
                args.addAll(Arrays.asList("-i",impulse.getAbsolutePath()));String layout=channels==1?"mono":"stereo";
                String irLayout=settings.dsp.irChannels==1?"mono":layout;double mix=settings.dsp.irMix/100.0;
                String resample="aresample="+rate+(settings.resampler==1?":resampler=soxr:precision=28":":resampler=swr:filter_size=64:phase_shift=10:cutoff=0.97");
                String graph="[0:a]aformat=sample_fmts=fltp:channel_layouts="+layout+","+resample+","+(plan.beforeIr.isEmpty()?"anull":plan.beforeIr)+",asplit=2[dry][feed];[1:a]aformat=sample_fmts=fltp:channel_layouts="+irLayout+","+resample+"[ir];[feed][ir]afir=dry=1:wet=1:gtype=gn:irgain="+DspSettings.decimal(Math.pow(10,settings.dsp.irGain/20.0))+":irfmt="+(settings.dsp.irChannels==1?"mono":"input")+":maxir=2.1:minp=256:maxp=8192[wet];[dry][wet]amix=inputs=2:weights='"+DspSettings.decimal(1-mix)+" "+DspSettings.decimal(mix)+"':normalize=0:duration=first,"+(plan.afterIr.isEmpty()?"anull":plan.afterIr)+"[processed]";
                args.addAll(Arrays.asList("-filter_complex_threads","1","-filter_complex",graph,"-map","[processed]"));
            }else{args.addAll(Arrays.asList("-map","0:a:0"));if(!plan.filter.isEmpty())args.addAll(Arrays.asList("-af",plan.filter));}
            args.addAll(Arrays.asList("-vn","-sn","-dn","-ac",String.valueOf(channels),"-ar",String.valueOf(rate)));
            args.addAll(Arrays.asList("-c:a",settings.pcmCodec(),"-flush_packets","1","-f",settings.pcmMuxer(),"-y",destination));
            running=FFmpegKit.executeWithArgumentsAsync(args.toArray(new String[0]),session->{if(!cancelled&&!ReturnCode.isSuccess(session.getReturnCode())){error="El DSP de Yin no pudo procesar el PCM";android.util.Log.e("YinEngine",error+": "+session.getAllLogsAsString());}complete=true;});
            receiver=sockets[0];sender=sockets[1];inputPipe=in;outputPipe=out;inputPipeFd=inFd;outputPipeFd=outFd;ffmpeg=running;
            pipeWriter=new ParcelFileDescriptor.AutoCloseOutputStream(inputPipeFd);pipeReader=new ParcelFileDescriptor.AutoCloseInputStream(outputPipeFd);
            death=()->cancel("Yang se desconectó");client.linkToDeath(death,0);
            sourceVolume=Math.max(0,Math.min(1,config.getFloat("volume",1f)));applyVolume();paused=!config.getBoolean("playing",false);
            new Thread(this::pumpInput,"Yin-PcmInput").start();new Thread(this::pumpOutput,"Yin-DspOutput").start();
            DacGain.outputStarted(context);
        } catch(Exception failure) {
            if(running!=null)FFmpegKit.cancel(running.getSessionId());
            if(sockets!=null)for(ParcelFileDescriptor fd:sockets)try{fd.close();}catch(Exception ignored){}
            if(inFd!=null)try{inFd.close();}catch(Exception ignored){}
            if(outFd!=null)try{outFd.close();}catch(Exception ignored){}
            if(in!=null)FFmpegKitConfig.closeFFmpegPipe(in);if(out!=null)FFmpegKitConfig.closeFFmpegPipe(out);
            track.release();throw failure;
        }
    }
    private static String inputMuxer(int encoding){return encoding==2?"s16le":encoding==3?"u8":encoding==21?"s24le":encoding==22?"s32le":"f32le";}
    private void pumpInput(){
        try(InputStream input=new ParcelFileDescriptor.AutoCloseInputStream(receiver)) {
            byte[] buffer=new byte[32768];int read;
            while(!cancelled&&(read=input.read(buffer))>=0){if(read==0)continue;pipeWriter.write(buffer,0,read);inputMeter.accept(buffer,0,read);receivedBytes+=read;}
            inputEnded=true;
        }catch(Exception failure){if(!cancelled){error="Se interrumpió el PCM de Yang";android.util.Log.w("YinEngine",error,failure);}}
        finally{try{pipeWriter.close();}catch(Exception ignored){}}
    }
    private void pumpOutput(){
        android.os.Process.setThreadPriority(android.os.Process.THREAD_PRIORITY_AUDIO);
        byte[] bytes=new byte[32768];ByteBuffer pcm=ByteBuffer.allocateDirect(bytes.length).order(ByteOrder.LITTLE_ENDIAN);pcm.limit(0);
        int carry=0;long lastData=SystemClock.elapsedRealtime();
        try {
            while(!cancelled){
                if(paused){if(trackPlaying){track.pause();trackPlaying=false;}lastData=SystemClock.elapsedRealtime();SystemClock.sleep(10);continue;}
                long now=SystemClock.elapsedRealtime();if(now-matchAt>=500){matchAt=now;comparison.observe(inputMeter,outputMeter);double target=comparison.trim();if(Math.abs(target-trimDb)>.01){trimDb+=Math.max(-1,Math.min(1,target-trimDb));applyVolume();}}
                if(!pcm.hasRemaining()){
                    if(pipeReader.available()>0){
                        int n=pipeReader.read(bytes,carry,bytes.length-carry);if(n<0)break;
                        int total=carry+n,aligned=total-total%frameBytes;pcm.clear();pcm.put(bytes,0,aligned);pcm.flip();carry=total-aligned;
                        outputMeter.accept(bytes,0,aligned);
                        if(carry>0)System.arraycopy(bytes,aligned,bytes,0,carry);lastData=SystemClock.elapsedRealtime();
                    }else if(complete)break;
                    else {if(SystemClock.elapsedRealtime()-lastData>15000)throw new IOException("El DSP dejó de entregar audio");SystemClock.sleep(5);continue;}
                }
                if(pcm.hasRemaining()){
                    int n=track.write(pcm,pcm.remaining(),AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IOException("AudioTrack rechazó el PCM de Yin: "+n);
                    submittedBytes+=n;
                    if(!trackPlaying&&submittedBytes>0){track.play();trackPlaying=true;}
                    if(n==0)SystemClock.sleep(5);
                }
            }
            if(!cancelled && complete && error.isEmpty() && submittedBytes>0){
                ByteBuffer tail=ByteBuffer.allocateDirect(Math.max(frameBytes,rate/50*frameBytes));long until=SystemClock.elapsedRealtime()+250;
                while(!cancelled && !paused && tail.hasRemaining() && SystemClock.elapsedRealtime()<until){int n=track.write(tail,tail.remaining(),AudioTrack.WRITE_NON_BLOCKING);if(n<0)throw new IOException("No se pudo terminar el bloque de salida");if(n==0)SystemClock.sleep(2);}
            }
            long lastProgress=SystemClock.elapsedRealtime(),previous=-1;
            while(!cancelled&&positionFrames()<submittedBytes/frameBytes){
                if(paused){if(trackPlaying){track.pause();trackPlaying=false;}lastProgress=SystemClock.elapsedRealtime();}
                else {if(!trackPlaying){track.play();trackPlaying=true;}long head=positionFrames();if(head!=previous){previous=head;lastProgress=SystemClock.elapsedRealtime();}else if(SystemClock.elapsedRealtime()-lastProgress>5000)throw new IOException("La salida de Yin dejó de avanzar");}
                SystemClock.sleep(10);
            }
            drained=!cancelled&&error.isEmpty();YinService.sessionEnded();
        }catch(Exception failure){if(!cancelled){error=failure.getMessage()==null?"Falló la salida de Yin":failure.getMessage();android.util.Log.e("YinEngine",error,failure);YinService.sessionEnded();}}
    }
    void requestReconfigure(){if(!cancelled)reconfigure=true;}
    synchronized long positionFrames(){
        if(cancelled)return 0;
        try{long current=Integer.toUnsignedLong(track.getPlaybackHeadPosition());if(current<lastHead)wraps++;lastHead=current;return Math.min(submittedBytes/frameBytes,current+(wraps<<32));}catch(IllegalStateException stopped){return 0;}
    }
    private void applyVolume(){track.setVolume((float)(sourceVolume*Math.pow(10,trimDb/20)));}
    void state(boolean playing,float volume){paused=!playing;if(!playing)try{track.pause();trackPlaying=false;}catch(Exception ignored){}sourceVolume=Math.max(0,Math.min(1,volume));applyVolume();}
    Bundle meters(){Bundle b=new Bundle();boolean active=!cancelled&&!paused&&!drained&&error.isEmpty();b.putBoolean("active",active);b.putString("profileKey",settings.storageKey);double[] input=inputMeter.recent(),output=outputMeter.recent();double volume=sourceVolume*Math.pow(10,trimDb/20);b.putDouble("before",PcmMeter.db(Math.sqrt(input[0])));b.putDouble("after",PcmMeter.db(Math.sqrt(output[0])*volume));b.putDouble("peak",PcmMeter.db(output[1]*volume));b.putBoolean("ceiling",output[1]>=.9999);b.putBoolean("signal",output[2]>0);b.putDouble("trim",trimDb);return b;}
    synchronized void source(Bundle metadata){if(metadata!=null)source=new Bundle(metadata);}
    synchronized Bundle snapshot(){
        if(cancelled){Bundle b=new Bundle();b.putBoolean("ok",false);b.putString("session",id);b.putString("message",error);return b;}
        Bundle b=new Bundle();b.putBoolean("ok",!cancelled&&error.isEmpty());b.putString("session",id);b.putInt("rate",rate);b.putInt("encoding",encoding);b.putInt("channels",channels);
        b.putInt("bufferFrames",track.getBufferSizeInFrames());b.putLong("positionUs",positionFrames()*1000000L/rate);b.putBoolean("drained",drained);b.putBoolean("playing",!paused);b.putBoolean("reconfigure",reconfigure);
        b.putLong("receivedBytes",receivedBytes);b.putBoolean("direct",settings.direct&&DirectOutput.nativeFlags(track)>=0&&(DirectOutput.nativeFlags(track)&1)!=0);
        b.putInt("inputRate",inputRate);b.putInt("inputChannels",inputChannels);b.putInt("inputEncoding",inputEncoding);
        b.putBundle("source",new Bundle(source));
        b.putInt("sessionId",track.getAudioSessionId());b.putInt("underruns",track.getUnderrunCount());
        b.putInt("flags",DirectOutput.nativeFlags(track));b.putInt("requestedRate",requestedRate);b.putInt("requestedEncoding",requestedEncoding);
        android.media.AudioDeviceInfo device=track.getRoutedDevice();
        if(device!=null){b.putInt("deviceId",device.getId());b.putString("device",device.getProductName().toString());b.putString("route",AudioSettings.route(device));}
        b.putString("profile",settings.profile);b.putInt("preamp",settings.preamp);b.putInt("balance",settings.balance);
        b.putString("profileKey",settings.storageKey);
        b.putDouble("preampDb",settings.preamp+settings.dsp.preampFraction);
        b.putBoolean("eq",plan.equalizing);b.putBoolean("limiter",plan.limiting);b.putDouble("gainDb",plan.gainDb);b.putDouble("headroomDb",plan.headroomDb);
        b.putString("effects",plan.effects);b.putBoolean("protection",settings.dsp.protection);b.putString("deviceProfile",settings.storageKey.equals(settings.profile)?"Por conexión":"Por dispositivo");
        b.putString("resampler",inputRate==rate?"Sin cambio de frecuencia":settings.resampler==1?"SoXR · precisión 28":"SWR · filtro 64 taps");
        b.putString("message",error.isEmpty()?DirectOutput.describe(track,settings)+(settings.compatibilityNote.isEmpty()?"":"\n"+settings.compatibilityNote):error);
        return b;
    }
    synchronized void cancel(String reason){
        if(cancelled)return;cancelled=true;error=reason;
        try{client.unlinkToDeath(death,0);}catch(Exception ignored){}
        try{Os.shutdown(receiver.getFileDescriptor(),OsConstants.SHUT_RDWR);}catch(Exception ignored){}
        try{receiver.close();}catch(Exception ignored){}try{sender.close();}catch(Exception ignored){}
        FFmpegKit.cancel(ffmpeg.getSessionId());
        try{pipeWriter.close();}catch(Exception ignored){}try{pipeReader.close();}catch(Exception ignored){}
        try{track.pause();track.flush();}catch(Exception ignored){}track.release();
        FFmpegKitConfig.closeFFmpegPipe(inputPipe);FFmpegKitConfig.closeFFmpegPipe(outputPipe);DacGain.outputStopped();YinService.sessionEnded();
    }
}
