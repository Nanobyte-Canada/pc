import fs from "node:fs";
import path from "node:path";
import { execSync } from "node:child_process";

const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const COVERAGE_JSON = path.join(ROOT, "docs", "testing", "coverage.json");
const RELIABILITY_JSON = path.join(ROOT, "docs", "testing", "reliability.json");
const DASHBOARD_PATH = path.join(ROOT, "docs", "testing", "dashboard.md");

interface HistoryEntry {
  timestamp: string;
  commit: string;
  branch: string;
  coverage: {
    totalTests: number;
    passed: number;
    failed: number;
    skipped: number;
    passRate: number;
  };
  reliability?: {
    flakyRate: number;
    rerunPassRate: number;
  };
}

interface HistoryFile {
  entries: HistoryEntry[];
}

function parseArgs(): { branch: string } {
  const args = process.argv.slice(2);
  let branch = "test-reports";

  for (let i = 0; i < args.length; i++) {
    if (args[i] === "--branch" && args[i + 1]) {
      branch = args[i + 1];
      i++;
    }
  }

  return { branch };
}

function readCoverageData(): HistoryEntry["coverage"] | null {
  if (!fs.existsSync(COVERAGE_JSON)) {
    console.warn("coverage.json not found; skipping coverage data.");
    return null;
  }

  const content = fs.readFileSync(COVERAGE_JSON, "utf-8");
  const data = JSON.parse(content);

  return {
    totalTests: data.summary?.totalTests ?? 0,
    passed: data.summary?.passed ?? 0,
    failed: data.summary?.failed ?? 0,
    skipped: data.summary?.skipped ?? 0,
    passRate: data.summary?.passRate ?? 0,
  };
}

function readReliabilityData(): HistoryEntry["reliability"] | null {
  if (!fs.existsSync(RELIABILITY_JSON)) {
    console.warn("reliability.json not found; skipping reliability data.");
    return null;
  }

  const content = fs.readFileSync(RELIABILITY_JSON, "utf-8");
  const data = JSON.parse(content);

  return {
    flakyRate: data.flakyRate ?? 0,
    rerunPassRate: data.rerunPassRate ?? 0,
  };
}

function getCurrentCommit(): string {
  try {
    return execSync("git rev-parse --short HEAD", { cwd: ROOT })
      .toString()
      .trim();
  } catch {
    return "unknown";
  }
}

function getCurrentBranch(): string {
  try {
    return execSync("git branch --show-current", { cwd: ROOT })
      .toString()
      .trim();
  } catch {
    return "unknown";
  }
}

function historyFilePath(): string {
  return path.join(ROOT, "docs", "testing", "history.json");
}

function readExistingHistory(): HistoryFile {
  const filePath = historyFilePath();
  if (!fs.existsSync(filePath)) {
    return { entries: [] };
  }

  const content = fs.readFileSync(filePath, "utf-8");
  return JSON.parse(content);
}

function writeHistory(history: HistoryFile): void {
  const filePath = historyFilePath();
  fs.mkdirSync(path.dirname(filePath), { recursive: true });
  fs.writeFileSync(filePath, JSON.stringify(history, null, 2) + "\n");
  console.log(`Wrote ${filePath} (${history.entries.length} entries)`);
}

function generateDashboard(history: HistoryFile): void {
  const lines = [
    "# Test Dashboard",
    "",
    `Generated: ${new Date().toISOString().slice(0, 10)}`,
    "",
    "## Coverage History",
    "",
    "| Timestamp | Commit | Branch | Tests | Passed | Failed | Pass Rate | Flaky Rate |",
    "|---|---|---|---|---|---|---|---|",
  ];

  const recentEntries = history.entries.slice(-30);
  for (const entry of recentEntries) {
    const ts = entry.timestamp.slice(0, 16).replace("T", " ");
    const flakyRate = entry.reliability
      ? `${entry.reliability.flakyRate.toFixed(1)}%`
      : "N/A";
    lines.push(
      `| ${ts} | ${entry.commit} | ${entry.branch} | ${entry.coverage.totalTests} | ${entry.coverage.passed} | ${entry.coverage.failed} | ${entry.coverage.passRate.toFixed(1)}% | ${flakyRate} |`
    );
  }

  if (recentEntries.length === 0) {
    lines.push("| _No data yet_ | | | | | | | |");
  }

  lines.push("");

  fs.mkdirSync(path.dirname(DASHBOARD_PATH), { recursive: true });
  fs.writeFileSync(DASHBOARD_PATH, lines.join("\n"));
  console.log(`Wrote ${DASHBOARD_PATH}`);
}

function switchToBranch(branch: string): boolean {
  try {
    execSync(`git checkout ${branch}`, { cwd: ROOT, stdio: "pipe" });
    return true;
  } catch {
    return false;
  }
}

function createBranch(branch: string): void {
  execSync(`git checkout -b ${branch}`, { cwd: ROOT, stdio: "pipe" });
}

function commitAndSwitchBack(
  branch: string,
  sourceBranch: string,
  commitMessage: string
): void {
  execSync("git add docs/testing/history.json docs/testing/dashboard.md", {
    cwd: ROOT,
    stdio: "pipe",
  });

  try {
    execSync(`git commit -m "${commitMessage}"`, {
      cwd: ROOT,
      stdio: "pipe",
    });
    console.log(`Committed to ${branch}`);
  } catch {
    console.log("No changes to commit on history branch.");
  }

  execSync(`git checkout ${sourceBranch}`, { cwd: ROOT, stdio: "pipe" });
}

async function main(): Promise<void> {
  const { branch: targetBranch } = parseArgs();
  const sourceBranch = getCurrentBranch();

  console.log(`Publishing history to branch: ${targetBranch}`);
  console.log(`Source branch: ${sourceBranch}`);

  const coverage = readCoverageData();
  const reliability = readReliabilityData();

  if (!coverage) {
    console.error("No coverage data available. Run build-coverage-report first.");
    process.exit(1);
  }

  const entry: HistoryEntry = {
    timestamp: new Date().toISOString(),
    commit: getCurrentCommit(),
    branch: sourceBranch,
    coverage,
    ...(reliability ? { reliability } : {}),
  };

  const history = readExistingHistory();
  history.entries.push(entry);

  const switched = switchToBranch(targetBranch);
  if (!switched) {
    createBranch(targetBranch);
  }

  writeHistory(history);
  generateDashboard(history);

  const commitMessage = `chore: update test history ${entry.timestamp.slice(0, 10)}`;
  commitAndSwitchBack(targetBranch, sourceBranch, commitMessage);

  console.log("History published successfully.");
}

main().catch((err: unknown) => {
  console.error(err);
  process.exit(1);
});
