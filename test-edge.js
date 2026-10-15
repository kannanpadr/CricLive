const { chromium } = require('playwright');

async function testWithInstalledBrowser() {
  console.log('Testing with installed msedge/chrome...');
  try {
    const browser = await chromium.launch({
      channel: 'msedge', // Uses the Windows pre-installed Microsoft Edge!
      headless: true
    });
    console.log('Successfully launched Edge!');
    const page = await browser.newPage();
    await page.goto('https://cricheroes.com/scorecard/16474811/idcc-2026/admin-ampions-bangalore-acb-vs-the-unified-x-i-chep', {
      waitUntil: 'networkidle',
      timeout: 30000
    });
    console.log('Page loaded, title:', await page.title());
    
    // Listen to network requests made by CricHeroes
    const text = await page.evaluate(() => document.body.innerText);
    console.log('Body length:', text.length);
    console.log('First 1000 chars of text:\n', text.slice(0, 1000));
    
    await browser.close();
  } catch (e) {
    console.error('Error:', e);
  }
}

testWithInstalledBrowser();
