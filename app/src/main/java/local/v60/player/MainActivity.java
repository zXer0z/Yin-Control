package local.v60.player;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.graphics.*;
import android.os.*;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.Locale;
import java.util.concurrent.Executors;

/** DJ-mixer hardware control shell with 6 direct tabs and live LED meter bridge. */
public final class MainActivity extends Activity {
    private static final String[] TAB_NAMES = {"SALIDA", "EQ", "DINÁMICA", "ESPACIO", "RUTA", "SISTEMA"};
    private static final String[] TAB_ICONS = {"speaker", "sound", "dac", "headphones", "route", "config"};

    private static final java.util.concurrent.ExecutorService rootWorker = Executors.newSingleThreadExecutor();
    private YinUi ui;
    private Switch enabled;
    private TextView statusLine, masterLabel;
    private LinearLayout masterPill;
    private YinUi.LedChip chipRoot, chipDirect, chipHw, chipSignal;
    private YinSoundPage sound;
    private YinRoutePage route;
    private YinConfigPage config;
    private final View[] tabBars = new View[6];
    private final ImageView[] tabIcons = new ImageView[6];
    private final TextView[] tabLabels = new TextView[6];
    private int selected;
    private String importProfile = "", importKey = "";
    private boolean importImpulse;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable refresh = new Runnable() {
        @Override public void run() {
            refreshStatus();
            if (selected == 4 && route != null) route.refresh(false);
            if (selected <= 3 && sound != null) sound.refreshRoutes();
            main.postDelayed(this, 1000);
        }
    };

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        YinUi.load(this);
        ui = new YinUi(this);
        getWindow().setDecorFitsSystemWindows(false);
        getWindow().setStatusBarColor(Color.TRANSPARENT);
        getWindow().setNavigationBarColor(Color.TRANSPARENT);
        if (saved != null) {
            importProfile = saved.getString("importProfile", "");
            importKey = saved.getString("importKey", "");
            importImpulse = saved.getBoolean("importImpulse");
        }
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != android.content.pm.PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 44);
        }

        LinearLayout root = ui.column();
        root.setBackgroundColor(YinUi.BG);

        LinearLayout header = buildHeader();
        root.addView(header);
        View topLine = new View(this);
        topLine.setBackgroundColor(YinUi.BORDER);
        root.addView(topLine, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        FrameLayout content = new FrameLayout(this);
        root.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));

        sound = new YinSoundPage(this);
        route = new YinRoutePage(this);
        config = new YinConfigPage(this);
        content.addView(sound.view);
        content.addView(route.view);
        content.addView(config.view);

        LinearLayout navShell = buildBottomNav();
        root.addView(navShell);

        root.setOnApplyWindowInsetsListener((v, insets) -> {
            Insets bars = insets.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            root.setPadding(bars.left, 0, bars.right, 0);
            header.setPadding(dp(14), bars.top + dp(8), dp(14), dp(8));
            navShell.setPadding(0, 0, 0, bars.bottom);
            return insets;
        });

        setContentView(root);
        select(saved == null ? 0 : saved.getInt("page", 0));
        refreshStatus();

        if (getSharedPreferences("yin_control", 0).getBoolean("enabled", false)) {
            startForegroundService(new Intent(this, YinService.class).setAction("local.v60.player.KEEP_ALIVE"));
            if (getSharedPreferences("yin_control", 0).getBoolean("persistent", true)) {
                rootWorker.execute(() -> {
                    try { KeepAlive.install(this); }
                    catch (Exception error) {
                        main.post(() -> {
                            if (!isDestroyed()) Toast.makeText(this, "Supervisor pendiente: " + error.getMessage(), Toast.LENGTH_LONG).show();
                        });
                    }
                });
            }
        }
    }

    private LinearLayout buildHeader() {
        LinearLayout header = ui.column();
        header.setBackgroundColor(YinUi.PANEL);
        header.setPadding(dp(14), dp(8), dp(14), dp(8));

        LinearLayout topRow = ui.row();
        ImageView logo = YinIcons.view(this, "yin", YinUi.ACCENT, 21);
        topRow.addView(logo, new LinearLayout.LayoutParams(dp(22), dp(22)));
        topRow.addView(new View(this), new LinearLayout.LayoutParams(dp(9), 1));

        LinearLayout titles = ui.column();
        TextView brand = ui.title("YIN CONTROL", 14.5f);
        brand.setLetterSpacing(.08f);
        titles.addView(brand);
        statusLine = ui.mono("EN REPOSO", 9.5f, YinUi.SUB, false);
        statusLine.setSingleLine(true);
        statusLine.setEllipsize(TextUtils.TruncateAt.END);
        titles.addView(statusLine);
        topRow.addView(titles, new LinearLayout.LayoutParams(0, -2, 1));

        masterPill = ui.row();
        masterPill.setPadding(dp(9), dp(2.5f), dp(4), dp(2.5f));
        masterPill.setBackground(ui.shape(YinUi.OLED, YinUi.OLED_BORDER, 18));
        masterLabel = ui.mono("OFF", 9.5f, YinUi.MUTED, true);
        masterPill.addView(masterLabel);
        masterPill.addView(new View(this), new LinearLayout.LayoutParams(dp(4), 1));
        enabled = new Switch(this);
        ui.tint(enabled);
        enabled.setChecked(getSharedPreferences("yin_control", 0).getBoolean("enabled", false));
        enabled.setContentDescription("Motor de audio Yin Control");
        enabled.setOnCheckedChangeListener((b, value) -> activate(value));
        masterPill.addView(enabled);
        topRow.addView(masterPill);
        header.addView(topRow);

        ui.gap(header, 7);

        HorizontalScrollView stripScroll = new HorizontalScrollView(this);
        stripScroll.setHorizontalScrollBarEnabled(false);
        stripScroll.setFillViewport(true);
        LinearLayout strip = ui.row();
        chipRoot = ui.ledChip("ROOT", YinUi.LED_OFF, YinUi.MUTED);
        chipDirect = ui.ledChip("STD", YinUi.LED_OFF, YinUi.MUTED);
        chipHw = ui.ledChip("PCM", YinUi.LED_OFF, YinUi.MUTED);
        chipSignal = ui.ledChip("INACTIVO", YinUi.LED_OFF, YinUi.MUTED);
        strip.addView(chipRoot.view, chipLp(0, 5));
        strip.addView(chipDirect.view, chipLp(0, 5));
        strip.addView(chipHw.view, chipLp(0, 5));
        strip.addView(new View(this), new LinearLayout.LayoutParams(0, 1, 1));
        strip.addView(chipSignal.view, chipLp(0, 0));
        stripScroll.addView(strip, new FrameLayout.LayoutParams(-1, -2));
        header.addView(stripScroll);

        ui.gap(header, 7);
        SignalMeterView masterMeter = new SignalMeterView(this, "", true, null);
        header.addView(masterMeter, new LinearLayout.LayoutParams(-1, dp(46)));
        return header;
    }

    private LinearLayout.LayoutParams chipLp(int leftDp, int rightDp) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(dp(leftDp), 0, dp(rightDp), 0);
        return lp;
    }

    private LinearLayout buildBottomNav() {
        LinearLayout shell = ui.column();
        shell.setBackgroundColor(YinUi.PANEL);
        View border = new View(this);
        border.setBackgroundColor(YinUi.BORDER);
        shell.addView(border, new LinearLayout.LayoutParams(-1, Math.max(1, dp(1))));

        LinearLayout nav = ui.row();
        for (int i = 0; i < 6; i++) {
            final int index = i;
            LinearLayout item = ui.column();
            item.setGravity(Gravity.CENTER_HORIZONTAL);
            item.setPadding(dp(1), 0, dp(1), dp(7));

            View bar = new View(this);
            bar.setBackgroundColor(Color.TRANSPARENT);
            tabBars[i] = bar;
            item.addView(bar, new LinearLayout.LayoutParams(dp(24), dp(2.5f)));

            ui.gap(item, 6);
            ImageView icon = YinIcons.view(this, TAB_ICONS[i], YinUi.MUTED, 17);
            tabIcons[i] = icon;
            item.addView(icon, new LinearLayout.LayoutParams(dp(18), dp(18)));

            ui.gap(item, 2);
            TextView label = ui.mono(TAB_NAMES[i], 8.5f, YinUi.MUTED, true);
            label.setGravity(Gravity.CENTER);
            label.setSingleLine(true);
            label.setLetterSpacing(.03f);
            tabLabels[i] = label;
            item.addView(label);

            item.setOnClickListener(v -> select(index));
            nav.addView(item, new LinearLayout.LayoutParams(0, -2, 1));
        }
        shell.addView(nav);
        return shell;
    }

    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }

    private void select(int page) {
        selected = Math.max(0, Math.min(5, page));
        boolean showSound = selected <= 3;
        sound.view.setVisibility(showSound ? View.VISIBLE : View.GONE);
        route.view.setVisibility(selected == 4 ? View.VISIBLE : View.GONE);
        config.view.setVisibility(selected == 5 ? View.VISIBLE : View.GONE);
        for (int i = 0; i < 6; i++) {
            boolean active = i == selected;
            tabBars[i].setBackgroundColor(active ? YinUi.ACCENT : Color.TRANSPARENT);
            YinIcons.tint(tabIcons[i], TAB_ICONS[i], active ? YinUi.ACCENT : YinUi.MUTED, 17);
            tabLabels[i].setTextColor(active ? YinUi.ACCENT : YinUi.MUTED);
        }
        if (showSound) sound.selectSection(selected);
        if (selected == 4) route.refresh(true);
    }

    @Override protected void onSaveInstanceState(Bundle out) {
        out.putInt("page", selected);
        out.putString("importProfile", importProfile);
        out.putString("importKey", importKey);
        out.putBoolean("importImpulse", importImpulse);
        super.onSaveInstanceState(out);
    }

    void importEffect(String profile, String key, boolean impulse) {
        importProfile = profile;
        importKey = key;
        importImpulse = impulse;
        Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(picker, 708);
    }

    @Override protected void onActivityResult(int request, int result, Intent data) {
        super.onActivityResult(request, result, data);
        if (request != 708 || result != RESULT_OK || data == null || data.getData() == null || importProfile.isEmpty()) return;
        Toast.makeText(this, "Preparando archivo…", Toast.LENGTH_SHORT).show();
        EffectImports.read(this, data.getData(), importProfile, importKey, importImpulse, message -> {
            if (isDestroyed()) return;
            Toast.makeText(this, message, Toast.LENGTH_LONG).show();
            sound.refreshSettings();
        });
    }

    private void activate(boolean value) {
        enabled.setEnabled(false);
        statusLine.setText(value ? "VERIFICANDO ROOT Y SERVICIO…" : "DESACTIVANDO MOTOR YIN…");
        masterLabel.setText("...");
        rootWorker.execute(() -> {
            String message = null;
            try {
                if (value) {
                    RootAccess.run("true", 20);
                    if (getSharedPreferences("yin_control", 0).getBoolean("persistent", true)) KeepAlive.install(this);
                } else {
                    KeepAlive.disable();
                }
                getSharedPreferences("yin_control", 0).edit().putBoolean("enabled", value).commit();
            } catch (Exception error) {
                message = error.getMessage();
            }
            final String result = message;
            main.post(() -> {
                if (isDestroyed()) return;
                enabled.setEnabled(true);
                if (result != null) {
                    enabled.setOnCheckedChangeListener(null);
                    enabled.setChecked(!value);
                    enabled.setOnCheckedChangeListener((b, v) -> activate(v));
                    Toast.makeText(this, result, Toast.LENGTH_LONG).show();
                } else if (value) {
                    startForegroundService(new Intent(this, YinService.class).setAction("local.v60.player.KEEP_ALIVE"));
                } else {
                    YinService.disable();
                    stopService(new Intent(this, YinService.class));
                }
                refreshStatus();
            });
        });
    }

    void profilesReset() {
        sound.refreshSettings();
        sound.refreshRoutes();
    }

    void reloadAppearance() { recreate(); }

    void persistent(boolean value, java.util.function.Consumer<String> done) {
        rootWorker.execute(() -> {
            String error = null;
            try {
                if (value && getSharedPreferences("yin_control", 0).getBoolean("enabled", false)) KeepAlive.install(this);
                else KeepAlive.disable();
                getSharedPreferences("yin_control", 0).edit().putBoolean("persistent", value).commit();
            } catch (Exception e) {
                error = e.getMessage();
            }
            final String result = error;
            main.post(() -> done.accept(result));
        });
    }

    void refreshStatus() {
        if (statusLine == null) return;
        Bundle state = YinService.publicStatus();
        boolean on = state.getBoolean("enabled", getSharedPreferences("yin_control", 0).getBoolean("enabled", false));
        boolean ready = state.getBoolean("ready");
        boolean ok = state.getBoolean("ok");
        boolean playing = ok && state.getBoolean("playing") && !state.getBoolean("drained");
        boolean root = state.getBoolean("root", RootAccess.granted());
        boolean bridge = state.getBoolean("bridge", AudioCapabilities.directPcm());
        Bundle source = state.getBundle("source");
        String songTitle = source == null ? "" : source.getString("sourceTitle", "");

        if (enabled.isEnabled()) {
            masterLabel.setText(on ? "ON" : "OFF");
            int onColor = ready ? YinUi.LED_GREEN : YinUi.LED_AMBER;
            masterLabel.setTextColor(on ? onColor : YinUi.MUTED);
            masterPill.setBackground(ui.shape(YinUi.OLED, on ? YinUi.alpha(onColor, .55f) : YinUi.OLED_BORDER, 18));
        }

        if (!on) {
            statusLine.setText("MOTOR EN REPOSO · SALIDA LOCAL EN YANG");
        } else if (playing) {
            statusLine.setText(!songTitle.isEmpty() ? "YANG → " + songTitle : "CONECTADO A YANG · " + state.getString("device", "AUDIO ACTIVO"));
        } else if (ok) {
            statusLine.setText("EN PAUSA · " + (songTitle.isEmpty() ? "YANG" : songTitle));
        } else if (ready) {
            statusLine.setText("LISTO · ESPERANDO PCM DE YANG");
        } else {
            statusLine.setText(state.getString("message", "Verificando servicio…"));
        }

        if (root) chipRoot.update("ROOT", YinUi.LED_GREEN, YinUi.INK, false);
        else if (RootAccess.canRequest()) chipRoot.update("ROOT", YinUi.LED_AMBER, YinUi.SUB, false);
        else chipRoot.update("NO ROOT", YinUi.LED_OFF, YinUi.MUTED, false);

        if (bridge) chipDirect.update("DIRECT", YinUi.LED_GREEN, YinUi.INK, false);
        else chipDirect.update("STD", YinUi.LED_OFF, YinUi.MUTED, false);

        int dacMode = DacGain.confirmedMode();
        BluetoothCodecs.Snapshot bt = BluetoothCodecs.latest();
        String activeRoute = state.getString("route", "");
        if (dacMode > 0) {
            String tag = dacMode == 1 ? "NORM" : dacMode == 2 ? "HI-Z" : "AUX";
            chipHw.update("DAC · " + tag, YinUi.LED_GREEN, YinUi.INK, false);
        } else if ("bluetooth".equals(activeRoute) && bt != null) {
            org.json.JSONObject cur = bt.data.optJSONObject("current");
            String cname = cur == null ? "A2DP" : cur.optString("name", "A2DP");
            chipHw.update("BT · " + cname, YinUi.LED_GREEN, YinUi.INK, false);
        } else if (AudioCapabilities.quadDac()) {
            chipHw.update("QUAD DAC", YinUi.LED_AMBER, YinUi.SUB, false);
        } else {
            chipHw.update("PCM", YinUi.LED_OFF, YinUi.MUTED, false);
        }

        if (playing) {
            String fmt = shortFreq(state.getInt("rate")) + " · " + shortBits(state.getInt("encoding")) + (state.getBoolean("direct") ? " DIR" : "");
            chipSignal.update(fmt, YinUi.LED_GREEN, YinUi.INK, true);
        } else if (ok) {
            chipSignal.update("PAUSA · " + shortFreq(state.getInt("rate")), YinUi.LED_AMBER, YinUi.SUB, false);
        } else if (ready) {
            chipSignal.update("EN ESPERA", YinUi.LED_AMBER, YinUi.SUB, false);
        } else {
            chipSignal.update("INACTIVO", YinUi.LED_OFF, YinUi.MUTED, false);
        }
    }

    static String shortFreq(int rate) {
        if (rate <= 0) return "--";
        double khz = rate / 1000.0;
        if (Math.abs(khz - Math.round(khz)) < 0.05) return Math.round(khz) + "k";
        return String.format(Locale.ROOT, "%.1fk", khz);
    }

    static String shortBits(int encoding) {
        switch (encoding) {
            case android.media.AudioFormat.ENCODING_PCM_16BIT: return "16b";
            case android.media.AudioFormat.ENCODING_PCM_8BIT: return "8b";
            case android.media.AudioFormat.ENCODING_PCM_FLOAT: return "32f";
            case android.media.AudioFormat.ENCODING_PCM_24BIT_PACKED: return "24b";
            case android.media.AudioFormat.ENCODING_PCM_32BIT: return "32b";
            default: return "PCM";
        }
    }

    @Override protected void onResume() {
        super.onResume();
        main.post(refresh);
    }

    @Override protected void onPause() {
        main.removeCallbacks(refresh);
        super.onPause();
    }
}
