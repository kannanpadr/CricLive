const fs = require('fs');

async function scanChunks() {
  const url = 'https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep';
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'
    }
  });
  const html = await res.text();
  const scriptSrcs = [...html.matchAll(/src="([^"]+\.js)"/g)].map(m => m[1]);
  console.log('Script sources:', scriptSrcs);

  for (const src of scriptSrcs) {
    try {
      const sRes = await fetch(src, { headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' } });
      const js = await sRes.text();
      // Look for API endpoints, baseURLs, or fetch endpoints
      const endpoints = js.match(/https?:\/\/[a-zA-Z0-9.\-_]+(?:\/api[^\s"'\`]*)?/g);
      if (endpoints) {
        endpoints.forEach(ep => {
          if (ep.includes('cricheroes') || ep.includes('api')) {
            console.log('Found endpoint in', src.slice(-20), ':', ep);
          }
        });
      }
      // Look for string literals containing /scorecard or /match
      const paths = js.match(/["'`](?:\/api\/|\/v\d\/)[^"'`]+["'`]/g);
      if (paths) {
        console.log('API paths in', src.slice(-20), ':', [...new Set(paths)]);
      }
    } catch (e) {
      console.error(e);
    }
  }
}

scanChunks().catch(console.error);
