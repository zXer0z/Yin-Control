package local.v60.player;

import android.widget.*;
import java.util.Locale;
import java.util.function.*;

/** Mixer racks for parametric EQ, convolver, calibrated dynamics, and ViPER-grade spatial DSP. */
final class YinDspControls {
    private static final int[] BINAURAL_HZ = {700, 700, 650};
    private static final int[] BINAURAL_MIX = {45, 60, 95};
    private static final String[] BINAURAL_LABELS = {"Leve", "Medio", "Fuerte"};

    private static final int[] SURROUND_DELAY = {2, 8, 14, 20};
    private static final int[] SURROUND_MIX = {55, 75, 85, 95};
    private static final int[] SURROUND_LP = {10500, 7500, 5500, 4000};
    private static final String[] SURROUND_LABELS = {"Sutil 2ms", "Estudio 8ms", "Sala 14ms", "Amplio 20ms"};

    private final MainActivity a;
    private final YinUi u;
    private final AudioSettings s;
    private final DspSettings d;
    private final Runnable save, redraw;

    YinDspControls(MainActivity a, AudioSettings s, Runnable save, Runnable redraw) {
        this.a = a;
        u = new YinUi(a);
        this.s = s;
        d = s.dsp;
        this.save = save;
        this.redraw = redraw;
    }

    private void toggle(LinearLayout p, String name, String note, boolean value, Consumer<Boolean> set) {
        u.toggleRow(p, name, note, value, on -> {
            set.accept(on);
            save.run();
            redraw.run();
        });
    }

    private void slider(LinearLayout p, String name, int initial, int min, int max, IntConsumer set, IntFunction<String> display) {
        u.sliderRow(p, name, initial, min, max, set, display, () -> {
            save.run();
            redraw.run();
        });
    }

    private void segmented(LinearLayout parent, String[] labels, int selected, int activeColor, IntConsumer onSelect) {
        LinearLayout row = u.row();
        for (int i = 0; i < labels.length; i++) {
            final int idx = i;
            Button b = u.key(labels[i], i == selected, activeColor, () -> {
                onSelect.accept(idx);
                save.run();
                redraw.run();
            });
            b.setTextSize(10.5f);
            b.setPadding(u.d(3), u.d(9), u.d(3), u.d(6));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            lp.setMargins(u.d(i == 0 ? 0 : 2.5f), u.d(3), u.d(i == labels.length - 1 ? 0 : 2.5f), u.d(3));
            row.addView(b, lp);
        }
        parent.addView(row);
    }

    void eq(LinearLayout parent, int selected, IntConsumer select) {
        LinearLayout p = u.module(parent, "02 // PARAMÉTRICO E IMPORTACIÓN", "Corrección de precisión");
        toggle(p, "Editar frecuencia y anchura (Q)", "Desbloquea filtros PK / LS / HS y ajuste por canal", d.parametric, v -> {
            if (v && !d.parametricEdited) {
                for (int i = 0; i < 10; i++) d.gain[i] = s.gains[i];
                d.parametricEdited = true;
            }
            d.parametric = v;
        });

        if (d.parametric) {
            u.gap(p, 4);
            for (int first = 0; first < 10; first += 5) {
                LinearLayout row = u.row();
                for (int col = 0; col < 5; col++) {
                    final int band = first + col;
                    Button b = u.key("B" + (band + 1), band == selected, () -> select.accept(band));
                    b.setTextSize(10.5f);
                    b.setPadding(0, u.d(7), 0, u.d(4));
                    b.setMinHeight(u.d(35));
                    b.setMinimumHeight(u.d(35));
                    b.setContentDescription("Editar banda " + (band + 1));
                    LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                    lp.setMargins(u.d(col == 0 ? 0 : 2), u.d(2), u.d(col == 4 ? 0 : 2), u.d(2));
                    row.addView(b, lp);
                }
                p.addView(row);
            }
            u.gap(p, 6);

            int f = (int) Math.round(Math.log(d.hz[selected] / 20.0) / Math.log(1000) * 1000);
            slider(p, "Frecuencia · B" + (selected + 1), f, 0, 1000,
                v -> d.hz[selected] = (int) Math.round(20 * Math.pow(1000, v / 1000.0)),
                v -> Math.round(20 * Math.pow(1000, v / 1000.0)) + " Hz");
            slider(p, "Ganancia", (int) Math.round(d.gain[selected] * 10), -120, 120,
                v -> { d.gain[selected] = v / 10.0; s.gains[selected] = (int) Math.round(v / 10.0); },
                v -> String.format(Locale.ROOT, "%+.1f dB", v / 10.0));
            slider(p, "Anchura Q", (int) Math.round(d.q[selected] * 100), 10, 1200,
                v -> d.q[selected] = v / 100.0,
                v -> String.format(Locale.ROOT, "%.2f", v / 100.0));

            u.gap(p, 4);
            segmented(p, new String[]{"Izquierdo", "Derecho", "Ambos"}, d.channel[selected], YinUi.ACCENT, c -> d.channel[selected] = c);
            segmented(p, new String[]{"Campana", "Graves", "Agudos"}, d.type[selected], YinUi.ACCENT, t -> d.type[selected] = t);
        }

        u.gap(p, 8);
        p.addView(u.actionKey("Importar corrección AutoEQ / APO", "import", false, () -> a.importEffect(s.profile, s.storageKey, false)),
            new LinearLayout.LayoutParams(-1, -2));
        u.gap(p, 6);
        p.addView(u.text("Texto local: hasta 10 filtros PK/LS/HS. Conserva decimales de ganancia y factor Q.", 10.5f, YinUi.MUTED));

        LinearLayout ir = u.module(parent, "03 // CONVOLVER", "Impulsos IR");
        u.oledScreen(ir, "ARCHIVO IR ACTIVO", d.irName.isEmpty() ? "Sin archivo cargado" : d.irName,
            d.convolver ? "ON" : "OFF", d.irName.isEmpty() ? YinUi.MUTED : YinUi.ACCENT);
        if (!d.irFile.isEmpty()) {
            toggle(ir, "Aplicar impulso", "", d.convolver, v -> d.convolver = v);
        }
        ir.addView(u.actionKey(d.irFile.isEmpty() ? "Cargar archivo WAV / IRS" : "Cambiar impulso", "import", false,
            () -> a.importEffect(s.profile, s.storageKey, true)), new LinearLayout.LayoutParams(-1, -2));
        if (d.convolver) {
            u.gap(ir, 6);
            slider(ir, "Mezcla del impulso", d.irMix, 0, 100, v -> d.irMix = v, v -> v + " %");
            slider(ir, "Ganancia del impulso", d.irGain, -24, 0, v -> d.irGain = v, v -> v + " dB");
        }
        u.gap(ir, 6);
        ir.addView(u.text("WAV mono/estéreo hasta 2 s y 16 MB; se adapta a la frecuencia de salida.", 10.5f, YinUi.MUTED));
    }

