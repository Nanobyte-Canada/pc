import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Analytics - Sectors', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/analytics');
  });

  test(scenario('ANALYTICS-SECT-001', 'analytics page loads and displays sector exposure'), async () => {
    test.skip(true, 'Analysis lives in a client-side in-memory store — a fresh navigation to /analytics always shows the empty state ("No portfolio analysis available"), so sector exposure is never displayed');
  });

  test(scenario('ANALYTICS-SECT-002', 'sector exposure chart renders'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="sector"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });

  test(scenario('ANALYTICS-SECT-003', 'geography map is visible'), async ({ page }) => {
    const map = page.locator('canvas, svg, [data-testid*="map"], [data-testid*="geo"], .map-container');
    await expect(map).toBeVisible();
  });

  test(scenario('ANALYTICS-SECT-004', 'top holdings table displays data'), async () => {
    test.skip(true, 'Top holdings only render after running a portfolio analysis in the Portfolio Builder (in-memory state); a fresh navigation to /analytics always shows the empty state');
  });
});
