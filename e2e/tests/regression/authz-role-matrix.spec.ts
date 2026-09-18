import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

const USER_ROUTES = ['/', '/portfolios', '/options', '/wheel', '/brokers/connections'];
const ALL_ROUTES = [...USER_ROUTES, '/admin'];

test.describe('Authorization - Role Matrix @regression', () => {
  // Single admin test account (owner decision): one credential pair is used
  // for all role scenarios because the configured test account has access to
  // both admin and user features.
  const adminEmail = process.env.APP_TEST_ADMIN_EMAIL;
  const adminPassword = process.env.APP_TEST_ADMIN_PASSWORD;

  test.describe('USER role', () => {
    test.beforeEach(async ({ page }) => {
      test.skip(!adminEmail || !adminPassword, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
      const loginPage = new LoginPage(page);
      await loginPage.goto();
      await loginPage.login(adminEmail!, adminPassword!);
    });

    for (const route of USER_ROUTES) {
      test(scenario(`AUTHZ-MATRIX-001`, `USER can access ${route}`), async ({ page }) => {
        await page.goto(route);
        await expect(page).not.toHaveURL(/\/login/);
      });
    }

    test(scenario('AUTHZ-MATRIX-002', 'USER cannot access admin page'), async () => {
      test.skip(
        true,
        'Requires a dedicated non-admin test account; only APP_TEST_ADMIN_* credentials are configured (owner decision: single admin account for all suites). Re-enable when a USER-role test account exists.'
      );
    });
  });

  test.describe('ADMIN role', () => {
    test.beforeEach(async ({ page }) => {
      test.skip(!adminEmail || !adminPassword, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
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
