import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Dashboard Overview', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
  });

  test(scenario('DASH-OVERVIEW-001', 'dashboard loads and displays KPI cards'), async ({ page }) => {
    await expect(page.locator('text=Total Value')).toBeVisible();
    await expect(page.locator('text=Buying Power')).toBeVisible();
    await expect(page.locator('text=Returns')).toBeVisible();
    await expect(page.locator('text=Sectors')).toBeVisible();
  });

  test(scenario('DASH-OVERVIEW-002', 'positions table renders on dashboard'), async ({ page }) => {
    // Re-enabled 2026-09-19 after the owner refreshed UAT broker data (the sync was
    // disabled and the stored aggregates were stale since 2026-08-28). Verified live:
    // the dashboard Positions section renders the AG Grid with 25 positions.
    const grid = page.locator('.ag-root-wrapper, [role="grid"]').first();
    await expect(grid).toBeVisible();
  });

  test(scenario('DASH-OVERVIEW-003', 'activities tab renders when individual account is selected'), async ({ page }) => {
    const accountPills = page.locator('.account-nav__pill');
    const accountCount = await accountPills.count();
    test.skip(accountCount <= 1, 'Need at least 2 accounts to test switching');

    await accountPills.nth(1).click();
    await expect(page.locator('text=Activities')).toBeVisible();

    await page.locator('text=Activities').click();
    const activitiesGrid = page.locator('.ag-root-wrapper, [role="grid"]').first();
    await expect(activitiesGrid).toBeVisible();
  });

  test(scenario('DASH-OVERVIEW-004', 'account switching toggles between all accounts and individual account'), async ({ page }) => {
    const width = page.viewportSize()?.width ?? 0;
    test.skip(width > 0 && width < 769, 'The account pill switcher is desktop-only (AccountNavBar.css:190-196 hides .account-nav__pills below 769px); mobile uses the account sheet');
    const allAccountsBtn = page.locator('.account-nav__pill', { hasText: 'All Accounts' });
    await expect(allAccountsBtn).toBeVisible();

    const accountPills = page.locator('.account-nav__pill:not(:has-text("All Accounts"))');
    const accountCount = await accountPills.count();
    test.skip(accountCount === 0, 'No individual accounts available');

    await accountPills.first().click();
    await expect(page.locator('text=Positions')).toBeVisible();
    await expect(page.locator('text=Activities')).toBeVisible();
    await expect(page.locator('text=Dividends')).toBeVisible();

    await allAccountsBtn.click();
    await expect(page.locator('.account-nav__pill--active', { hasText: 'All Accounts' })).toBeVisible();
  });
});
