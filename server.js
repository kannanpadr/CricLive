const express = require('express');
const cors = require('cors');
const path = require('path');
const http = require('http');
const { parseUrlMetadata, scrapeCricHeroesUrl, getPageDebugInfo, processExtractedData } = require('./cricheroes-service');

const app = express();
const PORT = process.env.PORT || 3000;

app.use(cors());
app.use(express.json());
app.use(express.static(path.join(__dirname, 'public')));

// Explicit page routes
app.get('/', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});
app.get('/health', (req, res) => {
  res.json({ status: 'ok', uptime: process.uptime() });
});
app.get('/api/debug-browser', async (req, res) => {
  res.json(await getPageDebugInfo());
});
app.get('/admin', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'index.html'));
});
app.get('/overlay', (req, res) => {
  res.sendFile(path.join(__dirname, 'public', 'overlay.html'));
});

// In-Memory state for the active match
let matchState = {
  matchId: "27644870",
  matchUrl: "https://cricheroes.com/scorecard/27644870/live",
  tournament: "IISC Champions League – ICL 2026",
  status: "live", // "not_started" | "live" | "innings_break" | "completed"
  statusText: "LIVE · 2nd Innings",
  sourceMode: "url_sync", // "url_sync" | "simulation" | "manual"
  pollingInterval: 4, // seconds
  lastUpdated: new Date().toISOString(),
  isStale: false,
  autoUrlSync: true,
  errorCount: 0,
  lastErrorMessage: null,

  // TEAM 1: First Batting Team
  teamA: {
    name: "Admin Champions Bangalore",
    shortName: "ACB",
    score: 128,
    wickets: 3,
    overs: 15.2,
    isBatting: true,
    color: "#e63946"
  },

  // TEAM 2: Chasing Team
  teamB: {
    name: "TSS Cricket",
    shortName: "TSS",
    score: 0,
    wickets: 0,
    overs: 0.0,
    isBatting: false,
    color: "#2a9d8f"
  },

  // Current Batting team and targets
  battingTeam: "ACB",
  bowlingTeam: "TSS",
  currentInnings: 1,
  totalOvers: 20,
  target: null,
  requiredRuns: null,
  ballsRemaining: null,
  crr: "8.35",
  rrr: null,

  // Current Batters on crease
  striker: {
    name: "Rahul Sharma",
    runs: 48,
    balls: 32,
    fours: 5,
    sixes: 2,
    strikeRate: "150.0",
    isStriker: true
  },
  nonStriker: {
    name: "Arun Kumar",
    runs: 22,
    balls: 18,
    fours: 2,
    sixes: 0,
    strikeRate: "122.2",
    isStriker: false
  },

  // Current Bowler
  bowler: {
    name: "Vikram Sen",
    overs: "3.2",
    maidens: 0,
    runs: 26,
    wickets: 2,
    economy: "7.80"
  },

  // Recent deliveries of current over
  thisOver: ["1", "4", "0", "2", "6", "1"],
  recentBalls: ["1", "4", "0", "2", "6", "1"],

  // Active trigger animation
  activeAnimation: null,

  // Overlay settings
  overlaySettings: {
    visible: true,
    layout: "full",
    theme: "bluegold",
    primary: "#087cff",
    secondary: "#ffc107",
    primaryColor: "#087cff",
    secondaryColor: "#ffc107",
    opacity: "95",
    showLogos: true,
    showTarget: true,
    displayOptions: {
      tournament: true,
      venue: true,
      crr: true,
      projected: true,
      target: true,
      batters: true,
      bowler: true,
      thisover: true
    }
  },

  matchResult: null
};

// History for undo
const matchHistory = [];

function pushHistory() {
  if (matchHistory.length > 25) matchHistory.shift();
  matchHistory.push(JSON.parse(JSON.stringify(matchState)));
}

