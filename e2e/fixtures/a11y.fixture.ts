import { test as base } from '@playwright/test';
import AxeBuilder from '@axe-core/playwright';

export const test = base.extend<{ accessibilityScan: void }>({
  accessibilityScan: async ({ page }, use) => {
    const accessibilityScanResults = await new AxeBuilder({ page })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    // Store results for baseline comparison
    await use(accessibilityScanResults);
  },
});

export { expect } from '@playwright/test';
