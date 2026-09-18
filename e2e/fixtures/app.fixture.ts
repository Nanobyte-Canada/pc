import { test as base } from '@playwright/test';
import { assertEnvironment, assertPageMarker } from '../support/environment';

export const test = base.extend<{ appEnvironment: void }>({
  appEnvironment: [async ({ page, baseURL }, use) => {
    assertEnvironment(baseURL!);
    await page.goto('/');
    await assertPageMarker(page);
    await use();
  }, { auto: true }],
});

export { expect } from '@playwright/test';