function getBattingTeam() {
  if (matchState.currentInnings === 2) {
    return matchState.teamB || matchState.teamA;
  }
  if (matchState.currentInnings === 1) {
    return matchState.teamA;
  }
  if (matchState.teamB?.isBatting) return matchState.teamB;
  if (matchState.teamA?.isBatting) return matchState.teamA;
  if (matchState.battingTeam && matchState.teamB && 
     (matchState.battingTeam === matchState.teamB.name || matchState.battingTeam === matchState.teamB.shortName)) {
    return matchState.teamB;
  }
  return matchState.teamA;
}

function getBowlingTeam() {
  const batting = getBattingTeam();
  return batting === matchState.teamA ? (matchState.teamB || matchState.teamA) : matchState.teamA;
}

function recalculateStats() {
  // If operator has not forced an innings (auto mode), dynamically evaluate from scorecard state:
  if (matchState.operatorInnings === undefined) {
    const hasTeamBBatted = Boolean(matchState.teamB && (matchState.teamB.score > 0 || matchState.teamB.wickets > 0 || matchState.teamB.overs > 0));
    const isTeamAFinished = Boolean(matchState.teamA && (matchState.teamA.wickets >= 10 || (matchState.teamA.overs >= (matchState.totalOvers || 20) && matchState.teamA.overs > 0)));
    const hasTarget = Boolean(matchState.target && Number(matchState.target) > 0);

    if (hasTeamBBatted || isTeamAFinished || hasTarget) {
      matchState.currentInnings = 2;
      if (matchState.teamA) matchState.teamA.isBatting = false;
      if (matchState.teamB) {
        matchState.teamB.isBatting = true;
        matchState.battingTeam = matchState.teamB.name || matchState.teamB.shortName;
      }
      if (matchState.teamA) matchState.bowlingTeam = matchState.teamA.name || matchState.teamA.shortName;
      if (!matchState.target && matchState.teamA) {
        matchState.target = (matchState.teamA.score || 0) + 1;
      }
    } else {
      matchState.currentInnings = 1;
      if (matchState.teamA) {
        matchState.teamA.isBatting = true;
        matchState.battingTeam = matchState.teamA.name || matchState.teamA.shortName;
      }
      if (matchState.teamB) {
        matchState.teamB.isBatting = false;
        matchState.bowlingTeam = matchState.teamB.name || matchState.teamB.shortName;
      }
      matchState.target = null;
    }
  } else {
    // Operator explicitly selected an innings
    if (matchState.currentInnings === 2) {
      if (matchState.teamA) matchState.teamA.isBatting = false;
      if (matchState.teamB) {
        matchState.teamB.isBatting = true;
        matchState.battingTeam = matchState.teamB.name || matchState.teamB.shortName;
      }
      if (matchState.teamA) {
        matchState.bowlingTeam = matchState.teamA.name || matchState.teamA.shortName;
        if (!matchState.target) {
          matchState.target = (matchState.teamA.score || 0) + 1;
        }
      }
    } else {
      if (matchState.teamA) {
        matchState.teamA.isBatting = true;
        matchState.battingTeam = matchState.teamA.name || matchState.teamA.shortName;
      }
      if (matchState.teamB) {
        matchState.teamB.isBatting = false;
        matchState.bowlingTeam = matchState.teamB.name || matchState.teamB.shortName;
      }
      matchState.target = null;
    }
  }

  const batting = getBattingTeam();
  const bowling = getBowlingTeam();
  
  const [ovPart, bPart] = String(batting?.overs || "0.0").split('.').map(Number);
  const totalBalls = (ovPart || 0) * 6 + (bPart || 0);
  
  if (totalBalls > 0) {
    matchState.crr = (((batting?.score || 0) / totalBalls) * 6).toFixed(2);
  } else {
    matchState.crr = "0.00";
  }

  if (matchState.currentInnings === 2 && matchState.target) {
    matchState.requiredRuns = Math.max(0, matchState.target - (batting?.score || 0));
    const maxBalls = (matchState.totalOvers || 20) * 6;
    matchState.ballsRemaining = Math.max(0, maxBalls - totalBalls);
    
    if (matchState.ballsRemaining > 0 && matchState.requiredRuns > 0) {
      matchState.rrr = ((matchState.requiredRuns / matchState.ballsRemaining) * 6).toFixed(2);
    } else if (matchState.requiredRuns <= 0) {
      matchState.rrr = "0.00";
      matchState.status = "completed";
      matchState.statusText = "COMPLETED";
      matchState.matchResult = `${batting?.name || 'Chasing Team'} WON BY ${10 - (batting?.wickets || 0)} WICKETS!`;
    } else {
      matchState.rrr = "—";
      matchState.status = "completed";
      matchState.statusText = "COMPLETED";
      matchState.matchResult = `${bowling?.name || 'Bowling Team'} WON BY ${matchState.requiredRuns - 1} RUNS!`;
    }
  } else {
    // 1st Innings: Target and chase are not needed
    matchState.target = null;
    matchState.requiredRuns = null;
    matchState.ballsRemaining = null;
    matchState.rrr = null;
  }

  if (matchState.striker && matchState.striker.balls > 0) {
    matchState.striker.strikeRate = ((matchState.striker.runs / matchState.striker.balls) * 100).toFixed(1);
  }
  if (matchState.nonStriker && matchState.nonStriker.balls > 0) {
    matchState.nonStriker.strikeRate = ((matchState.nonStriker.runs / matchState.nonStriker.balls) * 100).toFixed(1);
  }

  matchState.lastUpdated = new Date().toISOString();
  matchState.isStale = false;
}

