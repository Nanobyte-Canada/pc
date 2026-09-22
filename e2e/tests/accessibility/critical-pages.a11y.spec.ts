import { readFileSync } from 'node:fs';
import { test, expect } from '../../fixtures/a11y.fixture';
import { scenario } from '../../support/scenario';
import { LoginPage } from '../../pages/login.page';
import AxeBuilder from '@axe-core/playwright';

interface A11yBaselineEntry {
  rule: string;
  targets?: string[];
}

interface A11yBaselineFile {
  version: number;
  baseline: Record<string, A11yBaselineEntry[]>;
  exceptions: unknown[];
}

const baseline: A11yBaselineFile = JSON.parse(
  readFileSync(new URL('../../a11y-baseline.json', import.meta.url), 'utf-8'),
) as A11yBaselineFile;

interface AxeViolationLike {
  id: string;
  nodes: { target: string[] }[];
}

function isBaselined(pageName: string, violation: AxeViolationLike): boolean {
  const entries = baseline.baseline[pageName] ?? [];
  return entries.some((entry) => {
    if (entry.rule !== violation.id) return false;
    if (!entry.targets || entry.targets.length === 0) return true;
    const violationTargets = new Set(violation.nodes.flatMap((node) => node.target));
    return entry.targets.some((target) => violationTargets.has(target));
  });
}

const email = process.env.APP_TEST_ADMIN_EMAIL;
const password = process.env.APP_TEST_ADMIN_PASSWORD;

const criticalPages = [
  { name: 'Login', path: '/login', authenticated: false },
  { name: 'Dashboard', path: '/', authenticated: true },
  { name: 'Portfolio', path: '/portfolios', authenticated: true },
  { name: 'Options', path: '/options', authenticated: true },
  { name: 'Wheel', path: '/wheel', authenticated: true },
];

for (const page of criticalPages) {
  test(scenario(`A11Y-${page.name.toUpperCase()}-001`, `${page.name} page accessibility scan`), { tag: ['@a11y'] }, async ({ page: p }) => {
    if (page.authenticated) {
      test.skip(!email || !password, 'APP_TEST_ADMIN_EMAIL/APP_TEST_ADMIN_PASSWORD not set');
      const loginPage = new LoginPage(p);
      await loginPage.goto();
      await loginPage.login(email!, password!);
      await expect(p).toHaveURL('/');
    }
    await p.goto(page.path);
    await expect(p).toHaveURL(new RegExp(`${page.path}$`));
    await p.evaluate(() => document.fonts.ready);
    const results = await new AxeBuilder({ page: p })
      .withTags(['wcag2a', 'wcag2aa', 'wcag21a', 'wcag21aa'])
      .analyze();
    const unfiltered = results.violations.filter((v) => !isBaselined(page.name, v));
    const summary = unfiltered
      .map((v) => `${v.id} (${v.nodes.length} node${v.nodes.length === 1 ? '' : 's'})`)
      .join(', ');
    expect(
      unfiltered,
      `${page.name}: accessibility violations not covered by a11y-baseline.json: ${summary || 'none'}`,
    ).toEqual([]);
  });
}
