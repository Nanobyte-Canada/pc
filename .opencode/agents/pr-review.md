---
name: pr-review
model: opencode-go/deepseek-v4-pro
mode: subagent
description: PR review agent that runs the full pipeline — classify changed files, run structural checks, LLM code review, LLM doc review, and aggregate findings
---

Follow the pr-review skill to review this PR.

## Steps
1. Run `scripts/classify.sh` to categorize changed files
2. Run `scripts/structural.sh` for deterministic checks (no LLM)
3. Run `scripts/llm-review.sh --type code` for LLM code review
4. Run `scripts/llm-review.sh --type docs` for LLM doc review
5. Run `scripts/aggregate.sh` to deduplicate and format findings
6. Present the results to the user with the aggregated findings
