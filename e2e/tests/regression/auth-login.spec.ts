import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Authentication - Login', () => {
  const email = process.env.E2E_USER_EMAIL;
  const password = process.env.E2E_USER_PASSWORD;

  test(scenario('AUTH-LOGIN-001', 'successful login with valid credentials'), async ({ page }) => {
    test.skip(!email || !password, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
  });

  test(scenario('AUTH-LOGIN-002', 'login form shows validation for empty fields'), async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await page.click('button[type="submit"]');
    await expect(page.locator('[role="alert"], .error')).toBeVisible();
  });

  test(scenario('AUTH-LOGIN-003', 'login shows error for invalid credentials'), async ({ page }) => {
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login('invalid@test.com', 'wrongpassword');
    await expect(page.locator('[role="alert"], .error, text=Invalid')).toBeVisible();
  });
});
