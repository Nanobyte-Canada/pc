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
    // Login is async: settle on the authenticated shell before visiting guarded
    // routes, otherwise ProtectedRoute redirects to /login and the login page renders.
    await expect(page).toHaveURL('/');
    await page.goto('/screener/stocks');
    await expect(page).toHaveURL('/screener/stocks');
  });

  test(scenario('SCREENER-001', 'screener page loads with header and instrument count'), async ({ page }) => {
    await expect(page.getByRole('heading', { level: 1, name: 'Stocks' })).toBeVisible();
    await expect(page.locator('.screener-results-count')).toBeVisible();
  });

  test(scenario('SCREENER-002', 'filter panel is visible with filter controls'), async ({ page }) => {
    const filterPanel = page.locator('.screener-filters-card');
    await expect(filterPanel).toBeVisible();
    await expect(filterPanel.getByRole('button', { name: 'Filters' })).toBeVisible();
    await expect(filterPanel.getByPlaceholder('e.g. AAPL')).toBeVisible();
    await expect(filterPanel.getByRole('button', { name: 'Apply' })).toBeVisible();
    await expect(filterPanel.getByRole('button', { name: 'Reset' })).toBeVisible();
  });

  test(scenario('SCREENER-003', 'search input filters results by ticker'), async ({ page }) => {
    const searchInput = page.getByPlaceholder(/quick search by ticker/i);
    await expect(searchInput).toBeVisible();

    await searchInput.fill('AAPL');
    await searchInput.press('Enter');

    // Applying the ticker filter renders an active-filter chip and keeps the results grid mounted.
    await expect(page.locator('.filter-chip', { hasText: 'AAPL' })).toBeVisible();
    await expect(page.locator('.screener-grid-container')).toBeVisible();
  });

  test(scenario('SCREENER-004', 'results display in a data grid'), async ({ page }) => {
    const grid = page.locator('.screener-grid-container');
    await expect(grid).toBeVisible();
    await expect(grid.locator('.ag-root-wrapper')).toBeVisible();

    const rows = grid.locator('.ag-row, [role="row"]');
    await expect(rows.first()).toBeVisible();
  });
});
