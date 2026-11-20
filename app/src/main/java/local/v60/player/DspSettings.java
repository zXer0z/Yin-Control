package local.v60.player;

import java.util.*;
import org.json.*;

/** Validated per-profile DSP parameters and calibrated FFmpeg filter stages. */
final class DspSettings {
    private static final int SCHEMA_VERSION = 2;

    boolean parametric, convolver, bass, clarity, compressor, analogWarmth;
    boolean crossfeed, surround, stereoWide, channelsEnabled;

    final int[] hz = AudioSettings.BANDS.clone(), type = new int[10];
    final double[] q = {1, 1, 1, 1, 1, 1, 1, 1, 1, 1};
    final double[] gain = new double[10];
    boolean gainStored, parametricEdited;
    double preampFraction;

    final int[] channel = {2, 2, 2, 2, 2, 2, 2, 2, 2, 2};
    double leftGain, rightGain;
    int leftDelay, rightDelay;

    int bassMode = 1, bassGain = 5, bassHz = 75;
    int clarityMode = 0, clarityAmount = 4;
    int threshold = -18, ratio = 3, attack = 10, release = 180;
    int ceiling = -1;
    int warmthType = 0, warmthAmount = 4;

    int crossfeedHz = 700, crossfeedAmount = 60;
    int delay = 10, surroundAmount = 75, surroundLpHz = 7500;
    boolean surroundReverse;
    int stereoWidth = 135;

    int irMix = 100, irGain = -6, irChannels = 1;
    String irFile = "", irName = "";

    DspSettings copy() { return read(json()); }

    static DspSettings read(JSONObject j) {
        DspSettings d = new DspSettings();
        if (j == null) return d;
        int version = j.optInt("dspVersion", 1);

        d.parametric = j.optBoolean("parametric");
        d.convolver = j.optBoolean("convolver");
        d.bass = j.optBoolean("bass");
        d.clarity = j.optBoolean("clarity");
        d.compressor = j.optBoolean("compressor");
        d.analogWarmth = j.optBoolean("analogWarmth");
        d.crossfeed = j.optBoolean("crossfeed");
        d.surround = j.optBoolean("surround");
        d.stereoWide = j.optBoolean("stereoWide");
        d.parametricEdited = j.optBoolean("parametricEdited", d.parametric);

        d.channelsEnabled = j.optBoolean("channelsEnabled");
        d.leftGain = j.optDouble("leftGain");
        d.rightGain = j.optDouble("rightGain");
        d.leftDelay = j.optInt("leftDelay");
        d.rightDelay = j.optInt("rightDelay");

        JSONArray ch = j.optJSONArray("channel");
        for (int i = 0; i < 10; i++) {
            if (ch != null) d.channel[i] = Math.max(0, Math.min(2, ch.optInt(i, 2)));
        }

        d.bassMode = j.optInt("bassMode", 1);
        d.bassGain = j.optInt("bassGain", 5);
        d.bassHz = j.optInt("bassHz", 75);
        d.clarityMode = j.optInt("clarityMode", 0);
        d.clarityAmount = j.optInt("clarityAmount", 4);
        d.threshold = j.optInt("threshold", -18);
        d.ratio = j.optInt("ratio", 3);
        d.attack = j.optInt("attack", 10);
        d.release = j.optInt("release", 180);
        d.ceiling = j.optInt("ceiling", -1);
        d.warmthType = j.optInt("warmthType", 0);
        d.warmthAmount = j.optInt("warmthAmount", 4);

        d.crossfeedHz = j.optInt("crossfeedHz", 700);
        d.crossfeedAmount = j.optInt("crossfeedAmount", 60);
        d.delay = j.optInt("delay", 10);
        d.surroundAmount = j.optInt("surroundAmount", 75);
        d.surroundLpHz = j.optInt("surroundLpHz", 7500);
        d.surroundReverse = j.optBoolean("surroundReverse", false);
        d.stereoWidth = j.optInt("stereoWidth", 135);

        if (version < SCHEMA_VERSION) {
            if (d.crossfeedAmount == 25) d.crossfeedAmount = 60;
            if (d.surroundAmount == 25) d.surroundAmount = 75;
            if (d.delay == 8) d.delay = 10;
            if (d.bassGain == 3) d.bassGain = 5;
            if (d.ceiling == -6) d.ceiling = -1;
        }

        d.irFile = j.optString("irFile", "");
        d.irName = j.optString("irName", "");
        d.irMix = j.optInt("irMix", 100);
        d.irGain = j.optInt("irGain", -6);
        d.irChannels = j.optInt("irChannels", 1);

        JSONArray f = j.optJSONArray("hz"), width = j.optJSONArray("q"), types = j.optJSONArray("type"), g = j.optJSONArray("gain");
        d.gainStored = g != null;
        d.preampFraction = j.optDouble("preampFraction", 0);
        for (int i = 0; i < 10; i++) {
            if (f != null) d.hz[i] = f.optInt(i, AudioSettings.BANDS[i]);
            if (width != null) d.q[i] = width.optDouble(i, 1);
            if (types != null) d.type[i] = types.optInt(i);
            if (g != null) d.gain[i] = g.optDouble(i, 0);
        }
        d.validate();
        d.validatePrecision();
        return d;
    }

