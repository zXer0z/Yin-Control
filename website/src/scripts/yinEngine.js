// Yin Control Web Audio + RBJ Analytical EQ + Spatial Haas/Cure Simulator

export const BANDS = [31, 63, 125, 250, 500, 1000, 2000, 4000, 8000, 16000];
export const BAND_LABELS = ['31', '63', '125', '250', '500', '1k', '2k', '4k', '8k', '16k'];

export const ACCENT_THEMES = {
  menta:   { name: 'Menta',   hex: '#6ce5c6', rgb: '108, 229, 198' },
  cian:    { name: 'Cian',    hex: '#67d4f5', rgb: '103, 212, 245' },
  ambar:   { name: 'Ámbar',   hex: '#ff8c1a', rgb: '255, 140, 26'  },
  lavanda: { name: 'Lavanda', hex: '#b7a1ef', rgb: '183, 161, 239' },
  coral:   { name: 'Coral',   hex: '#ff6f7d', rgb: '255, 111, 125' },
  titanio: { name: 'Titanio', hex: '#c8d4de', rgb: '200, 212, 222' },
};

export const SOUND_COLOR_PRESETS = [
  {
    id: 'pure',
    name: 'PURE HI-FI',
    meta: 'BYPASS LIMPIO',
    color: '#3fe088',
    desc: 'Bypass limpio · señal pura sin coloración DSP',
    preamp: 0,
    gains: [0, 0, 0, 0, 0, 0, 0, 0, 0, 0],
    bass: false, bassMode: 1, bassGain: 5, bassHz: 75,
    clarity: false, warmth: false, warmthType: 0, warmthAmount: 4,
    crossfeed: false, crossfeedHz: 700, crossfeedAmount: 60,
    surround: false, delay: 10, surroundAmount: 75, surroundLpHz: 7500, surroundReverse: false,
  },
  {
    id: 'bass',
    name: 'BASS CLUB',
    meta: 'SUB + VALVULAR',
    color: '#ff8c1a',
    desc: 'Sub-Woofer punch + graves profundos + calidez valvular',
    preamp: -2,
    gains: [5, 4, 2, 0, -1, 0, 1, 2, 3, 2],
    bass: true, bassMode: 1, bassGain: 7, bassHz: 70,
    clarity: true, warmth: true, warmthType: 0, warmthAmount: 4,
    crossfeed: false, crossfeedHz: 700, crossfeedAmount: 60,
    surround: false, delay: 10, surroundAmount: 75, surroundLpHz: 7500, surroundReverse: false,
  },
  {
    id: 'vocal',
    name: 'VOCAL STUDIO',
    meta: 'PRESENCIA + CURE',
    color: '#18a0fb',
    desc: 'Voces al frente + claridad híbrida + imagen frontal',
    preamp: -1,
    gains: [-1, -1, 0, 1, 3, 4, 3, 2, 1, 1],
    bass: false, bassMode: 1, bassGain: 5, bassHz: 75,
    clarity: true, warmth: false, warmthType: 0, warmthAmount: 4,
    crossfeed: true, crossfeedHz: 700, crossfeedAmount: 60,
    surround: false, delay: 10, surroundAmount: 75, surroundLpHz: 7500, surroundReverse: false,
  },
  {
    id: 'stage',
    name: '3D STAGE',
    meta: 'HAAS 12ms + WIDE',
    color: '#67d4f5',
    desc: 'Differential Surround Haas + escena estéreo abierta',
    preamp: -2,
    gains: [3, 2, 1, 0, 0, 1, 2, 3, 3, 3],
    bass: true, bassMode: 0, bassGain: 4, bassHz: 75,
    clarity: true, warmth: false, warmthType: 0, warmthAmount: 4,
    crossfeed: false, crossfeedHz: 700, crossfeedAmount: 60,
    surround: true, delay: 12, surroundAmount: 80, surroundLpHz: 7500, surroundReverse: false,
  },
  {
    id: 'tube',
    name: 'TUBE WARMTH',
    meta: 'TANH + CURE LEVE',
    color: '#f2b857',
    desc: 'Saturación de válvula Tanh + cuerpo analógico cálido',
    preamp: -1,
    gains: [3, 3, 2, 1, 0, 0, -1, 0, 1, 2],
    bass: true, bassMode: 0, bassGain: 4, bassHz: 85,
    clarity: false, warmth: true, warmthType: 0, warmthAmount: 7,
    crossfeed: true, crossfeedHz: 700, crossfeedAmount: 45,
    surround: false, delay: 10, surroundAmount: 75, surroundLpHz: 7500, surroundReverse: false,
  },
  {
    id: 'night',
    name: 'NIGHT LOUNGE',
    meta: 'COMP 6:1 + FUERTE',
    color: '#b7a1ef',
    desc: 'Compresión nocturna + protección binaural fuerte',
    preamp: -2,
    gains: [2, 2, 1, 0, 0, 0, -1, -1, 0, 1],
    bass: true, bassMode: 2, bassGain: 4, bassHz: 75,
    clarity: false, warmth: true, warmthType: 1, warmthAmount: 3,
    crossfeed: true, crossfeedHz: 650, crossfeedAmount: 95,
    surround: false, delay: 10, surroundAmount: 75, surroundLpHz: 7500, surroundReverse: false,
  },
];

