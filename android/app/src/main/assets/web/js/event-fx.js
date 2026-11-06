/**
 * EventFx — original broadcast-style stingers for FOUR / SIX / WICKET / MILESTONE.
 * Everything is synthesized live with the Web Audio API (drums, brass stabs, risers,
 * crowd roar), so there are no audio files and no licensing concerns.
 *
 * Usage:  EventFx.setVolume(0.8); EventFx.play('FOUR' | 'SIX' | 'WICKET' | 'MILESTONE');
 */
(function () {
  let ctx = null;
  let master = null;
  let volume = 0.8;
  let noiseBuf = null;

  function ensure() {
    const AC = window.AudioContext || window.webkitAudioContext;
    if (!AC) return false;
    if (!ctx) {
      ctx = new AC();
      const comp = ctx.createDynamicsCompressor();
      comp.threshold.value = -14;
      comp.knee.value = 10;
      comp.ratio.value = 4;
      master = ctx.createGain();
      master.gain.value = volume;
      master.connect(comp);
      comp.connect(ctx.destination);
      noiseBuf = ctx.createBuffer(1, ctx.sampleRate * 2, ctx.sampleRate);
      const d = noiseBuf.getChannelData(0);
      for (let i = 0; i < d.length; i++) d[i] = Math.random() * 2 - 1;
    }
    if (ctx.state === 'suspended') ctx.resume().catch(() => {});
    return true;
  }

  // Browsers only allow audio after a user gesture; unlock on the first tap/click.
  ['pointerdown', 'keydown', 'touchstart'].forEach(ev =>
    window.addEventListener(ev, () => { try { ensure(); } catch (_) {} }, { once: true, passive: true })
  );

  // ---------- instruments ----------
  function env(g, t, a, peak, d) {
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + a);
    g.gain.exponentialRampToValueAtTime(0.0001, t + a + d);
  }

  function noise(t, dur, { type = 'highpass', freq = 6000, q = 0.7, peak = 0.3, attack = 0.002 } = {}) {
    const src = ctx.createBufferSource();
    src.buffer = noiseBuf;
    src.loop = true;
    const f = ctx.createBiquadFilter();
    f.type = type; f.frequency.value = freq; f.Q.value = q;
    const g = ctx.createGain();
    env(g, t, attack, peak, dur);
    src.connect(f); f.connect(g); g.connect(master);
    src.start(t); src.stop(t + attack + dur + 0.05);
    return f;
  }

  function kick(t, peak = 0.9) {
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.setValueAtTime(150, t);
    o.frequency.exponentialRampToValueAtTime(42, t + 0.18);
    env(g, t, 0.003, peak, 0.32);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + 0.4);
  }

  function snare(t, peak = 0.45) {
    noise(t, 0.16, { type: 'bandpass', freq: 1900, q: 0.8, peak });
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.type = 'triangle';
    o.frequency.setValueAtTime(220, t);
    o.frequency.exponentialRampToValueAtTime(160, t + 0.08);
    env(g, t, 0.002, peak * 0.6, 0.1);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + 0.15);
  }

  function crash(t, peak = 0.35, dur = 1.6) {
    noise(t, dur, { type: 'highpass', freq: 5200, q: 0.5, peak });
  }

  function boom(t, peak = 1.0) {
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.setValueAtTime(95, t);
    o.frequency.exponentialRampToValueAtTime(30, t + 0.9);
    env(g, t, 0.005, peak, 1.1);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + 1.2);
    noise(t, 0.5, { type: 'lowpass', freq: 400, peak: peak * 0.5 });
  }

  function tom(t, freq, peak = 0.6) {
    const o = ctx.createOscillator();
    const g = ctx.createGain();
    o.frequency.setValueAtTime(freq * 1.6, t);
    o.frequency.exponentialRampToValueAtTime(freq, t + 0.12);
    env(g, t, 0.003, peak, 0.45);
    o.connect(g); g.connect(master);
    o.start(t); o.stop(t + 0.55);
  }

  // Brass-like stab: detuned saws through a swelling low-pass filter
  function brass(t, freqs, dur, peak = 0.16) {
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.Q.value = 1.2;
    f.frequency.setValueAtTime(500, t);
    f.frequency.exponentialRampToValueAtTime(3600, t + 0.06);
    f.frequency.exponentialRampToValueAtTime(1300, t + Math.max(0.12, dur));
    const g = ctx.createGain();
    env(g, t, 0.025, peak, dur);
    f.connect(g); g.connect(master);
    freqs.forEach(fr => {
      [-7, 7].forEach(cents => {
        const o = ctx.createOscillator();
        o.type = 'sawtooth';
        o.frequency.value = fr;
        o.detune.value = cents;
        o.connect(f);
        o.start(t); o.stop(t + dur + 0.1);
      });
    });
  }

  function riser(t, dur, peak = 0.18) {
    const o = ctx.createOscillator();
    o.type = 'sawtooth';
    o.frequency.setValueAtTime(180, t);
    o.frequency.exponentialRampToValueAtTime(1400, t + dur);
    const f = ctx.createBiquadFilter();
    f.type = 'lowpass';
    f.frequency.setValueAtTime(400, t);
    f.frequency.exponentialRampToValueAtTime(6000, t + dur);
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + dur);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur + 0.05);
    o.connect(f); f.connect(g); g.connect(master);
    o.start(t); o.stop(t + dur + 0.1);
    const nf = noise(t, dur, { type: 'bandpass', freq: 800, q: 1, peak: peak * 0.8, attack: dur * 0.9 });
    nf.frequency.exponentialRampToValueAtTime(7000, t + dur);
  }

  // Stadium crowd: band-limited noise with a slow swell
  function crowd(t, dur, peak = 0.22, centre = 1100) {
    const src = ctx.createBufferSource();
    src.buffer = noiseBuf; src.loop = true;
    const f = ctx.createBiquadFilter();
    f.type = 'bandpass'; f.frequency.value = centre; f.Q.value = 0.6;
    const g = ctx.createGain();
    g.gain.setValueAtTime(0.0001, t);
    g.gain.exponentialRampToValueAtTime(peak, t + dur * 0.3);
    g.gain.setValueAtTime(peak, t + dur * 0.6);
    g.gain.exponentialRampToValueAtTime(0.0001, t + dur);
    const lfo = ctx.createOscillator();
    const lfoGain = ctx.createGain();
    lfo.frequency.value = 3.5; lfoGain.gain.value = 260;
    lfo.connect(lfoGain); lfoGain.connect(f.frequency);
    src.connect(f); f.connect(g); g.connect(master);
    src.start(t); src.stop(t + dur + 0.05);
    lfo.start(t); lfo.stop(t + dur + 0.05);
  }

  const N = { A2: 110, C3: 130.81, D3: 146.83, E3: 164.81, F3: 174.61, G3: 196, A3: 220, B3: 246.94,
    C4: 261.63, Cs4: 277.18, D4: 293.66, E4: 329.63, Fs4: 369.99, G4: 392, Gs4: 415.3, A4: 440, B4: 493.88,
    Cs5: 554.37, D5: 587.33, E5: 659.25, A5: 880 };

  // ---------- stingers ----------
  const stingers = {
    FOUR(t) {
      // snare pickup → crash + punchy brass hits → sustained chord over crowd
      [0, 0.09, 0.18, 0.27].forEach((d, i) => snare(t + d, 0.25 + i * 0.07));
      tom(t + 0.18, 140); tom(t + 0.27, 110);
      const h = t + 0.38;
      kick(h); crash(h, 0.3, 1.4);
      brass(h, [N.G3, N.C4, N.E4, N.G4], 0.16);
      kick(h + 0.21, 0.7); brass(h + 0.21, [N.G3, N.C4, N.E4, N.G4], 0.12);
      kick(h + 0.42); snare(h + 0.42, 0.4);
      brass(h + 0.42, [N.C4, N.E4, N.G4, N.C4 * 2], 1.1, 0.18);
      crowd(t + 0.25, 2.6, 0.2);
    },
    SIX(t) {
      // riser → big impact → rising three-chord brass fanfare, four-on-the-floor, huge crowd
      riser(t, 0.55);
      const h = t + 0.55;
      boom(h, 0.9); kick(h); crash(h, 0.4, 2.0);
      brass(h, [N.D3, N.Fs4 / 2, N.A3, N.D4], 0.2, 0.17);
      kick(h + 0.26); snare(h + 0.26, 0.35);
      brass(h + 0.26, [N.E3, N.Gs4 / 2, N.B3, N.E4], 0.2, 0.17);
      kick(h + 0.52); snare(h + 0.52, 0.45); crash(h + 0.52, 0.3, 1.6);
      brass(h + 0.52, [N.A3, N.Cs4, N.E4, N.A4, N.Cs5], 1.4, 0.2);
      [0.78, 1.04, 1.3, 1.56].forEach(d => kick(h + d, 0.6));
      [0.91, 1.17, 1.43].forEach(d => snare(h + d, 0.25));
      crowd(t + 0.4, 3.2, 0.26);
    },
    WICKET(t) {
      // impact → minor stab → descending dun-dun-dun → crowd gasp
      boom(t, 1.0); crash(t, 0.25, 1.2);
      brass(t, [N.A2, N.E3, N.A3, N.C4], 0.5, 0.2);
      tom(t, 90, 0.8);
      [[0.42, N.E3], [0.6, N.D3], [0.78, N.C3], [1.0, N.A2]].forEach(([d, f], i) => {
        brass(t + d, [f, f * 1.5, f * 2], i === 3 ? 1.1 : 0.14, 0.17);
        tom(t + d, f / 1.5, 0.55);
      });
      kick(t + 1.0); boom(t + 1.0, 0.6);
      crowd(t + 0.1, 0.9, 0.18, 520);   // "ooooh"
      crowd(t + 0.9, 1.9, 0.2, 1100);   // then the roar
    },
    MILESTONE(t) {
      riser(t, 0.4, 0.12);
      const h = t + 0.4;
      kick(h); crash(h, 0.3, 1.8);
      brass(h, [N.D4, N.Fs4, N.A4], 0.18, 0.15);
      brass(h + 0.2, [N.E4, N.Gs4, N.B4], 0.18, 0.15);
      kick(h + 0.4); snare(h + 0.4, 0.35);
      brass(h + 0.4, [N.A4 / 2, N.Cs4, N.E4, N.A4], 1.5, 0.18);
      crowd(t + 0.3, 3.0, 0.22);
    }
  };

  window.EventFx = {
    setVolume(v) {
      volume = Math.max(0, Math.min(1, Number(v)));
      if (master) master.gain.value = volume;
    },
    unlock() { try { ensure(); } catch (_) {} },
    play(type) {
      try {
        if (!ensure() || volume <= 0) return;
        const fn = stingers[String(type || '').toUpperCase()];
        if (fn) fn(ctx.currentTime + 0.03);
      } catch (_) {}
    }
  };
})();
