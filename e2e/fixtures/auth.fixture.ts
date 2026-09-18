import { test as base } from '@playwright/test';

export const test = base.extend<{ authenticatedPage: void }>({
  authenticatedPage: [async ({ page, baseURL }, use) => {
    // Login with the single UAT admin test account from CI secrets (no committed seed
    // accounts exist in this repo). Per owner decision, this one account is used for
    // all authenticated suites — it has access to both admin and user features.
    // Sessions are cookie-based; the browser holds the session cookie after form
    // login — no token handling.
    const email = process.env.APP_TEST_ADMIN_EMAIL;
    const password = process.env.APP_TEST_ADMIN_PASSWORD;
    if (!email || !password) {
      throw new Error('APP_TEST_ADMIN_EMAIL and APP_TEST_ADMIN_PASSWORD must be set');
    }
    await page.goto('/login');
    await page.fill('input[type="email"], input[name="email"]', email);
    await page.fill('input[type="password"], input[name="password"]', password);
    await page.click('button[type="submit"]');
    await page.waitForURL('/');
    await use(page);
  }, { auto: false }],
});

export { expect } from '@playwright/test';
