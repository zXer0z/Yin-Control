package local.v60.player;

import android.content.*;
import android.os.*;
import android.widget.*;
import java.util.Locale;

/** Explain the signal first; keep the system dump behind an explicit detail action. */
final class YinRoutePage {
    final ScrollView view;
    private final MainActivity a;
    private final YinUi u;
    private final LinearLayout body;
    private YinRouteRead.Result read;
    private boolean busy;
    private String last="",report="";
    private TextView age;
    private TextView bluetoothResult;
    private Button bluetoothButton;
    private boolean bluetoothBusy;
    private String bluetoothMessage="";
    YinRoutePage(MainActivity a){this.a=a;u=new YinUi(a);view=new ScrollView(a);view.setVerticalScrollBarEnabled(false);view.setClipToPadding(false);body=u.column();body.setPadding(u.d(20),u.d(12),u.d(20),u.d(48));view.addView(body,new ScrollView.LayoutParams(-1,-2));refresh(true);}
    void refresh(boolean force){
        Bundle b=YinService.publicStatus();Bundle source=b.getBundle("source");
        if(age!=null&&read!=null)age.setText("Última consulta hace "+Math.max(0,(SystemClock.elapsedRealtime()-read.observed)/1000)+" s");
        String signature=YinRouteRead.key(b)+":"+b.getBoolean("playing")+":"+b.getBoolean("drained")+":"+b.getBoolean("ok")+":"+(source==null?"":source.getString("sourceId","")+":"+source.getString("sourceCodec","")+":"+source.getInt("sourceRate")+":"+source.getInt("sourceBits"));
        if(!force&&last.equals(signature))return;last=signature;int y=view.getScrollY();render(b);view.post(()->view.scrollTo(0,y));
    }
    private static String pcm(int encoding){switch(encoding){case 2:return "16 bits";case 3:return "8 bits";case 4:return "32 bits float";case 21:return "24 bits";case 22:return "32 bits";default:return "profundidad no informada";}}
    private static String signal(int rate,int encoding){return rate>0?AudioSignalInfo.frequency(rate)+" · "+pcm(encoding):"Sin información";}
    private static int number(String value){try{return Integer.parseInt(value);}catch(Exception e){return 0;}}
    private static int halEncoding(String format){if(format.contains("PCM_16_BIT"))return 2;if(format.contains("PCM_24_BIT_PACKED")||format.contains("PCM_8_24_BIT"))return 21;if(format.contains("PCM_32_BIT"))return 22;if(format.contains("PCM_FLOAT"))return 4;if(format.contains("PCM_8_BIT"))return 3;return 0;}
    private void arrow(){TextView v=u.text("↓",22,YinUi.ACCENT);v.setGravity(android.view.Gravity.CENTER);body.addView(v);u.gap(body,12);}
    private void query(){if(busy)return;busy=true;refresh(true);YinRouteRead.request(YinService.publicStatus(),result->{if(a.isDestroyed())return;read=result;busy=false;refresh(true);});}
    private void render(Bundle b){
        body.removeAllViews();age=null;bluetoothResult=null;bluetoothButton=null;
        LinearLayout hero=u.card(body,"PASO A PASO","Ruta de audio");hero.addView(u.text("Yang abre tu música. Yin aplica el sonido. Android la envía a tu salida.",15,YinUi.MUTED));
        if(!b.getBoolean("ok")){LinearLayout empty=u.card(body,"SIN MÚSICA","Esperando a Yang");empty.addView(u.text(b.getString("message","Activa Yin y reproduce una canción en Yang."),14,YinUi.MUTED));return;}
        Bundle source=b.getBundle("source");if(source==null)source=new Bundle();
        LinearLayout yang=u.card(body,"01 / ARCHIVO ORIGINAL","Yang");
        String title=source.getString("sourceTitle","");if(!title.isEmpty()){yang.addView(u.text(title,14,YinUi.MUTED));u.gap(yang,8);}
        String original=source.getString("sourceCodec","");if(original.isEmpty())original="Formato no informado";
        int originalRate=source.getInt("sourceRate"),bits=source.getInt("sourceBits");
        original+=" · "+(originalRate>0?AudioSignalInfo.frequency(originalRate):"frecuencia no informada")+" · "+(bits>0?bits+" bits":"profundidad no informada");
        yang.addView(u.title(original,20));u.gap(yang,10);u.detail(yang,"EQ y preamplificador","Sin aplicar en Yang");
        yang.addView(u.text("Yang abre el archivo y envía el audio a Yin sin aplicar sus efectos. Un FLAC viaja como PCM (audio decodificado), conservando su frecuencia; no se vuelve a comprimir.",12,YinUi.MUTED));arrow();
        LinearLayout yin=u.card(body,"02 / AUDIO RECIBIDO","Yin");
        yin.addView(u.title("PCM · "+signal(b.getInt("inputRate"),b.getInt("inputEncoding")),20));u.gap(yin,10);
        yin.addView(u.text("Esto es lo que Yin recibe de Yang, antes de aplicar efectos.",13,YinUi.MUTED));
        if(originalRate>0&&originalRate!=b.getInt("inputRate"))u.detail(yin,"Cambio antes de Yin",AudioSignalInfo.frequency(originalRate)+" → "+AudioSignalInfo.frequency(b.getInt("inputRate")));
        if(bits>0&&b.getInt("inputEncoding")==4)yin.addView(u.text("El archivo tiene "+bits+" bits; el decodificador lo entrega en un contenedor float de 32 bits.",12,YinUi.MUTED));arrow();
        LinearLayout output=u.card(body,"03 / SALIDA DE YIN",b.getBoolean("direct")?"Salida directa":"Salida estándar de Android");
        output.addView(u.title("PCM · "+signal(b.getInt("rate"),b.getInt("encoding")),20));u.gap(output,10);
        u.detail(output,"Yin solicitó",signal(b.getInt("requestedRate"),b.getInt("requestedEncoding")));
        u.detail(output,"AudioTrack aceptó",signal(b.getInt("rate"),b.getInt("encoding")));
        u.detail(output,"EQ aplicado",b.getBoolean("eq")?"Sí":"No");
        u.detail(output,"Efectos activos",b.getString("effects","Sin efectos adicionales"));u.detail(output,"Perfil",b.getString("deviceProfile","Por conexión"));
        u.detail(output,"Preamplificador",b.getDouble("preampDb")==0?"Sin cambio":String.format(Locale.ROOT,"%+.2f dB",b.getDouble("preampDb")));
        u.detail(output,"Ganancia final",String.format(Locale.ROOT,"%+.1f dB",b.getDouble("gainDb")));
        u.detail(output,"Protección de picos",b.getBoolean("limiter")?"Aplicada":"Sin aplicar");
        u.detail(output,"Cambio de frecuencia",b.getInt("inputRate")==b.getInt("rate")?"No":AudioSignalInfo.frequency(b.getInt("inputRate"))+" → "+AudioSignalInfo.frequency(b.getInt("rate")));
        u.detail(output,"Flags",b.getInt("flags",-1)<0?"No disponibles":"0x"+Integer.toHexString(b.getInt("flags"))+(b.getBoolean("direct")?" · DIRECT":""));
        u.detail(output,"Búfer",b.getInt("rate")>0?Math.round(b.getInt("bufferFrames")*1000.0/b.getInt("rate"))+" ms":"No informado");
        output.addView(u.text("AudioTrack aceptado es la salida de la app. Consulta abajo para ver qué reporta Android después.",12,YinUi.MUTED));arrow();
        boolean valid=read!=null&&YinRouteRead.live(b)&&read.key.equals(YinRouteRead.key(b));
        LinearLayout hal=u.card(body,"04 / COMPROBAR LA SALIDA","Android / HAL");
        u.detail(hal,"Audio enviado por Yin",signal(b.getInt("rate"),b.getInt("encoding")));
        if(read==null)hal.addView(u.text("Falta consultar Android para saber si conservó ese formato.",14,YinUi.MUTED));
        else if(!valid)hal.addView(u.text("Cambió la reproducción o la conexión. Vuelve a consultar la salida.",14,YinUi.MUTED));
        else if(!read.error.isEmpty())hal.addView(u.text("No se pudo confirmar la salida. Reproduce música y vuelve a consultar.",14,YinUi.MUTED));
        else {
            int actualRate=number(read.halRate);boolean explicit=actualRate>0;if(!explicit)actualRate=number(read.threadRate);
            int actualEncoding=halEncoding(read.format);
            hal.addView(u.title("Android informa",16));hal.addView(u.title(signal(actualRate,actualEncoding),24));u.gap(hal,12);
            if(actualRate>0&&actualRate!=b.getInt("rate"))hal.addView(u.text("Android cambia la frecuencia: "+AudioSignalInfo.frequency(b.getInt("rate"))+" → "+AudioSignalInfo.frequency(actualRate),16,0xfff5c58a));
            if(actualEncoding>0&&actualEncoding!=b.getInt("encoding"))hal.addView(u.text("Android cambia el formato: "+pcm(b.getInt("encoding"))+" → "+pcm(actualEncoding),15,0xfff5c58a));
            if(actualRate==b.getInt("rate")&&actualEncoding>0&&actualEncoding==b.getInt("encoding"))hal.addView(u.text("Android reporta el mismo formato que envía Yin.",15,YinUi.ACCENT));
            if(actualRate<=0||actualEncoding<=0)hal.addView(u.text("La lectura está incompleta; no se puede confirmar todo el formato.",14,YinUi.MUTED));
            hal.addView(u.text(explicit?"Frecuencia y formato reportados por la HAL.":"Frecuencia de la salida en AudioFlinger y formato reportado por la HAL.",12,YinUi.MUTED));
            u.detail(hal,"Destino",b.getString("device","No informado"));
        }
        Button consult=u.button(busy?"Consultando salida…":"Consultar salida real · HAL",this::query);consult.setEnabled(!busy);hal.addView(consult);
        if(read!=null){age=u.text("Última consulta hace "+Math.max(0,(SystemClock.elapsedRealtime()-read.observed)/1000)+" s",12,YinUi.MUTED);hal.addView(age);}
        hal.addView(u.text("La lectura comprueba lo que informa Android; no mide la señal analógica del DAC.",12,YinUi.MUTED));
        if(read!=null&&valid)hal.addView(u.button("Detalle técnico · HAL y ALSA",()->{String detail="Sesión de Yin: "+b.getInt("sessionId")+"\nDispositivo: "+b.getInt("deviceId")+"\nUnderruns: "+b.getInt("underruns")+"\n\n"+read.error+"\n"+read.raw+"\n\nALSA · activos del sistema\n"+read.alsa+"\n\nEstos flujos ALSA no están vinculados de forma confirmada a Yin. Un contenedor de 32 bits no demuestra 32 bits de resolución física.";TextView text=u.text(detail,12,YinUi.INK);text.setTextIsSelectable(true);text.setPadding(u.d(20),u.d(12),u.d(20),u.d(12));ScrollView scroll=new ScrollView(a);scroll.addView(text);new android.app.AlertDialog.Builder(a).setTitle("Detalle de la consulta").setView(scroll).setPositiveButton("Cerrar",null).show();}));
        if("bluetooth".equals(b.getString("route"))){
            LinearLayout bt=u.card(body,"TRANSPORTE INALÁMBRICO","Bluetooth");
            bluetoothButton=u.button(bluetoothBusy?"Consultando…":"Consultar códec",this::queryBluetooth);bluetoothButton.setEnabled(!bluetoothBusy);bt.addView(bluetoothButton,new LinearLayout.LayoutParams(-1,-2));u.gap(bt,12);
            bluetoothResult=u.text(bluetoothText(),13,YinUi.INK);bluetoothResult.setTextIsSelectable(true);bt.addView(bluetoothResult,new LinearLayout.LayoutParams(-1,-2));
        }
        StringBuilder text=new StringBuilder("Yin Control · Ruta de audio\n");appendText(body,text);report=text.toString();
        body.addView(u.button("Copiar resumen",()->{StringBuilder current=new StringBuilder("Yin Control · Ruta de audio\n");appendText(body,current);a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Ruta de audio Yin",current.toString()));Toast.makeText(a,"Resumen copiado",Toast.LENGTH_SHORT).show();}));
    }
    private String bluetoothText(){BluetoothCodecs.Snapshot value=BluetoothCodecs.latest();if(value==null)return bluetoothMessage.isEmpty()?"Consulta el códec que Android está usando con tus auriculares.":bluetoothMessage;return value.device()+"\n"+value.description()+"\nLectura hace "+Math.max(0,(SystemClock.elapsedRealtime()-value.observed)/1000)+" s";}
    private void queryBluetooth(){if(bluetoothBusy)return;bluetoothBusy=true;if(bluetoothButton!=null){bluetoothButton.setEnabled(false);bluetoothButton.setText("Consultando…");}BluetoothCodecs.request(a,null,null,message->{bluetoothBusy=false;bluetoothMessage=message;if(a.isDestroyed())return;if(bluetoothButton!=null){bluetoothButton.setEnabled(true);bluetoothButton.setText("Consultar códec");}if(bluetoothResult!=null){bluetoothResult.setText(bluetoothText());bluetoothResult.requestLayout();bluetoothResult.post(()->{if(bluetoothResult!=null&&bluetoothResult.isAttachedToWindow()){android.graphics.Rect area=new android.graphics.Rect(0,0,bluetoothResult.getWidth(),bluetoothResult.getHeight());bluetoothResult.requestRectangleOnScreen(area,false);}});}});}
    private static void appendText(android.view.View v,StringBuilder out){if(v instanceof TextView&&!(v instanceof Button))out.append(((TextView)v).getText()).append('\n');if(v instanceof android.view.ViewGroup){android.view.ViewGroup g=(android.view.ViewGroup)v;for(int i=0;i<g.getChildCount();i++)appendText(g.getChildAt(i),out);}}
}
