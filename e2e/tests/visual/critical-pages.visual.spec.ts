import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

const visualPages = [
  { name: 'Login', path: '/login' },
  { name: 'Dashboard', path: '/' },
];

for (const page of visualPages) {
  test(scenario(`VISUAL-${page.name.toUpperCase()}-001`, `${page.name} visual regression`), { tag: ['@visual'] }, async ({ page: p }) => {
    await p.goto(page.path);

    // Stabilize the capture: the app loads web fonts from an external CDN with
    // `display: swap` and renders a Suspense fallback (`.page-loading`) while
    // lazy routes load. Captures taken before both settle produce false-positive
    // pixel diffs (fallback-font text / a visible "Loading..." state).
    await p.waitForFunction(
      () => document.fonts.ready.then(() => document.fonts.check('16px "DM Sans"')),
      undefined,
      { timeout: 15000 }
    );
    await p.locator('.page-loading').waitFor({ state: 'detached', timeout: 15000 });

    await expect(p).toHaveScreenshot(`${page.name.toLowerCase()}.png`, {
      maxDiffPixelRatio: 0.01,
    });
  });
}
