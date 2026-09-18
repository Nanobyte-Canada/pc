import fs from "node:fs";
import path from "node:path";
import { glob } from "tinyglobby";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const OUT_DIR = path.join(ROOT, "docs", "testing");
const CRITICAL_JOURNEYS_PATH = path.join(OUT_DIR, "critical-journeys.md");
const MANIFEST_PATH = path.join(ROOT, "specs", "ui", "manifest.json");
const PLAYWRIGHT_REPORT = path.join(ROOT, "e2e", "results", "results.json");
const VITEST_REPORT_GLOB = "frontend/results/**/*.json";

interface CriticalJourney {
  id: string;
  name: string;
  features: string;
}

interface Scenario {
  id: string;
  featureId: string;
  priority: string;
  type: string;
  layer: string;
  description: string;
  criticalJourneys: string[];
}

interface TestResult {
  testId: string;
  scenarioId: string;
  status: "passed" | "failed" | "skipped" | "flaky";
  duration: number;
  retries: number;
  browser: string;
  file: string;
  suite: string;
}

interface JourneyCoverage {
  journeyId: string;
  journeyName: string;
  totalScenarios: number;
  testedScenarios: number;
  passedScenarios: number;
  failedScenarios: number;
  skippedScenarios: number;
  flakyScenarios: number;
  coveragePercent: number;
  passRate: number;
  scenarios: Array<{
    scenarioId: string;
    status: "passed" | "failed" | "skipped" | "flaky" | "untested";
  }>;
}

interface CoverageReport {
  generated: string;
  summary: {
    totalTests: number;
    passed: number;
    failed: number;
    skipped: number;
    flaky: number;
    duration: number;
    passRate: number;
  };
  journeyCoverage: JourneyCoverage[];
  scenarioResults: TestResult[];
  unmappedTests: Array<{
    testName: string;
    file: string;
    status: string;
  }>;
  sources: {
    playwright: boolean;
    vitest: boolean;
    manifest: boolean;
  };
}

function parseCriticalJourneys(): CriticalJourney[] {
  if (!fs.existsSync(CRITICAL_JOURNEYS_PATH)) {
    console.warn("critical-journeys.md not found; journey coverage will be empty.");
    return [];
  }

  const content = fs.readFileSync(CRITICAL_JOURNEYS_PATH, "utf-8");
  const journeys: CriticalJourney[] = [];
  const lines = content.split("\n");

  for (const line of lines) {
    const match = line.match(
      /^\|\s*(CJ-\d+)\s*\|\s*(.+?)\s*\|\s*(.+?)\s*\|$/
    );
    if (match) {
      journeys.push({
        id: match[1],
        name: match[2].trim(),
        features: match[3].trim(),
      });
    }
  }

  return journeys;
}

function parseManifest(): Scenario[] {
  if (!fs.existsSync(MANIFEST_PATH)) {
    console.warn("manifest.json not found; scenario mapping will be empty.");
    return [];
  }

  const content = fs.readFileSync(MANIFEST_PATH, "utf-8");
  const manifest = JSON.parse(content);
  const scenarios: Scenario[] = [];

  if (manifest.plans && Array.isArray(manifest.plans)) {
    for (const plan of manifest.plans) {
      if (plan.scenarios && Array.isArray(plan.scenarios)) {
        for (const scenario of plan.scenarios) {
          scenarios.push({
            id: scenario.id,
            featureId: plan.feature_id,
            priority: scenario.priority,
            type: scenario.type,
            layer: scenario.layer,
            description: scenario.description,
            criticalJourneys: plan.critical_journeys ?? [],
          });
        }
      }
    }
  }

  return scenarios;
}

function extractScenarioId(testName: string): string | null {
  const match = testName.match(/\[([A-Z]+-\d{3})\]/);
  return match ? match[1] : null;
}

