import fs from "node:fs";
import path from "node:path";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const APP_TSX = path.join(ROOT, "frontend", "src", "App.tsx");
const OUT_DIR = path.join(ROOT, "docs", "testing");
const OUT_PATH = path.join(OUT_DIR, "discovered-routes.json");

interface DiscoveredRoute {
  path: string;
  params: string[];
}

function extractRoutes(content: string): DiscoveredRoute[] {
  const routePattern = /<Route\s+[^>]*path="([^"]+)"/g;
  const routes: DiscoveredRoute[] = [];
  const seen = new Set<string>();
  let match: RegExpExecArray | null;

  while ((match = routePattern.exec(content)) !== null) {
    const raw = match[1];
    if (raw === "*" || seen.has(raw)) continue;
    seen.add(raw);

    const paramPattern = /:(\w+)/g;
    const params: string[] = [];
    let paramMatch: RegExpExecArray | null;
    while ((paramMatch = paramPattern.exec(raw)) !== null) {
      params.push(paramMatch[1]);
    }

    routes.push({ path: raw, params });
  }

  return routes;
}

async function main(): Promise<void> {
  if (!fs.existsSync(APP_TSX)) {
    console.error(`App.tsx not found at ${APP_TSX}`);
    process.exit(1);
  }

  const content = fs.readFileSync(APP_TSX, "utf-8");
  const routes = extractRoutes(content);

  const output = { discovered_at: new Date().toISOString(), routes };

  fs.mkdirSync(OUT_DIR, { recursive: true });
  fs.writeFileSync(OUT_PATH, JSON.stringify(output, null, 2) + "\n");

  console.log(JSON.stringify(output, null, 2));
  console.log(`\nWrote ${OUT_PATH} (${routes.length} route(s))`);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
