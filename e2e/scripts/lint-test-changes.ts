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

// Skip policy: conditional skips with documented reasons are a sanctioned
// pattern — data-driven tests guard on env/fixtures (e.g.
// `test.skip(!email || !password, 'E2E_USER_EMAIL not set')`) and
// reviewer-mandated TODO skips carry explicit reason strings. The defect this
// rule exists to catch is the *unexplained, unconditional* skip that hides
// coverage: a bare `test.skip()`, `test.skip(true)`, a title-only
// `test.skip('some title')`, or a `describe.skip` with no reason comment.
// A skip call is therefore only flagged when it carries no non-empty string
// reason argument (a lone first-position string is a title, not a reason).

/**
 * Collect the full argument text of a call whose opening paren sits at
 * `openParenPos` on diff line `startLine`, scanning forward (max 20 lines)
 * until parentheses balance. String-aware; returns null if unbalanced.
 */
function extractCallArgs(
  lines: string[],
  startLine: number,
  openParenPos: number,
): string | null {
  let text = "";
  let depth = 0;
  let started = false;
  let inStr: string | null = null;

  for (let i = startLine; i < Math.min(lines.length, startLine + 20); i++) {
    const raw = lines[i];
    const content =
      raw.startsWith("+") && !raw.startsWith("+++") ? raw.slice(1) : raw;
    const from = i === startLine ? openParenPos : 0;

    for (let j = from; j < content.length; j++) {
      const ch = content[j];
      if (inStr) {
        text += ch;
        if (ch === "\\") {
          text += content[++j] ?? "";
          continue;
        }
        if (ch === inStr) inStr = null;
        continue;
      }
      if (ch === '"' || ch === "'" || ch === "`") {
        inStr = ch;
        text += ch;
        continue;
      }
      if (ch === "(") {
        depth++;
        started = true;
        text += ch;
        continue;
      }
      if (ch === ")") {
        depth--;
        text += ch;
        if (started && depth === 0) return text.slice(1, -1);
        continue;
      }
      text += ch;
    }
    text += "\n";
  }
  return null;
}

/** Split an argument list on top-level commas (string/bracket aware). */
function splitTopLevelArgs(args: string): string[] {
  const parts: string[] = [];
  let cur = "";
  let depth = 0;
  let inStr: string | null = null;

  for (let i = 0; i < args.length; i++) {
    const ch = args[i];
    if (inStr) {
      cur += ch;
      if (ch === "\\") {
        cur += args[++i] ?? "";
        continue;
      }
      if (ch === inStr) inStr = null;
      continue;
    }
    if (ch === '"' || ch === "'" || ch === "`") {
      inStr = ch;
      cur += ch;
      continue;
    }
    if (ch === "(" || ch === "[" || ch === "{") depth++;
    if (ch === ")" || ch === "]" || ch === "}") depth--;
    if (ch === "," && depth === 0) {
      parts.push(cur.trim());
      cur = "";
      continue;
    }
    cur += ch;
  }
  if (cur.trim()) parts.push(cur.trim());
  return parts;
}

/** True when the argument is a non-empty string literal (a reason/title). */
function isNonEmptyStringLiteral(arg: string): boolean {
  const m = arg.match(/^(?:'([^']*)'|"([^"]*)"|`([\s\S]*)`)$/);
  if (!m) return false;
  return (m[1] ?? m[2] ?? m[3] ?? "").trim().length > 0;
}

/** True when the line has a `//` comment outside of string literals. */
function hasReasonComment(line: string): boolean {
  const noStrings = line.replace(/(["'`])(?:\\.|(?!\1).)*\1/g, "");
  return /\/\/\s*\S/.test(noStrings);
}

function isSanctionedSkip(
  kind: string,
  argsText: string | null,
  line: string,
): boolean {
  if (argsText === null) return false;
  const args = splitTopLevelArgs(argsText);

  if (kind === "describe") {
    // describe.skip('title', () => {...}) — the first string is a title, so
    // sanction only via a reason string beyond the title or a reason comment.
    return args.slice(1).some(isNonEmptyStringLiteral) || hasReasonComment(line);
  }

  // test/it skip/fixme: sanctioned when any argument after the first is a
  // non-empty string reason (covers conditional env guards and TODO skips).
  // A lone first-position string is a title, not a reason — still flagged.
  return args.slice(1).some(isNonEmptyStringLiteral);
}

function checkTestSkip(diff: string, violations: Violation[]): void {
  const lines = diff.split("\n");
  let currentFile = "";
  let lineNum = 0;

  for (let i = 0; i < lines.length; i++) {
    const line = lines[i];
    if (line.startsWith("+++ b/")) {
      currentFile = line.slice(6);
    } else if (line.startsWith("@@")) {
      const match = line.match(/\+(\d+)/);
      if (match) lineNum = parseInt(match[1], 10) - 1;
    } else if (line.startsWith("+") && !line.startsWith("+++")) {
      lineNum++;
      const content = line.slice(1);
      const skipMatch = content.match(/\b(test|it|describe)\.(skip|fixme)\s*\(/);
      if (skipMatch && skipMatch.index !== undefined) {
        const openParenPos = skipMatch.index + skipMatch[0].length - 1;
        const argsText = extractCallArgs(lines, i, openParenPos);
        if (!isSanctionedSkip(skipMatch[1], argsText, content)) {
          violations.push({
            type: "test.skip",
            file: currentFile,
            line: lineNum,
            message: `Added ${skipMatch[1]}.${skipMatch[2]}: ${content.trim()}`,
          });
        }
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