// Global reactive console state
export const consoleState = {
  presetId: 'stage',
  preamp: -2,
  eqEnabled: true,
  gains: [...SOUND_COLOR_PRESETS[3].gains],
  selectedBand: 5,
  crossfeed: false,
  crossfeedHz: 700,
  crossfeedAmount: 60,
  surround: true,
  delay: 12,
  surroundAmount: 80,
  surroundLpHz: 7500,
  surroundReverse: false,
  bass: true,
  bassMode: 1,
  warmth: true,
  warmthType: 0,
  warmthAmount: 5,
  playing: false,
  inDb: -8.6,
  outDb: -9.4,
};

const listeners = new Set();
export function subscribeState(fn) {
  listeners.add(fn);
  return () => listeners.delete(fn);
}
export function emitState(reason = 'update') {
  syncAudioGraph();
  for (const fn of listeners) fn(consoleState, reason);
}

export function setTheme(key) {
  const theme = ACCENT_THEMES[key] || ACCENT_THEMES.menta;
  document.documentElement.style.setProperty('--accent', theme.hex);
  document.documentElement.style.setProperty('--accent-rgb', theme.rgb);
  document.documentElement.style.setProperty('--accent-soft', `rgba(${theme.rgb}, 0.14)`);
  emitState('theme');
}

export function applySoundColor(id) {
  const p = SOUND_COLOR_PRESETS.find((x) => x.id === id);
  if (!p) return;
  consoleState.presetId = p.id;
  consoleState.preamp = p.preamp;
  consoleState.eqEnabled = p.id !== 'pure';
  consoleState.gains = [...p.gains];
  consoleState.crossfeed = p.crossfeed;
  consoleState.crossfeedHz = p.crossfeedHz;
  consoleState.crossfeedAmount = p.crossfeedAmount;
  consoleState.surround = p.surround;
  consoleState.delay = p.delay;
  consoleState.surroundAmount = p.surroundAmount;
  consoleState.surroundLpHz = p.surroundLpHz;
  consoleState.surroundReverse = p.surroundReverse;
  consoleState.bass = p.bass;
  consoleState.bassMode = p.bassMode;
  consoleState.warmth = p.warmth;
  consoleState.warmthType = p.warmthType;
  consoleState.warmthAmount = p.warmthAmount;
  emitState('preset');
}

// RBJ Peaking EQ magnitude at frequency `f` (matching Yin's EqResponseView math)
export function evaluateRbjDb(f, gains, sampleRate = 96000) {
  let totalDb = 0;
  const q = 1.41421356;
  const w = (2 * Math.PI * f) / sampleRate;
  const cosW = Math.cos(w);
  const cos2W = Math.cos(2 * w);
  const sinW = Math.sin(w);
  const sin2W = Math.sin(2 * w);

  for (let i = 0; i < BANDS.length; i++) {
    const g = gains[i];
    if (Math.abs(g) < 0.05) continue;
    const fc = BANDS[i];
    if (fc >= sampleRate * 0.45) continue;
    const w0 = (2 * Math.PI * fc) / sampleRate;
    const a = Math.pow(10, g / 40);
    const alpha = Math.sin(w0) / (2 * q);
    const b0 = 1 + alpha * a;
    const b1 = -2 * Math.cos(w0);
    const b2 = 1 - alpha * a;
    const a0 = 1 + alpha / a;
    const a1 = b1;
    const a2 = 1 - alpha / a;

    const numRe = b0 + b1 * cosW + b2 * cos2W;
    const numIm = -(b1 * sinW + b2 * sin2W);
    const denRe = a0 + a1 * cosW + a2 * cos2W;
    const denIm = -(a1 * sinW + a2 * sin2W);
    const mag2 = (numRe * numRe + numIm * numIm) / Math.max(1e-18, denRe * denRe + denIm * denIm);
    totalDb += 10 * Math.log10(Math.max(1e-12, mag2));
  }
  return Math.max(-16, Math.min(16, totalDb));
}

