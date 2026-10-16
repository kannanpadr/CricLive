const { execSync } = require('child_process');

process.env.PLAYWRIGHT_BROWSERS_PATH = process.env.PLAYWRIGHT_BROWSERS_PATH || '0';

console.log('[Setup] Checking and installing Chromium browser binary (without sudo)...');
try {
  execSync('npx playwright install chromium', {
    stdio: 'inherit',
    env: {
      ...process.env,
      PLAYWRIGHT_BROWSERS_PATH: process.env.PLAYWRIGHT_BROWSERS_PATH
    }
  });
  console.log('[Setup] Chromium browser ready!');
} catch (err) {
  console.warn('[Setup Warning] Playwright browser installation notice:', err.message);
}
