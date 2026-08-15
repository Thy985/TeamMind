# Playwright Vue Component Tester

A Cordis plugin and standalone script for browser-based testing of Vue components using Playwright, with simulated network delay.

## Features

- **Real Browser Rendering**: Tests Vue components in a real Chromium browser
- **Network Delay Simulation**: Configurable network delay (default: 500ms) using Playwright's request interception
- **Cordis Integration**: Available as a dynamic tool (`playVuetest`) when the plugin is active
- **Standalone Mode**: Can run independently via Node.js

## Files

| File | Purpose |
|------|---------|
| `plugins/playwright-vue-tester.host.js` | Cordis Host plugin (Playwright automation) |
| `plugins/playwright-vue-tester.client.js` | Cordis Client plugin (UI panel) |
| `scripts/playwright-test-runner.js` | Standalone test runner script |
| `src/components/CounterWidget.vue` | Test Vue component |
| `src/components/AsyncLoader.vue` | Test Vue component |

## Prerequisites

```bash
# Install Playwright
pnpm add -D playwright @playwright/test

# Install Chromium browser
pnpm exec playwright install chromium
```

## Usage

### Via Cordis Plugin

The plugin registers a dynamic tool `playVuetest` when loaded:

```js
// In your Cordis composition or via cordis_define
{
  name: 'playwright-vue-tester',
  code: {
    host: fs.readFileSync('plugins/playwright-vue-tester.host.js', 'utf8'),
    client: fs.readFileSync('plugins/playwright-vue-tester.client.js', 'utf8')
  }
}
```

Then call the tool:
```
playVuetest(component: "CounterWidget", delayMs: 500, headless: true)
```

### Via Standalone Script

```bash
# Run test for CounterWidget with default 500ms delay
node scripts/playwright-test-runner.js CounterWidget

# Run with custom delay
node scripts/playwright-test-runner.js AsyncLoader --delay=1000

# Run in headed mode (visible browser)
node scripts/playwright-test-runner.js CounterWidget --headed
```

### Via Environment Variables

```bash
DELAY_MS=1000 TEST_PORT=9999 node scripts/playwright-test-runner.js CounterWidget
```

## Test Components

### CounterWidget
A simple counter component with increment/decrement buttons and a doubled value display.

Assertions:
- `count-initial-0` - Initial count is 0
- `button-increment` - Increment button exists
- `button-decrement` - Decrement button exists
- `doubled-value` - Doubled value is correct

### AsyncLoader
A component that simulates async data loading.

Assertions:
- `async-message` - Message is displayed after loading
- `loading-state` - Loading state is transient
- `reload-button` - Reload button works

## Network Delay Simulation

The plugin simulates network delay by intercepting all page requests and adding a configurable delay:

```javascript
await page.route('**/*', async route => {
  await new Promise(r => setTimeout(r, delayMs));
  route.continue();
});
```

This allows testing how components behave under slow network conditions.

## Troubleshooting

### Browser not found
```bash
pnpm exec playwright install chromium
```

### Playwright not available
Ensure Playwright is installed in the project:
```bash
pnpm add -D playwright @playwright/test
```

### Port already in use
Change the port via environment variable:
```bash
TEST_PORT=9999 node scripts/playwright-test-runner.js CounterWidget
```
