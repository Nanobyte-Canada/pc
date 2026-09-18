import { test, expect } from '../../fixtures/a11y.fixture';
import { scenario } from '../../support/scenario';
import AxeBuilder from '@axe-core/playwright';

const criticalPages = [
  { name: 'Login', path: '/login' },
  { name: 'Dashboard', path: '/' },
  { name: 'Portfolio', path: '/portfolios' },
  { name: 'Options', path: '/options' },
  { name: 'Wheel', path: '/wheel' },
];

for (const page of criticalPages) {
  test(scenario(`A11Y-${page.name.toUpperCase()}-001`, `${page.name} page accessibility scan`), async ({ page: p }) => {
    await p.goto(page.path);
    const results = await new AxeBuilder({ page: p })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    expect(results.violations).toEqual([]);
  });
}
