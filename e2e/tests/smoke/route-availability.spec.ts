import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';

test(scenario('PLATFORM-ROUTE-001', 'login page renders'), async ({ page }) => {
  await page.goto('/login');
  await expect(page).toHaveTitle(/portfolio/i);
  await expect(page.locator('text=Sign In')).toBeVisible();
});

test(scenario('PLATFORM-ROUTE-002', 'unauthenticated user redirects to login'), async ({ page }) => {
  await page.goto('/');
  await expect(page).toHaveURL(/\/login/);
});