function parsePlaywrightReport(): TestResult[] {
  if (!fs.existsSync(PLAYWRIGHT_REPORT)) {
    console.warn("Playwright results.json not found; browser tests will be empty.");
    return [];
  }

  const content = fs.readFileSync(PLAYWRIGHT_REPORT, "utf-8");
  const report = JSON.parse(content);
  const results: TestResult[] = [];

  function walkSuites(suites: any[], browser: string) {
    for (const suite of suites) {
      const suiteName = suite.title || "";
      if (suite.specs) {
        for (const spec of suite.specs) {
          const testName = spec.title || "";
          const scenarioId = extractScenarioId(testName);

          for (const test of spec.tests || []) {
            for (const result of test.results || []) {
              let status: TestResult["status"] = "failed";
              if (result.status === "passed") status = "passed";
              else if (result.status === "skipped") status = "skipped";
              else if (result.status === "failed") status = "failed";
              else if (result.status === "timedOut") status = "failed";

              results.push({
                testId: `${browser}:${suiteName}:${testName}`,
                scenarioId: scenarioId || "",
                status,
                duration: result.duration || 0,
                retries: result.retry || 0,
                browser,
                file: spec.file || "",
                suite: suiteName,
              });
            }
          }
        }
      }
      if (suite.suites) {
        walkSuites(suite.suites, browser);
      }
    }
  }

  const projects = report.projects || [];
  for (const project of projects) {
    const browser = project.name || "unknown";
    walkSuites(project.suites || [], browser);
  }

  return results;
}

async function parseVitestReport(): Promise<TestResult[]> {
  const results: TestResult[] = [];
  const vitestFiles = await glob(VITEST_REPORT_GLOB, { cwd: ROOT });

  for (const rel of vitestFiles) {
    const abs = path.join(ROOT, rel);
    if (!fs.existsSync(abs)) continue;

    const content = fs.readFileSync(abs, "utf-8");
    const report = JSON.parse(content);

    if (report.testResults && Array.isArray(report.testResults)) {
      for (const testResult of report.testResults) {
        const testName = testResult.name || "";
        const scenarioId = extractScenarioId(testName);

        let status: TestResult["status"] = "failed";
        if (testResult.status === "passed") status = "passed";
        else if (testResult.status === "skipped") status = "skipped";

        results.push({
          testId: `vitest:${testResult.name}`,
          scenarioId: scenarioId || "",
          status,
          duration: testResult.endTime ? testResult.endTime - testResult.startTime : 0,
          retries: 0,
          browser: "jsdom",
          file: testResult.name || "",
          suite: "vitest",
        });
      }
    }
  }

  return results;
}

async function buildCoverageReport(): Promise<CoverageReport> {
  const journeys = parseCriticalJourneys();
  const scenarios = parseManifest();
  const playwrightResults = parsePlaywrightReport();
  const vitestResults = await parseVitestReport();
  const allResults = [...playwrightResults, ...vitestResults];

  const totalTests = allResults.length;
  const passed = allResults.filter((r) => r.status === "passed").length;
  const failed = allResults.filter((r) => r.status === "failed").length;
  const skipped = allResults.filter((r) => r.status === "skipped").length;
  const flaky = allResults.filter((r) => r.status === "flaky").length;
  const duration = allResults.reduce((sum, r) => sum + r.duration, 0);
  const passRate = totalTests > 0 ? (passed / totalTests) * 100 : 0;

  const scenarioResults: TestResult[] = allResults.filter((r) => r.scenarioId);

  const journeyCoverage: JourneyCoverage[] = journeys.map((journey) => {
    const journeyScenarios = scenarios.filter((s) =>
      s.criticalJourneys.includes(journey.id)
    );
    const totalScenarios = journeyScenarios.length;

    const tested = journeyScenarios.filter((s) =>
      allResults.some((r) => r.scenarioId === s.id)
    );

    const testedScenarios = tested.length;
    const passedScenarios = tested.filter((s) =>
      allResults.some((r) => r.scenarioId === s.id && r.status === "passed")
    ).length;
    const failedScenarios = tested.filter((s) =>
      allResults.some((r) => r.scenarioId === s.id && r.status === "failed")
    ).length;
    const skippedScenarios = tested.filter((s) =>
      allResults.some((r) => r.scenarioId === s.id && r.status === "skipped")
    ).length;
    const flakyScenarios = tested.filter((s) =>
      allResults.some((r) => r.scenarioId === s.id && r.status === "flaky")
    ).length;

    const coveragePercent =
      totalScenarios > 0 ? (testedScenarios / totalScenarios) * 100 : 0;
    const passRate =
      testedScenarios > 0 ? (passedScenarios / testedScenarios) * 100 : 0;

    return {
      journeyId: journey.id,
      journeyName: journey.name,
      totalScenarios,
      testedScenarios,
      passedScenarios,
      failedScenarios,
      skippedScenarios,
      flakyScenarios,
      coveragePercent,
      passRate,
      scenarios: journeyScenarios.map((s) => {
        const result = allResults.find((r) => r.scenarioId === s.id);
        return {
          scenarioId: s.id,
          status: result ? result.status : ("untested" as const),
        };
      }),
    };
  });

  const unmappedTests = allResults
    .filter((r) => !r.scenarioId)
    .map((r) => ({
      testName: r.testId,
      file: r.file,
      status: r.status,
    }));

  return {
    generated: new Date().toISOString(),
    summary: {
      totalTests,
      passed,
      failed,
      skipped,
      flaky,
      duration,
      passRate,
    },
    journeyCoverage,
    scenarioResults,
    unmappedTests,
    sources: {
      playwright: fs.existsSync(PLAYWRIGHT_REPORT),
      vitest: (await glob(VITEST_REPORT_GLOB, { cwd: ROOT })).length > 0,
      manifest: fs.existsSync(MANIFEST_PATH),
    },
  };
}

