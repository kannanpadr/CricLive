const { chromium } = require('playwright');
const fs = require('fs');

async function debugLivePage(url) {
  const browser = await chromium.launch({
    channel: 'msedge',
    headless: true,
    args: ['--disable-blink-features=AutomationControlled', '--no-sandbox']
  });
  const context = await browser.newContext({
    userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/122.0.0.0 Safari/537.36',
    viewport: { width: 1400, height: 900 }
  });
  const page = await context.newPage();
  await page.addInitScript(() => { Object.defineProperty(navigator, 'webdriver', { get: () => undefined }); });

  const pageUrl = url.replace(/\/live\/?$/, '');
  console.log('Loading:', pageUrl);
  await page.goto(pageUrl, { waitUntil: 'domcontentloaded', timeout: 15000 });
  await page.waitForTimeout(4000);

  // Full raw text dump
  const bodyText = await page.evaluate(() => document.body.innerText);
  fs.writeFileSync('debug-raw.txt', bodyText);
  console.log('=== FULL RAW TEXT ===');
  console.log(bodyText);
  
  // Try to extract structured data using DOM queries
  const structuredData = await page.evaluate(() => {
    const data = {
      title: document.title,
      allText: document.body.innerText,
      tables: [],
      teamElements: [],
      scoreElements: [],
      taggedBlocks: []
    };

    // Find all elements that look like scores (runs/wickets)
    document.querySelectorAll('*').forEach(el => {
      const t = (el.innerText || '').trim();
      // Score pattern: "164/6" or "128-3" with possible "(20 ov)"
      if (/^\d{1,3}\/\d{1,2}$/.test(t) || /^\d{1,3}-\d{1,2}$/.test(t)) {
        data.scoreElements.push({ tag: el.tagName, className: el.className.slice(0, 80), text: t, outerHTML: el.outerHTML.slice(0, 200) });
      }
    });

    // Find tables
    document.querySelectorAll('table').forEach(tbl => {
      const rows = [];
      tbl.querySelectorAll('tr').forEach(tr => {
        const cells = [];
        tr.querySelectorAll('td, th').forEach(td => cells.push(td.innerText.trim()));
        if (cells.some(c => c.length > 0)) rows.push(cells);
      });
      if (rows.length > 0) data.tables.push(rows.slice(0, 8));
    });

    // Find team-name-like headings
    document.querySelectorAll('h1, h2, h3, h4, span, div, p').forEach(el => {
      const t = (el.innerText || '').trim();
      if (t.length > 3 && t.length < 50 && /^[A-Z0-9\s()&.'-]+$/.test(t) && !t.includes('\n')) {
        data.teamElements.push({ tag: el.tagName, className: el.className.slice(0, 60), text: t });
      }
    });

    return data;
  });

  fs.writeFileSync('debug-structured.json', JSON.stringify(structuredData, null, 2));
  console.log('\n=== SCORE ELEMENTS FOUND ===');
  console.log(structuredData.scoreElements);
  console.log('\n=== TABLES FOUND ===');
  console.log(JSON.stringify(structuredData.tables, null, 2));

  await browser.close();
}

const url = process.argv[2] || 'https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep';
debugLivePage(url).catch(console.error);
