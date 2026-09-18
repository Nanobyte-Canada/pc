import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const DISCOVERED_PATH = path.join(ROOT, "docs", "testing", "discovered-routes.json");
const TESTS_DIR = path.join(ROOT, "e2e", "tests");
const EXCLUSIONS_PATH = path.join(ROOT, "e2e", "route-exclusions.json");

interface DiscoveredRoute {
  path: string;
  params: string[];
}

interface Exclusion {
  route: string;
  reason: string;
}

interface ExclusionsFile {
  exclusions: Exclusion[];
}

/** Strip leading slashes so "/screener/:type" and "screener/:type" are handled identically. */
function normalizePath(route: string): string {
  return route.trim().replace(/^\/+/, "");
}

/** Split a route/path into non-empty segments. */
function segments(route: string): string[] {
  return normalizePath(route)
    .split("/")
    .filter((seg) => seg.length > 0);
}

/**
 * Check whether a concrete referenced path covers a discovered route.
 * The discovered route is split into segments; a ":param" segment matches any
 * single non-empty segment of the referenced path. Trailing extra segments in
 * the referenced path are allowed (e.g. /brokers/positions/ABC-1 also covers
 * the brokers/positions listing route).
 */
function pathCoversRoute(routeSegs: string[], literalSegs: string[]): boolean {
  if (routeSegs.length === 0) {
    return literalSegs.length === 0;
  }
  if (literalSegs.length < routeSegs.length) {
    return false;
  }
  for (let i = 0; i < routeSegs.length; i++) {
    const routeSeg = routeSegs[i];
    if (routeSeg.startsWith(":")) {
      if (literalSegs[i].length === 0) return false;
    } else if (routeSeg !== literalSegs[i]) {
      return false;
    }
  }
  return true;
}

/** Extract all string literal contents from a source file. */
function extractStringLiterals(content: string): string[] {
  const literals: string[] = [];
  const pattern = /'([^'\n]*)'|"([^"\n]*)"|`([^`]*)`/g;
  let match: RegExpExecArray | null;
  while ((match = pattern.exec(content)) !== null) {
    literals.push(match[1] ?? match[2] ?? match[3] ?? "");
  }
  return literals;
}

function collectSpecFiles(dir: string): string[] {
  const out: string[] = [];
  for (const entry of fs.readdirSync(dir, { withFileTypes: true })) {
    const full = path.join(dir, entry.name);
    if (entry.isDirectory()) {
      out.push(...collectSpecFiles(full));
    } else if (entry.isFile() && entry.name.endsWith(".spec.ts")) {
      out.push(full);
    }
  }
  return out;
}

function loadJson<T>(filePath: string): T {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Required file not found: ${filePath}`);
  }
  return JSON.parse(fs.readFileSync(filePath, "utf-8")) as T;
}

function main(): void {
  const discovered = loadJson<{ routes: DiscoveredRoute[] }>(DISCOVERED_PATH);

  // Collect every path-like string literal referenced in any spec file.
  const specFiles = collectSpecFiles(TESTS_DIR);
  const referencedSegs: string[][] = [];
  for (const file of specFiles) {
    const content = fs.readFileSync(file, "utf-8");
    for (const literal of extractStringLiterals(content)) {
      // Only treat literals that look like app paths (leading slash or a
      // route-shaped relative reference such as "portfolios"). The bare root
      // path "/" is kept as a zero-segment reference.
      const normalized = normalizePath(literal);
      if (!literal.startsWith("/")) continue;
      if (normalized.length > 0 && /\s/.test(normalized)) continue;
      referencedSegs.push(segments(normalized));
    }
  }

  // Load documented exclusions (optional file).
  let exclusions: Exclusion[] = [];
  if (fs.existsSync(EXCLUSIONS_PATH)) {
    const parsed = loadJson<ExclusionsFile>(EXCLUSIONS_PATH);
    exclusions = parsed.exclusions ?? [];
  }

  const covered: string[] = [];
  const excluded: string[] = [];
  const uncovered: string[] = [];

  for (const route of discovered.routes) {
    const routeSegs = segments(route.path);

    const isTestCovered = referencedSegs.some((litSegs) =>
      pathCoversRoute(routeSegs, litSegs),
    );
    if (isTestCovered) {
      covered.push(route.path);
      continue;
    }

    const exclusion = exclusions.find((ex) => {
      const reason = typeof ex.reason === "string" ? ex.reason.trim() : "";
      if (reason.length === 0) return false;
      return pathCoversRoute(routeSegs, segments(ex.route));
    });
    if (exclusion) {
      excluded.push(route.path);
      continue;
    }

    uncovered.push(route.path);
  }

  console.log(`Discovered routes: ${discovered.routes.length}`);
  console.log(`Spec files scanned: ${specFiles.length}`);
  console.log(`Test-covered: ${covered.length}`);
  for (const route of covered) {
    console.log(`  [covered]  ${route}`);
  }
  console.log(`Excluded (documented): ${excluded.length}`);
  for (const route of excluded) {
    console.log(`  [excluded] ${route}`);
  }

  if (uncovered.length > 0) {
    console.error(`Uncovered routes (${uncovered.length}):`);
    for (const route of uncovered) {
      console.error(`  ${route}`);
    }
    process.exit(1);
  }

  console.log("All discovered routes are test-covered or have a documented exclusion.");
}

main();
