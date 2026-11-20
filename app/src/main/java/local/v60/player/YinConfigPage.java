package local.v60.player;

import android.content.SharedPreferences;
import android.view.*;
import android.widget.*;

/** System console appearance, hardware diagnostics, root persistence and profile reset. */
final class YinConfigPage {
    final ScrollView view;
    private final MainActivity a;
    private final YinUi u;

    YinConfigPage(MainActivity a) {
        this.a = a;
        u = new YinUi(a);
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        render();
    }

    private void render() {
        view.removeAllViews();
        LinearLayout body = u.column();
        body.setPadding(u.d(14), u.d(12), u.d(14), u.d(24));
        view.addView(body);
        SharedPreferences prefs = a.getSharedPreferences("yin_control", 0);
        int currentAccent = prefs.getInt("accent", 0);

        LinearLayout look = u.module(body, "01 // CHASIS Y OLED", "Apariencia de consola");
        look.addView(u.mono("COLOR DE ACENTO", 10, YinUi.SUB, true));
        u.gap(look, 6);
        String[] names = {"Menta", "Cian", "Ámbar", "Lavanda", "Coral", "Titanio"};
        for (int start = 0; start < 6; start += 3) {
            LinearLayout row = u.row();
            for (int col = 0; col < 3; col++) {
                final int index = start + col;
                boolean selected = currentAccent == index;
                LinearLayout tile = u.row();
                tile.setPadding(u.d(10), u.d(10), u.d(10), u.d(10));
                int tint = YinUi.ACCENTS[index];
                tile.setBackground(u.ripple(
                    selected ? YinUi.mix(YinUi.PANEL, tint, .22f) : YinUi.WELL,
                    selected ? tint : YinUi.BORDER,
                    8
                ));
                tile.addView(new YinUi.LedDot(a, tint, 9), new LinearLayout.LayoutParams(u.d(9), u.d(9)));
                tile.addView(new View(a), new LinearLayout.LayoutParams(u.d(7), 1));
                TextView label = u.mono(names[index], 11, selected ? YinUi.INK : YinUi.SUB, selected);
                label.setSingleLine(true);
                tile.addView(label);
                tile.setOnClickListener(v -> {
                    prefs.edit().putInt("accent", index).apply();
                    a.reloadAppearance();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(col == 0 ? 0 : 3), u.d(3), u.d(col == 2 ? 0 : 3), u.d(3));
                row.addView(tile, lp);
            }
            look.addView(row);
        }
        u.divider(look);
        u.toggleRow(look, "Mantener pantalla encendida", "Solo mientras el panel de Yin está abierto",
            prefs.getBoolean("screen_on", false), on -> {
                prefs.edit().putBoolean("screen_on", on).apply();
                screen();
            });
        screen();

        LinearLayout diag = u.module(body, "02 // DIAGNÓSTICO", "Estado del sistema");
        u.telemetryPair(diag,
            "ACCESO ROOT", RootAccess.granted() ? "Autorizado" : RootAccess.canRequest() ? "Disponible" : "No detectado",
            "PUENTE DIRECT", AudioCapabilities.directPcm() ? "LSPosed activo" : "Sin confirmar");
        u.telemetryPair(diag,
            "QUAD DAC V60", AudioCapabilities.quadDac() ? "ROM verificada" : "Estándar",
            "VERSIÓN", BuildConfig.VERSION_NAME + " (" + BuildConfig.VERSION_CODE + ")");

        LinearLayout service = u.module(body, "03 // PERSISTENCIA ROOT", "Servicio y recuperación");
        TextView info = u.mono("Supervisor service.d (Magisk). Espera a Yang sin mantener el teléfono despierto; solo toma wakelock al reproducir.", 10.5f, YinUi.SUB, false);
        Switch persistent = u.toggleRow(service, "Arrancar con el teléfono y recuperarse", "",
            prefs.getBoolean("persistent", true), on -> {});
        persistent.setOnCheckedChangeListener((button, on) -> {
            persistent.setEnabled(false);
            info.setText("Actualizando supervisor…");
            a.persistent(on, error -> {
                persistent.setEnabled(true);
                if (error != null) {
                    info.setText("No se pudo cambiar: " + error);
                    view.post(this::render);
                } else {
                    info.setText(on
                        ? "Recuperación automática activada. Se instalará al encender el motor."
                        : "Arranque automático desactivado. Puedes usar el motor cuando lo enciendas.");
                }
            });
        });
        LinearLayout infoBox = u.well(service);
        infoBox.addView(info);
        service.addView(u.text("El interruptor maestro en la cabecera enciende o detiene el motor. No edita HAL, kernel ni mixer.", 10.5f, YinUi.MUTED));

        LinearLayout reset = u.module(body, "04 // MANTENIMIENTO", "Restablecer perfiles");
        reset.addView(u.text("Devuelve todos los perfiles de salida a plano, apaga efectos y centra el balance. Conserva tu color y los archivos en Yang.", 11.5f, YinUi.SUB));
        u.gap(reset, 10);
        Button restore = u.actionKey("Restablecer todos los perfiles", "reset", false, () -> {});
        boolean[] armed = {false};
        Runnable disarm = () -> {
            armed[0] = false;
            restore.setText("Restablecer todos los perfiles");
            restore.setTextColor(YinUi.INK);
            restore.setBackground(u.ripple(YinUi.KEY, YinUi.BORDER, 8));
        };
        restore.setOnClickListener(v -> {
            if (!armed[0]) {
                armed[0] = true;
                restore.setText("Confirmar restablecimiento");
                restore.setTextColor(YinUi.BG);
                restore.setBackground(u.ripple(YinUi.LED_RED, YinUi.LED_RED, 8));
                restore.postDelayed(disarm, 6000);
                return;
            }
            restore.removeCallbacks(disarm);
            a.getSharedPreferences("audio_settings", 0).edit().clear().apply();
            a.getSharedPreferences("yin_ab", 0).edit().clear().apply();
            YinService.settingsChanged();
            a.profilesReset();
            disarm.run();
            Toast.makeText(a, "Perfiles restablecidos", Toast.LENGTH_SHORT).show();
        });
        reset.addView(restore, new LinearLayout.LayoutParams(-1, -2));
    }

    private void screen() {
        if (a.getSharedPreferences("yin_control", 0).getBoolean("screen_on", false)) {
            a.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        } else {
            a.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }
}
