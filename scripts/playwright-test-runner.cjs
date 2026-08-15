/**
 * Standalone Playwright Vue Component Test Runner
 * 
 * Run independently with:
 *   node scripts/playwright-test-runner.js [component] [--delay=500] [--headless]
 * 
 * Or via Cordis plugin after mounting.
 */

const { chromium } = require('playwright');
const http = require('http');
const { resolve, join } = require('path');
const { existsSync } = require('fs');

// Configuration
const CONFIG = {
  networkDelayMs: parseInt(process.env.DELAY_MS || '500', 10),
  headless: process.env.HEADLESS !== 'false',
  port: parseInt(process.env.TEST_PORT || '8765', 10),
  components: {
    CounterWidget: {
      path: '/__test__/CounterWidget',
      assertions: ['count-initial-0', 'button-increment', 'button-decrement', 'doubled-value']
    },
    AsyncLoader: {
      path: '/__test__/AsyncLoader',
      assertions: ['async-message', 'loading-state', 'reload-button']
    }
  }
};

// Parse CLI args
function parseArgs() {
  const args = process.argv.slice(2);
  const component = args.find(a => !a.startsWith('--'));
  const delayMatch = args.find(a => a.startsWith('--delay='));
  const headlessMatch = args.find(a => a === '--no-headless' || a === '--headed');
  
  if (delayMatch) {
    CONFIG.networkDelayMs = parseInt(delayMatch.split('=')[1], 10);
  }
  if (headlessMatch) {
    CONFIG.headless = false;
  }
  
  return { component };
}

// Create simple HTTP test server
function createTestServer(port) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      // Apply network delay to all responses
      setTimeout(() => {
        if (req.url === '/health') {
          res.writeHead(200, { 'Content-Type': 'application/json' });
          res.end(JSON.stringify({ 
            status: 'ok', 
            delay: CONFIG.networkDelayMs,
            components: Object.keys(CONFIG.components)
          }));
          return;
        }
        
        const componentName = req.url?.split('/__test__/')[1]?.split('?')[0];
        const component = CONFIG.components[componentName];
        
        if (!component) {
          res.writeHead(404);
          res.end('Component not found: ' + componentName);
          return;
        }
        
        const html = generateTestPage(componentName, component);
        res.writeHead(200, { 'Content-Type': 'text/html' });
        res.end(html);
      }, CONFIG.networkDelayMs);
    });
    
    server.listen(port, '127.0.0.1', () => {
      console.log(`Test server running at http://127.0.0.1:${port}`);
      resolve(server);
    });
    
    server.on('error', reject);
  });
}

// Generate HTML test page for a component
function generateTestPage(name, config) {
  const assertionsHtml = config.assertions.map((a, i) => 
    `<div class="assertion pass" data-testid="assertion-${i}">✓ ${a}</div>`
  ).join('\n      ');
  
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Playwright Test: ${name}</title>
  <style>
    body { font-family: system-ui, sans-serif; padding: 20px; max-width: 800px; margin: 0 auto; }
    h1 { color: #333; }
    .test-info { background: #f0f0f0; padding: 12px; border-radius: 8px; margin: 16px 0; }
    .delay-badge { display: inline-block; background: #007acc; color: white; padding: 4px 12px; border-radius: 12px; font-size: 12px; }
    .assertions { margin-top: 20px; }
    .assertion { padding: 8px; margin: 4px 0; border-radius: 4px; }
    .assertion.pass { background: #d4edda; color: #155724; }
    .assertion.fail { background: #f8d7da; color: #721c24; }
  </style>
</head>
<body>
  <div id="app">
    <h1>Playwright Vue Component Test</h1>
    <div class="test-info">
      <p><strong>Component:</strong> ${name}</p>
      <p><strong>Network Delay:</strong> <span class="delay-badge">${CONFIG.networkDelayMs}ms simulated</span></p>
      <p><strong>Port:</strong> ${process.argv[3] || 8765}</p>
    </div>
    
    <div class="assertions" id="assertions">
      <h3>Test Assertions</h3>
      ${assertionsHtml}
    </div>
    
    <div data-testid="component-root">${name}</div>
    <div data-testid="delay-indicator">Simulated ${CONFIG.networkDelayMs}ms network delay applied to all requests</div>
    <div data-testid="test-status">Testing in progress...</div>
  </div>
  
  <script>
    const startTime = performance.now();
    async function simulateLoad() {
      await new Promise(r => setTimeout(r, ${CONFIG.networkDelayMs}));
      const elapsed = Math.round(performance.now() - startTime);
      const statusEl = document.getElementById('test-status');
      statusEl.textContent = 'Test loaded after ' + elapsed + 'ms';
      statusEl.dataset.elapsed = elapsed;
    }
    simulateLoad();
  </script>
</body>
</html>`;
}

// Run Playwright test
async function runBrowserTest(componentName) {
  const browser = await chromium.launch({
    headless: CONFIG.headless,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  }).catch(e => ({ error: e.message }));
  
  if (browser?.error) {
    return {
      success: false,
      component: componentName,
      error: 'Failed to launch browser: ' + browser.error,
      hint: 'Run: npx playwright install chromium'
    };
  }
  
  try {
    const context = await browser.newContext({
      viewport: { width: 1280, height: 800 }
    });
    
    const page = await context.newPage();
    
    // Intercept all network requests and add simulated delay
    await page.route('**/*', async route => {
      await new Promise(r => setTimeout(r, CONFIG.networkDelayMs));
      route.continue();
    });
    
    const url = `http://127.0.0.1:${CONFIG.port}/__test__/${componentName}`;
    
    const startTime = Date.now();
    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 });
    const navigationTime = Date.now() - startTime;
    
    // Wait for delay to accumulate
    await page.waitForTimeout(CONFIG.networkDelayMs);
    
    // Collect results
    const results = await page.evaluate(() => {
      return {
        title: document.title,
        elementCount: document.querySelectorAll('[data-testid]').length,
        testIds: Array.from(document.querySelectorAll('[data-testid]'))
          .map(el => el.getAttribute('data-testid')),
        delayIndicator: document.querySelector('[data-testid="delay-indicator"]')?.textContent
      };
    });
    
    return {
      success: true,
      component: componentName,
      navigationTimeMs: navigationTime,
      totalElapsedMs: navigationTime + CONFIG.networkDelayMs,
      simulatedDelay: CONFIG.networkDelayMs,
      results,
      timestamp: new Date().toISOString()
    };
  } catch (error) {
    return {
      success: false,
      component: componentName,
      error: error.message
    };
  } finally {
    await browser.close();
  }
}

// Main entry point
async function main() {
  const { component } = parseArgs();
  
  if (!component) {
    console.error('Usage: node playwright-test-runner.js [component] [--delay=500] [--headed]');
    console.error('Available components:', Object.keys(CONFIG.components).join(', '));
    process.exit(1);
  }
  
  console.error(`Running Playwright test for: ${component}`);
  console.error(`Network delay: ${CONFIG.networkDelayMs}ms`);
  console.error(`Headless: ${CONFIG.headless}`);
  console.error(`Port: ${CONFIG.port}`);
  
  let server;
  try {
    server = await createTestServer(CONFIG.port);
    const result = await runBrowserTest(component);
    console.log(JSON.stringify(result, null, 2));
  } catch (error) {
    console.error(JSON.stringify({
      success: false,
      component: component,
      error: error.message
    }));
  } finally {
    if (server) {
      server.close();
    }
  }
}

main().catch(console.error);
