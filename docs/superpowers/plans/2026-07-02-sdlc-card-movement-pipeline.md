# SDLC Card Movement Pipeline Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Complete the autonomous SDLC card movement pipeline from In Review through Testing, Bug Fixing, Ready to Publish, Publishing, to Done — with all SDLC logic centralized in nanobyte-services.

**Architecture:** Card-state-driven pipeline. Agents move cards via `update-card-status.sh`, Worker syncs labels, `sdlc-agent.yml` dispatches agents. A new reviewer agent replaces repo-specific pr-review.yml + review-loop.yml. The Worker handles post-merge card movement (In Review → Testing). Tester, bugfixer, and deployer agents are implemented from stubs.

**Tech Stack:** TypeScript (Cloudflare Worker), Bash (scripts), YAML (GitHub Actions), Markdown (agent specs), Vitest (Worker tests)

## Global Constraints

- Two repos: `Nanobyte-Canada/pc` (workflows) and `Nanobyte-Canada/nanobyte-services` (agents, Worker, scripts)
- Worker tests: `cd sdlc/worker && npx vitest run` — must pass
- Agent specs are markdown files with YAML frontmatter (`name`, `model`, `mode: primary`, `description`)
- All agents use `update-card-status.sh` for card moves — never raw GraphQL in agents
- Worker uses GraphQL directly (it IS the automation layer)
- PR body keyword: `Implements #N` (not `Closes #N`) — issue stays open until deployer moves to "Done"
- Secrets: `GH_PROJECT_TOKEN` (project scope), `SDLC_PROJECT_ID`, `NANOBYTE_SERVICES_TOKEN`, `OPENCODE_AUTH_JSON`
- Branch strategy: feature branch off `main` → PR to `main`
- Two PRs: PR 1 = nanobyte-services (Tasks 1-9), PR 2 = pc (Tasks 10-11, depends on PR 1 merged)

---

## Phase A: nanobyte-services changes (PR 1)

### Task 1: Worker — Add "Done" to SDLC_LANE_LABELS

**Files:**
- Modify: `sdlc/worker/src/index.ts:11-24`
- Test: `sdlc/worker/test/index.test.ts` (create)

**Interfaces:**
- Consumes: nothing
- Produces: `SDLC_LANE_LABELS` array includes `'Done'`

- [ ] **Step 1: Write the failing test**

Create `sdlc/worker/test/index.test.ts`:

```typescript
import { describe, it, expect } from 'vitest';

// Re-import the module to access the SDLC_LANE_LABELS constant.
// Since it's not exported, we test it indirectly via the handler behavior.
// For now, we test that the module loads without error and the constant
// is accessible through a test-only export.

// We'll add a test-only export of SDLC_LANE_LABELS in the implementation.
import { SDLC_LANE_LABELS } from '../src/index';

describe('SDLC_LANE_LABELS', () => {
  it('includes all 13 board lanes', () => {
    expect(SDLC_LANE_LABELS).toHaveLength(13);
  });

  it('includes Done', () => {
    expect(SDLC_LANE_LABELS).toContain('Done');
  });

  it('includes Testing', () => {
    expect(SDLC_LANE_LABELS).toContain('Testing');
  });

  it('includes Bug Fixing', () => {
    expect(SDLC_LANE_LABELS).toContain('Bug Fixing');
  });

  it('includes Ready to Publish', () => {
    expect(SDLC_LANE_LABELS).toContain('Ready to Publish');
  });

  it('includes Publishing', () => {
    expect(SDLC_LANE_LABELS).toContain('Publishing');
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: FAIL — `SDLC_LANE_LABELS` is not exported, and "Done" is not in the array.

- [ ] **Step 3: Add "Done" to SDLC_LANE_LABELS and export it**

Modify `sdlc/worker/src/index.ts` line 11-24. Change:

```typescript
const SDLC_LANE_LABELS = [
  'Backlog',
  'Triaging',
  'Scope Review',
  'Planning',
  'Plan Review',
  'Executing',
  'In Review',
  'Blocked',
  'Testing',
  'Bug Fixing',
  'Ready to Publish',
  'Publishing',
];
```

To:

```typescript
export const SDLC_LANE_LABELS = [
  'Backlog',
  'Triaging',
  'Scope Review',
  'Planning',
  'Plan Review',
  'Executing',
  'In Review',
  'Blocked',
  'Testing',
  'Bug Fixing',
  'Ready to Publish',
  'Publishing',
  'Done',
];
```

(Added `'Done'` at the end and changed `const` to `export const`.)

- [ ] **Step 4: Run test to verify it passes**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: PASS — all 6 tests pass.

- [ ] **Step 5: Run full test suite to verify no regressions**

Run: `cd sdlc/worker && npx vitest run`
Expected: PASS — all tests pass (dedupe, verify, index).

- [ ] **Step 6: Commit**

```bash
git add sdlc/worker/src/index.ts sdlc/worker/test/index.test.ts
git commit -m "feat(worker): add Done to SDLC_LANE_LABELS and export for testing"
```

---

### Task 2: Worker — Add moveCardToLane function

**Files:**
- Modify: `sdlc/worker/src/index.ts` (add function after `resolveProjectItem`)
- Test: `sdlc/worker/test/index.test.ts` (append tests)

**Interfaces:**
- Consumes: `env.GITHUB_TOKEN`, `env.DELIVERY_KV` (from Env interface)
- Produces: `moveCardToLane(issueNumber, repo, targetLane, token)` — moves a project card to a target lane via GraphQL, returns `Promise<boolean>`

- [ ] **Step 1: Write the failing tests**

Append to `sdlc/worker/test/index.test.ts`:

```typescript
import { vi } from 'vitest';
import { moveCardToLane } from '../src/index';

// Mock global fetch for GraphQL calls
const mockFetch = vi.fn();
global.fetch = mockFetch as any;