    JSONObject json() {
        validate();
        validatePrecision();
        JSONObject j = new JSONObject();
        try {
            j.put("dspVersion", SCHEMA_VERSION);
            j.put("parametricEdited", parametricEdited);
            j.put("channelsEnabled", channelsEnabled).put("leftGain", leftGain).put("rightGain", rightGain)
                .put("leftDelay", leftDelay).put("rightDelay", rightDelay);
            JSONArray channelArr = new JSONArray();
            for (int c : this.channel) channelArr.put(c);
            j.put("channel", channelArr);

            j.put("parametric", parametric).put("convolver", convolver).put("bass", bass)
                .put("clarity", clarity).put("compressor", compressor).put("analogWarmth", analogWarmth)
                .put("crossfeed", crossfeed).put("surround", surround).put("stereoWide", stereoWide);

            j.put("bassMode", bassMode).put("bassGain", bassGain).put("bassHz", bassHz)
                .put("clarityMode", clarityMode).put("clarityAmount", clarityAmount)
                .put("threshold", threshold).put("ratio", ratio).put("attack", attack).put("release", release)
                .put("ceiling", ceiling).put("warmthType", warmthType).put("warmthAmount", warmthAmount)
                .put("crossfeedHz", crossfeedHz).put("crossfeedAmount", crossfeedAmount)
                .put("delay", delay).put("surroundAmount", surroundAmount)
                .put("surroundLpHz", surroundLpHz).put("surroundReverse", surroundReverse)
                .put("stereoWidth", stereoWidth)
                .put("irFile", irFile).put("irName", irName).put("irMix", irMix).put("irGain", irGain).put("irChannels", irChannels);

            JSONArray f = new JSONArray(), w = new JSONArray(), t = new JSONArray(), g = new JSONArray();
            for (int i = 0; i < 10; i++) {
                f.put(hz[i]); w.put(q[i]); t.put(type[i]); g.put(gain[i]);
            }
            j.put("hz", f).put("q", w).put("type", t).put("gain", g).put("preampFraction", preampFraction);
        } catch (JSONException error) {
            throw new IllegalStateException(error);
        }
        return j;
    }

    void validate() {
        for (int i = 0; i < 10; i++) {
            hz[i] = clamp(hz[i], 20, 20000);
            q[i] = Double.isFinite(q[i]) ? Math.max(.1, Math.min(12, q[i])) : 1;
            type[i] = clamp(type[i], 0, 2);
        }
        bassMode = clamp(bassMode, 0, 2);
        bassGain = clamp(bassGain, 1, 12);
        bassHz = clamp(bassHz, 40, 160);
        clarityMode = clamp(clarityMode, 0, 2);
        clarityAmount = clamp(clarityAmount, 1, 10);
        threshold = clamp(threshold, -40, -6);
        ratio = clamp(ratio, 1, 10);
        attack = clamp(attack, 1, 100);
        release = clamp(release, 40, 1000);
        ceiling = clamp(ceiling, -12, -1);
        warmthType = clamp(warmthType, 0, 2);
        warmthAmount = clamp(warmthAmount, 1, 10);
        crossfeedHz = clamp(crossfeedHz, 300, 1200);
        crossfeedAmount = clamp(crossfeedAmount, 20, 95);
        delay = clamp(delay, 1, 25);
        surroundAmount = clamp(surroundAmount, 10, 100);
        surroundLpHz = clamp(surroundLpHz, 1500, 16000);
        stereoWidth = clamp(stereoWidth, 110, 200);
        irMix = clamp(irMix, 0, 100);
        irGain = clamp(irGain, -24, 0);
        irChannels = clamp(irChannels, 1, 2);
    }

    void seed(int[] graphic) {
        if (!gainStored) {
            for (int i = 0; i < 10; i++) gain[i] = graphic[i];
            gainStored = true;
        }
    }

