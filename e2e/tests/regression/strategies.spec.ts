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

  /**
   * Helper: load the SPY options chain. Strategy cards and the education
   * panel only render after a successful chain load, so tests that assert on
   * them must call this first and honour the skip when market data is
   * unavailable.
   */
  async function loadChainOrSkip(page: import('@playwright/test').Page): Promise<void> {
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    const chainOrError = await Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);

    if (chainOrError !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot load options chain');
    }
  }

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
    // Strategy cards and the education panel only render after the chain loads
    await loadChainOrSkip(page);

    await expect(page.locator('.strategy-card').first()).toBeVisible()
    await page.locator('.strategy-card').first().click()
    const edu = page.locator('.strategy-edu-card')
    await expect(edu).toBeVisible()
    await expect(edu.getByText('When to Use')).toBeVisible()
    await expect(edu.getByText('Risk Explanation')).toBeVisible()
    await expect(edu.getByText('Key Characteristics')).toBeVisible()
    await expect(edu.locator('li').first()).not.toBeEmpty()
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

  test(scenario('STRAT-007', 'strategy selection updates education and leg template'), async ({ page }) => {
    // Cards and the education panel only render after the chain loads
    await loadChainOrSkip(page);

    await page.locator('.strategy-card').first().click()
    await expect(page.locator('.strategy-card--selected')).toHaveCount(1)
    await expect(page.locator('.strategy-edu-card')).toBeVisible()
    await page.locator('.strategy-card').nth(4).click()   // Iron Condor, 4 legs
    await expect(page.locator('.strategy-card--selected')).toHaveCount(1)
    await expect(page.locator('.strategy-edu-card')).toBeVisible()
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

    const cards = page.locator('button.strategy-card');
    // ADR-0035: a collection loop must first assert the collection is non-empty
    await expect(cards.first()).toBeVisible({ timeout: 10000 });

    for (const card of await cards.all()) {
      await card.click()
      const edu = page.locator('.strategy-edu-card')
      await expect(edu).toBeVisible()
      const text = await edu.innerText()
      expect(text).toContain('When to Use')
      expect(text.length).toBeGreaterThan(80)
    }
  });
});
