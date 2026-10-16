/**
 * Web Audio API Sound Synthesizer for Cricket Broadcast Overlay
 * Synthesizes boundary fanfares, wicket alerts, and sound effects natively without external files.
 */
class SoundService {
  constructor() {
    this.ctx = null;
    this.enabled = true;
  }

  init() {
    if (!this.ctx && (window.AudioContext || window.webkitAudioContext)) {
      const AudioCtx = window.AudioContext || window.webkitAudioContext;
      this.ctx = new AudioCtx();
    }
    if (this.ctx && this.ctx.state === 'suspended') {
      this.ctx.resume();
    }
  }

  playFour() {
    if (!this.enabled) return;
    this.init();
    if (!this.ctx) return;

    // Upbeat brass-like arpeggio fanfare (C5, E5, G5, C6)
    const notes = [523.25, 659.25, 783.99, 1046.50];
    const now = this.ctx.currentTime;
    
    notes.forEach((freq, idx) => {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      
      osc.type = 'triangle';
      osc.frequency.setValueAtTime(freq, now + idx * 0.12);
      
      gain.gain.setValueAtTime(0, now + idx * 0.12);
      gain.gain.linearRampToValueAtTime(0.25, now + idx * 0.12 + 0.02);
      gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.12 + 0.35);
      
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      
      osc.start(now + idx * 0.12);
      osc.stop(now + idx * 0.12 + 0.38);
    });
  }

  playSix() {
    if (!this.enabled) return;
    this.init();
    if (!this.ctx) return;

    // Grand cinematic triumph sound with rising sweep
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(300, now);
    osc.frequency.exponentialRampToValueAtTime(880, now + 0.4);

    gain.gain.setValueAtTime(0.3, now);
    gain.gain.exponentialRampToValueAtTime(0.01, now + 0.8);

    osc.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start(now);
    osc.stop(now + 0.85);

    // Complementary chime
    setTimeout(() => {
      if (!this.ctx) return;
      const chime = this.ctx.createOscillator();
      const cGain = this.ctx.createGain();
      const cNow = this.ctx.currentTime;
      chime.type = 'sine';
      chime.frequency.setValueAtTime(1174.66, cNow); // D6
      cGain.gain.setValueAtTime(0.2, cNow);
      cGain.gain.exponentialRampToValueAtTime(0.001, cNow + 0.5);
      chime.connect(cGain);
      cGain.connect(this.ctx.destination);
      chime.start(cNow);
      chime.stop(cNow + 0.55);
    }, 200);
  }

  playWicket() {
    if (!this.enabled) return;
    this.init();
    if (!this.ctx) return;

    // Deep dramatic impact bass and siren hit
    const now = this.ctx.currentTime;
    const osc = this.ctx.createOscillator();
    const gain = this.ctx.createGain();

    osc.type = 'sawtooth';
    osc.frequency.setValueAtTime(220, now);
    osc.frequency.linearRampToValueAtTime(110, now + 0.5);

    gain.gain.setValueAtTime(0.35, now);
    gain.gain.exponentialRampToValueAtTime(0.001, now + 0.6);

    osc.connect(gain);
    gain.connect(this.ctx.destination);

    osc.start(now);
    osc.stop(now + 0.65);
  }

  playMilestone() {
    if (!this.enabled) return;
    this.init();
    if (!this.ctx) return;

    // Celebratory chime
    const now = this.ctx.currentTime;
    [440, 554.37, 659.25, 880].forEach((freq, idx) => {
      const osc = this.ctx.createOscillator();
      const gain = this.ctx.createGain();
      osc.type = 'sine';
      osc.frequency.setValueAtTime(freq, now + idx * 0.08);
      gain.gain.setValueAtTime(0.2, now + idx * 0.08);
      gain.gain.exponentialRampToValueAtTime(0.001, now + idx * 0.08 + 0.4);
      osc.connect(gain);
      gain.connect(this.ctx.destination);
      osc.start(now + idx * 0.08);
      osc.stop(now + idx * 0.08 + 0.45);
    });
  }
}

window.soundService = new SoundService();
