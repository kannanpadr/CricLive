async function inspect() {
  const url = 'https://cricheroes.com/scorecard/27644870';
  const res = await fetch(url, {
    headers: {
      'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
      'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8'
    }
  });
  const html = await res.text();
  console.log('HTML length:', html.length);

  // Check for next data or JSON
  const nextData = html.match(/<script id="__NEXT_DATA__"[^>]*>([\s\S]*?)<\/script>/);
  if (nextData) {
    console.log('Found __NEXT_DATA__!');
    console.log(nextData[1].slice(0, 500));
    return;
  }

  // Look for any self.__next_f or JSON payloads in script tags
  const scripts = [...html.matchAll(/<script[^>]*>([\s\S]*?)<\/script>/g)];
  console.log('Total scripts found:', scripts.length);
  for (let i = 0; i < scripts.length; i++) {
    const content = scripts[i][1];
    if (content.includes('self.__next_f.push') || content.includes('27644870') || content.includes('score')) {
      console.log(`Script ${i} length: ${content.length}`);
      // find snippets mentioning scores or teams
      const matches = content.match(/.{0,50}(?:Admin|Bangalore|TSS|ICL|score|wickets|overs).{0,50}/gi);
      if (matches) {
        console.log(`Script ${i} matches:`, matches.slice(0, 5));
      }
    }
  }

  // Check title & meta tags
  const title = html.match(/<title>([^<]*)<\/title>/i);
  console.log('Title:', title ? title[1] : 'No title');
  const metaDesc = html.match(/<meta[^>]*name="description"[^>]*content="([^"]*)"/i);
  console.log('Meta desc:', metaDesc ? metaDesc[1] : 'No meta desc');
  const ogTitle = html.match(/<meta[^>]*property="og:title"[^>]*content="([^"]*)"/i);
  console.log('OG Title:', ogTitle ? ogTitle[1] : 'No OG Title');
  const ogDesc = html.match(/<meta[^>]*property="og:description"[^>]*content="([^"]*)"/i);
  console.log('OG Desc:', ogDesc ? ogDesc[1] : 'No OG Desc');
}

inspect().catch(console.error);
