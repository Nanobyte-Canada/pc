import { resolve, relative } from "node:path";
import { readFileSync } from "node:fs";
import { Project } from "ts-morph";
import { glob } from "tinyglobby";

const ROOT = resolve(import.meta.dirname, "../..");
const FRONTEND_SRC = resolve(ROOT, "frontend/src");
const FRONTEND_TSCONFIG = resolve(ROOT, "frontend/tsconfig.json");

interface ImpactGraph {
  changed_file: string;
  direct_importers: string[];
  transitive_importers: string[];
  impacted_tests: string[];
}

function toRepoRelative(absPath: string): string {
  return relative(ROOT, absPath);
}

function buildProject(): Project {
  return new Project({
    tsConfigFilePath: FRONTEND_TSCONFIG,
    skipAddingFilesFromTsConfig: false,
  });
}

function findDirectImporters(project: Project, changedFile: string): string[] {
  const absChanged = resolve(ROOT, changedFile);
  const importers = new Set<string>();

  for (const sf of project.getSourceFiles()) {
    const filePath = sf.getFilePath();
    if (filePath === absChanged) continue;

    for (const imp of sf.getImportDeclarations()) {
      const resolved = imp.getModuleSpecifierSourceFile();
      if (resolved && resolved.getFilePath() === absChanged) {
        importers.add(filePath);
        break;
      }
    }

    for (const exp of sf.getExportDeclarations()) {
      const resolved = exp.getModuleSpecifierSourceFile();
      if (resolved && resolved.getFilePath() === absChanged) {
        importers.add(filePath);
        break;
      }
    }
  }

  return Array.from(importers).sort();
}

function findTransitiveImporters(
  project: Project,
  directImporters: string[],
  changedFile: string,
): string[] {
  const absChanged = resolve(ROOT, changedFile);
  const visited = new Set<string>([absChanged, ...directImporters]);
  const result: string[] = [];

  let frontier = [...directImporters];

  while (frontier.length > 0) {
    const nextFrontier: string[] = [];

    for (const filePath of frontier) {
      for (const sf of project.getSourceFiles()) {
        const sfPath = sf.getFilePath();
        if (visited.has(sfPath)) continue;

        for (const imp of sf.getImportDeclarations()) {
          const resolved = imp.getModuleSpecifierSourceFile();
          if (resolved && resolved.getFilePath() === filePath) {
            visited.add(sfPath);
            result.push(sfPath);
            nextFrontier.push(sfPath);
            break;
          }
        }

        for (const exp of sf.getExportDeclarations()) {
          const resolved = exp.getModuleSpecifierSourceFile();
          if (resolved && resolved.getFilePath() === filePath) {
            visited.add(sfPath);
            result.push(sfPath);
            nextFrontier.push(sfPath);
            break;
          }
        }
      }
    }

    frontier = nextFrontier;
  }

  return result.sort();
}

async function findImpactedTests(
  transitiveImporters: string[],
  directImporters: string[],
  changedFile: string,
): Promise<string[]> {
  const allImpacted = new Set<string>([
    resolve(ROOT, changedFile),
    ...directImporters,
    ...transitiveImporters,
  ]);

  const EXTENSIONS = ["", ".ts", ".tsx", ".js", ".jsx"];

  function isImpacted(resolved: string): boolean {
    for (const ext of EXTENSIONS) {
      if (allImpacted.has(resolved + ext)) return true;
    }
    return false;
  }

  const testPatterns = [
    "frontend/src/**/*.test.{ts,tsx}",
    "frontend/src/**/*.spec.{ts,tsx}",
    "frontend/src/**/__tests__/**/*.{ts,tsx}",
    "frontend/src/**/*.e2e.{ts,tsx}",
    "e2e/tests/**/*.ts",
  ];

  const testFiles = await glob(testPatterns, { cwd: ROOT });
  const impactedTests: string[] = [];

  for (const relTest of testFiles) {
    const absTest = resolve(ROOT, relTest);
    try {
      const content = readFileSync(absTest, "utf-8");
      const importRegex = /(?:import|export)\s+.*?from\s+['"]([^'"]+)['"]/g;
      let match: RegExpExecArray | null;

      while ((match = importRegex.exec(content)) !== null) {
        const specifier = match[1];
        let resolved: string;
        if (specifier.startsWith("@/")) {
          resolved = resolve(FRONTEND_SRC, specifier.slice(2));
        } else {
          resolved = resolve(absTest, "..", specifier);
        }
        if (isImpacted(resolved)) {
          impactedTests.push(toRepoRelative(absTest));
          break;
        }
      }
    } catch {
      // skip files that can't be read
    }
  }

  return [...new Set(impactedTests)].sort();
}

async function main(): Promise<void> {
  const changedFile = process.argv[2];

  if (!changedFile) {
    console.error("Usage: npx tsx scripts/build-impact-graph.ts <changed-file-path>");
    console.error("Example: npx tsx scripts/build-impact-graph.ts src/components/Button.tsx");
    process.exit(1);
  }

  let repoRelative: string;

  if (changedFile.startsWith("frontend/src/")) {
    repoRelative = changedFile;
  } else if (changedFile.startsWith("src/")) {
    repoRelative = `frontend/${changedFile}`;
  } else {
    console.error(`File must be within frontend/src/: ${changedFile}`);
    console.error("Provide path as frontend/src/... or src/...");
    process.exit(1);
  }

  const absChanged = resolve(ROOT, repoRelative);
  if (!absChanged.startsWith(FRONTEND_SRC)) {
    console.error(`File must be within frontend/src/: ${changedFile}`);
    process.exit(1);
  }

  console.error(`Building import graph for ${repoRelative}...`);

  const project = buildProject();

  const directImporters = findDirectImporters(project, repoRelative);
  console.error(`Direct importers: ${directImporters.length}`);

  const transitiveImporters = findTransitiveImporters(project, directImporters, repoRelative);
  console.error(`Transitive importers: ${transitiveImporters.length}`);

  const impactedTests = await findImpactedTests(transitiveImporters, directImporters, repoRelative);
  console.error(`Impacted tests: ${impactedTests.length}`);

  const graph: ImpactGraph = {
    changed_file: repoRelative,
    direct_importers: directImporters.map(toRepoRelative),
    transitive_importers: transitiveImporters.map(toRepoRelative),
    impacted_tests: impactedTests,
  };

  console.log(JSON.stringify(graph, null, 2));
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
