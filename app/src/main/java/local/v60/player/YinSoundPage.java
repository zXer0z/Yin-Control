package local.v60.player;

import android.os.Bundle;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;

/** DJ-mixer channel rack across 4 direct sections: Salida, EQ, Dinámica, Espacio. */
final class YinSoundPage {
    private static final String[] SHORT_ROUTES = {"Altavoz", "Jack", "BT", "USB"};
    private static final String[] ROUTE_ICONS = {"speaker", "headphones", "bluetooth", "usb"};
    private static final String[] PRESET_NAMES = {"Plano", "Bajos", "Voces", "Agudos", "Acústico"};
    private static final int[][] PRESET_GAINS = {
        {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
        {4, 4, 2, 0, -1, -1, 0, 0, 1, 1},
        {-2, -1, 0, 0, 2, 4, 3, 1, 0, 0},
        {-1, -1, 0, 0, 0, 1, 2, 3, 4, 4},
        {3, 2, 1, 0, 0, 1, 2, 3, 3, 2}
    };

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

    private final LinearLayout[] routeTiles = new LinearLayout[4];
    private final ImageView[] routeIcons = new ImageView[4];
    private final YinUi.LedDot[] routeLeds = new YinUi.LedDot[4];
    private final TextView[] routeTitles = new TextView[4];
    private final TextView[] routeTags = new TextView[4];
    private TextView selectedTitle, selectedFormat;
    private YinUi.LedChip selectedBadge;

    YinSoundPage(MainActivity a) {
        this.a = a;
        u = new YinUi(a);
        profile = AudioSettings.route(AudioSettings.predictedDevice(a));
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        body = u.column();
        body.setPadding(u.d(14), u.d(10), u.d(14), u.d(24));
        view.addView(body);
        render();
    }

    void selectSection(int nextSection) {
        int clamped = Math.max(0, Math.min(3, nextSection));
        if (section == clamped) return;
        section = clamped;
        view.scrollTo(0, 0);
        render();
    }

    private void save() { s.save(a); }

    private void options(LinearLayout p, String title, String[] values, int selected, int columns, IntConsumer action) {
        u.gap(p, 10);
        p.addView(u.mono(title, 10, YinUi.SUB, true));
        u.gap(p, 4);
        for (int start = 0; start < values.length; start += columns) {
            LinearLayout row = u.row();
            for (int col = 0; col < columns; col++) {
                int i = start + col;
                if (i >= values.length) {
                    row.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
                    continue;
                }
                final int choice = i;
                Button b = u.key(values[i], i == selected, () -> {
                    if (choice == selected) return;
                    int y = view.getScrollY();
                    action.accept(choice);
                    save();
                    render();
                    view.post(() -> view.scrollTo(0, y));
                });
                b.setTextSize(10.5f);
                b.setPadding(u.d(4), u.d(9), u.d(4), u.d(6));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(col == 0 ? 0 : 2.5f), u.d(2.5f), u.d(col == columns - 1 ? 0 : 2.5f), u.d(2.5f));
                row.addView(b, lp);
            }
            p.addView(row);
        }
    }

