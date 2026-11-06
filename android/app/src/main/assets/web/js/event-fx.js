/**
 * EventFx — broadcast stingers for FOUR / SIX / WICKET / MILESTONE.
 *
 * Original compositions synthesized live with the Web Audio API (no audio files, no
 * licensing issues): brass section, strings, dhol, drum kit, timpani, bells, air horn,
 * risers, impacts, stadium crowd and applause, all through a stadium reverb.
 *
 *   FOUR       upbeat brass hook over a dhol / bhangra groove        (~3.5 s)
 *   SIX        riser → impact → brass anthem D–A–Bm–G–D, air horn     (~4.8 s)
 *   WICKET     impact + "dun-dun-dun-DUNN" motif, tremolo strings     (~4.2 s)
 *   MILESTONE  bells → "ta-ta-ta-TAAA" fanfare, timpani, applause     (~4.7 s)
 *
 * Operators can replace any event with their own audio file (setCustom).
 *
 *   EventFx.setVolume(0.8);  EventFx.play('SIX');
 *   EventFx.setCustom('SIX', dataUrl | null);
 *   EventFx.renderWav('SIX') -> Promise<ArrayBuffer>   (offline render, for previews)
 */
(function () {
  const mtof = m => 440 * Math.pow(2, (m - 69) / 12);

  // ------------------------------------------------------------------------------
  // Engine: instruments bound to one AudioContext (realtime or offline)
  // ------------------------------------------------------------------------------
  function createEngine(ctx, dest) {
    const sr = ctx.sampleRate;

    // Mix bus → glue compressor → make-up gain → brick-wall limiter (no clipping)
    const master = ctx.createGain();
    master.gain.value = 0.5;
    const comp = ctx.createDynamicsCompressor();
    comp.threshold.value = -16;
    comp.knee.value = 8;
    comp.ratio.value = 4;
    comp.attack.value = 0.004;
    comp.release.value = 0.25;
    const makeup = ctx.createGain();
    makeup.gain.value = 1.15;
    const limiter = ctx.createDynamicsCompressor();
    limiter.threshold.value = -4;
    limiter.knee.value = 0;
    limiter.ratio.value = 20;
    limiter.attack.value = 0.001;
    limiter.release.value = 0.12;
    const ceiling = ctx.createGain();
    ceiling.gain.value = 0.85;
    master.connect(comp);
    comp.connect(makeup);
    makeup.connect(limiter);
    limiter.connect(ceiling);
    ceiling.connect(dest);

    // Shared white-noise buffer
    const noiseBuf = ctx.createBuffer(1, sr * 3, sr);
    const nd = noiseBuf.getChannelData(0);
    for (let i = 0; i < nd.length; i++) nd[i] = Math.random() * 2 - 1;

    // Stadium reverb (generated stereo impulse)
    const verb = ctx.createConvolver();
    const irLen = Math.floor(sr * 2.4);
    const ir = ctx.createBuffer(2, irLen, sr);
    for (let ch = 0; ch < 2; ch++) {
      const d = ir.getChannelData(ch);
      for (let i = 0; i < irLen; i++) d[i] = (Math.random() * 2 - 1) * Math.pow(1 - i / irLen, 3.2);
    }
    verb.buffer = ir;
    const verbOut = ctx.createGain();
    verbOut.gain.value = 0.32;
    verb.connect(verbOut);
    verbOut.connect(master);

    function out(node, wet = 0.2) {
      node.connect(master);
      if (wet > 0) {
        const s = ctx.createGain();
        s.gain.value = wet;
        node.connect(s);
        s.connect(verb);
      }
    }

    function env(g, t, a, peak, d) {
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(Math.max(0.0002, peak), t + a);
      g.gain.exponentialRampToValueAtTime(0.0001, t + a + d);
    }

    function noiseSrc(t, dur) {
      const src = ctx.createBufferSource();
      src.buffer = noiseBuf;
      src.loop = true;
      src.start(t, Math.random() * 2);
      src.stop(t + dur + 0.05);
      return src;
    }

    function noise(t, dur, { type = 'highpass', freq = 6000, q = 0.7, peak = 0.3, attack = 0.002, wet = 0.2 } = {}) {
      const src = noiseSrc(t, attack + dur);
      const f = ctx.createBiquadFilter();
      f.type = type; f.frequency.value = freq; f.Q.value = q;
      const g = ctx.createGain();
      env(g, t, attack, peak, dur);
      src.connect(f); f.connect(g); out(g, wet);
      return f;
    }

    function tone(t, type, f0, f1, glide, a, peak, d, wet = 0.1) {
      const o = ctx.createOscillator();
      o.type = type;
      o.frequency.setValueAtTime(f0, t);
      if (f1 && f1 !== f0) o.frequency.exponentialRampToValueAtTime(f1, t + glide);
      const g = ctx.createGain();
      env(g, t, a, peak, d);
      o.connect(g); out(g, wet);
      o.start(t); o.stop(t + a + d + 0.05);
    }

    // ---- Drum kit ----
    const kick = (t, v = 0.9) => {
      tone(t, 'sine', 165, 42, 0.13, 0.002, v, 0.36, 0.04);
      noise(t, 0.015, { type: 'highpass', freq: 3000, peak: v * 0.25, wet: 0 });
    };
    const snare = (t, v = 0.5) => {
      noise(t, 0.17, { type: 'bandpass', freq: 1900, q: 0.7, peak: v, wet: 0.28 });
      tone(t, 'triangle', 200, 150, 0.08, 0.002, v * 0.45, 0.11, 0.15);
    };
    const clap = (t, v = 0.45) => {
      [0, 0.011, 0.023].forEach(d => noise(t + d, 0.025, { type: 'bandpass', freq: 1300, q: 1.2, peak: v * 0.8, wet: 0.2 }));
      noise(t + 0.03, 0.16, { type: 'bandpass', freq: 1200, q: 1, peak: v * 0.5, wet: 0.35 });
    };
    const hat = (t, v = 0.1, open = false) => noise(t, open ? 0.22 : 0.045, { type: 'highpass', freq: 8200, peak: v, wet: 0.05 });
    const crash = (t, v = 0.4, dur = 2.2) => {
      noise(t, dur, { type: 'highpass', freq: 4800, q: 0.3, peak: v, wet: 0.45 });
      noise(t, dur * 0.6, { type: 'bandpass', freq: 9500, q: 0.8, peak: v * 0.5, wet: 0.3 });
    };
    const reverseCymbal = (t, dur, v = 0.3) => {
      const src = noiseSrc(t, dur);
      const f = ctx.createBiquadFilter();
      f.type = 'highpass'; f.frequency.value = 5000;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(v, t + dur);
      g.gain.linearRampToValueAtTime(0.0001, t + dur + 0.03);
      src.connect(f); f.connect(g); out(g, 0.4);
    };
    const snareRoll = (t, dur, v0 = 0.15, v1 = 0.55) => {
      let x = 0, gap = 0.09;
      while (x < dur) {
        snare(t + x, v0 + (v1 - v0) * (x / dur));
        x += gap;
        gap = Math.max(0.035, gap * 0.9);
      }
    };

    // ---- Indian dhol (bass "dagga" + treble "tilli") ----
    const dhol = (t, kind = 'dagga', v = 0.7) => {
      if (kind === 'dagga' || kind === 'dha') {
        tone(t, 'sine', 105, 62, 0.22, 0.003, v, 0.42, 0.15);
        noise(t, 0.05, { type: 'lowpass', freq: 380, peak: v * 0.35, wet: 0.1 });
      }
      if (kind === 'tilli' || kind === 'dha') {
        tone(t, 'triangle', 460, 380, 0.05, 0.002, v * 0.55, 0.12, 0.15);
        noise(t, 0.04, { type: 'bandpass', freq: 2600, q: 1.2, peak: v * 0.4, wet: 0.1 });
      }
    };

    // ---- Orchestral percussion ----
    const timpani = (t, f, v = 0.7, dur = 1.6) => {
      [1, 1.5, 1.98].forEach((p, i) => tone(t, 'sine', f * p * 1.03, f * p, 0.08, 0.004, v * [1, 0.35, 0.2][i], dur * [1, 0.6, 0.4][i], 0.4));
      noise(t, 0.08, { type: 'lowpass', freq: 500, peak: v * 0.35, wet: 0.3 });
    };
    const timpaniRoll = (t, dur, f, v0 = 0.12, v1 = 0.6) => {
      for (let x = 0; x < dur; x += 0.055) timpani(t + x, f, v0 + (v1 - v0) * (x / dur), 0.35);
    };
    const taiko = (t, f = 60, v = 0.8) => {
      tone(t, 'sine', f * 1.6, f, 0.1, 0.003, v, 0.75, 0.45);
      noise(t, 0.12, { type: 'lowpass', freq: 260, peak: v * 0.45, wet: 0.35 });
    };
    const boom = (t, v = 0.9) => {
      tone(t, 'sine', 75, 27, 1.3, 0.004, v, 1.5, 0.5);
      noise(t, 0.6, { type: 'lowpass', freq: 260, peak: v * 0.55, wet: 0.5 });
      noise(t, 0.25, { type: 'bandpass', freq: 1200, q: 0.5, peak: v * 0.25, wet: 0.6 });
    };
    const sub = (t, m, dur, v = 0.45) => tone(t, 'sine', mtof(m), mtof(m), 0, 0.01, v, dur, 0);

    // ---- Brass section ----
    function brassVoice(t, m, dur, v, bright, wet, vibrato) {
      const f = ctx.createBiquadFilter();
      f.type = 'lowpass';
      f.Q.value = 1.4;
      f.frequency.setValueAtTime(500, t);
      f.frequency.exponentialRampToValueAtTime(bright, t + 0.045);
      f.frequency.exponentialRampToValueAtTime(bright * 0.55, t + 0.25);
      f.frequency.setValueAtTime(bright * 0.55, t + dur);
      f.frequency.exponentialRampToValueAtTime(400, t + dur + 0.2);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(v, t + 0.02);
      g.gain.exponentialRampToValueAtTime(v * 0.72, t + 0.16);
      g.gain.setValueAtTime(v * 0.72, t + dur);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.2);
      f.connect(g); out(g, wet);
      let lfo = null;
      if (vibrato && dur > 0.45) {
        lfo = ctx.createOscillator();
        const lg = ctx.createGain();
        lfo.frequency.value = 5.6;
        lg.gain.setValueAtTime(0, t);
        lg.gain.linearRampToValueAtTime(9, t + Math.min(dur, 0.5));
        lfo.connect(lg);
        lfo.start(t); lfo.stop(t + dur + 0.25);
        lfo._g = lg;
      }
      [-9, 0, 9].forEach(c => {
        const o = ctx.createOscillator();
        o.type = 'sawtooth';
        o.frequency.value = mtof(m);
        o.detune.value = c;
        if (lfo) lfo._g.connect(o.detune);
        o.connect(f);
        o.start(t); o.stop(t + dur + 0.25);
      });
    }
    const brass = (t, notes, dur, v = 0.14, { bright = 3400, wet = 0.3, vibrato = true } = {}) => {
      const list = Array.isArray(notes) ? notes : [notes];
      const each = v / Math.sqrt(list.length);
      list.forEach(m => brassVoice(t, m, dur, each, bright, wet, vibrato));
    };
    // Lead trumpet line, doubled an octave down for weight
    const lead = (t, m, dur, v = 0.17) => {
      brassVoice(t, m, dur, v, 4200, 0.3, true);
      brassVoice(t, m - 12, dur, v * 0.55, 2600, 0.25, true);
    };

    // ---- Strings ----
    const strings = (t, notes, dur, v = 0.09, { attack = 0.25, tremolo = false } = {}) => {
      const f = ctx.createBiquadFilter();
      f.type = 'lowpass'; f.frequency.value = 2400; f.Q.value = 0.5;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(v, t + attack);
      g.gain.setValueAtTime(v, t + dur);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.35);
      let trem = g;
      if (tremolo) {
        trem = ctx.createGain();
        trem.gain.value = 0.6;
        const lfo = ctx.createOscillator();
        const lg = ctx.createGain();
        lfo.frequency.value = 11; lg.gain.value = 0.4;
        lfo.connect(lg); lg.connect(trem.gain);
        lfo.start(t); lfo.stop(t + dur + 0.4);
        f.connect(trem); trem.connect(g);
      } else {
        f.connect(g);
      }
      out(g, 0.55);
      (Array.isArray(notes) ? notes : [notes]).forEach(m => {
        [-12, 0, 12].forEach(c => {
          const o = ctx.createOscillator();
          o.type = 'sawtooth';
          o.frequency.value = mtof(m);
          o.detune.value = c + (Math.random() * 4 - 2);
          o.connect(f);
          o.start(t); o.stop(t + dur + 0.4);
        });
      });
    };

    // ---- Bells / chimes ----
    const bell = (t, m, v = 0.18) => {
      [[1, 1, 2.4], [2.76, 0.45, 1.3], [5.4, 0.22, 0.7], [8.93, 0.1, 0.35]].forEach(([p, a, d]) =>
        tone(t, 'sine', mtof(m) * p, mtof(m) * p, 0, 0.002, v * a, d, 0.5));
    };

    // ---- FX ----
    const riser = (t, dur, v = 0.22) => {
      const o = ctx.createOscillator();
      o.type = 'sawtooth';
      o.frequency.setValueAtTime(110, t);
      o.frequency.exponentialRampToValueAtTime(1500, t + dur);
      const f = ctx.createBiquadFilter();
      f.type = 'lowpass';
      f.frequency.setValueAtTime(300, t);
      f.frequency.exponentialRampToValueAtTime(7000, t + dur);
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(v, t + dur);
      g.gain.linearRampToValueAtTime(0.0001, t + dur + 0.03);
      o.connect(f); f.connect(g); out(g, 0.35);
      o.start(t); o.stop(t + dur + 0.05);
      const nsrc = noiseSrc(t, dur);
      const nf = ctx.createBiquadFilter();
      nf.type = 'bandpass'; nf.Q.value = 1.4;
      nf.frequency.setValueAtTime(400, t);
      nf.frequency.exponentialRampToValueAtTime(9000, t + dur);
      const ng = ctx.createGain();
      ng.gain.setValueAtTime(0.0001, t);
      ng.gain.exponentialRampToValueAtTime(v * 0.9, t + dur);
      ng.gain.linearRampToValueAtTime(0.0001, t + dur + 0.03);
      nsrc.connect(nf); nf.connect(ng); out(ng, 0.4);
    };
    const airhorn = (t, dur, v = 0.14) => {
      const f = ctx.createBiquadFilter();
      f.type = 'bandpass'; f.frequency.value = 950; f.Q.value = 0.9;
      const g = ctx.createGain();
      g.gain.setValueAtTime(0.0001, t);
      g.gain.exponentialRampToValueAtTime(v, t + 0.03);
      g.gain.setValueAtTime(v, t + dur);
      g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.12);
      f.connect(g); out(g, 0.4);
      [233, 236.5, 466].forEach(fr => {
        const o = ctx.createOscillator();
        o.type = 'sawtooth';
        o.frequency.setValueAtTime(fr * 0.86, t);
        o.frequency.exponentialRampToValueAtTime(fr, t + 0.06);
        o.connect(f);
        o.start(t); o.stop(t + dur + 0.15);
      });
    };

    // ---- Stadium crowd ----
    const crowd = (t, dur, v = 0.22, mood = 'cheer') => {
      v *= 0.55; // keep the crowd under the music
      const bands = mood === 'gasp' ? [[420, 2.5], [780, 2.5], [1150, 2]] : [[520, 0.8], [950, 0.9], [1600, 0.9], [2700, 1]];
      bands.forEach(([fr, q], bi) => {
        const src = noiseSrc(t, dur);
        const f = ctx.createBiquadFilter();
        f.type = 'bandpass'; f.frequency.value = fr; f.Q.value = q;
        const g = ctx.createGain();
        const step = 0.07;
        g.gain.setValueAtTime(0.0001, t);
        const rise = mood === 'gasp' ? 0.22 : Math.min(0.45, dur * 0.25);
        for (let x = step; x < dur; x += step) {
          const shape = x < rise ? x / rise : (x > dur * 0.65 ? Math.max(0, 1 - (x - dur * 0.65) / (dur * 0.35)) : 1);
          const jitter = 0.75 + Math.random() * 0.5;
          g.gain.linearRampToValueAtTime(Math.max(0.0001, v * shape * jitter / (1 + bi * 0.25)), t + x);
        }
        g.gain.linearRampToValueAtTime(0.0001, t + dur);
        src.connect(f); f.connect(g); out(g, 0.5);
      });
    };
    const applause = (t, dur, v = 0.12, density = 26) => {
      v *= 0.7;
      const n = Math.floor(dur * density);
      for (let i = 0; i < n; i++) {
        const x = Math.random() * dur;
        const shape = x < 0.4 ? x / 0.4 : (x > dur * 0.6 ? Math.max(0.05, 1 - (x - dur * 0.6) / (dur * 0.4)) : 1);
        noise(t + x, 0.02 + Math.random() * 0.025, {
          type: 'bandpass', freq: 900 + Math.random() * 1800, q: 1.4, peak: v * shape * (0.5 + Math.random() * 0.5), wet: 0.35
        });
      }
    };

    // Helper: schedule [beat, notes, beats] entries against a beat length
    const seq = (t0, beat, list, fn) => list.forEach(([b, n, len]) => fn(t0 + b * beat, n, len * beat));

    return {
      kick, snare, clap, hat, crash, reverseCymbal, snareRoll, dhol, timpani, timpaniRoll, taiko, boom, sub,
      brass, lead, strings, bell, riser, airhorn, crowd, applause, seq
    };
  }

  // Chords (MIDI)
  const D = [62, 66, 69], G = [67, 71, 74], A = [61, 64, 69], Bm = [59, 62, 66], Gl = [59, 62, 67];

  // ------------------------------------------------------------------------------
  // Compositions
  // ------------------------------------------------------------------------------
  const SCORES = {
    // Upbeat brass hook over a bhangra-style dhol groove
    FOUR: { length: 4.0, play(e, t0) {
      const E = 60 / 132 / 2; // eighth note
      e.snareRoll(t0, 0.36, 0.18, 0.55);
      e.riser(t0, 0.4, 0.18);
      const h = t0 + 0.4;
      e.kick(h); e.crash(h, 0.4, 2.2); e.clap(h, 0.4); e.boom(h, 0.35);

      e.seq(h, E, [[0, 74, 1], [1, 74, 0.5], [1.5, 74, 0.5], [2, 78, 1], [3, 81, 1], [4, 78, 0.5], [4.5, 81, 0.5], [5, 83, 1], [6, 86, 3.2]],
        (t, m, d) => e.lead(t, m, d * 0.92, 0.17));
      e.seq(h, E, [[0, D, 0.8], [2, D, 0.8], [4, G, 0.8], [5, A, 0.8], [6, [...D, 74], 3.2]],
        (t, n, d) => e.brass(t, n, d, 0.13, { bright: 2600 }));
      e.seq(h, E, [[0, 38, 1.8], [2, 38, 1.8], [4, 43, 0.9], [5, 45, 0.9], [6, 38, 3]], (t, m, d) => e.sub(t, m, d, 0.4));

      for (let i = 0; i < 9; i++) {
        const t = h + i * E;
        if (i % 2 === 0) e.kick(t, 0.75);
        e.hat(t, i % 2 ? 0.09 : 0.05);
      }
      [2, 6].forEach(i => e.clap(h + i * E, 0.4));
      [[0, 'dha'], [1, 'tilli'], [1.5, 'tilli'], [2.5, 'tilli'], [3, 'dagga'], [3.5, 'tilli'], [4, 'dha'], [5, 'tilli'], [5.5, 'tilli'], [6, 'dha']]
        .forEach(([i, k]) => e.dhol(h + i * E, k, 0.6));
      e.crash(h + 6 * E, 0.35, 2);
      e.crowd(t0 + 0.25, 3.6, 0.2);
      e.applause(h, 3.0, 0.1);
    } },

    // Epic stadium anthem: riser → impact → D – A – Bm – G – D with lead brass
    SIX: { length: 5.4, play(e, t0) {
      const B = 60 / 128;
      e.riser(t0, 0.8, 0.26);
      e.reverseCymbal(t0 + 0.2, 0.6, 0.28);
      e.snareRoll(t0 + 0.15, 0.62, 0.12, 0.6);
      const h = t0 + 0.8;
      e.boom(h, 0.95); e.kick(h, 1); e.crash(h, 0.5, 2.6); e.taiko(h, 55, 0.7);
      e.brass(h, [50, 57, 62, 66, 69, 74], 0.38, 0.2, { bright: 3800, vibrato: false });
      e.airhorn(h + 0.05, 0.42, 0.12);

      e.seq(h, B, [[0, 81, 1], [1, 78, 0.5], [1.5, 79, 0.5], [2, 81, 1], [3, 86, 1], [4, 83, 1], [5, 81, 1], [6, 86, 2.6]],
        (t, m, d) => e.lead(t, m, d * 0.93, 0.18));
      e.seq(h, B, [[0, D, 1.9], [2, A, 1.9], [4, Bm, 0.95], [5, Gl, 0.95], [6, [...D, 74], 2.6]],
        (t, n, d) => { e.brass(t, n, d, 0.11, { bright: 2400 }); e.strings(t, n.map(x => x - 12), d, 0.06, { attack: 0.08 }); });
      e.seq(h, B, [[0, 38, 2], [2, 45, 2], [4, 47, 1], [5, 43, 1], [6, 38, 2.6]], (t, m, d) => e.sub(t, m, d, 0.42));

      for (let i = 0; i < 8; i++) {
        const t = h + i * B;
        e.kick(t, 0.8);
        if (i % 2 === 1) { e.clap(t, 0.42); e.snare(t, 0.35); }
        e.hat(t + B / 2, 0.08);
        e.dhol(t, i % 2 ? 'tilli' : 'dagga', 0.5);
        e.dhol(t + B * 0.75, 'tilli', 0.4);
      }
      e.crash(h + 4 * B, 0.3, 1.8);
      e.snareRoll(h + 5.25 * B, 0.7 * B, 0.2, 0.55);
      e.crash(h + 6 * B, 0.45, 2.4); e.boom(h + 6 * B, 0.45); e.timpani(h + 6 * B, mtof(38), 0.6, 1.6);
      e.airhorn(h + 6 * B + 0.05, 0.6, 0.1);
      e.crowd(t0 + 0.6, 4.8, 0.3);
      e.applause(h, 4.2, 0.13);
    } },

    // Dramatic: impact, low brass "dun-dun-dun-DUNN", tremolo strings, final hit
    WICKET: { length: 4.6, play(e, t0) {
      e.boom(t0, 1); e.crash(t0, 0.32, 1.8); e.taiko(t0, 52, 0.9);
      e.timpani(t0, mtof(38), 0.9, 2);
      e.brass(t0, [38, 45, 50, 53], 0.55, 0.22, { bright: 2200, vibrato: false });
      e.strings(t0, [50, 53, 57, 62], 0.5, 0.08, { attack: 0.02 });
      e.crowd(t0 + 0.05, 1.1, 0.24, 'gasp');

      [[0.55, 53, 0.17], [0.8, 52, 0.17], [1.05, 50, 0.17], [1.35, 45, 1.2]].forEach(([d, m, len]) => {
        e.brass(t0 + d, [m, m + 12], len, 0.2, { bright: 2000, vibrato: len > 0.5 });
        e.timpani(t0 + d, mtof(m - 12), 0.7, len > 0.5 ? 1.6 : 0.5);
        e.taiko(t0 + d, 58, 0.5);
      });
      e.crash(t0 + 1.35, 0.3, 1.6); e.boom(t0 + 1.35, 0.5);
      e.strings(t0 + 1.35, [45, 50, 53, 57], 2.2, 0.075, { attack: 0.5, tremolo: true });
      [1.95, 2.13, 2.65, 2.83].forEach(d => e.kick(t0 + d, 0.55));
      e.timpaniRoll(t0 + 2.4, 0.62, mtof(45), 0.08, 0.5);
      e.brass(t0 + 3.05, [38, 45, 50, 53, 57], 0.9, 0.22, { bright: 2600, vibrato: false });
      e.crash(t0 + 3.05, 0.35, 1.5); e.boom(t0 + 3.05, 0.6); e.timpani(t0 + 3.05, mtof(38), 0.8, 1.4);
      e.crowd(t0 + 1.3, 3.0, 0.22);
    } },

    // Triumphant: bells → timpani roll → "ta-ta-ta-TAAA" fanfare → big D major
    MILESTONE: { length: 5.4, play(e, t0) {
      const B = 60 / 112;
      [86, 90, 93, 98].forEach((m, i) => e.bell(t0 + i * 0.09, m, 0.14));
      e.reverseCymbal(t0 + 0.05, 0.65, 0.25);
      e.timpaniRoll(t0 + 0.2, 0.5, mtof(45), 0.1, 0.5);
      const h = t0 + 0.7;
      e.crash(h, 0.4, 2); e.boom(h, 0.45); e.kick(h, 0.8); e.timpani(h, mtof(38), 0.7, 1.2);

      e.seq(h, B, [[0, 74, 0.3], [1 / 3, 74, 0.3], [2 / 3, 74, 0.3], [1, 81, 1.4], [2.5, 83, 0.45], [3, 81, 0.45], [3.5, 79, 0.45], [4, 78, 0.45], [4.5, 76, 0.45], [5, 86, 2.4]],
        (t, m, d) => e.lead(t, m, d * 0.95, 0.18));
      e.seq(h, B, [[0, D, 0.9], [1, D, 1.45], [2.5, G, 0.95], [3.5, A, 1.45], [5, [...D, 74], 2.4]],
        (t, n, d) => { e.brass(t, n, d, 0.11, { bright: 2400 }); e.strings(t, n.map(x => x - 12), d, 0.065, { attack: 0.12 }); });
      e.seq(h, B, [[1, 38, 1.5], [2.5, 43, 1], [3.5, 45, 1.5], [5, 38, 2.4]], (t, m, d) => e.sub(t, m, d, 0.4));
      [1, 2.5, 3.5].forEach(b => { e.timpani(h + b * B, mtof(b === 2.5 ? 43 : b === 3.5 ? 45 : 38), 0.6, 0.9); e.kick(h + b * B, 0.6); });
      e.snareRoll(h + 4 * B, B, 0.15, 0.55);
      e.crash(h + 5 * B, 0.45, 2.2); e.boom(h + 5 * B, 0.4); e.timpani(h + 5 * B, mtof(38), 0.75, 1.6);
      [86, 93, 98].forEach((m, i) => e.bell(h + 5 * B + i * 0.12, m, 0.1));
      e.crowd(t0 + 0.5, 4.6, 0.22);
      e.applause(h, 4.2, 0.17, 34);
    } }
  };

  // ------------------------------------------------------------------------------
  // Realtime playback + custom files
  // ------------------------------------------------------------------------------
  let ctx = null;
  let outGain = null;
  let engine = null;
  let volume = 0.8;
  const custom = {};        // type -> dataUrl
  const customBuf = {};     // type -> AudioBuffer

  function ensure() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    if (!ctx) {
      ctx = new AC();
      outGain = ctx.createGain();
      outGain.gain.value = volume;
      outGain.connect(ctx.destination);
      engine = createEngine(ctx, outGain);
    }
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    return true;
  }

  // Browsers only allow audio after a user gesture; unlock on the first tap/click.
  ['pointerdown', 'keydown', 'touchstart'].forEach(ev =>
    window.addEventListener(ev, () => { try { ensure(); } catch (_) {} }, { once: true, passive: true })
  );

  function dataUrlToArrayBuffer(url) {
    const b64 = String(url).split(',')[1] || '';
    const bin = atob(b64);
    const buf = new Uint8Array(bin.length);
    for (let i = 0; i < bin.length; i++) buf[i] = bin.charCodeAt(i);
    return buf.buffer;
  }

  async function customBuffer(type) {
    if (customBuf[type]) return customBuf[type];
    if (!custom[type]) return null;
    const ab = dataUrlToArrayBuffer(custom[type]);
    customBuf[type] = await new Promise((res, rej) => ctx.decodeAudioData(ab, res, rej));
    return customBuf[type];
  }

  async function play(type) {
    try {
      type = String(type || '').toUpperCase();
      if (!ensure() || volume <= 0) return;
      if (custom[type]) {
        const buf = await customBuffer(type).catch(() => null);
        if (buf) {
          const src = ctx.createBufferSource();
          src.buffer = buf;
          src.connect(outGain);
          src.start(ctx.currentTime + 0.02);
          return;
        }
      }
      const score = SCORES[type];
      if (score) score.play(engine, ctx.currentTime + 0.05);
    } catch (_) {}
  }

  // Offline render to a 16-bit stereo WAV (used for previews / testing)
  async function renderWav(type) {
    const score = SCORES[String(type || '').toUpperCase()];
    if (!score) return null;
    const sr = 44100;
    const off = new OfflineAudioContext(2, Math.ceil(sr * score.length), sr);
    const e = createEngine(off, off.destination);
    score.play(e, 0.02);
    const buf = await off.startRendering();
    const n = buf.length, chs = [buf.getChannelData(0), buf.getChannelData(1)];
    const ab = new ArrayBuffer(44 + n * 4);
    const v = new DataView(ab);
    const w = (o, s) => { for (let i = 0; i < s.length; i++) v.setUint8(o + i, s.charCodeAt(i)); };
    w(0, 'RIFF'); v.setUint32(4, 36 + n * 4, true); w(8, 'WAVE'); w(12, 'fmt ');
    v.setUint32(16, 16, true); v.setUint16(20, 1, true); v.setUint16(22, 2, true);
    v.setUint32(24, sr, true); v.setUint32(28, sr * 4, true); v.setUint16(32, 4, true); v.setUint16(34, 16, true);
    w(36, 'data'); v.setUint32(40, n * 4, true);
    let p = 44, peak = 0;
    for (let i = 0; i < n; i++) for (let c = 0; c < 2; c++) {
      const s = Math.max(-1, Math.min(1, chs[c][i]));
      peak = Math.max(peak, Math.abs(s));
      v.setInt16(p, s < 0 ? s * 0x8000 : s * 0x7fff, true); p += 2;
    }
    return { wav: ab, peak, seconds: score.length };
  }

  window.EventFx = {
    types: Object.keys(SCORES),
    setVolume(v) {
      volume = Math.max(0, Math.min(1, Number(v)));
      if (outGain) outGain.gain.value = volume;
    },
    /** Use an uploaded audio file (data: URL) for an event; null restores the built-in score. */
    setCustom(type, dataUrl) {
      type = String(type || '').toUpperCase();
      if (dataUrl) custom[type] = dataUrl; else delete custom[type];
      delete customBuf[type];
    },
    hasCustom(type) { return Boolean(custom[String(type || '').toUpperCase()]); },
    unlock() { try { ensure(); } catch (_) {} },
    play,
    renderWav
  };
})();
