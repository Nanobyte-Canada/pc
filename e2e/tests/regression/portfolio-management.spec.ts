import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Portfolio Management', { tag: ['@regression'] }, () => {
  const email = process.env.E2E_USER_EMAIL;
  const password = process.env.E2E_USER_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'E2E_USER_EMAIL/E2E_USER_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await page.goto('/portfolios');
    await expect(page).toHaveURL('/portfolios');
  });

  test(scenario('PORT-MGMT-001', 'portfolio page loads with header'), async ({ page }) => {
    await expect(page.locator('h1', { hasText: 'Portfolio' })).toBeVisible();
  });

  test(scenario('PORT-MGMT-002', 'model portfolio cards display'), async ({ page }) => {
    const cards = page.locator('.portfolio-page__cards .portfolio-card, [class*="portfolio-card"]');
    await expect(cards.first()).toBeVisible();
    const cardCount = await cards.count();
    expect(cardCount).toBeGreaterThanOrEqual(1);
  });

  test(scenario('PORT-MGMT-003', 'custom portfolio builder is accessible from custom slot'), async ({ page }) => {
    const customSlot = page.locator('.portfolio-page__cards > *').last();
    await customSlot.click();

    const builderOrEditBtn = page.locator('text=Edit Portfolio, text=Create Portfolio, [class*="custom"], [class*="builder"]').first();
    await expect(builderOrEditBtn).toBeVisible();
  });

  test(scenario('PORT-MGMT-004', 'analysis panel renders when a system model is selected'), async ({ page }) => {
    const systemModelCard = page.locator('.portfolio-page__cards > *:not(:last-child)').first();
    await systemModelCard.click();

    const analysisPanel = page.locator('[class*="analysis"], [class*="Analysis"]').first();
    await expect(analysisPanel).toBeVisible();
  });
});
