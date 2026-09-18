import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const DISCOVERED_PATH = path.join(ROOT, "docs", "testing", "discovered-routes.json");
const MANIFEST_PATH = path.join(ROOT, "specs", "ui", "manifest.json");

interface DiscoveredRoute {
  path: string;
  params: string[];
}

interface Plan {
  feature_id: string;
  routes: string[];
  tags: string[];
  source_file: string;
}

interface Manifest {
  plans: Plan[];
}

function parseArgs(): string[] {
  const args = process.argv.slice(2);
  const excludes: string[] = [];

  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--exclude" && args[i + 1]) {
      excludes.push(args[i + 1]);
      i++;
    }
  }

  return excludes;
}

function loadJson<T>(filePath: string): T {
  if (!fs.existsSync(filePath)) {
    throw new Error(`Required file not found: ${filePath}`);
  }
  return JSON.parse(fs.readFileSync(filePath, "utf-8"));
}

function normalizeRoute(route: string): string {
  return route
    .replace(/:(\w+)/g, "**")
    .replace(/\/+$/, "");
}

function main(): void {
  const excludes = parseArgs();

  const discovered = loadJson<{ routes: DiscoveredRoute[] }>(DISCOVERED_PATH);
  const manifest = loadJson<Manifest>(MANIFEST_PATH);

  const plannedRoutes = new Set<string>();
  for (const plan of manifest.plans) {
    for (const route of plan.routes) {
      plannedRoutes.add(normalizeRoute(route));
    }
  }

  const smokeTaggedPlanRoutes = new Set<string>();
  for (const plan of manifest.plans) {
    if (plan.tags.includes("smoke")) {
      for (const route of plan.routes) {
        smokeTaggedPlanRoutes.add(normalizeRoute(route));
      }
    }
  }

  const uncovered: string[] = [];

  for (const route of discovered.routes) {
    const normalized = normalizeRoute(route.path);

    const isExcluded = excludes.some((ex) => {
      const normalizedEx = normalizeRoute(ex);
      return normalized === normalizedEx || normalized.startsWith(normalizedEx + "/");
    });

    if (isExcluded) continue;

    if (!plannedRoutes.has(normalized) && !smokeTaggedPlanRoutes.has(normalized)) {
      uncovered.push(route.path);
    }
  }

  if (uncovered.length === 0) {
    console.log("All discovered routes are covered by test plans.");
  } else {
    console.error(`Uncovered routes (${uncovered.length}):`);
    for (const route of uncovered) {
      console.error(`  ${route}`);
    }
    process.exit(1);
  }
}

main();
