// public/app.js
// Handles UI interactions for the simplified live score monitor

/** Utility to make POST requests */
async function postJson(url, data) {
  const resp = await fetch(url, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify(data)
  });
  return resp.json();
}

/** Utility to fetch JSON GET */
async function getJson(url) {
  const resp = await fetch(url);
  if (!resp.ok) return null;
  return resp.json();
}

/** Update the DOM with the latest score object */
function renderScore(score) {
  if (!score) return;
  document.getElementById('matchTitle').textContent = score.tournament || 'Live Match';
  document.getElementById('battingTeam').textContent = score.battingTeam || '';
  document.getElementById('runs').textContent = score.runs ?? '-';
  document.getElementById('wickets').textContent = score.wickets ?? '-';
  document.getElementById('overs').textContent = score.overs ?? '-';
  document.getElementById('runRate').textContent = score.runRate ?? '-';
  // target & required RR are optional
  const targetLine = document.getElementById('targetLine');
  const rrLine = document.getElementById('rrLine');
  if (score.target) {
    document.getElementById('target').textContent = score.target;
    targetLine.hidden = false;
  } else {
    targetLine.hidden = true;
  }
  if (score.requiredRunRate) {
    document.getElementById('requiredRR').textContent = score.requiredRunRate;
    rrLine.hidden = false;
  } else {
    rrLine.hidden = true;
  }
  document.getElementById('status').textContent = score.status || '';
  document.getElementById('lastUpdated').textContent = new Date().toLocaleTimeString();
  document.getElementById('scoreDisplay').hidden = false;
}

let pollHandle = null;
function startPolling() {
  if (pollHandle) clearInterval(pollHandle);
  // initial fetch
  fetchScore();
  // repeat every 7 seconds (matches server interval)
  pollHandle = setInterval(fetchScore, 2000);
}

async function fetchScore() {
  const data = await getJson('/api/score');
  if (data) renderScore(data);
}

// Bind button click
document.getElementById('setUrlBtn').addEventListener('click', async () => {
  const url = document.getElementById('scoreUrl').value.trim();
  if (!url) return;
  try {
    await postJson('/api/url', { url });
    startPolling();
  } catch (e) {
    console.error('Failed to set URL', e);
  }
});