    void validatePrecision() {
        preampFraction = Double.isFinite(preampFraction) ? Math.max(-.999, Math.min(.999, preampFraction)) : 0;
        leftGain = Double.isFinite(leftGain) ? Math.max(-24, Math.min(6, leftGain)) : 0;
        rightGain = Double.isFinite(rightGain) ? Math.max(-24, Math.min(6, rightGain)) : 0;
        leftDelay = clamp(leftDelay, 0, 200);
        rightDelay = clamp(rightDelay, 0, 200);
        for (int i = 0; i < 10; i++) {
            gain[i] = Double.isFinite(gain[i]) ? Math.max(-12, Math.min(12, gain[i])) : 0;
        }
    }

    double gain(int band, int graphic) { return parametric ? gain[band] : graphic; }
    int frequency(int band) { return parametric ? hz[band] : AudioSettings.BANDS[band]; }

    String eqFilter(int band, double gain) {
        int f = frequency(band);
        if (!parametric || type[band] == 0) {
            return "equalizer=f=" + f + ":width_type=q:width=" + decimal(parametric ? q[band] : 1.41421356) + ":g=" + decimal(gain) + ":precision=f64";
        }
        return (type[band] == 1 ? "bass" : "treble") + "=f=" + f + ":width_type=q:width=" + decimal(q[band]) + ":g=" + decimal(gain) + ":precision=f64";
    }

    String eqFilter(int band, double gain, int channels) {
        return eqFilter(band, gain) + (channels == 2 && parametric && channel[band] != 2 ? ":channels=" + (channel[band] == 0 ? "FL" : "FR") : "");
    }

    boolean applies(int band, int channels) { return !parametric || channels == 2 || channel[band] != 1; }

