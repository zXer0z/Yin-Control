package local.v60.player;

import android.app.*;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.*;
import android.graphics.drawable.*;
import android.media.*;
import android.os.*;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;
import java.util.function.IntConsumer;
import static local.v60.player.SoundStyle.*;

/**
 * Redesigned Audiophile & Apple Music style Sound Settings Screen.
 * Enforces physical output limits per route (Speaker max 16-bit/48kHz, Bluetooth max 24-bit/96kHz,
 * Jack/AUX & USB unlocked with full Quad DAC + DoP128 AUX support) and explains why limits apply.
 */
final class AudioSettingsDialog {
    private static final int GOLD = 0xffd49b27;
    private static final int GREEN = 0xff30d158;
    private static final int CYAN = 0xff0a84ff;
    private static final int AMBER = 0xfff5b041;

    // Selectable PCM rates (excludes dead 352.8kHz PCM entry; DoP128 uses 352.8kHz natively via DopBackend)
    private static final int[] UI_RATES = {0, 44100, 48000, 88200, 96000, 176400, 192000, 384000};
    private static final String[] UI_RATE_NAMES = {
        "Original del archivo",
        "44.1 kHz",
        "48 kHz",
        "88.2 kHz",
        "96 kHz",
        "176.4 kHz",
        "192 kHz",
        "384 kHz"
    };

    private final Activity activity;
    private final java.util.function.Consumer<String> saved;
    private final Dialog dialog;
    private final Map<String, AudioSettings> drafts = new LinkedHashMap<>();
    private final List<Dialog> sheets = new ArrayList<>();
    private LinearLayout body, connectionHost, connectionSteps;
    private ConnectionSlider connectionSlider;
    private ScrollView scroll;
    private TextView footHint;
    private String profile;
    private int tab;
    private boolean codecBusy, gainBusy;
    private Integer gainPending;
    private final Runnable gainObserver;
    private String codecMessage = "Consulta la conexión para ver el códec negociado.";
    private final TextView[] tabs = new TextView[3];
    private AudioDeviceCallback routes;
    private SoundEqGraph eqGraph;

    AudioSettingsDialog(Activity a, java.util.function.Consumer<String> saved) {
        activity = a;
        this.saved = saved;
        dialog = new AppDialog(a, false, true);
        gainObserver = () -> {
            if (dialog.isShowing() && !activity.isFinishing()) render();
        };
        profile = AudioSettings.route(AudioSettings.predictedDevice(a));
        for (String key : AudioSettings.PROFILES) {
            drafts.put(key, AudioSettings.load(a, key));
        }
    }

