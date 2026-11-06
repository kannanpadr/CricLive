/**
 * ScoreTracker — builds a full innings batting / bowling card.
 *
 * Sources (best first):
 *  1. state.fullScorecard — the complete CricHeroes scorecard read by the phone's 4G reader
 *     (all batters with dismissals, all bowlers). Live numbers for the batters at the
 *     crease and the current bowler are merged on top so it is never behind.
 *  2. A local tracker that remembers every batter / bowler seen in the score updates of
 *     this innings (manual scoring, or before the CricHeroes card has loaded).
 *
 * The tracker lives in localStorage so the control panel and the overlay (different
 * WebViews in the app) build the same card.
 */
(function () {
  const KEY = "chInningsTracker";
  const PLACEHOLDER = /^(next batter|batter \d|bowler|—|-|)$/i;

  function load() {
    try { return JSON.parse(localStorage.getItem(KEY) || "null"); } catch (_) { return null; }
  }
  function save(t) {
    try { localStorage.setItem(KEY, JSON.stringify(t)); } catch (_) {}
  }
  function clean(name) {
    return String(name || "").replace(/\*/g, "").trim();
  }
  function num(v) {
    const n = Number(v);
    return Number.isFinite(n) ? n : 0;
  }
  function wicketsOf(s) {
    let w = Number(s.wickets);
    if (!Number.isFinite(w)) w = parseInt(String(s.score || "").split("/")[1], 10);
    return Number.isFinite(w) ? w : 0;
  }
  function inningsKey(s) {
    return String(s.batting_team || s.battingTeam || "").toUpperCase() + "|" + String(s.currentInnings ?? "");
  }

  function ingest(s) {
    if (!s || typeof s !== "object") return;
    const key = inningsKey(s);
    let t = load();
    if (!t || t.key !== key) {
      t = { key, batters: {}, order: [], bowlers: {}, bowlerOrder: [], wickets: wicketsOf(s) };
    }
    const wk = wicketsOf(s);
    const live = new Set();

    (Array.isArray(s.batters) ? s.batters : []).forEach(b => {
      const name = clean(b && b.name);
      if (!name || PLACEHOLDER.test(name)) return;
      live.add(name);
      if (!t.batters[name]) {
        t.batters[name] = { name, dismissal: "not out", out: false };
        t.order.push(name);
      }
      Object.assign(t.batters[name], {
        runs: num(b.runs), balls: num(b.balls), fours: num(b.fours), sixes: num(b.sixes),
        out: false, dismissal: "not out"
      });
    });

    // A batter who was at the crease and has gone while wickets rose is out
    if (wk > t.wickets) {
      const bowler = clean(s.bowler && s.bowler.name);
      Object.values(t.batters).forEach(b => {
        if (!b.out && b.atCrease && !live.has(b.name)) {
          b.out = true;
          b.dismissal = bowler ? "b " + bowler : "out";
        }
      });
    }
    Object.values(t.batters).forEach(b => { b.atCrease = live.has(b.name); });
    t.wickets = wk;

    const bw = s.bowler;
    const bName = clean(bw && bw.name);
    if (bName && !PLACEHOLDER.test(bName)) {
      if (!t.bowlers[bName]) { t.bowlers[bName] = { name: bName }; t.bowlerOrder.push(bName); }
      Object.assign(t.bowlers[bName], {
        overs: String(bw.overs ?? "0"), maidens: num(bw.maidens), runs: num(bw.runs),
        wickets: num(bw.wickets), eco: bw.eco || bw.economy || ""
      });
    }
    save(t);
  }

  function pickInnings(full, s) {
    const inns = (full && Array.isArray(full.innings)) ? full.innings.filter(i => i && i.batting && i.batting.length) : [];
    if (!inns.length) return null;
    const liveNames = (Array.isArray(s.batters) ? s.batters : []).map(b => clean(b && b.name).toLowerCase()).filter(Boolean);
    const matches = inn => inn.batting.some(b => liveNames.includes(clean(b.name).toLowerCase()));
    for (let i = inns.length - 1; i >= 0; i--) if (matches(inns[i])) return inns[i];
    return Number(s.currentInnings) === 1 ? inns[0] : inns[inns.length - 1];
  }

  function sr(runs, balls) {
    return balls > 0 ? ((runs / balls) * 100).toFixed(2) : "0.00";
  }

  /** Returns { batting:[], bowling:[], extras, extrasRuns, source } for the current innings. */
  function build(s) {
    s = s || {};
    const live = (Array.isArray(s.batters) ? s.batters : []).filter(b => b && clean(b.name));
    const liveBowler = s.bowler && clean(s.bowler.name) ? s.bowler : null;
    const strikerName = clean((live.find(b => b.striker || b.isStriker) || {}).name).toLowerCase();
    const inn = pickInnings(s.fullScorecard, s);

    let batting, bowling, extras = "", extrasRuns = "", source;
    if (inn) {
      source = "cricheroes";
      batting = inn.batting.map(b => ({ ...b }));
      bowling = (inn.bowling || []).map(b => ({ ...b }));
      extras = inn.extras || "";
      extrasRuns = inn.extrasRuns || "";
    } else {
      source = "tracker";
      const t = load();
      const same = t && t.key === inningsKey(s);
      batting = same ? t.order.map(n => t.batters[n]).map(b => ({
        name: b.name, dismissal: b.out ? b.dismissal : "not out",
        runs: b.runs, balls: b.balls, fours: b.fours, sixes: b.sixes
      })) : [];
      bowling = same ? t.bowlerOrder.map(n => ({ ...t.bowlers[n] })) : [];
    }

    // Merge the freshest live numbers on top
    live.forEach(lb => {
      const n = clean(lb.name);
      if (PLACEHOLDER.test(n)) return;
      let row = batting.find(b => clean(b.name).toLowerCase() === n.toLowerCase());
      if (!row) { row = { name: n, dismissal: "not out" }; batting.push(row); }
      Object.assign(row, { runs: num(lb.runs), balls: num(lb.balls), fours: num(lb.fours ?? row.fours), sixes: num(lb.sixes ?? row.sixes) });
      if (!row.dismissal || /^not out$/i.test(row.dismissal)) row.dismissal = "not out";
    });
    if (liveBowler) {
      const n = clean(liveBowler.name);
      if (!PLACEHOLDER.test(n)) {
        let row = bowling.find(b => clean(b.name).toLowerCase() === n.toLowerCase());
        if (!row) { row = { name: n }; bowling.push(row); }
        if (source === "tracker" || liveBowler.overs !== undefined) {
          Object.assign(row, {
            overs: String(liveBowler.overs ?? row.overs ?? "0"),
            maidens: num(liveBowler.maidens ?? row.maidens),
            runs: num(liveBowler.runs ?? row.runs),
            wickets: num(liveBowler.wickets ?? row.wickets),
            eco: liveBowler.eco || liveBowler.economy || row.eco || ""
          });
        }
        row.current = true;
      }
    }

    batting.forEach(b => {
      b.runs = num(b.runs); b.balls = num(b.balls);
      b.sr = b.sr || sr(b.runs, b.balls);
      b.notOut = /not out/i.test(b.dismissal || "");
      b.onStrike = clean(b.name).toLowerCase() === strikerName;
    });
    return { batting, bowling, extras, extrasRuns, source };
  }

  window.ScoreTracker = { ingest, build, reset() { try { localStorage.removeItem(KEY); } catch (_) {} } };
})();
