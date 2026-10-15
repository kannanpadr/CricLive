async function testRealMatch() {
  const url = 'https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep';
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
  });
  const html = await res.text();
  console.log('Status:', res.status, 'HTML length:', html.length);

  const title = html.match(/<title>([^<]*)<\/title>/i);
  console.log('Title:', title ? title[1] : 'No title');

  const ogTitle = html.match(/<meta[^>]*property="og:title"[^>]*content="([^"]*)"/i);
  console.log('OG Title:', ogTitle ? ogTitle[1] : 'No OG Title');

  const ogDesc = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/i);
  console.log('OG Desc:', ogDesc ? ogDesc[1] : 'No OG Desc');

  // Let's search for JSON data in self.__next_f
  const nextPushes = [...html.matchAll(/self\.__next_f\.push\(\[1,"([\s\S]*?)"\]\)/g)];
  console.log('Next pushes count:', nextPushes.length);
  for (const m of nextPushes) {
    const raw = m[1];
    if (raw.includes('score') || raw.includes('innings') || raw.includes('Admin')) {
      // Unescape json
      console.log('Relevant push slice:', raw.slice(0, 300));
    }
  }
}

testRealMatch().catch(console.error);
