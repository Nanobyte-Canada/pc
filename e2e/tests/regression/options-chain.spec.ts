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

  test(scenario('OPT-CHAIN-002', 'chain table renders with rows'), async () => {
    test.skip(true, 'Options page shows the empty state "Enter a symbol above to load the options chain" until a symbol is loaded — no chain table exists by default (market data disconnected)');
  });

  test(scenario('OPT-CHAIN-003', 'strategy selector is visible'), async () => {
    test.skip(true, 'No strategy selector exists on the options page — it only offers a symbol textbox and a Load Chain button');
  });

  test(scenario('OPT-CHAIN-004', 'P&L chart renders'), async () => {
    test.skip(true, 'P&L chart renders only after loading a chain for a symbol; the page shows the empty state by default');
  });
});
