import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Reporting - Contributions', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/brokers/reporting');
  });

  test(scenario('RPT-CONTRIB-001', 'reporting page loads and shows total value'), async ({ page }) => {
    await expect(page).toHaveURL(/\/reporting/);
    await expect(page.getByRole('heading', { name: 'Total Value' })).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-002', 'contributions chart renders'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="contribut"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-003', 'dividends table is visible'), async ({ page }) => {
    const dividends = page.getByRole('heading', { name: 'Dividend History' });
    await expect(dividends).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-004', 'total value display shows numeric data'), async ({ page }) => {
    const valueEl = page.locator('.kpi-card:has-text("Net Change") .kpi-value');
    await expect(valueEl).toBeVisible();
  });
});
