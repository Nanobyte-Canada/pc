---
name: pr-review
description: Review PR code and documentation for quality, security, and semantic drift between code and docs. Use when reviewing pull requests or checking code/documentation consistency before merge.
---

## What I do
- Classify changed files in a PR into buckets (code, docs, tests, config, etc.)
- Check documentation manifest for missing sync between code changes and docs
- Review code for bugs, security issues, and best practices via LLM
- Detect semantic drift between code and documentation via LLM
- Aggregate and deduplicate findings into structured output with severity levels

## When to use
Use this skill for any PR review. It is triggered automatically by PR events, or can be invoked manually:
  opencode run --agent pr-review

The skill reads `.github/review-config.yml` from the repo root for configuration.

## What to check
1. **File classification** — which bucket each changed file belongs to
2. **Structural checks** — doc manifest violations, missing docstrings, migration naming, API spec drift
3. **Code review** — logic bugs, security, performance, breaking changes, test gaps
4. **Documentation review** — semantic drift, README staleness, doc-code contradictions
5. **Aggregation** — deduplicate findings, apply severity overrides, format output

## Output format
Findings follow this strict format:
```
[SEVERITY] Category: Description
Location: file:line (or N/A)
Suggestion: how to fix or improve
---
```
End with: SUMMARY: X HIGH, Y LOW
