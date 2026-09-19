import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Wheel - Calendar', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
    await page.goto('/wheel');
  });

  test(scenario('WHEEL-CAL-001', 'wheel page loads successfully'), async ({ page }) => {
    await expect(page).toHaveURL(/\/wheel/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('WHEEL-CAL-002', 'calendar grid renders'), async ({ page }) => {
    const grid = page.getByRole('table');
    await expect(grid).toBeVisible();
  });

  test(scenario('WHEEL-CAL-003', 'KPIs display'), async ({ page }) => {
    const kpis = page.getByText('Capital Available');
    await expect(kpis).toBeVisible();
  });

  test(scenario('WHEEL-CAL-004', 'top tickers table is visible'), async ({ page }) => {
    const tickersBar = page.getByRole('button', { name: 'Add Ticker' });
    await expect(tickersBar).toBeVisible();
  });
});
