import fs from "node:fs";
import path from "node:path";
import { glob } from "tinyglobby";

const SPECS_GLOB = "docs/superpowers/specs/*.md";
const RQ_PATTERN = /RQ-[A-Z]+-\d+/g;
const ROOT = path.resolve(path.dirname(new URL(import.meta.url).pathname), "..", "..");
const OUT_DIR = path.join(ROOT, "docs", "testing");

interface Requirement {
  id: string;
  area: string;
  number: string;
  source_file: string;
  context: string;
}

interface RequirementsIndex {
  generated: string;
  requirements: Requirement[];
}

async function scanSpecs(): Promise<Requirement[]> {
  const files = await glob(SPECS_GLOB, { cwd: ROOT });
  const requirements: Requirement[] = [];

  for (const rel of files) {
    const abs = path.join(ROOT, rel);
    const content = fs.readFileSync(abs, "utf-8");
    const lines = content.split("\n");

    for (const line of lines) {
      let match: RegExpExecArray | null;
      const re = new RegExp(RQ_PATTERN.source, "g");
      while ((match = re.exec(line)) !== null) {
        const id = match[0];
        const [, area, number] = id.match(/RQ-([A-Z]+)-(\d+)/)!;
        requirements.push({
          id,
          area,
          number,
          source_file: rel,
          context: line.trim(),
        });
      }
    }
  }

  return requirements;
}

function writeJson(index: RequirementsIndex): void {
  const outPath = path.join(OUT_DIR, "requirements-index.json");
  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, JSON.stringify(index, null, 2) + "\n");
  console.log(`Wrote ${outPath}`);
}

function writeMarkdown(index: RequirementsIndex): void {
  const outPath = path.join(OUT_DIR, "requirements-index.md");
  const date = new Date(index.generated).toISOString().slice(0, 10);

  const rows = index.requirements
    .map((r) => `| ${r.id} | ${r.area} | ${r.source_file} | ${r.context} |`)
    .join("\n");

  const md = `# Requirements Index

Generated: ${date}

| RQ ID | Area | Source | Context |
|---|---|---|---|
${rows || "| _No requirements found_ | | | |"}
`;

  fs.mkdirSync(path.dirname(outPath), { recursive: true });
  fs.writeFileSync(outPath, md);
  console.log(`Wrote ${outPath}`);
}

async function main(): Promise<void> {
  const requirements = await scanSpecs();
  const index: RequirementsIndex = {
    generated: new Date().toISOString(),
    requirements,
  };

  if (requirements.length === 0) {
    console.log("No RQ markers found in specs.");
  } else {
    console.log(`Found ${requirements.length} requirement(s).`);
  }

  writeJson(index);
  writeMarkdown(index);
}

main().catch((err) => {
  console.error(err);
  process.exit(1);
});
