import { expect, Page } from '@playwright/test';

export async function waitForNoSpinner(page: Page, timeout = 10000): Promise<void> {
  const spinner = page.locator('[role="progressbar"], .loading, [data-loading]');
  await expect(spinner).toBeHidden({ timeout });
}

export async function waitForApiResponse(page: Page, urlPattern: string | RegExp, timeout = 10000): Promise<void> {
  await page.waitForResponse(
    (response) =>
      typeof urlPattern === 'string'
        ? response.url().includes(urlPattern)
        : urlPattern.test(response.url()),
    { timeout }
  );
}
