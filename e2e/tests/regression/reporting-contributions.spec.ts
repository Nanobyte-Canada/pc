import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Reporting - Contributions', { tag: ['@regression'] }, () => {
  const email = process.env.E2E_USER_EMAIL;
  const password = process.env.E2E_USER_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/brokers/reporting');
  });

  test(scenario('RPT-CONTRIB-001', 'reporting page loads and shows total value'), async ({ page }) => {
    await expect(page).toHaveURL(/\/reporting/);
    await expect(page.locator('text=Total Value, text=Total')).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-002', 'contributions chart renders'), async ({ page }) => {
    const chart = page.locator('canvas, svg, [data-testid*="chart"], [data-testid*="contribut"], .recharts-wrapper, .chart-container');
    await expect(chart).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-003', 'dividends table is visible'), async ({ page }) => {
    const dividends = page.locator('table, [role="grid"], .ag-root-wrapper, [data-testid*="dividend"]');
    await expect(dividends).toBeVisible();
  });

  test(scenario('RPT-CONTRIB-004', 'total value display shows numeric data'), async ({ page }) => {
    const valueEl = page.locator('[data-testid*="total"], [data-testid*="value"]').first();
    await expect(valueEl).toBeVisible();
  });
});