    private void render() {
        s = AudioSettings.load(a, profile);
        if (settingsBody == null) {
            LinearLayout routeRow = u.row();
            for (int i = 0; i < 4; i++) {
                final int idx = i;
                LinearLayout tile = u.column();
                tile.setPadding(u.d(9), u.d(7), u.d(9), u.d(7));
                tile.setBackground(u.shape(YinUi.PANEL, YinUi.BORDER, 8));

                LinearLayout top = u.row();
                ImageView iv = YinIcons.view(a, ROUTE_ICONS[i], YinUi.SUB, 15);
                routeIcons[i] = iv;
                top.addView(iv, new LinearLayout.LayoutParams(u.d(17), u.d(17)));
                top.addView(new View(a), new LinearLayout.LayoutParams(0, 1, 1));
                YinUi.LedDot led = new YinUi.LedDot(a, YinUi.LED_OFF, 7);
                routeLeds[i] = led;
                top.addView(led, new LinearLayout.LayoutParams(u.d(7), u.d(7)));
                tile.addView(top);

                u.gap(tile, 5);
                TextView name = u.title(SHORT_ROUTES[i], 12);
                name.setSingleLine(true);
                routeTitles[i] = name;
                tile.addView(name);

                TextView tag = u.mono("--", 8.5f, YinUi.MUTED, true);
                tag.setSingleLine(true);
                routeTags[i] = tag;
                tile.addView(tag);

                routeTiles[i] = tile;
                tile.setOnClickListener(v -> {
                    if (profile.equals(AudioSettings.PROFILES[idx])) return;
                    profile = AudioSettings.PROFILES[idx];
                    render();
                });
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(i == 0 ? 0 : 2.5f), 0, u.d(i == 3 ? 0 : 2.5f), 0);
                routeRow.addView(tile, lp);
            }
            body.addView(routeRow);
            u.gap(body, 6);

            LinearLayout oledBox = u.wellBox();
            oledBox.setPadding(u.d(10), u.d(8), u.d(10), u.d(8));
            LinearLayout statusRow = u.row();
            LinearLayout statusLeft = u.column();
            selectedTitle = u.mono("", 12.5f, YinUi.INK, true);
            selectedTitle.setSingleLine(true);
            selectedTitle.setEllipsize(TextUtils.TruncateAt.END);
            statusLeft.addView(selectedTitle);
            selectedFormat = u.mono("", 10, YinUi.ACCENT, false);
            selectedFormat.setMaxLines(2);
            selectedFormat.setEllipsize(TextUtils.TruncateAt.END);
            statusLeft.addView(selectedFormat);
            statusRow.addView(statusLeft, new LinearLayout.LayoutParams(0, -2, 1));
            statusRow.addView(new View(a), new LinearLayout.LayoutParams(u.d(8), 1));
            selectedBadge = u.ledChip("PERFIL", YinUi.LED_OFF, YinUi.MUTED);
            statusRow.addView(selectedBadge.view);
            oledBox.addView(statusRow);
            body.addView(oledBox);
            u.gap(body, 10);

            settingsBody = u.column();
            body.addView(settingsBody);
        }

        refreshRoutes();
        LinearLayout content = settingsBody;
        content.removeAllViews();
        dacPaint = null;

        if (section == 0) renderOutput(content);
        else if (section == 1) renderEq(content);
        else if (section == 2) renderDynamics(content);
        else renderSpace(content);

