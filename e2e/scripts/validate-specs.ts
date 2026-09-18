import { readFile, access } from "node:fs/promises";
import { resolve, relative } from "node:path";
import matter from "gray-matter";
import Ajv from "ajv";
import { glob } from "tinyglobby";

const REPO_ROOT = resolve(import.meta.dirname, "../..");
const SPECS_GLOB = "specs/ui/**/*.md";
const TEMPLATE_NAME = "_template.md";
const CODEOWNERS_PATH = ".github/CODEOWNERS";
const REQUIREMENTS_INDEX_PATH = "docs/testing/requirements-index.json";

interface FrontMatter {
  feature_id: string;
  feature: string;
  owner: string;
  status: string;
  priority: string;
  critical_journeys: string[];
  requirement_refs: string[];
  routes: string[];
  roles: string[];
  flags: string[];
  components: string[];
  source_overrides: string[];
  tags: string[];
  last_reviewed: string;
  review: {
    approved_by: string;
    approved_on: string;
    requirement_version: string;
  };
  next_id: string;
}

interface ValidationError {
  file: string;
  line: number | null;
  field: string;
  message: string;
  severity: "error" | "warning";
}

const FRONT_MATTER_SCHEMA = {
  type: "object",
  required: [
    "feature_id",
    "feature",
    "owner",
    "status",
    "priority",
    "critical_journeys",
    "requirement_refs",
    "routes",
    "roles",
    "flags",
    "components",
    "source_overrides",
    "tags",
    "last_reviewed",
    "review",
    "next_id",
  ],
  properties: {
    feature_id: { type: "string", pattern: "^[A-Z][A-Z0-9-]+$" },
    feature: { type: "string", minLength: 1 },
    owner: { type: "string", minLength: 1 },
    status: { type: "string", enum: ["draft", "approved", "retired"] },
    priority: { type: "string", enum: ["critical", "high", "normal"] },
    critical_journeys: {
      type: "array",
      items: { type: "string", pattern: "^CJ-\\d{2}$" },
    },
    requirement_refs: {
      type: "array",
      items: { type: "string", pattern: "^RQ-[A-Z]+-\\d{3}$" },
    },
    routes: { type: "array", items: { type: "string" } },
    roles: { type: "array", items: { type: "string" } },
    flags: { type: "array", items: { type: "string" } },
    components: { type: "array", items: { type: "string" } },
    source_overrides: { type: "array", items: { type: "string" } },
    tags: { type: "array", items: { type: "string" } },
    last_reviewed: { type: "string", pattern: "^\\d{4}-\\d{2}-\\d{2}$" },
    review: {
      type: "object",
      required: ["approved_by", "approved_on", "requirement_version"],
      properties: {
        approved_by: { type: "string" },
        approved_on: { type: "string" },
        requirement_version: { type: "string" },
      },
    },
    next_id: { type: "string", pattern: "^\\d{3}$" },
  },
  additionalProperties: false,
} as const;

async function fileExists(path: string): Promise<boolean> {
  try {
    await access(path);
    return true;
  } catch {
    return false;
  }
}

function parseLineNumber(content: string, searchString: string): number | null {
  const lines = content.split("\n");
  for (let i = 0; i < lines.length; i++) {
    if (lines[i].includes(searchString)) {
      return i + 1;
    }
  }
  return null;
}

