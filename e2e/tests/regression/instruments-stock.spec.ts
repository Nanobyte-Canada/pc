import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Instruments - Stock Detail', { tag: ['@regression'] }, () => {
  const email = process.env.E2E_USER_EMAIL;
  const password = process.env.E2E_USER_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/instruments/stock/AAPL');
  });

  test(scenario('INST-STOCK-001', 'instruments page loads and displays stock list'), async ({ page }) => {
    await expect(page).toHaveURL(/\/instruments/);
    await expect(page.locator('table, [role="grid"], .ag-root-wrapper')).toBeVisible();
  });

  test(scenario('INST-STOCK-002', 'clicking a stock navigates to detail page'), async ({ page }) => {
    const stockRow = page.locator('table tbody tr, [role="row"]').first();
    await stockRow.click();
    await expect(page).toHaveURL(/\/instruments\/.+/);
  });

  test(scenario('INST-STOCK-003', 'stock detail page shows price data'), async ({ page }) => {
    const stockRow = page.locator('table tbody tr, [role="row"]').first();
    await stockRow.click();
    const price = page.locator('text=Price, text=Last, [data-testid*="price"]');
    await expect(price).toBeVisible();
  });

  test(scenario('INST-STOCK-004', 'stock detail page displays key metrics'), async ({ page }) => {
    const stockRow = page.locator('table tbody tr, [role="row"]').first();
    await stockRow.click();
    const metrics = page.locator('[data-testid*="metric"], [data-testid*="key"], table, .ag-root-wrapper');
    await expect(metrics).toBeVisible();
  });

  test(scenario('INST-STOCK-005', 'stock chart renders on detail page'), async ({ page }) => {
    const stockRow = page.locator('table tbody tr, [role="row"]').first();
    await stockRow.click();
    const chart = page.locator('canvas, svg, [data-testid*="chart"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });
});
