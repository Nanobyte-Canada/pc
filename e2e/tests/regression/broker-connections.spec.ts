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
    const cards = page.locator('[class*="card"], [data-testid*="connection"], [role="article"]');
    await expect(cards.first()).toBeVisible();
  });

  test(scenario('BROKER-CONN-003', 'connect button is visible'), async ({ page }) => {
    const connectBtn = page.locator('button:has-text("Connect"), [data-testid*="connect"], a:has-text("Connect")');
    await expect(connectBtn).toBeVisible();
  });

  test(scenario('BROKER-CONN-004', 'positions tab is accessible'), async ({ page }) => {
    await page.goto('/brokers/positions');
    const positionsTab = page.locator('[role="tab"]:has-text("Positions"), button:has-text("Positions"), a:has-text("Positions"), [data-testid*="positions-tab"]');
    await expect(positionsTab).toBeVisible();
  });
});