// --- Web Audio API Synthesizer + Live Processing Chain ---
let ctx = null;
let masterPreamp = null;
let eqFilters = [];
let splitter = null;
let merger = null;
let delayL = null;
let delayR = null;
let lpL = null;
let lpR = null;
let directGainL = null;
let directGainR = null;
let crossLtoR = null;
let crossRtoL = null;
let limiter = null;
let analyserOut = null;
let loopTimer = null;

function ensureAudio() {
  if (ctx) return ctx;
  const AC = window.AudioContext || window.webkitAudioContext;
  if (!AC) return null;
  ctx = new AC();

  masterPreamp = ctx.createGain();

  let node = masterPreamp;
  eqFilters = BANDS.map((freq) => {
    const f = ctx.createBiquadFilter();
    f.type = 'peaking';
    f.frequency.value = freq;
    f.Q.value = 1.41421356;
    f.gain.value = 0;
    node.connect(f);
    node = f;
    return f;
  });

  splitter = ctx.createChannelSplitter(2);
  merger = ctx.createChannelMerger(2);
  delayL = ctx.createDelay(0.1);
  delayR = ctx.createDelay(0.1);
  lpL = ctx.createBiquadFilter();
  lpR = ctx.createBiquadFilter();
  lpL.type = 'lowpass';
  lpR.type = 'lowpass';

  directGainL = ctx.createGain();
  directGainR = ctx.createGain();
  crossLtoR = ctx.createGain();
  crossRtoL = ctx.createGain();

  limiter = ctx.createDynamicsCompressor();
  limiter.threshold.value = -2;
  limiter.knee.value = 4;
  limiter.ratio.value = 12;
  limiter.attack.value = 0.003;
  limiter.release.value = 0.12;

  analyserOut = ctx.createAnalyser();
  analyserOut.fftSize = 256;

  node.connect(splitter);
  // L path + crossfeed
  splitter.connect(delayL, 0);
  delayL.connect(lpL);
  lpL.connect(directGainL);
  directGainL.connect(merger, 0, 0);
  lpL.connect(crossLtoR);
  crossLtoR.connect(merger, 0, 1);

  // R path + crossfeed
  splitter.connect(delayR, 1);
  delayR.connect(lpR);
  lpR.connect(directGainR);
  directGainR.connect(merger, 0, 1);
  lpR.connect(crossRtoL);
  crossRtoL.connect(merger, 0, 0);

  merger.connect(limiter);
  limiter.connect(analyserOut);
  analyserOut.connect(ctx.destination);

  syncAudioGraph();
  return ctx;
}

function syncAudioGraph() {
  if (!ctx || !masterPreamp) return;
  const now = ctx.currentTime;
  const preAmpLin = Math.pow(10, consoleState.preamp / 20) * 0.65;
  masterPreamp.gain.setTargetAtTime(preAmpLin, now, 0.025);

  eqFilters.forEach((f, i) => {
    const g = consoleState.eqEnabled ? consoleState.gains[i] : 0;
    f.gain.setTargetAtTime(g, now, 0.025);
  });

  // Haas Differential Surround
  let dL = 0, dR = 0;
  let cutL = 19500, cutR = 19500;
  if (consoleState.surround) {
    const mix = consoleState.surroundAmount / 100;
    const sec = (Math.max(1, consoleState.delay) * (0.45 + 0.55 * mix)) / 1000;
    if (consoleState.surroundReverse) {
      dL = sec;
      cutL = consoleState.surroundLpHz;
    } else {
      dR = sec;
      cutR = consoleState.surroundLpHz;
    }
  }
  delayL.delayTime.setTargetAtTime(dL, now, 0.03);
  delayR.delayTime.setTargetAtTime(dR, now, 0.03);
  lpL.frequency.setTargetAtTime(cutL, now, 0.04);
  lpR.frequency.setTargetAtTime(cutR, now, 0.04);

  // Cure Crossfeed matrix
  let cross = 0;
  if (consoleState.crossfeed) {
    cross = (consoleState.crossfeedAmount / 100) * 0.34;
  }
  const direct = 1 - cross * 0.5;
  directGainL.gain.setTargetAtTime(direct, now, 0.03);
  directGainR.gain.setTargetAtTime(direct, now, 0.03);
  crossLtoR.gain.setTargetAtTime(cross, now, 0.03);
  crossRtoL.gain.setTargetAtTime(cross, now, 0.03);
}

