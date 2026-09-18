import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

test.describe('Wheel - Calendar', () => {
  test.beforeEach(async ({ page }) => {
    await page.goto('/wheel');
  });

  test(scenario('WHEEL-CAL-001', 'wheel page loads successfully @regression'), async ({ page }) => {
    await expect(page).toHaveURL(/\/wheel/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('WHEEL-CAL-002', 'calendar grid renders @regression'), async ({ page }) => {
    const grid = page.locator('[class*="calendar"], [data-testid*="calendar"], table, [role="grid"]');
    await expect(grid).toBeVisible();
  });

  test(scenario('WHEEL-CAL-003', 'KPIs display @regression'), async ({ page }) => {
    const kpis = page.locator('[class*="kpi"], [data-testid*="kpi"], [class*="stat"], [class*="metric"]');
    await expect(kpis.first()).toBeVisible();
  });

  test(scenario('WHEEL-CAL-004', 'top tickers table is visible @regression'), async ({ page }) => {
    const tickersTable = page.locator('table, [role="table"], [data-testid*="ticker"]');
    await expect(tickersTable).toBeVisible();
  });
});