// --- API Endpoints ---

function getEnrichedState() {
  const batting = getBattingTeam();
  const bowling = getBowlingTeam();
  
  const crrNum = parseFloat(matchState.crr) || 0;
  const projected = Math.round(crrNum * (matchState.totalOvers || 20));
  const isSecondInnings = Boolean(matchState.currentInnings === 2);

  let chase = matchState.matchResult || "";
  if (!chase) {
    if (isSecondInnings) {
      chase = matchState.chase || `${batting?.name || batting?.shortName || 'Chasing team'} need ${matchState.requiredRuns} runs in ${matchState.ballsRemaining} balls`;
    } else {
      chase = `${batting?.name || batting?.shortName || 'Batting team'} 1st Innings`;
    }
  }

  const scoreStr = `${batting?.score ?? 0}/${batting?.wickets ?? 0}`;
  const oversStr = batting?.overs !== undefined ? String(batting.overs) : "0.0";

  return {
    ...matchState,
    tournament: matchState.tournament || "RBCL UNDER-14",
    venue: matchState.venue || "Bengaluru, Karnataka · RBCL Ground",
    runs: batting?.score ?? 0,
    wickets: batting?.wickets ?? 0,
    score: scoreStr,
    overs: oversStr,
    batting_team: batting?.name || matchState.battingTeam,
    bowling_team: bowling?.name || matchState.bowlingTeam,
    target: isSecondInnings ? matchState.target : null,
    totalOvers: matchState.totalOvers || 20,
    target_overs: matchState.totalOvers || 20,
    projected: matchState.projected || projected,
    chase: matchState.chase || chase,
    batters: [
      matchState.striker ? { ...matchState.striker, striker: true } : { name: "—", runs: 0, balls: 0, fours: 0, sixes: 0, striker: true },
      matchState.nonStriker ? { ...matchState.nonStriker, striker: false } : { name: "—", runs: 0, balls: 0, fours: 0, sixes: 0, striker: false }
    ],
    this_over: matchState.thisOver || matchState.recentBalls || [],
    status: matchState.status === 'live' ? 'LIVE' : (matchState.status ? matchState.status.toUpperCase() : 'LIVE'),
    updated: new Date(matchState.lastUpdated || Date.now()).toLocaleTimeString()
  };
}

// 1. Get Live Scorecard State (supports /api/score and /score)
app.get(['/api/score', '/score'], (req, res) => {
  res.json(getEnrichedState());
});

