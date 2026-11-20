package local.v60.player;

import android.app.AlertDialog;
import android.content.*;
import android.graphics.Typeface;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

/** Visual signal timeline from Yang source through Yin DSP to Android HAL verification. */
final class YinRoutePage {
    final ScrollView view;
    private final MainActivity a;
    private final YinUi u;
    private final LinearLayout body;
    private YinRouteRead.Result read;
    private boolean busy;
    private String last = "";
    private TextView age;
    private TextView bluetoothResult;
    private Button bluetoothButton;
    private boolean bluetoothBusy;
    private String bluetoothMessage = "";

    YinRoutePage(MainActivity a) {
        this.a = a;
        u = new YinUi(a);
        view = new ScrollView(a);
        view.setVerticalScrollBarEnabled(false);
        view.setClipToPadding(false);
        body = u.column();
        body.setPadding(u.d(14), u.d(12), u.d(14), u.d(36));
        view.addView(body, new ScrollView.LayoutParams(-1, -2));
        refresh(true);
    }

    void refresh(boolean force) {
        Bundle b = YinService.publicStatus();
        Bundle source = b.getBundle("source");
        if (age != null && read != null) {
            age.setText("Consultado hace " + Math.max(0, (SystemClock.elapsedRealtime() - read.observed) / 1000) + " s");
        }
        String signature = YinRouteRead.key(b) + ":" + b.getBoolean("playing") + ":" + b.getBoolean("drained")
            + ":" + b.getBoolean("ok") + ":" + (source == null ? "" : source.getString("sourceId", "")
            + ":" + source.getString("sourceCodec", "") + ":" + source.getInt("sourceRate") + ":" + source.getInt("sourceBits"));
        if (!force && last.equals(signature)) return;
        last = signature;
        int y = view.getScrollY();
        render(b);
        view.post(() -> view.scrollTo(0, y));
    }

    private static String pcm(int encoding) {
        switch (encoding) {
            case 2: return "16 bits";
            case 3: return "8 bits";
            case 4: return "32f float";
            case 21: return "24 bits";
            case 22: return "32 bits";
            default: return "sin datos";
        }
    }

    private static String signal(int rate, int encoding) {
        return rate > 0 ? AudioSignalInfo.frequency(rate) + " · " + pcm(encoding) : "Sin datos";
    }

    private static int number(String value) {
        try { return Integer.parseInt(value); } catch (Exception e) { return 0; }
    }

    private static int halEncoding(String format) {
        if (format.contains("PCM_16_BIT")) return 2;
        if (format.contains("PCM_24_BIT_PACKED") || format.contains("PCM_8_24_BIT")) return 21;
        if (format.contains("PCM_32_BIT")) return 22;
        if (format.contains("PCM_FLOAT")) return 4;
        if (format.contains("PCM_8_BIT")) return 3;
        return 0;
    }

    private void connector() {
        LinearLayout box = u.column();
        box.setGravity(Gravity.CENTER_HORIZONTAL);
        View line1 = new View(a);
        line1.setBackgroundColor(YinUi.alpha(YinUi.ACCENT, .5f));
        box.addView(line1, new LinearLayout.LayoutParams(u.d(2), u.d(6)));
        box.addView(new YinUi.LedDot(a, YinUi.ACCENT, 7), new LinearLayout.LayoutParams(u.d(7), u.d(7)));
        View line2 = new View(a);
        line2.setBackgroundColor(YinUi.alpha(YinUi.ACCENT, .5f));
        box.addView(line2, new LinearLayout.LayoutParams(u.d(2), u.d(6)));
        body.addView(box, new LinearLayout.LayoutParams(-1, -2));
    }

    private void query() {
        if (busy) return;
        busy = true;
        refresh(true);
        YinRouteRead.request(YinService.publicStatus(), result -> {
            if (a.isDestroyed()) return;
            read = result;
            busy = false;
            refresh(true);
        });
    }