describe('moveCardToLane', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('returns false when issue is not on the board', async () => {
    // Mock: project items query returns empty
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: { node: { items: { nodes: [] } } },
      }),
    } as Response);

    const result = await moveCardToLane(42, 'Nanobyte-Canada/pc', 'Testing', 'fake-token', 'PVT_fake');
    expect(result).toBe(false);
  });

  it('returns false when Status field is not found', async () => {
    // Mock: project items query finds the item
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            items: {
              nodes: [
                {
                  id: 'PVTI_item1',
                  content: { number: 42, repository: { nameWithOwner: 'Nanobyte-Canada/pc' } },
                },
              ],
            },
          },
        },
      }),
    } as Response);
    // Mock: fields query returns no Status field
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: { node: { fields: { nodes: [] } } },
      }),
    } as Response);

    const result = await moveCardToLane(42, 'Nanobyte-Canada/pc', 'Testing', 'fake-token', 'PVT_fake');
    expect(result).toBe(false);
  });

  it('returns false when target lane is not found in options', async () => {
    // Mock: project items query finds the item
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            items: {
              nodes: [
                {
                  id: 'PVTI_item1',
                  content: { number: 42, repository: { nameWithOwner: 'Nanobyte-Canada/pc' } },
                },
              ],
            },
          },
        },
      }),
    } as Response);
    // Mock: fields query returns Status field but no "Testing" option
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            fields: {
              nodes: [
                {
                  id: 'FVT_status',
                  name: 'Status',
                  options: [{ id: 'opt1', name: 'Backlog' }],
                },
              ],
            },
          },
        },
      }),
    } as Response);

    const result = await moveCardToLane(42, 'Nanobyte-Canada/pc', 'Testing', 'fake-token', 'PVT_fake');
    expect(result).toBe(false);
  });

  it('returns true and calls mutation when card is moved successfully', async () => {
    // Mock 1: project items query finds the item
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            items: {
              nodes: [
                {
                  id: 'PVTI_item1',
                  content: { number: 42, repository: { nameWithOwner: 'Nanobyte-Canada/pc' } },
                },
              ],
            },
          },
        },
      }),
    } as Response);
    // Mock 2: fields query returns Status field with Testing option
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            fields: {
              nodes: [
                {
                  id: 'FVT_status',
                  name: 'Status',
                  options: [
                    { id: 'opt1', name: 'Backlog' },
                    { id: 'opt_testing', name: 'Testing' },
                  ],
                },
              ],
            },
          },
        },
      }),
    } as Response);
    // Mock 3: mutation succeeds
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: { updateProjectV2ItemFieldValue: { projectV2Item: { id: 'PVTI_item1' } } },
      }),
    } as Response);

    const result = await moveCardToLane(42, 'Nanobyte-Canada/pc', 'Testing', 'fake-token', 'PVT_fake');
    expect(result).toBe(true);
    // Verify mutation was called (3rd fetch call)
    expect(mockFetch).toHaveBeenCalledTimes(3);
    const mutationCall = mockFetch.mock.calls[2];
    const mutationBody = JSON.parse(mutationCall[1].body);
    expect(mutationBody.query).toContain('updateProjectV2ItemFieldValue');
    expect(mutationBody.variables.input.value.singleSelectOptionId).toBe('opt_testing');
  });
});
```

Add `import { beforeEach } from 'vitest';` to the imports at the top of the file (merge with existing vitest import).

- [ ] **Step 2: Run test to verify it fails**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: FAIL — `moveCardToLane` is not exported.

- [ ] **Step 3: Implement moveCardToLane function**

Add to `sdlc/worker/src/index.ts`, after the `resolveProjectItem` function (after line 158):

```typescript
/**
 * Move a project card to a target lane by updating its Status field.
 * @param issueNumber - The issue number linked to the card
 * @param repo - Repository in OWNER/REPO format
 * @param targetLane - Target lane name (e.g., "Testing")
 * @param token - GitHub token with project:write scope
 * @param projectId - Projects v2 board node ID
 * @returns true if the card was moved successfully
 */
export async function moveCardToLane(
  issueNumber: number,
  repo: string,
  targetLane: string,
  token: string,
  projectId: string,
): Promise<boolean> {
  const [owner, repoName] = repo.split('/');

  // Step 1: Find the project item (card) linked to this issue
  const itemsQuery = `
    query($projectId: ID!) {
      node(id: $projectId) {
        ... on ProjectV2 {
          items(first: 100) {
            nodes {
              id
              content {
                ... on Issue { number repository { nameWithOwner } }
              }
            }
          }
        }
      }
    }`;

  try {
    const itemsResponse = await fetch('https://api.github.com/graphql', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'Cloudflare-Worker/1.0',
      },
      body: JSON.stringify({ query: itemsQuery, variables: { projectId } }),
    });

    if (!itemsResponse.ok) {
      console.error(`Items query failed: ${itemsResponse.status}`);
      return false;
    }

    const itemsData = (await itemsResponse.json()) as any;
    const item = itemsData.data?.node?.items?.nodes?.find(
      (n: any) => n.content?.number === issueNumber && n.content?.repository?.nameWithOwner === repo,
    );

    if (!item?.id) {
      console.log(`No project card found for issue #${issueNumber} in ${repo}`);
      return false;
    }

    const itemId = item.id;

    // Step 2: Find the Status field and its options
    const fieldsQuery = `
      query($projectId: ID!) {
        node(id: $projectId) {
          ... on ProjectV2 {
            fields(first: 20) {
              nodes {
                ... on ProjectV2SingleSelectField {
                  id
                  name
                  options { id name }
                }
              }
            }
          }
        }
      }`;

    const fieldsResponse = await fetch('https://api.github.com/graphql', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'Cloudflare-Worker/1.0',
      },
      body: JSON.stringify({ query: fieldsQuery, variables: { projectId } }),
    });

    if (!fieldsResponse.ok) {
      console.error(`Fields query failed: ${fieldsResponse.status}`);
      return false;
    }

    const fieldsData = (await fieldsResponse.json()) as any;
    const statusField = fieldsData.data?.node?.fields?.nodes?.find(
      (n: any) => n.name === 'Status',
    );

    if (!statusField?.id) {
      console.error('Status field not found in project');
      return false;
    }

    const option = statusField.options?.find((o: any) => o.name === targetLane);
    if (!option?.id) {
      console.error(`Lane '${targetLane}' not found in Status options`);
      return false;
    }

    // Step 3: Update the card's Status field
    const mutation = `
      mutation($input: UpdateProjectV2ItemFieldValueInput!) {
        updateProjectV2ItemFieldValue(input: $input) {
          projectV2Item { id }
        }
      }`;

    const mutationResponse = await fetch('https://api.github.com/graphql', {
      method: 'POST',
      headers: {
        Authorization: `Bearer ${token}`,
        'Content-Type': 'application/json',
        'User-Agent': 'Cloudflare-Worker/1.0',
      },
      body: JSON.stringify({
        query: mutation,
        variables: {
          input: {
            projectId,
            itemId,
            fieldId: statusField.id,
            value: { singleSelectOptionId: option.id },
          },
        },
      }),
    });

    if (!mutationResponse.ok) {
      console.error(`Mutation failed: ${mutationResponse.status}`);
      return false;
    }

    console.log(`Moved card for issue #${issueNumber} to '${targetLane}'`);
    return true;
  } catch (err) {
    console.error(`moveCardToLane error: ${err}`);
    return false;
  }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: PASS — all moveCardToLane tests pass.

