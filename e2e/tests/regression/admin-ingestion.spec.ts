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
    await expect(page).toHaveURL('/');
  });

  test(scenario('ADMIN-ING-001', 'admin page loads for ADMIN role'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page).toHaveURL(/\/admin/);
    await expect(page.getByRole('heading', { name: 'Admin Panel' })).toBeVisible();
  });

  test(scenario('ADMIN-ING-002', 'ingestion stats display'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('.admin-stats-grid')).toBeVisible();
  });

  test(scenario('ADMIN-ING-003', 'workflows table visible'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.locator('.admin-workflows-section')).toBeVisible();
  });

  test(scenario('ADMIN-ING-004', 'run history accessible'), async ({ page }) => {
    await page.goto('/admin');
    await expect(page.getByText('Recent Runs')).toBeVisible();
  });
});
