# Strategy Production-Ready — Deviation Remediation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Close every gap between the shipped `feat/strategy-production-ready` branch and its design spec — from the blocker that makes trading unreachable in deployed environments through to low-severity spec amendments.

**Architecture:** Fixes are grouped so each phase is independently landable: (0) deployment wiring, (1) making the Butterfly strategy actually usable end-to-end, (2) restoring verification integrity, (3) backend spec gaps and Greeks, (4) spec amendments and low-severity polish. Backend is Kotlin/Spring Boot 3.3.5; frontend is React/TypeScript with Zustand + Vitest; E2E is Playwright against a deployed environment.

**Tech Stack:** Kotlin 21 / Spring Boot 3.3.5 / Gradle (per-module wrappers), React 18 / TypeScript / Zustand / Vitest / Testing Library, Playwright, Docker Compose.

## Global Constraints

Copied from `AGENTS.md` — every task implicitly includes these:

- **Never add `Co-Authored-By:` or any AI-attribution line to a commit message.** Plain, imperative messages only.
- **Never push unless explicitly asked.** Commit locally and stop.
- **Compose / ports / networks / CI-CD / DB-schema changes REQUIRE a new ADR entry in `docs/adr.md`** in the same commit. Never delete or rewrite past entries. Next free number is **ADR-0033**.
- **Port scheme:** prod `1xxxx`, uat `2xxxx`. pc: strategy prod `10083`, uat `20083`. Never allocate overlapping host ports.
- **Container naming:** `{env}-portfolio-{service}` (`prod-portfolio-strategy`, `uat-portfolio-strategy`). Compose service keys stay app-prefixed (`portfolio-strategy`) — never generic, because service keys become Docker network aliases (ADR-0032).
- **Top-level `name:` in every compose file** (`name: portfolio-prod`, `name: portfolio-uat`).
- **Secrets come from Vault** (AppRole). Never hardcode secrets.
- **No JDK on this machine by default.** Install/use Temurin 21 and export before any Gradle command:
  `export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH`
- **No root `gradlew`.** Run Gradle in the module directory (`backend/strategy/`, `backend/broker-gateway/`).
- **Run Gradle test tasks one at a time.** Running two Gradle test suites concurrently on this machine starves the test workers ("Unable to connect to the child process 'Gradle Test Executor 1'").
- **Frontend commands run in `frontend/`:** `npx tsc --noEmit`, `npx vitest run`.

## Deviation → Task Index

| ID | Deviation | Severity | Task |
|----|-----------|----------|------|
| D1 | `BROKER_GATEWAY_URL` unset for strategy service | Blocker | 1 |
| D5 | Butterfly leg quantity unrepresentable (`LegTemplate` has no quantity; API exposes no templates) | Blocker | 2 |
| D4 | `LegBuilder` unmodified — no quantity adjusters, no live mids | Blocker | 3, 4 |
| D3 | `useOptionStreaming` dead code; no flash animation; no chain toggle-remove | Blocker | 4, 5 |
| D6 | E2E runs against deployed env; `STRAT-003` contradicts implementation | High | 6, 8 |
| D12 | E2E scenario gaps (missing TRADE-007/009; weak STRAT-002/007/008, TRADE-002) | High | 7 |
| D9 | Non-butterfly `legCount` validation missing | Medium | 9 |
| D10 | Butterfly validation loosened; no strike-ordering check | Medium | 10 |
| D7 | Greeks: theta/vega hardcoded zero | Medium | 11 |
| D8 | Sequential fallback removed (ratify + amend spec) | Medium | 12 |
| D11 | Coverage >80% never measured | Medium | 8 |
| D13 | Layout deviations (confirm text, quantity source, selector position) | Medium | 13 |
| — | PnlChart dollar axis; QuoteBar / UnderlyingSearch unmodified; `selectedStrategyOutlook`; enum typing; synthetic leg status | Low | 12, 13 |

**Two spec facts to keep straight while working:**
- The spec's §1.1 label "Resulting enum (5 values)" is a **typo** — it lists 6 values, the code has 6, and `STRAT-001` asserts 6. Do **not** "fix" the enum to 5.
- `§4.1` was written on an outdated premise ("hook only supports stock quotes"). Option streaming already exists on `main` (`subscribeOption` / `option_quote` / `batchUpdateChainQuotes`). Task 12 amends the spec instead of building a duplicate pipeline.

---

## Phase 0 — Unblock deployed trading

### Task 1: Wire `BROKER_GATEWAY_URL` for the strategy service

**Why:** `BrokerGatewayClient` defaults to `http://localhost:8084`. Inside the strategy container that is the strategy service itself, so every `/trade` call fails with a connection error. The var is present for `portfolio-backend` but missing from `portfolio-strategy` in all three compose files.

**Files:**
- Modify: `deploy/prod/docker-compose.yml` (the `portfolio-strategy:` → `environment:` block, around line 118-135)
- Modify: `deploy/uat/docker-compose.yml` (`portfolio-strategy:` → `environment:`)
- Modify: `docker-compose.yml` (same service)
- Modify: `docs/adr.md` (append ADR-0033)
- Test: `scripts/verify-strategy-gateway-url.sh` (new)

**Interfaces:**
- Consumes: `BrokerGatewayClient` reads Spring property `broker-gateway.url` (`backend/strategy/src/main/kotlin/com/portfolio/strategy/service/BrokerGatewayClient.kt:13`), populated by env var `BROKER_GATEWAY_URL` through Spring relaxed binding.
- Produces: nothing importable; this is pure configuration.

- [ ] **Step 1: Write the failing verification script**

Create `scripts/verify-strategy-gateway-url.sh`:

```bash
#!/usr/bin/env bash
# Fails if any compose file omits BROKER_GATEWAY_URL for the strategy service.
set -euo pipefail

check() {
  local file="$1" expected="$2"
  local actual
  actual=$(python3 -c "
import yaml,sys
d = yaml.safe_load(open('$file'))
env = d['services']['portfolio-strategy'].get('environment') or {}
print(env.get('BROKER_GATEWAY_URL', ''))
")
  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL $file: BROKER_GATEWAY_URL='$actual' (expected '$expected')" >&2
    return 1
  fi
  echo "OK   $file: $actual"
}

check deploy/prod/docker-compose.yml "http://prod-portfolio-broker-gateway:8084"
check deploy/uat/docker-compose.yml  "http://uat-portfolio-broker-gateway:8084"
check docker-compose.yml             "http://portfolio-broker-gateway:8084"
echo "All compose files wire BROKER_GATEWAY_URL for the strategy service."
```

- [ ] **Step 2: Run it to verify it fails**

```bash
chmod +x scripts/verify-strategy-gateway-url.sh
./scripts/verify-strategy-gateway-url.sh
```

Expected: `FAIL deploy/prod/docker-compose.yml: BROKER_GATEWAY_URL='' (expected 'http://prod-portfolio-broker-gateway:8084')` and a non-zero exit.

- [ ] **Step 3: Add the env var to all three compose files**

In each file, inside the `portfolio-strategy:` service's `environment:` mapping, add the line next to the existing service URLs (mirror the spacing of `MARKET_DATA_SERVICE_URL`; in the prod file the block spans lines 120–153, so insert directly after `PORTFOLIO_SERVICE_URL` on line 132):

`deploy/prod/docker-compose.yml`:
```yaml
      BROKER_GATEWAY_URL: http://prod-portfolio-broker-gateway:8084
```

`deploy/uat/docker-compose.yml`:
```yaml
      BROKER_GATEWAY_URL: http://uat-portfolio-broker-gateway:8084
```

`docker-compose.yml`:
```yaml
      BROKER_GATEWAY_URL: http://portfolio-broker-gateway:8084
```

Do **not** add Postgres/Redis services, volumes, or host ports to the two deployed files (ADR-0017), and do **not** change the top-level `name:` key.

- [ ] **Step 4: Run the verification to confirm it passes**

```bash
./scripts/verify-strategy-gateway-url.sh
```

Expected: three `OK` lines then `All compose files wire BROKER_GATEWAY_URL for the strategy service.`

- [ ] **Step 5: Add ADR-0033**

Append to `docs/adr.md` (match the formatting of the surrounding entries — read ADR-0032 first and copy its heading style):

```markdown
## ADR-0033: Wire BROKER_GATEWAY_URL into the strategy service

### Status
Accepted

### Context
The strategy service places atomic multi-leg orders by calling the broker-gateway's
`.../combo-orders` endpoint through `BrokerGatewayClient`, which reads the Spring
property `broker-gateway.url` and defaults to `http://localhost:8084`.

`deploy/prod/docker-compose.yml`, `deploy/uat/docker-compose.yml` and the root
`docker-compose.yml` set `BROKER_GATEWAY_URL` for `portfolio-backend` but not for
`portfolio-strategy`. Inside the strategy container the fallback resolves to the
strategy service itself, so every one-click trade would fail with a connection error
in every deployed environment.

### Decision
Set `BROKER_GATEWAY_URL` explicitly for the `portfolio-strategy` service in all three
compose files, pointing at that environment's broker-gateway container:

- prod: `http://prod-portfolio-broker-gateway:8084`
- uat:  `http://uat-portfolio-broker-gateway:8084`
- local: `http://portfolio-broker-gateway:8084`

A `scripts/verify-strategy-gateway-url.sh` check guards against regression.

### Consequences
Trading can reach the broker-gateway in every environment. The compose files now
carry one more service URL, which must be kept in sync when containers are renamed.
```

- [ ] **Step 6: Commit**

```bash
git add deploy/prod/docker-compose.yml deploy/uat/docker-compose.yml docker-compose.yml docs/adr.md scripts/verify-strategy-gateway-url.sh
git commit -m "fix(strategy): wire broker-gateway url into strategy service"
```

> **Typo trap:** the Spring property is `broker-gateway.url`, so the env var is `BROKER_GATEWAY_URL`. A `BROKER_GATEWAY.URL` or `BROKER_GATEWAY` var will be silently ignored and the localhost default will come back.

---

## Phase 1 — Make Butterfly usable end to end

### Task 2: Represent leg quantity in `LegTemplate` and expose templates via the API

**Why:** The spec mandates `LegTemplate(SELL, CALL, OTM_1) -- quantity 2`. `LegTemplate` has no quantity field, so the 2× middle leg exists only as a post-hoc validator rule. Worse, `StrategyInfoResponse` exposes **no** `legTemplates` at all — no client can discover the required quantities, so any generated Butterfly defaults to quantity 1 and is rejected with HTTP 400.

**Files:**
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/model/StrategyDefinition.kt`
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyRegistry.kt:62-71`
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/api/dto/StrategyDtos.kt`
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/api/controller/StrategyController.kt` (`getStrategyInfo` and `listStrategies`)
- Test: `backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyRegistryTest.kt` (new)

**Interfaces:**
- Produces: `data class LegTemplate(val action: LegAction, val optionType: OptionType?, val strikeOffset: StrikeOffset, val quantity: Int = 1)`
- Produces: `data class LegTemplateDto(val action: String, val optionType: String?, val strikeOffset: String, val quantity: Int)`
- Produces: `StrategyInfoResponse` gains `val legTemplates: List<LegTemplateDto>`; `StrategyListResponse` gains the same field.
- Consumes: `registry.getDefinition(type)` and `registry.listAll()` (existing).

- [ ] **Step 1: Write the failing test**

Create `backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyRegistryTest.kt`. The module's test convention is JUnit 5 with `org.junit.jupiter.api.Assertions.*` static imports (see `LegValidatorTest.kt:1-9`) — match it exactly:

```kotlin
package com.portfolio.strategy.engine

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.model.*
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.Test
import java.math.BigDecimal

