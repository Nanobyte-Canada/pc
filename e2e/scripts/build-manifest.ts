import fs from "node:fs";
import path from "node:path";
import { glob } from "tinyglobby";
import matter from "gray-matter";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const SPECS_GLOB = path.join(ROOT, "specs", "ui", "**", "*.md");
const OUT_PATH = path.join(ROOT, "specs", "ui", "manifest.json");

interface Scenario {
  id: string;
  priority: string;
  type: string;
  layer: string;
  description: string;
}

interface Plan {
  feature_id: string;
  feature: string;
  owner: string;
  status: string;
  priority: string;
  critical_journeys: string[];
  requirement_refs: string[];
  scenarios: Scenario[];
  routes: string[];
  roles: string[];
  tags: string[];
  source_file: string;
}

interface Manifest {
  generated: string;
  plans: Plan[];
}

function parseScenarios(content: string, featureId: string): Scenario[] {
  const scenarioRegex = new RegExp(
    `^\\|\\s*${escapeRegex(featureId)}-(\\d+)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(\\w[-\\w]*)\\s*\\|\\s*(\\w+)\\s*\\|\\s*(.+)\\s*\\|$`,
    "gm",
  );
  const scenarios: Scenario[] = [];
  let match: RegExpExecArray | null;

  while ((match = scenarioRegex.exec(content)) !== null) {
    scenarios.push({
      id: `${featureId}-${match[1]}`,
      priority: match[2],
      type: match[3],
      layer: match[4],
      description: match[5].trim(),
    });
  }

  return scenarios;
}

function escapeRegex(str: string): string {
  return str.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
}

function parseSpecFile(filePath: string, rootRelative: string): Plan | null {
  const raw = fs.readFileSync(filePath, "utf-8");
  const { data, content } = matter(raw);

  const featureId = data.feature_id;
  if (!featureId) {
    console.warn(`Skipping ${rootRelative}: no feature_id in front matter`);
    return null;
  }

  return {
    feature_id: featureId,
    feature: data.feature ?? "",
    owner: data.owner ?? "",
    status: data.status ?? "draft",
    priority: data.priority ?? "normal",
    critical_journeys: data.critical_journeys ?? [],
    requirement_refs: data.requirement_refs ?? [],
    scenarios: parseScenarios(content, featureId),
    routes: data.routes ?? [],
    roles: data.roles ?? [],
    tags: data.tags ?? [],
    source_file: rootRelative,
  };
}

async function main(): Promise<void> {
  const files = await glob(SPECS_GLOB, { cwd: ROOT });
  const plans: Plan[] = [];

  for (const rel of files) {
    const basename = path.basename(rel);
    if (basename === "_template.md") continue;

    const abs = path.join(ROOT, rel);
    const plan = parseSpecFile(abs, rel);
    if (plan) plans.push(plan);
  }

  const manifest: Manifest = {
    generated: new Date().toISOString(),
    plans,
  };

  fs.mkdirSync(path.dirname(OUT_PATH), { recursive: true });
  fs.writeFileSync(OUT_PATH, JSON.stringify(manifest, null, 2) + "\n");
  console.log(`Wrote ${OUT_PATH} (${plans.length} plan(s))`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