app.post(['/api/score', '/score'], (req, res) => {
  if (req.body) {
    pushHistory();
    const data = req.body;
    if (data.tournament) matchState.tournament = data.tournament;
    if (data.venue) matchState.venue = data.venue;
    if (data.runs !== undefined) {
      const b = getBattingTeam();
      if (b) b.score = Number(data.runs);
    }
    if (data.wickets !== undefined) {
      const b = getBattingTeam();
      if (b) b.wickets = Number(data.wickets);
    }
    if (data.overs !== undefined) {
      const b = getBattingTeam();
      if (b) b.overs = data.overs;
    }
    if (data.batting_team) {
      const b = getBattingTeam();
      if (b) b.name = data.batting_team;
    }
    if (data.bowling_team) {
      const bo = getBowlingTeam();
      if (bo) bo.name = data.bowling_team;
    }
    if (data.batters && Array.isArray(data.batters)) {
      if (data.batters[0]) matchState.striker = { ...matchState.striker, ...data.batters[0] };
      if (data.batters[1]) matchState.nonStriker = { ...matchState.nonStriker, ...data.batters[1] };
    }
    if (data.bowler) {
      matchState.bowler = { ...matchState.bowler, ...data.bowler };
    }
    if (data.this_over && Array.isArray(data.this_over)) {
      matchState.thisOver = data.this_over;
    }
    matchState.lastUpdated = new Date().toISOString();
    recalculateStats();
    res.json({ success: true, state: getEnrichedState() });
  } else {
    res.status(400).json({ error: "Missing body" });
  }
});

// 2. AUTO-CONFIGURE FROM CRICHEROES FULL URL (FR-01, FR-02)
app.post('/api/auto-configure', async (req, res) => {
  const { url } = req.body;
  if (!url) {
    return res.status(400).json({ error: "Please provide a valid CricHeroes URL" });
  }

  try {
    console.log(`[Auto-Config] Processing CricHeroes URL: ${url}`);
    pushHistory();

    const scrapedData = await scrapeCricHeroesUrl(url);

    // Merge scraped data into matchState (dynamic scorecard detection)
    delete matchState.operatorInnings;
    delete matchState.operatorTotalOvers;
    matchState = {
      ...matchState,
      ...scrapedData,
      matchUrl: url,
      lastUpdated: new Date().toISOString(),
      isStale: false
    };

    recalculateStats();

    console.log(`[Auto-Config] Success! Configured: ${matchState.tournament} · ${matchState.teamA.shortName} (${matchState.teamA.score}/${matchState.teamA.wickets}) vs ${matchState.teamB.shortName} (${matchState.teamB.score}/${matchState.teamB.wickets})`);

    res.json({
      success: true,
      message: `Match auto-configured: ${matchState.tournament} (${matchState.teamA.shortName} vs ${matchState.teamB.shortName})`,
      state: matchState
    });
  } catch (err) {
    console.error("[Auto-Config] Error:", err);
    res.status(500).json({ error: "Failed to auto-configure match: " + err.message });
  }
});

// 2b. MOBILE-ASSISTED SYNC (Bypasses Cloudflare using user's mobile 4G/5G connection)
app.post(['/api/mobile-sync', '/api/sync-score'], (req, res) => {
  const { url, domData, state } = req.body;
  if (!url && !domData && !state) {
    return res.status(400).json({ error: "Missing match URL, domData, or state payload" });
  }

  try {
    let parsed = null;
    const matchUrl = url || matchState.matchUrl;

    if (domData && matchUrl) {
      const meta = parseUrlMetadata(matchUrl);
      parsed = processExtractedData(domData, meta, matchUrl);
    } else if (state) {
      parsed = state;
    }

    if (parsed) {
      pushHistory();
      delete matchState.operatorInnings;
      delete matchState.operatorTotalOvers;

      matchState = {
        ...matchState,
        ...parsed,
        matchUrl: matchUrl,
        lastUpdated: new Date().toISOString(),
        isStale: false,
        sourceMode: 'mobile_sync'
      };

      recalculateStats();
      scheduleStaleCheck();

      console.log(`[Mobile-Sync] ✓ Real-time update from phone: ${matchState.tournament} · ${matchState.teamA?.shortName || 'A'} (${matchState.teamA?.score}/${matchState.teamA?.wickets}) vs ${matchState.teamB?.shortName || 'B'} (${matchState.teamB?.score}/${matchState.teamB?.wickets})`);

      return res.json({
        success: true,
        message: "Live score synced successfully from mobile device",
        state: getEnrichedState()
      });
    }

    res.status(400).json({ error: "Could not parse mobile sync data" });
  } catch (err) {
    console.error("[Mobile-Sync Error]:", err);
    res.status(500).json({ error: "Failed to process mobile sync: " + err.message });
  }
});

