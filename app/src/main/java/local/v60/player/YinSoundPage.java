package local.v60.player;



import android.content.res.ColorStateList;

import android.widget.*;

import java.util.*;

import java.util.function.IntConsumer;



/** Output preferences are edited independently from live route measurements. */

final class YinSoundPage {

    private final MainActivity a;

    private final YinUi u;

    final ScrollView view;

    private final LinearLayout body;

    private String profile;

    private AudioSettings s;

    private int section;

    private int eqBand;

    private Runnable dacPaint;

    private LinearLayout settingsBody;

    private final TextView[] sectionTabs=new TextView[5];

    private final android.view.View[] dots=new android.view.View[4];

    private final TextView[] routeStates=new TextView[4],routeNames=new TextView[4],routeFormats=new TextView[4];

    YinSoundPage(MainActivity a){this.a=a;u=new YinUi(a);profile=AudioSettings.route(AudioSettings.predictedDevice(a));view=new ScrollView(a);view.setVerticalScrollBarEnabled(false);body=u.column();body.setPadding(u.d(20),u.d(12),u.d(20),u.d(20));view.addView(body);render();}

    private void save(){s.save(a);}

    private void options(LinearLayout p,String title,String[] values,int selected,int columns,IntConsumer action){

        u.gap(p,12);p.addView(u.text(title,12,YinUi.MUTED));u.gap(p,5);

        for(int start=0;start<values.length;start+=columns){LinearLayout row=u.row();for(int col=0;col<columns;col++){int i=start+col;if(i>=values.length){row.addView(new android.view.View(a),new LinearLayout.LayoutParams(0,1,1));continue;}final int choice=i;Button b=u.button(values[i],()->{if(choice==selected)return;int y=view.getScrollY();action.accept(choice);save();render();view.post(()->view.scrollTo(0,y));});b.setTextSize(12);b.setPadding(u.d(5),u.d(8),u.d(5),u.d(8));b.setMinWidth(0);b.setMinimumWidth(0);b.setMinHeight(u.d(42));b.setMinimumHeight(u.d(42));b.setSelected(i==selected);b.setBackground(u.shape(i==selected?YinUi.ACCENT:YinUi.SOFT,14));b.setTextColor(i==selected?YinUi.BG:YinUi.INK);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(u.d(3),u.d(3),u.d(3),u.d(3));row.addView(b,lp);}p.addView(row);}

    }

    private void toggle(LinearLayout p,String name,boolean checked,java.util.function.Consumer<Boolean> change){Switch v=new Switch(a);u.tint(v);v.setText(name);v.setTextSize(14);v.setTextColor(YinUi.INK);v.setPadding(0,u.d(10),0,u.d(10));v.setChecked(checked);v.setOnCheckedChangeListener((b,on)->{change.accept(on);save();});p.addView(v);}

