import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Admin - Ingestion @regression', () => {
  const adminEmail = process.env.APP_TEST_ADMIN_EMAIL;
  const adminPassword = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!adminEmail || !adminPassword, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(adminEmail!, adminPassword!);
  });

  test(scenario('ADMIN-ING-001', 'admin page loads for ADMIN role'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/admin/);
    await expect(page.locator('h1, h2, [role="heading"]')).toBeVisible();
  });

  test(scenario('ADMIN-ING-002', 'ingestion stats display'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('text=Ingestion')).toBeVisible();
  });

  test(scenario('ADMIN-ING-003', 'workflows table visible'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('table, [role="table"], [class*="table"]')).toBeVisible();
  });

  test(scenario('ADMIN-ING-004', 'run history accessible'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('text=Run, text=History, text=Recent')).toBeVisible();
  });
});
