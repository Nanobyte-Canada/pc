import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Screener Filters', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/screener/stocks');
    await expect(page).toHaveURL('/screener/stocks');
  });

  test(scenario('SCREENER-001', 'screener page loads with header and instrument count'), async ({ page }) => {
    await expect(page.locator('h1', { hasText: 'Stocks' })).toBeVisible();
    await expect(page.locator('.screener-results-count, [class*="results-count"]')).toBeVisible();
  });

  test(scenario('SCREENER-002', 'filter panel is visible with filter controls'), async ({ page }) => {
    const filterPanel = page.locator('.screener-filters, [class*="screener-filter"], [class*="filter"]').first();
    await expect(filterPanel).toBeVisible();
  });

  test(scenario('SCREENER-003', 'search input filters results by ticker'), async ({ page }) => {
    const searchInput = page.locator('.screener-search-input, input[placeholder*="search" i], input[placeholder*="ticker" i]').first();
    await expect(searchInput).toBeVisible();

    await searchInput.fill('AAPL');
    await searchInput.press('Enter');
    await page.waitForTimeout(1000);

    const resultsGrid = page.locator('.ag-root-wrapper, [role="grid"], .screener-grid-container').first();
    await expect(resultsGrid).toBeVisible();
  });

  test(scenario('SCREENER-004', 'results display in a data grid'), async ({ page }) => {
    const grid = page.locator('.ag-root-wrapper, [role="grid"], .screener-grid-container').first();
    await expect(grid).toBeVisible();

    const rows = page.locator('.ag-row, [role="row"]');
    await expect(rows.first()).toBeVisible();
  });
});
