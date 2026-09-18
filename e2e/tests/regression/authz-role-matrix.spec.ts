import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

const USER_ROUTES = ['/', '/portfolios', '/options', '/wheel', '/brokers/connections'];
const ALL_ROUTES = [...USER_ROUTES, '/admin'];

test.describe('Authorization - Role Matrix @regression', () => {
  const userEmail = process.env.E2E_USER_EMAIL;
  const userPassword = process.env.E2E_USER_PASSWORD;
  const adminEmail = process.env.E2E_ADMIN_EMAIL;
  const adminPassword = process.env.E2E_ADMIN_PASSWORD;

  test.describe('USER role', () => {
    test.beforeEach(async ({ page }) => {
      test.skip(!userEmail || !userPassword, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(userEmail!, userPassword!);
    });

    for (const route of USER_ROUTES) {
      test(scenario(`AUTHZ-MATRIX-001`, `USER can access ${route}`), async ({ page }) => {
        await page.goto(route);
        await expect(page).not.toHaveURL(/\/login/);
      });
    }

    test(scenario('AUTHZ-MATRIX-002', 'USER cannot access admin page'), async ({ page }) => {
      await page.goto('/admin');
      await expect(page).toHaveURL(/\/(login|$)/);
    });
  });

  test.describe('ADMIN role', () => {
    test.beforeEach(async ({ page }) => {
      test.skip(!adminEmail || !adminPassword, 'E2E_ADMIN_EMAIL/E2E_ADMIN_PASSWORD not set');
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(adminEmail!, adminPassword!);
    });

    for (const route of ALL_ROUTES) {
      test(scenario(`AUTHZ-MATRIX-003`, `ADMIN can access ${route}`), async ({ page }) => {
        await page.goto(route);
        await expect(page).not.toHaveURL(/\/login/);
      });
    }
  });

  test.describe('Unauthenticated', () => {
    for (const route of ALL_ROUTES) {
      test(scenario(`AUTHZ-MATRIX-004`, `unauthenticated user redirects to login for ${route}`), async ({ page }) => {
        await page.goto(route);
        await expect(page).toHaveURL(/\/login/);
      });
    }
  });
});