function triggerNote(freq, dur, panVal, gainVal, type = 'triangle', startOffset = 0) {
  if (!ctx || !masterPreamp) return;
  const t0 = ctx.currentTime + startOffset;
  const osc = ctx.createOscillator();
  const g = ctx.createGain();
  const pan = ctx.createStereoPanner();
  osc.type = type;
  osc.frequency.setValueAtTime(freq, t0);
  pan.pan.setValueAtTime(panVal, t0);

  g.gain.setValueAtTime(0.0001, t0);
  g.gain.exponentialRampToValueAtTime(gainVal, t0 + 0.025);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + dur);

  osc.connect(g);
  g.connect(pan);
  pan.connect(masterPreamp);
  osc.start(t0);
  osc.stop(t0 + dur + 0.03);
}

function triggerKick(startOffset = 0) {
  if (!ctx || !masterPreamp) return;
  const t0 = ctx.currentTime + startOffset;
  const osc = ctx.createOscillator();
  const g = ctx.createGain();
  osc.type = 'sine';
  osc.frequency.setValueAtTime(118, t0);
  osc.frequency.exponentialRampToValueAtTime(42, t0 + 0.16);
  g.gain.setValueAtTime(0.32, t0);
  g.gain.exponentialRampToValueAtTime(0.0001, t0 + 0.28);
  osc.connect(g);
  g.connect(masterPreamp);
  osc.start(t0);
  osc.stop(t0 + 0.3);
}

let step = 0;
const CHORDS = [
  [146.83, 220.0, 261.63, 329.63], // Dm9
  [130.81, 196.0, 246.94, 293.66], // Cmaj9
  [116.54, 174.61, 220.0, 261.63], // Bbmaj9
  [110.0,  164.81, 196.0, 261.63], // Am7
];

export function toggleDemoAudio() {
  const c = ensureAudio();
  if (!c) return false;
  if (consoleState.playing) {
    clearInterval(loopTimer);
    loopTimer = null;
    consoleState.playing = false;
    emitState('audio');
    return false;
  }
  if (c.state === 'suspended') c.resume();
  consoleState.playing = true;
  emitState('audio');

  const playStep = () => {
    const chord = CHORDS[Math.floor(step / 4) % CHORDS.length];
    if (step % 4 === 0) {
      triggerNote(chord[0] * 0.5, 0.9, 0, 0.24, 'sine');
      chord.forEach((n, idx) => {
        const pan = idx % 2 === 0 ? -0.45 : 0.45;
        triggerNote(n, 1.35, pan, 0.065, 'triangle', idx * 0.02);
      });
    }
    if (step % 2 === 0) triggerKick(0);
    // Crisp high-frequency air pluck for clarity / binaural cues
    const hiNote = chord[(step % 3) + 1] * 2;
    triggerNote(hiNote, 0.28, (step % 2 === 0 ? -0.7 : 0.7), 0.04, 'sine');
    step++;
  };

  playStep();
  loopTimer = setInterval(playStep, 360);
  return true;
}

export function sampleMeterDb(timeSec) {
  if (consoleState.playing && analyserOut) {
    const buf = new Uint8Array(analyserOut.frequencyBinCount);
    analyserOut.getByteTimeDomainData(buf);
    let sum = 0;
    for (let i = 0; i < buf.length; i++) {
      const v = (buf[i] - 128) / 128;
      sum += v * v;
    }
    const rms = Math.sqrt(sum / buf.length);
    const outDb = Math.max(-42, Math.min(-0.4, 20 * Math.log10(Math.max(1e-4, rms * 1.9))));
    const eqBoostAvg = consoleState.eqEnabled
      ? consoleState.gains.reduce((a, b) => a + b, 0) / consoleState.gains.length
      : 0;
    const inDb = Math.max(-42, Math.min(-1.0, outDb - consoleState.preamp * 0.45 - eqBoostAvg * 0.25));
    return { inDb, outDb, energy: Math.max(0, Math.min(1, (outDb + 32) / 28)) };
  }

  // Organic studio-groove simulated telemetry when Web Audio is silent
  const beat = Math.pow(0.5 + 0.5 * Math.sin(timeSec * 5.2), 3) * 5.5;
  const sway = Math.sin(timeSec * 1.4) * 2.1 + Math.cos(timeSec * 3.1) * 1.2;
  const inDb = Math.max(-24, Math.min(-3.2, -11.4 + beat + sway));
  const avgGain = consoleState.eqEnabled
    ? consoleState.gains.reduce((acc, g) => acc + g * 0.14, 0)
    : 0;
  const bassBoost = consoleState.bass ? 1.4 : 0;
  const outDb = Math.max(-28, Math.min(-0.8, inDb + consoleState.preamp * 0.5 + avgGain + bassBoost));
  return { inDb, outDb, energy: Math.max(0.05, Math.min(1, (outDb + 24) / 22)) };
}
