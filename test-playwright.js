const { chromium } = require('playwright');

async function testCricHeroesPlaywright(url) {
  console.log('Launching browser to test:', url);
  const browser = await chromium.launch({ headless: true });
  try {
    const page = await browser.newPage({
      userAgent: 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'
    });
    
    console.log('Navigating...');
    await page.goto(url, { waitUntil: 'domcontentloaded', timeout: 30000 });
    await page.waitForTimeout(4000); // Wait for hydration / react render

    const title = await page.title();
    console.log('Page Title:', title);

    // Extract text and inspect elements
    const pageText = await page.evaluate(() => {
      // Find all team names, scores, overs
      const body = document.body.innerText;
      return body.slice(0, 1500);
    });

    console.log('Extracted visible text:');
    console.log(pageText);

    // Check for score containers
    const scoreInfo = await page.evaluate(() => {
      // Look for elements with scores
      const elements = Array.from(document.querySelectorAll('*'));
      const scores = [];
      for (const el of elements) {
        const text = el.innerText || '';
        // regex for score like 164/6 or 128-3 or overs
        if (/^\d{1,3}[\/\-]\d{1,2}$/.test(text.trim())) {
          scores.push({ tag: el.tagName, text: text.trim(), class: el.className });
        }
      }
      return scores;
    });

    console.log('Scores found:', scoreInfo);

  } catch (err) {
    console.error('Playwright error:', err);
  } finally {
    await browser.close();
  }
}

testCricHeroesPlaywright('https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep');
