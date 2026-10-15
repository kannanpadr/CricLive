const { chromium } = require('playwright');
(async () => {
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
    args: ['--disable-blink-features=AutomationControlled', '--no-sandbox']
  });
  const page = await browser.newPage({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
  });
  
  // Inspect the live match URL
  const url = 'https://cricheroes.com/scorecard/27664456/individual/caravan-club-vs-century-cc/live';
  console.log('Navigating to:', url);
  await page.goto(url, { waitUntil: 'domcontentloaded' });
  await page.waitForTimeout(3000);

  const analysis = await page.evaluate(() => {
    // 1. All canvases
    const canvases = [];
    document.querySelectorAll('canvas').forEach((c, i) => {
      canvases.push({
        index: i,
        dataText: c.getAttribute('data-canvas-text'),
        parentTag: c.parentElement?.tagName,
        parentClass: c.parentElement?.className,
        parentText: c.parentElement?.innerText?.replace(/\n/g, ' ')
      });
    });

    // 2. All elements containing slash numbers like \d+/\d+
    const scoreEls = [];
    document.querySelectorAll('*').forEach(el => {
      if (el.children.length === 0 && /\b\d{1,3}\/\d{1,2}\b/.test(el.innerText || '')) {
        scoreEls.push({
          tag: el.tagName,
          className: el.className,
          text: el.innerText.trim(),
          parentText: el.parentElement?.innerText?.replace(/\n/g, ' ')?.slice(0, 80)
        });
      }
    });

    // 3. Team names and headers
    const teamHeaders = [];
    document.querySelectorAll('[class*="team"], [class*="Team"], [class*="score"], [class*="Score"]').forEach(el => {
      const t = el.innerText?.trim();
      if (t && t.length < 50 && (t.includes('/') || t.includes('Ov'))) {
        teamHeaders.push({
          class: el.className,
          text: t.replace(/\n/g, ' ')
        });
      }
    });

    return { canvases, scoreEls, teamHeaders: teamHeaders.slice(0, 15) };
  });

  console.log('CANVASES:', JSON.stringify(analysis.canvases, null, 2));
  console.log('SCORE ELEMENTS (HTML text):', JSON.stringify(analysis.scoreEls, null, 2));
  console.log('TEAM HEADERS:', JSON.stringify(analysis.teamHeaders, null, 2));

  await browser.close();
})();
