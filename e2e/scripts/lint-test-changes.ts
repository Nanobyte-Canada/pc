import { execSync } from "node:child_process";

import { resolve } from "node:path";

const ROOT = resolve(import.meta.dirname, "../..");

interface Violation {
  type: string;
  file: string;
  line: number;
  message: string;
}

function gitDiff(baseRef: string): string {
  try {
    return execSync(`git diff ${baseRef}...HEAD -- '*.test.*' '*.spec.*'`, {
      cwd: ROOT,
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "pipe"],
    });
  } catch {
    return "";
  }
}

function checkTestSkip(diff: string, violations: Violation[]): void {
  const lines = diff.split("\n");
  let currentFile = "";
  let lineNum = 0;

  for (const line of lines) {
    if (line.startsWith("+++ b/")) {
      currentFile = line.slice(6);
    } else if (line.startsWith("@@")) {
      const match = line.match(/\+(\d+)/);
      if (match) lineNum = parseInt(match[1], 10) - 1;
    } else if (line.startsWith("+") && !line.startsWith("+++")) {
      lineNum++;
      const content = line.slice(1);
      if (
        /\btest\.skip\s*\(/.test(content) ||
        /\btest\.fixme\s*\(/.test(content) ||
        /\bit\.skip\s*\(/.test(content) ||
        /\bdescribe\.skip\s*\(/.test(content)
      ) {
        violations.push({
          type: "test.skip",
          file: currentFile,
          line: lineNum,
          message: `Added ${content.trim().match(/\b(test|it|describe)\.(skip|fixme)/)?.[0] || "skip/fixme"}: ${content.trim()}`,
        });
      }
    } else if (!line.startsWith("-")) {
      lineNum++;
    }
  }
}

function isWeakerMatcher(original: string, replacement: string): boolean {
  const weak = new Set([
    "toBeTruthy",
    "toBeDefined",
    "toContain",
    "toMatch",
    "toBeGreaterThan",
    "toBeGreaterThanOrEqual",
    "toBeLessThan",
    "toBeLessThanOrEqual",
  ]);
  const strong = new Set(["toBe", "toEqual", "toStrictEqual"]);

  return strong.has(original) && weak.has(replacement);
}

function checkMatcherLoosening(diff: string, violations: Violation[]): void {
  const lines = diff.split("\n");
  let currentFile = "";
  let removedAssertLine = "";
  let lineNum = 0;

  for (const line of lines) {
    if (line.startsWith("+++ b/")) {
      currentFile = line.slice(6);
    } else if (line.startsWith("@@")) {
      const match = line.match(/\+(\d+)/);
      if (match) lineNum = parseInt(match[1], 10) - 1;
    } else if (line.startsWith("-") && !line.startsWith("---")) {
      const removed = line.slice(1);
      if (/expect\(.*\)\.\w+/.test(removed)) {
        removedAssertLine = removed;
      }
    } else if (line.startsWith("+") && !line.startsWith("+++")) {
      lineNum++;
      const added = line.slice(1);

      if (removedAssertLine && /expect\(.*\)\.\w+/.test(added)) {
        const origMatch = removedAssertLine.match(
          /expect\([^)]+\)\.(\w+)/,
        );
        const newMatch = added.match(/expect\([^)]+\)\.(\w+)/);

        if (origMatch && newMatch && isWeakerMatcher(origMatch[1], newMatch[1])) {
          violations.push({
            type: "matcher-loosened",
            file: currentFile,
            line: lineNum,
            message: `Matcher loosened: .${origMatch[1]} → .${newMatch[1]}`,
          });
        }
      }
      removedAssertLine = "";
    } else {
      lineNum++;
    }
  }
}

function checkConfigWeakening(diff: string, violations: Violation[]): void {
  const lines = diff.split("\n");
  let currentFile = "";
  let lineNum = 0;

  for (const line of lines) {
    if (line.startsWith("+++ b/")) {
      currentFile = line.slice(6);
    } else if (line.startsWith("@@")) {
      const match = line.match(/\+(\d+)/);
      if (match) lineNum = parseInt(match[1], 10) - 1;
    } else if (line.startsWith("+") && !line.startsWith("+++")) {
      lineNum++;
      const content = line.slice(1);

      const timeoutMatch = content.match(/timeout:\s*(\d+)/);
      if (timeoutMatch) {
        const removedLine = findRemovedLine(lines, lineNum);
        const origTimeout = removedLine?.match(/timeout:\s*(\d+)/)?.[1];
        if (origTimeout && parseInt(timeoutMatch[1]) < parseInt(origTimeout)) {
          violations.push({
            type: "config-weakened",
            file: currentFile,
            line: lineNum,
            message: `Timeout reduced: ${origTimeout}ms → ${timeoutMatch[1]}ms`,
          });
        }
      }

      if (
        /\bexpect\s*\(\s*\)\s*\.\s*\w+\s*\(/.test(content) &&
        /\/\*.*assert.*\*\//.test(content)
      ) {
        violations.push({
          type: "config-weakened",
          file: currentFile,
          line: lineNum,
          message: "Assertion may have been commented out",
        });
      }
    } else if (!line.startsWith("-")) {
      lineNum++;
    }
  }
}

function findRemovedLine(
  lines: string[],
  _currentLineNum: number,
): string | null {
  const removed: string[] = [];
  for (const line of lines) {
    if (line.startsWith("-") && !line.startsWith("---")) {
      removed.push(line.slice(1));
    }
  }
  return removed.length > 0 ? removed[removed.length - 1] : null;
}

function reportViolations(violations: Violation[]): void {
  if (violations.length === 0) {
    console.log("✅ No test quality violations found.");
    return;
  }

  console.error(`\n❌ Found ${violations.length} violation(s):\n`);

  const grouped = new Map<string, Violation[]>();
  for (const v of violations) {
    const key = v.type;
    if (!grouped.has(key)) grouped.set(key, []);
    grouped.get(key)!.push(v);
  }

  for (const [type, items] of grouped) {
    console.error(`[${type}]`);
    for (const v of items) {
      console.error(`  ${v.file}:${v.line} — ${v.message}`);
    }
    console.error();
  }

  process.exit(1);
}

async function main(): Promise<void> {
  const baseRef = process.argv[2] || "origin/main";
  const diff = gitDiff(baseRef);

  if (!diff) {
    console.log("No test file changes detected.");
    return;
  }

  const violations: Violation[] = [];
  checkTestSkip(diff, violations);
  checkMatcherLoosening(diff, violations);
  checkConfigWeakening(diff, violations);

  reportViolations(violations);
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(2);
});