    private void render(Bundle b) {
        body.removeAllViews();
        age = null;
        bluetoothResult = null;
        bluetoothButton = null;

        if (!b.getBoolean("ok")) {
            LinearLayout empty = u.module(body, "RUTA DE SEÑAL // EN ESPERA", "Esperando música de Yang");
            LinearLayout well = u.well(empty);
            LinearLayout row = u.row();
            row.addView(new YinUi.LedDot(a, YinUi.LED_AMBER, 8), new LinearLayout.LayoutParams(u.d(8), u.d(8)));
            row.addView(new View(a), new LinearLayout.LayoutParams(u.d(8), 1));
            row.addView(u.text(b.getString("message", "Activa Yin Control y reproduce una canción en Yang."), 12, YinUi.SUB));
            well.addView(row);
            return;
        }

        Bundle source = b.getBundle("source");
        if (source == null) source = new Bundle();

        LinearLayout yang = u.module(body, "01 // ORIGEN YANG", "Archivo decodificado");
        String title = source.getString("sourceTitle", "");
        if (!title.isEmpty()) {
            yang.addView(u.text(title, 13, YinUi.SUB));
            u.gap(yang, 4);
        }
        String codec = source.getString("sourceCodec", "");
        if (codec.isEmpty()) codec = "PCM";
        int originalRate = source.getInt("sourceRate"), bits = source.getInt("sourceBits");
        String origLine = codec + " · " + (originalRate > 0 ? AudioSignalInfo.frequency(originalRate) : "frec. N/D")
            + " · " + (bits > 0 ? bits + " bits" : "prof. N/D");
        yang.addView(u.mono(origLine, 15, YinUi.INK, true));
        u.gap(yang, 6);
        LinearLayout bypassRow = u.row();
        bypassRow.addView(u.ledChip("DSP YANG: BYPASS", YinUi.LED_GREEN, YinUi.SUB).view);
        yang.addView(bypassRow);
        connector();

        LinearLayout yin = u.module(body, "02 // ENTRADA YIN", "PCM recibido por socket");
        yin.addView(u.mono("PCM · " + signal(b.getInt("inputRate"), b.getInt("inputEncoding")), 15, YinUi.INK, true));
        if (originalRate > 0 && originalRate != b.getInt("inputRate")) {
            u.gap(yin, 4);
            u.detail(yin, "Resample en origen", AudioSignalInfo.frequency(originalRate) + " → " + AudioSignalInfo.frequency(b.getInt("inputRate")));
        }
        if (bits > 0 && b.getInt("inputEncoding") == 4) {
            u.gap(yin, 4);
            yin.addView(u.text("Contenedor float de 32 bits desde archivo de " + bits + " bits.", 10.5f, YinUi.MUTED));
        }
        connector();

        boolean isDirect = b.getBoolean("direct");
        LinearLayout output = u.module(body, "03 // MOTOR YIN", isDirect ? "Salida directa (DIRECT)" : "AudioTrack estándar");
        output.addView(u.mono("PCM · " + signal(b.getInt("rate"), b.getInt("encoding")), 16, YinUi.ACCENT, true));
        u.gap(output, 8);

        HorizontalScrollView chipsScroll = new HorizontalScrollView(a);
        chipsScroll.setHorizontalScrollBarEnabled(false);
        LinearLayout chips = u.row();
        addChip(chips, isDirect ? "DIRECT" : "AUDIOTRACK", isDirect ? YinUi.LED_GREEN : YinUi.LED_OFF);
        addChip(chips, b.getBoolean("eq") ? "EQ ON" : "EQ OFF", b.getBoolean("eq") ? YinUi.ACCENT : YinUi.LED_OFF);
        addChip(chips, b.getBoolean("limiter") ? "LIM ON" : "LIM OFF", b.getBoolean("limiter") ? YinUi.LED_GREEN : YinUi.LED_OFF);
        addChip(chips, b.getInt("inputRate") == b.getInt("rate") ? "1:1 HZ" : "RESAMPLE",
            b.getInt("inputRate") == b.getInt("rate") ? YinUi.LED_GREEN : YinUi.LED_AMBER);
        chipsScroll.addView(chips);
        output.addView(chipsScroll);
        u.gap(output, 6);

        u.telemetryPair(output, "YIN SOLICITÓ", signal(b.getInt("requestedRate"), b.getInt("requestedEncoding")),
            "AUDIOTRACK ACEPTÓ", signal(b.getInt("rate"), b.getInt("encoding")));
        u.telemetryPair(output, "PREAMPLIFICADOR",
            b.getDouble("preampDb") == 0 ? "0.00 dB" : String.format(Locale.ROOT, "%+.2f dB", b.getDouble("preampDb")),
            "GANANCIA FINAL", String.format(Locale.ROOT, "%+.1f dB", b.getDouble("gainDb")));
        String flagsStr = b.getInt("flags", -1) < 0 ? "N/D" : "0x" + Integer.toHexString(b.getInt("flags")) + (isDirect ? " · DIRECT" : "");
        String bufStr = b.getInt("rate") > 0 ? Math.round(b.getInt("bufferFrames") * 1000.0 / b.getInt("rate")) + " ms" : "N/D";
        u.telemetryPair(output, "FLAGS", flagsStr, "BÚFER", bufStr);
        u.telemetryPair(output, "PERFIL", b.getString("deviceProfile", "Por conexión"),
            "EFECTOS", b.getString("effects", "Sin efectos"));
        connector();

        boolean valid = read != null && YinRouteRead.live(b) && read.key.equals(YinRouteRead.key(b));
        LinearLayout hal = u.module(body, "04 // VERIFICACIÓN HAL", "Android / AudioFlinger");
        if (read == null) {
            u.telemetryPair(hal, "YIN ENVÍA", signal(b.getInt("rate"), b.getInt("encoding")),
                "ANDROID INFORMA", "Sin consultar");
            u.gap(hal, 6);
            hal.addView(u.text("Pulsa Consultar HAL para leer qué frecuencia y formato reporta AudioFlinger.", 11.5f, YinUi.SUB));
        } else if (!valid) {
            hal.addView(u.text("Cambió la canción o la salida. Vuelve a consultar la ruta real.", 11.5f, YinUi.LED_AMBER));
        } else if (!read.error.isEmpty()) {
            hal.addView(u.text(read.error, 11.5f, YinUi.LED_AMBER));
        } else {
            int actualRate = number(read.halRate);
            boolean explicit = actualRate > 0;
            if (!explicit) actualRate = number(read.threadRate);
            int actualEncoding = halEncoding(read.format);

            u.telemetryPair(hal, "YIN ENVÍA", signal(b.getInt("rate"), b.getInt("encoding")),
                "ANDROID INFORMA", signal(actualRate, actualEncoding));
            u.gap(hal, 6);

            boolean exactMatch = actualRate == b.getInt("rate") && actualEncoding > 0 && actualEncoding == b.getInt("encoding");
            boolean incomplete = actualRate <= 0 || actualEncoding <= 0;
            YinUi.LedChip verifyChip = u.ledChip(
                incomplete ? "LECTURA INCOMPLETA" : exactMatch ? "FORMATO CONSERVADO EN HAL" : "MODIFICADO POR ANDROID",
                incomplete ? YinUi.LED_OFF : exactMatch ? YinUi.LED_GREEN : YinUi.LED_AMBER,
                incomplete ? YinUi.SUB : YinUi.INK
            );
            LinearLayout badgeRow = u.row();
            badgeRow.addView(verifyChip.view);
            hal.addView(badgeRow);
            u.gap(hal, 6);

            if (actualRate > 0 && actualRate != b.getInt("rate")) {
                hal.addView(u.mono("Cambio de frecuencia: " + AudioSignalInfo.frequency(b.getInt("rate")) + " → " + AudioSignalInfo.frequency(actualRate), 11, YinUi.LED_AMBER, false));
            }
            if (actualEncoding > 0 && actualEncoding != b.getInt("encoding")) {
                hal.addView(u.mono("Cambio de formato: " + pcm(b.getInt("encoding")) + " → " + pcm(actualEncoding), 11, YinUi.LED_AMBER, false));
            }
            u.detail(hal, "Destino activo", b.getString("device", "No informado"));
            hal.addView(u.text(explicit ? "Frecuencia y formato reportados por la HAL." : "Frecuencia del hilo AudioFlinger + formato HAL.", 10.5f, YinUi.MUTED));
        }

        u.gap(hal, 8);
        LinearLayout halBtns = u.row();
        Button consult = u.actionKey(busy ? "Consultando…" : "Consultar HAL", "refresh", read == null || !valid, this::query);
        consult.setEnabled(!busy);
        halBtns.addView(consult, new LinearLayout.LayoutParams(0, -2, 1));
        if (read != null && valid) {
            View sp = new View(a);
            halBtns.addView(sp, new LinearLayout.LayoutParams(u.d(6), 1));
            Button rawBtn = u.key("Ver dump HAL / ALSA", false, () -> showRawDialog(b));
            halBtns.addView(rawBtn, new LinearLayout.LayoutParams(0, -2, 1));
        }
        hal.addView(halBtns);
        if (read != null) {
            u.gap(hal, 6);
            age = u.mono("Consultado hace " + Math.max(0, (SystemClock.elapsedRealtime() - read.observed) / 1000) + " s", 10, YinUi.MUTED, false);
            hal.addView(age);
        }

        if ("bluetooth".equals(b.getString("route"))) {
            LinearLayout bt = u.module(body, "05 // TRANSPORTE INALÁMBRICO", "Bluetooth A2DP");
            LinearLayout btWell = u.well(bt);
            bluetoothResult = u.mono(bluetoothText(), 11, YinUi.INK, false);
            bluetoothResult.setTextIsSelectable(true);
            btWell.addView(bluetoothResult);
            bluetoothButton = u.actionKey(bluetoothBusy ? "Consultando…" : "Consultar códec Bluetooth", "refresh", false, this::queryBluetooth);
            bluetoothButton.setEnabled(!bluetoothBusy);
            bt.addView(bluetoothButton, new LinearLayout.LayoutParams(-1, -2));
        }

        u.gap(body, 4);
        body.addView(u.actionKey("Copiar resumen técnico", "copy", false, () -> {
            StringBuilder current = new StringBuilder("Yin Control · Ruta de audio\n");
            appendText(body, current);
            a.getSystemService(ClipboardManager.class).setPrimaryClip(ClipData.newPlainText("Ruta de audio Yin", current.toString()));
            Toast.makeText(a, "Resumen copiado", Toast.LENGTH_SHORT).show();
        }), new LinearLayout.LayoutParams(-1, -2));
    }

