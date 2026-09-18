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

  test(scenario('ANALYTICS-SECT-001', 'analytics page loads and displays sector exposure'), async ({ page }) => {
    await expect(page).toHaveURL(/\/analytics/);
    await expect(page.locator('text=Sectors, text=Sector')).toBeVisible();
  });

  test(scenario('ANALYTICS-SECT-002', 'sector exposure chart renders'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="sector"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });

  test(scenario('ANALYTICS-SECT-003', 'geography map is visible'), async ({ page }) => {
    const map = page.locator('canvas, svg, [data-testid*="map"], [data-testid*="geo"], .map-container');
    await expect(map).toBeVisible();
  });

  test(scenario('ANALYTICS-SECT-004', 'top holdings table displays data'), async ({ page }) => {
    const holdings = page.locator('table, [role="grid"], .ag-root-wrapper, [data-testid*="holding"]');
    await expect(holdings).toBeVisible();
  });
});
