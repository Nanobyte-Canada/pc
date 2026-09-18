import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';

test.describe('Options - Chain', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/options');
  });

  test(scenario('OPT-CHAIN-001', 'options page loads successfully @regression'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('OPT-CHAIN-002', 'chain table renders with rows @regression'), async ({ page }) => {
    const table = page.locator('table, [role="table"], .options-chain, [data-testid*="chain"]');
    await expect(table).toBeVisible();
  });

  test(scenario('OPT-CHAIN-003', 'strategy selector is visible @regression'), async ({ page }) => {
    const selector = page.locator('select, [role="combobox"], [data-testid*="strategy"], [aria-label*="strategy"]');
    await expect(selector).toBeVisible();
  });

  test(scenario('OPT-CHAIN-004', 'P&L chart renders @regression'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="pnl"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });
});
