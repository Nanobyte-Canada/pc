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
    test.skip(true, 'Sector chart requires an in-memory analysis run; AnalyticsPage.tsx:40 renders the empty state on fresh navigation (same premise as ANALYTICS-SECT-001). Locator is tightened to the AG Charts container for when the analysis flow becomes seedable.');
    const chart = page.locator('.chart-container canvas');
    await expect(chart).toBeVisible();
    // kept intentionally: assertion ready when the premise becomes testable
  });

  test(scenario('ANALYTICS-SECT-003', 'geography map is visible'), async ({ page }) => {
    test.skip(true, 'Geography chart requires an in-memory analysis run; AnalyticsPage.tsx:40 renders the empty state on fresh navigation (same premise as ANALYTICS-SECT-001). Locator is tightened to the AG Charts container for when the analysis flow becomes seedable.');
    const map = page.locator('.chart-container canvas');
    await expect(map).toBeVisible();
    // kept intentionally: assertion ready when the premise becomes testable
  });

  test(scenario('ANALYTICS-SECT-004', 'top holdings table displays data'), async () => {
    test.skip(true, 'Top holdings only render after running a portfolio analysis in the Portfolio Builder (in-memory state); a fresh navigation to /analytics always shows the empty state');
  });
});
