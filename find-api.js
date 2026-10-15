async function findApiUrl() {
  const chunks = [
    "https://next.cricheroes.com/_next/static/chunks/06z4mxumxchuu.js",
    "https://next.cricheroes.com/_next/static/chunks/0q47tvccnttda.js"
  ];
  for (const c of chunks) {
    try {
      const res = await fetch(c, {
        headers: { 'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36' }
      });
      const text = await res.text();
      const matches = text.match(/https?:\/\/[a-zA-Z0-9.\-_]+\/(?:api|v1|scorecard|matches)[a-zA-Z0-9.\-_\/]+/g);
      if (matches) {
        console.log('API URLs found in chunk:', matches.slice(0, 10));
      }
      const paths = text.match(/\/api\/[a-zA-Z0-9.\-_\/]+/g);
      if (paths) {
        console.log('API paths found:', [...new Set(paths)].slice(0, 10));
      }
    } catch (e) {
      console.error(e);
    }
  }
}
findApiUrl();
