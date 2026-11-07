/*
 * Spoken ball-by-ball commentary.
 * - Android app: phone Text-to-Speech through the window.AndroidVoice bridge.
 * - Browsers: the Web Speech API (speechSynthesis).
 * Feed it the match state with CommentaryVoice.observe(state); it speaks each new
 * commentary line once (newest-first list, as CricHeroes / manual scoring provide).
 */
(function () {
  const cfg = {
    enabled: true,     // commentaryMode is "voice" or "both"
    lang: "en-IN",
    rate: 1.0,
    pitch: 1.0,
    volume: 1.0,
    detail: true,      // also read the CricHeroes description sentence
    overSummary: true  // "End of over 5. Falcons 42 for 2."
  };
  let role = "control";
  let busyFn = () => false;
  let primed = false;
  let prevOver = null, prevKey = null;
  const seen = [];
  const queue = [];
  let pumpTimer = null;
  let lastSpokeAt = 0;

  const pick = arr => arr[Math.floor(Math.random() * arr.length)];
  const hasNative = () => !!(window.AndroidVoice && typeof window.AndroidVoice.speak === "function");
  const hasWeb = () => typeof window.speechSynthesis !== "undefined" && typeof window.SpeechSynthesisUtterance !== "undefined";

  // Browsers: the overlay in OBS can't carry speech audio, so only the control
  // panel speaks there unless the overlay URL has ?voice=1.
  function allowedHere() {
    if (hasNative()) return true;
    if (!hasWeb()) return false;
    if (role === "overlay") return /[?&]voice=1\b/.test(location.search);
    return true;
  }

  function loadSettings() {
    let s = {};
    try { s = JSON.parse(localStorage.getItem("chOverlaySettings") || "{}") || {}; } catch (_) {}
    applySettings(s);
  }

  function applySettings(s) {
    if (!s || typeof s !== "object") return;
    // Partial updates (e.g. only colours) must not reset the mode
    if (s.commentaryMode) cfg.enabled = s.commentaryMode === "voice" || s.commentaryMode === "both";
    if (s.voiceLang) cfg.lang = s.voiceLang;
    if (s.voiceRate) cfg.rate = Number(s.voiceRate) || 1;
    if (s.voicePitch) cfg.pitch = Number(s.voicePitch) || 1;
    if (s.voiceVolume !== undefined) cfg.volume = Math.max(0, Math.min(1, Number(s.voiceVolume)));
    if (s.voiceDetail !== undefined) cfg.detail = s.voiceDetail !== false;
    if (s.voiceOverSummary !== undefined) cfg.overSummary = s.voiceOverSummary !== false;
    if (!cfg.enabled) stop();
  }

  // ---------- Turning a commentary item into something worth hearing ----------
  function cleanForSpeech(t) {
    return String(t || "")
      .replace(/[\u{1F300}-\u{1FAFF}\u{2600}-\u{27BF}]/gu, "")
      .replace(/\bWd\b/gi, "wide")
      .replace(/\bNb\b/gi, "no ball")
      .replace(/\bLb\b/gi, "leg bye")
      .replace(/\b(\d+)\/(\d+)\b/g, "$1 for $2")
      .replace(/\s+/g, " ")
      .trim();
  }

  function phraseFor(item) {
    const text = cleanForSpeech(item.text);
    if (!text) return "";
    const outcome = (text.split(",").slice(1).join(",") || text);
    let shout = "";
    if (/\b(OUT|WICKET|BOWLED|CAUGHT|LBW|RUN OUT|STUMPED)\b/i.test(outcome)) {
      shout = pick(["Gone! That's a big wicket!", "OUT! The fielding side are up!", "And he has to walk back!"]);
    } else if (/\bSIX\b|\b6 runs\b/i.test(outcome)) {
      shout = pick(["That's huge! SIX!", "Into the stands! Maximum!", "Massive hit! SIX runs!"]);
    } else if (/\bFOUR\b|\b4 runs\b/i.test(outcome)) {
      shout = pick(["Cracking shot! FOUR!", "That races away to the fence!", "Beautifully timed, FOUR!"]);
    }
    let detail = cfg.detail ? cleanForSpeech(item.detail) : "";
    if (detail.length > 220) detail = detail.slice(0, 220).replace(/\s+\S*$/, "") + ".";
    return [text + ".", shout, detail].filter(Boolean).join(" ");
  }

  function overSummary(s, over) {
    const team = String(s.batting_team || s.battingTeam || "").trim();
    const score = (typeof s.score === "string" && s.score.includes("/"))
      ? s.score : (s.runs ?? 0) + "/" + (s.wickets ?? 0);
    const [r, w] = score.split("/");
    let line = `End of over ${over}. ${team ? team + ", " : ""}${r} for ${w}.`;
    const chase = s.chase_text || s.chaseText || s.equation || "";
    if (chase) line += " " + cleanForSpeech(String(chase).replace(/^.*?(need)/i, "$1")) + ".";
    return line;
  }

  // ---------- Engines ----------
  function engineBusy() {
    try {
      if (hasNative()) return window.AndroidVoice.isSpeaking();
      if (hasWeb()) return speechSynthesis.speaking || speechSynthesis.pending;
    } catch (_) {}
    return false;
  }

  function webVoice() {
    const voices = speechSynthesis.getVoices() || [];
    const want = cfg.lang.toLowerCase();
    return voices.find(v => v.lang && v.lang.toLowerCase() === want) ||
      voices.find(v => v.lang && v.lang.toLowerCase().startsWith(want.split("-")[0])) || null;
  }

  function sayNow(text) {
    if (!text) return;
    lastSpokeAt = Date.now();
    if (hasNative()) {
      try { window.AndroidVoice.speak(text, cfg.lang, cfg.rate, cfg.pitch, cfg.volume); } catch (_) {}
      return;
    }
    if (!hasWeb()) return;
    const u = new SpeechSynthesisUtterance(text);
    u.lang = cfg.lang;
    u.rate = cfg.rate;
    u.pitch = cfg.pitch;
    u.volume = cfg.volume;
    const v = webVoice();
    if (v) u.voice = v;
    speechSynthesis.speak(u);
  }

  function stop() {
    queue.length = 0;
    try {
      if (hasNative()) window.AndroidVoice.stop();
      else if (hasWeb()) speechSynthesis.cancel();
    } catch (_) {}
  }

  // ---------- Queue: one line at a time, never over a stinger, never stale ----------
  function enqueue(text) {
    if (!text) return;
    queue.push({ text, at: Date.now() });
    // If the match is moving faster than we can talk, skip to the latest balls
    while (queue.length > 3) queue.shift();
    if (!pumpTimer) pumpTimer = setInterval(pump, 400);
  }

  function pump() {
    if (!queue.length) { clearInterval(pumpTimer); pumpTimer = null; return; }
    if (!cfg.enabled || !allowedHere()) { queue.length = 0; return; }
    // Native engine reports speaking a moment after speak(); give it time
    if (Date.now() - lastSpokeAt < 700) return;
    if (engineBusy()) return;
    let busy = false;
    try { busy = !!busyFn(); } catch (_) {}
    if (busy) return;
    const next = queue.shift();
    if (Date.now() - next.at > 30000) return; // stale
    sayNow(next.text);
  }

  // ---------- Public ----------
  function keyOf(item) { return (item.ball || "") + "|" + (item.text || ""); }

  function remember(k) {
    seen.push(k);
    if (seen.length > 60) seen.shift();
  }

  function observe(s) {
    if (!s || typeof s !== "object") return;
    const list = Array.isArray(s.commentary) ? s.commentary.filter(i => i && i.text) : [];

    // Over-end summary (same innings, over number went up by 1-2)
    const k = String(s.batting_team || s.battingTeam || "").toUpperCase() + "|" + String(s.currentInnings ?? "");
    const ov = parseInt(String(s.overs ?? "").split(".")[0], 10);
    const overNow = Number.isFinite(ov) ? ov : null;
    const overEnded = primed && overNow != null && prevOver != null && k === prevKey && overNow > prevOver && overNow - prevOver <= 2;
    if (overNow != null) { prevOver = overNow; prevKey = k; }

    if (!primed || !seen.length) {
      // The first commentary a page sees (stored state, a replayed sync) is history:
      // remember it silently instead of reading out the backlog
      list.forEach(i => remember(keyOf(i)));
      primed = true;
      return;
    }

    const fresh = [];
    for (const item of list) {
      const key = keyOf(item);
      if (seen.includes(key)) break; // newest-first: everything after is old
      fresh.push(item);
    }
    fresh.reverse().forEach(item => {
      remember(keyOf(item));
      if (cfg.enabled && allowedHere()) enqueue(phraseFor(item));
    });

    if (overEnded && cfg.enabled && cfg.overSummary && allowedHere()) enqueue(overSummary(s, overNow));
  }

  function test(text) {
    const line = text || phraseFor({ text: "Kumar to Sharma, FOUR", detail: "Short and wide, cut hard through point." });
    // A test is the operator's tap, which also unlocks browser speech
    sayNow(line);
    return line;
  }

  window.CommentaryVoice = {
    attach(opts) {
      role = (opts && opts.role) || role;
      if (opts && typeof opts.busy === "function") busyFn = opts.busy;
      loadSettings();
    },
    applySettings, observe, stop, test,
    available: () => hasNative() || hasWeb(),
    engine: () => hasNative() ? "phone" : (hasWeb() ? "browser" : "none"),
    phraseFor, overSummary, _cfg: cfg
  };
  // Warm up the browser voice list (it loads asynchronously)
  try { if (hasWeb()) speechSynthesis.getVoices(); } catch (_) {}
})();
