import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Strategy Selector', { tag: ['@regression'] }, () => {
  const email = process.env.APP_TEST_ADMIN_EMAIL;
  const password = process.env.APP_TEST_ADMIN_PASSWORD;

  test.beforeEach(async ({ page }) => {
    test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
    const loginPage = new LoginPage(page);
    await loginPage.goto();
    await loginPage.login(email!, password!);
    await expect(page).toHaveURL('/');
    await page.goto('/options');
  });

  test(scenario('STRAT-001', 'strategy list loads with all strategies'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });
    await expect(strategyCards).toHaveCount(6);

    const names = page.locator('.strategy-card__name');
    const nameTexts = await names.allTextContents();

    expect(nameTexts.some(t => t.includes('Bull Call Spread'))).toBeTruthy();
    expect(nameTexts.some(t => t.includes('Bear Put Spread'))).toBeTruthy();
    expect(nameTexts.some(t => t.includes('Bull Put Spread'))).toBeTruthy();
    expect(nameTexts.some(t => t.includes('Bear Call Spread'))).toBeTruthy();
    expect(nameTexts.some(t => t.includes('Iron Condor'))).toBeTruthy();
    expect(nameTexts.some(t => t.includes('Butterfly Spread'))).toBeTruthy();

    // Verify removed strategies are NOT present
    expect(nameTexts.some(t => t.includes('Covered Call'))).toBeFalsy();
    expect(nameTexts.some(t => t.includes('Protective Put'))).toBeFalsy();
  });

  test(scenario('STRAT-002', 'strategy education display'), async ({ page }) => {
    // Strategies must be loaded first
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    // Click first strategy card
    await strategyCards.first().click();

    // Education panel placeholder or card should appear
    // Education card only renders after chain is loaded, so the left panel shows placeholder text
    const educationOrPlaceholder = page.locator('.options-page__left-panel');
    await expect(educationOrPlaceholder).toBeVisible();
  });

  test(scenario('STRAT-003', 'outlook labels are shown'), async ({ page }) => {
    const strategyCards = page.locator('.strategy-card')
    await expect(strategyCards).toHaveCount(6)

    const labels = await page.locator('.strategy-card__outlook').allTextContents()
    expect(labels.length).toBe(6)
    for (const label of labels) {
      expect(['Bullish', 'Bearish', 'Neutral']).toContain(label.trim())
    }
  })

  test(scenario('STRAT-004', 'butterfly spread is present with 3-leg description'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const butterflyCard = strategyCards.filter({ hasText: 'Butterfly Spread' });
    await expect(butterflyCard).toBeVisible();

    // Butterfly should show "3 legs"
    const legCount = butterflyCard.locator('.strategy-card__legs');
    await expect(legCount).toContainText('3');
  });

  test(scenario('STRAT-005', 'covered call is removed'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const names = page.locator('.strategy-card__name');
    const nameTexts = await names.allTextContents();
    expect(nameTexts.some(t => t.includes('Covered Call'))).toBeFalsy();
  });

  test(scenario('STRAT-006', 'protective put is removed'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const names = page.locator('.strategy-card__name');
    const nameTexts = await names.allTextContents();
    expect(nameTexts.some(t => t.includes('Protective Put'))).toBeFalsy();
  });

  test(scenario('STRAT-007', 'strategy selection highlights card and shows left panel'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    // Select a strategy
    const bullCallCard = strategyCards.filter({ hasText: 'Bull Call Spread' });
    await bullCallCard.click();

    // Card should have selected state
    await expect(bullCallCard).toHaveClass(/strategy-card--selected/);

    // Left panel should be visible (education or placeholder)
    const leftPanel = page.locator('.options-page__left-panel');
    await expect(leftPanel).toBeVisible();
  });

  test(scenario('STRAT-008', 'all strategies have education content'), async ({ page }) => {
    // Load chain first so education card can render
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    const chainOrError = await Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);

    if (chainOrError !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test education content');
      return;
    }

    const strategyCards = page.locator('button.strategy-card');
    const count = await strategyCards.count();

    for (let i = 0; i < count; i++) {
      const card = strategyCards.nth(i);
      const name = await card.locator('.strategy-card__name').textContent();
      await card.click();

      // Education card should appear in the left panel
      const education = page.locator('.strategy-edu-card');
      await expect(education).toBeVisible({ timeout: 5000 });

      // Education should have "When to Use" section
      await expect(education.locator('h4', { hasText: 'When to Use' })).toBeVisible();
      // Education should have "Risk" section
      await expect(education.locator('h4', { hasText: 'Risk' })).toBeVisible();

      // Deselect strategy to reset for next iteration
      await card.click();
      await page.waitForTimeout(200);
    }
  });
});
