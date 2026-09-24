import { test, expect } from '../../fixtures/app.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';

test.describe('Options - Chain', { tag: ['@regression'] }, () => {
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
   * Seed a known symbol (SPY) through the market-data API — spec §5.3:
   * "Use API call to seed known symbol (e.g., SPY)".
   *
   * Endpoint discovered from the frontend's chain-load call chain:
   * OptionsPage.handleSearch → getQuote() + getOptionExpirations() →
   * GET /market-data-api/api/v1/chains/{symbol}/expirations
   * (frontend/src/services/marketDataService.ts — the chain loader lives
   * there, not in optionsStrategyService.ts).
   *
   * Returns 'ok' when the provider responded, 'unavailable' otherwise so
   * callers can keep the conditional skip for provider-outage environments.
   */
  async function seedSymbolViaApi(
    page: import('@playwright/test').Page
  ): Promise<'ok' | 'unavailable'> {
    const response = await page.request
      .get('/market-data-api/api/v1/chains/SPY/expirations')
      .catch(() => null);
    return response && response.ok() ? 'ok' : 'unavailable';
  }

  test(scenario('OPT-CHAIN-001', 'options page loads successfully'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('OPT-CHAIN-002', 'chain table renders with rows'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);

    // Data setup: seed known symbol via API (spec §5.3)
    if ((await seedSymbolViaApi(page)) !== 'ok') {
      test.skip(true, 'Market data provider unavailable — chain cannot be loaded');
      return;
    }

    // Navigation/state: the chain table renders from client-side store that
    // only handleSearch populates, so the UI search stays to put the page
    // into its chain-loaded state.
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    // Wait for either the chain to load or an error to appear (market data unavailable)
    const chainOrError = await Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);

    if (chainOrError !== 'chain') {
      test.skip(true, 'Market data provider unavailable — chain cannot be loaded');
      return;
    }

    // Chain loaded — assert strikes are visible
    const strikeCells = page.locator('.chain-table__strike-cell');
    await expect(strikeCells.first()).toBeVisible();
    const count = await strikeCells.count();
    expect(count).toBeGreaterThan(0);
  });

  test(scenario('OPT-CHAIN-003', 'strategy selector is visible'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);

    // Data setup: seed known symbol via API (spec §5.3)
    if ((await seedSymbolViaApi(page)) !== 'ok') {
      test.skip(true, 'Market data provider unavailable — chain cannot be loaded');
      return;
    }

    // Navigation/state: strategy cards only render after a successful chain
    // load (getStrategies runs inside the chain-load handler), so the UI
    // search must put the page into its chain-loaded state first.
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    // Wait for either the chain to load or an error to appear (market data unavailable)
    const chainOrError = await Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);

    if (chainOrError !== 'chain') {
      test.skip(true, 'Market data provider unavailable — chain cannot be loaded');
      return;
    }

    // Strategy selector renders once strategies load from the API during chain load
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const count = await strategyCards.count();
    expect(count).toBeGreaterThanOrEqual(6);
  });

  test(scenario('OPT-CHAIN-004', 'P&L chart renders after calculation'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);

    // Data setup: seed known symbol via API (spec §5.3)
    if ((await seedSymbolViaApi(page)) !== 'ok') {
      test.skip(true, 'Market data provider unavailable — cannot test P&L chart');
      return;
    }

    // Navigation/state: chain table renders from the client-side store that
    // only handleSearch populates — keep the UI search to load the page state.
    const symbolInput = page.locator('input.underlying-search__input');
    await symbolInput.fill('SPY');
    await page.locator('button.underlying-search__button').click();

    // Wait for chain to load or skip if market data unavailable
    const chainOrError = await Promise.race([
      page.locator('.chain-table').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'chain' as const),
      page.locator('.options-page__error').waitFor({ state: 'visible', timeout: 15000 }).then(() => 'error' as const),
    ]).catch(() => 'timeout' as const);

    if (chainOrError !== 'chain') {
      test.skip(true, 'Market data provider unavailable — cannot test P&L chart');
      return;
    }

    // Click on a call bid cell to add a leg
    const callBidCell = page.locator('.chain-table__call-side').first();
    await callBidCell.click();

    // Click calculate (scoped: mobile bottom sheet mounts a second LegBuilder)
    const calcButton = page.locator('.options-page__center-panel .leg-builder__calculate');
    await expect(calcButton).toBeEnabled();
    await calcButton.click();

    // P&L chart should render (scoped: bottom sheet mounts a second chart)
    const pnlChart = page.locator('.options-page__right-panel .pnl-chart');
    await expect(pnlChart).toBeVisible({ timeout: 10000 });
  });
});
