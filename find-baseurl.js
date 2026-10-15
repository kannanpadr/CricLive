const fs = require('fs');

async function findBaseUrl() {
  const chunks = [
    "https://next.cricheroes.com/_next/static/chunks/123xr0ff8odpi.js",
    "https://next.cricheroes.com/_next/static/chunks/0~k6u5_j-9bf2.js",
    "https://next.cricheroes.com/_next/static/chunks/0_g6cr6pz6cs6.js",
    "https://next.cricheroes.com/_next/static/chunks/0rrdsqadqyala.js",
    "https://next.cricheroes.com/_next/static/chunks/0n_m5uki~k.qr.js",
    "https://next.cricheroes.com/_next/static/chunks/0cywo_4myw2.h.js",
    "https://next.cricheroes.com/_next/static/chunks/turbopack-0b4paxwn~j7rw.js",
    "https://next.cricheroes.com/_next/static/chunks/06z4mxumxchuu.js",
    "https://next.cricheroes.com/_next/static/chunks/0q47tvccnttda.js"
  ];
  for (const c of chunks) {
    try {
      const res = await fetch(c, { headers: { 'User-Agent': 'Mozilla/5.0' } });
      const t = await res.text();
      // look for api base url
      const m = t.match(/https:\/\/[a-zA-Z0-9.\-_]*api[a-zA-Z0-9.\-_]*/g);
      if (m) {
        console.log('Match in', c.slice(-20), ':', [...new Set(m)]);
      }
      const m2 = t.match(/["']https:\/\/[^"']+\/api\/[^"']+["']/g);
      if (m2) {
        console.log('Match2 in', c.slice(-20), ':', [...new Set(m2)]);
      }
    } catch (e) {}
  }
}

findBaseUrl();
