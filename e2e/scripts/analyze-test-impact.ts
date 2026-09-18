import { execSync } from "node:child_process";
import { readFileSync, existsSync } from "node:fs";
import { resolve } from "node:path";
import { glob } from "tinyglobby";
import matter from "gray-matter";

const ROOT = resolve(import.meta.dirname, "../..");
const MANIFEST_PATH = resolve(ROOT, "specs/ui/manifest.json");
const SPECS_GLOB = "specs/ui/**/*.md";

type ChangeClassification = "style-only" | "behavioral";

interface ManifestEntry {
  feature_id: string;
  feature: string;
  source_files: string[];
  scenario_ids: string[];
  routes: string[];
  components: string[];
}

interface ImpactResult {
  changed_files: string[];
  impacted_features: Array<{
    feature_id: string;
    feature: string;
    scenario_ids: string[];
    matched_sources: string[];
    classification: ChangeClassification;
  }>;
}

function gitDiffNames(baseRef: string): string[] {
  try {
    const output = execSync(`git diff --name-only ${baseRef}...HEAD`, {
      cwd: ROOT,
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "pipe"],
    });
    return output
      .split("\n")
      .map((l) => l.trim())
      .filter(Boolean);
  } catch {
    console.error(`Failed to run git diff against ${baseRef}`);
    return [];
  }
}

function gitDiffFile(baseRef: string, filePath: string): string {
  try {
    return execSync(`git diff ${baseRef}...HEAD -- "${filePath}"`, {
      cwd: ROOT,
      encoding: "utf-8",
      stdio: ["pipe", "pipe", "pipe"],
    });
  } catch {
    return "";
  }
}

function extractAddedLines(diff: string): string[] {
  const lines = diff.split("\n");
  const added: string[] = [];
  for (const line of lines) {
    if (line.startsWith("+") && !line.startsWith("+++")) {
      added.push(line.slice(1));
    }
  }
  return added;
}

function extractRemovedLines(diff: string): string[] {
  const lines = diff.split("\n");
  const removed: string[] = [];
  for (const line of lines) {
    if (line.startsWith("-") && !line.startsWith("---")) {
      removed.push(line.slice(1));
    }
  }
  return removed;
}