    private void addChip(LinearLayout parent, String text, int ledColor) {
        YinUi.LedChip chip = u.ledChip(text, ledColor, ledColor == YinUi.LED_OFF ? YinUi.MUTED : YinUi.INK);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, -2);
        lp.setMargins(0, 0, u.d(5), 0);
        parent.addView(chip.view, lp);
    }

    private void showRawDialog(Bundle b) {
        String detail = "Sesión Yin: " + b.getInt("sessionId")
            + "\nDispositivo ID: " + b.getInt("deviceId")
            + "\nUnderruns: " + b.getInt("underruns")
            + "\n\n" + read.error + "\n" + read.raw
            + "\n\nALSA · flujos activos\n" + read.alsa
            + "\n\nNota: un contenedor de 32 bits no demuestra 32 bits físicos en DAC.";
        TextView text = new TextView(a);
        text.setText(detail);
        text.setTypeface(Typeface.MONOSPACE);
        text.setTextSize(10.5f);
        text.setTextColor(YinUi.INK);
        text.setTextIsSelectable(true);
        text.setPadding(u.d(18), u.d(12), u.d(18), u.d(12));
        ScrollView scroll = new ScrollView(a);
        scroll.setBackgroundColor(YinUi.WELL);
        scroll.addView(text);
        new AlertDialog.Builder(a, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Dump técnico HAL / ALSA")
            .setView(scroll)
            .setPositiveButton("Cerrar", null)
            .show();
    }

    private String bluetoothText() {
        BluetoothCodecs.Snapshot value = BluetoothCodecs.latest();
        if (value == null) return bluetoothMessage.isEmpty() ? "Consulta el códec negociado con tus auriculares." : bluetoothMessage;
        return value.device() + "\n" + value.description() + "\nLectura hace " + Math.max(0, (SystemClock.elapsedRealtime() - value.observed) / 1000) + " s";
    }

    private void queryBluetooth() {
        if (bluetoothBusy) return;
        bluetoothBusy = true;
        if (bluetoothButton != null) { bluetoothButton.setEnabled(false); bluetoothButton.setText("Consultando…"); }
        BluetoothCodecs.request(a, null, null, message -> {
            bluetoothBusy = false;
            bluetoothMessage = message;
            if (a.isDestroyed()) return;
            if (bluetoothButton != null) { bluetoothButton.setEnabled(true); bluetoothButton.setText("Consultar códec Bluetooth"); }
            if (bluetoothResult != null) {
                bluetoothResult.setText(bluetoothText());
                bluetoothResult.requestLayout();
                bluetoothResult.post(() -> {
                    if (bluetoothResult != null && bluetoothResult.isAttachedToWindow()) {
                        android.graphics.Rect area = new android.graphics.Rect(0, 0, bluetoothResult.getWidth(), bluetoothResult.getHeight());
                        bluetoothResult.requestRectangleOnScreen(area, false);
                    }
                });
            }
        });
    }

    private static void appendText(View v, StringBuilder out) {
        if (v instanceof TextView && !(v instanceof Button)) out.append(((TextView) v).getText()).append('\n');
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) appendText(g.getChildAt(i), out);
        }
    }
}