class StrategyRegistryTest {

    private val registry = StrategyRegistry()

    @Test
    fun `butterfly middle leg requires quantity 2`() {
        val definition = registry.getDefinition(StrategyType.BUTTERFLY_SPREAD)
        val sellLeg = definition.legTemplates.single { it.action == LegAction.SELL }
        assertEquals(2, sellLeg.quantity)
    }

    @Test
    fun `butterfly outer legs default to quantity 1`() {
        val definition = registry.getDefinition(StrategyType.BUTTERFLY_SPREAD)
        val buyLegs = definition.legTemplates.filter { it.action == LegAction.BUY }
        assertEquals(2, buyLegs.size)
        assertEquals(listOf(1, 1), buyLegs.map { it.quantity })
    }

    @Test
    fun `every strategy template quantity is at least 1`() {
        registry.listAll().forEach { definition ->
            definition.legTemplates.forEach { template ->
                assertTrue(template.quantity >= 1) { "${definition.type} has quantity ${template.quantity}" }
            }
        }
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests "com.portfolio.strategy.engine.StrategyRegistryTest" --console=plain
```

Expected: compilation failure — `LegTemplate` has no `quantity` property.

- [ ] **Step 3: Add `quantity` to `LegTemplate`**

In `backend/strategy/src/main/kotlin/com/portfolio/strategy/model/StrategyDefinition.kt`:

```kotlin
data class LegTemplate(
    val action: LegAction,
    val optionType: OptionType?,
    val strikeOffset: StrikeOffset,
    val quantity: Int = 1
)
```

- [ ] **Step 4: Set the Butterfly middle leg to quantity 2**

In `StrategyRegistry.kt`, update the Butterfly `legTemplates` list:

```kotlin
            legTemplates = listOf(
                LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.ATM),
                LegTemplate(LegAction.SELL, OptionType.CALL, StrikeOffset.OTM_1, quantity = 2),
                LegTemplate(LegAction.BUY, OptionType.CALL, StrikeOffset.OTM_2)
            )
```

Leave every other strategy's templates untouched — the default `quantity = 1` keeps them compiling.

- [ ] **Step 5: Run the test to verify it passes**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests "com.portfolio.strategy.engine.StrategyRegistryTest" --console=plain
```

Expected: `BUILD SUCCESSFUL`, 3 tests pass.

- [ ] **Step 6: Write the failing DTO test**

Add to `StrategyRegistryTest.kt` a check that the controller response carries templates (or, if you prefer an HTTP-level test, put this in a new `StrategyControllerTest.kt` using the existing Spring test conventions in the module — read `backend/portfolio/src/test` for a `@SpringBootTest`/`MockMvc` example and copy it):

```kotlin
    @Test
    fun `leg template dto carries quantity`() {
        val definition = registry.getDefinition(StrategyType.BUTTERFLY_SPREAD)
        val dtos = definition.legTemplates.map {
            LegTemplateDto(
                action = it.action.name,
                optionType = it.optionType?.name,
                strikeOffset = it.strikeOffset.name,
                quantity = it.quantity
            )
        }
        assertEquals(2, dtos.single { it.action == "SELL" }.quantity)
    }
```

- [ ] **Step 7: Run it, then add `LegTemplateDto` and wire it into both responses**

Add to `StrategyDtos.kt`:

```kotlin
data class LegTemplateDto(
    val action: String,
    val optionType: String?,
    val strikeOffset: String,
    val quantity: Int
)
```

Add `val legTemplates: List<LegTemplateDto>` to **both** `StrategyListResponse` and `StrategyInfoResponse`, then update `StrategyController`:

```kotlin
    @GetMapping
    fun listStrategies(): List<StrategyListResponse> {
        return registry.listAll().map { d ->
            StrategyListResponse(
                d.type.name, d.displayName, d.description, d.outlook, d.riskProfile, d.legCount,
                d.legTemplates.map { it.toDto() }
            )
        }
    }

    @GetMapping("/{name}")
    fun getStrategyInfo(@PathVariable name: String): StrategyInfoResponse {
        val strategyType = try { StrategyType.valueOf(name) } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid strategy name: $name")
        }
        val definition = registry.getDefinition(strategyType)
        val education = educationEngine.getContent(strategyType)
        return StrategyInfoResponse(
            definition.type.name, definition.displayName, definition.description,
            definition.outlook, definition.riskProfile, definition.legCount,
            definition.legTemplates.map { it.toDto() },
            education
        )
    }

    private fun LegTemplate.toDto() = LegTemplateDto(
        action = action.name,
        optionType = optionType?.name,
        strikeOffset = strikeOffset.name,
        quantity = quantity
    )
```

Add the import `com.portfolio.strategy.model.LegTemplate` to `StrategyController.kt` and `LegTemplateDto` to the DTO import list if the module imports DTOs individually.

- [ ] **Step 8: Run the full strategy test suite**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --console=plain
```

Expected: `BUILD SUCCESSFUL`. Existing 49 tests plus the new ones, 0 failures. If `StrategyInfoResponse` positional construction is used anywhere else, the compiler will flag it — fix by adding the new argument.

- [ ] **Step 9: Commit**

```bash
git add backend/strategy/src/main/kotlin/com/portfolio/strategy/model/StrategyDefinition.kt \
        backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyRegistry.kt \
        backend/strategy/src/main/kotlin/com/portfolio/strategy/api/dto/StrategyDtos.kt \
        backend/strategy/src/main/kotlin/com/portfolio/strategy/api/controller/StrategyController.kt \
        backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyRegistryTest.kt
git commit -m "feat(strategy): expose leg template quantities in strategy api"
```

---

### Task 3: LegBuilder quantity adjusters + live mid prices

**Why:** The spec requires per-leg quantity adjusters and real-time mid prices in the leg builder. `LegBuilder.tsx` is byte-identical to `main`: it shows a static `leg.price` captured at click time and offers no quantity control, so the user cannot build a valid Butterfly (the middle leg must be quantity 2). The store already has an unused `updateLeg(index, leg)` action.

**Files:**
- Modify: `frontend/src/components/options/LegBuilder.tsx`
- Modify: `frontend/src/components/options/LegBuilder.css`
- Modify: `frontend/src/pages/OptionsPage.tsx` (pass live mid prices in)
- Test: `frontend/src/components/options/LegBuilder.test.tsx` (new)

**Interfaces:**
- Consumes: `useStrategyStore()` → `{ legs, removeLeg, updateLeg, clearStrategy, selectedStrategy }`; `Leg { action, optionType, strike, expiry, quantity, price?, bid?, ask?, mid?, delta?, symbol? }`.
- Produces: `LegBuilderProps` gains an optional `liveMid?: (leg: Leg) => number | undefined` (or `liveMids?: Map<string, number>` — pick whichever Task 4's hook returns and keep the name identical in both tasks).

- [ ] **Step 1: Read the existing options component test pattern**

```bash
sed -n '1,30p' frontend/src/components/options/OptionsChainTable.test.tsx
```

Copy its imports and render/mocking approach verbatim into the new test so store mocking matches existing conventions.

- [ ] **Step 2: Write the failing test**

Create `frontend/src/components/options/LegBuilder.test.tsx`:

```tsx
import { describe, it, expect, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { LegBuilder } from './LegBuilder'
import { useStrategyStore } from '@/stores/strategyStore'

const leg = {
  action: 'BUY' as const,
  optionType: 'CALL' as const,
  strike: 100,
  expiry: '2026-12-18',
  quantity: 1,
  price: 2.5,
  symbol: 'SPY',
}

describe('LegBuilder', () => {
  beforeEach(() => {
    useStrategyStore.setState({ legs: [leg] })
  })

  it('increments a leg quantity through the store', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Increase quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(2)
  })

  it('decrements a leg quantity through the store', () => {
    useStrategyStore.setState({ legs: [{ ...leg, quantity: 2 }] })
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Decrease quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(1)
  })

  it('never decrements below one', () => {
    render(<LegBuilder onCalculate={() => {}} isCalculating={false} />)
    fireEvent.click(screen.getByLabelText('Decrease quantity for leg 1'))
    expect(useStrategyStore.getState().legs[0].quantity).toBe(1)
  })

  it('renders a live mid price when supplied', () => {
    render(
      <LegBuilder onCalculate={() => {}} isCalculating={false} liveMid={() => 3.75} />
    )
    expect(screen.getByText('$3.75')).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Run the test to verify it fails**

```bash
cd frontend
npx vitest run src/components/options/LegBuilder.test.tsx
```

Expected: FAIL — no accessible label `Increase quantity for leg 1` (and `liveMid` is not a prop).

- [ ] **Step 4: Implement quantity adjusters and live mid**

Update `LegBuilder.tsx`. Keep the existing badges, strike/expiry fields, × remove button, Clear All, and Calculate button exactly as they are; add a quantity field between Strike and Mid, and prefer the live mid over the captured price:

```tsx
interface LegBuilderProps {
  onCalculate: () => void
  isCalculating: boolean
  liveMid?: (leg: Leg) => number | undefined
}

export function LegBuilder({ onCalculate, isCalculating, liveMid }: LegBuilderProps) {
  const { legs, removeLeg, updateLeg, clearStrategy } = useStrategyStore()

  const adjustQuantity = (index: number, delta: number) => {
    const current = legs[index]
    const next = Math.max(1, current.quantity + delta)
    if (next === current.quantity) return
    updateLeg(index, { ...current, quantity: next })
  }
  // ...inside the card fields, after the Strike field:
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Qty</span>
                  <span className="leg-builder__card-value">
                    <button
                      className="leg-builder__qty-btn"
                      aria-label={`Decrease quantity for leg ${i + 1}`}
                      onClick={() => adjustQuantity(i, -1)}
                    >
                      &minus;
                    </button>
                    <span className="leg-builder__qty-value">{leg.quantity}</span>
                    <button
                      className="leg-builder__qty-btn"
                      aria-label={`Increase quantity for leg ${i + 1}`}
                      onClick={() => adjustQuantity(i, 1)}
                    >
                      +
                    </button>
                  </span>
                </div>
  // ...and replace the Mid value cell:
                <div className="leg-builder__card-field">
                  <span className="leg-builder__card-label">Mid</span>
                  <span className="leg-builder__card-value">
                    ${(liveMid?.(leg) ?? leg.price)?.toFixed(2) ?? '-'}
                  </span>
                </div>
```

Import `Leg` in addition to the store: `import type { Leg } from '@/types/options'`.

- [ ] **Step 5: Add the CSS**

Append to `LegBuilder.css` (match the file's existing custom-property names and spacing scale — read the file first):

```css
.leg-builder__qty-btn {
  background: none;
  border: 1px solid var(--border, #333);
  border-radius: 4px;
  color: inherit;
  cursor: pointer;
  width: 20px;
  height: 20px;
  line-height: 1;
  padding: 0;
}

.leg-builder__qty-value {
  display: inline-block;
  min-width: 18px;
  text-align: center;
}
```

- [ ] **Step 6: Run the test to verify it passes**

```bash
cd frontend
npx vitest run src/components/options/LegBuilder.test.tsx
```

Expected: 4 tests pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/options/LegBuilder.tsx frontend/src/components/options/LegBuilder.css frontend/src/components/options/LegBuilder.test.tsx
git commit -m "feat(options): add leg quantity adjusters and live mid display"
```

---

### Task 4: Live mid prices in the leg builder, derived from the chain store

**Why:** The legs show a static `leg.price` captured at click time. The live data already reaches the frontend: `OptionsPage` subscribes the chain expiry (`subscribeChainExpiry`, line 71), `useMarketDataWebSocket` routes `option_quote` messages into `quoteStore.chains` via `batchUpdateChainQuotes` (lines 83–89 → `quoteStore.ts:88-133`), and `OptionsChainTable` re-renders from that store. The leg builder simply never reads it.

`frontend/src/hooks/useOptionStreaming.ts` (151 lines) is **never imported anywhere** — dead code. Its subscription logic duplicates what the page-level chain subscription already does, and its `OptionTick` type has no `mid` field. Delete it rather than wire it; the live-mid derivation is a pure function over the store.

**Files:**
- Create: `frontend/src/hooks/liveMids.ts` (pure helper)
- Modify: `frontend/src/pages/OptionsPage.tsx`
- Delete: `frontend/src/hooks/useOptionStreaming.ts`
- Test: `frontend/src/hooks/liveMids.test.ts` (new)

**Interfaces:**
- Consumes: `useQuoteStore(state => state.chains)` — `chains: Record<string, OptionsChain>` keyed by underlying; `chain.expirations[expiry][strikeKey]` → `{ call, put }` quotes with `bid/ask/last/mid`. Strike keys are normalized (`normalizeStrikeKey`, `quoteStore.ts:23-30`) with the fallback-candidate list used in `batchUpdateChainQuotes`.
- Produces: `derivePriceFor(chains: Record<string, OptionsChain>, underlying: string | null): (leg: Leg) => number | undefined` — this is what Task 3's `liveMid` prop receives.
- Note: the selected expiry lives **inside** `OptionsChainTable` (`selectedExpiry`, `OptionsChainTable.tsx:17`), not in the page. The helper therefore keys off each leg's own `expiry` field, which is always populated, and needs no expiry state at all.

- [ ] **Step 1: Write the failing test**

Create `frontend/src/hooks/liveMids.test.ts`:

```ts
import { describe, it, expect } from 'vitest'
import { derivePriceFor } from './liveMids'
import type { OptionsChain } from '@/types/options'

const chain = {
  underlying: 'SPY',
  spotPrice: 100,
  expirations: {
    '2026-12-18': {
      '100': {
        call: { bid: 2.4, ask: 2.6, last: 2.5, mid: 2.5 },
        put: null,
      },
    },
  },
} as unknown as Record<string, OptionsChain>

const leg = {
  action: 'BUY' as const,
  optionType: 'CALL' as const,
  strike: 100,
  expiry: '2026-12-18',
  quantity: 1,
  symbol: 'SPY',
}

describe('derivePriceFor', () => {
  it('returns the stored mid for a matching leg', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor(leg)).toBe(2.5)
  })

  it('falls back to the bid/ask midpoint when mid is missing', () => {
    const noMid = {
      ...chain,
      expirations: {
        '2026-12-18': {
          '100': { call: { bid: 2.4, ask: 2.6, last: 2.5 }, put: null },
        },
      },
    } as unknown as Record<string, OptionsChain>
    const priceFor = derivePriceFor(noMid, 'SPY')
    expect(priceFor(leg)).toBe(2.5)
  })

  it('returns undefined when the leg has no live quote', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor({ ...leg, strike: 999 })).toBeUndefined()
  })

  it('returns undefined when the leg carries no symbol', () => {
    const priceFor = derivePriceFor(chain, 'SPY')
    expect(priceFor({ ...leg, symbol: undefined })).toBeUndefined()
  })
})
```

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend
npx vitest run src/hooks/liveMids.test.ts
```

Expected: FAIL — `./liveMids` does not exist.

- [ ] **Step 3: Implement the helper**

Create `frontend/src/hooks/liveMids.ts`. Mirror the strike-key fallback candidates from `batchUpdateChainQuotes` (`quoteStore.ts:104-118`) so both paths resolve keys identically:

```ts
import type { Leg, OptionsChain } from '@/types/options'

function strikeKeyCandidates(strike: number): string[] {
  return [
    String(strike),
    strike.toFixed(1),
    strike.toFixed(2),
    strike.toFixed(4),
    `${strike}.0`,
    `${strike}.00`,
  ]
}

export function derivePriceFor(
  chains: Record<string, OptionsChain>,
  underlying: string | null
): (leg: Leg) => number | undefined {
  return (leg) => {
    if (!underlying || !leg.symbol) return undefined
    const expiryData = chains[underlying]?.expirations[leg.expiry]
    if (!expiryData) return undefined

    let strikeData = expiryData[String(leg.strike)]
    if (!strikeData) {
      for (const candidate of strikeKeyCandidates(leg.strike)) {
        if (expiryData[candidate]) {
          strikeData = expiryData[candidate]
          break
        }
      }
    }
    if (!strikeData) return undefined

    const quote = leg.optionType === 'PUT' ? strikeData.put : strikeData.call
    if (!quote) return undefined
    if (typeof quote.mid === 'number' && quote.mid > 0) return quote.mid
    if (quote.bid > 0 && quote.ask > 0) return (quote.bid + quote.ask) / 2
    return undefined
  }
}
```

- [ ] **Step 4: Run the test to verify it passes**

```bash
cd frontend
npx vitest run src/hooks/liveMids.test.ts
```

Expected: 4 tests pass.

- [ ] **Step 5: Wire it into `OptionsPage` and pass it to `LegBuilder`**

In `OptionsPage.tsx`, the page already reads the quote store for `selectedUnderlying` and `quotes`. Add the chains selector and derive the callback once per render:

```tsx
  const chains = useQuoteStore((s) => s.chains)
  const priceFor = useMemo(() => derivePriceFor(chains, selectedUnderlying), [chains, selectedUnderlying])
```

Add `useMemo` to the existing React import and `derivePriceFor` to a new import from `@/hooks/liveMids`. Then pass it to the builder (the existing `LegBuilder` render already passes `onCalculate` and `isCalculating` — keep those exactly as they are and add one prop):

```tsx
          <LegBuilder
            onCalculate={handleCalculate}
            isCalculating={isCalculating}
            liveMid={priceFor}
          />
```

Match the existing prop values verbatim — read the current `LegBuilder` render block in `OptionsPage.tsx` (center panel, around lines 227–242) and only add the `liveMid` line.

- [ ] **Step 6: Delete the dead hook**

```bash
git rm frontend/src/hooks/useOptionStreaming.ts
```

Then confirm nothing referenced it:

```bash
grep -rn "useOptionStreaming" frontend/src
```

Expected: no matches.

- [ ] **Step 7: Run the tests and typecheck**

```bash
cd frontend
npx vitest run src/hooks/liveMids.test.ts src/components/options/LegBuilder.test.tsx
npx tsc --noEmit
```

Expected: tests pass, `tsc` exit 0.

- [ ] **Step 8: Commit**

```bash
git add frontend/src/hooks/liveMids.ts frontend/src/hooks/liveMids.test.ts frontend/src/pages/OptionsPage.tsx
git commit -m "feat(options): derive live leg mids from the chain store"
```

---

### Task 5: Price flash animation and toggle-remove on the chain

**Why:** The spec requires the chain to "flash green/red on change" (nothing in the frontend references `flash` at all) and clicking "bid/ask adds/removes legs". Today every cell click only adds, and there is no visual feedback for a price change. The store already has `removeLeg`, so only the click semantics and CSS are missing.

**Files:**
- Modify: `frontend/src/components/options/OptionsChainTable.tsx`
- Modify: `frontend/src/components/options/OptionsChainTable.css`
- Test: `frontend/src/components/options/OptionsChainTable.test.tsx` (existing file — append)

**Interfaces:**
- Consumes: `useStrategyStore()` → `{ legs, addLeg, removeLeg }`; the chain quote objects already passed in as props.
- Produces: CSS classes `chain-table__flash--up` / `chain-table__flash--down` and `data-flash` attributes on price cells.

- [ ] **Step 1: Write the failing test**

The existing `OptionsChainTable.test.tsx` (186 lines) mocks the store at module level with `vi.mock('@/stores/strategyStore', …)` returning `{ addLeg: vi.fn() }`, builds chain fixtures with a local `makeChain(overrides?)` factory, and has **no** `beforeEach` reset. Extend that pattern — do not introduce a real store here. Append to `frontend/src/components/options/OptionsChainTable.test.tsx`:

```tsx
  it(scenario('OPT-TABLE-009', 'applies an upward flash class when a price rises'), () => {
    const { container, rerender } = render(
      <OptionsChainTable chain={makeChain()} strikesPerSide={25} onStrikesPerSideChange={vi.fn()} />
    )
    expect(container.querySelectorAll('.chain-table__flash--up').length).toBe(0)
    // re-render with a higher bid on the same contract
    rerender(
      <OptionsChainTable chain={makeChain({ higherBid: true })} strikesPerSide={25} onStrikesPerSideChange={vi.fn()} />
    )
    expect(container.querySelectorAll('.chain-table__flash--up').length).toBeGreaterThan(0)
  })

  it(scenario('OPT-TABLE-010', 'removes a leg when the same contract is clicked twice'), () => {
    render(
      <OptionsChainTable chain={makeChain()} strikesPerSide={25} onStrikesPerSideChange={vi.fn()} />
    )
    const bidCells = document.querySelectorAll('.chain-table__call-side')
    fireEvent.click(bidCells[0])
    expect(mockAddLeg).toHaveBeenCalledTimes(1)
    fireEvent.click(bidCells[0])
    expect(mockRemoveLeg).toHaveBeenCalledWith(0)
  })
```

Two prerequisites, both real code changes to the test file:

- Extend the module-level store mock so the component can read `legs` and `removeLeg` through its selectors — the current mock only provides `addLeg`:

```tsx
const mockAddLeg = vi.fn()
const mockRemoveLeg = vi.fn()
vi.mock('@/stores/strategyStore', () => ({
  useStrategyStore: (selector: (s: unknown) => unknown) =>
    selector({ legs: [], addLeg: mockAddLeg, removeLeg: mockRemoveLeg }),
}))
```

- Extend `makeChain` with a `higherBid` override (or pass an explicit bid value) so the rerender produces a changed price on the same strike key. Read the existing `makeChain` factory and add the override in its style.

- [ ] **Step 2: Run the test to verify it fails**

```bash
cd frontend
npx vitest run src/components/options/OptionsChainTable.test.tsx
```

Expected: FAIL — no `.chain-table__flash--up` element, and the second click leaves one leg in the store.

- [ ] **Step 3: Implement flash tracking**

`useRef` is **not** currently imported in `OptionsChainTable.tsx` (imports are `useState, useEffect` only, line 1) — add it. Rows are rendered inline in `.map` callbacks, so hooks cannot live in the row; extract a small child-row component and pass everything it needs as props (the component reads the store via selectors, so props keep the child pure and testable):

```tsx
function useFlashDirection(price: number | undefined, key: string) {
  const prev = useRef<Map<string, number>>(new Map())
  const [flash, setFlash] = useState<'' | 'up' | 'down'>('')

  useEffect(() => {
    if (price === undefined) return
    const last = prev.current.get(key)
    prev.current.set(key, price)
    if (last === undefined || last === price) return
    setFlash(price > last ? 'up' : 'down')
    const t = setTimeout(() => setFlash(''), 400)
    return () => clearTimeout(t)
  }, [price, key])

  return flash
}
```

Create a `ChainRow` component that receives `{ strikeKey, data, spot, legs, toggleLeg }` as props and renders the existing `<tr>` markup verbatim (both desktop and mobile variants stay as they are — only the price cells gain the flash class). Apply the hook per cell:

```tsx
const callBidFlash = useFlashDirection(data.call?.bid, `${strikeKey}:call:bid`)
```

and merge it into the cell className:

```tsx
<td className={`chain-table__call-side ${callITM ? 'chain-table__itm' : ''} ${callBidFlash ? `chain-table__flash--${callBidFlash}` : ''}`}
    onClick={() => toggleLeg(data.call, 'BUY')}>
  {data.call?.bid?.toFixed(2) ?? '-'}
</td>
```

Do the same for the call ask, put bid, put ask, and the two mobile cells.

- [ ] **Step 4: Implement toggle-remove**

The component currently reads only `addLeg` from the store via a selector (`OptionsChainTable.tsx:20`). Extend the selectors:

```tsx
  const addLeg = useStrategyStore((s) => s.addLeg)
  const removeLeg = useStrategyStore((s) => s.removeLeg)
  const legs = useStrategyStore((s) => s.legs)
```

Replace the add-only handler. Keep the existing leg-construction code and compare on the fields that identify a contract:

```tsx
  const toggleLeg = (quote: OptionQuoteData, action: LegAction) => {
    const existing = legs.findIndex(
      (l) =>
        l.strike === quote.strike &&
        l.expiry === quote.expiry &&
        l.optionType === quote.optionType &&
        l.action === action
    )
    if (existing >= 0) {
      removeLeg(existing)
      return
    }
    addLeg({
      action,
      optionType: quote.optionType as OptionTypeName,
      strike: quote.strike,
      expiry: quote.expiry,
      quantity: 1,
      price: quote.mid,
      bid: quote.bid,
      ask: quote.ask,
      mid: quote.mid,
      delta: quote.greeks?.delta,
      symbol: quote.underlying,
    })
  }
```

Then point all six click handlers at `toggleLeg`.

- [ ] **Step 5: Add the CSS**

Append to `OptionsChainTable.css`:

```css
@keyframes chain-flash-up {
  from { background-color: rgba(34, 197, 94, 0.35); }
  to   { background-color: transparent; }
}

@keyframes chain-flash-down {
  from { background-color: rgba(239, 68, 68, 0.35); }
  to   { background-color: transparent; }
}

.chain-table__flash--up { animation: chain-flash-up 400ms ease-out; }
.chain-table__flash--down { animation: chain-flash-down 400ms ease-out; }
```

- [ ] **Step 6: Run the tests and typecheck**

```bash
cd frontend
npx vitest run src/components/options/OptionsChainTable.test.tsx
npx tsc --noEmit
```

Expected: tests pass, `tsc` exit 0. Then run the whole frontend suite once (`npx vitest run`) and confirm 188+ tests still pass.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/options/OptionsChainTable.tsx frontend/src/components/options/OptionsChainTable.css frontend/src/components/options/OptionsChainTable.test.tsx
git commit -m "feat(options): flash price changes and toggle legs from the chain"
```

---

## Phase 2 — Restore verification integrity

### Task 6: Fix the outlook badge label and make `STRAT-003` non-vacuous

**Why:** `StrategySelector.tsx:66` renders `{s.marketOutlook.split(' ')[0]}`. Registry outlooks are `"Moderately Bullish"`, `"Moderately Bearish"`, and `"Neutral (Range-Bound)"`, so four of six badges render literally **"Moderately"** — a meaningless label. `STRAT-003` expects the text to be one of `Bullish | Bearish | Neutral | Range-Bound`, so against this branch's code it should fail; it only appears to pass because the loop can iterate over zero cards and assert nothing.

**Files:**
- Modify: `frontend/src/components/options/StrategySelector.tsx`
- Modify: `e2e/tests/regression/strategies.spec.ts:52-64`
- Test: `frontend/src/components/options/StrategySelector.test.tsx` (new)

**Interfaces:**
- Produces: `export function outlookLabel(outlook: string): 'Bullish' | 'Bearish' | 'Neutral'` — pure, testable, used for the badge text. `outlookBadgeClass` keeps its existing behaviour.

- [ ] **Step 1: Write the failing unit test**

Create `frontend/src/components/options/StrategySelector.test.tsx` (copy the render/store-mocking pattern from `OptionsChainTable.test.tsx`):

```tsx
import { describe, it, expect } from 'vitest'
import { outlookLabel } from './StrategySelector'

describe('outlookLabel', () => {
  it('maps a moderately bullish outlook to Bullish', () => {
    expect(outlookLabel('Moderately Bullish')).toBe('Bullish')
  })

  it('maps a moderately bearish outlook to Bearish', () => {
    expect(outlookLabel('Moderately Bearish')).toBe('Bearish')
  })

  it('maps a range-bound outlook to Neutral', () => {
    expect(outlookLabel('Neutral (Range-Bound)')).toBe('Neutral')
  })

  it('defaults to Neutral for an unknown outlook', () => {
    expect(outlookLabel('Something Else')).toBe('Neutral')
  })
})
```

- [ ] **Step 2: Run it to verify it fails**

```bash
cd frontend
npx vitest run src/components/options/StrategySelector.test.tsx
```

Expected: FAIL — `outlookLabel` is not exported.

- [ ] **Step 3: Implement and use it**

In `StrategySelector.tsx`, add next to `outlookBadgeClass`:

```tsx
export function outlookLabel(outlook: string): 'Bullish' | 'Bearish' | 'Neutral' {
  const lower = outlook.toLowerCase()
  if (lower.includes('bullish')) return 'Bullish'
  if (lower.includes('bearish')) return 'Bearish'
  return 'Neutral'
}
```

and change the badge body:

```tsx
            <span className={`strategy-card__outlook ${outlookBadgeClass(s.marketOutlook)}`}>
              {outlookLabel(s.marketOutlook)}
            </span>
```

- [ ] **Step 4: Run the unit test to verify it passes**

```bash
cd frontend
npx vitest run src/components/options/StrategySelector.test.tsx
```

Expected: 4 tests pass.

- [ ] **Step 5: Make `STRAT-003` assert something and be non-vacuous**

In `e2e/tests/regression/strategies.spec.ts`, replace the `STRAT-003` body. Keep the `scenario(...)` helper and the existing file conventions:

```ts
  test(scenario('STRAT-003', 'outlook labels are shown'), async ({ page }) => {
    const strategyCards = page.locator('.strategy-card')
    await expect(strategyCards).toHaveCount(6)

    const labels = await page.locator('.strategy-card__outlook').allTextContents()
    expect(labels.length).toBe(6)
    for (const label of labels) {
      expect(['Bullish', 'Bearish', 'Neutral']).toContain(label.trim())
    }
  })
```

The `toHaveCount(6)` is the important addition — it makes the empty-list case fail loudly instead of passing vacuously.

- [ ] **Step 6: Verify the spec file parses and the assertion list matches the registry**

Cross-check against `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyRegistry.kt`:

- BULL_CALL_SPREAD → `Moderately Bullish` → `Bullish`
- BEAR_PUT_SPREAD → `Moderately Bearish` → `Bearish`
- BULL_PUT_SPREAD → `Moderately Bullish (Income Strategy)` → `Bullish`
- BEAR_CALL_SPREAD → `Moderately Bearish (Income Strategy)` → `Bearish`
- IRON_CONDOR → `Neutral (Range-Bound)` → `Neutral`
- BUTTERFLY_SPREAD → `Neutral (Range-Bound)` → `Neutral`

If you prefer per-strategy assertions over the loop, replace it with six `expect` calls keyed on the card's strategy name — that is strictly stronger and acceptable here.

- [ ] **Step 7: Commit**

```bash
git add frontend/src/components/options/StrategySelector.tsx frontend/src/components/options/StrategySelector.test.tsx e2e/tests/regression/strategies.spec.ts
git commit -m "fix(options): show real outlook labels on strategy cards"
```

---

### Task 7: Close the E2E scenario gaps and strengthen weak assertions

**Why:** Spec §5.1–5.2 define `STRAT-001..008` and `TRADE-001..010`. The branch ships only `TRADE-001..008`, with spec `TRADE-007` (Iron Condor two-leg validation error) and `TRADE-009` (break-even points on the chart) having **no counterpart**, and `STRAT-002`, `STRAT-007`, `STRAT-008`, `TRADE-002` asserting only that a panel is visible rather than the content the spec requires.

**Files:**
- Modify: `e2e/tests/regression/strategies.spec.ts`
- Modify: `e2e/tests/regression/options-trading.spec.ts`

**Interfaces:**
- Consumes: `scenario(id, title)` from `e2e/support/scenario`; `@regression` tag; the authenticated `beforeEach` already in both files.
- Produces: `TRADE-007` and `TRADE-009`; strengthened `STRAT-002`, `STRAT-007`, `STRAT-008`, `TRADE-002`.

- [ ] **Step 1: Add the two missing trading scenarios**

Append to `e2e/tests/regression/options-trading.spec.ts` (insert them in numeric order; keep the file's existing conditional-skip guard for environments where the chain cannot load):

```ts
  test(scenario('TRADE-007', 'iron condor with too few legs shows a validation error'), async ({ page }) => {
    await selectStrategy(page, 'Iron Condor')
    // add only two of the four required legs
    await addFirstAvailableLeg(page)
    await addFirstAvailableLeg(page)
    await page.getByRole('button', { name: /Calculate P&L/i }).click()
    await expect(page.getByText(/requires exactly 4 legs/i)).toBeVisible()
  })

  test(scenario('TRADE-009', 'break-even prices are shown on the chart'), async ({ page }) => {
    await selectStrategy(page, 'Bull Call Spread')
    await addFirstAvailableLeg(page)
    await addFirstAvailableLeg(page)
    await page.getByRole('button', { name: /Calculate P&L/i }).click()
    await expect(page.locator('.pnl-chart__breakeven').first()).toBeVisible()
  })
```

`selectStrategy`, `addFirstAvailableLeg` and the `.pnl-chart__breakeven` selector do not exist yet:

- Add the two helpers as local functions in the spec file, built from the same locators the existing `TRADE-001..008` tests already use (read them and extract, do not reinvent).
- `TRADE-007` depends on the validator returning the message required by Task 9 — if Task 9 lands after this one, expect this test to fail until then. Land Task 9 first or accept a red test between commits.
- `TRADE-009` needs no chart change: `PnlChart.tsx` **already renders** break-even markers — circles on the zero line (`PnlChart.tsx:94-98`) and a text block with the `pnl-chart__breakeven` class (`:102-109`). The test verifies existing behaviour; the richer dollar-axis work is Task 13.

- [ ] **Step 2: Run the two new tests against the environment**

```bash
cd e2e
BASE_URL=https://uatportfolio.nanobyte.ca npx playwright test --grep "TRADE-007|TRADE-009"
```

Expected: both pass once Task 9 and the `pnl-chart__breakeven` class are in place. If the chain cannot load in that environment the existing conditional skip applies — record that outcome rather than deleting the test.

- [ ] **Step 3: Strengthen the four weak assertions**

`STRAT-002` — assert content, not just panel visibility:

```ts
  test(scenario('STRAT-002', 'strategy education display'), async ({ page }) => {
    await expect(page.locator('.strategy-card').first()).toBeVisible()
    await page.locator('.strategy-card').first().click()
    const edu = page.locator('.strategy-edu-card')
    await expect(edu).toBeVisible()
    await expect(edu.getByText('When to Use')).toBeVisible()
    await expect(edu.getByText('Risk Explanation')).toBeVisible()
    await expect(edu.getByText('Key Characteristics')).toBeVisible()
    await expect(edu.locator('li').first()).not.toBeEmpty()
  })
```

`STRAT-007` — assert the leg template actually changed:

```ts
  test(scenario('STRAT-007', 'strategy selection updates education and leg template'), async ({ page }) => {
    await page.locator('.strategy-card').first().click()
    await expect(page.locator('.strategy-card--selected')).toHaveCount(1)
    await expect(page.locator('.strategy-edu-card')).toBeVisible()
    await page.locator('.strategy-card').nth(4).click()   // Iron Condor, 4 legs
    await expect(page.locator('.strategy-card--selected')).toHaveCount(1)
    await expect(page.locator('.strategy-edu-card')).toBeVisible()
  })
```

If the page exposes the selected strategy's leg count in the DOM (check `OptionsPage.tsx`), assert that number instead of relying on card index — the index is brittle if strategy order changes.

`STRAT-008` — assert non-empty text, not headings:

```ts
    for (const card of await cards.all()) {
      await card.click()
      const edu = page.locator('.strategy-edu-card')
      await expect(edu).toBeVisible()
      const text = await edu.innerText()
      expect(text).toContain('When to Use')
      expect(text.length).toBeGreaterThan(80)
    }
```

The length guard is deliberately loose; tighten it once you see real content lengths.

`TRADE-002` — assert the badges, per spec ("LegBuilder shows correct badges"):

```ts
    await expect(page.locator('.leg-builder__badge--buy, .leg-builder__badge--sell')).toHaveCount(1)
    await expect(page.locator('.leg-builder__badge--call, .leg-builder__badge--put')).toHaveCount(1)
```

- [ ] **Step 4: Run the full regression suite**

```bash
cd e2e
BASE_URL=https://uatportfolio.nanobyte.ca npx playwright test --grep @regression
```

Expected: no failures that are not pre-existing environment skips. Record the pass/skip counts in the commit message — “all E2E tests pass” is only meaningful with the skip count stated.

- [ ] **Step 5: Commit**

```bash
git add e2e/tests/regression/strategies.spec.ts e2e/tests/regression/options-trading.spec.ts frontend/src/components/options/PnlChart.tsx
git commit -m "test(e2e): cover missing strategy and trading scenarios"
```

---

### Task 8: Measure the coverage the spec demands, and document the E2E environment scope

**Why:** Success Criterion 7 requires >80% coverage on the calculator and validator; no coverage tool is configured, so the criterion is unverifiable. Separately, the regression suite runs against a **deployed** UAT environment rather than the branch artifact, which is why a red-against-branch assertion (`STRAT-003`) was reported as green. The CI limitation must be explicit rather than silently misleading.

**Files:**
- Modify: `backend/strategy/build.gradle.kts`
- Modify: `.github/workflows/ui-tests-deployed.yml` (rename only, no behaviour change)
- Modify: `docs/adr.md` (append ADR-0035)
- Test: coverage gate itself is the test

**Interfaces:**
- Produces: Gradle task `jacocoTestCoverageVerification` bound to `check`, with an 80% line floor on `com.portfolio.strategy.engine.StrategyCalculator` and `com.portfolio.strategy.engine.LegValidator`.

- [ ] **Step 1: Read the module build file**

```bash
cat backend/strategy/build.gradle.kts
```

Note the plugin block style, the Kotlin/JVM versions, and whether dependency versions come from a convention plugin or are declared inline. Match that style in the next step.

- [ ] **Step 2: Add JaCoCo and a coverage gate**

Add `jacoco` to the **existing** `plugins {}` block — a second `plugins {}` block is a Gradle error. The file currently declares five plugins (`org.springframework.boot`, `io.spring.dependency-management`, `kotlin("jvm")`, `kotlin("plugin.spring")`, `kotlin("plugin.jpa")`); append one line:

```kotlin
plugins {
    // ...existing five entries stay exactly as they are...
    jacoco
}
```

Then append at the end of the file (there is currently no `tasks.test` block — the suite already runs under the JUnit Platform in CI; this block makes that explicit and attaches the report):

```kotlin
jacoco {
    toolVersion = "0.8.12"
}

tasks.test {
    useJUnitPlatform()
    finalizedBy(tasks.jacocoTestReport)
}

tasks.jacocoTestReport {
    dependsOn(tasks.test)
    reports {
        xml.required.set(true)
        html.required.set(true)
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test)
    violationRules {
        rule {
            element = "CLASS"
            includes = listOf(
                "com.portfolio.strategy.engine.StrategyCalculator",
                "com.portfolio.strategy.engine.LegValidator"
            )
            limit {
                counter = "LINE"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(tasks.jacocoTestCoverageVerification)
}
```

- [ ] **Step 3: Run the gate and see where the truth lies**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew check --console=plain
```

If it fails, open `build/reports/jacoco/test/html/index.html`, read the per-class line coverage, and add targeted tests for the uncovered branches. Do **not** lower the threshold to make it pass — the spec's number is the requirement. The most likely uncovered branches are the `StrikeOffset.STOCK` path and the zero-spot divide guards.

- [ ] **Step 4: Confirm the gate fails when it should**

Temporarily comment out one test method in `StrategyCalculatorTest.kt`, re-run `./gradlew check`, and confirm the build fails with a coverage violation. Restore the test. This proves the gate is wired rather than decorative.

- [ ] **Step 5: Make the E2E environment scope explicit**

Rename the regression job and workflow so a green tick cannot be misread as "the branch is verified":

- In `.github/workflows/ui-tests-deployed.yml`, change the workflow `name:` to `UI Tests — UAT (Deployed)` and the regression job's `name:` to `Regression Tests (deployed UAT)`.
- Do not change triggers, `BASE_URL`, or the `--grep` filters — behaviour stays identical.

- [ ] **Step 6: Record the limitation as ADR-0035**

Append to `docs/adr.md`:

```markdown
## ADR-0035: Regression UI tests target deployed UAT, not the PR artifact

### Status
Accepted

### Context
`.github/workflows/ui-tests-deployed.yml` runs `npx playwright test --grep @regression`
against `https://uatportfolio.nanobyte.ca`. The environment under test is therefore
whatever image UAT currently runs, which is not guaranteed to be the PR's build. A PR
can show a green `UI Tests — PR` check while the specs assert behaviour that the PR's
own code does not have (this happened: an outlook-label assertion was green while the
branch rendered a different string).

### Decision
Keep the deployed-UAT model for now, but make its scope explicit: the workflow and job
are renamed `UI Tests — UAT (Deployed)` / `Regression Tests (deployed UAT)`. The
documentation and the workflow name must not imply the PR artifact is verified.
Specs must additionally avoid vacuous passes — assertions that iterate over a collection
must first assert the collection is non-empty.

### Consequences
Green UI checks mean "the deployed UAT environment satisfies these specs", not "this
branch satisfies these specs". Closing that gap (a PR-scoped preview deployment) is
deliberately out of scope and remains open work.
```

- [ ] **Step 7: Commit**

```bash
git add backend/strategy/build.gradle.kts .github/workflows/ui-tests-deployed.yml docs/adr.md
git commit -m "build(strategy): enforce coverage gate and clarify e2e scope"
```

> **Reminder:** this task touches `.github/workflows/` and therefore requires the ADR above in the same commit.

---

## Phase 3 — Backend spec gaps and Greeks

### Task 9: Validate leg count against the strategy definition

**Why:** Spec §1.5 requires "For other spreads: leg count must match strategy definition's `legCount`". Only butterfly-specific checks exist, so an Iron Condor with two legs is accepted by the validator and only fails later (or produces nonsense P&L).

**Files:**
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt`
- Test: `backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/LegValidatorTest.kt` (append)

**Interfaces:**
- Consumes: `StrategyRegistry.getDefinition(type).legCount`.
- Produces: `class LegValidator(private val registry: StrategyRegistry)` — constructor changes from no-arg to one-arg. Error message format: `"<Display Name> requires exactly <n> legs"`.
- Note: `Task 7`'s `TRADE-007` asserts `/requires exactly 4 legs/i`, which this message satisfies.

- [ ] **Step 1: Read the current validator**

```bash
cat backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt
```

Note the `validate(legs: List<Leg>, strategyType: StrategyType?)` signature and where the empty-legs / duplicate / expiry checks sit, so the new check lands after them.

- [ ] **Step 2: Write the failing tests**

Append to `LegValidatorTest.kt`. The file has **no** fixture-builder helper — every test constructs `Leg(...)` inline — so add the helper below. The validator is currently constructed no-arg (`private val validator = LegValidator()`, line 10); that line changes to `LegValidator(StrategyRegistry())` with an added import of `com.portfolio.strategy.engine.StrategyRegistry` is unnecessary (same package) — only the constructor line changes:

```kotlin
    private val validator = LegValidator(StrategyRegistry())
```

```kotlin
    @Test
    fun `iron condor with two legs is rejected`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.PUT, "95", 1),
            leg(LegAction.SELL, OptionType.PUT, "100", 1)
        )
        val result = validator.validate(legs, StrategyType.IRON_CONDOR)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("requires exactly 4 legs") })
    }

    @Test
    fun `bull call spread with three legs is rejected`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 1),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("requires exactly 2 legs") })
    }

    @Test
    fun `correct leg count is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 1)
        )
        val result = validator.validate(legs, StrategyType.BULL_CALL_SPREAD)
        assertTrue(result.errors.none { it.contains("legs") })
    }
```

Use the existing inline `Leg(...)` construction style for the test bodies; add this helper next to them (the file has none today):

```kotlin
    private fun leg(action: LegAction, optionType: OptionType, strike: String, quantity: Int) = Leg(
        action = action,
        optionType = optionType,
        strike = BigDecimal(strike),
        expiry = LocalDate.of(2026, 12, 18),
        quantity = quantity,
        mid = BigDecimal("1.00")
    )
```

Note: existing tests call `validator.validate(legs)` with one argument, so `validate` already has a default second parameter — the two-arg calls above are valid.

- [ ] **Step 3: Run the tests to verify they fail**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests "com.portfolio.strategy.engine.LegValidatorTest" --console=plain
```

Expected: FAIL — the two wrong-count cases report `valid == true`.

- [ ] **Step 4: Inject the registry and add the check**

```kotlin
@Component
class LegValidator(private val registry: StrategyRegistry) {
```

Inside `validate`, after the existing structural checks and only when `strategyType != null`:

```kotlin
        if (strategyType != null) {
            val definition = registry.getDefinition(strategyType)
            if (legs.size != definition.legCount) {
                errors.add("${definition.displayName} requires exactly ${definition.legCount} legs")
            }
        }
```

Guard the butterfly block so it does not double-report the count (the butterfly block already reports `"Butterfly Spread requires exactly 3 legs"` for the same condition). Either delete the butterfly count check and rely on this generic one, or skip the generic check when `strategyType == StrategyType.BUTTERFLY_SPREAD`. Prefer the former — one message, one source of truth — and keep the butterfly-specific *quantity/type/order* checks.

- [ ] **Step 5: Run the tests to verify they pass**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --console=plain
```

Expected: `BUILD SUCCESSFUL`, all tests pass. If the constructor change breaks other call sites, the compiler will list them — fix each by passing `StrategyRegistry()`.

- [ ] **Step 6: Commit**

```bash
git add backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt \
        backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/LegValidatorTest.kt
git commit -m "feat(strategy): validate leg count against strategy definition"
```

---

### Task 10: Require strike ordering on the Butterfly and pin the option-type rule

**Why:** Spec §1.5 says the Butterfly must be "all calls" with the "middle strike quantity 2x the outer strikes". The implementation accepts a mix of calls and puts as long as they match each other, and never checks that the sold strike sits between the two bought strikes — so a malformed butterfly (short the lowest strike) passes validation and produces a wrong payoff curve.

**Files:**
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt`
- Modify: `docs/superpowers/specs/2026-09-21-strategy-production-ready-design.md` (§1.5 — see Task 11; if Task 11 lands first, just conform the code to the amended text)
- Test: `backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/LegValidatorTest.kt` (append)

**Interfaces:**
- Produces: two new error strings — `"Butterfly Spread requires the SELL strike to sit between the two BUY strikes"` and, only if you keep it call-only, the existing all-calls message.

**Decision required before starting:** the implemented rule (all legs the same option type, calls *or* puts) is more general than the spec and is itself correct — put butterflies are legitimate strategies. The recommendation is to **keep the implemented rule and amend the spec** (Task 11), then add the missing strike-ordering check here. If you would rather be strictly spec-compliant, replace the same-type check with a calls-only check instead.

- [ ] **Step 1: Write the failing tests**

```kotlin
    @Test
    fun `butterfly with sell strike below both buys is rejected`() {
        val legs = listOf(
            leg(LegAction.SELL, OptionType.CALL, "95", 2),
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertFalse(result.valid)
        assertTrue(result.errors.any { it.contains("between the two BUY strikes") })
    }

    @Test
    fun `butterfly with correctly ordered strikes is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.CALL, "100", 1),
            leg(LegAction.SELL, OptionType.CALL, "105", 2),
            leg(LegAction.BUY, OptionType.CALL, "110", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertTrue(result.valid, "unexpected errors: ${result.errors}")
    }

