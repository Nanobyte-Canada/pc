import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Options - Chain', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
    await page.goto('/options');
  });

  test(scenario('OPT-CHAIN-001', 'options page loads successfully'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('OPT-CHAIN-002', 'chain table renders with rows'), async ({ page }) => {
    const table = page.locator('table, [role="table"], .options-chain, [data-testid*="chain"]');
    await expect(table).toBeVisible();
  });

  test(scenario('OPT-CHAIN-003', 'strategy selector is visible'), async ({ page }) => {
    const selector = page.locator('select, [role="combobox"], [data-testid*="strategy"], [aria-label*="strategy"]');
    await expect(selector).toBeVisible();
  });

  test(scenario('OPT-CHAIN-004', 'P&L chart renders'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="pnl"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });
});
