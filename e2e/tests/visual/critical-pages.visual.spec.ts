import { test, expect } from '@playwright/test';
import { scenario } from '../../support/scenario';

const visualPages = [
  { name: 'Login', path: '/login' },
  { name: 'Dashboard', path: '/' },
];

for (const page of visualPages) {
  test(scenario(`VISUAL-${page.name.toUpperCase()}-001`, `${page.name} visual regression`), async ({ page: p }) => {
    await p.goto(page.path);
    await expect(p).toHaveScreenshot(`${page.name.toLowerCase()}.png`, {
      maxDiffPixelRatio: 0.01,
    });
  });
}
