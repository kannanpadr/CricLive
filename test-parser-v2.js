const { parsePageContent } = require('./cricheroes-service');
const { scrapeCricHeroesUrl } = require('./cricheroes-service');

async function runTests() {
  console.log('\n======= TEST 1: LIVE MATCH (Legends Blue vs Legends Green) =======');
  const r1 = await scrapeCricHeroesUrl('https://cricheroes.com/scorecard/27636860/legends-summer-series-/legends-blue-vs-legends-green-11/live');
  console.log(`Tournament: ${r1.tournament}`);
  console.log(`Team A: ${r1.teamA.name} (${r1.teamA.shortName}) → ${r1.teamA.score}/${r1.teamA.wickets} (${r1.teamA.overs} ov) | Batting: ${r1.teamA.isBatting}`);
  console.log(`Team B: ${r1.teamB.name} (${r1.teamB.shortName}) → ${r1.teamB.score}/${r1.teamB.wickets} (${r1.teamB.overs} ov) | Batting: ${r1.teamB.isBatting}`);
  console.log(`Status: ${r1.status} | ${r1.statusText}`);
  console.log(`Innings: ${r1.currentInnings} | Target: ${r1.target} | Need: ${r1.requiredRuns} off ${r1.ballsRemaining}b`);
  console.log(`CRR: ${r1.crr} | RRR: ${r1.rrr}`);
  console.log(`Striker: ${r1.striker?.name} (${r1.striker?.runs} off ${r1.striker?.balls})`);
  console.log(`Non-Striker: ${r1.nonStriker?.name} (${r1.nonStriker?.runs} off ${r1.nonStriker?.balls})`);
  console.log(`Bowler: ${r1.bowler?.name} - ${r1.bowler?.wickets}/${r1.bowler?.runs} (${r1.bowler?.overs} ov)`);
  console.log(`Result: ${r1.matchResult}`);

  console.log('\n======= TEST 2: COMPLETED MATCH (IDCC 2026) =======');
  const r2 = await scrapeCricHeroesUrl('https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep');
  console.log(`Tournament: ${r2.tournament}`);
  console.log(`Team A: ${r2.teamA.name} (${r2.teamA.shortName}) → ${r2.teamA.score}/${r2.teamA.wickets} (${r2.teamA.overs} ov)`);
  console.log(`Team B: ${r2.teamB.name} (${r2.teamB.shortName}) → ${r2.teamB.score}/${r2.teamB.wickets} (${r2.teamB.overs} ov)`);
  console.log(`Status: ${r2.status} | ${r2.statusText}`);
  console.log(`Result: ${r2.matchResult}`);
}

runTests().catch(console.error);
