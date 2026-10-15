const { chromium } = require('playwright');

async function testStealth() {
  console.log('Testing with stealth args...');
  try {
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
      viewport: { width: 1280, height: 720 },
      deviceScaleFactor: 1
    });

    const page = await context.newPage();
    await page.addInitScript(() => {
      Object.defineProperty(navigator, 'webdriver', { get: () => undefined });
    });

    console.log('Navigating to scorecard...');
    const res = await page.goto('https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep', {
      waitUntil: 'domcontentloaded',
      timeout: 30000
    });

    await page.waitForTimeout(3000);
    const title = await page.title();
    console.log('Title after stealth navigation:', title);

    const bodyText = await page.evaluate(() => document.body.innerText);
    console.log('Body length:', bodyText.length);
    if (bodyText.length > 500) {
      console.log('First 600 chars:\n', bodyText.slice(0, 600));
    }

    await browser.close();
  } catch (err) {
    console.error('Stealth error:', err);
  }
}

testStealth();
