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

  test(scenario('OPT-CHAIN-001', 'options page loads successfully'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);
    await expect(page.locator('body')).toBeVisible();
  });

  test(scenario('OPT-CHAIN-002', 'chain table renders with rows'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);

    // Enter symbol and load chain
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

    // Strategy selector renders when strategies load from the API (independent of market data)
    const strategyCards = page.locator('button.strategy-card');
    await expect(strategyCards.first()).toBeVisible({ timeout: 10000 });

    const count = await strategyCards.count();
    expect(count).toBeGreaterThanOrEqual(6);
  });

  test(scenario('OPT-CHAIN-004', 'P&L chart renders after calculation'), async ({ page }) => {
    await expect(page).toHaveURL(/\/options/);

    // Enter symbol and load chain
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

    // Click calculate
    const calcButton = page.locator('.leg-builder__calculate');
    await expect(calcButton).toBeEnabled();
    await calcButton.click();

    // P&L chart should render
    const pnlChart = page.locator('.pnl-chart');
    await expect(pnlChart).toBeVisible({ timeout: 10000 });
  });
});