function writeJson(report: CoverageReport): void {
  const outPath = path.join(OUT_DIR, "coverage.json");
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(report, null, 2) + "\n");
  console.log(`Wrote ${outPath}`);
}

function writeMarkdown(report: CoverageReport): void {
  const outPath = path.join(OUT_DIR, "coverage.md");
  const date = new Date(report.generated).toISOString().slice(0, 10);
  const { summary } = report;

  const journeyRows = report.journeyCoverage
    .map(
      (j) =>
        `| ${j.journeyId} | ${j.journeyName} | ${j.totalScenarios} | ${j.testedScenarios} | ${j.passedScenarios} | ${j.failedScenarios} | ${j.skippedScenarios} | ${j.flakyScenarios} | ${j.coveragePercent.toFixed(1)}% | ${j.passRate.toFixed(1)}% |`
    )
    .join("\n");

  const sourceLines = [
    `- Playwright: ${report.sources.playwright ? "yes" : "no"}`,
    `- Vitest: ${report.sources.vitest ? "yes" : "no"}`,
    `- Manifest: ${report.sources.manifest ? "yes" : "no"}`,
  ].join("\n");

  const unmappedRows = report.unmappedTests
    .map((t) => `| ${t.testName} | ${t.file} | ${t.status} |`)
    .join("\n");

  const md = `# Coverage Report

Generated: ${date}

## Summary

| Metric | Value |
|---|---|
| Total tests | ${summary.totalTests} |
| Passed | ${summary.passed} |
| Failed | ${summary.failed} |
| Skipped | ${summary.skipped} |
| Flaky | ${summary.flaky} |
| Duration | ${(summary.duration / 1000).toFixed(1)}s |
| Pass rate | ${summary.passRate.toFixed(1)}% |

## Journey Coverage

| Journey | Name | Total | Tested | Passed | Failed | Skipped | Flaky | Coverage | Pass Rate |
|---|---|---|---|---|---|---|---|---|---|
${journeyRows || "| _No journeys_ | | | | | | | | | |"}

## Sources

${sourceLines}

## Unmapped Tests

Tests without a scenario ID in their name:

| Test | File | Status |
|---|---|---|
${unmappedRows || "| _All tests mapped_ | | |"}
`;

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, md);
  console.log(`Wrote ${outPath}`);
}

async function main(): Promise<void> {
  console.log("Building coverage report...");
  const report = await buildCoverageReport();
  writeJson(report);
  writeMarkdown(report);
  console.log(
    `Summary: ${report.summary.passed}/${report.summary.totalTests} passed (${report.summary.passRate.toFixed(1)}%)`
  );
}

main().catch((err: unknown) => {
  console.error(err);
  process.exit(1);
});
