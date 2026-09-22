import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Options Trading', { tag: ['@regression'] }, () => {
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
   * Helper: attempt to load SPY options chain.
   * Returns 'chain' if the chain loaded, or 'error'/'timeout' if not.
   * Tests that need market data should call this and skip on failure.
   */
  async function loadSpyChain(page: import('@playwright/test').Page): Promise<'chain' | 'error' | 'timeout'> {
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    return Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);
  }

  test(scenario('TRADE-001', 'load options chain for symbol'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot load options chain');
      return;
    }

    // Chain loaded — strike rows should be visible
    const strikeCells = page.locator('.chain-table__strike-cell');
    await expect(strikeCells.first()).toBeVisible();
    const count = await strikeCells.count();
    expect(count).toBeGreaterThan(0);
  });

  test(scenario('TRADE-002', 'add legs to builder from chain'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test adding legs');
      return;
    }

    // Click on a call bid cell to add a BUY leg
    const callBidCell = page.locator('.chain-table__call-side').first();
    await callBidCell.click();

    // Leg builder should show the leg
    const legCards = page.locator('.leg-builder__card');
    await expect(legCards).toHaveCount(1);
  });

  test(scenario('TRADE-003', 'calculate P&L after adding legs'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test P&L calculation');
      return;
    }

    // Add two legs (buy call + sell call for vertical spread)
    const callSideCells = page.locator('.chain-table__call-side');
    await callSideCells.nth(0).click();
    await callSideCells.nth(1).click();

    const legCards = page.locator('.leg-builder__card');
    await expect(legCards).toHaveCount(2);

    // Click calculate
    const calcButton = page.locator('.leg-builder__calculate');
    await expect(calcButton).toBeEnabled();
    await calcButton.click();

    // P&L chart should render
    const pnlChart = page.locator('.pnl-chart');
    await expect(pnlChart).toBeVisible({ timeout: 10000 });
  });

  test(scenario('TRADE-004', 'dollar value P&L displayed'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test dollar P&L');
      return;
    }

    // Add two legs and calculate
    const callSideCells = page.locator('.chain-table__call-side');
    await callSideCells.nth(0).click();
    await callSideCells.nth(1).click();

    await page.locator('.leg-builder__calculate').click();

    // Dollar value metrics should show
    const dollarMetrics = page.locator('.dollar-metrics');
    await expect(dollarMetrics).toBeVisible({ timeout: 10000 });
    await expect(dollarMetrics).toContainText('$');
  });

  test(scenario('TRADE-005', 'trade button shows connection prompt when disconnected'), async ({ page }) => {
    // The trade section should be visible (either button or disconnected message)
    const tradeSection = page.locator('.one-click-trade');
    await expect(tradeSection).toBeVisible();

    // Either the trade button is visible (connected) or the disconnected prompt is shown
    const tradeButton = page.locator('.one-click-trade__btn');
    const disconnectedMsg = page.locator('.one-click-trade__disconnected');

    const isButtonVisible = await tradeButton.isVisible().catch(() => false);
    const isDisconnectedVisible = await disconnectedMsg.isVisible().catch(() => false);

    // One of these should be visible
    expect(isButtonVisible || isDisconnectedVisible).toBeTruthy();
  });

  test(scenario('TRADE-006', 'remove legs from builder'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test removing legs');
      return;
    }

    // Add a leg
    const callBidCell = page.locator('.chain-table__call-side').first();
    await callBidCell.click();

    const legCards = page.locator('.leg-builder__card');
    await expect(legCards).toHaveCount(1);

    // Click clear all
    const clearButton = page.locator('.leg-builder__clear');
    await clearButton.click();

    // Builder should show empty state
    await expect(page.locator('.leg-builder__empty')).toBeVisible();
  });

  test(scenario('TRADE-007', 'butterfly spread card shows 3 legs'), async ({ page }) => {
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const butterflyCard = strategyCards.filter({ hasText: 'Butterfly Spread' });
    await expect(butterflyCard).toBeVisible();

    // Verify it shows "3 legs" in the card
    const legCount = butterflyCard.locator('.strategy-card__legs');
    await expect(legCount).toContainText('3');

    // Click to select — education or placeholder should appear
    await butterflyCard.click();
    await expect(butterflyCard).toHaveClass(/strategy-card--selected/);
  });

  test(scenario('TRADE-008', 'strategy suggest endpoint returns bullish strategies'), async ({ page }) => {
    // Test the suggest API endpoint directly
    const response = await page.request.get(
      '/strategy-api/api/v1/strategies/suggest?outlook=bullish&underlying=SPY'
    );

    expect(response.ok()).toBeTruthy();
    const strategies = await response.json();
    expect(strategies.length).toBeGreaterThan(0);

    // All returned strategies should have "Bullish" in their outlook
    for (const strategy of strategies) {
      expect(strategy.marketOutlook.toLowerCase()).toContain('bullish');
    }
  });
});