    void dynamics(LinearLayout parent) {
        LinearLayout bass = u.module(parent, "03 // GRAVES Y SUB-WOOFER", "Potencia de bajos");
        toggle(bass, "Activar refuerzo de graves", "Modo Natural, Sub-Woofer o Dinámico adaptativo", d.bass, v -> d.bass = v);
        if (d.bass) {
            u.gap(bass, 2);
            segmented(bass, new String[]{"Natural", "Sub-Woofer", "Dinámico"}, d.bassMode, YinUi.ACCENT, m -> d.bassMode = m);
            slider(bass, "Refuerzo de graves", d.bassGain, 1, 12, v -> d.bassGain = v, v -> "+" + v + " dB");
            slider(bass, "Frecuencia de graves", d.bassHz, 40, 160, v -> d.bassHz = v, v -> v + " Hz");
        }

        LinearLayout clarity = u.module(parent, "04 // CLARIDAD Y DETALLE", "Realce armónico y voces");
        toggle(clarity, "Realzar claridad y micro-detalle", "Aumenta definición y aire sin estridencias", d.clarity, v -> d.clarity = v);
        if (d.clarity) {
            u.gap(clarity, 2);
            segmented(clarity, new String[]{"Armónico", "Presencia", "Híbrido"}, d.clarityMode, YinUi.ACCENT, m -> d.clarityMode = m);
            slider(clarity, "Intensidad de claridad", d.clarityAmount, 1, 10, v -> d.clarityAmount = v, v -> v + " / 10");
        }

        LinearLayout comp = u.module(parent, "04B // COMPRESOR DINÁMICO", "Nivelación de volumen");
        toggle(comp, "Nivelar diferencias de volumen", "Controla pasajes suaves y picos fuertes", d.compressor, v -> d.compressor = v);
        if (d.compressor) {
            u.gap(comp, 2);
            int compPreset = d.ratio == 2 && d.threshold == -16 ? 0
                : d.ratio == 3 && d.threshold == -18 ? 1
                : d.ratio == 6 && d.threshold == -26 ? 2 : -1;
            segmented(comp, new String[]{"Suave (2:1)", "Estudio (3:1)", "Noche (6:1)"}, compPreset, YinUi.FX_BLUE, idx -> {
                if (idx == 0) { d.threshold = -16; d.ratio = 2; d.attack = 20; d.release = 220; }
                else if (idx == 1) { d.threshold = -18; d.ratio = 3; d.attack = 10; d.release = 180; }
                else { d.threshold = -26; d.ratio = 6; d.attack = 5; d.release = 260; }
            });
            slider(comp, "Umbral", d.threshold, -40, -6, v -> d.threshold = v, v -> v + " dBFS");
            slider(comp, "Relación", d.ratio, 1, 10, v -> d.ratio = v, v -> v + ":1");
            slider(comp, "Ataque", d.attack, 1, 100, v -> d.attack = v, v -> v + " ms");
            slider(comp, "Recuperación", d.release, 40, 1000, v -> d.release = v, v -> v + " ms");
        }
    }

