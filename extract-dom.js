const { chromium } = require('playwright');
const fs = require('fs');

async function extractFullCricHeroesDOM() {
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
    args: [
      '--disable-blink-features=AutomationControlled',
      '--no-sandbox',
      '--disable-dev-shm-usage'
    ]
  });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    viewport: { width: 1400, height: 900 }
  });

  const page = await context.newPage();
  await page.addInitScript(() => {
    Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
  });

  await page.goto('https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep', {
    waitUntil: 'networkidle',
    timeout: 30000
  });

  const data = await page.evaluate(() => {
    // Collect all text and classnames
    const title = document.title;
    const bodyText = document.body.innerText;
    
    // Look for team names and scores
    // CricHeroes scorecard typically has team boxes
    const divs = Array.from(document.querySelectorAll('div, span, p, h1, h2, h3, h4'));
    const scoreMatches = [];
    divs.forEach(d => {
      const t = d.innerText ? d.innerText.trim() : '';
      if (/^\d{1,3}[\/\-]\d{1,2}(?:\s*\(\d+(?:\.\d+)?\s*ov\))?/i.test(t)) {
        scoreMatches.push({ tag: d.tagName, text: t, className: d.className });
      }
    });

    return { title, bodyText, scoreMatches };
  });

  fs.writeFileSync('cricheroes-page-data.json', JSON.stringify(data, null, 2));
  console.log('Saved data, text length:', data.bodyText.length);
  console.log('Scores found in elements:', data.scoreMatches.slice(0, 5));
  console.log('First 1200 characters of page text:');
  console.log(data.bodyText.slice(0, 1200));

  await browser.close();
}

extractFullCricHeroesDOM().catch(console.error);