// 3. Update Overlay Settings
app.post('/api/overlay-settings', (req, res) => {
  if (req.body) {
    const s = req.body;
    if (s.primaryColor && !s.primary) s.primary = s.primaryColor;
    if (s.secondaryColor && !s.secondary) s.secondary = s.secondaryColor;
    if (s.primary && !s.primaryColor) s.primaryColor = s.primary;
    if (s.secondary && !s.secondaryColor) s.secondaryColor = s.secondary;
    matchState.overlaySettings = { ...matchState.overlaySettings, ...s };
    res.json({ success: true, settings: matchState.overlaySettings });
  } else {
    res.status(400).json({ error: "Missing payload" });
  }
});

// 4. Trigger Instant Match Event
app.post('/api/trigger-event', (req, res) => {
  const { type, message } = req.body;
  if (!type) {
    return res.status(400).json({ error: "Missing event type" });
  }
  matchState.activeAnimation = {
    type,
    message: message || type,
    timestamp: Date.now()
  };
  res.json({ success: true, activeAnimation: matchState.activeAnimation });
});

// 5. Update Configuration manually
app.post(['/api/config', '/config'], (req, res) => {
  pushHistory();
  const { matchId, matchUrl, tournament, venue, teamA, teamB, sourceMode, pollingInterval, totalOvers, autoUrlSync, currentInnings, target } = req.body;
  if (matchId) matchState.matchId = matchId;
  if (matchUrl) matchState.matchUrl = matchUrl;
  if (tournament) matchState.tournament = tournament;
  if (venue) matchState.venue = venue;
  if (teamA) matchState.teamA = { ...matchState.teamA, ...teamA };
  if (teamB) matchState.teamB = { ...matchState.teamB, ...teamB };
  if (sourceMode) matchState.sourceMode = sourceMode;
  if (pollingInterval) matchState.pollingInterval = Number(pollingInterval);
  if (totalOvers !== undefined && totalOvers !== '' && Number(totalOvers) > 0) {
    matchState.totalOvers = Number(totalOvers);
    matchState.operatorTotalOvers = Number(totalOvers);
  } else if (totalOvers === '' || totalOvers === 0 || totalOvers === 'auto') {
    delete matchState.operatorTotalOvers;
  }
  if (autoUrlSync !== undefined) matchState.autoUrlSync = Boolean(autoUrlSync);
  if (currentInnings !== undefined && currentInnings !== 'auto' && currentInnings !== '') {
    matchState.currentInnings = Number(currentInnings);
    matchState.operatorInnings = Number(currentInnings);
  } else if (currentInnings === 'auto' || currentInnings === '') {
    delete matchState.operatorInnings;
  }
  if (target !== undefined) matchState.target = target ? Number(target) : null;
  recalculateStats();
  scheduleStaleCheck();
  res.json({ success: true, state: getEnrichedState() });
});