function extractScenarioIds(content: string): Array<{ id: string; line: number }> {
  const results: Array<{ id: string; line: number }> = [];
  const lines = content.split("\n");
  for (let i = 0; i < lines.length; i++) {
    const match = lines[i].match(/^### ([A-Z][A-Z0-9]+-\d{3})\b/);
    if (match) {
      results.push({ id: match[1], line: i + 1 });
    }
  }
  return results;
}

async function loadCodeowners(): Promise<Set<string>> {
  const owners = new Set<string>();
  const path = resolve(REPO_ROOT, CODEOWNERS_PATH);
  if (!(await fileExists(path))) {
    return owners;
  }
  const content = await readFile(path, "utf-8");
  for (const line of content.split("\n")) {
    const trimmed = line.trim();
    if (!trimmed || trimmed.startsWith("#")) continue;
    const parts = trimmed.split(/\s+/);
    for (let i = 1; i < parts.length; i++) {
      const owner = parts[i].replace(/^@/, "");
      if (owner) owners.add(owner);
    }
  }
  return owners;
}

async function loadRequirementsIndex(): Promise<Set<string> | null> {
  const path = resolve(REPO_ROOT, REQUIREMENTS_INDEX_PATH);
  if (!(await fileExists(path))) {
    return null;
  }
  const content = await readFile(path, "utf-8");
  const index = JSON.parse(content);
  const ids = new Set<string>();
  if (index?.requirements && Array.isArray(index.requirements)) {
    for (const req of index.requirements) {
      if (req.id) ids.add(req.id);
    }
  }
  return ids;
}

async function validateFile(
  filePath: string,
  ajvValidator: ReturnType<typeof Ajv.prototype.compile>,
  codeowners: Set<string>,
  requirementsIndex: Set<string> | null,
): Promise<ValidationError[]> {
  const errors: ValidationError[] = [];
  const relPath = relative(REPO_ROOT, filePath);
  const content = await readFile(filePath, "utf-8");
  const { data: rawFrontMatter } = matter(content);

  const frontMatter = {
    ...rawFrontMatter,
    last_reviewed:
      rawFrontMatter.last_reviewed instanceof Date
        ? rawFrontMatter.last_reviewed.toISOString().slice(0, 10)
        : String(rawFrontMatter.last_reviewed ?? ""),
    next_id:
      typeof rawFrontMatter.next_id === "number"
        ? String(rawFrontMatter.next_id).padStart(3, "0")
        : String(rawFrontMatter.next_id ?? ""),
  };

  const valid = ajvValidator(frontMatter);
  if (!valid && ajvValidator.errors) {
    for (const err of ajvValidator.errors) {
      const field = err.instancePath.replace(/^\//, "").replace(/\//g, ".");
      const line = field
        ? parseLineNumber(content, field.split(".").pop()!)
        : parseLineNumber(content, "feature_id");
      errors.push({
        file: relPath,
        line,
        field: field || "front-matter",
        message: err.message ?? "invalid value",
        severity: "error",
      });
    }
  }

  const fm = frontMatter as FrontMatter;

  if (fm) {
    if (fm.owner && !codeowners.has(fm.owner)) {
      const line = parseLineNumber(content, "owner:");
      errors.push({
        file: relPath,
        line,
        field: "owner",
        message: `owner '${fm.owner}' not found in ${CODEOWNERS_PATH}`,
        severity: "error",
      });
    }

    if (requirementsIndex && fm.requirement_refs) {
      for (const ref of fm.requirement_refs) {
        if (!requirementsIndex.has(ref)) {
          const line = parseLineNumber(content, ref);
          errors.push({
            file: relPath,
            line,
            field: "requirement_refs",
            message: `requirement '${ref}' not found in requirements index`,
            severity: "error",
          });
        }
      }
    }
  }

  const scenarios = extractScenarioIds(content);
  for (const scenario of scenarios) {
    if (fm && fm.next_id) {
      const scenarioNum = parseInt(scenario.id.split("-").pop()!, 10);
      const nextNum = parseInt(fm.next_id, 10);
      if (scenarioNum >= nextNum) {
        errors.push({
          file: relPath,
          line: scenario.line,
          field: "scenario-id",
          message: `scenario '${scenario.id}' must be less than next_id '${fm.next_id}'`,
          severity: "error",
        });
      }
    }
  }

  return errors;
}

async function main(): Promise<void> {
  const allErrors: ValidationError[] = [];
  const warnings: ValidationError[] = [];

  const codeowners = await loadCodeowners();
  if (codeowners.size === 0) {
    warnings.push({
      file: CODEOWNERS_PATH,
      line: null,
      field: "CODEOWNERS",
      message: "CODEOWNERS file not found or empty; owner validation skipped",
      severity: "warning",
    });
  }

  const requirementsIndex = await loadRequirementsIndex();
  if (requirementsIndex === null) {
    warnings.push({
      file: REQUIREMENTS_INDEX_PATH,
      line: null,
      field: "requirements-index",
      message:
        "requirements-index.json not found; requirement_refs validation skipped",
      severity: "warning",
    });
  }

  const specFiles = await glob([SPECS_GLOB], {
    cwd: REPO_ROOT,
    absolute: true,
  });

  const planFiles = specFiles.filter(
    (f) => !f.endsWith(TEMPLATE_NAME),
  );

  if (planFiles.length === 0) {
    console.log("No spec plans found (specs/ui/**/*.md). Nothing to validate.");
    for (const w of warnings) {
      console.warn(`  WARN: ${w.message}`);
    }
    process.exit(0);
  }

  const ajv = new Ajv({ allErrors: true });
  const validate = ajv.compile(FRONT_MATTER_SCHEMA);

  const globalScenarioIds = new Map<string, string>();

  for (const filePath of planFiles) {
    const errors = await validateFile(filePath, validate, codeowners, requirementsIndex);

    const content = await readFile(filePath, "utf-8");
    const scenarios = extractScenarioIds(content);
    const relPath = relative(REPO_ROOT, filePath);

    for (const scenario of scenarios) {
      const existing = globalScenarioIds.get(scenario.id);
      if (existing) {
        errors.push({
          file: relPath,
          line: scenario.line,
          field: "scenario-id",
          message: `duplicate scenario '${scenario.id}' also in ${existing}`,
          severity: "error",
        });
      } else {
        globalScenarioIds.set(scenario.id, relPath);
      }
    }

    allErrors.push(...errors);
  }

  const errorCount = allErrors.filter((e) => e.severity === "error").length;
  const warnCount = warnings.length;

  for (const err of allErrors) {
    const loc = err.line ? `:${err.line}` : "";
    const tag = err.severity === "error" ? "ERROR" : "WARN";
    console.error(`${tag} ${err.file}${loc} [${err.field}]: ${err.message}`);
  }
  for (const w of warnings) {
    const loc = w.line ? `:${w.line}` : "";
    console.warn(`WARN ${w.file}${loc} [${w.field}]: ${w.message}`);
  }

  console.log(
    `\nValidated ${planFiles.length} plan(s): ${errorCount} error(s), ${warnCount} warning(s).`,
  );

  process.exit(errorCount > 0 ? 1 : 0);
}

main().catch((err) => {
  console.error("Fatal error:", err);
  process.exit(2);
});