        u.gap(content, 4);
        content.addView(u.text("Ajustes por conexión · reinician la sesión PCM sin interrumpir tu biblioteca en Yang.", 10.5f, YinUi.MUTED));
    }

    private void renderOutput(LinearLayout content) {
        if (profile.equals("wired")) dacCard(content);
        if (profile.equals("bluetooth")) bluetoothCard(content);

        LinearLayout out = u.module(content, "01 // MOTOR Y FORMATO", AudioSettings.name(profile));
        u.toggleRow(
            out,
            "Perfil automático por dispositivo",
            s.storageKey.equals(s.profile)
                ? "Editando el perfil general de esta conexión"
                : "Editando ajustes propios del dispositivo conectado",
            DeviceProfiles.enabled(a),
            on -> {
                a.getSharedPreferences("yin_control", 0).edit().putBoolean("device_profiles", on).apply();
                YinService.settingsChanged();
                render();
            }
        );

        if (profile.equals("wired") || profile.equals("bluetooth")) {
            options(out, "MOTOR DE AUDIO", new String[]{"AudioTrack", "PCM Directo"}, s.direct ? 1 : 0, 2, i -> {
                s.direct = i == 1;
                if (s.direct && s.encoding == 0) s.encoding = 2;
                if (!s.direct && s.rate > 192000) s.rate = 0;
            });
        }

        if (profile.equals("speaker")) {
            u.detail(out, "Formato fijo", "48.0 kHz · 16 bits");
        } else {
            List<Integer> rates = new ArrayList<>();
            for (int n : AudioSettings.RATES) if (s.acceptsRate(n)) rates.add(n);
            String[] rateNames = rates.stream().map(YinSoundPage::shortRateLabel).toArray(String[]::new);
            options(out, "FRECUENCIA DE MUESTREO", rateNames, rates.indexOf(s.rate), 4, i -> {
                s.rate = rates.get(i);
                if (s.rate > 192000) s.encoding = 3;
            });

            List<Integer> enc = new ArrayList<>();
            for (int i = 0; i < 4; i++) {
                AudioSettings test = s.copy();
                test.encoding = i;
                test.validate();
                if (test.encoding == i) enc.add(i);
            }
            String[] formats = {"Float 32", "16 bits", "24 bits", "32 bits"};
            options(out, "PROFUNDIDAD DE BITS", enc.stream().map(i -> formats[i]).toArray(String[]::new),
                enc.indexOf(s.encoding), Math.min(4, enc.size()), i -> {
                    s.encoding = enc.get(i);
                    if (s.encoding == 0) s.direct = false;
                    if (s.encoding != 3 && s.rate > 192000) s.rate = 192000;
                });

            u.gap(out, 6);
            if (profile.equals("wired")) {
                out.addView(u.text("Float usa AudioTrack. 352.8/384 kHz requieren PCM Directo de 32 bits (*352.8k experimental).", 10.5f, YinUi.MUTED));
            } else if (profile.equals("bluetooth")) {
                out.addView(u.text("Máximo de Yin: 96 kHz / 24 bits. La transmisión final depende del códec negociado.", 10.5f, YinUi.MUTED));
            } else if (profile.equals("usb")) {
                out.addView(u.text("Límite actual del motor USB: 192 kHz.", 10.5f, YinUi.MUTED));
            }
        }

        options(out, "BÚFER DE SALIDA",
            Arrays.stream(AudioSettings.BUFFERS).mapToObj(n -> n + " ms").toArray(String[]::new),
            index(AudioSettings.BUFFERS, s.bufferMs), 4, i -> s.bufferMs = AudioSettings.BUFFERS[i]);
        options(out, "REMUESTREADOR", new String[]{"SWR", "SoXR"}, s.resampler, 2, i -> s.resampler = i);

        LinearLayout monitor = u.module(content, "02 // MONITOR MASTER", "Vúmetro LED de salida");
        monitor.addView(new SignalMeterView(a, s.storageKey, false, null), new LinearLayout.LayoutParams(-1, u.d(134)));
        u.gap(monitor, 6);
        monitor.addView(u.text("Medición digital en tiempo real para el perfil seleccionado (dBFS).", 10.5f, YinUi.MUTED));
    }

    private void renderEq(LinearLayout content) {
        LinearLayout eq = u.module(content, "01 // ECUALIZADOR", "");
        LinearLayout eqHead = u.row();
        LinearLayout eqTitles = u.column();
        eqTitles.addView(u.title("Consola gráfica de 10 bandas", 14.5f));
        eqTitles.addView(u.text("±12 dB · toca la curva o desliza los faders", 10.5f, YinUi.MUTED));
        eqHead.addView(eqTitles, new LinearLayout.LayoutParams(0, -2, 1));
        Button zeroBtn = u.actionKey("0 dB", "reset", false, () -> {
            Arrays.fill(s.gains, 0);
            Arrays.fill(s.dsp.gain, 0);
            save();
            render();
        });
        zeroBtn.setPadding(u.d(9), u.d(7), u.d(9), u.d(5));
        zeroBtn.setMinHeight(u.d(34));
        zeroBtn.setMinimumHeight(u.d(34));
        eqHead.addView(zeroBtn);
        eqHead.addView(new View(a), new LinearLayout.LayoutParams(u.d(6), 1));
        Switch power = new Switch(a);
        u.tint(power);
        power.setChecked(s.eq);
        power.setContentDescription("Encender ecualizador");
        power.setOnCheckedChangeListener((b, on) -> {
            s.eq = on;
            save();
            view.post(this::refreshSettings);
        });
        eqHead.addView(power);
        eq.addView(eqHead);
        u.gap(eq, 8);

        int selHz = s.dsp.frequency(eqBand);
        String selFreq = selHz >= 1000
            ? String.format(Locale.ROOT, "%.1fk Hz", selHz / 1000.0).replace(".0k", "k")
            : selHz + " Hz";
        double selGain = s.dsp.gain(eqBand, s.gains[eqBand]);
        String bandSummary = String.format(Locale.ROOT, "B%d · %s · %+.1f dB", eqBand + 1, selFreq, selGain);
        u.oledScreen(eq, s.eq ? "CURVA ACTIVA" : "CURVA EN ESPERA",
            s.eq ? "EQ ENCENDIDO · 10 BANDAS" : "EQ EN BYPASS", bandSummary, s.eq ? YinUi.ACCENT : YinUi.MUTED);

        eq.addView(new EqResponseView(a, s, eqBand, i -> view.post(() -> {
            eqBand = i;
            refreshSettings();
        })), new LinearLayout.LayoutParams(-1, u.d(176)));
        u.gap(eq, 8);

        LinearLayout presetRow = u.row();
        for (int p = 0; p < PRESET_NAMES.length; p++) {
            final int idx = p;
            Button pb = u.key(PRESET_NAMES[p], matchesPreset(p), () -> {
                for (int b = 0; b < 10; b++) {
                    s.gains[b] = PRESET_GAINS[idx][b];
                    s.dsp.gain[b] = PRESET_GAINS[idx][b];
                }
                s.eq = true;
                save();
                refreshSettings();
            });
            pb.setTextSize(10);
            pb.setPadding(u.d(2), u.d(8), u.d(2), u.d(5));
            pb.setMinHeight(u.d(34));
            pb.setMinimumHeight(u.d(34));
            LinearLayout.LayoutParams plp = new LinearLayout.LayoutParams(0, -2, 1);
            plp.setMargins(u.d(p == 0 ? 0 : 2), 0, u.d(p == PRESET_NAMES.length - 1 ? 0 : 2), 0);
            presetRow.addView(pb, plp);
        }
        eq.addView(presetRow);
        u.gap(eq, 10);

        LinearLayout bank = u.row();
        bank.setAlpha(s.eq ? 1f : .45f);
        bank.setClipChildren(false);
        eq.addView(bank, new LinearLayout.LayoutParams(-1, -2));

        for (int i = 0; i < 10; i++) {
            final int band = i;
            int hz = s.dsp.frequency(i);
            LinearLayout slot = u.column();
            slot.setGravity(Gravity.CENTER);
            slot.setClipChildren(false);
            slot.setPadding(u.d(1), u.d(4), u.d(1), u.d(4));
            if (i == eqBand) {
                slot.setBackground(u.shape(YinUi.alpha(YinUi.SELECTED, .65f), YinUi.alpha(YinUi.ACCENT, .35f), 6));
            }

            double value = s.dsp.gain(i, s.gains[i]);
            String gTxt = s.dsp.parametric
                ? String.format(Locale.ROOT, "%.1f", value)
                : (s.gains[i] > 0 ? "+" + s.gains[i] : String.valueOf(s.gains[i]));
            TextView gain = u.mono(gTxt, 8.5f, i == eqBand ? YinUi.INK : YinUi.ACCENT, i == eqBand);
            gain.setGravity(Gravity.CENTER);
            gain.setSingleLine(true);
            gain.setPadding(u.d(1), u.d(2), u.d(1), u.d(2));
            gain.setBackground(u.shape(YinUi.OLED, YinUi.OLED_BORDER, 3));
            slot.addView(gain, new LinearLayout.LayoutParams(-1, -2));

            GainFader fader = new GainFader(a, hz, (int) Math.round(value), v -> {
                s.gains[band] = v;
                if (s.dsp.parametric) s.dsp.gain[band] = v;
                gain.setText(v > 0 ? "+" + v : String.valueOf(v));
            }, () -> {
                eqBand = band;
                save();
                view.post(this::refreshSettings);
            });
            fader.setBandSelected(i == eqBand);
            fader.setEnabled(s.eq);
            slot.addView(fader, new LinearLayout.LayoutParams(-1, u.d(164)));

            String hzText = hz >= 1000
                ? String.format(Locale.ROOT, "%.1fk", hz / 1000.0).replace(".0k", "k")
                : String.valueOf(hz);
            TextView hzLabel = u.mono(hzText, 9, i == eqBand ? YinUi.ACCENT : YinUi.MUTED, i == eqBand);
            hzLabel.setGravity(Gravity.CENTER);
            hzLabel.setSingleLine(true);
            slot.addView(hzLabel);
            bank.addView(slot, new LinearLayout.LayoutParams(0, -2, 1));
        }

        u.gap(eq, 8);
        eq.addView(u.text("Canal L sólido · Canal R discontinuo. Curva analítica RBJ en tiempo real.", 10.5f, YinUi.MUTED));

        new YinDspControls(a, s, this::save, this::refreshSettings).eq(content, eqBand, i -> {
            eqBand = i;
            refreshSettings();
        });
    }

    private void renderDynamics(LinearLayout content) {
        YinStudioControls studio = new YinStudioControls(a, s, this::save, this::refreshSettings);
        studio.soundColorPanel(content);

        LinearLayout master = u.module(content, "02 // PREAMP Y LIMITADOR", "Ganancia maestra");
        if (s.dsp.preampFraction != 0) {
            u.detail(master, "Preamp importado", String.format(Locale.ROOT, "%+.2f dB", s.preamp + s.dsp.preampFraction));
        }
        u.sliderRow(master, "Preamplificador", s.preamp, -24, 12, v -> {
            s.preamp = v;
            s.dsp.preampFraction = 0;
        }, v -> String.format(Locale.ROOT, "%+d dB", v), this::save);

        u.toggleRow(master, "Margen automático (Headroom)", "Atenúa antes del EQ y graves para evitar clipping", s.headroom, v -> {
            s.headroom = v;
            save();
        });
        u.toggleRow(master, "Limitador de picos", "Protección suave frente a picos digitales", s.limiter, v -> {
            s.limiter = v;
            save();
            refreshSettings();
        });
        if (s.limiter) {
            u.sliderRow(master, "Techo del limitador", s.dsp.ceiling, -12, -1, v -> s.dsp.ceiling = v,
                v -> v + " dBFS", this::save);
        }

        new YinDspControls(a, s, this::save, this::refreshSettings).dynamics(content);
        studio.warmthPanel(content);
    }

    private void renderSpace(LinearLayout content) {
        LinearLayout crossfader = u.module(content, "01 // CROSSFADER · BALANCE L / R", "Panorama estéreo Magvel");
        u.sliderRow(crossfader, "Balance L / R", s.balance, -100, 100, v -> s.balance = v,
            v -> v == 0 ? "CENTER" : v < 0 ? "L " + (-v) : "R " + v, () -> {
                save();
                refreshSettings();
            });
        if (s.balance != 0) {
            LinearLayout centerRow = u.row();
            centerRow.setGravity(Gravity.END);
            Button centerBtn = u.key("CENTER LOCK · Centrar", false, YinUi.CUE_ORANGE, () -> {
                s.balance = 0;
                save();
                refreshSettings();
            });
            centerBtn.setMinHeight(u.d(34));
            centerBtn.setMinimumHeight(u.d(34));
            centerBtn.setPadding(u.d(12), u.d(7), u.d(12), u.d(5));
            centerRow.addView(centerBtn);
            crossfader.addView(centerRow);
        }

        new YinDspControls(a, s, this::save, this::refreshSettings).space(content);
        new YinStudioControls(a, s, this::save, this::refreshSettings).channelsPanel(content);
    }

    private boolean matchesPreset(int idx) {
        if (!s.eq || s.dsp.parametric) return false;
        for (int i = 0; i < 10; i++) {
            if (s.gains[i] != PRESET_GAINS[idx][i]) return false;
        }
        return true;
    }

    void refreshSettings() {
        int y = view.getScrollY();
        render();
        view.post(() -> view.scrollTo(0, y));
    }

    private void dacCard(LinearLayout parent) {
        LinearLayout card = u.module(parent, "00 // HARDWARE QUAD DAC", "Amplificador ESS");
        YinUi.OledBox oled = u.oledBox(card, "ESTADO DEL AMPLIFICADOR", DacGain.description(), "LG V60", YinUi.INK);

        LinearLayout row = u.row();
        Button[] modes = new Button[3];
        boolean[] busy = {false};
        Button read = u.actionKey("Consultar modo del DAC", "refresh", false, () -> {});

        Runnable paint = () -> {
            int confirmed = DacGain.confirmedMode();
            oled.sub.setText(busy[0] ? "LEYENDO" : confirmed > 0 ? DacGain.name(confirmed).toUpperCase(Locale.ROOT) : "SIN LEER");
            oled.sub.setTextColor(busy[0] ? YinUi.LED_AMBER : confirmed > 0 ? YinUi.LED_GREEN : YinUi.MUTED);
            if (!busy[0]) oled.value.setText(DacGain.description());
            for (int i = 0; i < 3; i++) {
                boolean active = confirmed == i + 1;
                u.styleKey(modes[i], active, YinUi.ACCENT);
                modes[i].setEnabled(!busy[0]);
            }
            read.setEnabled(!busy[0]);
        };

        java.util.function.Consumer<Integer> request = mode -> {
            if (busy[0]) return;
            busy[0] = true;
            oled.value.setText(mode == null ? "Consultando DAC…" : "Aplicando " + DacGain.name(mode) + "…");
            paint.run();
            DacGain.request(a, mode, message -> {
                busy[0] = false;
                oled.value.setText(message);
                paint.run();
            });
        };

        String[] labels = {"Normal", "Alta imp.", "AUX"};
        for (int i = 0; i < 3; i++) {
            final int mode = i + 1;
            Button b = u.key(labels[i], false, () -> request.accept(mode));
            b.setContentDescription(DacGain.name(mode));
            modes[i] = b;
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, u.d(42), 1);
            p.setMargins(u.d(i == 0 ? 0 : 2.5f), 0, u.d(i == 2 ? 0 : 2.5f), 0);
            row.addView(b, p);
        }
        card.addView(row);
        u.gap(card, 6);
        read.setOnClickListener(v -> request.accept(null));
        card.addView(read, new LinearLayout.LayoutParams(-1, -2));
        u.gap(card, 6);
        card.addView(u.text("Normal: uso habitual. Alta impedancia: mayor ganancia. AUX: salida de línea.", 10.5f, YinUi.MUTED));
        dacPaint = paint;
        paint.run();
    }

    private void bluetoothCard(LinearLayout parent) {
        LinearLayout card = u.module(parent, "00 // BLUETOOTH A2DP", "Códec inalámbrico");
        YinUi.OledBox oled = u.oledBox(card, "CÓDEC NEGOCIADO", "Bluetooth · sin consultar", "A2DP", YinUi.INK);

        TextView feedback = u.text("", 11, YinUi.SUB);
        feedback.setMaxLines(3);
        feedback.setVisibility(View.GONE);

        LinearLayout choices = u.column();
        Runnable refresh = () -> {
            BluetoothCodecs.Snapshot value = BluetoothCodecs.latest();
            choices.removeAllViews();
            if (value == null) {
                oled.value.setText("Bluetooth · sin confirmar");
                oled.sub.setText("A2DP");
                return;
            }
            org.json.JSONObject current = value.data.optJSONObject("current");
            oled.value.setText(current == null ? "Bluetooth · no informado" : current.optString("name", "Android"));
            oled.sub.setText(current == null
                ? value.device()
                : BluetoothCodecs.rate(current.optInt("rate")) + " · " + BluetoothCodecs.bits(current.optInt("bits")));
            org.json.JSONArray available = value.data.optJSONArray("available");
            if (available == null) return;
            LinearLayout row = null;
            for (int i = 0; i < available.length(); i++) {
                org.json.JSONObject entry = available.optJSONObject(i);
                if (entry == null) continue;
                if (i % 3 == 0) {
                    row = u.row();
                    choices.addView(row);
                }
                final int type = entry.optInt("type");
                Button choice = u.key(entry.optString("name", "Códec"), value.type() == type, () -> {
                    feedback.setVisibility(View.VISIBLE);
                    feedback.setText("Solicitando códec…");
                    BluetoothCodecs.request(a, type, value.data.optString("address"), message -> {
                        feedback.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
                        feedback.setText(message);
                        BluetoothCodecs.Snapshot now = BluetoothCodecs.latest();
                        if (now != null) {
                            oled.value.setText(now.description());
                            oled.sub.setText("ANDROID");
                        }
                    });
                });
                choice.setTextSize(10.5f);
                choice.setPadding(u.d(4), u.d(9), u.d(4), u.d(6));
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
                lp.setMargins(u.d(2.5f), u.d(2.5f), u.d(2.5f), u.d(2.5f));
                if (row != null) row.addView(choice, lp);
            }
        };

        LinearLayout actions = u.row();
        Button update = u.actionKey("Leer códec", "refresh", false, () -> {
            feedback.setVisibility(View.VISIBLE);
            feedback.setText("Leyendo conexión…");
            BluetoothCodecs.request(a, null, null, message -> {
                feedback.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
                feedback.setText(message);
                refresh.run();
            });
        });
        Button maximum = u.key("Máxima (LDAC)", true, () -> {
            BluetoothCodecs.Snapshot value = BluetoothCodecs.latest();
            if (value == null) {
                feedback.setVisibility(View.VISIBLE);
                feedback.setText("Lee primero la conexión.");
                return;
            }
            feedback.setVisibility(View.VISIBLE);
            feedback.setText("Solicitando LDAC…");
            BluetoothCodecs.maximum(a, value.data.optString("address"), message -> {
                feedback.setVisibility(message == null || message.isEmpty() ? View.GONE : View.VISIBLE);
                feedback.setText(message);
                refresh.run();
            });
        });
        LinearLayout.LayoutParams lpL = new LinearLayout.LayoutParams(0, -2, 1);
        lpL.setMargins(0, 0, u.d(3), 0);
        LinearLayout.LayoutParams lpR = new LinearLayout.LayoutParams(0, -2, 1);
        lpR.setMargins(u.d(3), 0, 0, 0);
        actions.addView(update, lpL);
        actions.addView(maximum, lpR);
        card.addView(actions);
        u.gap(card, 6);
        card.addView(choices);
        card.addView(feedback);
        refresh.run();
    }

    void refreshRoutes() {
        if (dacPaint != null) dacPaint.run();
        Bundle state = YinService.publicStatus();
        android.media.AudioDeviceInfo predicted = AudioSettings.predictedDevice(a);
        boolean active = state.getBoolean("ok") && state.getBoolean("playing") && !state.getBoolean("drained");
        String activeProfile = state.getString("route", "");

        for (int i = 0; i < 4; i++) {
            if (routeTiles[i] == null) continue;
            String key = AudioSettings.PROFILES[i];
            boolean sel = profile.equals(key);
            boolean inUse = active && key.equals(activeProfile);
            boolean connected = predicted != null && key.equals(AudioSettings.route(predicted));

            int ledColor = inUse ? YinUi.LED_GREEN : connected ? YinUi.ACCENT : YinUi.LED_OFF;
            routeLeds[i].setColor(ledColor);
            routeTiles[i].setBackground(u.shape(sel ? YinUi.SELECTED : YinUi.PANEL, sel ? YinUi.ACCENT : YinUi.BORDER, 8));
            YinIcons.tint(routeIcons[i], ROUTE_ICONS[i], sel ? YinUi.ACCENT : inUse ? YinUi.LED_GREEN : YinUi.SUB, 15);
            routeTitles[i].setTextColor(sel ? YinUi.INK : YinUi.SUB);

            AudioSettings pref = AudioSettings.load(a, key);
            String tagText = inUse ? "EN USO" : connected ? "ACTIVO" : shortRateLabel(pref.rate);
            routeTags[i].setText(tagText);
            routeTags[i].setTextColor(inUse ? YinUi.LED_GREEN : connected ? YinUi.ACCENT : YinUi.MUTED);

            if (sel && selectedTitle != null) {
                selectedTitle.setText(inUse
                    ? state.getString("device", AudioSettings.name(key))
                    : connected ? predicted.getProductName().toString() : "CH" + (i + 1) + " · " + AudioSettings.name(key));
                if (inUse) {
                    selectedFormat.setText(AudioSignalInfo.frequency(state.getInt("rate")) + " · "
                        + AudioSettings.encodingName(state.getInt("encoding")) + " · "
                        + (state.getBoolean("direct") ? "DIRECT" : "AudioTrack"));
                    selectedFormat.setTextColor(YinUi.ACCENT);
                    selectedBadge.update("EN VIVO", YinUi.LED_GREEN, YinUi.INK, true);
                } else {
                    String prefRate = pref.rate == 0 ? "Frec. auto" : AudioSignalInfo.frequency(pref.rate);
                    String prefEnc = new String[]{"Float 32", "16 bits", "24 bits", "32 bits"}[pref.encoding];
                    selectedFormat.setText(prefRate + " · " + prefEnc + " · " + (pref.direct ? "PCM Directo" : "AudioTrack"));
                    selectedFormat.setTextColor(YinUi.SUB);
                    if (connected) selectedBadge.update("CONECTADO", YinUi.ACCENT, YinUi.INK, false);
                    else selectedBadge.update("PERFIL", YinUi.LED_OFF, YinUi.MUTED, false);
                }
            }
        }
    }

    private static String shortRateLabel(int rate) {
        if (rate == 0) return "Auto";
        double khz = rate / 1000.0;
        String base = Math.abs(khz - Math.round(khz)) < 0.05
            ? Math.round(khz) + "k"
            : String.format(Locale.ROOT, "%.1fk", khz);
        return rate == 352800 ? base + "*" : base;
    }

    private static int index(int[] values, int value) {
        for (int i = 0; i < values.length; i++) if (values[i] == value) return i;
        return 0;
    }
}
