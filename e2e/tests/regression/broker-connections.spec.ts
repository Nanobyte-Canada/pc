import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Broker - Connections', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
    await page.goto('/brokers/connections');
  });

  test(scenario('BROKER-CONN-001', 'broker page loads successfully'), async ({ page }) => {
    await expect(page).toHaveURL(/\/broker/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('BROKER-CONN-002', 'connection cards display'), async ({ page }) => {
    // Real page: broker cards render as buttons named after the broker
    // (aria: button "Q QUESTRADE 3 Accounts Connected" / button "W WEALTHSIMPLE")
    const cards = page.getByRole('button', { name: /QUESTRADE|WEALTHSIMPLE/ });
    await expect(cards.first()).toBeVisible();
  });

  test(scenario('BROKER-CONN-003', 'connect button is visible'), async ({ page }) => {
    // Real page has no control labelled "Connect" — the broker cards in
    // "Available Brokers" open the ConnectBrokerDialog when clicked.
    // (Old selector matched the hidden bottom-tab "Connections" nav button.)
    const connectBtn = page.getByRole('button', { name: /QUESTRADE|WEALTHSIMPLE/ });
    await expect(connectBtn.first()).toBeVisible();
  });

  test(scenario('BROKER-CONN-004', 'positions tab is accessible'), async ({ page }) => {
    await page.goto('/brokers/positions');
    // /brokers/positions is a standalone page: heading "Portfolio Positions",
    // All/By Broker toggle, positions treegrid — there is no "Positions" tab.
    await expect(page.getByRole('heading', { name: 'Portfolio Positions' })).toBeVisible();
  });
});
