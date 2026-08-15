#!/usr/bin/env node
/**
 * Playwright Vue Component Tester - Plugin Loader
 * 
 * This script loads the Cordis plugin directly and registers it.
 * Usage: node scripts/load-plugin.js
 * 
 * Alternatively, add to cordis.patch.yml:
 *   - insert:
 *       - id: playwright-vue-tester
 *         name: ./plugins/playwright-vue-tester.host.js
 */

const path = require('path');
const fs = require('fs');

// Resolve plugin paths
const workspaceRoot = process.cwd();
const pluginHostPath = path.join(workspaceRoot, 'plugins', 'playwright-vue-tester.host.js');
const pluginClientPath = path.join(workspaceRoot, 'plugins', 'playwright-vue-tester.client.js');

console.log('Loading Playwright Vue Tester plugin...');
console.log('Workspace:', workspaceRoot);
console.log('Host plugin:', pluginHostPath);
console.log('Client plugin:', pluginClientPath);

// Check if Playwright is available
let playwright;
try {
  playwright = require('playwright');
  console.log('Playwright version:', playwright?.version || 'unknown');
} catch (e) {
  console.error('Playwright not found. Install with: pnpm add -D playwright @playwright/test');
  process.exit(1);
}

// Check if Chromium is installed
async function checkBrowser() {
  const { chromium } = playwright;
  if (!chromium) {
    console.error('Chromium not available in Playwright');
    return false;
  }
  
  try {
    // Try to launch browser to verify installation
    const browser = await chromium.launch({ headless: true });
    await browser.close();
    console.log('Chromium browser available');
    return true;
  } catch (e) {
    console.error('Chromium not installed. Run: pnpm exec playwright install chromium');
    console.error('Error:', e.message);
    return false;
  }
}

// Load and execute plugin
function loadPlugin() {
  if (!fs.existsSync(pluginHostPath)) {
    console.error('Host plugin not found:', pluginHostPath);
    process.exit(1);
  }
  
  const pluginCode = fs.readFileSync(pluginHostPath, 'utf8');
  
  // Create a simple context mock for testing
  const mockContext = {
    get: (name) => {
      console.log(`Context get: ${name}`);
      return null;
    },
    effect: (fn, label) => {
      console.log(`Effect registered: ${label}`);
      return fn;
    }
  };
  
  // Execute plugin code
  try {
    const pluginFactory = eval(pluginCode);
    console.log('Plugin factory loaded successfully');
    console.log('Plugin type:', typeof pluginFactory);
    
    // The plugin should return an object with apply method
    if (typeof pluginFactory === 'function') {
      const plugin = pluginFactory(mockContext);
      console.log('Plugin created:', plugin ? 'yes' : 'no');
    }
  } catch (e) {
    console.error('Failed to load plugin:', e.message);
    console.error(e.stack);
    process.exit(1);
  }
}

// Main
async function main() {
  const available = await checkBrowser();
  
  if (!available) {
    console.log('\nTo complete setup:');
    console.log('  pnpm exec playwright install chromium');
  }
  
  console.log('\nLoading plugin...');
  loadPlugin();
  
  console.log('\nPlugin loaded. To integrate with DSH:');
  console.log('1. Edit C:\\Users\\lenovo\\.dsh\\profiles\\web\\cordis.patch.yml');
  console.log('2. Add the plugin entry under - insert:');
  console.log('3. Restart DSH to load the plugin');
}

main().catch(console.error);
