import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';

test.describe('Broker - Connections', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/brokers/connections');
  });

  test(scenario('BROKER-CONN-001', 'broker page loads successfully @regression'), async ({ page }) => {
    await expect(page).toHaveURL(/\/broker/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('BROKER-CONN-002', 'connection cards display @regression'), async ({ page }) => {
    const cards = page.locator('[class*="card"], [data-testid*="connection"], [role="article"]');
    await expect(cards.first()).toBeVisible();
  });

  test(scenario('BROKER-CONN-003', 'connect button is visible @regression'), async ({ page }) => {
    const connectBtn = page.locator('button:has-text("Connect"), [data-testid*="connect"], a:has-text("Connect")');
    await expect(connectBtn).toBeVisible();
  });

  test(scenario('BROKER-CONN-004', 'positions tab is accessible @regression'), async ({ page }) => {
    await page.goto('/brokers/positions');
    const positionsTab = page.locator('[role="tab"]:has-text("Positions"), button:has-text("Positions"), a:has-text("Positions"), [data-testid*="positions-tab"]');
    await expect(positionsTab).toBeVisible();
  });
});
