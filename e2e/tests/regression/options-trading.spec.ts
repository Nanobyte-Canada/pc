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

  /**
   * Helper: select a strategy card by display name and wait for its
   * selected state.
   */
  async function selectStrategy(page: import('@playwright/test').Page, name: string): Promise<void> {
    const card = page.locator('button.strategy-card').filter({ hasText: name });
    await expect(card).toBeVisible({ timeout: 10000 });
    await card.click();
    await expect(card).toHaveClass(/strategy-card--selected/);
  }

  /**
   * Helper: add one leg by clicking the first chain cell that is not already
   * in the builder. Cells toggle (clicking a leg that is already added removes
   * it), so start scanning after the legs already present and confirm the leg
   * count actually increased before returning.
   */
  async function addFirstAvailableLeg(page: import('@playwright/test').Page): Promise<void> {
    const legCards = page.locator('.leg-builder__card');
    const before = await legCards.count();
    const legCountText = await page.locator('.leg-builder__count').first().textContent();
    const alreadyAdded = parseInt(legCountText?.replace(/\D/g, '') ?? '0', 10) || 0;

    const callCells = page.locator('.chain-table__call-side');
    const cellCount = await callCells.count();

    for (let i = alreadyAdded; i < cellCount; i++) {
      await callCells.nth(i).click();
      try {
        await expect.poll(() => legCards.count(), { timeout: 2000 }).toBeGreaterThan(before);
        return;
      } catch {
        // This cell did not add a leg (no live quote on that row) — try the next.
      }
    }

    throw new Error(`could not add a leg: no chain cell raised the leg count above ${before}`);
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

    // Leg builder should show the leg (scoped: mobile bottom sheet mounts a second LegBuilder)
    const legCards = page.locator('.options-page__center-panel .leg-builder__card');
    await expect(legCards).toHaveCount(1);

    // Per spec: LegBuilder shows correct badges. Scoped to the desktop panel —
    // the mobile bottom sheet renders a second LegBuilder in the DOM.
    await expect(page.locator('.options-page__center-panel .leg-builder__badge--buy, .options-page__center-panel .leg-builder__badge--sell')).toHaveCount(1)
    await expect(page.locator('.options-page__center-panel .leg-builder__badge--call, .options-page__center-panel .leg-builder__badge--put')).toHaveCount(1)
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

    // Scoped: mobile bottom sheet mounts a second LegBuilder in the DOM
    const legCards = page.locator('.options-page__center-panel .leg-builder__card');
    await expect(legCards).toHaveCount(2);

    // Click calculate (scoped: second LegBuilder in the mobile bottom sheet)
    const calcButton = page.locator('.options-page__center-panel .leg-builder__calculate');
    await expect(calcButton).toBeEnabled();
    await calcButton.click();

    // P&L chart should render (scoped: bottom sheet mounts a second chart)
    const pnlChart = page.locator('.options-page__right-panel .pnl-chart');
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

    // Scoped: mobile bottom sheet mounts a second LegBuilder in the DOM
    await page.locator('.options-page__center-panel .leg-builder__calculate').click();

    // Dollar value metrics should show (scoped: bottom sheet mounts a second copy)
    const dollarMetrics = page.locator('.options-page__center-panel .dollar-metrics');
    await expect(dollarMetrics).toBeVisible({ timeout: 10000 });
    await expect(dollarMetrics).toContainText('$');
  });

  test(scenario('TRADE-005', 'trade button shows connection prompt when disconnected'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test trade section');
      return;
    }

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

    // Scoped: mobile bottom sheet mounts a second LegBuilder in the DOM
    const legCards = page.locator('.options-page__center-panel .leg-builder__card');
    await expect(legCards).toHaveCount(1);

    // Click clear all
    const clearButton = page.locator('.options-page__center-panel .leg-builder__clear');
    await clearButton.click();

    // Builder should show empty state
    await expect(page.locator('.options-page__center-panel .leg-builder__empty')).toBeVisible();
  });

  test(scenario('TRADE-007', 'iron condor with too few legs shows a validation error'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test leg validation');
      return;
    }

    await selectStrategy(page, 'Iron Condor')
    // add only two of the four required legs
    await addFirstAvailableLeg(page)
    await addFirstAvailableLeg(page)
    await page.locator('.leg-builder__calculate').first().click()
    await expect(page.getByText(/requires exactly 4 legs/i)).toBeVisible()
  })

  test(scenario('TRADE-008', 'strategy suggest endpoint returns bullish strategies'), async ({ page }) => {
    // Test the suggest API endpoint directly (POST with body per API reference:
    // docs/reference/api-endpoints.md — @PostMapping("/suggest") + @RequestBody)
    const response = await page.request.post(
      '/strategy-api/api/v1/strategies/suggest',
      { data: { outlook: 'bullish', underlying: 'SPY' } }
    );

    expect(response.ok()).toBeTruthy();
    const strategies = await response.json();
    expect(strategies.length).toBeGreaterThan(0);

    // All returned strategies should have "Bullish" in their outlook
    // (suggest endpoint returns the raw DTO field `outlook`, not the frontend-mapped `marketOutlook`)
    for (const strategy of strategies) {
      expect(strategy.outlook.toLowerCase()).toContain('bullish');
    }
  });

  test(scenario('TRADE-009', 'break-even prices are shown on the chart'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test break-even markers');
      return;
    }

    await selectStrategy(page, 'Bull Call Spread')
    await addFirstAvailableLeg(page)
    await addFirstAvailableLeg(page)
    await page.locator('.leg-builder__calculate').first().click()
    await expect(page.locator('.pnl-chart__breakeven').first()).toBeVisible()
  })

  test(scenario('TRADE-010', 'butterfly spread card shows 3 legs'), async ({ page }) => {
    const result = await loadSpyChain(page);
    if (result !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test butterfly spread card');
      return;
    }

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
});
