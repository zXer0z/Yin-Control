package local.v60.player;



import android.content.SharedPreferences;

import android.view.WindowManager;

import android.widget.*;



/** App preferences are independent from per-output sound profiles. */

final class YinConfigPage {

    final ScrollView view;

    private final MainActivity a;private final YinUi u;

    YinConfigPage(MainActivity a){this.a=a;u=new YinUi(a);view=new ScrollView(a);view.setVerticalScrollBarEnabled(false);render();}

    private void render(){view.removeAllViews();LinearLayout body=u.column();body.setPadding(u.d(20),u.d(12),u.d(20),u.d(20));view.addView(body);SharedPreferences prefs=a.getSharedPreferences("yin_control",0);

        LinearLayout look=u.card(body,"","A tu gusto");look.addView(u.text("Color de acento",13,YinUi.MUTED));String[] names={"Menta","Lavanda","Azul","Melocotón","Rosa","Arena"};

        for(int start=0;start<6;start+=3){LinearLayout row=u.row();for(int col=0;col<3;col++){final int index=start+col;Button b=u.button(names[index],()->{prefs.edit().putInt("accent",index).apply();a.reloadAppearance();});b.setTextColor(YinUi.ACCENTS[index]);b.setBackground(u.shape(YinUi.mix(YinUi.CARD,YinUi.ACCENTS[index],prefs.getInt("accent",0)==index?.28f:.10f),16));b.setTextSize(11);b.setMinWidth(0);b.setMinimumWidth(0);b.setPadding(u.d(3),u.d(10),u.d(3),u.d(10));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,-2,1);lp.setMargins(u.d(3),u.d(5),u.d(3),u.d(5));row.addView(b,lp);}look.addView(row);}

        Switch awake=new Switch(a);u.tint(awake);awake.setText("Mantener la pantalla encendida");awake.setTextSize(13);awake.setTextColor(YinUi.INK);awake.setChecked(prefs.getBoolean("screen_on",false));awake.setOnCheckedChangeListener((b,on)->{prefs.edit().putBoolean("screen_on",on).apply();screen();});look.addView(awake);screen();look.addView(u.text("Solo mientras el panel de Yin está abierto.",11,YinUi.MUTED));

        LinearLayout service=u.card(body,"","Servicio en segundo plano");Switch persistent=new Switch(a);u.tint(persistent);persistent.setText("Arrancar con el teléfono y recuperarse");persistent.setTextSize(13);persistent.setTextColor(YinUi.INK);persistent.setChecked(prefs.getBoolean("persistent",true));service.addView(persistent);TextView info=u.text("Supervisor root + servicio de audio. Espera a Yang sin mantener el teléfono despierto; el bloqueo de suspensión solo se usa al reproducir.",12,YinUi.MUTED);service.addView(info);

        persistent.setOnCheckedChangeListener((button,on)->{persistent.setEnabled(false);info.setText("Actualizando supervisor…");a.persistent(on,error->{persistent.setEnabled(true);if(error!=null){info.setText("No se pudo cambiar: "+error);view.post(this::render);}else info.setText(on?"Recuperación automática activada. Se instalará al activar el motor si todavía está apagado.":"Arranque automático desactivado. Puedes seguir usando el motor desde Yang.");});});

        service.addView(u.text("El motor se activa o apaga en Sonido. El supervisor no modifica el kernel ni procesa audio de otras aplicaciones.",11,YinUi.MUTED));

        LinearLayout reset=u.card(body,"","Volver a empezar");reset.addView(u.text("Restablece todos los perfiles de salida y de dispositivos: EQ plano, efectos apagados, balance centrado y salida estándar. También borra A/B. Conserva tu color, la biblioteca y los archivos originales.",12,YinUi.MUTED));u.gap(reset,10);

        Button restore=u.button("Restablecer todos los perfiles",()->{});boolean[] armed={false};Runnable disarm=()->{armed[0]=false;restore.setText("Restablecer todos los perfiles");};restore.setOnClickListener(v->{if(!armed[0]){armed[0]=true;restore.setText("Confirmar restablecimiento");restore.postDelayed(disarm,6000);return;}restore.removeCallbacks(disarm);a.getSharedPreferences("audio_settings",0).edit().clear().apply();a.getSharedPreferences("yin_ab",0).edit().clear().apply();YinService.settingsChanged();a.profilesReset();disarm.run();Toast.makeText(a,"Perfiles restablecidos",Toast.LENGTH_SHORT).show();});reset.addView(restore);

    }

    private void screen(){if(a.getSharedPreferences("yin_control",0).getBoolean("screen_on",false))a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);else a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);}

}

