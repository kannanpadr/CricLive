function parseCricHeroesContent(text, title, url) {
  const result = {
    tournament: "Cricket Tournament",
    matchStatus: "live",
    statusText: "LIVE",
    teamA: { name: "Team 1", shortName: "TM1", score: 0, wickets: 0, overs: 0.0, isBatting: false },
    teamB: { name: "Team 2", shortName: "TM2", score: 0, wickets: 0, overs: 0.0, isBatting: false },
    currentInnings: 1,
    target: null,
    striker: null,
    nonStriker: null,
    bowler: null,
    thisOver: [],
    matchResult: null
  };

  // 1. Try to extract tournament from title or text
  if (title) {
    const titleParts = title.split('-');
    if (titleParts.length > 1) {
      result.tournament = titleParts[1].replace(/Scorecard.*/i, '').trim().toUpperCase();
    }
  }

  // 2. Look for innings / scores in match notes or summary
  // Example patterns:
  // "Innings Break: Nethaji Cricket Academy (NCA) - 177/12 in 49.5 overs"
  // "End of Day: Karaikalmedu Blue Fin Cricket Academy - 99/15 in 30.2 overs"
  // "Team Name: 164/6 in 20.0 overs"
  const inningsMatches = [...text.matchAll(/(?:Innings Break:|End of Day:|Innings Ended:)?\s*([A-Za-z0-9\s().&'-]+?)\s*[-:]\s*(\d{1,3})\/(\d{1,2})\s*in\s*(\d+(?:\.\d+)?)\s*overs/gi)];
  
  if (inningsMatches.length >= 2) {
    // Both innings found
    const t1 = inningsMatches[0];
    const t2 = inningsMatches[1];
    
    result.teamA.name = t1[1].trim();
    result.teamA.shortName = makeShortName(result.teamA.name);
    result.teamA.score = parseInt(t1[2], 10);
    result.teamA.wickets = parseInt(t1[3], 10);
    result.teamA.overs = parseFloat(t1[4]);

    result.teamB.name = t2[1].trim();
    result.teamB.shortName = makeShortName(result.teamB.name);
    result.teamB.score = parseInt(t2[2], 10);
    result.teamB.wickets = parseInt(t2[3], 10);
    result.teamB.overs = parseFloat(t2[4]);

    result.currentInnings = 2;
    result.teamA.isBatting = false;
    result.teamB.isBatting = true;
    result.target = result.teamA.score + 1;
  } else if (inningsMatches.length === 1) {
    const t1 = inningsMatches[0];
    result.teamA.name = t1[1].trim();
    result.teamA.shortName = makeShortName(result.teamA.name);
    result.teamA.score = parseInt(t1[2], 10);
    result.teamA.wickets = parseInt(t1[3], 10);
    result.teamA.overs = parseFloat(t1[4]);
    result.teamA.isBatting = true;
    result.currentInnings = 1;
  }

  // Look for match result
  // "Team A won by X runs" or "Team B won by X wickets"
  const winMatch = text.match(/([A-Za-z0-9\s().&'-]+?)\s*won by\s*(\d+\s*(?:runs|wickets|wkts))/i);
  if (winMatch) {
    result.matchResult = `${winMatch[1].trim()} won by ${winMatch[2].trim()}`.toUpperCase();
    result.matchStatus = "completed";
    result.statusText = "MATCH CONCLUDED";
  }

  // Look for batters table
  // "Batters R B 4s 6s SR\nName 52 2 0 61.54"
  const batterLines = [...text.matchAll(/([A-Za-z0-9\s().&'-]+?)\s+(\d{1,3})\s+(\d{1,3})\s+(\d{1,2})\s+(\d{1,2})\s+([\d.]+)/g)];
  if (batterLines.length >= 1) {
    const b1 = batterLines[0];
    result.striker = {
      name: cleanPlayerName(b1[1]),
      runs: parseInt(b1[2], 10) || 0,
      balls: parseInt(b1[3], 10) || 0,
      fours: parseInt(b1[4], 10) || 0,
      sixes: parseInt(b1[5], 10) || 0,
      strikeRate: b1[6] || "0.0",
      isStriker: true
    };
  }
  if (batterLines.length >= 2) {
    const b2 = batterLines[1];
    result.nonStriker = {
      name: cleanPlayerName(b2[1]),
      runs: parseInt(b2[2], 10) || 0,
      balls: parseInt(b2[3], 10) || 0,
      fours: parseInt(b2[4], 10) || 0,
      sixes: parseInt(b2[5], 10) || 0,
      strikeRate: b2[6] || "0.0",
      isStriker: false
    };
  }

  // Look for bowlers
  // "Bowlers O M R W Eco\nName 6.0 0 5 2.83"
  const bowlerLines = [...text.matchAll(/([A-Za-z0-9\s().&'-]+?)\s+(\d+(?:\.\d+)?)\s+(\d+)\s+(\d+)\s+(\d+)\s+([\d.]+)/g)];
  if (bowlerLines.length >= 1) {
    const bw = bowlerLines[0];
    result.bowler = {
      name: cleanPlayerName(bw[1]),
      overs: bw[2],
      maidens: parseInt(bw[3], 10) || 0,
      runs: parseInt(bw[4], 10) || 0,
      wickets: parseInt(bw[5], 10) || 0,
      economy: bw[6] || "0.00"
    };
  }

  return result;
}

function makeShortName(name) {
  if (!name) return "TM";
  // Look for brackets like "(NCA)"
  const bracket = name.match(/\(([A-Za-z0-9]+)\)/);
  if (bracket) return bracket[1].toUpperCase();
  // Otherwise take initials of words
  const words = name.split(/\s+/).filter(w => w.length > 0);
  if (words.length >= 2) {
    return words.map(w => w[0]).join('').slice(0, 4).toUpperCase();
  }
  return name.slice(0, 3).toUpperCase();
}

function cleanPlayerName(raw) {
  if (!raw) return "Player";
  return raw.replace(/\([^)]*\)/g, '').trim().split('\n').pop().trim();
}

// Test with our extracted dump
const fs = require('fs');
const dump = JSON.parse(fs.readFileSync('cricheroes-page-data.json', 'utf8'));
const parsed = parseCricHeroesContent(dump.bodyText, dump.title, 'https://cricheroes.com/scorecard/16474811');
console.log('Parsed match info:');
console.dir(parsed, { depth: null });