    Dialog show() {
        LinearLayout root = column();
        root.setBackgroundColor(BG);
        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(bars.left, bars.top, bars.right, bars.bottom);
            return insets;
        });

        // Top App Bar
        LinearLayout toolbar = row();
        toolbar.setGravity(Gravity.CENTER_VERTICAL);
        toolbar.setPadding(d(14), d(8), d(18), d(6));
        Button close = icon("back", "Cerrar", INK, 0, 21);
        toolbar.addView(close, new LinearLayout.LayoutParams(d(44), d(44)));
        close.setOnClickListener(v -> dialog.dismiss());

        LinearLayout heading = column();
        heading.setPadding(d(8), 0, 0, 0);
        TextView kicker = label("YIN CONTROL · MOTOR ROOT", 9.5f, ACCENT, 700);
        kicker.setLetterSpacing(.08f);
        heading.addView(kicker);
        TextView title = label("Sonido y Salidas", 23, INK, 750);
        title.setLetterSpacing(-.03f);
        heading.addView(title);
        toolbar.addView(heading, new LinearLayout.LayoutParams(0, -2, 1));

        Button reset = icon("reset", "Restablecer perfil", MUTED, SURFACE, 18);
        reset.setTooltipText("Restablecer este perfil");
        toolbar.addView(reset, new LinearLayout.LayoutParams(d(40), d(40)));
        reset.setOnClickListener(v -> confirmReset());
        root.addView(toolbar);
        root.addView(((MainActivity) activity).controllerBar());

        // Route Selector Host
        connectionHost = column();
        LinearLayout.LayoutParams connectionLp = new LinearLayout.LayoutParams(-1, -2);
        connectionLp.setMargins(d(18), d(6), d(18), d(2));
        root.addView(connectionHost, connectionLp);

        // Section Tabs: Salida y DAC | Ecualizador | Volumen y DSP
        LinearLayout nav = row();
        nav.setPadding(d(3), d(3), d(3), d(3));
        int navBgColor = isDark() ? 0xff1c1c20 : 0xffe5e5ea;
        GradientDrawable navBg = SoundStyle.surface(activity, navBgColor, 12);
        if (isDark()) navBg.setStroke(1, 0x18ffffff);
        nav.setBackground(navBg);
        String[] names = {"Salida y DAC", "Ecualizador", "Volumen y DSP"};
        for (int i = 0; i < 3; i++) {
            final int index = i;
            TextView t = new TextView(activity);
            t.setText(names[i]);
            t.setTextSize(12.5f);
            t.setGravity(Gravity.CENTER);
            t.setContentDescription("Sección " + names[i]);
            t.setPadding(d(4), d(8), d(4), d(8));
            t.setIncludeFontPadding(false);
            t.setSingleLine(true);
            t.setEllipsize(TextUtils.TruncateAt.END);
            tabs[i] = t;
            tabs[i].setOnClickListener(v -> {
                tab = index;
                scroll.scrollTo(0, 0);
                render();
            });
            nav.addView(tabs[i], new LinearLayout.LayoutParams(0, -2, 1));
        }
        LinearLayout.LayoutParams navLp = new LinearLayout.LayoutParams(-1, -2);
        navLp.setMargins(d(18), d(8), d(18), d(4));
        root.addView(nav, navLp);

        scroll = new ScrollView(activity);
        scroll.setFillViewport(false);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setClipToPadding(false);
        body = column();
        body.setPadding(d(18), d(8), d(18), d(28));
        scroll.addView(body);
        root.addView(scroll, new LinearLayout.LayoutParams(-1, 0, 1));

        LinearLayout footer = column();
        footer.setPadding(d(18), d(6), d(18), d(8));
        footer.setBackgroundColor(BG);
        footHint = label("", 10.5f, MUTED, 500);
        footHint.setGravity(Gravity.CENTER);
        add(footer, footHint, 0);
        root.addView(footer);

        dialog.setContentView(root);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.setLayout(-1, -1);
            window.getDecorView().requestApplyInsets();
        }
        AudioManager manager = activity.getSystemService(AudioManager.class);
        routes = new AudioDeviceCallback() {
            public void onAudioDevicesAdded(AudioDeviceInfo[] devices) { refreshRoute(); }
            public void onAudioDevicesRemoved(AudioDeviceInfo[] devices) { refreshRoute(); }
        };
        manager.registerAudioDeviceCallback(routes, new Handler(Looper.getMainLooper()));
        DacGain.observe(gainObserver);
        dialog.setOnDismissListener(d -> {
            DacGain.unobserve(gainObserver);
            manager.unregisterAudioDeviceCallback(routes);
            for (Dialog sheet : new ArrayList<>(sheets)) sheet.dismiss();
            if (activity instanceof MainActivity && !activity.isFinishing() && !activity.isDestroyed()) {
                activity.finish();
            }
        });
        render();
        return dialog;
    }

    private void changed() {
        AudioSettings s = drafts.get(profile);
        s.validate();
        AudioSettings effective = s.forPlayback();
        String problem = effective.outputProblem(effective.rate);
        if (!problem.isEmpty()) {
            footHint.setText("Sin aplicar · revisa la combinación de salida");
            return;
        }
        String before = activity.getSharedPreferences("audio_settings", 0).getString(profile, null);
        s.save(activity);
        String after = activity.getSharedPreferences("audio_settings", 0).getString(profile, null);
        if (!Objects.equals(before, after)) saved.accept(profile);
        footHint.setText("Guardado en el perfil " + profileName(profile));
    }

    private String profileName(String key) {
        return "wired".equals(key) ? "Jack / AUX" : "usb".equals(key) ? "USB DAC" : AudioSettings.name(key);
    }

    private String glyph(String key) {
        return "wired".equals(key) ? "headphones" : "bluetooth".equals(key) ? "bluetooth" : "usb".equals(key) ? "usb" : "speaker";
    }

    private void refreshRoute() {
        if (dialog.isShowing()) renderConnection();
    }

    private void renderConnection() {
        if (connectionSlider == null) {
            int initial = Arrays.asList(AudioSettings.PROFILES).indexOf(profile);
            connectionSlider = new ConnectionSlider(activity, initial, index -> {
                profile = AudioSettings.PROFILES[index];
                scroll.scrollTo(0, 0);
                render();
            });
            connectionHost.addView(connectionSlider, new LinearLayout.LayoutParams(-1, -2));
            connectionSteps = row();
            connectionSteps.setGravity(Gravity.CENTER);
            add(connectionHost, connectionSteps, 2);
        }
        AudioDeviceInfo actual = AudioSettings.predictedDevice(activity);
        String active = AudioSettings.route(actual);
        connectionSlider.pages.removeAllViews();
        connectionSteps.removeAllViews();
        int selected = Arrays.asList(AudioSettings.PROFILES).indexOf(profile);

        Button previous = icon("back", "Perfil anterior", MUTED, 0, 15);
        previous.setEnabled(selected > 0);
        previous.setAlpha(selected > 0 ? 1f : .25f);
        connectionSteps.addView(previous, new LinearLayout.LayoutParams(d(34), d(34)));
        previous.setOnClickListener(v -> connectionSlider.select(selected - 1, true));

        for (int i = 0; i < AudioSettings.PROFILES.length; i++) {
            String key = AudioSettings.PROFILES[i];
            boolean connected = active.equals(key);
            AudioSettings s = drafts.get(key).forPlayback();
            LinearLayout page = column();
            page.setPadding(d(2), 0, d(2), 0);

            LinearLayout card = row();
            card.setGravity(Gravity.CENTER_VERTICAL);
            card.setMinimumHeight(d(76));
            card.setPadding(d(14), d(12), d(14), d(12));
            GradientDrawable cardBg = SoundStyle.surface(activity, SURFACE, 16);
            cardBg.setStroke(1, isDark() ? 0x18ffffff : 0x10000000);
            card.setBackground(cardBg);

            FrameLayout markFrame = new FrameLayout(activity);
            markFrame.setBackground(SoundStyle.surface(activity, SOFT, 12));
            ImageView mark = new ImageView(activity);
            mark.setImageDrawable(new MusicStyle.Icon(activity, glyph(key), ACCENT, 20));
            markFrame.addView(mark, new FrameLayout.LayoutParams(d(20), d(20), Gravity.CENTER));
            card.addView(markFrame, new LinearLayout.LayoutParams(d(38), d(38)));

            LinearLayout info = column();
            info.setPadding(d(12), 0, d(8), 0);
            TextView caption = label(profileName(key) + (connected ? " · EN USO AHORA" : " · PERFIL GUARDADO"), 10f, connected ? GREEN : MUTED, 700);
            caption.setLetterSpacing(.06f);
            info.addView(caption);

            String device = connected && actual != null ? actual.getProductName().toString() : profileName(key);
            if ("speaker".equals(key)) device = "Altavoz integrado del teléfono";
            else if ("wired".equals(key) && AudioCapabilities.quadDac()) device = "Jack 3.5 mm / AUX · ESS Quad DAC";
            TextView name = label(device, 14.5f, INK, 700);
            name.setMaxLines(2);
            name.setLineSpacing(d(1), 1.05f);
            name.setEllipsize(TextUtils.TruncateAt.END);
            add(info, name, 2);

            String engine = s.dop ? "DoP128 Nativo · Reserva AUX" : s.direct ? "Alta resolución · PCM DIRECT" : "AudioTrack estándar · " + routeCeilingShort(key);
            TextView status = label(connected ? engine : ("Se aplicará al conectar · activo: " + profileName(active)), 11f, connected ? INK : MUTED, 500);
            status.setMaxLines(2);
            status.setLineSpacing(d(1), 1.05f);
            status.setEllipsize(TextUtils.TruncateAt.END);
            add(info, status, 2);
            card.addView(info, new LinearLayout.LayoutParams(0, -2, 1));

            TextView capBadge = label(routeCeilingBadge(key), 9.5f, 0xffffffff, 700);
            capBadge.setPadding(d(8), d(4), d(8), d(4));
            int badgeColor = "wired".equals(key) ? GOLD : "usb".equals(key) ? CYAN : "bluetooth".equals(key) ? 0xff5856d6 : 0xff636366;
            capBadge.setBackground(SoundStyle.surface(activity, badgeColor, 999));
            card.addView(capBadge);

            page.addView(card, new LinearLayout.LayoutParams(-1, -1));
            connectionSlider.pages.addView(page, new LinearLayout.LayoutParams(Math.max(1, connectionSlider.getWidth()), -1));

            final int index = i;
            FrameLayout step = new FrameLayout(activity);
            step.setBackground(SoundStyle.ripple(activity, Color.TRANSPARENT, 14));
            step.setContentDescription("Editar " + profileName(key) + (i == selected ? ", seleccionado" : "") + (connected ? ", en uso" : ""));
            step.setFocusable(true);
            step.setOnClickListener(v -> connectionSlider.select(index, true));

            View dot = new View(activity);
            dot.setBackground(SoundStyle.surface(activity, i == selected ? ACCENT : (isDark() ? 0x33ffffff : LINE), 3));
            FrameLayout.LayoutParams dp = new FrameLayout.LayoutParams(d(i == selected ? 20 : 6), d(5), Gravity.CENTER);
            step.addView(dot, dp);
            connectionSteps.addView(step, new LinearLayout.LayoutParams(d(28), d(28)));
        }

        Button next = icon("chevron", "Perfil siguiente", MUTED, 0, 15);
        next.setEnabled(selected < AudioSettings.PROFILES.length - 1);
        next.setAlpha(next.isEnabled() ? 1f : .25f);
        connectionSteps.addView(next, new LinearLayout.LayoutParams(d(34), d(34)));
        next.setOnClickListener(v -> connectionSlider.select(selected + 1, true));
    }

    private String routeCeilingBadge(String prof) {
        if ("speaker".equals(prof)) return "MÁX 16-BIT / 48K";
        if ("bluetooth".equals(prof)) return "MÁX 24-BIT / 96K";
        if ("wired".equals(prof)) return AudioCapabilities.dop() ? "32-BIT / 384K · DoP" : "32-BIT / 384K";
        return "32-BIT / 192K";
    }

    private String routeCeilingShort(String prof) {
        if ("speaker".equals(prof)) return "Máx. 16 bits / 48 kHz";
        if ("bluetooth".equals(prof)) return "Máx. 24 bits / 96 kHz";
        if ("wired".equals(prof)) return "Hasta 32 bits / 384 kHz si la ruta lo admite";
        return "Hasta 32 bits / 192 kHz";
    }

    private void render() {
        int y = scroll.getScrollY();
        body.removeAllViews();
        eqGraph = null;
        for (int i = 0; i < 3; i++) {
            boolean active = i == tab;
            tabs[i].setTextColor(active ? (isDark() ? 0xffffffff : 0xff111111) : (isDark() ? 0xff8e8e93 : 0xff6c6c70));
            tabs[i].setTypeface(SoundStyle.font(activity, active ? 700 : 500));
            if (active) {
                int activeBg = isDark() ? 0xff2c2c34 : 0xffffffff;
                GradientDrawable tabShape = SoundStyle.surface(activity, activeBg, 9);
                if (isDark()) tabShape.setStroke(1, 0x22ffffff);
                else tabShape.setStroke(1, 0x14000000);
                tabs[i].setBackground(tabShape);
                tabs[i].setElevation(d(isDark() ? 1f : 2f));
                tabs[i].setOutlineSpotShadowColor(0x28000000);
                tabs[i].setOutlineAmbientShadowColor(0x14000000);
            } else {
                tabs[i].setBackground(null);
                tabs[i].setElevation(0);
            }
            tabs[i].setSelected(active);
        }
        AudioSettings s = drafts.get(profile);
        s.validate();
        renderConnection();
        if (tab == 0) {
            renderOutput(s);
        } else if (tab == 1) {
            renderEqualizer(s);
        } else {
            renderVolume(s);
        }
        footHint.setText("Los ajustes se guardan por separado para " + profileName(profile));
        polish(body);
        scroll.post(() -> {
            if (dialog.isShowing()) scroll.scrollTo(0, y);
        });
    }

    private void renderOutput(AudioSettings s) {
        AudioSettings effective = s.forPlayback();
        String problem = effective.outputProblem(effective.rate);
        if (!problem.isEmpty()) addProblemNotice(problem);

        // 1. MOTOR Y RESOLUCIÓN DE SALIDA
        section("RESOLUCIÓN Y MOTOR DE SALIDA");

        if ("wired".equals(profile) && AudioCapabilities.directPcm()) {
            segmentedCard(
                "Motor de salida (Jack / AUX)",
                "AudioTrack estándar o PCM DIRECT Hi-Res al Quad DAC.",
                new String[]{"Estándar (AudioTrack)", "Alta Resolución (Direct)"},
                s.direct ? 1 : 0,
                i -> {
                    s.direct = (i == 1);
                    if (s.direct && s.encoding == 0) s.encoding = 2;
                    if (!s.direct && s.rate > 192000) s.rate = 0;
                    render();
                },
                "Alta resolución abre una sesión PCM DIRECT exclusiva con el módulo LSPosed para enviar hasta 384 kHz al ESS Quad DAC sin pasar por el mezclador normal de Android."
            );
        } else if ("bluetooth".equals(profile) && AudioCapabilities.directPcm()) {
            segmentedCard(
                "Motor de salida Bluetooth",
                "AudioTrack normal o ruta directa exclusiva A2DP a 96 kHz.",
                new String[]{"Estándar (AudioTrack)", "Directo 96 kHz (LDAC)"},
                s.direct ? 1 : 0,
                i -> {
                    s.direct = (i == 1);
                    if (s.direct) {
                        s.rate = 96000;
                        s.encoding = 2;
                    }
                    render();
                },
                "Directo 96 kHz solicita PCM DIRECT a 96 kHz / 24 bits para auriculares LDAC compatibles con la política BT96."
            );
        }

        renderBitDepthCard(s, effective);
        renderSampleRateCard(s, effective);

        segmentedCard(
            "Búfer de reproducción",
            null,
            new String[]{"100 ms", "200 ms", "400 ms", "800 ms"},
            index(AudioSettings.BUFFERS, s.bufferMs),
            i -> {
                s.bufferMs = AudioSettings.BUFFERS[i];
                render();
            },
            "Margen de memoria contra microcortes. 200 ms o 400 ms ofrece la mayor estabilidad en archivos Hi-Res y DSD."
        );

        // 2. HARDWARE LG V60: QUAD DAC & DoP128 (Wired / AUX only)
        if ("wired".equals(profile)) {
            if (AudioCapabilities.quadDac()) {
                section("HARDWARE LG V60 · ESS QUAD DAC");
                renderDacGain();
            }
            if (AudioCapabilities.dop()) {
                segmentedCard(
                    "Transporte DSD Nativo (DoP128)",
                    "DSD128 directo por AUX sin conversión.",
                    new String[]{"Convertir DSD a PCM", "DoP128 Directo · AUX"},
                    s.dop ? 1 : 0,
                    i -> {
                        s.dop = (i == 1);
                        render();
                    },
                    "DoP128 empaqueta el flujo DSD128 (5.6 MHz) dentro de tramas PCM de 24 bits / 352.8 kHz sin conversión a PCM ni ecualizador. La app coordina y mantiene el modo AUX automáticamente mientras suena el archivo DSD128."
                );
            }
        }

        // 3. BLUETOOTH CODEC & LDAC (Bluetooth only)
        if ("bluetooth".equals(profile)) {
            renderBluetooth();
        }

        // 4. TRANSICIONES Y CONVERSIÓN
        section("CONVERSIÓN PCM");

        segmentedCard(
            "Motor de remuestreo",
            "Solo activo si la frecuencia difiere de la pista.",
            new String[]{"SWR (Rápido)", "SoXR (Alta precisión)"},
            s.resampler,
            i -> {
                s.resampler = i;
                render();
            },
            "Si la canción coincide con la frecuencia de salida, no hay remuestreo. SoXR aplica interpolación de fase lineal de 28 bits."
        );

    }

    private void renderBitDepthCard(AudioSettings s, AudioSettings effective) {
        LinearLayout box = cardBox();
        String subText = "speaker".equals(profile) ? "Fijo a 16 bits por límite físico del altavoz."
                : "bluetooth".equals(profile) ? (s.direct ? "Directo 96 kHz opera en 24 bits." : "Hasta 24 bits (máx. soportado por LDAC / A2DP).")
                : effective.needsWideContainer() ? "Contenedor de 24 bits activo para Hi-Res."
                : "Precisión PCM entregada a la salida.";
        header(box, "Profundidad de bits (Formato PCM)", subText,
                "Define el contenedor PCM entregado a la salida. En Altavoz se bloquea a 16 bits; en Bluetooth permite hasta 24 bits; en Jack / AUX y USB DAC están desbloqueados todos los formatos compatibles.");

        String[] labels = {"Float 32", "16 bits", "24 bits", "32 bits"};
        LinearLayout row1 = row();
        row1.setPadding(0, d(10), 0, 0);
        for (int i = 0; i < 4; i++) {
            final int idx = i;
            boolean enabled = optionEnabled("Formato de salida", idx);
            boolean active = effective.encoding == idx;
            Button b = button(labels[i], false);
            b.setTextSize(10.5f);
            b.setMinimumHeight(d(42));
            b.setSelected(active);
            b.setTextColor(active ? 0xffffffff : (enabled ? INK : MUTED));
            if (!enabled) {
                b.setCompoundDrawablesWithIntrinsicBounds(new MusicStyle.Icon(activity, "lock", active ? 0xffffffff : MUTED, 11), null, null, null);
                b.setCompoundDrawablePadding(d(2));
            }
            int btnBg = active ? ACCENT : (isDark() ? 0xff25252b : 0xffefeff4);
            b.setBackground(SoundStyle.ripple(activity, btnBg, 11));
            b.setAlpha(enabled ? 1f : 0.45f);
            b.setOnClickListener(v -> {
                if (enabled) {
                    s.encoding = idx;
                    if (idx == 0) s.direct = false;
                    render();
                    changed();
                } else {
                    Toast.makeText(activity, lockReasonForEncoding(idx), Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, d(42), 1);
            if (i > 0) lp.leftMargin = d(6);
            row1.addView(b, lp);
        }
        box.addView(row1);
        body.addView(box);
    }

    private void renderSampleRateCard(AudioSettings s, AudioSettings effective) {
        LinearLayout box = cardBox();
        int currentRateIdx = index(UI_RATES, effective.rate);
        String currentName = UI_RATE_NAMES[currentRateIdx];
        if (effective.rate == 0) {
            if ("speaker".equals(profile)) currentName = "Automática (Original hasta 48 kHz)";
            else if ("bluetooth".equals(profile)) currentName = "Automática (Original hasta 96 kHz)";
            else currentName = "Original del archivo (Bit-Perfect)";
        }
        String subText = "speaker".equals(profile) ? "Limitada a máx. 48 kHz en altavoz."
                : "bluetooth".equals(profile) ? "Limitada a máx. 96 kHz en Bluetooth."
                : "Frecuencia nativa de la pista o fija.";

        header(box, "Frecuencia de muestreo", subText,
                "Elige «Original del archivo» para conservar la frecuencia nativa de cada canción dentro del límite físico de la salida conectada, o fija una frecuencia constante.");

        LinearLayout selector = row();
        selector.setGravity(Gravity.CENTER_VERTICAL);
        selector.setPadding(d(12), d(11), d(12), d(11));
        selector.setBackground(SoundStyle.ripple(activity, isDark() ? 0xff25252b : 0xffefeff4, 12));

        LinearLayout textCol = column();
        TextView valTv = label(currentName, 14.5f, INK, 700);
        valTv.setMaxLines(2);
        valTv.setLineSpacing(d(1), 1.05f);
        textCol.addView(valTv);
        String capNote = "speaker".equals(profile) ? "Frecuencias > 48 kHz bloqueadas en Altavoz"
                : "bluetooth".equals(profile) ? "Frecuencias > 96 kHz bloqueadas en Bluetooth"
                : "Todas las frecuencias habilitadas para este perfil";
        TextView noteTv = label(capNote, 10.5f, MUTED, 500);
        add(textCol, noteTv, 2);
        selector.addView(textCol, new LinearLayout.LayoutParams(0, -2, 1));

        TextView changeLbl = label("Cambiar ›", 12, ACCENT, 700);
        selector.addView(changeLbl);

        selector.setOnClickListener(v -> showSampleRateSheet(s, effective));
        add(box, selector, 10);
        body.addView(box);
    }

    private void showSampleRateSheet(AudioSettings s, AudioSettings effective) {
        Dialog sheet = new AppDialog(activity);
        String summary = "speaker".equals(profile)
                ? "En Altavoz solo se permiten hasta 48 kHz (el altavoz físico no reproduce ultrasonidos)."
                : "bluetooth".equals(profile)
                ? "En Bluetooth el techo físico de LDAC / A2DP es 96 kHz."
                : "En Jack / AUX están disponibles todas las frecuencias hasta 384 kHz.";
        LinearLayout content = sheetContent(sheet, "Frecuencia de muestreo (" + profileName(profile) + ")", summary);
        ScrollView list = new ScrollView(activity);
        list.setVerticalScrollBarEnabled(false);
        LinearLayout items = column();

        int selectedIdx = index(UI_RATES, effective.rate);
        for (int i = 0; i < UI_RATES.length; i++) {
            final int idx = i;
            final int rateVal = UI_RATES[i];
            boolean enabled = isRateIndexEnabled(rateVal);
            boolean isSelected = (i == selectedIdx);

            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(d(14), d(12), d(12), d(12));
            line.setBackground(SoundStyle.ripple(activity, isSelected ? SOFT : SURFACE, 12));
            line.setAlpha(enabled ? 1f : 0.45f);

            LinearLayout nameCol = column();
            String displayTitle = UI_RATE_NAMES[i];
            if (rateVal == 0) {
                if ("speaker".equals(profile)) displayTitle = "Original del archivo (máx. 48 kHz)";
                else if ("bluetooth".equals(profile)) displayTitle = "Original del archivo (máx. 96 kHz)";
            }
            TextView name = label(displayTitle, 14, isSelected ? ACCENT : INK, isSelected ? 700 : 600);
            if (!enabled) {
                name.setCompoundDrawablesWithIntrinsicBounds(new MusicStyle.Icon(activity, "lock", MUTED, 13), null, null, null);
                name.setCompoundDrawablePadding(d(6));
            }
            nameCol.addView(name);

            String reason = rateOptionSubtext(rateVal, enabled);
            if (!reason.isEmpty()) {
                TextView rTv = label(reason, 10.5f, enabled ? MUTED : AMBER, 500);
                add(nameCol, rTv, 2);
            }
            line.addView(nameCol, new LinearLayout.LayoutParams(0, -2, 1));

            if (isSelected) {
                ImageView mark = new ImageView(activity);
                mark.setImageDrawable(new MusicStyle.Icon(activity, "check", ACCENT, 18));
                line.addView(mark, new LinearLayout.LayoutParams(d(24), d(24)));
            }

            line.setOnClickListener(v -> {
                if (enabled) {
                    sheet.dismiss();
                    s.rate = rateVal;
                    if (s.rate > 192000) s.encoding = 3;
                    render();
                    changed();
                } else {
                    Toast.makeText(activity, reason, Toast.LENGTH_SHORT).show();
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = d(4);
            items.addView(line, lp);
        }
        list.addView(items);
        int maximum = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .54f);
        content.addView(list, new LinearLayout.LayoutParams(-1, Math.min(maximum, d(UI_RATES.length * 62))));
        showSheet(sheet, content);
    }

    private boolean isRateIndexEnabled(int rateVal) {
        AudioSettings s = drafts.get(profile).forPlayback();
        if ("speaker".equals(profile)) {
            return rateVal == 0 || rateVal <= 48000;
        }
        if ("bluetooth".equals(profile)) {
            if (s.direct) return rateVal == 96000;
            return rateVal == 0 || rateVal <= 96000;
        }
        return s.acceptsRate(rateVal);
    }

    private String rateOptionSubtext(int rateVal, boolean enabled) {
        if (rateVal == 0) return "Se adapta automáticamente a cada pista sin remuestreo innecesario";
        if (!enabled) {
            if ("speaker".equals(profile) && rateVal > 48000) {
                return "Bloqueado: el altavoz físico del teléfono no reproduce más de 48 kHz";
            }
            if ("bluetooth".equals(profile)) {
                if (rateVal > 96000) return "Bloqueado: el límite máximo de Bluetooth LDAC / A2DP es 96 kHz";
                return "El modo Directo 96 kHz opera fijo a 96 kHz";
            }
            if (rateVal == 384000) {
                return "Requiere perfil Jack / AUX con el motor «Alta Resolución (Direct)» activo";
            }
        }
        if (rateVal == 384000) return "Ultra Hi-Res · activa automáticamente contenedor de 32 bits";
        if (rateVal >= 88200) return "Alta resolución (Hi-Res Audio)";
        return "Estándar CD / Estudio";
    }

    private String lockReasonForEncoding(int idx) {
        if ("speaker".equals(profile)) {
            return "Bloqueado en Altavoz: el altavoz integrado está limitado físicamente a 16 bits / 48 kHz.";
        }
        if ("bluetooth".equals(profile)) {
            if (idx == 0) return "Float 32 está bloqueado en Bluetooth; usa 16 o 24 bits.";
            if (idx == 3) return "Bloqueado en Bluetooth: el máximo admitido por LDAC / A2DP es 24 bits.";
            return "El modo Directo 96 kHz de Bluetooth requiere 24 bits.";
        }
        AudioSettings s = drafts.get(profile).forPlayback();
        if (s.rate > 192000 && idx != 3) {
            return "Para 384 kHz esta ROM requiere contenedor de 32 bits.";
        }
        if (s.direct && idx == 0) {
            return "La ruta directa de Alta Resolución requiere PCM entero (16, 24 o 32 bits).";
        }
        return "No disponible en este modo.";
    }

    private void renderDacGain() {
        if (!RootAccess.canRequest()) {
            LinearLayout note = cardBox();
            header(note, "Ganancia del Quad DAC", "Administrada por el sistema", null);
            add(note, label("La reproducción por Jack / AUX sigue disponible. El selector físico de ganancia requiere acceso root.", 11, MUTED, 400), 6);
            Button system = button("Ajustes de sonido del teléfono", false);
            add(note, system, 10);
            system.setOnClickListener(v -> {
                try { activity.startActivity(new Intent(Settings.ACTION_SOUND_SETTINGS)); } catch (ActivityNotFoundException ignored) {}
            });
            body.addView(note);
            return;
        }
        int active = gainBusy ? -1 : DacGain.confirmedMode();
        String status = gainBusy ? (gainPending == null ? "Consultando el DAC…" : "Aplicando " + DacGain.name(gainPending) + "…")
                : active > 0 ? "Modo activo confirmado: " + DacGain.name(active) : "Sin lectura confirmada en este instante";
        LinearLayout box = cardBox();
        header(box, "Ganancia física del ESS Quad DAC", status,
                "Conmuta el registro físico del amplificador ESS Sabre para la salida Jack 3.5 mm / AUX. Cuando reproduces DSD128 por DoP, la app reserva temporalmente el perfil AUX de forma automática.");
        TextView detail = label(DacGain.description(), 10.5f, MUTED, 450);
        detail.setContentDescription("Estado leído del DAC");
        add(box, detail, 8);
        String[] names = {"Normal", "Alta impedancia", "AUX (Línea / DoP)"};
        String[] descriptions = {
            "Para auriculares sensibles e IEMs · atenuación estándar",
            "Máxima potencia para auriculares de estudio (>50 Ω)",
            "Nivel de línea para amplificador externo · compatible con DoP128"
        };
        for (int i = 0; i < names.length; i++) {
            final int mode = i + 1;
            boolean checked = active == mode, pending = gainBusy && gainPending != null && gainPending == mode;
            LinearLayout option = row();
            option.setGravity(Gravity.CENTER_VERTICAL);
            option.setPadding(d(13), d(11), d(13), d(11));
            option.setBackground(SoundStyle.ripple(activity, checked ? SOFT : (isDark() ? 0xff141418 : BG), 12));
            option.setContentDescription("Ganancia DAC " + (mode == 2 ? "Alta" : names[i]));
            option.setSelected(checked);
            option.setEnabled(!gainBusy);
            FrameLayout mark = new FrameLayout(activity);
            GradientDrawable ring = new GradientDrawable();
            ring.setShape(GradientDrawable.OVAL);
            ring.setSize(d(18), d(18));
            if (checked) {
                ring.setStroke(d(2), ACCENT);
                ring.setColor(Color.TRANSPARENT);
                View dot = new View(activity);
                GradientDrawable dotBg = new GradientDrawable();
                dotBg.setShape(GradientDrawable.OVAL);
                dotBg.setColor(ACCENT);
                dotBg.setSize(d(9), d(9));
                dot.setBackground(dotBg);
                mark.addView(dot, new FrameLayout.LayoutParams(d(9), d(9), Gravity.CENTER));
            } else {
                ring.setStroke(d(1.5f), MUTED);
                ring.setColor(Color.TRANSPARENT);
            }
            mark.setBackground(ring);
            LinearLayout.LayoutParams mp = new LinearLayout.LayoutParams(d(18), d(18));
            mp.rightMargin = d(10);
            option.addView(mark, mp);
            LinearLayout text = column();
            TextView title = label(names[i] + (checked ? " · ACTIVO" : pending ? " · aplicando…" : ""), 13, checked ? ACCENT : INK, 650);
            text.addView(title);
            TextView caption = label(descriptions[i], 10.5f, MUTED, 450);
            add(text, caption, 3);
            option.addView(text, new LinearLayout.LayoutParams(0, -2, 1));
            option.setOnClickListener(v -> { if (!gainBusy) requestGain(mode); });
            add(box, option, 8);
        }
        Button read = button(gainBusy ? "Consultando…" : "Consultar registros del Quad DAC", false);
        read.setContentDescription("Consultar DAC");
        read.setEnabled(!gainBusy);
        read.setOnClickListener(v -> requestGain(null));
        add(box, read, 10);
        body.addView(box);
    }

    private void requestGain(Integer mode) {
        gainPending = mode;
        gainBusy = true;
        render();
        DacGain.request(activity, mode, message -> {
            gainBusy = false;
            gainPending = null;
            if (dialog.isShowing() && !activity.isFinishing()) {
                render();
                footHint.setText(message);
            }
        });
    }

    private void renderBluetooth() {
        BluetoothCodecs.Snapshot snapshot = BluetoothCodecs.latest();
        section("CÓDEC BLUETOOTH Y LDAC (MÁX. 24-BIT / 96 KHZ)");
        LinearLayout box = cardBox();
        if (!RootAccess.canRequest()) {
            header(box, "Conexión Bluetooth", "Ajuste de códec administrado por Android", null);
            AudioDeviceInfo device = AudioSettings.predictedDevice(activity);
            String name = device != null && "bluetooth".equals(AudioSettings.route(device)) ? device.getProductName().toString() : "Auriculares Bluetooth";
            add(box, label(name, 15, INK, 650), 8);
            add(box, label("La salida de la app está limitada a 24 bits / 96 kHz para coincidir con el techo físico de A2DP / LDAC.", 11, MUTED, 450), 5);
            Button system = button("Ajustes Bluetooth del teléfono", false);
            add(box, system, 10);
            system.setOnClickListener(v -> {
                try { activity.startActivity(new Intent(Settings.ACTION_BLUETOOTH_SETTINGS)); } catch (ActivityNotFoundException ignored) {}
            });
            body.addView(box);
            return;
        }
        header(box, "Códec A2DP negociado", "Consulta o conmuta el códec inalámbrico activo.",
                "Permite verificar el códec negociado con tus auriculares y activar LDAC a su máxima resolución permitida (24 bits / 96 kHz).");
        TextView value = label(snapshot == null ? "Sin lectura reciente" : snapshot.description(), snapshot == null ? 15 : 16, INK, 650);
        add(box, value, 8);
        if (snapshot != null) {
            add(box, label(snapshot.device() + " · hace " + Math.max(0, (SystemClock.elapsedRealtime() - snapshot.observed) / 1000) + " s", 10.5f, MUTED, 450), 4);
            add(box, label(snapshot.mixerDescription(), 11, MUTED, 450), 5);
        }
        TextView state = label(codecMessage, 10.5f, MUTED, 450);
        state.setMaxLines(4);
        add(box, state, 6);

        LinearLayout actions = row();
        Button read = button(codecBusy ? "Consultando…" : "Consultar · root", false);
        read.setEnabled(!codecBusy);
        Button choose = button("Cambiar códec", false);
        choose.setEnabled(!codecBusy && RootAccess.granted() && snapshot != null && snapshot.data.optJSONArray("available") != null && snapshot.data.optJSONArray("available").length() > 0);
        LinearLayout.LayoutParams ap = new LinearLayout.LayoutParams(0, d(42), 1);
        actions.addView(read, ap);
        LinearLayout.LayoutParams cp = new LinearLayout.LayoutParams(0, d(42), 1);
        cp.leftMargin = d(8);
        actions.addView(choose, cp);
        add(box, actions, 12);

        read.setOnClickListener(v -> runCodec(null, null));
        choose.setOnClickListener(v -> {
            BluetoothCodecs.Snapshot current = BluetoothCodecs.latest();
            if (current == null) return;
            org.json.JSONArray available = current.data.optJSONArray("available");
            if (available == null) return;
            String[] names = new String[available.length()];
            int selected = -1;
            for (int i = 0; i < names.length; i++) {
                names[i] = available.optJSONObject(i).optString("name", "Códec");
                if (available.optJSONObject(i).optInt("type") == current.type()) selected = i;
            }
            Dialog choices = choiceSheet("Cambiar códec Bluetooth", "Compatible con " + current.device() + ".", names, selected, i -> true,
                    i -> runCodec(available.optJSONObject(i).optInt("type"), current.data.optString("address")));
            choose.setTag(choices);
            choices.setOnDismissListener(d -> {
                choose.setTag(null);
                sheets.remove(choices);
            });
        });

        Button maximum = button("Activar LDAC · Máxima calidad (24 bits / 96 kHz)", true);
        boolean ldac = false;
        if (snapshot != null) {
            org.json.JSONArray codecs = snapshot.data.optJSONArray("available");
            if (codecs != null) {
                for (int i = 0; i < codecs.length(); i++) {
                    if (codecs.optJSONObject(i).optInt("type", -1) == 4) ldac = true;
                }
            }
        }
        maximum.setEnabled(!codecBusy && snapshot != null && RootAccess.granted() && ldac);
        add(box, maximum, 10);
        maximum.setOnClickListener(v -> {
            BluetoothCodecs.Snapshot current = BluetoothCodecs.latest();
            if (current == null) return;
            codecBusy = true;
            codecMessage = "Negociando LDAC 24-bit / 96 kHz con Android…";
            render();
            BluetoothCodecs.maximum(activity, current.data.optString("address"), message -> {
                codecBusy = false;
                codecMessage = message;
                BluetoothCodecs.Snapshot confirmed = BluetoothCodecs.latest();
                if (confirmed != null && confirmed.data.optBoolean("maximumConfirmed")) {
                    drafts.put("bluetooth", AudioSettings.load(activity, "bluetooth"));
                    saved.accept("bluetooth");
                }
                if (dialog.isShowing() && !activity.isFinishing()) render();
            });
        });
        body.addView(box);
    }

    private void runCodec(Integer type, String address) {
        codecBusy = true;
        codecMessage = type == null ? "Consultando códec A2DP…" : "Aplicando códec Bluetooth…";
        render();
        BluetoothCodecs.request(activity, type, address, message -> {
            codecBusy = false;
            codecMessage = message;
            if (dialog.isShowing() && !activity.isFinishing()) render();
        });
    }

    private void section(String name) {
        TextView v = label(name, 10f, ACCENT, 700);
        v.setLetterSpacing(.09f);
        v.setPadding(d(4), d(12), 0, d(2));
        add(body, v, 4);
    }

    private LinearLayout cardBox() {
        LinearLayout box = column();
        GradientDrawable bg = SoundStyle.surface(activity, SURFACE, 18);
        bg.setStroke(1, isDark() ? 0x16ffffff : 0x10000000);
        box.setBackground(bg);
        box.setPadding(d(16), d(14), d(16), d(14));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = d(8);
        box.setLayoutParams(lp);
        return box;
    }

    private LinearLayout header(LinearLayout box, String title, String subtitle, String help) {
        LinearLayout line = row();
        line.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout names = column();
        TextView t = label(title, 14f, INK, 650);
        names.addView(t);
        if (subtitle != null && !subtitle.isEmpty()) {
            add(names, label(subtitle, 11f, MUTED, 450), 3);
        }
        line.addView(names, new LinearLayout.LayoutParams(0, -2, 1));
        if (help != null) {
            Button info = icon("info", "Ayuda: " + title, MUTED, 0, 15);
            line.addView(info, new LinearLayout.LayoutParams(d(34), d(34)));
            info.setOnClickListener(v -> showHelp(title, help));
        }
        box.addView(line);
        return line;
    }

    private void switchCard(String title, String subtitle, boolean checked, java.util.function.Consumer<Boolean> onChanged, String help) {
        LinearLayout box = cardBox();
        LinearLayout line = header(box, title, subtitle, help);
        Switch toggle = new Switch(activity);
        toggle.setContentDescription(title);
        toggle.setChecked(checked);
        toggle.setButtonTintList(null);
        toggle.setThumbTintList(ColorStateList.valueOf(checked ? ACCENT : 0xffb9c0c9));
        toggle.setTrackTintList(ColorStateList.valueOf(checked ? 0xfff5bdc7 : (isDark() ? 0xff3a3a3c : 0xffe4e7ec)));
        toggle.setMinimumHeight(d(42));
        line.addView(toggle, new LinearLayout.LayoutParams(d(48), d(42)));
        toggle.setOnCheckedChangeListener((v, value) -> {
            onChanged.accept(value);
            changed();
        });
        body.addView(box);
    }

    private void segmentedCard(String title, String subtitle, String[] options, int selected, IntConsumer choose, String help) {
        LinearLayout box = cardBox();
        header(box, title, subtitle, help);
        LinearLayout segments = row();
        segments.setPadding(0, d(10), 0, 0);
        for (int i = 0; i < options.length; i++) {
            if (options.length > 3 && i % 2 == 0) {
                segments = row();
                segments.setPadding(0, d(8), 0, 0);
                box.addView(segments);
            }
            final int index = i;
            boolean active = (i == selected);
            boolean enabled = optionEnabled(title, i);
            Button b = button(options[i], false);
            b.setTextSize(11.5f);
            b.setPadding(d(6), d(4), d(6), d(4));
            b.setLineSpacing(d(1), 1.05f);
            b.setSelected(active);
            b.setTextColor(active ? 0xffffffff : (enabled ? INK : MUTED));
            int bgCol = active ? ACCENT : (isDark() ? 0xff25252b : 0xffefeff4);
            b.setBackground(SoundStyle.ripple(activity, bgCol, 11));
            b.setEnabled(enabled);
            b.setAlpha(enabled ? 1f : .38f);
            b.setMinimumHeight(d(40));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
            if (options.length > 3 ? i % 2 > 0 : i > 0) lp.leftMargin = d(6);
            segments.addView(b, lp);
            b.setOnClickListener(v -> {
                if (optionEnabled(title, index)) {
                    choose.accept(index);
                    changed();
                }
            });
        }
        if (options.length <= 3) box.addView(segments);
        body.addView(box);
    }

    private Dialog choiceSheet(String title, String summary, String[] options, int selected, java.util.function.IntPredicate enabled, IntConsumer chosen) {
        Dialog sheet = new AppDialog(activity);
        LinearLayout content = sheetContent(sheet, title, summary);
        ScrollView list = new ScrollView(activity);
        list.setVerticalScrollBarEnabled(false);
        LinearLayout items = column();
        for (int i = 0; i < options.length; i++) {
            final int index = i;
            LinearLayout line = row();
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setPadding(d(14), d(14), d(12), d(14));
            line.setMinimumHeight(d(48));
            line.setBackground(SoundStyle.ripple(activity, i == selected ? SOFT : SURFACE, 12));
            line.setEnabled(enabled.test(i));
            line.setAlpha(line.isEnabled() ? 1 : .35f);
            TextView name = label(options[i], 14, i == selected ? ACCENT : INK, i == selected ? 650 : 500);
            line.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
            if (i == selected) {
                ImageView mark = new ImageView(activity);
                mark.setImageDrawable(new MusicStyle.Icon(activity, "check", ACCENT, 18));
                line.addView(mark, new LinearLayout.LayoutParams(d(24), d(24)));
            }
            line.setOnClickListener(v -> {
                if (enabled.test(index)) {
                    sheet.dismiss();
                    chosen.accept(index);
                }
            });
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
            lp.topMargin = d(4);
            items.addView(line, lp);
        }
        list.addView(items);
        int maximum = Math.round(activity.getResources().getDisplayMetrics().heightPixels * .48f);
        content.addView(list, new LinearLayout.LayoutParams(-1, Math.min(maximum, d(options.length * 55))));
        showSheet(sheet, content);
        return sheet;
    }

    private LinearLayout sheetContent(Dialog sheet, String title, String summary) {
        LinearLayout content = column();
        content.setBackground(SoundStyle.surface(activity, SURFACE, 26));
        content.setPadding(d(20), d(12), d(20), d(24));
        View handle = new View(activity);
        handle.setBackground(SoundStyle.surface(activity, LINE, 2));
        LinearLayout.LayoutParams hp = new LinearLayout.LayoutParams(d(34), d(4));
        hp.gravity = Gravity.CENTER_HORIZONTAL;
        hp.bottomMargin = d(14);
        content.addView(handle, hp);
        LinearLayout row = row();
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = label(title, 18.5f, INK, 700);
        name.setLetterSpacing(-.02f);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        Button close = icon("close", "Cerrar panel", MUTED, LINE, 17);
        row.addView(close, new LinearLayout.LayoutParams(d(36), d(36)));
        close.setOnClickListener(v -> sheet.dismiss());
        content.addView(row);
        if (summary != null) {
            TextView description = label(summary, 11.5f, MUTED, 450);
            description.setLineSpacing(d(3), 1.08f);
            add(content, description, 8);
        }
        return content;
    }

    private void showSheet(Dialog sheet, LinearLayout content) {
        sheets.add(sheet);
        sheet.setOnDismissListener(d -> sheets.remove(sheet));
        sheet.setContentView(content);
        sheet.show();
        if (sheet instanceof AppDialog) ((AppDialog) sheet).palette(BG, SURFACE);
        Window window = sheet.getWindow();
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
            window.addFlags(WindowManager.LayoutParams.FLAG_DIM_BEHIND);
            WindowManager.LayoutParams a = window.getAttributes();
            a.dimAmount = .38f;
            window.setAttributes(a);
            window.setGravity(Gravity.BOTTOM);
            window.setLayout(-1, -2);
        }
    }

    private void showHelp(String title, String message) {
        Dialog sheet = new AppDialog(activity);
        LinearLayout content = sheetContent(sheet, title, message);
        Button ok = button("Entendido", true);
        add(content, ok, 18);
        ok.setOnClickListener(v -> sheet.dismiss());
        showSheet(sheet, content);
    }

    private void confirmReset() {
        Dialog sheet = new AppDialog(activity);
        LinearLayout content = sheetContent(sheet, "Restablecer " + profileName(profile),
                "Se restaurarán los valores recomendados para " + profileName(profile) + ".");
        Button reset = button("Restablecer perfil", true);
        add(content, reset, 18);
        reset.setOnClickListener(v -> {
            AudioSettings fresh = new AudioSettings(profile);
            fresh.validate();
            drafts.put(profile, fresh);
            sheet.dismiss();
            render();
            changed();
        });
        showSheet(sheet, content);
    }

    private void sliderCard(String title, String subtitle, int min, int max, int value, String unit, IntConsumer onChanged, String help) {
        LinearLayout box = cardBox();
        header(box, title, subtitle, help);
        LinearLayout valRow = row();
        valRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView badge = label(formatSliderValue(value, unit), 22, INK, 700);
        badge.setLetterSpacing(-.03f);
        valRow.addView(badge, new LinearLayout.LayoutParams(0, -2, 1));
        if (value != 0) {
            Button zeroBtn = button("Restablecer a 0", false);
            zeroBtn.setTextSize(10.5f);
            zeroBtn.setMinimumHeight(d(28));
            zeroBtn.setPadding(d(10), d(2), d(10), d(2));
            zeroBtn.setOnClickListener(v -> {
                onChanged.accept(0);
                changed();
                render();
            });
            valRow.addView(zeroBtn);
        }
        add(box, valRow, 10);

        SeekBar bar = new SeekBar(activity);
        bar.setContentDescription(title);
        bar.setMax(max - min);
        bar.setProgress(value - min);
        bar.setPadding(d(5), d(8), d(5), d(8));
        bar.setMinimumHeight(d(38));
        bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            public void onProgressChanged(SeekBar b, int n, boolean user) {
                if (user) {
                    badge.setText(formatSliderValue(n + min, unit));
                    onChanged.accept(n + min);
                    changed();
                }
            }
            public void onStartTrackingTouch(SeekBar b) {}
            public void onStopTrackingTouch(SeekBar b) {}
        });
        add(box, bar, 6);
        body.addView(box);
    }

    private String formatSliderValue(int value, String unit) {
        if (" %".equals(unit)) return value == 0 ? "Centrado (0%)" : Math.abs(value) + " % " + (value < 0 ? "izquierda" : "derecha");
        return signed(value) + unit;
    }

    private boolean optionEnabled(String title, int index) {
        AudioSettings s = drafts.get(profile).forPlayback();
        if ("Formato de salida".equals(title)) {
            if ("speaker".equals(profile)) {
                return index == 1; // Only 16-bit for speaker
            }
            if ("bluetooth".equals(profile)) {
                return s.direct ? index == 2 : (index == 1 || index == 2); // 16-bit or 24-bit max for Bluetooth
            }
            if (s.rate > 192000) return index == 3;
            return !s.direct || index != 0;
        }
        if ("Conversión DSD a PCM".equals(title)) {
            if ("bluetooth".equals(profile)) return index == 0; // 88.2 kHz fits inside 96 kHz ceiling
            return true;
        }
        return true;
    }

    private void addProblemNotice(String problem) {
        LinearLayout notice = cardBox();
        notice.setBackground(SoundStyle.surface(activity, SOFT, 16));
        header(notice, "Revisa estos ajustes", "La combinación elegida requiere atención.", problem);
        body.addView(notice);
    }

    private void polish(View view) {
        if (view instanceof SeekBar) {
            SeekBar bar = (SeekBar) view;
            bar.setBackground(null);
            GradientDrawable base = SoundStyle.surface(activity, LINE, 2), fill = SoundStyle.surface(activity, ACCENT, 2);
            LayerDrawable track = new LayerDrawable(new Drawable[]{base, new ClipDrawable(fill, Gravity.LEFT, ClipDrawable.HORIZONTAL)});
            track.setId(0, android.R.id.background);
            track.setId(1, android.R.id.progress);
            for (int i = 0; i < 2; i++) {
                track.setLayerHeight(i, d(3));
                track.setLayerGravity(i, Gravity.CENTER_VERTICAL);
            }
            bar.setProgressDrawable(track);
            GradientDrawable thumb = new GradientDrawable();
            thumb.setShape(GradientDrawable.OVAL);
            thumb.setColor(ACCENT);
            thumb.setSize(d(15), d(15));
            thumb.setStroke(d(3), SURFACE);
            bar.setThumb(thumb);
            bar.setThumbOffset(d(7));
        }
        if (view instanceof ViewGroup) {
            for (int i = 0; i < ((ViewGroup) view).getChildCount(); i++) {
                polish(((ViewGroup) view).getChildAt(i));
            }
        }
    }

    private Button button(String name, boolean primary) {
        Button b = new Button(activity);
        b.setText(name);
        b.setTextSize(12.5f);
        b.setTypeface(SoundStyle.font(activity, 650));
        b.setAllCaps(false);
        b.setIncludeFontPadding(false);
        b.setTextColor(new ColorStateList(
                new int[][]{new int[]{-android.R.attr.state_enabled}, new int[]{}},
                new int[]{MUTED, primary ? Color.WHITE : INK}
        ));
        b.setMinWidth(0);
        b.setMinimumWidth(0);
        b.setMinHeight(0);
        b.setMinimumHeight(d(42));
        b.setPadding(d(8), d(6), d(8), d(6));
        b.setStateListAnimator(null);
        int btnBg = primary ? (isDark() ? ACCENT : DARK) : (isDark() ? 0xff25252b : 0xfff0f2f5);
        b.setBackground(SoundStyle.ripple(activity, btnBg, 12));
        return b;
    }

    private Button icon(String name, String description, int color, int background, int size) {
        return MusicStyle.icon(activity, name, description, color, background, size);
    }

    private TextView label(String value, float size, int color, int weight) {
        return SoundStyle.text(activity, value, size, color, weight);
    }

    private static TextView text(Activity activity, String value, float size, int color) {
        return SoundStyle.text(activity, value, size, color, 500);
    }

    private LinearLayout column() {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.VERTICAL);
        return v;
    }

    private LinearLayout row() {
        LinearLayout v = new LinearLayout(activity);
        v.setOrientation(LinearLayout.HORIZONTAL);
        return v;
    }

    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.topMargin = d(top);
        parent.addView(child, lp);
    }

    private int d(float n) {
        return Math.round(n * activity.getResources().getDisplayMetrics().density);
    }

    private static String signed(int n) {
        return (n > 0 ? "+" : "") + n;
    }

    private static int index(int[] values, int n) {
        for (int i = 0; i < values.length; i++) if (values[i] == n) return i;
        return 0;
    }

    private void renderEqualizer(AudioSettings s) {
        section("RESPUESTA EN FRECUENCIA (" + profileName(profile).toUpperCase(Locale.ROOT) + ")");
        LinearLayout curve = cardBox();
        header(curve, "Curva de ecualización paramétrica", "Diez bandas de precisión en 64 bits (−12 a +12 dB)", null);
        eqGraph = new SoundEqGraph(activity, s);
        curve.addView(eqGraph, new LinearLayout.LayoutParams(-1, d(116)));
        body.addView(curve);

        switchCard(
            "Ecualizador activo",
            "Filtros IIR en coma flotante de doble precisión (Float64).",
            s.eq,
            val -> {
                s.eq = val;
                render();
            },
            "Cada banda ajusta una octava del espectro sonoro. Puedes guardar una curva distinta para Altavoz, Jack / AUX, Bluetooth y USB DAC."
        );

        LinearLayout presetCard = cardBox();
        TextView pLabel = text(activity, "PREAJUSTES RÁPIDOS", 10, ACCENT);
        pLabel.setTypeface(SoundStyle.font(activity, 700));
        pLabel.setLetterSpacing(.07f);
        presetCard.addView(pLabel);

        LinearLayout presets = row();
        presets.setPadding(0, d(8), 0, 0);

        String[] pNames = {"Plano (0 dB)", "Graves", "Voces", "Suave"};
        int[][] pValues = {
            {0, 0, 0, 0, 0, 0, 0, 0, 0, 0},
            {4, 4, 3, 1, 0, 0, 0, 0, 0, 0},
            {-2, -2, -1, 0, 1, 2, 2, 1, 0, -1},
            {1, 1, 0, 0, 0, 0, -1, -2, -3, -3}
        };

        for (int i = 0; i < pNames.length; i++) {
            final int n = i;
            Button b = button(pNames[i], false);
            boolean selected = Arrays.equals(s.gains, pValues[i]);
            b.setBackground(SoundStyle.ripple(activity, selected ? ACCENT : (isDark() ? 0xff25252b : 0xfff0f2f5), 11));
            b.setTextColor(selected ? 0xffffffff : INK);
            b.setTextSize(11.5f);
            b.setPadding(0, d(6), 0, d(6));
            LinearLayout.LayoutParams bLp = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) bLp.leftMargin = d(6);
            presets.addView(b, bLp);
            b.setOnClickListener(v -> {
                System.arraycopy(pValues[n], 0, s.gains, 0, 10);
                s.eq = (n != 0);
                changed();
                render();
            });
        }
        presetCard.addView(presets);
        body.addView(presetCard);

        if (s.eq) {
            LinearLayout bandsCard = cardBox();
            TextView bHeader = text(activity, "BANDAS DE FRECUENCIA (TOCA EL VALOR PARA PONER A 0 dB)", 10, MUTED);
            bHeader.setTypeface(SoundStyle.font(activity, 700));
            bHeader.setPadding(0, 0, 0, d(6));
            bandsCard.addView(bHeader);

            int neutralBadgeBg = isDark() ? 0xff25252b : 0xfff3f3f6;

            for (int i = 0; i < 10; i++) {
                final int band = i;
                int hz = AudioSettings.BANDS[i];
                String hzStr = hz >= 1000 ? (hz / 1000) + " kHz" : hz + " Hz";

                LinearLayout bandRow = row();
                bandRow.setGravity(Gravity.CENTER_VERTICAL);
                bandRow.setPadding(0, d(4), 0, d(4));

                TextView freq = text(activity, hzStr, 12, INK);
                freq.setTypeface(SoundStyle.font(activity, 650));
                bandRow.addView(freq, new LinearLayout.LayoutParams(d(54), -2));

                SeekBar bar = new SeekBar(activity);
                bar.setMax(24);
                bar.setProgress(s.gains[i] + 12);
                bar.setProgressTintList(ColorStateList.valueOf(ACCENT));
                bar.setThumbTintList(ColorStateList.valueOf(ACCENT));
                bandRow.addView(bar, new LinearLayout.LayoutParams(0, -2, 1));

                TextView gainBadge = text(activity, signed(s.gains[i]) + " dB", 11, s.gains[i] != 0 ? ACCENT : MUTED);
                gainBadge.setTypeface(SoundStyle.font(activity, 650));
                gainBadge.setGravity(Gravity.CENTER);
                GradientDrawable gBg = new GradientDrawable();
                gBg.setColor(s.gains[i] != 0 ? SOFT : neutralBadgeBg);
                gBg.setCornerRadius(d(8));
                gainBadge.setBackground(gBg);
                gainBadge.setPadding(d(6), d(3), d(6), d(3));

                bar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    public void onProgressChanged(SeekBar b, int progress, boolean fromUser) {
                        if (fromUser) {
                            int val = progress - 12;
                            s.gains[band] = val;
                            changed();
                            if (eqGraph != null) eqGraph.invalidate();
                            gainBadge.setText(signed(val) + " dB");
                            gainBadge.setTextColor(val != 0 ? ACCENT : MUTED);
                            GradientDrawable d = new GradientDrawable();
                            d.setColor(val != 0 ? SOFT : neutralBadgeBg);
                            d.setCornerRadius(d(8));
                            gainBadge.setBackground(d);
                        }
                    }
                    public void onStartTrackingTouch(SeekBar b) {}
                    public void onStopTrackingTouch(SeekBar b) {}
                });

                gainBadge.setOnClickListener(v -> {
                    s.gains[band] = 0;
                    changed();
                    if (eqGraph != null) eqGraph.invalidate();
                    bar.setProgress(12);
                    gainBadge.setText("0 dB");
                    gainBadge.setTextColor(MUTED);
                    GradientDrawable d = new GradientDrawable();
                    d.setColor(neutralBadgeBg);
                    d.setCornerRadius(d(8));
                    gainBadge.setBackground(d);
                });

                LinearLayout.LayoutParams gLp = new LinearLayout.LayoutParams(d(52), -2);
                gLp.leftMargin = d(6);
                bandRow.addView(gainBadge, gLp);

                bandsCard.addView(bandRow);
            }

            body.addView(bandsCard);
        }

        switchCard(
            "Protección contra distorsión (Headroom)",
            "Atenúa automáticamente la entrada si subes bandas por encima de 0 dB.",
            s.headroom,
            val -> {
                s.headroom = val;
                render();
            },
            "Si realzas una banda a +4 dB, reserva 4 dB de margen antes del ecualizador para impedir saturación digital (clipping)."
        );
    }

    private void renderVolume(AudioSettings s) {
        section("CONTROL DE GANANCIA Y DINÁMICA (" + profileName(profile).toUpperCase(Locale.ROOT) + ")");
        sliderCard(
            "Preamplificación digital",
            "Ganancia previa al ecualizador en 64 bits. 0 dB no añade ganancia.",
            -24,
            12,
            s.preamp,
            " dB",
            val -> s.preamp = val,
            "Ajusta la ganancia digital previa. Si está en 0 dB no añade ganancia digital; el formato y los demás efectos se configuran por separado."
        );

        switchCard(
            "Limitador de picos (−0.45 dBFS)",
            "Protege contra recortes digitales cuando aplicas preamplificación o EQ.",
            s.limiter,
            val -> {
                s.limiter = val;
                render();
            },
            "Solo entra en acción cuando hay ganancia positiva, ecualizador o balance activo, impidiendo que los picos superen el techo digital."
        );

        TextView replayInfo = label("ReplayGain no está disponible: Yang envía PCM sin etiquetas de ganancia.", 11, MUTED, 450);
        replayInfo.setPadding(0, d(8), 0, d(12));
        body.addView(replayInfo);

        sliderCard(
            "Balance de canales (L / R)",
            "Equilibrio estéreo entre el canal izquierdo y el derecho.",
            -100,
            100,
            s.balance,
            " %",
            val -> s.balance = val,
            "0% mantiene ambos canales idénticos sin procesamiento adicional."
        );
    }
}
