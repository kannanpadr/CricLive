const fs = require('fs');

async function dumpNextData() {
  const url = 'https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep';
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
  });
  const html = await res.text();
  
  // Extract all pushes
  const pushes = [...html.matchAll(/self\.__next_f\.push\(\[1,"([\s\S]*?)"\]\)/g)];
  console.log('Total pushes:', pushes.length);
  
  let fullPayload = '';
  pushes.forEach((p, idx) => {
    // Unescape JSON string
    try {
      const unescaped = JSON.parse(`"${p[1]}"`);
      fullPayload += `\n--- PUSH ${idx} ---\n` + unescaped;
    } catch (e) {
      fullPayload += `\n--- PUSH ${idx} (raw) ---\n` + p[1];
    }
  });

  fs.writeFileSync('cricheroes-dump.txt', fullPayload);
  console.log('Saved to cricheroes-dump.txt, size:', fullPayload.length);

  // Search for keywords
  const keywords = ['score', 'wicket', 'overs', 'Admin', 'Unified', 'target', 'innings'];
  keywords.forEach(kw => {
    const idx = fullPayload.indexOf(kw);
    console.log(`Keyword "${kw}" at index:`, idx);
    if (idx !== -1) {
      console.log(`Snippet around "${kw}":`, fullPayload.slice(Math.max(0, idx - 50), idx + 100));
    }
  });
}

dumpNextData().catch(console.error);