    @Test
    fun `bearish put butterfly is accepted`() {
        val legs = listOf(
            leg(LegAction.BUY, OptionType.PUT, "100", 1),
            leg(LegAction.SELL, OptionType.PUT, "95", 2),
            leg(LegAction.BUY, OptionType.PUT, "90", 1)
        )
        val result = validator.validate(legs, StrategyType.BUTTERFLY_SPREAD)
        assertTrue(result.valid, "unexpected errors: ${result.errors}")
    }
```

The third test encodes the recommendation. Delete it if you chose the calls-only path in the decision above.

- [ ] **Step 2: Run the tests to verify they fail**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests "com.portfolio.strategy.engine.LegValidatorTest" --console=plain
```

Expected: the ordering test fails (`valid == true`).

- [ ] **Step 3: Add the strike-ordering check**

Inside the `BUTTERFLY_SPREAD` branch, after the quantity checks:

```kotlin
            val sellStrike = sellLegs.firstOrNull()?.strike
            val buyStrikes = buyLegs.map { it.strike }
            if (sellStrike != null && buyStrikes.size == 2) {
                val low = buyStrikes.minOrNull()!!
                val high = buyStrikes.maxOrNull()!!
                if (sellStrike <= low || sellStrike >= high) {
                    errors.add("Butterfly Spread requires the SELL strike to sit between the two BUY strikes")
                }
            }
```

This works for both call and put butterflies: for a call butterfly the sold strike is above both buys on a rising ladder (low < sell < high is satisfied by `100 < 105 < 110`), and for a put butterfly by `90 < 95 < 100`. Verify both with the tests rather than reasoning alone.

- [ ] **Step 4: Run the tests to verify they pass**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --console=plain
```

Expected: `BUILD SUCCESSFUL`, all tests pass including the three new ones.

- [ ] **Step 5: Commit**

```bash
git add backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt \
        backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/LegValidatorTest.kt
git commit -m "feat(strategy): require ordered strikes on butterfly spreads"
```

---

### Task 11: Compute gamma, theta and vega with Black-Scholes

**Why:** Spec §1.4 requires fixing the Greeks via `common/math/BlackScholes.kt` (already present, unused by strategy) following the market-data `GreeksCalculator` pattern. Today theta and vega are hardcoded `BigDecimal.ZERO`, gamma is a `delta / (spot × 0.01)` heuristic with a TODO, and `StrategyCalculatorTest.kt` codifies the gap with a test named `theta and vega are zero (not yet implemented)`. The review bot papered over the requirement by adding a user-facing disclaimer in `EducationEngine` instead of computing them.

**Files:**
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyCalculator.kt:111-132`
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/EducationEngine.kt:21`
- Test: `backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyCalculatorTest.kt:287`

**Interfaces:**
- Consumes: `com.portfolio.common.math.BlackScholes.{delta,gamma,theta,vega}`, `com.portfolio.common.domain.{OptionType, Greeks, GreeksSource}` (all already on the strategy module's classpath — `StrategyDefinition` already imports `com.portfolio.common.domain.OptionType`).
- Produces: `class StrategyCalculator(@Value("\${risk-free-rate:0.05}") private val riskFreeRate: Double = 0.05)` — the default keeps the existing no-arg test construction compiling. The class annotation stays **`@Component`** (it is not `@Service` today; do not change it).
- Produces: non-zero `NetGreeks.theta` / `NetGreeks.vega` for legs with future expiry.

**Known limitation to state in the code, not hide:** `Leg` carries no implied volatility, so Greeks are computed at a flat 20% IV — the same fallback `market-data`'s `GreeksCalculator` uses (`iv ?: 0.20`). Wiring real IV requires exposing `impliedVolatility` from the options-chain DTO, which is out of scope here.

**Weighting convention — read before writing:** the existing `calculateNetGreeks` (`StrategyCalculator.kt:111-132`) weights option-leg delta by **sign only** (`netDelta += leg.delta * multiplier`, no quantity, no ÷100); only stock legs get `quantity / 100`. The new gamma/theta/vega accumulation must follow the **same option-leg convention** (sign only) so the four greeks stay mutually consistent. Adding quantity weighting for option legs would be a behaviour change to delta's semantics — out of scope here; note it as a follow-up instead.

- [ ] **Step 1: Read the current implementation**

```bash
sed -n '100,133p' backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyCalculator.kt
```

`BlackScholes` signatures, from `backend/common/src/main/kotlin/com/portfolio/common/math/BlackScholes.kt` and its use in market-data:

```kotlin
BlackScholes.delta(spot: BigDecimal, strike: BigDecimal, tte: Double, r: Double, sigma: Double, optionType: OptionType)
BlackScholes.gamma(spot: BigDecimal, strike: BigDecimal, tte: Double, r: Double, sigma: Double)
BlackScholes.theta(spot: BigDecimal, strike: BigDecimal, tte: Double, r: Double, sigma: Double, optionType: OptionType)
BlackScholes.vega(spot: BigDecimal, strike: BigDecimal, tte: Double, r: Double, sigma: Double)
```

- [ ] **Step 2: Check for tests that encode the gamma heuristic**

```bash
grep -n "gamma" backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyCalculatorTest.kt
```

If any test asserts the heuristic value (`delta / (spot × 0.01)`), it will break in Step 6 — rewrite it in the same step to assert the Black-Scholes value instead. The known test to delete is `theta and vega are zero (not yet implemented)` (`:286-295`).

- [ ] **Step 3: Replace the "not yet implemented" test with real expectations**

In `StrategyCalculatorTest.kt`, delete `theta and vega are zero (not yet implemented)` and add (the file uses `@BeforeEach` + `lateinit var calculator` with `calculator = StrategyCalculator()` — the default constructor argument keeps that working; match the file's positional `Leg(...)` style):

```kotlin
    @Test
    fun `theta and vega are computed from black scholes`() {
        val legs = listOf(
            Leg(LegAction.BUY, OptionType.CALL, BigDecimal("100"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3.00"), delta = BigDecimal("0.52"))
        )
        val result = calculator.calculate(legs, BigDecimal("100"))

        assertTrue(result.netGreeks.theta.compareTo(BigDecimal.ZERO) < 0,
            "long option theta should be negative, was ${result.netGreeks.theta}")
        assertTrue(result.netGreeks.vega.compareTo(BigDecimal.ZERO) > 0,
            "long option vega should be positive, was ${result.netGreeks.vega}")
        assertTrue(result.netGreeks.gamma.compareTo(BigDecimal.ZERO) > 0,
            "long option gamma should be positive, was ${result.netGreeks.gamma}")
    }

    @Test
    fun `short leg flips theta and vega signs`() {
        val legs = listOf(
            Leg(LegAction.SELL, OptionType.CALL, BigDecimal("100"), LocalDate.now().plusDays(30), 1,
                mid = BigDecimal("3.00"), delta = BigDecimal("0.52"))
        )
        val result = calculator.calculate(legs, BigDecimal("100"))

        assertTrue(result.netGreeks.theta.compareTo(BigDecimal.ZERO) > 0,
            "short option theta should be positive, was ${result.netGreeks.theta}")
        assertTrue(result.netGreeks.vega.compareTo(BigDecimal.ZERO) < 0,
            "short option vega should be negative, was ${result.netGreeks.vega}")
    }
```

The sign conventions are the assertion that matters — they catch an accumulation that forgets to negate short legs.

- [ ] **Step 4: Run the tests to verify they fail**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --tests "com.portfolio.strategy.engine.StrategyCalculatorTest" --console=plain
```

Expected: FAIL — theta and vega are always zero, so the sign assertions fail.

- [ ] **Step 5: Implement the Greeks**

Add the constructor parameter, the IV constant, and the per-leg helper. Keep the `@Component` annotation:

```kotlin
@Component
class StrategyCalculator(
    @Value("\${risk-free-rate:0.05}") private val riskFreeRate: Double = 0.05
) {
    private companion object {
        /** Flat volatility fallback, matching market-data's GreeksCalculator (`iv ?: 0.20`). */
        const val DEFAULT_IV = 0.20
    }

    private fun legGreeks(leg: Leg, spot: BigDecimal): Greeks? {
        val expiry = leg.expiry ?: return null
        val optionType = leg.optionType ?: return null
        if (spot <= BigDecimal.ZERO) return null
        val tte = ChronoUnit.DAYS.between(LocalDate.now(), expiry) / 365.0
        if (tte <= 0.0) return null

        return Greeks(
            delta = BlackScholes.delta(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV, optionType),
            gamma = BlackScholes.gamma(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV),
            theta = BlackScholes.theta(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV, optionType),
            vega = BlackScholes.vega(spot, leg.strike, tte, riskFreeRate, DEFAULT_IV),
            rho = BigDecimal.ZERO,
            source = GreeksSource.BLACK_SCHOLES
        )
    }
```

Now rewrite `calculateNetGreeks`. Keep the existing delta loop **exactly as it is** (including its stock-leg branch) and add the three new accumulations with the same sign-only multiplier:

```kotlin
        var netGamma = BigDecimal.ZERO
        var netTheta = BigDecimal.ZERO
        var netVega = BigDecimal.ZERO

        legs.forEach { leg ->
            val multiplier = when (leg.action) {
                LegAction.BUY -> BigDecimal.ONE
                LegAction.SELL -> BigDecimal.ONE.negate()
            }
            val greeks = legGreeks(leg, spotPrice) ?: return@forEach
            netGamma += greeks.gamma * multiplier
            netTheta += greeks.theta * multiplier
            netVega += greeks.vega * multiplier
        }
```

Delete the `// Approximate gamma as delta / (spotPrice * 0.01)` block and the two `TODO` comments, and return all four:

```kotlin
        return NetGreeks(
            netDelta.setScale(SCALE, RoundingMode.HALF_UP),
            netGamma.setScale(SCALE, RoundingMode.HALF_UP),
            netTheta.setScale(SCALE, RoundingMode.HALF_UP),
            netVega.setScale(SCALE, RoundingMode.HALF_UP)
        )
```

Add imports: `com.portfolio.common.domain.Greeks`, `com.portfolio.common.domain.GreeksSource`, `com.portfolio.common.math.BlackScholes`, `org.springframework.beans.factory.annotation.Value`, `java.time.LocalDate`, `java.time.temporal.ChronoUnit`. The current import block is only seven lines (`OptionType`, `com.portfolio.strategy.model.*`, `Component`, `BigDecimal`, `RoundingMode`).

Keep the existing `netDelta` computation untouched — do not recompute delta from Black-Scholes, because the chain already supplies a more accurate per-leg delta and changing it would alter the P&L curve.

- [ ] **Step 6: Update the education disclaimer**

In `EducationEngine.kt:21`, the disclaimer added by the review bot is now misleading (theta and vega are computed, not placeholders). Reword it to state the real remaining limitation:

```kotlin
        warnings.add("Greeks assume a flat 20% implied volatility until volatility-surface data is available")
```

- [ ] **Step 7: Run the full suite**

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --console=plain
```

Expected: `BUILD SUCCESSFUL`, 0 failures. Run again after Task 8's coverage gate lands — the new `legGreeks` null-guard branches must be covered to keep the 80% floor.

- [ ] **Step 8: Commit**

```bash
git add backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/StrategyCalculator.kt \
        backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/EducationEngine.kt \
        backend/strategy/src/test/kotlin/com/portfolio/strategy/engine/StrategyCalculatorTest.kt
git commit -m "feat(strategy): compute theta and vega with black scholes"
```

---

## Phase 4 — Spec amendments and low-severity polish

### Task 12: Amend the spec to match ratified decisions

**Why:** Several deviations are cases where the implementation is defensible but the spec says otherwise. Leaving the text stale guarantees the same review findings recur. Each amendment below records a decision; the two that need an explicit product call are marked.

**Files:**
- Modify: `docs/superpowers/specs/2026-09-21-strategy-production-ready-design.md`

**Decision required:** item 4 (sequential fallback) and item 7 (streaming model). Apply the recommended text unless the alternative is chosen; if the alternative is chosen, the corresponding *code* task must be added instead.

- [ ] **Step 1: Fix the enum-count typo (§1.1)**

Replace:

```markdown
Resulting enum (5 values):
```

with:

```markdown
Resulting enum (6 values):
```

Do **not** change the enum in code — 6 is correct and `STRAT-001` asserts 6.

- [ ] **Step 2: Correct the dollar-value formula (§1.4)**

Replace the three inline comments under "New DTO fields in `CalculateResponse`:" with the implemented semantics:

```kotlin
val maxProfitDollars: BigDecimal   // maxProfit * 100; maxProfit is already position-level
val maxLossDollars: BigDecimal     // maxLoss * 100 (positive value = max loss amount)
val netDebitCreditDollars: BigDecimal  // netDebitCredit * 100
```

Add one sentence below the block:

```markdown
Quantity is applied per leg when the payoff curve is built (`leg.mid * leg.quantity`,
and the same for intrinsic value), not as a single top-level multiplier. This is
required for asymmetric structures such as the Butterfly, whose middle leg carries
quantity 2. Scaling an entire position (e.g. five butterflies) must be expressed by
editing each leg.
```

- [ ] **Step 3: Record the Butterfly option-type and ordering rule (§1.5)**

Replace:

```markdown
- For BUTTERFLY_SPREAD: exactly 3 legs required, all calls, middle strike quantity must be 2x the outer strikes
```

with:

```markdown
- For BUTTERFLY_SPREAD: exactly 3 legs required; all legs must share one option type
  (all calls or all puts — a put butterfly is a valid bearish structure); the SELL leg
  must carry quantity 2 and the two BUY legs quantity 1 each; the SELL strike must sit
  strictly between the two BUY strikes
```

- [ ] **Step 4: Ratify the atomic-only multi-leg policy (§2.2 and Risk Mitigation)**

**Recommended** — replace the entire default-implementation code block in §2.2 (which shows the sequential `placeOrder` loop) with:

```kotlin
fun placeMultiLegOrder(
    credentials: BrokerCredentials,
    accountId: String,
    request: MultiLegOrderRequest
): OrderResult {
    return OrderResult(null, OrderStatus.REJECTED,
        "Atomic multi-leg orders are not supported by ${brokerType.name}")
}
```

and add:

```markdown
Rationale: a multi-leg strategy executed as independent single-leg orders has leg risk —
if any leg fails or fills at a different price, the account is left with an unbalanced
position that no longer matches the calculated payoff. Rejecting is safer than degrading.
Only adapters that can submit an atomic combo order (Questrade) support this path.
```

Then replace the Risk Mitigation row:

```markdown
| Questrade combo order API format changes | Abstracted behind `BrokerAdapter`; unsupported or failing adapters reject the order rather than degrading to sequential single-leg execution |
```

If instead the fallback is wanted, do **not** apply this step — add a task to restore the `placeOrder` loop from `git show fe3e3f4` and keep the original spec text.

- [ ] **Step 5: Correct the combo endpoint location and route (§2.4)**

Replace the §2.4 heading text and snippet to describe what shipped:

```markdown
**File:** `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/ComboOrderController.kt`

Route: `POST /api/v1/gateway/connections/{connectionId}/accounts/{accountId}/combo-orders`

`connectionId` and `accountId` are both path variables because credential resolution and
account scoping are per-connection; the originally specified bare `/combo` route on
`OrderController` could not carry them.
```

- [ ] **Step 6: Record the streaming model that actually exists (§4.1–4.2)**

**Recommended** — replace §4.1's method list and message-format block with:

```markdown
Options streaming already exists in the market-data service and was **not** part of this
work: `OptionStreamingService.startStreaming(symbol, expiry, strike, optionType)` /
`stopStreaming(...)` are reference-counted, and `QuoteWebSocketHandler` broadcasts
normalized `option_quote` messages that the frontend's `useMarketDataWebSocket` hook
consumes via `batchUpdateChainQuotes`.

Consequently there is no `option_tick` message type and no conId-keyed subscription API.
The remaining frontend work is consuming this existing stream in the leg builder
(live mid prices) and flashing price changes in the chain table.
```

And replace §4.2's `subscribeOptionChain` / `unsubscribeOptionChain` methods and the conId-keyed return value with:

```markdown
Live leg-builder prices are derived directly from the chain store — a pure helper,
`derivePriceFor(chains, underlying)` in `frontend/src/hooks/liveMids.ts`, resolves each
leg's quote using the same strike-key normalization as `batchUpdateChainQuotes` and
prefers the stored `mid`, falling back to the bid/ask midpoint. The unused
`useOptionStreaming` hook was deleted: its per-contract subscription logic duplicated
the page-level chain subscription. Contract-level batch subscription by conId is not
required by the shipped design.
```

If instead the `option_tick` pipeline is wanted, do **not** apply this step — add a task for `backend/market-data` (`startOptionStreaming`/`stopOptionStreaming`, a new `option_tick` broadcast) plus the conId-keyed frontend hook, and leave the spec as-is.

- [ ] **Step 7: Record the E2E scenario-ID mapping (§5.2) and the E2E scope**

Replace the §5.2 table's `TRADE-007` / `TRADE-008` / `TRADE-009` / `TRADE-010` rows with the shipped IDs, and add a note:

```markdown
TRADE-007 (Iron Condor with too few legs) and TRADE-009 (break-even markers) were added
after the initial implementation. The suggest-endpoint scenario is TRADE-008, not
TRADE-010 as first drafted. See ADR-0035 for the deployed-UAT scope of this suite.
```

- [ ] **Step 8: Drop the unused store field and record the trade DTO's lenient typing (§3.5, §1.6)**

Remove `selectedStrategyOutlook` from the §3.5 list (it was never implemented and nothing consumes it; adding dead state would be worse than amending the text). Add below the `TradeRequest` snippet:

```markdown
`strategyType` is accepted as a `String` and parsed server-side, so an unknown value
yields a 400 with a clear message rather than a Jackson deserialization failure.
Each leg must also carry `symbol` — the combo-order path needs it to resolve Questrade
symbolIds, and a leg without one is rejected before any broker call.
```

- [ ] **Step 9: Commit**

```bash
git add docs/superpowers/specs/2026-09-21-strategy-production-ready-design.md
git commit -m "docs(spec): reconcile strategy design with shipped behaviour"
```

---

### Task 13: Low-severity UI and API polish

**Why:** A bundle of small, independent deviations: the trade confirmation omits the limit price the spec requires, the dollar-value metrics display only the first leg's quantity, `PnlChart` lacks the spec'd break-even annotations and dollar axis, and per-leg status is reported as `REJECTED` even when the call never reached the broker.

**Files:**
- Modify: `frontend/src/components/options/OneClickTradeButton.tsx`
- Modify: `frontend/src/pages/OptionsPage.tsx`
- Modify: `frontend/src/components/options/PnlChart.tsx` (+ `.css`)
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/api/controller/StrategyController.kt`
- Modify: `backend/strategy/src/main/kotlin/com/portfolio/strategy/engine/LegValidator.kt` (only if you choose to fix the Clear All side effect — see Step 6)
- Test: `frontend/src/components/options/OneClickTradeButton.test.tsx` (new)

**Interfaces:**
- Consumes: `TradeRequest.limitPrice` (already computed in `OneClickTradeButton`), `CalculationResult.breakEvens`, `legs` from the store.
- Produces: honest `LegResult.status` values — `SUBMITTED` / `REJECTED` / `UNKNOWN`.

- [ ] **Step 1: Write the failing test for the confirmation text**

`OneClickTradeButton` reads four sources — `useStrategyStore`, `useBrokerConnection`, `useQuoteStore`, `useToast` — so the test must mock all of them at module level (the `OptionsChainTable.test.tsx` pattern, extended):

```tsx
import { describe, it, expect, vi, beforeEach } from 'vitest'
import { render, screen, fireEvent } from '@testing-library/react'
import { OneClickTradeButton } from './OneClickTradeButton'

const mockConnection = { connected: true, connectionId: 1, accountNumber: '123' }
vi.mock('@/hooks/useBrokerConnection', () => ({
  useBrokerConnection: () => ({ connection: mockConnection, loading: false }),
}))
vi.mock('@/stores/quoteStore', () => ({
  useQuoteStore: (selector: (s: unknown) => unknown) =>
    selector({ selectedUnderlying: 'SPY', quotes: { SPY: { last: 100 } } }),
}))
vi.mock('@/stores/toastStore', () => ({
  useToast: () => ({ success: vi.fn(), error: vi.fn(), warning: vi.fn() }),
}))
vi.mock('@/stores/strategyStore', () => ({
  useStrategyStore: (selector: (s: unknown) => unknown) =>
    selector({
      legs: [{ action: 'BUY', optionType: 'CALL', strike: 100, expiry: '2026-12-18', quantity: 1, price: 2.5, symbol: 'SPY' }],
      selectedStrategy: 'BULL_CALL_SPREAD',
      tradeInProgress: false,
      setTradeInProgress: vi.fn(),
      connectionStatus: { connected: true, brokerType: 'questrade' },
    }),
}))

describe('OneClickTradeButton', () => {
  beforeEach(() => {
    vi.spyOn(window, 'confirm').mockReturnValue(false)
  })

  it('shows the limit price in the confirmation', () => {
    render(<OneClickTradeButton />)
    fireEvent.click(screen.getByRole('button', { name: /trade on questrade/i }))
    expect(window.confirm).toHaveBeenCalledWith(expect.stringContaining('$'))
  })
})
```

Adjust the mocked store shapes to whatever the component actually destructures — read lines 10–20 of the component first (it destructures `legs, selectedStrategy, tradeInProgress, setTradeInProgress, connectionStatus` from the strategy store and reads `selectedUnderlying`/`quotes` from the quote store).

- [ ] **Step 2: Run it to verify it fails**

```bash
cd frontend
npx vitest run src/components/options/OneClickTradeButton.test.tsx
```

Expected: FAIL — the confirm string contains no `$`.

- [ ] **Step 3: Include the limit price in the confirmation**

In `OneClickTradeButton.tsx`, compute the limit price before the confirm call (it is currently computed inside the `try` block after confirmation) and include it:

```tsx
    const limitPrice = legs.reduce((sum, l) => {
      if (l.action === 'BUY') return sum + (l.price ?? 0)
      return sum - (l.price ?? 0)
    }, 0)

    const confirmed = window.confirm(
      `Submit ${legs.length}-leg ${selectedStrategy.replace(/_/g, ' ').toLowerCase()} order ` +
      `at a limit of $${Math.abs(limitPrice).toFixed(2)} to Questrade?`
    )
```

Remove the now-duplicated `limitPrice` computation from inside `try` and keep using the same variable in the request body.

- [ ] **Step 4: Pass the strategy's total quantity to the metrics**

`DollarValuePnlMetrics` takes six props — `maxProfit`, `maxLoss`, `maxProfitDollars`, `maxLossDollars`, `netDebitCreditDollars`, `quantity` — and `OptionsPage` currently passes `quantity = legs[0].quantity`. Keep every prop it already passes and change only the quantity to the sum across legs so an asymmetric Butterfly reports honestly:

```tsx
          <DollarValuePnlMetrics
            maxProfit={calcResult.maxProfit}
            maxLoss={calcResult.maxLoss}
            maxProfitDollars={calcResult.maxProfitDollars}
            maxLossDollars={calcResult.maxLossDollars}
            netDebitCreditDollars={calcResult.netDebitCreditDollars}
            quantity={legs.reduce((sum, l) => sum + l.quantity, 0)}
          />
```

Read the current render block first and preserve any prop values that differ from the sketch above — only the `quantity` line changes.

- [ ] **Step 5: Add dollar-axis labels to `PnlChart`**

Break-even markers **already exist** — circles on the zero line (`PnlChart.tsx:94-98`) and a `pnl-chart__breakeven` text block (`:102-109`) — so the only remaining §3.4 gap is the dollar axis. The chart computes its domain as `minX/maxX` from `pnlCurve` spot prices and `minY/maxY` from pnls (`:14-18`), with `toX`/`toY` mappers (`:21-22`). Add dollar tick labels along the price axis using the existing `toX` mapper:

```tsx
          {/* Price axis labels (dollars) */}
          {[minX, (minX + maxX) / 2, maxX].map((price, i) => (
            <text
              key={`axis-${i}`}
              x={toX(price)}
              y={height - 5}
              textAnchor="middle"
              fontSize="10"
              fill="var(--text-muted, #64748b)"
            >
              ${price.toFixed(0)}
            </text>
          ))}
```

Insert inside the existing `<svg>` after the break-even markers. No CSS change is needed — the labels inherit the SVG text styling. Do **not** add a second `pnl-chart__breakeven` element; Task 7's `TRADE-009` selector already resolves against the existing one.

- [ ] **Step 6: Stop mislabelling legs as rejected when the broker was never reached**

In `StrategyController.tradeStrategy`, replace:

```kotlin
                status = if (status == "SUBMITTED") "SUBMITTED" else "REJECTED"
```

with:

```kotlin
                status = when (status) {
                    "SUBMITTED" -> "SUBMITTED"
                    "REJECTED" -> "REJECTED"
                    else -> "UNKNOWN"
                }
```

so an `ERROR` (gateway unreachable) no longer claims each leg was rejected. Update `docs/reference/api-endpoints.md` if it documents the three-value status set.

- [ ] **Step 7: Run the frontend and backend suites**

```bash
cd frontend
npx vitest run
npx tsc --noEmit
```

```bash
cd backend/strategy
export JAVA_HOME=/tmp/opencode/jdk/jdk-21.0.12.1+1; export PATH=$JAVA_HOME/bin:$PATH
./gradlew test --console=plain
```

Expected: frontend 188+ tests pass with `tsc` exit 0; backend `BUILD SUCCESSFUL`.

- [ ] **Step 8: Note the two remaining descopes**

Do not silently drop these; record them in the PR description and, if a tracker exists, as issues:

- `QuoteBar` streaming wiring and `UnderlyingSearch` autocomplete (§3.4) — unchanged from `main`, not implemented.
- The left sidebar still renders education only; `StrategySelector` sits above the three-column grid rather than inside the left panel (§3.2). Moving it is a layout change the designer should own, not a mechanical edit.

- [ ] **Step 9: Commit**

```bash
git add frontend/src/components/options/OneClickTradeButton.tsx \
        frontend/src/components/options/OneClickTradeButton.test.tsx \
        frontend/src/pages/OptionsPage.tsx \
        frontend/src/components/options/PnlChart.tsx \
        frontend/src/components/options/PnlChart.css \
        backend/strategy/src/main/kotlin/com/portfolio/strategy/api/controller/StrategyController.kt
git commit -m "fix(options): confirm limit price and report honest leg status"
```

---

## Self-Review

**Spec coverage.** Every deviation in the review maps to a task: D1→T1, D5→T2, D4→T3/T4, D3→T4/T5, D6→T6/T8, D12→T7, D11→T8, D9→T9, D10→T10, D7→T11, D8→T12 step 4, D13→T13. Two §3.4 components (`QuoteBar`, `UnderlyingSearch`) and the §3.2 sidebar arrangement are explicitly descoped in T13 step 8 rather than implemented — that is a deliberate scope decision, not an oversight, and must appear in the PR description.

**Placeholder scan.** No `TBD` / "add appropriate error handling" steps. Five places instruct the implementer to read a file before editing (`LegValidator.kt`, `OptionsPage.tsx`'s LegBuilder render block, `DollarValuePnlMetrics.tsx`'s current props, `OptionsChainTable.test.tsx`'s `makeChain` factory, and the store-mock shapes in the `OneClickTradeButton` test) because the exact current values were not captured during review; each such step states precisely what to match.

**Type consistency.** `LegTemplate.quantity` (T2) is what the registry sets and the DTO exposes. `LegBuilder.liveMid` (T3) is the same prop `OptionsPage` passes from `derivePriceFor(chains, selectedUnderlying)` (T4). `strategyType`/`symbol` naming follows the existing `LegRequest`. The `TRADE-007` assertion in T7 depends on the error string produced in T9 (`"requires exactly 4 legs"`) — the wording is identical in both tasks. `TRADE-009`'s selector (`.pnl-chart__breakeven`) already exists in `PnlChart.tsx:102-109`, so T7 and T13 are independent on that axis.

**Ordering hazards.** T7's `TRADE-007` is red until T9 lands — either land T9 first or accept a red test between commits and say so in the PR. T8's coverage gate can fail once T11 adds new branches to `StrategyCalculator` — re-run `./gradlew check` after T11. T5's flash test depends on the `makeChain` override added in the same task, so it cannot be split across commits.

---

## Execution Handoff

Plan complete and saved to `docs/superpowers/plans/2026-09-22-strategy-production-ready-deviations.md`. Two execution options:

**1. Subagent-Driven (recommended)** — I dispatch a fresh subagent per task, review between tasks, fast iteration.

**2. Inline Execution** — execute tasks in this session using executing-plans, batch execution with checkpoints for review.

Which approach?
