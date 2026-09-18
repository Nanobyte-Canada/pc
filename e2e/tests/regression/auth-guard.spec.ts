import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';

test.describe('Authentication - Guard', () => {
  test(scenario('AUTH-GUARD-001', 'unauthenticated user redirects to login'), async ({ page }) => {
    await page.goto('/');
    await expect(page).toHaveURL(/\/login/);
  });

  test(scenario('AUTH-GUARD-002', 'protected route redirects unauthenticated user'), async ({ page }) => {
    await page.goto('/portfolios');
    await expect(page).toHaveURL(/\/login/);
  });

  test.skip(scenario('AUTH-GUARD-003', 'admin route accessible only to ADMIN role'), 'TODO: implement admin role gate test — requires user role fixture');
});