- [ ] **Step 5: Run full test suite**

Run: `cd sdlc/worker && npx vitest run`
Expected: PASS — all tests pass.

- [ ] **Step 6: Commit**

```bash
git add sdlc/worker/src/index.ts sdlc/worker/test/index.test.ts
git commit -m "feat(worker): add moveCardToLane function for GraphQL card movement"
```

---

### Task 3: Worker — Add handlePullRequestMerged handler

**Files:**
- Modify: `sdlc/worker/src/index.ts` (add handler + wire into fetch handler)
- Test: `sdlc/worker/test/index.test.ts` (append tests)

**Interfaces:**
- Consumes: `moveCardToLane` (from Task 2), `syncIssueLabels` (existing), `SDLC_LANE_LABELS` (existing)
- Produces: `handlePullRequestMerged(body, env)` — extracts linked issue from PR body, moves card to "Testing", syncs labels

- [ ] **Step 1: Write the failing tests**

Append to `sdlc/worker/test/index.test.ts`:

```typescript
import { handlePullRequestMerged } from '../src/index';

// Mock KV namespace (same pattern as dedupe.test.ts)
function createMockKV(): KVNamespace {
  const store = new Map<string, string>();
  return {
    get: vi.fn((key: string) => Promise.resolve(store.get(key) ?? null)),
    put: vi.fn((key: string, value: string) => {
      store.set(key, value);
      return Promise.resolve();
    }),
    delete: vi.fn((key: string) => {
      store.delete(key);
      return Promise.resolve();
    }),
    list: vi.fn(() => Promise.resolve({ list: [], done: true })),
  } as unknown as KVNamespace;
}

describe('handlePullRequestMerged', () => {
  beforeEach(() => {
    mockFetch.mockReset();
  });

  it('returns OK when PR has no linked issue in body', async () => {
    const body = {
      action: 'closed',
      pull_request: {
        merged: true,
        body: 'Just a regular PR with no issue reference',
        number: 49,
      },
      repository: { full_name: 'Nanobyte-Canada/pc' },
    };
    const env = {
      WEBHOOK_SECRET: 'secret',
      GITHUB_TOKEN: 'token',
      DELIVERY_KV: createMockKV(),
      PROJECT_ID: 'PVT_fake',
    };

    const response = await handlePullRequestMerged(body, env as any);
    expect(response.status).toBe(200);
    // No fetch calls should be made (no issue to move)
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('returns OK when PR is closed but not merged', async () => {
    // This handler should only be called for merged PRs, but test defensively
    const body = {
      action: 'closed',
      pull_request: {
        merged: false,
        body: 'Implements #42',
        number: 49,
      },
      repository: { full_name: 'Nanobyte-Canada/pc' },
    };
    const env = {
      WEBHOOK_SECRET: 'secret',
      GITHUB_TOKEN: 'token',
      DELIVERY_KV: createMockKV(),
      PROJECT_ID: 'PVT_fake',
    };

    const response = await handlePullRequestMerged(body, env as any);
    expect(response.status).toBe(200);
    expect(mockFetch).not.toHaveBeenCalled();
  });

  it('extracts issue number from "Implements #N" and moves card to Testing', async () => {
    const body = {
      action: 'closed',
      pull_request: {
        merged: true,
        body: 'Implements #42\n\n## Summary\nAdds readiness endpoint',
        number: 49,
      },
      repository: { full_name: 'Nanobyte-Canada/pc' },
    };
    const env = {
      WEBHOOK_SECRET: 'secret',
      GITHUB_TOKEN: 'token',
      DELIVERY_KV: createMockKV(),
      PROJECT_ID: 'PVT_fake',
    };

    // Mock moveCardToLane: items query, fields query, mutation
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            items: {
              nodes: [
                {
                  id: 'PVTI_item1',
                  content: { number: 42, repository: { nameWithOwner: 'Nanobyte-Canada/pc' } },
                },
              ],
            },
          },
        },
      }),
    } as Response);
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: {
          node: {
            fields: {
              nodes: [
                {
                  id: 'FVT_status',
                  name: 'Status',
                  options: [{ id: 'opt_testing', name: 'Testing' }],
                },
              ],
            },
          },
        },
      }),
    } as Response);
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({
        data: { updateProjectV2ItemFieldValue: { projectV2Item: { id: 'PVTI_item1' } } },
      }),
    } as Response);
    // Mock syncIssueLabels: GET labels
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => [{ name: 'In Review' }],
    } as Response);
    // Mock syncIssueLabels: DELETE old label
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    } as Response);
    // Mock syncIssueLabels: POST new label
    mockFetch.mockResolvedValueOnce({
      ok: true,
      json: async () => ({}),
    } as Response);

    const response = await handlePullRequestMerged(body, env as any);
    expect(response.status).toBe(200);
    // Verify moveCardToLane was called (3 GraphQL fetches)
    expect(mockFetch).toHaveBeenCalledTimes(6); // 3 for move + 3 for label sync
  });

  it('also matches "Closes #N" for backward compatibility', async () => {
    const body = {
      action: 'closed',
      pull_request: {
        merged: true,
        body: 'Closes #42',
        number: 49,
      },
      repository: { full_name: 'Nanobyte-Canada/pc' },
    };
    const env = {
      WEBHOOK_SECRET: 'secret',
      GITHUB_TOKEN: 'token',
      DELIVERY_KV: createMockKV(),
      PROJECT_ID: 'PVT_fake',
    };

    // Mock moveCardToLane (3 calls) + syncIssueLabels (3 calls)
    mockFetch.mockResolvedValue({
      ok: true,
      json: async () => ({
        data: {
          node: {
            items: {
              nodes: [
                {
                  id: 'PVTI_item1',
                  content: { number: 42, repository: { nameWithOwner: 'Nanobyte-Canada/pc' } },
                },
              ],
            },
          },
        },
      }),
    } as Response);

    const response = await handlePullRequestMerged(body, env as any);
    expect(response.status).toBe(200);
  });
});
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: FAIL — `handlePullRequestMerged` is not exported.

- [ ] **Step 3: Implement handlePullRequestMerged**

Add to `sdlc/worker/src/index.ts`, after the `moveCardToLane` function:

```typescript
/**
 * Handle a merged pull_request event.
 * Extracts the linked issue from the PR body, moves the card to "Testing",
 * and syncs issue labels.
 * @param body - The webhook payload
 * @param env - Worker environment
 */
