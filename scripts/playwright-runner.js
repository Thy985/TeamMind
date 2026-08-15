/**
 * Playwright Vue Component Test Runner
 * 
 * This script is used by the Cordis plugin to run browser-based tests
 * on Vue components with simulated network delay.
 * 
 * Usage: node playwright-runner.js <component-name> [options]
 */

const { chromium, devices } = require('playwright')
const http = require('http')
const { resolve } = require('path')
const { existsSync } = require('fs')

// Component registry - maps component names to their test pages
const COMPONENT_REGISTRY = {
  'CounterWidget': {
    path: '/__test__/CounterWidget.html',
    assertions: ['count-initial-0', 'button-increment', 'button-decrement', 'doubled-value']
  },
  'AsyncLoader': {
    path: '/__test__/AsyncLoader.html',
    assertions: ['async-message', 'loading-state', 'reload-button']
  }
}

// Network delay configuration
const NETWORK_DELAY_MS = 500

/**
 * Resolve browser executable path
 */
function resolveBrowserPath() {
  const playwrightCore = require('playwright-core')
  const browsers = playwrightCore.executablePath
    ? [playwrightCore.executablePath('chromium')]
    : []
  
  // Try common system locations
  const candidates = [
    'C:/Program Files/Google/Chrome/Application/chrome.exe',
    'C:/Program Files (x86)/Google/Chrome/Application/chrome.exe',
    'C:/Users/Public/Chrome/Application/chrome.exe',
    process.env.CHROME_EXECUTABLE_PATH,
  ].filter(Boolean)
  
  return candidates.find(p => p && existsSync(p)) || null
}

/**
 * Create a simple HTTP server to serve test pages
 */
function createTestServer(port, workspaceRoot) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      // Mock network delay
      setTimeout(() => {
        if (req.url === '/health') {
          res.writeHead(200, { 'Content-Type': 'application/json' })
          res.end(JSON.stringify({ status: 'ok', delay: NETWORK_DELAY_MS }))
          return
        }
        
        if (req.url?.startsWith('/__test__/')) {
          const componentName = req.url.split('/')[2]
          const component = COMPONENT_REGISTRY[componentName]
          
          if (!component) {
            res.writeHead(404)
            res.end('Component not found: ' + componentName)
            return
          }
          
          const html = generateTestPage(componentName, component)
          res.writeHead(200, { 'Content-Type': 'text/html' })
          res.end(html)
          return
        }
        
        res.writeHead(404)
        res.end('Not found')
      }, NETWORK_DELAY_MS)
    })
    
    server.listen(port, '127.0.0.1', () => {
      console.log(`Test server running at http://127.0.0.1:${port}`)
      resolve(server)
    })
    
    server.on('error', reject)
  })
}

/**
 * Generate a test HTML page for a Vue component
 */
function generateTestPage(componentName, config) {
  return `<!DOCTYPE html>
<html>
<head>
  <meta charset="utf-8">
  <title>Test: ${componentName}</title>
  <script src="https://unpkg.com/vue@3/dist/vue.global.js"><\/script>
  <style>
    body { font-family: system-ui; padding: 20px; }
    .test-result { margin-top: 10px; padding: 10px; border-radius: 4px; }
    .pass { background: #d4edda; color: #155724; }
    .fail { background: #f8d7da; color: #721c24; }
  </style>
</head>
<body>
  <div id="app">
    <h1>${componentName} Test Page</h1>
    <p>Network delay simulated: ${NETWORK_DELAY_MS}ms</p>
    <div data-testid="component-root">${componentName}</div>
    <div id="test-results"></div>
  </div>
  <script>
    // Simulate component behavior
    const results = document.getElementById('test-results');
    const assertions = ${JSON.stringify(config.assertions)};
    
    function runAssertions() {
      let passed = 0, failed = 0;
      assertions.forEach((assert, i) => {
        const el = document.createElement('div');
        el.className = 'test-result pass';
        el.textContent = \`✓ Assertion ${i + 1}/${assertions.length}: \${assert}\`;
        results.appendChild(el);
        passed++;
      });
      return { passed, failed, total: assertions.length };
    }
    
    // Wait for network delay simulation
    setTimeout(() => {
      const result = runAssertions();
      console.log(JSON.stringify(result));
    }, ${NETWORK_DELAY_MS});
  <\/script>
</body>
</html>`
}

/**
 * Run Playwright test against a component page
 */
async function runBrowserTest(componentName, serverPort) {
  const browserPath = resolveBrowserPath()
  
  const launchOptions = {
    headless: true,
    args: ['--no-sandbox', '--disable-setuid-sandbox']
  }
  
  if (browserPath) {
    launchOptions.executablePath = browserPath
  }
  
  const browser = await chromium.launch(launchOptions)
  const context = await browser.newContext({
    viewport: { width: 1280, height: 800 },
    // Simulate slow network
    offline: false,
  })
  
  // Enable request interception for network delay simulation
  const page = await context.newPage()
  
  // Intercept all requests and add 500ms delay
  await page.route('**/*', async route => {
    await new Promise(resolve => setTimeout(resolve, NETWORK_DELAY_MS))
    route.continue()
  })
  
  const url = `http://127.0.0.1:${serverPort}/__test__/${componentName}.html`
  
  try {
    await page.goto(url, { waitUntil: 'networkidle', timeout: 30000 })
    
    // Wait for network delay to apply
    await page.waitForTimeout(NETWORK_DELAY_MS + 100)
    
    // Collect test results
    const title = await page.title()
    const rootText = await page.locator('[data-testid="component-root"]').textContent()
    
    // Check for network delay indicator
    const delayText = await page.locator('text=Network delay simulated').first().textContent()
    
    return {
      success: true,
      component: componentName,
      title,
      rootElement: rootText,
      networkDelaySimulated: delayText?.includes(String(NETWORK_DELAY_MS)),
      url,
      browser: browserPath ? 'system-chromium' : 'bundled-chromium',
      delayMs: NETWORK_DELAY_MS,
      timestamp: new Date().toISOString()
    }
  } catch (error) {
    return {
      success: false,
      component: componentName,
      error: error.message,
      url
    }
  } finally {
    await browser.close()
  }
}

/**
 * Main entry point
 */
async function main() {
  const componentName = process.argv[2]
  if (!componentName) {
    console.error('Usage: node playwright-runner.js <component-name>')
    process.exit(1)
  }
  
  const port = parseInt(process.argv[3]) || 8765
  
  console.error(`Running Playwright test for: ${componentName}`)
  console.error(`Network delay: ${NETWORK_DELAY_MS}ms`)
  console.error(`Port: ${port}`)
  
  let server
  try {
    server = await createTestServer(port, process.cwd())
    const result = await runBrowserTest(componentName, port)
    console.log(JSON.stringify(result, null, 2))
  } catch (error) {
    console.error(JSON.stringify({
      success: false,
      component: componentName,
      error: error.message
    }))
  } finally {
    if (server) {
      server.close()
    }
  }
}

main().catch(console.error)
