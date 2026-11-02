package local.v60.player;

import android.content.*;
import android.content.res.ColorStateList;
import android.view.*;
import android.widget.*;

/** Sound snapshots, reorderable signal chain and stereo trims, with plain labels. */
final class YinStudioControls {
    private final MainActivity a;private final YinUi u;private final AudioSettings s;private final Runnable save,redraw;
    YinStudioControls(MainActivity a,AudioSettings s,Runnable save,Runnable redraw){this.a=a;u=new YinUi(a);this.s=s;this.save=save;this.redraw=redraw;}
    void show(LinearLayout parent){
        LinearLayout meters=u.card(parent,"","Tu señal, de un vistazo");TextView abStatus=u.text(AbComparison.status(a,s),12,YinUi.MUTED);
        meters.addView(new SignalMeterView(a,s.storageKey,()->{String text=AbComparison.status(a,s);if(!text.contentEquals(abStatus.getText()))abStatus.setText(text);}),new LinearLayout.LayoutParams(-1,u.d(130)));
        meters.addView(u.text("Barras del audio digital antes y después del procesado. No indican cuánto volumen llega a tus oídos.",11,YinUi.MUTED));
        LinearLayout ab=u.card(parent,"","Comparar A / B");ab.addView(u.text("Guarda un sonido en A, cambia sus ajustes y guarda el otro en B. Después alterna para decidir cuál prefieres.",12,YinUi.MUTED));u.gap(ab,8);
        String active=AbComparison.active(a,s);LinearLayout listen=u.row(),store=u.row();for(String slot:new String[]{"A","B"}){
            Button button=u.button("Escuchar "+slot,()->{try{AbComparison.apply(a,s,slot);redraw.run();}catch(Exception error){Toast.makeText(a,"No se pudo recuperar el ajuste",Toast.LENGTH_SHORT).show();}});button.setEnabled(AbComparison.exists(a,s,slot));if(active.equals(slot)){button.setTextColor(YinUi.BG);button.setBackground(u.shape(YinUi.ACCENT,16));}add(listen,button);
            add(store,u.button("Guardar actual en "+slot,()->{AbComparison.capture(a,s,slot);redraw.run();}));
        }ab.addView(listen);ab.addView(store);Switch match=new Switch(a);u.tint(match);match.setText("Igualar el nivel medio");match.setTextSize(13);match.setTextColor(YinUi.INK);match.setChecked(AbComparison.matching(a,s));match.setOnCheckedChangeListener((b,on)->{AbComparison.matching(a,s,on);abStatus.setText(AbComparison.status(a,s));});ab.addView(match);u.gap(ab,8);ab.addView(abStatus);
        ab.addView(u.text("La estimación necesita al menos 5 segundos de señal en cada ajuste. Compara el mismo fragmento para reducir diferencias. Solo baja el ajuste más fuerte; no aumenta el más suave. No es una igualación perceptual exacta.",11,YinUi.MUTED));
        LinearLayout chain=u.card(parent,"","Orden de los efectos");chain.addView(u.text("El sonido pasa de arriba hacia abajo. Mantén pulsada una fila y arrástrala, o usa las flechas.",12,YinUi.MUTED));u.gap(chain,8);
        for(int i=0;i<s.dsp.order.length;i++){final int index=i;String id=s.dsp.order[i];LinearLayout row=u.row();row.setPadding(u.d(8),u.d(4),u.d(4),u.d(4));row.setBackground(u.shape(YinUi.SOFT,16));
            Switch toggle=new Switch(a);u.tint(toggle);toggle.setText((i+1)+" · "+DspSettings.name(id));toggle.setTextColor(YinUi.INK);toggle.setTextSize(12);toggle.setChecked(s.dsp.enabled(id,s.eq));toggle.setOnCheckedChangeListener((b,on)->{if(id.equals("eq"))s.eq=on;else s.dsp.enable(id,on);save.run();});row.addView(toggle,new LinearLayout.LayoutParams(0,-2,1));
            for(int direction:new int[]{-1,1}){Button move=u.button(direction<0?"↑":"↓",()->move(index,index+direction));move.setPadding(0,0,0,0);move.setTextSize(16);move.setMinWidth(0);move.setMinimumWidth(0);move.setEnabled(index+direction>=0&&index+direction<s.dsp.order.length);move.setContentDescription((direction<0?"Subir ":"Bajar ")+DspSettings.name(id));row.addView(move,new LinearLayout.LayoutParams(u.d(32),u.d(44)));}
            row.setOnLongClickListener(v->v.startDragAndDrop(ClipData.newPlainText("yin-stage",id),new View.DragShadowBuilder(v),id,0));toggle.setOnLongClickListener(v->row.performLongClick());row.setContentDescription("Mantén pulsado para mover "+DspSettings.name(id));
            row.setOnDragListener((v,e)->{if(!(e.getLocalState() instanceof String))return false;switch(e.getAction()){case DragEvent.ACTION_DRAG_STARTED:return true;case DragEvent.ACTION_DRAG_ENTERED:v.setAlpha(.6f);return true;case DragEvent.ACTION_DRAG_EXITED:case DragEvent.ACTION_DRAG_ENDED:v.setAlpha(1);return true;case DragEvent.ACTION_DROP:v.setAlpha(1);int from=java.util.Arrays.asList(s.dsp.order).indexOf((String)e.getLocalState());if(from>=0)v.post(()->move(from,index));return true;default:return true;}});
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=u.d(8);chain.addView(row,lp);
        }
        chain.addView(u.text("Preamplificador y margen van primero. La protección y el limitador siempre van al final. Una etapa apagada conserva su sitio.",11,YinUi.MUTED));
        LinearLayout channels=u.card(parent,"","Cada oído por separado");Switch enabled=new Switch(a);u.tint(enabled);enabled.setText("Activar ajustes de canales");enabled.setTextColor(YinUi.INK);enabled.setTextSize(13);enabled.setChecked(s.dsp.channelsEnabled);enabled.setOnCheckedChangeListener((b,on)->{s.dsp.channelsEnabled=on;save.run();redraw.run();});channels.addView(enabled);
        if(s.dsp.channelsEnabled){slider(channels,"Volumen izquierdo",(int)Math.round(s.dsp.leftGain*10),-240,60,v->s.dsp.leftGain=v/10.0,v->String.format(java.util.Locale.ROOT,"%.1f dB",v/10.0));slider(channels,"Volumen derecho",(int)Math.round(s.dsp.rightGain*10),-240,60,v->s.dsp.rightGain=v/10.0,v->String.format(java.util.Locale.ROOT,"%.1f dB",v/10.0));slider(channels,"Retardo izquierdo",s.dsp.leftDelay,0,200,v->s.dsp.leftDelay=v,v->String.format(java.util.Locale.ROOT,"%.1f ms",v/10.0));slider(channels,"Retardo derecho",s.dsp.rightDelay,0,200,v->s.dsp.rightDelay=v,v->String.format(java.util.Locale.ROOT,"%.1f ms",v/10.0));channels.addView(u.button("Igualar ambos oídos",()->{s.dsp.leftGain=s.dsp.rightGain=0;s.dsp.leftDelay=s.dsp.rightDelay=0;save.run();redraw.run();}));}
        channels.addView(u.text("En el EQ paramétrico puedes elegir Ambos, Izquierdo o Derecho para cada banda. Los retardos y ganancias por canal se aplican solo a audio estéreo.",11,YinUi.MUTED));
    }
    private void move(int from,int to){if(from==to||to<0||to>=s.dsp.order.length)return;java.util.ArrayList<String> order=new java.util.ArrayList<>(java.util.Arrays.asList(s.dsp.order));String id=order.remove(from);order.add(to,id);s.dsp.order=order.toArray(new String[0]);save.run();redraw.run();}
    private void add(LinearLayout p,View b){LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(u.d(3),u.d(3),u.d(3),u.d(3));p.addView(b,lp);}
    private void slider(LinearLayout p,String name,int initial,int min,int max,java.util.function.IntConsumer set,java.util.function.IntFunction<String> display){TextView label=u.text(name+" · "+display.apply(initial),12,YinUi.MUTED);p.addView(label);SeekBar b=new SeekBar(a);b.setMax(max-min);b.setProgress(initial-min);b.setContentDescription(name);b.setProgressTintList(ColorStateList.valueOf(YinUi.ACCENT));b.setThumbTintList(ColorStateList.valueOf(YinUi.ACCENT));b.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar v,int n,boolean user){if(user){set.accept(n+min);label.setText(name+" · "+display.apply(n+min));}}public void onStartTrackingTouch(SeekBar v){}public void onStopTrackingTouch(SeekBar v){save.run();}});p.addView(b,new LinearLayout.LayoutParams(-1,u.d(44)));}
}
