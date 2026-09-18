import type { Page } from '@playwright/test';

const ALLOWED_HOSTS = ['uatportfolio.nanobyte.ca'];

export function assertEnvironment(baseURL: string): void {
  const url = new URL(baseURL);
  if (!ALLOWED_HOSTS.includes(url.hostname)) {
    throw new Error(
      `Environment safety violation: ${url.hostname} is not in the allowed hostlist. ` +
      `Allowed: ${ALLOWED_HOSTS.join(', ')}`
    );
  }
}

export async function assertPageMarker(page: Page): Promise<void> {
  const marker = await page
    .locator('meta[name="app-environment"]')
    .getAttribute('content');
  if (marker !== 'uat') {
    throw new Error(
      `Environment safety violation: app-environment meta tag is "${marker}", expected "uat". ` +
      `This check prevents accidental testing against production.`
    );
  }
}