    /** Appends all active DSP stages in fixed acoustic order: dynamics, warmth, spatial, channel trims. */
    void appendStages(List<String> filters, List<String> activeNames, int rate, int channels, boolean headphones) {
        if (bass && bassHz < rate * 0.45) {
            if (bassMode == 0) {
                filters.add("bass=f=" + bassHz + ":width_type=q:width=0.707:g=" + bassGain + ":precision=f64");
                activeNames.add("Graves");
            } else if (bassMode == 1) {
                filters.add("asubboost=dry=0.85:wet=" + decimal(0.25 + bassGain * 0.05) + ":boost=" + decimal(1.5 + bassGain * 0.45)
                    + ":cutoff=" + Math.max(50, bassHz) + ":slope=0.5:feedback=0.4:delay=12");
                filters.add("bass=f=" + bassHz + ":width_type=q:width=0.75:g=" + decimal(bassGain * 0.4) + ":precision=f64");
                activeNames.add("Sub-Woofer");
            } else {
                filters.add("adynamicequalizer=dfrequency=" + bassHz + ":tfrequency=" + bassHz
                    + ":mode=boostbelow:auto=adaptive:tftype=lowshelf:ratio=2.5:range=" + bassGain + ":attack=15:release=180");
                filters.add("bass=f=" + bassHz + ":width_type=q:width=0.75:g=" + decimal(bassGain * 0.5) + ":precision=f64");
                activeNames.add("Graves dinámicos");
            }
        }

        if (clarity) {
            int before = filters.size();
            if (clarityMode == 0) {
                filters.add("crystalizer=i=" + decimal(clarityAmount * 0.45) + ":c=1");
            } else if (clarityMode == 1) {
                if (rate > 14000) {
                    filters.add("treble=f=6500:width_type=q:width=0.75:g=" + decimal(clarityAmount * 0.65) + ":precision=f64");
                }
            } else {
                filters.add("crystalizer=i=" + decimal(clarityAmount * 0.30) + ":c=1");
                if (rate > 16000) {
                    filters.add("treble=f=7500:width_type=q:width=0.75:g=" + decimal(clarityAmount * 0.45) + ":precision=f64");
                }
            }
            if (filters.size() > before) activeNames.add("Claridad");
        }

        if (compressor) {
            filters.add("acompressor=threshold=" + decimal(Math.pow(10, threshold / 20.0))
                + ":ratio=" + ratio + ":attack=" + attack + ":release=" + release
                + ":makeup=1:knee=2.828:link=maximum:detection=rms");
            activeNames.add("Compresor");
        }

        if (analogWarmth) {
            String curve = warmthType == 1 ? "atan" : warmthType == 2 ? "cubic" : "tanh";
            double thresh = Math.max(0.25, 0.92 - warmthAmount * 0.055);
            double param = warmthType == 0 ? 0.8 + warmthAmount * 0.14 : 1.0;
            filters.add("asoftclip=type=" + curve + ":threshold=" + decimal(thresh) + ":output=0.98:param=" + decimal(param) + ":oversample=2");
            activeNames.add("Calidez analógica");
        }

        if (headphones && channels == 2) {
            if (crossfeed) {
                double range = Math.max(0.10, Math.min(0.92, (1.0 - crossfeedHz / 2100.0) / 0.9));
                double strength = Math.max(0.15, Math.min(1.0, crossfeedAmount / 100.0));
                filters.add("crossfeed=strength=" + decimal(strength) + ":range=" + decimal(range) + ":slope=0.5:level_in=0.96:level_out=1");
                double cross = 0.04 + 0.18 * Math.pow(strength, 1.35);
                double dry = 1.0 - cross * 0.65;
                filters.add("pan=stereo|c0=" + decimal(dry) + "*c0+" + decimal(cross) + "*c1|c1=" + decimal(dry) + "*c1+" + decimal(cross) + "*c0");
                activeNames.add("Protección binaural");
            }
            if (surround) {
                double mix = surroundAmount / 100.0;
                double effDelay = Math.max(0.5, delay * (0.35 + 0.65 * mix));
                String delayedCh = surroundReverse ? "FL" : "FR";
                filters.add("adelay=delays=" + (surroundReverse ? decimal(effDelay) + "|0" : "0|" + decimal(effDelay)));
                if (surroundLpHz < rate * 0.45) {
                    double lpCut = 2.0 + 7.0 * mix;
                    filters.add("treble=f=" + surroundLpHz + ":width_type=q:width=0.707:g=-" + decimal(lpCut) + ":precision=f64:channels=" + delayedCh);
                }
                double dL = surroundReverse ? Math.min(35.0, delay + 1.5) : 1.6;
                double dR = surroundReverse ? 1.6 : Math.min(35.0, delay + 1.5);
                double sideGain = 1.08 + 0.72 * mix;
                filters.add("haas=middle_source=mid:left_delay=" + decimal(dL) + ":right_delay=" + decimal(dR)
                    + ":side_gain=" + decimal(sideGain) + ":level_in=0.92:level_out=1");
                activeNames.add("Differential Surround");
            }
            if (stereoWide) {
                filters.add("extrastereo=m=" + decimal(stereoWidth / 100.0) + ":c=1");
                activeNames.add("Escena estéreo");
            }
        }

        if (channelsEnabled && channels == 2) {
            int before = filters.size();
            if (leftGain != 0 || rightGain != 0) {
                filters.add("pan=stereo|c0=" + decimal(Math.pow(10, leftGain / 20)) + "*c0|c1=" + decimal(Math.pow(10, rightGain / 20)) + "*c1");
            }
            if (leftDelay != 0 || rightDelay != 0) {
                filters.add("adelay=delays=" + decimal(leftDelay / 10.0) + "|" + decimal(rightDelay / 10.0));
            }
            if (filters.size() > before) activeNames.add("Calibración L/R");
        }
    }

    boolean active(boolean headphones, int channels) {
        return bass || clarity || compressor || analogWarmth
            || (headphones && channels == 2 && (crossfeed || surround || stereoWide));
    }

    String summary(boolean headphones, int channels) {
        ArrayList<String> s = new ArrayList<>();
        if (convolver && !irFile.isEmpty()) s.add("Convolver IR");
        if (bass) s.add(bassMode == 1 ? "Sub-Woofer" : bassMode == 2 ? "Graves dinámicos" : "Graves");
        if (clarity) s.add("Claridad");
        if (compressor) s.add("Compresor");
        if (analogWarmth) s.add("Calidez analógica");
        if (headphones && channels == 2 && crossfeed) s.add("Protección binaural");
        if (headphones && channels == 2 && surround) s.add("Differential Surround");
        if (headphones && channels == 2 && stereoWide) s.add("Escena estéreo");
        if (channelsEnabled && channels == 2 && (leftGain != 0 || rightGain != 0 || leftDelay != 0 || rightDelay != 0)) {
            s.add("Calibración L/R");
        }
        return s.isEmpty() ? "Sin efectos adicionales" : String.join(" · ", s);
    }

    static String decimal(double n) { return String.format(Locale.ROOT, "%.6f", n); }
    private static int clamp(int v, int min, int max) { return Math.max(min, Math.min(max, v)); }
}