// 6. Manual Ball-by-Ball Update
app.post('/api/ball', (req, res) => {
  pushHistory();
  const { outcome, runs = 0 } = req.body;
  
  const batting = getBattingTeam();

  if (outcome === '0') {
    matchState.thisOver.push('0');
    if (matchState.striker) matchState.striker.balls += 1;
    batting.overs = incrementOvers(batting.overs);
  } else if (outcome === '1' || outcome === '2' || outcome === '3') {
    const r = parseInt(outcome, 10);
    batting.score += r;
    if (matchState.striker) {
      matchState.striker.runs += r;
      matchState.striker.balls += 1;
    }
    if (matchState.bowler) matchState.bowler.runs += r;
    batting.overs = incrementOvers(batting.overs);
    matchState.thisOver.push(outcome);

    if (r % 2 !== 0) swapStrikers();
  } else if (outcome === '4') {
    batting.score += 4;
    if (matchState.striker) {
      matchState.striker.runs += 4;
      matchState.striker.balls += 1;
      matchState.striker.fours += 1;
    }
    if (matchState.bowler) matchState.bowler.runs += 4;
    batting.overs = incrementOvers(batting.overs);
    matchState.thisOver.push('4');
    matchState.activeAnimation = {
      type: 'FOUR',
      message: `${matchState.striker ? matchState.striker.name.toUpperCase() : 'BATTER'} SMASHES A BOUNDARY!`,
      timestamp: Date.now()
    };
  } else if (outcome === '6') {
    batting.score += 6;
    if (matchState.striker) {
      matchState.striker.runs += 6;
      matchState.striker.balls += 1;
      matchState.striker.sixes += 1;
    }
    if (matchState.bowler) matchState.bowler.runs += 6;
    batting.overs = incrementOvers(batting.overs);
    matchState.thisOver.push('6');
    matchState.activeAnimation = {
      type: 'SIX',
      message: `MAXIMUM! HUGE SIX BY ${matchState.striker ? matchState.striker.name.toUpperCase() : 'BATTER'}!`,
      timestamp: Date.now()
    };
  } else if (outcome === 'W') {
    batting.wickets += 1;
    if (matchState.striker) matchState.striker.balls += 1;
    if (matchState.bowler) matchState.bowler.wickets += 1;
    batting.overs = incrementOvers(batting.overs);
    matchState.thisOver.push('W');
    matchState.activeAnimation = {
      type: 'WICKET',
      message: `WICKET! ${matchState.striker ? matchState.striker.name.toUpperCase() : 'BATTER'} IS OUT!`,
      timestamp: Date.now()
    };
    matchState.striker = {
      name: "New Batter",
      runs: 0,
      balls: 0,
      fours: 0,
      sixes: 0,
      strikeRate: "0.0",
      isStriker: true
    };
  } else if (outcome === 'Wd' || outcome === 'Nb') {
    batting.score += 1;
    if (matchState.bowler) matchState.bowler.runs += 1;
    matchState.thisOver.push(outcome);
  }

  // Check 50 milestone
  if (matchState.striker && matchState.striker.runs >= 50 && matchState.striker.runs < 54 && matchState.striker.runs - runs < 50) {
    matchState.activeAnimation = {
      type: 'MILESTONE',
      message: `SPLENDID 50! ${matchState.striker.name.toUpperCase()} REACHES HALF-CENTURY!`,
      timestamp: Date.now()
    };
  }

  // Check over completion
  const [, bPart] = batting.overs.toString().split('.').map(Number);
  if (bPart === 0 && matchState.thisOver.length > 0) {
    swapStrikers();
    if (matchState.bowler) matchState.bowler.overs = batting.overs.toString();
    matchState.recentBalls = [...matchState.thisOver];
    matchState.thisOver = [];
  }

  recalculateStats();
  res.json({ success: true, state: matchState });
});

// 7. Undo
app.post('/api/undo', (req, res) => {
  if (matchHistory.length > 0) {
    matchState = matchHistory.pop();
    recalculateStats();
    res.json({ success: true, state: matchState });
  } else {
    res.status(400).json({ error: "No history to undo" });
  }
});