    private void slider(LinearLayout p,String name,int value,int min,int max,IntConsumer change){TextView label=u.text(name+"   "+value,13,YinUi.MUTED);p.addView(label);SeekBar bar=new SeekBar(a);bar.setMax(max-min);bar.setProgress(value-min);bar.setProgressTintList(ColorStateList.valueOf(YinUi.ACCENT));bar.setThumbTintList(ColorStateList.valueOf(YinUi.ACCENT));bar.setContentDescription(name);bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int v,boolean user){if(user){label.setText(name+"   "+(v+min));change.accept(v+min);}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){save();}});p.addView(bar,new LinearLayout.LayoutParams(-1,u.d(46)));}

    private void render(){

        s=AudioSettings.load(a,profile);

        if(settingsBody==null){

        body.addView(a.controllerBar());u.gap(body,12);

        int current=Arrays.asList(AudioSettings.PROFILES).indexOf(profile);

        ConnectionSlider carousel=new ConnectionSlider(a,current,i->{profile=AudioSettings.PROFILES[i];render();});

        for(int i=0;i<4;i++){

            LinearLayout page=u.column();page.setPadding(u.d(16),u.d(14),u.d(16),u.d(14));page.setBackground(u.shape(YinUi.CARD,22));

            routeStates[i]=u.text("",10,YinUi.ACCENT);routeStates[i].setLetterSpacing(.04f);page.addView(routeStates[i]);u.gap(page,8);

            LinearLayout heading=u.row();Button icon=MusicStyle.icon(a,new String[]{"speaker","headphones","bluetooth","usb"}[i],AudioSettings.PROFILE_NAMES[i],YinUi.ACCENT,YinUi.SELECTED,23);icon.setClickable(false);icon.setFocusable(false);heading.addView(icon,new LinearLayout.LayoutParams(u.d(44),u.d(44)));

            routeNames[i]=u.title(AudioSettings.PROFILE_NAMES[i],18);routeNames[i].setPadding(u.d(12),0,0,0);routeNames[i].setMaxLines(2);heading.addView(routeNames[i],new LinearLayout.LayoutParams(0,-2,1));page.addView(heading);u.gap(page,8);

            routeFormats[i]=u.text("",12,YinUi.MUTED);routeFormats[i].setMaxLines(2);routeFormats[i].setEllipsize(android.text.TextUtils.TruncateAt.END);page.addView(routeFormats[i]);

            FrameLayout frame=new FrameLayout(a);FrameLayout.LayoutParams cardLp=new FrameLayout.LayoutParams(-1,-2);cardLp.setMargins(u.d(6),0,u.d(6),0);frame.addView(page,cardLp);carousel.pages.addView(frame,new LinearLayout.LayoutParams(1,-2));

        }

        body.addView(carousel,new LinearLayout.LayoutParams(-1,-2));refreshRoutes();

        LinearLayout steps=u.row();steps.setGravity(android.view.Gravity.CENTER);

        for(int i=0;i<4;i++){final int n=i;FrameLayout hit=new FrameLayout(a);hit.setContentDescription(AudioSettings.PROFILE_NAMES[i]);hit.setFocusable(true);hit.setOnClickListener(v->carousel.select(n,true));android.view.View dot=new android.view.View(a);dots[i]=dot;dot.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams p=new FrameLayout.LayoutParams(u.d(i==current?16:5),u.d(4),android.view.Gravity.CENTER);hit.addView(dot,p);steps.addView(hit,new LinearLayout.LayoutParams(u.d(30),u.d(30)));}body.addView(steps);u.gap(body,4);

        LinearLayout tabs=u.row();tabs.setPadding(u.d(4),u.d(4),u.d(4),u.d(4));tabs.setBackground(u.shape(YinUi.CARD,24));String[] namesTab={"Salida","EQ","Volumen","Espacio","Estudio"};

        for(int i=0;i<5;i++){final int tab=i;TextView label=u.text(namesTab[i],11,section==i?YinUi.ACCENT:YinUi.MUTED);sectionTabs[i]=label;label.setGravity(android.view.Gravity.CENTER);label.setPadding(0,u.d(12),0,u.d(12));if(section==i)label.setBackground(u.shape(YinUi.SELECTED,20));label.setOnClickListener(v->{if(section==tab)return;section=tab;render();});tabs.addView(label,new LinearLayout.LayoutParams(0,-2,1));}body.addView(tabs);u.gap(body,12);

        settingsBody=u.column();body.addView(settingsBody);

        }

        for(int i=0;i<5;i++){sectionTabs[i].setTextColor(section==i?YinUi.ACCENT:YinUi.MUTED);sectionTabs[i].setBackground(section==i?u.shape(YinUi.SELECTED,20):null);}

        for(int i=0;i<4;i++){boolean selected=profile.equals(AudioSettings.PROFILES[i]);dots[i].setBackground(u.shape(selected?YinUi.ACCENT:0xff45525b,3));FrameLayout.LayoutParams p=(FrameLayout.LayoutParams)dots[i].getLayoutParams();p.width=u.d(selected?16:5);dots[i].setLayoutParams(p);}

        LinearLayout body=settingsBody;body.removeAllViews();dacPaint=null;

        if(section==0){

        if(profile.equals("bluetooth"))bluetoothCard(body);

        LinearLayout out=u.card(body,"","Salida · "+AudioSettings.name(profile));

        Switch perDevice=new Switch(a);u.tint(perDevice);perDevice.setText("Perfil automático por dispositivo");perDevice.setTextColor(YinUi.INK);perDevice.setTextSize(12);perDevice.setChecked(DeviceProfiles.enabled(a));perDevice.setOnCheckedChangeListener((b,on)->{a.getSharedPreferences("yin_control",0).edit().putBoolean("device_profiles",on).apply();YinService.settingsChanged();render();});out.addView(perDevice);out.addView(u.text(s.storageKey.equals(s.profile)?"Editando el perfil general de esta conexión":"Editando ajustes propios del dispositivo conectado",11,YinUi.MUTED));

        if(profile.equals("wired")||profile.equals("bluetooth"))options(out,"Motor de salida",new String[]{"AudioTrack","PCM directo"},s.direct?1:0,2,i->{s.direct=i==1;if(s.direct&&s.encoding==0)s.encoding=2;if(!s.direct&&s.rate>192000)s.rate=0;});

        if(profile.equals("speaker"))u.detail(out,"Formato fijo","48 kHz · 16 bits");

        else {

        List<Integer> rates=new ArrayList<>();for(int n:AudioSettings.RATES)if(s.acceptsRate(n))rates.add(n);

        String[] names=rates.stream().map(n->n==0?"Automática":AudioSignalInfo.frequency(n)+(n==352800?" *":"")).toArray(String[]::new);

        options(out,"Frecuencia de muestreo",names,rates.indexOf(s.rate),3,i->{s.rate=rates.get(i);if(s.rate>192000)s.encoding=3;});

        List<Integer> enc=new ArrayList<>();for(int i=0;i<4;i++){AudioSettings test=s.copy();test.encoding=i;test.validate();if(test.encoding==i)enc.add(i);}

        String[] formats={"Float 32 bits","PCM 16 bits","PCM 24 bits","PCM 32 bits"};

        options(out,"Profundidad de bits",enc.stream().map(i->formats[i].replace("PCM ","")).toArray(String[]::new),enc.indexOf(s.encoding),Math.min(4,enc.size()),i->{s.encoding=enc.get(i);if(s.encoding==0)s.direct=false;if(s.encoding!=3&&s.rate>192000)s.rate=192000;});

        if(profile.equals("wired"))out.addView(u.text("Float usa AudioTrack. 352,8/384 kHz requieren salida directa y PCM entero de 32 bits. *352,8 kHz es experimental; verifica la salida en Ruta de audio.",11,YinUi.MUTED));

        if(profile.equals("bluetooth"))out.addView(u.text("Máximo de Yin: 96 kHz / 24 bits. La transmisión depende del códec que Android negocie.",11,YinUi.MUTED));

        if(profile.equals("usb"))out.addView(u.text("Sin un DAC USB no se ha medido la salida. 192 kHz es el límite actual de este motor, no el máximo físico de USB.",11,YinUi.MUTED));

        }

        options(out,"Búfer",Arrays.stream(AudioSettings.BUFFERS).mapToObj(n->n+" ms").toArray(String[]::new),index(AudioSettings.BUFFERS,s.bufferMs),4,i->s.bufferMs=AudioSettings.BUFFERS[i]);

        options(out,"Remuestreador",new String[]{"SWR","SoXR"},s.resampler,2,i->s.resampler=i);

        }

        if(section==1){

        LinearLayout response=u.card(body,"",s.eq?"Así cambia tu EQ":"Curva guardada · EQ apagado");response.addView(new EqResponseView(a,s,eqBand,i->view.post(()->{eqBand=i;refreshSettings();})),new LinearLayout.LayoutParams(-1,u.d(190)));response.addView(u.text("Toca un punto para elegir una banda. Izquierdo: línea continua. Derecho: línea discontinua. Curva calculada del EQ; no incluye el IR ni otros efectos.",11,YinUi.MUTED));

        LinearLayout eq=u.card(body,"03 / ECUALIZADOR","Dale tu forma al sonido");Switch power=new Switch(a);u.tint(power);power.setText(s.eq?"EQ encendido · sonido ajustado":"EQ apagado · sonido sin ecualizar");power.setTextColor(s.eq?YinUi.ACCENT:YinUi.MUTED);power.setTextSize(13);power.setChecked(s.eq);power.setOnCheckedChangeListener((b,on)->{s.eq=on;save();view.post(this::refreshSettings);});eq.addView(power);

        eq.addView(u.text(s.eq?"Las bandas coloreadas están modificando el sonido.":"Tus bandas quedan guardadas. Enciende el EQ para editarlas y escucharlas.",11,YinUi.MUTED));

        eq.addView(u.text("±12 dB · desliza cada banda verticalmente",12,YinUi.MUTED));u.gap(eq,12);

        LinearLayout bank=u.row();bank.setAlpha(s.eq?1f:.38f);bank.setClipChildren(false);eq.addView(bank,new LinearLayout.LayoutParams(-1,-2));

        for(int i=0;i<10;i++){

            final int band=i;int hz=s.dsp.frequency(i);LinearLayout slot=u.column();slot.setGravity(android.view.Gravity.CENTER);slot.setClipChildren(false);

            double value=s.dsp.gain(i,s.gains[i]);TextView gain=u.text(s.dsp.parametric?String.format(java.util.Locale.ROOT,"%.1f",value):String.valueOf(s.gains[i]),10,YinUi.ACCENT);gain.setGravity(android.view.Gravity.CENTER);slot.addView(gain);

            GainFader fader=new GainFader(a,hz,(int)Math.round(value),v->{s.gains[band]=v;if(s.dsp.parametric)s.dsp.gain[band]=v;gain.setText(String.valueOf(v));},()->{save();view.post(this::refreshSettings);});fader.setEnabled(s.eq);slot.addView(fader,new LinearLayout.LayoutParams(-1,u.d(172)));

            TextView hzLabel=u.text(hz>=1000?String.format(java.util.Locale.ROOT,"%.1fk",hz/1000.0).replace(".0k","k"):String.valueOf(hz),10,YinUi.MUTED);hzLabel.setGravity(android.view.Gravity.CENTER);slot.addView(hzLabel);bank.addView(slot,new LinearLayout.LayoutParams(0,-2,1));

        }

        eq.addView(u.button("Restablecer bandas",()->{Arrays.fill(s.gains,0);Arrays.fill(s.dsp.gain,0);save();render();}));

        new YinDspControls(a,s,this::save,this::refreshSettings).eq(body,eqBand,i->{eqBand=i;refreshSettings();});

        }

        if(section==2){

        LinearLayout dsp=u.card(body,"04 / GANANCIA","Volumen y protección");

        if(s.dsp.preampFraction!=0)u.detail(dsp,"Preamp importado",String.format(java.util.Locale.ROOT,"%.2f dB",s.preamp+s.dsp.preampFraction));

        slider(dsp,"Preamplificador · dB",s.preamp,-24,12,v->{s.preamp=v;s.dsp.preampFraction=0;});slider(dsp,"Balance · L / R",s.balance,-100,100,v->s.balance=v);

        toggle(dsp,"Margen automático para el EQ",s.headroom,v->s.headroom=v);toggle(dsp,"Limitador de picos",s.limiter,v->s.limiter=v);

        new YinDspControls(a,s,this::save,this::refreshSettings).dynamics(body);

        }

        if(section==3)new YinDspControls(a,s,this::save,this::refreshSettings).space(body);

        if(section==4)new YinStudioControls(a,s,this::save,this::refreshSettings).show(body);

        if(section==0&&profile.equals("wired")){

            dacCard(body);

        }

        body.addView(u.text("Los cambios se guardan por conexión y reinician la sesión de audio. Yang conserva la reproducción y la biblioteca.",12,YinUi.MUTED));

    }

    void refreshSettings(){int y=view.getScrollY();render();view.post(()->view.scrollTo(0,y));}

    private void dacCard(LinearLayout parent){

        LinearLayout card=u.card(parent,"","Amplificador Quad DAC");TextView state=u.text(DacGain.description(),12,YinUi.MUTED);card.addView(state);u.gap(card,10);

        LinearLayout row=u.row();Button[] modes=new Button[3];boolean[] busy={false};

        Button read=u.button("Actualizar modo del DAC",()->{});

        Runnable paint=()->{int confirmed=DacGain.confirmedMode();for(int i=0;i<3;i++){boolean active=confirmed==i+1;modes[i].setTextColor(active?YinUi.BG:YinUi.INK);modes[i].setBackground(u.shape(active?YinUi.ACCENT:YinUi.SOFT,16));modes[i].setEnabled(!busy[0]);}read.setEnabled(!busy[0]);};

        java.util.function.Consumer<Integer> request=mode->{if(busy[0])return;busy[0]=true;state.setText(mode==null?"Consultando DAC…":"Aplicando "+DacGain.name(mode)+"…");paint.run();DacGain.request(a,mode,message->{busy[0]=false;state.setText(message);paint.run();});};

        for(int i=0;i<3;i++){final int mode=i+1;Button b=u.button(new String[]{"Normal","Alta\nimpedancia","AUX"}[i],()->request.accept(mode));b.setTextSize(12);b.setPadding(u.d(4),u.d(6),u.d(4),u.d(6));b.setMinWidth(0);b.setMinimumWidth(0);b.setMaxLines(2);b.setContentDescription(DacGain.name(mode));modes[i]=b;LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,u.d(54),1);p.setMargins(u.d(3),0,u.d(3),0);row.addView(b,p);}card.addView(row);u.gap(card,10);read.setOnClickListener(v->request.accept(null));card.addView(read,new LinearLayout.LayoutParams(-1,-2));u.gap(card,8);

        card.addView(u.text("Normal: uso habitual. Alta impedancia: más ganancia. AUX: conexión a otra entrada de audio. Son modos del amplificador; no cambian bits ni kHz.",11,YinUi.MUTED));

        dacPaint=paint;paint.run();

    }

    private void bluetoothCard(LinearLayout parent){

        LinearLayout card=u.card(parent,"","");

        TextView codec=u.title("Bluetooth · sin consultar",16),quality=u.text("Actualiza para leer la conexión real.",12,YinUi.MUTED),feedback=u.text("",12,YinUi.MUTED);

        card.addView(codec);card.addView(quality);feedback.setMaxLines(3);feedback.setVisibility(android.view.View.GONE);feedback.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int start,int count,int after){}public void onTextChanged(CharSequence s,int start,int before,int count){feedback.setVisibility(s.length()==0?android.view.View.GONE:android.view.View.VISIBLE);}public void afterTextChanged(android.text.Editable s){}});

        LinearLayout choices=u.column();

        Runnable refresh=()->{

            BluetoothCodecs.Snapshot value=BluetoothCodecs.latest();choices.removeAllViews();

            if(value==null){codec.setText("Bluetooth · sin confirmar");return;}

            org.json.JSONObject current=value.data.optJSONObject("current");

            codec.setText(current==null?"Bluetooth · no informado":"Bluetooth · "+current.optString("name","Códec Android"));

            quality.setText(current==null?value.device():BluetoothCodecs.rate(current.optInt("rate"))+" · "+BluetoothCodecs.bits(current.optInt("bits")));

            org.json.JSONArray available=value.data.optJSONArray("available");if(available==null)return;

            LinearLayout row=null;

            for(int i=0;i<available.length();i++){

                org.json.JSONObject entry=available.optJSONObject(i);if(entry==null)continue;

                if(i%3==0){row=u.row();choices.addView(row);}final int type=entry.optInt("type");

                Button choice=u.button(entry.optString("name","Códec"),()->{

                    feedback.setText("Solicitando códec…");BluetoothCodecs.request(a,type,value.data.optString("address"),message->{feedback.setText(message);BluetoothCodecs.Snapshot now=BluetoothCodecs.latest();if(now!=null){codec.setText(now.description());quality.setText("Última lectura de Android");}});

                });choice.setTextSize(11);choice.setPadding(u.d(4),u.d(8),u.d(4),u.d(8));choice.setMinWidth(0);choice.setMinimumWidth(0);

                if(value.type()==type){choice.setTextColor(YinUi.BG);choice.setBackground(u.shape(YinUi.ACCENT,14));}

                LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(u.d(3),u.d(3),u.d(3),u.d(3));if(row!=null)row.addView(choice,lp);

            }

        };

        LinearLayout actions=u.row();Button update=u.button("Actualizar",()->{feedback.setText("Leyendo conexión…");BluetoothCodecs.request(a,null,null,message->{feedback.setText(message);refresh.run();});});

        Button maximum=u.button("Máxima calidad",()->{BluetoothCodecs.Snapshot value=BluetoothCodecs.latest();if(value==null){feedback.setText("Actualiza primero la conexión.");return;}feedback.setText("Solicitando LDAC…");BluetoothCodecs.maximum(a,value.data.optString("address"),message->{feedback.setText(message);refresh.run();});});

        LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(u.d(3),u.d(6),u.d(3),u.d(6));actions.addView(update,lp);actions.addView(maximum,new LinearLayout.LayoutParams(lp));card.addView(actions);

        card.addView(choices);card.addView(feedback);refresh.run();

    }

    void refreshRoutes(){

        if(dacPaint!=null)dacPaint.run();

        android.os.Bundle state=YinService.publicStatus();android.media.AudioDeviceInfo predicted=AudioSettings.predictedDevice(a);

        boolean active=state.getBoolean("ok")&&state.getBoolean("playing")&&!state.getBoolean("drained");String activeProfile=state.getString("route","");

        for(int i=0;i<4;i++){if(routeStates[i]==null)continue;String key=AudioSettings.PROFILES[i];boolean inUse=active&&key.equals(activeProfile);boolean connected=predicted!=null&&key.equals(AudioSettings.route(predicted));

            routeStates[i].setText(inUse?"EN USO AHORA":connected?"CONECTADO · SIN REPRODUCCIÓN":key.equals("speaker")?"ALTAVOZ DISPONIBLE":"PERFIL GUARDADO");

            routeNames[i].setText(inUse?state.getString("device",AudioSettings.name(key)):connected?predicted.getProductName().toString():AudioSettings.name(key));

            AudioSettings pref=AudioSettings.load(a,key);String preferred=(pref.rate==0?"Frecuencia automática":AudioSignalInfo.frequency(pref.rate))+" · "+new String[]{"Float 32 bits","16 bits","24 bits","32 bits"}[pref.encoding];

            routeFormats[i].setText(inUse?"Reproduciendo a "+AudioSignalInfo.frequency(state.getInt("rate"))+" · "+AudioSettings.encodingName(state.getInt("encoding"))+"\n"+(state.getBoolean("direct")?"Salida directa activa":"Salida estándar de Android"):preferred+"\nAjustes independientes para "+AudioSettings.name(key));

        }

    }

    private static int index(int[] values,int value){for(int i=0;i<values.length;i++)if(values[i]==value)return i;return 0;}

}