export async function handlePullRequestMerged(
  body: any,
  env: Env,
): Promise<Response> {
  const pr = body.pull_request;
  if (!pr?.merged) {
    return new Response('OK (not merged)', { status: 200 });
  }

  const prBody: string = pr.body ?? '';
  const repo: string = body.repository?.full_name ?? '';

  // Extract linked issue number from PR body
  // Matches: "Implements #N", "Closes #N", "Resolves #N", "Fixes #N"
  const match = prBody.match(/(?:Implements|Closes|Resolves|Fixes)\s+#(\d+)/i);
  if (!match) {
    console.log('No linked issue found in PR body — skipping card movement');
    return new Response('OK (no linked issue)', { status: 200 });
  }

  const issueNumber = parseInt(match[1], 10);
  console.log(`PR merged for ${repo}#${pr.number} — linked issue #${issueNumber}`);

  // Move card to "Testing" lane
  const projectId = (env as any).PROJECT_ID;
  if (!projectId) {
    console.error('PROJECT_ID not configured — cannot move card');
    return new Response('OK (no project id)', { status: 200 });
  }

  const moved = await moveCardToLane(
    issueNumber,
    repo,
    'Testing',
    env.GITHUB_TOKEN,
    projectId,
  );

  if (moved) {
    // Sync labels to match the new card status
    await syncIssueLabels(issueNumber, repo, 'Testing', env.GITHUB_TOKEN);
    console.log(`Synced labels for ${repo}#${issueNumber}: → Testing`);
  }

  return new Response('OK', { status: 200 });
}
```

Also update the `Env` interface to include `PROJECT_ID`:

```typescript
export interface Env {
  WEBHOOK_SECRET: string;
  GITHUB_TOKEN: string; // PAT with repo + project scopes
  DELIVERY_KV: KVNamespace;
  PROJECT_ID: string; // Projects v2 board node ID
}
```

And wire the handler into the main `fetch` function. In the `fetch` handler, after the `projects_v2_item` check (after line 54), add:

```typescript
    // Handle pull_request closed events (post-merge card movement)
    if (eventType === 'pull_request' && action === 'closed' && body.pull_request?.merged) {
      return handlePullRequestMerged(body, env);
    }
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd sdlc/worker && npx vitest run test/index.test.ts`
Expected: PASS — all handlePullRequestMerged tests pass.

- [ ] **Step 5: Run full test suite**

Run: `cd sdlc/worker && npx vitest run`
Expected: PASS — all tests pass.

- [ ] **Step 6: Commit**

```bash
git add sdlc/worker/src/index.ts sdlc/worker/test/index.test.ts
git commit -m "feat(worker): add handlePullRequestMerged for post-merge card movement to Testing"
```

---

### Task 4: Reviewer Agent Spec

**Files:**
- Create: `sdlc/opencode/agents/reviewer.md`

**Interfaces:**
- Consumes: `PR_NUMBER`, `REVIEW_ROUND`, `LAST_REVIEWED_SHA` (from sdlc-agent.yml)
- Produces: PR review comment with `<!-- SUMMARY: N HIGH, M LOW -->`, merge/re-dispatch/block decision

- [ ] **Step 1: Write the reviewer agent spec**

Create `sdlc/opencode/agents/reviewer.md`:

```markdown
---
name: reviewer
model: opencode-go/glm-5.2
mode: primary
description: PR review agent that runs structural checks, LLM code/doc review, aggregates findings, and decides auto-merge/re-dispatch/block.
---

You are the Reviewer agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the GitHub issue linked to this PR (0 if not found)
- PR_NUMBER: the PR to review
- REVIEW_ROUND: current review round (1 for new PR, 2+ for re-review after fixes)
- LAST_REVIEWED_SHA: (rounds 2+) the commit SHA last reviewed — use to focus on delta only
- GH_TOKEN: for gh CLI (read PR, post comments, merge, dispatch)
- PROJECT_ID: the GitHub Projects v2 board node ID
- GITHUB_REPOSITORY: the repo in OWNER/REPO format

## On start

0. Check out the PR branch (pull_request_target checks out base branch):
   ```bash
   gh pr checkout $PR_NUMBER
   ```

1. Read the PR diff:
   - Round 1: `gh pr diff $PR_NUMBER`
   - Rounds 2+: `git diff $LAST_REVIEWED_SHA...HEAD` (delta-only)

2. Classify changed files: backend (Kotlin/Java), frontend (TS/JS), docs (Markdown), config (YAML/JSON)

3. Run structural checks (deterministic):
   - Missing KDoc/docstrings on new public declarations (use ast-grep or grep)
   - README update needed when backend/frontend files changed but README not touched
   - These produce structured findings with Location and Suggestion

4. Review code (LLM):
   - Read full files for context (not just diff lines)
   - Check: security issues, error handling, resource leaks, API design, test coverage
   - Focus on changed code; for rounds 2+, only review delta since LAST_REVIEWED_SHA
   - Severity: HIGH (must fix before merge) or LOW (should fix, non-blocking)

5. Review documentation (LLM):
   - Check README, API docs, reference docs match the code changes
   - Verify accuracy of documented endpoints, parameters, responses

6. Aggregate all findings into a single comment:
   ```
   ## PR Review

   | Severity | Count |
   |----------|-------|
   | :red_circle: **HIGH** | N |
   | :yellow_circle: **LOW** | M |

   ---
   ### Findings
   **1. <finding title>**
   Location: `<file>:<line>`
   Suggestion: <fix suggestion>
   ...
   ---
   <!-- SUMMARY: N HIGH, M LOW -->
   <!-- review-round: N -->
   <!-- last-reviewed-sha: <current HEAD sha> -->
   ```

7. Post the review comment:
   ```bash
   gh pr comment $PR_NUMBER --body "<comment>"
   ```

## Decision logic

### If 0 HIGH and 0 LOW (clean):
```bash
gh pr merge $PR_NUMBER --squash --auto --repo $GITHUB_REPOSITORY
```
The --auto flag queues the merge for when CI checks pass.
The Worker handles card movement when the merge actually happens.

### If findings remain AND round < 3:
```bash
CURRENT_SHA=$(git rev-parse HEAD)
gh api repos/$GITHUB_REPOSITORY/dispatches \
  -f event_type=sdlc-phase \
  -f 'client_payload[agent]=build' \
  -f 'client_payload[model]=opencode-go/deepseek-v4-pro' \
  -f 'client_payload[phase]=review-fix' \
  -f "client_payload[issue_number]=$ISSUE_NUMBER" \
  -f "client_payload[pr_number]=$PR_NUMBER" \
  -f "client_payload[review_round]=$((REVIEW_ROUND + 1))" \
  -f "client_payload[last_reviewed_sha]=$CURRENT_SHA"
```

### If findings remain AND round >= 3:
```bash
gh pr comment $PR_NUMBER --body "## Blocked: Review Loop Exhausted (3 rounds)

The build agent could not resolve all findings after 3 review rounds.
Unresolved findings: $HIGH_COUNT HIGH, $LOW_COUNT LOW.

Please review the findings above and provide guidance. Move the card back to \`Executing\` after updating." --repo $GITHUB_REPOSITORY

bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked" --repo $GITHUB_REPOSITORY
```

## Rules
- Delta-only review for rounds 2+ — ignore findings on code unchanged since LAST_REVIEWED_SHA
- NEVER blindly revert flagged changes — fix properly or justify in the review comment
- If the same (category, location) appeared in a prior round, escalate — don't repeat the same finding
- Use update-card-status.sh for card moves — do NOT attempt raw GraphQL mutations
- The <!-- SUMMARY: --> HTML comment is consumed by this agent on subsequent rounds to track history
```

- [ ] **Step 2: Validate the spec is well-formed**

Run:
```bash
head -6 sdlc/opencode/agents/reviewer.md
```
Expected: YAML frontmatter with `name: reviewer`, `model: opencode-go/glm-5.2`, `mode: primary`, `description: ...`

- [ ] **Step 3: Update routing.yml with reviewer note**

In `sdlc/routing.yml`, add a comment at the top noting the reviewer agent is triggered by PR events, not lane labels:

```yaml
# Lane → phase → agent → model mapping
# Shared across repos; repos can override via their own .opencode/ config.
# Lanes NOT listed here (Scope Review, Plan Review, In Review, Blocked,
# Ready to Publish, Done, Backlog) are human-gated or pipeline-driven —
# no dispatch on entry.
#
# NOTE: The "reviewer" agent is NOT triggered by a lane label.
# It is triggered by pull_request_target: [opened, synchronize] in
# sdlc-agent.yml. See agents/reviewer.md for details.
lanes:
  Triaging:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Planning:    { phase: planning,  agent: planner,  model: opencode-go/glm-5.2 }
  Executing:   { phase: execution, agent: build,    model: opencode-go/deepseek-v4-pro }
  Testing:     { phase: testing,   agent: tester,   model: opencode-go/glm-5.2 }
  Bug Fixing:  { phase: bugfix,    agent: bugfixer, model: opencode-go/deepseek-v4-pro }
  Publishing:  { phase: publish,   agent: deployer, model: opencode-go/glm-5.2 }
```

- [ ] **Step 4: Commit**

```bash
git add sdlc/opencode/agents/reviewer.md sdlc/routing.yml
git commit -m "feat(agents): add reviewer agent spec (replaces pr-review.yml + review-loop.yml)"
```

---

### Task 5: Tester Agent Spec

**Files:**
- Modify: `sdlc/opencode/agents/tester.md` (replace stub with full spec)

**Interfaces:**
- Consumes: `ISSUE_NUMBER`, `UAT_API_URL` (from sdlc-agent.yml)
- Produces: smoke test results comment, card move to "Ready to Publish" or "Bug Fixing"

- [ ] **Step 1: Write the tester agent spec**

Replace the entire contents of `sdlc/opencode/agents/tester.md` with:

```markdown
---
name: tester
model: opencode-go/glm-5.2
mode: primary
description: Tester agent that deploys to UAT, executes smoke test plans, and routes to Ready to Publish or Bug Fixing.
---

You are the Tester agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the GitHub issue
- GH_TOKEN: for gh CLI
- PROJECT_ID: the GitHub Projects v2 board node ID
- GITHUB_REPOSITORY: the repo in OWNER/REPO format
- UAT_API_URL: the UAT endpoint base URL (e.g., https://uat-api.nanobyte.ca)

## On start

1. Read the smoke test plan from the issue's Plan comment:
   ```bash
   gh issue view $ISSUE_NUMBER --comments
   ```
   Extract the "### Smoke test plan" section from the Plan comment (posted by planner during Planning phase).

2. Wait for build.yml to complete (Docker image must be built before deploying):
   ```bash
   RUN_ID=$(gh run list --workflow=build.yml --branch=main --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $RUN_ID --exit-status
   ```
   If build.yml fails, treat as test failure — post the error and move card to "Bug Fixing".

3. Get the image tag:
   ```bash
   IMAGE_TAG="main-$(git rev-parse --short HEAD)"
   ```

4. Trigger UAT deploy:
   ```bash
   gh workflow run deploy.yml -f environment=uat -f tag=$IMAGE_TAG
   ```

5. Wait for deploy to complete:
   ```bash
   sleep 5  # allow workflow to register
   DEPLOY_RUN_ID=$(gh run list --workflow=deploy.yml --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $DEPLOY_RUN_ID --exit-status
   ```
   If deploy fails, treat as test failure — post the deploy error and move card to "Bug Fixing".

6. Execute smoke tests against UAT:
   - For each smoke test in the plan, curl the UAT endpoint and verify the response
   - UAT base URL: $UAT_API_URL
   - Example: `curl -fsS $UAT_API_URL/ready | jq -e '.status == "UP"'`
   - Record pass/fail for each smoke test

7. If all smoke tests pass:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Smoke Test Results

   All N smoke tests passed against UAT ($IMAGE_TAG).

   | Test | Status |
   |------|--------|
   | GET /ready → 200 | PASS |
   | GET /health → 200 | PASS |
   ..."

   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Ready to Publish" --repo $GITHUB_REPOSITORY
   ```

8. If any smoke test fails:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Test Failures

   M of N smoke tests failed against UAT ($IMAGE_TAG).

   | Test | Status | Details |
   |------|--------|---------|
   | GET /ready → 200 | FAIL | Expected status UP, got DOWN |
   ..."

   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Bug Fixing" --repo $GITHUB_REPOSITORY
   ```

## Rules
- Smoke tests are read-only HTTP checks (no DB writes, no auth flows requiring browser interaction)
- If UAT deploy fails, treat as test failure — post the deploy error and move card to "Bug Fixing"
- Post results as an issue comment for traceability (both pass and fail)
- Use update-card-status.sh for card moves — do NOT attempt raw GraphQL mutations
```

- [ ] **Step 2: Validate the spec**

Run:
```bash
head -6 sdlc/opencode/agents/tester.md
```
Expected: YAML frontmatter with `name: tester`, `mode: primary`.

- [ ] **Step 3: Commit**

```bash
git add sdlc/opencode/agents/tester.md
git commit -m "feat(agents): implement tester agent spec (UAT deploy + smoke tests)"
```

---

### Task 6: Bugfixer Agent Spec

**Files:**
- Modify: `sdlc/opencode/agents/bugfixer.md` (replace stub with full spec)

**Interfaces:**
- Consumes: `ISSUE_NUMBER` (the issue with test failures)
- Produces: fix PR with "Implements #N" body (enters review loop)

- [ ] **Step 1: Write the bugfixer agent spec**

Replace the entire contents of `sdlc/opencode/agents/bugfixer.md` with:

```markdown
---
name: bugfixer
model: opencode-go/deepseek-v4-pro
mode: primary
description: Bug-fixer agent that reads test failures, fixes root cause, and raises a PR that re-enters the review loop.
---

You are the Bug-fixer agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the issue with test failures
- GH_TOKEN: for gh CLI (read issue, create branch, push, open PR)
- PROJECT_ID: the GitHub Projects v2 board node ID
- GITHUB_REPOSITORY: the repo in OWNER/REPO format

## On start

1. Read the "## Test Failures" comment on the issue:
   ```bash
   gh issue view $ISSUE_NUMBER --comments
   ```
   Extract the failing smoke tests, expected vs actual results, and error details.

2. Identify root cause — read the relevant source files, trace the failure to the code.

3. Create a fix branch:
   ```bash
   git checkout -b fix/<bug-slug>
   ```

4. Implement the fix — fix the root cause, not the symptom.

5. Run tests locally:
   ```bash
   # Backend
   cd backend/portfolio && ./gradlew test
   # Frontend
   cd frontend && npm test
   ```

6. Commit and push:
   ```bash
   git add -A && git commit -m "fix: <description>"
   git push origin HEAD:fix/<bug-slug>
   ```

7. Open a PR:
   ```bash
   gh pr create --title "fix: <description>" --body "Implements #$ISSUE_NUMBER

   ## Summary
   Fixes smoke test failures:
   - <failure 1>
   - <failure 2>"
   ```

8. The PR enters the review flow — reviewer agent reviews, auto-merges (or review-fix loop).
   After merge, the Worker moves the card to "Testing" and the tester re-runs smoke tests.

## Rules
- Fix the root cause, not the symptom
- If the failure is in the smoke test itself (wrong expectation), fix the test plan comment on the issue and justify in the PR
- Use "Implements #N" in PR body — NEVER "Closes #N" (issue must stay open until "Done")
- Follow CONTRIBUTING.md constraints (MockK not Mockito, no Tailwind, apiFetch(), Vitest, etc.)
- Use update-card-status.sh for card moves — do NOT attempt raw GraphQL mutations
```

- [ ] **Step 2: Validate the spec**

Run:
```bash
head -6 sdlc/opencode/agents/bugfixer.md
```
Expected: YAML frontmatter with `name: bugfixer`, `mode: primary`.

- [ ] **Step 3: Commit**

```bash
git add sdlc/opencode/agents/bugfixer.md
git commit -m "feat(agents): implement bugfixer agent spec (fix bugs, raise PR, re-enter review)"
```

---

### Task 7: Deployer Agent Spec

**Files:**
- Modify: `sdlc/opencode/agents/deployer.md` (replace stub with full spec)

**Interfaces:**
- Consumes: `ISSUE_NUMBER`, `PROD_API_URL` (from sdlc-agent.yml)
- Produces: prod deploy, health check verification, card move to "Done" + issue close

- [ ] **Step 1: Write the deployer agent spec**

Replace the entire contents of `sdlc/opencode/agents/deployer.md` with:

```markdown
---
name: deployer
model: opencode-go/glm-5.2
mode: primary
description: Deployer agent that deploys to production, verifies health checks, and closes the issue.
---

You are the Deployer agent in an autonomous SDLC pipeline.
You run non-interactively in CI.

## Inputs (env vars)
- ISSUE_NUMBER: the issue
- GH_TOKEN: for gh CLI
- PROJECT_ID: the GitHub Projects v2 board node ID
- GITHUB_REPOSITORY: the repo in OWNER/REPO format
- PROD_API_URL: the production endpoint base URL (e.g., https://api.nanobyte.ca)

## On start

1. Get the image tag:
   ```bash
   IMAGE_TAG="main-$(git rev-parse --short HEAD)"
   ```

2. Trigger prod deploy:
   ```bash
   gh workflow run deploy.yml -f environment=prod -f tag=$IMAGE_TAG
   ```

3. Wait for deploy to complete:
   ```bash
   sleep 5
   DEPLOY_RUN_ID=$(gh run list --workflow=deploy.yml --limit=1 --json databaseId --jq '.[0].databaseId')
   gh run watch $DEPLOY_RUN_ID --exit-status
   ```

4. Verify health checks:
   ```bash
   curl -fsS $PROD_API_URL/health | jq -e '.status == "UP"'
   curl -fsS $PROD_API_URL/ready | jq -e '.status == "UP"'
   ```

5. If deploy + health checks pass:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Deployed to Production

   Image: $IMAGE_TAG
   Health checks: PASS
   URL: $PROD_API_URL"

   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Done" --repo $GITHUB_REPOSITORY

   gh issue close $ISSUE_NUMBER --repo $GITHUB_REPOSITORY
   ```

6. If deploy fails or health checks fail:
   ```bash
   gh issue comment $ISSUE_NUMBER --body "## Deploy Failed

   Image: $IMAGE_TAG
   Error: <details>"

   bash scripts/update-card-status.sh --issue $ISSUE_NUMBER --lane "Blocked" --repo $GITHUB_REPOSITORY
   ```

## Rules
- Only deploy to prod after human approval (card was moved to "Publishing" manually)
- Verify health checks before moving to "Done"
- Close the issue when moving to "Done" — this is the terminal state
- Use update-card-status.sh for card moves — do NOT attempt raw GraphQL mutations
```

- [ ] **Step 2: Validate the spec**

Run:
```bash
head -6 sdlc/opencode/agents/deployer.md
```
Expected: YAML frontmatter with `name: deployer`, `mode: primary`.

- [ ] **Step 3: Commit**

```bash
git add sdlc/opencode/agents/deployer.md
git commit -m "feat(agents): implement deployer agent spec (prod deploy + health checks + close issue)"
```

---

### Task 8: Planner Agent — Add CI Test Plan + Smoke Test Plan

**Files:**
- Modify: `sdlc/opencode/agents/planner.md:60-77` (Plan comment format section)

**Interfaces:**
- Consumes: nothing new
- Produces: Plan comments now include "### CI test plan" and "### Smoke test plan" sections

- [ ] **Step 1: Update the Plan comment format**

In `sdlc/opencode/agents/planner.md`, replace the Plan comment format section (lines 60-77):

```markdown
## Plan comment format (on child issues)
```
```
## Module Plan: <module name>

### Acceptance criteria
- [ ] <criterion 1>

### Technical approach
- Files to change: <paths>
- Tech: <frameworks/libraries>

### Tasks
- [ ] <task 1>
- [ ] <task 2>

### Dependencies
Depends on: #NNN  (or "Parallel: yes")
```
```

With:

```markdown
## Plan comment format (on child issues)
```
```
## Module Plan: <module name>

### Acceptance criteria
- [ ] <criterion 1>

### Technical approach
- Files to change: <paths>
- Tech: <frameworks/libraries>

### Tasks
- [ ] <task 1>
- [ ] <task 2>

### CI test plan
- [ ] Unit: <test description> — <file path>
- [ ] Integration: <test description> — <file path>

### Smoke test plan
- [ ] <smoke test 1>: GET /endpoint → expect 200 + <response shape>
- [ ] <smoke test 2>: POST /endpoint with <body> → expect 201 + <response shape>

### Dependencies
Depends on: #NNN  (or "Parallel: yes")
```
```

- [ ] **Step 2: Add rules for test plans**

In `sdlc/opencode/agents/planner.md`, in the "## Rules" section (after line 84), add:

```markdown
- CI test plan must cover each acceptance criterion with at least one unit or integration test
- Smoke test plan must verify each acceptance criterion end-to-end via HTTP
- Smoke tests are HTTP-only (no DB writes, no auth flows requiring browser interaction)
- Specify the exact endpoint, HTTP method, expected status code, and response shape for each smoke test
```

- [ ] **Step 3: Commit**

```bash
git add sdlc/opencode/agents/planner.md
git commit -m "feat(agents): planner now includes CI test plan + smoke test plan in module plans"
```

---

### Task 9: Build Agent — "Closes" to "Implements" + Implement CI Tests

**Files:**
- Modify: `sdlc/opencode/agents/build.md:27` (PR body keyword)
- Modify: `sdlc/opencode/agents/build.md:23` (Executing step 3)

**Interfaces:**
- Consumes: CI test plan from the Plan comment (posted by planner)
- Produces: PR with "Implements #N" body, CI tests implemented

- [ ] **Step 1: Change PR body keyword**

In `sdlc/opencode/agents/build.md`, line 27, change:

```markdown
6. Open a PR: `gh pr create --title "<title>" --body "Closes #$ISSUE_NUMBER"`
```

To:

```markdown
6. Open a PR: `gh pr create --title "<title>" --body "Implements #$ISSUE_NUMBER

## Summary
<brief description of what was implemented>"`
```

- [ ] **Step 2: Add CI test implementation to Executing step 3**

In `sdlc/opencode/agents/build.md`, line 23, change:

```markdown
3. Implement each checkbox task from the plan
```

To:

```markdown
3. Implement each checkbox task from the plan, INCLUDING the "### CI test plan" tasks
   - Write unit tests and integration tests as specified in the CI test plan section
   - These tests run in build.yml on PR/push
```

- [ ] **Step 3: Add rule about Implements keyword**

In `sdlc/opencode/agents/build.md`, in the "## Rules" section, add:

```markdown
- Use "Implements #N" in PR body — NEVER "Closes #N" (issue must stay open until deployer moves to "Done")
```

- [ ] **Step 4: Commit**

```bash
git add sdlc/opencode/agents/build.md
git commit -m "feat(agents): build agent uses Implements keyword + implements CI tests from plan"
```

---

## Phase B: pc repo changes (PR 2 — depends on PR 1 merged)

### Task 10: sdlc-agent.yml — Add pull_request_target Trigger + Reviewer Dispatch

**Files:**
- Modify: `.github/workflows/sdlc-agent.yml:3-43` (triggers + guard)
- Modify: `.github/workflows/sdlc-agent.yml:85-138` (Determine agent step)
- Modify: `.github/workflows/sdlc-agent.yml:148-156` (env vars)

**Interfaces:**
- Consumes: reviewer agent from nanobyte-services (cloned at runtime)
- Produces: reviewer agent dispatched on PR open/synchronize

- [ ] **Step 1: Add pull_request_target trigger**

In `.github/workflows/sdlc-agent.yml`, after the `issues:` block (line 5), add:

```yaml
  pull_request_target:
    types: [opened, synchronize]
```

The full `on:` block should look like:

```yaml
on:
  issues:
    types: [labeled]
  pull_request_target:
    types: [opened, synchronize]
  repository_dispatch:
    types: [sdlc-phase]
  workflow_dispatch:
    inputs:
      issue_number:
        description: Issue number
        required: true
        default: "39"
      label:
        description: Label that triggered the event
        required: true
        default: "Planning"
      phase:
        description: SDLC phase
        required: true
        default: "planning"
      agent:
        description: Agent to use
        required: true
        default: "planner"
      model:
        description: Model to use
        required: true
        default: "opencode-go/glm-5.2"
```

- [ ] **Step 2: Update the if guard**

In `.github/workflows/sdlc-agent.yml`, line 40-43, change:

```yaml
    if: |
      (github.event_name == 'workflow_dispatch') ||
      (github.event_name == 'issues' && contains(fromJson('["Triaging","Planning","Executing","Testing","Bug Fixing","Publishing","agent-ready"]'), github.event.label.name)) ||
      github.event_name == 'repository_dispatch'
```

To:

```yaml
    if: |
      (github.event_name == 'workflow_dispatch') ||
      (github.event_name == 'issues' && contains(fromJson('["Triaging","Planning","Executing","Testing","Bug Fixing","Publishing","agent-ready"]'), github.event.label.name)) ||
      github.event_name == 'repository_dispatch' ||
      github.event_name == 'pull_request_target'
```

- [ ] **Step 3: Add reviewer dispatch to "Determine agent" step**

In `.github/workflows/sdlc-agent.yml`, in the "Determine agent + model + issue + phase" step, after the `workflow_dispatch` branch (after line 128) and before the `else` (repository_dispatch) branch, add:

```bash
          elif [ "${{ github.event_name }}" = "pull_request_target" ]; then
            PR_NUMBER=${{ github.event.pull_request.number }}
            echo "agent=reviewer" >> $GITHUB_OUTPUT
            echo "model=opencode-go/glm-5.2" >> $GITHUB_OUTPUT
            echo "phase=review" >> $GITHUB_OUTPUT
            echo "pr_number=$PR_NUMBER" >> $GITHUB_OUTPUT
            # Resolve ISSUE_NUMBER from PR body (Implements #N, Closes #N, Fixes #N)
            PR_BODY="${{ github.event.pull_request.body }}"
            ISSUE_NUM=$(echo "$PR_BODY" | grep -oP '(?:Implements|Closes|Resolves|Fixes) #\K[0-9]+' | head -1 || echo "0")
            echo "issue_number=${ISSUE_NUM:-0}" >> $GITHUB_OUTPUT
            # Review round: read from prior review comments, default to 1
            echo "review_round=1" >> $GITHUB_OUTPUT
            echo "last_reviewed_sha=" >> $GITHUB_OUTPUT
```

- [ ] **Step 4: Add UAT_API_URL and PROD_API_URL env vars**

In `.github/workflows/sdlc-agent.yml`, in the "Run phase agent" step's env block (line 148-156), add:

```yaml
        env:
          GH_TOKEN: ${{ secrets.GH_PROJECT_TOKEN }}
          ISSUE_NUMBER: ${{ steps.params.outputs.issue_number }}
          PHASE: ${{ steps.params.outputs.phase }}
          PR_NUMBER: ${{ steps.params.outputs.pr_number }}
          REVIEW_ROUND: ${{ steps.params.outputs.review_round }}
          LAST_REVIEWED_SHA: ${{ steps.params.outputs.last_reviewed_sha }}
          PROJECT_ID: ${{ secrets.SDLC_PROJECT_ID }}
          GITHUB_REPOSITORY: ${{ github.repository }}
          UAT_API_URL: ${{ vars.UAT_API_URL }}
          PROD_API_URL: ${{ vars.PROD_API_URL }}
```

- [ ] **Step 5: Validate YAML syntax**

Run:
```bash
python3 -c "import yaml; yaml.safe_load(open('.github/workflows/sdlc-agent.yml'))" && echo "YAML valid"
```
Expected: `YAML valid`

- [ ] **Step 6: Commit**

```bash
git add .github/workflows/sdlc-agent.yml
git commit -m "feat(workflow): add pull_request_target trigger for reviewer agent + UAT/PROD API URLs"
```

---

### Task 11: Delete Old Workflow Files

**Files:**
- Delete: `.github/workflows/pr-review.yml`
- Delete: `.github/workflows/review-loop.yml`
- Delete: `scripts/aggregate.sh`
- Delete: `scripts/post-comment.sh`

**Interfaces:**
- Consumes: reviewer agent (from Task 4, merged via PR 1)
- Produces: clean repo — no duplicate review logic

- [ ] **Step 1: Delete the files**

```bash
git rm .github/workflows/pr-review.yml
git rm .github/workflows/review-loop.yml
git rm scripts/aggregate.sh
git rm scripts/post-comment.sh
```

- [ ] **Step 2: Verify no other files reference the deleted workflows**

Run:
```bash
grep -r "pr-review\|review-loop\|aggregate.sh\|post-comment.sh" .github/ scripts/ 2>/dev/null || echo "No references found"
```
Expected: `No references found` (or only references in git history, not active files).

- [ ] **Step 3: Commit**

```bash
git commit -m "refactor: remove pr-review.yml, review-loop.yml, aggregate.sh, post-comment.sh

Replaced by the reviewer agent in nanobyte-services. All PR review
and review-loop logic is now agent-driven and centralized."
```

---

## Post-Implementation: Configure GitHub Variables

After both PRs are merged, configure these GitHub repository variables (Settings → Secrets and variables → Actions → Variables):

- `UAT_API_URL` = `https://uat-api.nanobyte.ca` (or the actual UAT URL)
- `PROD_API_URL` = `https://api.nanobyte.ca` (or the actual prod URL)

And ensure the Worker has the `PROJECT_ID` environment variable set (in `wrangler.toml` or Cloudflare dashboard):
- `PROJECT_ID` = `PVT_kwDOEbgU584Bbxz4` (the Nanobyte SDLC board node ID)
