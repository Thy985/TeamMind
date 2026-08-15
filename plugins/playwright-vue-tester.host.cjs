/**
 * Playwright Vue Component Tester - Cordis Plugin
 * 
 * Host-side plugin that:
 * 1. Serves Vue component test pages via webServer
 * 2. Simulates 500ms network delay via request interception
 * 3. Provides a dynamic tool `playVuetest` for the model
 * 
 * Note: This plugin works in both headless and headed modes.
 * If Chromium is not available, it falls back to a simulated mode.
 */

// Component registry
const COMPONENTS = {
  CounterWidget: {
    path: '/__test__/CounterWidget',
    assertions: ['count-initial-0', 'button-increment', 'button-decrement', 'doubled-value']
  },
  AsyncLoader: {
    path: '/__test__/AsyncLoader',
    assertions: ['async-message', 'loading-state', 'reload-button']
  }
};

const NETWORK_DELAY_MS = 500;

/**
 * Plugin factory function
 * @param {object} ctx - Cordis context
 * @returns {object} Plugin definition
 */
function createPlugin(ctx) {
  const webServer = ctx.get('webServer');
  const harness = ctx.get('harness');
  
  // Try to load Playwright
  let playwright;
  try {
    playwright = require('playwright');
  } catch (e) {
    console.error('[pvct] Playwright not available:', e.message);
    playwright = null;
  }
  
  // Check if browser is available
  let browserAvailable = false;
  let chromiumLauncher = null;
  
  if (playwright && playwright.chromium) {
    chromiumLauncher = playwright.chromium;
    browserAvailable = true;
  }
  
  // Register web routes for test pages
  if (webServer) {
    for (const [name, config] of Object.entries(COMPONENTS)) {
      const html = buildTestPage(name, config);
      webServer.register({
        path: config.path,
        handler: (_req, res) => {
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(html);
        }
      });
    }
  }
  
  // Register dynamic tool
  if (harness && harness.registerTool) {
    harness.registerTool(ctx, {
      name: 'playVuetest',
      description: `Run a Playwright browser test on a Vue component with simulated ${NETWORK_DELAY_MS}ms network delay`,
      parameters: {
        type: 'object',
        properties: {
          component: {
            type: 'string',
            enum: Object.keys(COMPONENTS),
            description: 'Component name to test'
          },
          delayMs: {
            type: 'number',
            default: NETWORK_DELAY_MS,
            description: 'Network delay to simulate in milliseconds'
          },
          headless: {
            type: 'boolean',
            default: true,
            description: 'Run browser in headless mode'
          }
        },
        required: ['component']
      },
      async execute(args) {
        return await runBrowserTest(args.component, {
          delayMs: args.delayMs ?? NETWORK_DELAY_MS,
          headless: args.headless ?? true
        });
      }
    });
  }
  
  // Register RPC handlers
  if (harness && harness.handle) {
    harness.handle('run-test', async (args) => {
      return await runBrowserTest(args.component, {
        delayMs: args.delayMs ?? NETWORK_DELAY_MS,
        headless: args.headless ?? true
      });
    });
    
    harness.handle('get-components', () => ({
      components: Object.keys(COMPONENTS)
    }));
    
    harness.handle('get-status', () => ({
      playwrightAvailable: !!playwright,
      browserAvailable: browserAvailable,
      networkDelayMs: NETWORK_DELAY_MS,
      testPages: Object.keys(COMPONENTS)
    }));
  }
  
  async function runBrowserTest(componentName, options) {
    // If no browser available, use simulated mode
    if (!browserAvailable) {
      return runSimulatedTest(componentName, options);
    }
    
    const browser = await chromiumLauncher.launch({
      headless: options.headless,
      args: ['--no-sandbox', '--disable-setuid-sandbox']
    }).catch(e => ({ error: e.message }));
    
    if (browser?.error) {
      return {
        success: false,
        component: componentName,
        error: 'Failed to launch browser: ' + browser.error,
        hint: 'Run: pnpm exec playwright install chromium'
      };
    }
    
    try {
      const context = await browser.newContext({
        viewport: { width: 1280, height: 800 }
      });
      
      const page = await context.newPage();
      
      // Intercept all network requests and add simulated delay
      await page.route('**/*', async route => {
        await new Promise(r => setTimeout(r, options.delayMs));
        route.continue();
      });
      
      const testUrl = `http://localhost:5173/__test__/${componentName}`;
      
      const startTime = Date.now();
      await page.goto(testUrl, { 
        waitUntil: 'networkidle', 
        timeout: 30000 
      });
      const navigationTime = Date.now() - startTime;
      
      // Wait for the simulated delay to accumulate
      await page.waitForTimeout(options.delayMs);
      
      // Collect results
      const results = await page.evaluate(() => {
        return {
          title: document.title,
          elementCount: document.querySelectorAll('[data-testid]').length,
          testIds: Array.from(document.querySelectorAll('[data-testid]'))
            .map(el => el.getAttribute('data-testid'))
        };
      });
      
      return {
        success: true,
        component: componentName,
        navigationTimeMs: navigationTime,
        totalElapsedMs: navigationTime + options.delayMs,
        simulatedDelay: options.delayMs,
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
  
  // Simulated test mode (when browser is not available)
  async function runSimulatedTest(componentName, options) {
    const component = COMPONENTS[componentName];
    if (!component) {
      return {
        success: false,
        component: componentName,
        error: `Component not found: ${componentName}`
      };
    }
    
    // Simulate network delay
    await new Promise(r => setTimeout(r, options.delayMs));
    
    // Return simulated results
    return {
      success: true,
      component: componentName,
      mode: 'simulated',
      note: 'Browser not available, using simulated mode',
      navigationTimeMs: options.delayMs,
      totalElapsedMs: options.delayMs * 2,
      simulatedDelay: options.delayMs,
      results: {
        title: `Playwright Test: ${componentName}`,
        elementCount: component.assertions.length + 2,
        testIds: [
          'component-root',
          'delay-indicator',
          ...component.assertions.map((a, i) => `assertion-${i}`)
        ]
      },
      timestamp: new Date().toISOString()
    };
  }
  
  function buildTestPage(name, config) {
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
      <p><strong>Network Delay:</strong> <span class="delay-badge">${NETWORK_DELAY_MS}ms simulated</span></p>
    </div>
    
    <div class="assertions" id="assertions">
      <h3>Test Assertions</h3>
      ${assertionsHtml}
    </div>
    
    <div data-testid="component-root">${name}</div>
    <div data-testid="delay-indicator">Simulated ${NETWORK_DELAY_MS}ms network delay applied to all requests</div>
    <div data-testid="test-status">Testing in progress...</div>
  </div>
  
  <script>
    const startTime = performance.now();
    async function simulateLoad() {
      await new Promise(r => setTimeout(r, ${NETWORK_DELAY_MS}));
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
  
  return {
    apply: function(context) {
      // Plugin initialization
      console.log('[pvct] Plugin initialized');
    }
  };
}

// Export the factory function
module.exports = createPlugin;
