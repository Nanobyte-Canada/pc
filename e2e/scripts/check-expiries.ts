import { readFileSync, readdirSync, existsSync } from 'node:fs';
import { join, resolve } from 'node:path';
import matter from 'gray-matter';

interface A11yBaseline {
  version: number;
  baseline: Record<string, unknown>;
  exceptions: Array<{ id: string; expiresAt?: string }>;
}

interface QuarantineEntry {
  id: string;
  expiresAt: string;
}

interface ExpiredItem {
  type: 'a11y-baseline' | 'spec-review' | 'quarantine';
  file: string;
  id?: string;
  expiredDate: string;
}

const ROOT = resolve(import.meta.dirname, '..');

function checkA11yBaseline(): ExpiredItem[] {
  const items: ExpiredItem[] = [];
  const baselinePath = join(ROOT, 'a11y-baseline.json');

  if (!existsSync(baselinePath)) return items;

  const baseline: A11yBaseline = JSON.parse(readFileSync(baselinePath, 'utf-8'));
  const now = new Date();

  for (const entry of baseline.exceptions) {
    if (entry.expiresAt) {
      const expiresAt = new Date(entry.expiresAt);
      if (expiresAt < now) {
        items.push({
          type: 'a11y-baseline',
          file: 'e2e/a11y-baseline.json',
          id: entry.id,
          expiredDate: entry.expiresAt,
        });
      }
    }
  }

  return items;
}

function checkSpecReviews(): ExpiredItem[] {
  const items: ExpiredItem[] = [];
  const specsDir = join(ROOT, '..', 'specs', 'ui');

  if (!existsSync(specsDir)) return items;

  const now = new Date();
  const walk = (dir: string) => {
    for (const entry of readdirSync(dir, { withFileTypes: true })) {
      const fullPath = join(dir, entry.name);
      if (entry.isDirectory()) {
        walk(fullPath);
      } else if (entry.name.endsWith('.md')) {
        const content = readFileSync(fullPath, 'utf-8');
        const { data } = matter(content);
        if (data.last_reviewed) {
          const reviewedDate = new Date(data.last_reviewed);
          const thirtyDaysAgo = new Date(now.getTime() - 30 * 24 * 60 * 60 * 1000);
          if (reviewedDate < thirtyDaysAgo) {
            items.push({
              type: 'spec-review',
              file: fullPath,
              expiredDate: data.last_reviewed,
            });
          }
        }
      }
    }
  };

  walk(specsDir);
  return items;
}

function checkQuarantines(): ExpiredItem[] {
  const items: ExpiredItem[] = [];
  const quarantinePath = join(ROOT, 'quarantine.json');

  if (!existsSync(quarantinePath)) return items;

  const entries: QuarantineEntry[] = JSON.parse(readFileSync(quarantinePath, 'utf-8'));
  const now = new Date();

  for (const entry of entries) {
    const expiresAt = new Date(entry.expiresAt);
    if (expiresAt < now) {
      items.push({
        type: 'quarantine',
        file: 'e2e/quarantine.json',
        id: entry.id,
        expiredDate: entry.expiresAt,
      });
    }
  }

  return items;
}

function main(): void {
  const expired: ExpiredItem[] = [
    ...checkA11yBaseline(),
    ...checkSpecReviews(),
    ...checkQuarantines(),
  ];

  if (expired.length === 0) {
    console.log('No expired items found.');
    return;
  }

  console.error(`Found ${expired.length} expired item(s):\n`);
  for (const item of expired) {
    const idPart = item.id ? ` [${item.id}]` : '';
    console.error(`  ${item.type}${idPart}: ${item.file} (expired: ${item.expiredDate})`);
  }

  process.exit(1);
}

main();