    void space(LinearLayout parent) {
        if (s.profile.equals("speaker")) {
            LinearLayout p = u.module(parent, "02 // ESPACIO", "Efectos para auriculares");
            p.addView(u.text("La protección auditiva binaural, Differential Surround y escena estéreo se aplican en Jack / AUX, Bluetooth y USB.", 12, YinUi.SUB));
            return;
        }

        LinearLayout cross = u.module(parent, "02 // PROTECCIÓN AUDITIVA BINAURAL", "Crossfeed Cure Tech+");
        cross.addView(u.text("Cada oído escucha suavemente ambos canales como con altavoces frontales, eliminando el aislamiento artificial de los auriculares y reduciendo la fatiga auditiva.", 11, YinUi.SUB));
        u.gap(cross, 6);
        toggle(cross, "Activar protección auditiva", "Imagen frontal natural y descanso prolongado", d.crossfeed, v -> d.crossfeed = v);
        if (d.crossfeed) {
            u.gap(cross, 2);
            int activeLevel = -1;
            for (int i = 0; i < BINAURAL_LABELS.length; i++) {
                if (d.crossfeedHz == BINAURAL_HZ[i] && d.crossfeedAmount == BINAURAL_MIX[i]) activeLevel = i;
            }
            segmented(cross, BINAURAL_LABELS, activeLevel, YinUi.LED_GREEN, idx -> {
                d.crossfeedHz = BINAURAL_HZ[idx];
                d.crossfeedAmount = BINAURAL_MIX[idx];
            });
            slider(cross, "Frecuencia de corte", d.crossfeedHz, 300, 1200, v -> d.crossfeedHz = v, v -> v + " Hz");
            slider(cross, "Nivel binaural (Feedback)", d.crossfeedAmount, 20, 95, v -> d.crossfeedAmount = v, v -> v + " %");
            u.gap(cross, 2);
            cross.addView(u.text("Leve: 700 Hz · 45% · Medio: 700 Hz · 60% · Fuerte: 650 Hz · 95%.", 10.5f, YinUi.MUTED));
        }

        LinearLayout surround = u.module(parent, "03 // DIFFERENTIAL SURROUND", "Efecto Haas envolvente");
        surround.addView(u.text("Introduce un desfase temporal entre los canales izquierdo y derecho con filtro pasa-bajos en el canal retardado, sacando el sonido fuera de la cabeza.", 11, YinUi.SUB));
        u.gap(surround, 6);
        toggle(surround, "Activar Differential Surround", "Solo actúa en señales estéreo", d.surround, v -> d.surround = v);
        if (d.surround) {
            u.gap(surround, 2);
            int activeRoom = -1;
            for (int i = 0; i < SURROUND_LABELS.length; i++) {
                if (d.delay == SURROUND_DELAY[i] && d.surroundAmount == SURROUND_MIX[i] && d.surroundLpHz == SURROUND_LP[i]) {
                    activeRoom = i;
                }
            }
            segmented(surround, SURROUND_LABELS, activeRoom, YinUi.FX_BLUE, idx -> {
                d.delay = SURROUND_DELAY[idx];
                d.surroundAmount = SURROUND_MIX[idx];
                d.surroundLpHz = SURROUND_LP[idx];
            });
            segmented(surround, new String[]{"Derecho retardado", "Izquierdo (Reverse)"}, d.surroundReverse ? 1 : 0, YinUi.CUE_ORANGE, i -> d.surroundReverse = i == 1);
            slider(surround, "Retardo interaural (Delay)", d.delay, 1, 25, v -> d.delay = v, v -> v + " ms");
            slider(surround, "Mezcla envolvente (Wet/Dry)", d.surroundAmount, 10, 100, v -> d.surroundAmount = v, v -> v + " %");
            slider(surround, "Corte pasa-bajos (LP Cutoff)", d.surroundLpHz, 1500, 16000, v -> d.surroundLpHz = v,
                v -> v >= 1000 ? String.format(Locale.ROOT, "%.1f kHz", v / 1000.0).replace(".0 ", " ") : v + " Hz");
        }

        LinearLayout wide = u.module(parent, "04 // ESCENA ESTÉREO", "Expansión Mid / Side");
        toggle(wide, "Ampliar anchura estéreo", "Expande el canal lateral (L-R) conservando el centro", d.stereoWide, v -> d.stereoWide = v);
        if (d.stereoWide) {
            slider(wide, "Apertura de escena", d.stereoWidth, 110, 200, v -> d.stereoWidth = v, v -> v + " %");
        }
    }
}
