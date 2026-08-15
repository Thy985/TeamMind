import { test, expect } from '@playwright/test';

test.describe('CounterWidget', () => {
  test.beforeEach(async ({ page }) => {
    // Simulate 500ms network delay on all requests
    await page.route('**/*', async route => {
      await new Promise(r => setTimeout(r, 500));
      route.continue();
    });
    await page.goto('/__test__/CounterWidget');
  });

  test('should have correct initial state', async ({ page }) => {
    await expect(page.locator('[data-testid="component-root"]')).toBeVisible();
    await expect(page.locator('[data-testid="delay-indicator"]')).toContainText('500ms');
  });

  test('should pass all assertions', async ({ page }) => {
    const assertions = page.locator('[data-testid^="assertion-"]');
    await expect(assertions).toHaveCount(4);
    await expect(assertions.nth(0)).toContainText('count-initial-0');
    await expect(assertions.nth(1)).toContainText('button-increment');
    await expect(assertions.nth(2)).toContainText('button-decrement');
    await expect(assertions.nth(3)).toContainText('doubled-value');
  });

  test('should account for network delay in total time', async ({ page }) => {
    const startTime = Date.now();
    await page.reload();
    const elapsed = Date.now() - startTime;
    // Should take at least the simulated delay
    expect(elapsed).toBeGreaterThanOrEqual(400);
  });
});

test.describe('AsyncLoader', () => {
  test.beforeEach(async ({ page }) => {
    await page.route('**/*', async route => {
      await new Promise(r => setTimeout(r, 500));
      route.continue();
    });
    await page.goto('/__test__/AsyncLoader');
  });

  test('should display component after delay', async ({ page }) => {
    await expect(page.locator('[data-testid="component-root"]')).toBeVisible();
  });

  test('should show delay indicator', async ({ page }) => {
    await expect(page.locator('[data-testid="delay-indicator"]')).toBeVisible();
  });
});