// 8. Reset Reference Match 27644870
app.post('/api/reset-reference', (req, res) => {
  pushHistory();
  matchState.matchId = "27644870";
  matchState.matchUrl = "https://cricheroes.com/scorecard/27644870/live";
  matchState.tournament = "IISC Champions League – ICL 2026";
  matchState.status = "live";
  matchState.statusText = "LIVE · 2nd Innings";
  matchState.teamA = {
    name: "Admin Champions Bangalore",
    shortName: "ACB",
    score: 164,
    wickets: 6,
    overs: 20.0,
    isBatting: false,
    color: "#e63946"
  };
  matchState.teamB = {
    name: "TSS Cricket",
    shortName: "TSS",
    score: 128,
    wickets: 3,
    overs: 15.2,
    isBatting: true,
    color: "#2a9d8f"
  };
  matchState.battingTeam = "TSS";
  matchState.bowlingTeam = "ACB";
  matchState.currentInnings = 1;
  matchState.target = null;
  matchState.striker = {
    name: "Rahul Sharma",
    runs: 48,
    balls: 32,
    fours: 5,
    sixes: 2,
    strikeRate: "150.0",
    isStriker: true
  };
  matchState.nonStriker = {
    name: "Arun Kumar",
    runs: 22,
    balls: 18,
    fours: 2,
    sixes: 0,
    strikeRate: "122.2",
    isStriker: false
  };
  matchState.bowler = {
    name: "Vikram Sen",
    overs: "3.2",
    maidens: 0,
    runs: 26,
    wickets: 2,
    economy: "7.80"
  };
  matchState.thisOver = ["1", "4", "0", "2", "6", "1"];
  matchState.recentBalls = ["1", "4", "0", "2", "6", "1"];
  recalculateStats();
  res.json({ success: true, state: matchState });
});

function incrementOvers(ov) {
  let [o, b] = ov.toString().split('.').map(Number);
  o = o || 0;
  b = b || 0;
  b += 1;
  if (b >= 6) {
    o += 1;
    b = 0;
  }
  return parseFloat(`${o}.${b}`);
}

function swapStrikers() {
  if (!matchState.striker || !matchState.nonStriker) return;
  const temp = { ...matchState.striker };
  matchState.striker = { ...matchState.nonStriker, isStriker: true };
  matchState.nonStriker = { ...temp, isStriker: false };
}

// Background auto URL syncer if enabled
let isScrapingInProgress = false;
function startAutoSync() {
  if (global.autoSyncTimeout) clearTimeout(global.autoSyncTimeout);
  if (global.autoSyncInterval) clearInterval(global.autoSyncInterval);

  const runSync = async () => {
    if (matchState.autoUrlSync && matchState.matchUrl && !isScrapingInProgress) {
      isScrapingInProgress = true;
      try {
        const freshData = await scrapeCricHeroesUrl(matchState.matchUrl);
        if (freshData && freshData.statusText !== 'LIVE · AWAITING DATA') {
          const forcedInnings = matchState.operatorInnings;
          const forcedOvers = matchState.operatorTotalOvers;
          matchState = { ...matchState, ...freshData };
          if (forcedInnings !== undefined) {
            matchState.currentInnings = forcedInnings;
          }
          if (forcedOvers !== undefined) {
            matchState.totalOvers = forcedOvers;
          }
          recalculateStats();
          scheduleStaleCheck();
        }
      } catch (e) {
        console.warn("[Background Sync] Error:", e.message);
      } finally {
        isScrapingInProgress = false;
      }
    }
    const intervalSec = Math.max(5, matchState.pollingInterval || 5);
    global.autoSyncTimeout = setTimeout(runSync, intervalSec * 1000);
  };

  global.autoSyncTimeout = setTimeout(runSync, 2000);
}

// Stale data detection: mark stale if no update received for > 25 seconds
let staleTimeout;
function scheduleStaleCheck() {
  if (staleTimeout) clearTimeout(staleTimeout);
  staleTimeout = setTimeout(() => {
    matchState.isStale = true;
    console.warn("[Stale] No recent update, data may be outdated.");
  }, Math.max(25000, matchState.pollingInterval * 3000));
}

// Initialize sync and stale detection
startAutoSync();
scheduleStaleCheck();

const server = http.createServer(app);
server.listen(PORT, '0.0.0.0', () => {
  console.log(`=======================================================`);
  console.log(`🏏 Live Cricket Scorecard Overlay Server Running!`);
  console.log(`📺 Overlay URL for OBS: http://localhost:${PORT}/overlay.html`);
  console.log(`🎛️ Operator Control Panel: http://localhost:${PORT}/index.html`);
  console.log(`=======================================================`);
});