const STYLE_PATTERNS = [
  /className\s*[=:]\s*['"`{]/,
  /class\s*[=:]\s*['"`{]/,
  /style\s*[=:]\s*\{/,
  /\bstyle\s*:\s*\{/,
  /['"]\s*:\s*['"][^'"]*(?:color|background|border|margin|padding|font|display|width|height|opacity|transform|animation|transition|flex|grid|gap|shadow|radius|overflow|position|z-index)/i,
  /css\s*`[^`]*`/,
  /tailwind|tw-|@apply/,
  /import\s+['"].*\.s?css['"]/,
  /\.module\.s?css/,
  /classNames?\s*\(/,
  /styled\(/,
  /makeStyles|useStyles|createStyles/,
  /theme\.(?:spacing|palette|typography|mixins)/,
];

const BEHAVIORAL_PATTERNS = [
  /useEffect\s*\(/,
  /useState\s*\(/,
  /useReducer\s*\(/,
  /useContext\s*\(/,
  /useCallback\s*\(/,
  /useMemo\s*\(/,
  /useRef\s*\(/,
  /useQuery\s*\(/,
  /useMutation\s*\(/,
  /onClick\s*[=:]/,
  /onChange\s*[=:]/,
  /onSubmit\s*[=:]/,
  /onBlur\s*[=:]/,
  /onFocus\s*[=:]/,
  /onKeyDown\s*[=:]/,
  /onKeyUp\s*[=:]/,
  /onKeyPress\s*[=:]/,
  /onMouseEnter\s*[=:]/,
  /onMouseLeave\s*[=:]/,
  /fetch\s*\(/,
  /axios\./,
  /\.get\s*\(|\.post\s*\(|\.put\s*\(|\.delete\s*\(|\.patch\s*\(/,
  /api\./,
  /router\.(?:push|replace|go)/,
  /navigate\s*\(/,
  /useState|set[A-Z]\w*\s*\(/,
  /dispatch\s*\(/,
  /console\.(?:log|warn|error|info)/,
  /throw\s+new\s+Error/,
  /try\s*\{|catch\s*\(/,
  /async\s+function|await\s+/,
  /\bif\s*\(|else\s+if|else\s*\{/,
  /for\s*\(|while\s*\(|\.map\s*\(|\.filter\s*\(|\.reduce\s*\(/,
  /===|!==|==|!=|>=|<=|&&|\|\||[?:]/,
  /export\s+(?:default\s+)?(?:function|const|class)/,
  /import\s+.*\bfrom\b/,
  /new\s+(?:Date|Array|Object|Map|Set|Promise)/,
  /\.(?:then|catch|finally)\s*\(/,
];

function classifyDiffHunk(
  addedLines: string[],
  removedLines: string[],
  filePath: string,
): ChangeClassification {
  const allLines = [...addedLines, ...removedLines];

  const hasBehavioralChange = BEHAVIORAL_PATTERNS.some((p) =>
    allLines.some((line) => p.test(line)),
  );

  if (hasBehavioralChange) {
    return "behavioral";
  }

  const hasStyleChange = STYLE_PATTERNS.some((p) =>
    allLines.some((line) => p.test(line)),
  );

  if (hasStyleChange) {
    return "style-only";
  }

  if (filePath.endsWith(".css") || filePath.endsWith(".scss")) {
    return "style-only";
  }

  if (
    filePath.endsWith(".tsx") ||
    filePath.endsWith(".jsx")
  ) {
    const jsxLines = allLines.filter(
      (l) => /<[A-Z]/.test(l) || /className|style|class=/.test(l),
    );
    const nonJsxLines = allLines.filter(
      (l) => !/<[A-Z]/.test(l) && !/className|style|class=/.test(l),
    );

    if (jsxLines.length > 0 && nonJsxLines.length === 0) {
      return "style-only";
    }
  }

  return "behavioral";
}

function classifyFileChanges(
  baseRef: string,
  filePaths: string[],
): Map<string, ChangeClassification> {
  const classifications = new Map<string, ChangeClassification>();

  for (const file of filePaths) {
    const diff = gitDiffFile(baseRef, file);
    if (!diff) {
      classifications.set(file, "behavioral");
      continue;
    }

    const added = extractAddedLines(diff);
    const removed = extractRemovedLines(diff);
    const classification = classifyDiffHunk(added, removed, file);
    classifications.set(file, classification);
  }

  return classifications;
}

async function loadManifest(): Promise<ManifestEntry[]> {
  if (existsSync(MANIFEST_PATH)) {
    try {
      const raw = JSON.parse(readFileSync(MANIFEST_PATH, "utf-8"));
      const entries = Array.isArray(raw) ? raw : raw.plans || raw.features || [];
      if (entries.length > 0) return entries;
    } catch {
      console.warn("Failed to parse manifest; falling back to spec scan.");
    }
  }
  return scanSpecsForManifest();
}

async function scanSpecsForManifest(): Promise<ManifestEntry[]> {
  const entries: ManifestEntry[] = [];

  try {
    const specFiles = await glob(SPECS_GLOB, { cwd: ROOT });
    for (const rel of specFiles) {
      const abs = resolve(ROOT, rel);
      const content = readFileSync(abs, "utf-8");
      const { data: fm } = matter(content);
      if (!fm.feature_id) continue;

      const scenarioIds = extractScenarioIds(content);

      entries.push({
        feature_id: fm.feature_id,
        feature: fm.feature || fm.feature_id,
        source_files: fm.components || [],
        scenario_ids: scenarioIds,
        routes: fm.routes || [],
        components: fm.components || [],
      });
    }
  } catch {
    // glob may not be available in some environments
  }

  return entries;
}

function extractScenarioIds(content: string): string[] {
  const ids: string[] = [];
  const lines = content.split("\n");
  for (const line of lines) {
    const match = line.match(/^### ([A-Z][A-Z0-9]+-\d{3})\b/);
    if (match) ids.push(match[1]);
  }
  return ids;
}

function mapFilesToFeatures(
  changedFiles: string[],
  manifest: ManifestEntry[],
  fileClassifications: Map<string, ChangeClassification>,
): ImpactResult {
  const impactedMap = new Map<string, ManifestEntry["feature_id"][]>();
  const featureMap = new Map<string, ManifestEntry>();

  for (const entry of manifest) {
    featureMap.set(entry.feature_id, entry);
  }

  for (const file of changedFiles) {
    for (const entry of manifest) {
      const matchedSources = entry.source_files.filter((src) =>
        file.includes(src) || file.endsWith(src),
      );
      if (matchedSources.length > 0) {
        if (!impactedMap.has(entry.feature_id)) {
          impactedMap.set(entry.feature_id, []);
        }
      }
    }
  }

  const impacted_features = Array.from(impactedMap.keys()).map((fid) => {
    const entry = featureMap.get(fid)!;
    const matchedSources = entry.source_files.filter((src) =>
      changedFiles.some((f) => f.includes(src) || f.endsWith(src)),
    );

    const sourceClassifications = matchedSources.map((src) => {
      const matchedFile = changedFiles.find(
        (f) => f.includes(src) || f.endsWith(src),
      );
      return matchedFile
        ? fileClassifications.get(matchedFile) || "behavioral"
        : "behavioral";
    });

    const classification: ChangeClassification = sourceClassifications.includes(
      "behavioral",
    )
      ? "behavioral"
      : "style-only";

    return {
      feature_id: entry.feature_id,
      feature: entry.feature,
      scenario_ids: entry.scenario_ids,
      matched_sources: matchedSources,
      classification,
    };
  });

  return { changed_files: changedFiles, impacted_features };
}

async function postPrComment(result: ImpactResult): Promise<void> {
  const githubToken = process.env.GITHUB_TOKEN;
  const prNumber = process.env.PR_NUMBER;
  const repo = process.env.GITHUB_REPOSITORY;

  if (!githubToken || !prNumber || !repo) return;

  const summary = buildCommentBody(result);
  const apiUrl = `https://api.github.com/repos/${repo}/issues/${prNumber}/comments`;

  try {
    const response = await fetch(apiUrl, {
      method: "POST",
      headers: {
        Authorization: `token ${githubToken}`,
        "Content-Type": "application/json",
      },
      body: JSON.stringify({ body: summary }),
    });

    if (response.ok) {
      console.log("Posted impact analysis as PR comment.");
    } else {
      console.warn(`Failed to post PR comment: ${response.status} ${response.statusText}`);
    }
  } catch {
    console.warn("Failed to post PR comment.");
  }
}

function buildCommentBody(result: ImpactResult): string {
  const lines = [
    "## 🧪 Test Impact Analysis",
    "",
    `**Changed files:** ${result.changed_files.length}`,
    `**Impacted features:** ${result.impacted_features.length}`,
    "",
  ];

  const behavioral = result.impacted_features.filter(
    (f) => f.classification === "behavioral",
  );
  const styleOnly = result.impacted_features.filter(
    (f) => f.classification === "style-only",
  );

  if (result.impacted_features.length === 0) {
    lines.push("No UI features impacted by this change.");
  } else {
    if (behavioral.length > 0) {
      lines.push("### Behavioral changes (test re-run recommended)");
      lines.push("");
      lines.push("| Feature | Scenarios | Matched Sources |");
      lines.push("|---------|-----------|-----------------|");
      for (const f of behavioral) {
        const scenarios = f.scenario_ids.length;
        const sources = f.matched_sources.join(", ") || "—";
        lines.push(
          `| ${f.feature_id} — ${f.feature} | ${scenarios} | ${sources} |`,
        );
      }
      lines.push("");
    }

    if (styleOnly.length > 0) {
      lines.push("### Style-only changes (test re-run may be skipped)");
      lines.push("");
      lines.push("| Feature | Scenarios | Matched Sources |");
      lines.push("|---------|-----------|-----------------|");
      for (const f of styleOnly) {
        const scenarios = f.scenario_ids.length;
        const sources = f.matched_sources.join(", ") || "—";
        lines.push(
          `| ${f.feature_id} — ${f.feature} | ${scenarios} | ${sources} |`,
        );
      }
      lines.push("");
    }
  }

  return lines.join("\n");
}

async function main(): Promise<void> {
  const baseRef = process.argv[2] || process.env.MERGE_BASE || "origin/main";

  console.log(`Analyzing impact against ${baseRef}...`);

  const changedFiles = gitDiffNames(baseRef);
  if (changedFiles.length === 0) {
    console.log("No changed files found.");
    return;
  }

  console.log(`Changed files (${changedFiles.length}):`);
  for (const f of changedFiles) console.log(`  ${f}`);

  const fileClassifications = classifyFileChanges(baseRef, changedFiles);

  console.log("\nFile classifications:");
  for (const [file, cls] of fileClassifications) {
    console.log(`  ${cls.padEnd(12)} ${file}`);
  }

  const manifest = await loadManifest();
  const result = mapFilesToFeatures(changedFiles, manifest, fileClassifications);

  console.log(`\nImpacted features (${result.impacted_features.length}):`);
  if (result.impacted_features.length === 0) {
    console.log("  None");
  } else {
    for (const f of result.impacted_features) {
      console.log(
        `  ${f.feature_id} — ${f.feature} (${f.scenario_ids.length} scenarios) [${f.classification}]`,
      );
    }

    const behavioral = result.impacted_features.filter(
      (f) => f.classification === "behavioral",
    );
    const styleOnly = result.impacted_features.filter(
      (f) => f.classification === "style-only",
    );
    if (behavioral.length > 0) {
      console.log(
        `\n  ${behavioral.length} feature(s) require test re-run (behavioral changes)`,
      );
    }
    if (styleOnly.length > 0) {
      console.log(
        `  ${styleOnly.length} feature(s) are style-only (test re-run may be skipped)`,
      );
    }
  }

  if (process.env.CI) {
    await postPrComment(result);
  }

  console.log(JSON.stringify(result, null, 2));
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(1);
});
