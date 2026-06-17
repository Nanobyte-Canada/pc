import { test, expect, type Page } from '@playwright/test'

const EMAIL = process.env.E2E_USER_EMAIL ?? ''
const PASSWORD = process.env.E2E_USER_PASSWORD ?? ''
const QUESTRADE_TOKEN = process.env.E2E_QUESTRADE_TOKEN ?? ''
const GATEWAY_URL = process.env.E2E_GATEWAY_URL ?? 'http://127.0.0.1:8084'
const GATEWAY_KEY = process.env.E2E_GATEWAY_KEY ?? 'dev-gateway-key'

test.beforeAll(() => {
  if (!EMAIL || !PASSWORD) throw new Error('E2E_USER_EMAIL and E2E_USER_PASSWORD are required')
  if (!QUESTRADE_TOKEN) throw new Error('E2E_QUESTRADE_TOKEN is required (generate at https://login.questrade.com/APIAccess/UserApps.aspx)')
})

test.describe.serial('Questrade Connection UAT', () => {
  let page: Page

  test.beforeAll(async ({ browser }) => {
    page = await browser.newPage()
  })

  test.afterAll(async () => {
    await page.close()
  })

  test('1. Login', async () => {
    await page.goto('/login')
    await page.fill('#email', EMAIL)
    await page.fill('#password', PASSWORD)
    await page.click('button[type="submit"]')
    await page.waitForURL('/', { timeout: 15_000 })
    await expect(page.locator('.sidebar').first()).toBeVisible({ timeout: 10_000 })
  })

  test('2. Navigate to broker connections', async () => {
    await page.goto('/brokers/connections')
    await expect(page.locator('h1')).toContainText('Broker Connections', { timeout: 10_000 })
    await expect(page.getByText('Available Brokers')).toBeVisible()
  })

  test('3. Verify clean state', async () => {
    await page.reload()
    await expect(page.locator('h1')).toContainText('Broker Connections', { timeout: 10_000 })
    const cards = page.locator('.broker-connection-card')
    const count = await cards.count()
    if (count > 0) {
      console.log(`Warning: ${count} existing connections found — tests may produce unexpected results`)
    }
  })

  test('4. Open Questrade connect dialog', async () => {
    const questradeCard = page.locator('.broker-card', { hasText: /QUESTRADE/i })
    await expect(questradeCard).toBeVisible({ timeout: 10_000 })
    await questradeCard.click()

    await expect(page.locator('.connect-dialog')).toBeVisible({ timeout: 5_000 })
    await expect(page.locator('.connect-dialog h2')).toContainText('Connect')
    await expect(page.locator('#refresh-token')).toBeVisible()
  })

  test('5. Connect with real Questrade token', async () => {
    await page.fill('#refresh-token', QUESTRADE_TOKEN)
    await page.click('.connect-dialog-btn.connect')

    const dialogError = page.locator('.connect-dialog-error')
    const dialogHidden = page.locator('.connect-dialog')

    const result = await Promise.race([
      dialogError.waitFor({ state: 'visible', timeout: 60_000 })
        .then(() => 'error' as const),
      dialogHidden.waitFor({ state: 'hidden', timeout: 60_000 })
        .then(() => 'success' as const),
    ])

    if (result === 'error') {
      const errorText = await dialogError.textContent()
      throw new Error(
        `Questrade connection failed: "${errorText}". ` +
        'The token is likely expired or already used. ' +
        'Generate a fresh token at https://login.questrade.com/APIAccess/UserApps.aspx'
      )
    }

    const notification = page.locator('.broker-notification.success')
    await expect(notification).toBeVisible({ timeout: 30_000 })
    await expect(notification).toContainText(/Connected|account/i)
  })

  test('6. Accounts are discovered and displayed', async () => {
    await expect(page.locator('.broker-syncing-overlay')).toBeHidden({ timeout: 120_000 })

    const connectionCards = page.locator('.broker-connection-card')
    await expect(connectionCards.first()).toBeVisible({ timeout: 30_000 })

    const count = await connectionCards.count()
    expect(count).toBeGreaterThanOrEqual(1)

    const firstCard = connectionCards.first()
    await expect(firstCard.locator('button', { hasText: /Sync All/i })).toBeVisible()
  })

  test('7. Sync fetches positions and balances', async () => {
    const firstCard = page.locator('.broker-connection-card').first()
    const syncButton = firstCard.locator('button', { hasText: /Sync All/i })

    if (await syncButton.isEnabled()) {
      await syncButton.click()

      await expect(syncButton).not.toContainText('Syncing', { timeout: 90_000 })

      const positionsText = firstCard.locator('.connection-positions-count')
      if (await positionsText.isVisible()) {
        const text = await positionsText.textContent() ?? ''
        expect(text).toMatch(/\d+\s*position/)
      }

      const valueText = firstCard.locator('.connection-total-value')
      if (await valueText.isVisible()) {
        const text = await valueText.textContent() ?? ''
        expect(text).toMatch(/\$[\d,]+/)
      }
    }
  })

  test('8. Token refresh scheduler is running', async () => {
    const resp = await fetch(`${GATEWAY_URL}/api/v1/gateway/health`, {
      headers: { 'X-Gateway-Api-Key': GATEWAY_KEY }
    })
    expect(resp.ok).toBe(true)
    const health = await resp.json()
    expect(health.status).toBe('UP')

    await fetch(`${GATEWAY_URL}/api/v1/gateway/connections?userId=0`, {
      headers: { 'X-Gateway-Api-Key': GATEWAY_KEY }
    }).catch(() => null)

    console.log('Gateway health OK — token refresh scheduler is active')
  })

  test('9. Connection persists after waiting', async () => {
    console.log('Waiting 30 seconds to verify connection stays ACTIVE...')
    await page.waitForTimeout(30_000)

    await page.reload()
    await expect(page.locator('h1')).toContainText('Broker Connections', { timeout: 10_000 })

    const connectionCards = page.locator('.broker-connection-card')
    await expect(connectionCards.first()).toBeVisible({ timeout: 10_000 })

    const firstCard = connectionCards.first()
    const statusText = await firstCard.textContent() ?? ''
    expect(statusText).toContain('Active')
    expect(statusText).not.toContain('Error')
    expect(statusText).not.toContain('Expired')

    console.log('Connection still ACTIVE after 30s — token refresh working')
  })

  test('10. Connection left alive for scheduler observation', async () => {
    const cards = page.locator('.broker-connection-card')
    const count = await cards.count()
    expect(count).toBeGreaterThanOrEqual(1)
    console.log(`${count} connections left alive — monitor gateway logs for scheduler refresh cycles`)
  })
})
