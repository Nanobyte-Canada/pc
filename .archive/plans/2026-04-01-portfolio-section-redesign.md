# Portfolio Section Redesign — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Merge Model Portfolios, Portfolio Groups, and Portfolio Builder into a single Portfolio page with direct model-to-account application, add Rebalancing Progress and Pending Orders widgets to account detail, and apply a unified color scheme across dashboard pages.

**Architecture:** The Portfolio Group abstraction layer is removed entirely. Model portfolios link directly to broker connections via a new `model_portfolio_id` FK on `broker_connections`. New backend endpoints serve rebalance progress and pending orders per-account. The frontend merges three pages into one card-grid layout with expandable analysis panels, reusing existing dashboard widget components.

**Tech Stack:** Kotlin/Spring Boot (backend), React/TypeScript/Vite (frontend), PostgreSQL + Flyway (DB), React Query (data fetching), AG Grid/Charts (tables), CSS custom properties (styling)

**Design Spec:** `docs/superpowers/specs/2026-04-01-portfolio-section-redesign.md`
**UI Mockups:** `.superpowers/brainstorm/4009-1775013039/content/` (account-detail-v4.html, dashboard-colors-v2.html, portfolio-page-layout.html)

---

## File Structure

### Backend — New/Modified Files
| Action | File | Responsibility |
|--------|------|---------------|
| Create | `backend/src/main/resources/db/migration/V60__account_model_linking.sql` | Add model_portfolio_id to broker_connections, add rebalance columns |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt` | Add modelPortfolio relationship, accuracy, lastRebalancedAt fields |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/dto/ModelPortfolioDtos.kt` | Add ApplyToAccountsRequest, RebalanceProgressDto, PendingOrderDto, ModelAnalysisDto |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/service/ModelPortfolioService.kt` | Replace applyToGroup with applyToAccounts, add getAnalysis |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/controller/ModelPortfolioController.kt` | Replace /apply with /apply-to-accounts, add /analysis endpoint |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt` | Add /rebalance-progress and /pending-orders endpoints |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/service/RebalanceService.kt` | Add calculateTradesForAccount method |
| Modify | `backend/src/main/kotlin/com/portfolio/broker/service/DriftCalculationService.kt` | Add calculateAccountAccuracy method |

### Frontend — New Files
| Action | File | Responsibility |
|--------|------|---------------|
| Create | `frontend/src/pages/PortfolioPage.tsx` | Merged portfolio page with card grid + expandable detail |
| Create | `frontend/src/pages/PortfolioPage.css` | Styles for merged portfolio page |
| Create | `frontend/src/components/portfolios/ModelPortfolioCard.tsx` | Individual model card component |
| Create | `frontend/src/components/portfolios/ModelPortfolioCard.css` | Card styles |
| Create | `frontend/src/components/portfolios/ModelAnalysisPanel.tsx` | Expandable analysis panel reusing dashboard widgets |
| Create | `frontend/src/components/portfolios/ModelAnalysisPanel.css` | Analysis panel styles |
| Create | `frontend/src/components/portfolios/CustomPortfolioBuilder.tsx` | Inline builder with InstrumentTabs + weights |
| Create | `frontend/src/components/portfolios/CustomPortfolioBuilder.css` | Builder styles |
| Create | `frontend/src/components/portfolios/ApplyToAccountModal.tsx` | Multi-select account modal |
| Create | `frontend/src/components/portfolios/ApplyToAccountModal.css` | Modal styles |
| Create | `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.tsx` | Target vs actual progress bars |
| Create | `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.css` | Progress widget styles |
| Create | `frontend/src/components/dashboard/widgets/PendingOrdersWidget.tsx` | Model-generated pending orders |
| Create | `frontend/src/components/dashboard/widgets/PendingOrdersWidget.css` | Pending orders styles |

### Frontend — Modified Files
| Action | File | Change |
|--------|------|--------|
| Modify | `frontend/src/components/layout/AppSidebar.tsx` | Replace 3 nav items with 1 |
| Modify | `frontend/src/App.tsx` | Update routes |
| Modify | `frontend/src/services/modelPortfolioService.ts` | Replace applyModelToGroup, add new service functions |
| Modify | `frontend/src/hooks/useModelPortfolios.ts` | Replace useApplyModelToGroup, add new hooks |
| Modify | `frontend/src/types/modelPortfolio.ts` | Add new types, update ApplyModelRequest |
| Modify | `frontend/src/components/dashboard/WidgetRegistry.ts` | Register new widgets |
| Modify | `frontend/src/components/dashboard/DashboardGrid.tsx` | Add color scheme, new widget zone |
| Modify | `frontend/src/components/dashboard/DashboardGrid.css` | Widget accent color classes |
| Modify | `frontend/src/pages/AccountDetailPage.tsx` | Add model badge, new widgets row |
| Modify | `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.tsx` | Add accuracy circle, model badge, mini progress bar |
| Modify | `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.css` | Accuracy/badge styles |

### Frontend — Files to Remove
| File | Reason |
|------|--------|
| `frontend/src/pages/ModelPortfoliosPage.tsx` | Replaced by PortfolioPage |
| `frontend/src/pages/ModelPortfoliosPage.css` | Replaced by PortfolioPage.css |
| `frontend/src/pages/PortfolioGroupDetailPage.tsx` | Portfolio Groups removed |
| `frontend/src/pages/PortfolioGroupDetailPage.css` | Portfolio Groups removed |
| `frontend/src/components/portfolio-groups/*` | Portfolio Groups removed |
| `frontend/src/components/model-portfolios/*` | Replaced by portfolios/ components |
| `frontend/src/pages/PortfolioBuilderPage.tsx` (if exists) | Logic moved to CustomPortfolioBuilder |
| `frontend/src/hooks/usePortfolioGroups.ts` | Portfolio Groups removed |
| `frontend/src/services/portfolioGroupService.ts` | Portfolio Groups removed |
| `frontend/src/types/portfolioGroup.ts` | Portfolio Groups removed |

---

## Task 1: Database Migration — Account-Model Linking

**Files:**
- Create: `backend/src/main/resources/db/migration/V60__account_model_linking.sql`

- [ ] **Step 1: Create the migration file**

```sql
-- V60__account_model_linking.sql
-- Link model portfolios directly to broker connections (replacing portfolio groups)

-- Add model portfolio reference to broker connections
ALTER TABLE broker_connections ADD COLUMN model_portfolio_id BIGINT
  REFERENCES model_portfolios(id) ON DELETE SET NULL;

-- Add accuracy tracking to broker connections
ALTER TABLE broker_connections ADD COLUMN model_accuracy DECIMAL(5,2);
ALTER TABLE broker_connections ADD COLUMN last_rebalanced_at TIMESTAMP;

-- Index for quick lookup
CREATE INDEX idx_broker_connections_model ON broker_connections(model_portfolio_id);

-- Migrate rebalance_events from group_id to connection_id
ALTER TABLE rebalance_events ADD COLUMN connection_id BIGINT
  REFERENCES broker_connections(id) ON DELETE CASCADE;
CREATE INDEX idx_rebalance_events_connection ON rebalance_events(connection_id);
```

- [ ] **Step 2: Verify migration applies**

Run: `docker compose up --build -d && docker compose logs -f backend | head -100`
Expected: Flyway migration V60 applied successfully, no errors

- [ ] **Step 3: Commit**

```bash
git add backend/src/main/resources/db/migration/V60__account_model_linking.sql
git commit -m "feat: add V60 migration for account-model linking"
```

---

## Task 2: Backend Entity — Update BrokerConnection

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt`

- [ ] **Step 1: Read the current BrokerConnection entity**

Read `backend/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt` to see existing fields.

- [ ] **Step 2: Add model portfolio relationship and tracking fields**

Add these fields to the BrokerConnection entity class:

```kotlin
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "model_portfolio_id")
var modelPortfolio: ModelPortfolio? = null,

@Column(name = "model_accuracy")
var modelAccuracy: BigDecimal? = null,

@Column(name = "last_rebalanced_at")
var lastRebalancedAt: OffsetDateTime? = null,
```

Add import for `java.math.BigDecimal` if not present.

- [ ] **Step 3: Verify it compiles**

Run: `docker compose exec backend ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 4: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt
git commit -m "feat: add modelPortfolio, modelAccuracy, lastRebalancedAt to BrokerConnection"
```

---

## Task 3: Backend DTOs — New Request/Response Types

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/dto/ModelPortfolioDtos.kt`

- [ ] **Step 1: Replace ApplyModelRequest with ApplyToAccountsRequest**

Replace the existing `ApplyModelRequest` in `ModelPortfolioDtos.kt`:

```kotlin
data class ApplyToAccountsRequest(
    val connectionIds: List<Long>
)
```

- [ ] **Step 2: Add RebalanceProgressDto**

```kotlin
data class RebalanceProgressEntry(
    val symbol: String,
    val securityName: String?,
    val targetPercent: BigDecimal,
    val actualPercent: BigDecimal
)

data class RebalanceProgressDto(
    val connectionId: Long,
    val modelName: String,
    val accuracy: BigDecimal,
    val entries: List<RebalanceProgressEntry>
)
```

- [ ] **Step 3: Add PendingOrderDto**

```kotlin
data class PendingOrderDto(
    val action: String, // "BUY" or "SELL"
    val symbol: String,
    val securityName: String?,
    val units: Int,
    val price: BigDecimal,
    val amount: BigDecimal,
    val currency: String,
    val accountName: String
)

data class PendingOrdersResponse(
    val connectionId: Long,
    val orders: List<PendingOrderDto>,
    val totalAmount: BigDecimal
)
```

- [ ] **Step 4: Add ModelAnalysisDto**

```kotlin
data class ModelAnalysisDto(
    val modelId: Long,
    val sectorExposure: List<ExposureEntry>,
    val geographyExposure: List<ExposureEntry>,
    val riskScore: Int,
    val riskLevel: String,
    val holdings: List<ModelAllocationDto>
)

data class ExposureEntry(
    val name: String,
    val percentage: BigDecimal
)
```

- [ ] **Step 5: Verify it compiles**

Run: `docker compose exec backend ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 6: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/dto/ModelPortfolioDtos.kt
git commit -m "feat: add DTOs for apply-to-accounts, rebalance progress, pending orders, model analysis"
```

---

## Task 4: Backend Service — Apply Model to Accounts

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/ModelPortfolioService.kt`

- [ ] **Step 1: Write the test for applyToAccounts**

Create test file `backend/src/test/kotlin/com/portfolio/broker/service/ModelPortfolioServiceTest.kt`:

```kotlin
package com.portfolio.broker.service

import com.portfolio.broker.entity.*
import com.portfolio.broker.repository.*
import io.mockk.*
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.math.BigDecimal
import kotlin.test.assertEquals

class ModelPortfolioServiceTest {

    private val modelRepository = mockk<ModelPortfolioRepository>()
    private val allocationRepository = mockk<ModelPortfolioAllocationRepository>()
    private val connectionRepository = mockk<BrokerConnectionRepository>()
    private val service = ModelPortfolioService(
        modelRepository, allocationRepository, connectionRepository
    )

    @Test
    fun `applyToAccounts links model to multiple connections`() {
        val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        val conn1 = mockk<BrokerConnection>(relaxed = true)
        val conn2 = mockk<BrokerConnection>(relaxed = true)

        every { modelRepository.findById(1) } returns java.util.Optional.of(model)
        every { connectionRepository.findByIdAndUserId(10, 1) } returns conn1
        every { connectionRepository.findByIdAndUserId(20, 1) } returns conn2
        every { connectionRepository.save(any()) } returnsArgument 0

        service.applyToAccounts(userId = 1, modelId = 1, connectionIds = listOf(10, 20))

        verify { conn1.modelPortfolio = model }
        verify { conn2.modelPortfolio = model }
        verify(exactly = 2) { connectionRepository.save(any()) }
    }

    @Test
    fun `applyToAccounts throws for invalid connection`() {
        val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
        every { modelRepository.findById(1) } returns java.util.Optional.of(model)
        every { connectionRepository.findByIdAndUserId(999, 1) } returns null

        assertThrows<IllegalArgumentException> {
            service.applyToAccounts(userId = 1, modelId = 1, connectionIds = listOf(999))
        }
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker compose exec backend ./gradlew test --tests "com.portfolio.broker.service.ModelPortfolioServiceTest"`
Expected: FAIL — `applyToAccounts` method does not exist yet

- [ ] **Step 3: Implement applyToAccounts in ModelPortfolioService**

Replace the existing `applyToGroup` method with:

```kotlin
@Transactional
fun applyToAccounts(userId: Long, modelId: Long, connectionIds: List<Long>) {
    val model = findAccessible(modelId, userId)

    connectionIds.forEach { connId ->
        val connection = connectionRepository.findByIdAndUserId(connId, userId)
            ?: throw IllegalArgumentException("Connection not found: $connId")
        connection.modelPortfolio = model
        connectionRepository.save(connection)
    }

    log.info("Applied model '{}' (id={}) to {} accounts for user {}",
        model.name, modelId, connectionIds.size, userId)
}
```

Update the constructor to inject `BrokerConnectionRepository` instead of `PortfolioGroupRepository` and `PortfolioTargetRepository`:

```kotlin
class ModelPortfolioService(
    private val modelRepository: ModelPortfolioRepository,
    private val allocationRepository: ModelPortfolioAllocationRepository,
    private val connectionRepository: BrokerConnectionRepository
)
```

Remove the `applyToGroup` method and the imports for `PortfolioGroupRepository`, `PortfolioTargetRepository`, and `PortfolioGroupDetailDto`.

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose exec backend ./gradlew test --tests "com.portfolio.broker.service.ModelPortfolioServiceTest"`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/ModelPortfolioService.kt
git add backend/src/test/kotlin/com/portfolio/broker/service/ModelPortfolioServiceTest.kt
git commit -m "feat: replace applyToGroup with applyToAccounts in ModelPortfolioService"
```

---

## Task 5: Backend Service — Account Rebalance Progress & Pending Orders

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/DriftCalculationService.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/service/RebalanceService.kt`

- [ ] **Step 1: Read DriftCalculationService and RebalanceService**

Read both files to understand existing patterns for drift calculation and trade generation.

- [ ] **Step 2: Add getRebalanceProgress to DriftCalculationService**

Add a method that calculates target vs actual for a single account against its applied model:

```kotlin
fun getRebalanceProgress(connection: BrokerConnection): RebalanceProgressDto {
    val model = connection.modelPortfolio
        ?: throw IllegalArgumentException("No model applied to connection ${connection.id}")

    val positions = positionRepository.findByConnectionId(connection.id)
    val totalValue = positions.sumOf { it.marketValue ?: BigDecimal.ZERO }

    val entries = model.allocations.map { alloc ->
        val actualValue = positions
            .filter { it.symbol?.equals(alloc.symbol, ignoreCase = true) == true }
            .sumOf { it.marketValue ?: BigDecimal.ZERO }
        val actualPercent = if (totalValue > BigDecimal.ZERO)
            actualValue.divide(totalValue, 4, java.math.RoundingMode.HALF_UP)
                .multiply(BigDecimal(100))
        else BigDecimal.ZERO

        RebalanceProgressEntry(
            symbol = alloc.symbol,
            securityName = positions.find { it.symbol?.equals(alloc.symbol, ignoreCase = true) == true }?.securityName,
            targetPercent = alloc.targetPercent,
            actualPercent = actualPercent
        )
    }

    val accuracy = calculateAccuracy(entries)

    return RebalanceProgressDto(
        connectionId = connection.id,
        modelName = model.name,
        accuracy = accuracy,
        entries = entries
    )
}

private fun calculateAccuracy(entries: List<RebalanceProgressEntry>): BigDecimal {
    if (entries.isEmpty()) return BigDecimal.ZERO
    val totalDrift = entries.sumOf { (it.targetPercent - it.actualPercent).abs() }
    val maxDrift = BigDecimal(200) // worst case: all in wrong positions
    return BigDecimal(100).subtract(
        totalDrift.divide(maxDrift, 4, java.math.RoundingMode.HALF_UP).multiply(BigDecimal(100))
    ).coerceAtLeast(BigDecimal.ZERO)
}
```

- [ ] **Step 3: Add calculateTradesForAccount to RebalanceService**

Add a method that generates BUY/SELL trades to align an account with its model:

```kotlin
fun calculateTradesForAccount(connection: BrokerConnection): PendingOrdersResponse {
    val model = connection.modelPortfolio
        ?: throw IllegalArgumentException("No model applied to connection ${connection.id}")

    val positions = positionRepository.findByConnectionId(connection.id)
    val totalValue = positions.sumOf { it.marketValue ?: BigDecimal.ZERO }
    val orders = mutableListOf<PendingOrderDto>()

    model.allocations.forEach { alloc ->
        val targetValue = totalValue.multiply(alloc.targetPercent).divide(BigDecimal(100), 2, java.math.RoundingMode.HALF_UP)
        val currentPosition = positions.find { it.symbol?.equals(alloc.symbol, ignoreCase = true) == true }
        val currentValue = currentPosition?.marketValue ?: BigDecimal.ZERO
        val diff = targetValue.subtract(currentValue)
        val price = currentPosition?.price ?: BigDecimal.ONE // fallback; real impl uses market data

        if (diff.abs() >= BigDecimal(10)) { // minimum trade amount $10
            val units = diff.divide(price, 0, java.math.RoundingMode.DOWN).toInt()
            if (units != 0) {
                orders.add(PendingOrderDto(
                    action = if (diff > BigDecimal.ZERO) "BUY" else "SELL",
                    symbol = alloc.symbol,
                    securityName = currentPosition?.securityName,
                    units = kotlin.math.abs(units),
                    price = price,
                    amount = price.multiply(BigDecimal(kotlin.math.abs(units))),
                    currency = currentPosition?.currency ?: "CAD",
                    accountName = connection.accountName ?: ""
                ))
            }
        }
    }

    return PendingOrdersResponse(
        connectionId = connection.id,
        orders = orders,
        totalAmount = orders.sumOf { it.amount }
    )
}
```

- [ ] **Step 4: Verify it compiles**

Run: `docker compose exec backend ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/service/DriftCalculationService.kt
git add backend/src/main/kotlin/com/portfolio/broker/service/RebalanceService.kt
git commit -m "feat: add account-level rebalance progress and pending orders calculation"
```

---

## Task 6: Backend Controller — New Endpoints

**Files:**
- Modify: `backend/src/main/kotlin/com/portfolio/broker/controller/ModelPortfolioController.kt`
- Modify: `backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt`

- [ ] **Step 1: Update ModelPortfolioController — replace /apply with /apply-to-accounts**

Replace the existing `applyToGroup` endpoint:

```kotlin
@PostMapping("/{id}/apply-to-accounts")
fun applyToAccounts(
    @PathVariable id: Long,
    @RequestBody request: ApplyToAccountsRequest,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<Void> {
    modelPortfolioService.applyToAccounts(principal.id, id, request.connectionIds)
    return ResponseEntity.ok().build()
}
```

- [ ] **Step 2: Add /analysis endpoint to ModelPortfolioController**

```kotlin
@GetMapping("/{id}/analysis")
fun getAnalysis(
    @PathVariable id: Long,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<ModelAnalysisDto> {
    val analysis = modelPortfolioService.getAnalysis(id, principal.id)
    return ResponseEntity.ok(analysis)
}
```

- [ ] **Step 3: Add rebalance-progress and pending-orders to BrokerController**

Add these endpoints to `BrokerController.kt`:

```kotlin
@GetMapping("/connections/{connectionId}/rebalance-progress")
fun getRebalanceProgress(
    @PathVariable connectionId: Long,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<RebalanceProgressDto> {
    val connection = brokerService.getConnectionForUser(connectionId, principal.id)
    val progress = driftCalculationService.getRebalanceProgress(connection)
    return ResponseEntity.ok(progress)
}

@GetMapping("/connections/{connectionId}/pending-orders")
fun getPendingOrders(
    @PathVariable connectionId: Long,
    @AuthenticationPrincipal principal: UserPrincipal
): ResponseEntity<PendingOrdersResponse> {
    val connection = brokerService.getConnectionForUser(connectionId, principal.id)
    val orders = rebalanceService.calculateTradesForAccount(connection)
    return ResponseEntity.ok(orders)
}
```

Inject `DriftCalculationService` and `RebalanceService` into BrokerController constructor if not already present.

- [ ] **Step 4: Verify it compiles**

Run: `docker compose exec backend ./gradlew compileKotlin`
Expected: BUILD SUCCESSFUL

- [ ] **Step 5: Commit**

```bash
git add backend/src/main/kotlin/com/portfolio/broker/controller/ModelPortfolioController.kt
git add backend/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt
git commit -m "feat: add apply-to-accounts, analysis, rebalance-progress, pending-orders endpoints"
```

---

## Task 7: Frontend Types — Update modelPortfolio Types

**Files:**
- Modify: `frontend/src/types/modelPortfolio.ts`

- [ ] **Step 1: Replace ApplyModelRequest and add new types**

Replace `ApplyModelRequest` and add new types at the bottom of `frontend/src/types/modelPortfolio.ts`:

```typescript
// Replace existing ApplyModelRequest
export interface ApplyToAccountsRequest {
  connectionIds: number[]
}

// New response types
export interface RebalanceProgressEntry {
  symbol: string
  securityName: string | null
  targetPercent: number
  actualPercent: number
}

export interface RebalanceProgressResponse {
  connectionId: number
  modelName: string
  accuracy: number
  entries: RebalanceProgressEntry[]
}

export interface PendingOrder {
  action: 'BUY' | 'SELL'
  symbol: string
  securityName: string | null
  units: number
  price: number
  amount: number
  currency: string
  accountName: string
}

export interface PendingOrdersResponse {
  connectionId: number
  orders: PendingOrder[]
  totalAmount: number
}

export interface ExposureEntry {
  name: string
  percentage: number
}

export interface ModelAnalysisResponse {
  modelId: number
  sectorExposure: ExposureEntry[]
  geographyExposure: ExposureEntry[]
  riskScore: number
  riskLevel: string
  holdings: ModelAllocation[]
}
```

Remove the old `ApplyModelRequest` interface.

- [ ] **Step 2: Commit**

```bash
git add frontend/src/types/modelPortfolio.ts
git commit -m "feat: add frontend types for apply-to-accounts, rebalance progress, pending orders, model analysis"
```

---

## Task 8: Frontend Service & Hooks — New API Functions

**Files:**
- Modify: `frontend/src/services/modelPortfolioService.ts`
- Modify: `frontend/src/hooks/useModelPortfolios.ts`

- [ ] **Step 1: Update modelPortfolioService.ts**

Replace `applyModelToGroup` and add new functions:

```typescript
import type {
  ModelPortfoliosListResponse,
  ModelPortfolioDetail,
  CreateModelPortfolioRequest,
  UpdateModelPortfolioRequest,
  ApplyToAccountsRequest,
  ModelAnalysisResponse,
  RebalanceProgressResponse,
  PendingOrdersResponse,
} from '@/types/modelPortfolio'

// ... keep existing getModelPortfolios, getModelPortfolio, createModelPortfolio,
// updateModelPortfolio, deleteModelPortfolio unchanged ...

// Replace applyModelToGroup:
export async function applyModelToAccounts(modelId: number, request: ApplyToAccountsRequest): Promise<void> {
  const response = await apiFetch(`${BASE}/${modelId}/apply-to-accounts`, { method: 'POST', body: JSON.stringify(request) })
  if (!response.ok) throw new Error('Failed to apply model to accounts')
}

export async function getModelAnalysis(modelId: number): Promise<ModelAnalysisResponse> {
  const response = await apiFetch(`${BASE}/${modelId}/analysis`)
  if (!response.ok) throw new Error('Failed to fetch model analysis')
  return response.json()
}

export async function getRebalanceProgress(connectionId: number): Promise<RebalanceProgressResponse> {
  const response = await apiFetch(`/api/v1/brokers/connections/${connectionId}/rebalance-progress`)
  if (!response.ok) throw new Error('Failed to fetch rebalance progress')
  return response.json()
}

export async function getPendingOrders(connectionId: number): Promise<PendingOrdersResponse> {
  const response = await apiFetch(`/api/v1/brokers/connections/${connectionId}/pending-orders`)
  if (!response.ok) throw new Error('Failed to fetch pending orders')
  return response.json()
}
```

Remove the old `applyModelToGroup` function and the `PortfolioGroup` import.

- [ ] **Step 2: Update useModelPortfolios.ts**

Replace `useApplyModelToGroup` and add new hooks:

```typescript
import {
  getModelPortfolios,
  getModelPortfolio,
  createModelPortfolio,
  updateModelPortfolio,
  deleteModelPortfolio,
  applyModelToAccounts,
  getModelAnalysis,
  getRebalanceProgress,
  getPendingOrders,
} from '@/services/modelPortfolioService'

// ... keep existing useModelPortfolios, useModelPortfolio,
// useCreateModelPortfolio, useUpdateModelPortfolio, useDeleteModelPortfolio ...

// Replace useApplyModelToGroup:
export function useApplyModelToAccounts() {
  const qc = useQueryClient()
  return useMutation({
    mutationFn: ({ modelId, connectionIds }: { modelId: number; connectionIds: number[] }) =>
      applyModelToAccounts(modelId, { connectionIds }),
    onSuccess: () => {
      qc.invalidateQueries({ queryKey: keys.all })
      qc.invalidateQueries({ queryKey: ['broker-connections'] })
    },
  })
}

export function useModelAnalysis(modelId: number, enabled = true) {
  return useQuery({
    queryKey: ['model-analysis', modelId] as const,
    queryFn: () => getModelAnalysis(modelId),
    enabled,
    staleTime: 5 * 60 * 1000,
  })
}

export function useRebalanceProgress(connectionId: number, enabled = true) {
  return useQuery({
    queryKey: ['rebalance-progress', connectionId] as const,
    queryFn: () => getRebalanceProgress(connectionId),
    enabled,
    staleTime: 60 * 1000,
  })
}

export function usePendingOrders(connectionId: number, enabled = true) {
  return useQuery({
    queryKey: ['pending-orders', connectionId] as const,
    queryFn: () => getPendingOrders(connectionId),
    enabled,
    staleTime: 60 * 1000,
  })
}
```

- [ ] **Step 3: Verify build**

Run from `frontend/`: `npm run build`
Expected: Build succeeds

- [ ] **Step 4: Commit**

```bash
git add frontend/src/services/modelPortfolioService.ts frontend/src/hooks/useModelPortfolios.ts
git commit -m "feat: add service functions and hooks for apply-to-accounts, model analysis, rebalance progress, pending orders"
```

---

## Task 9: Frontend — Navigation & Routes

**Files:**
- Modify: `frontend/src/components/layout/AppSidebar.tsx`
- Modify: `frontend/src/App.tsx`

- [ ] **Step 1: Update AppSidebar — replace 3 portfolio nav items with 1**

In `AppSidebar.tsx`, replace the three nav items (Model Portfolios `/models`, Portfolio Groups `/portfolios`, Portfolio Builder `/builder`) with a single item:

```tsx
{ icon: Briefcase, label: 'Portfolio', path: '/portfolios' },
```

Remove the `Layers` and `Hammer` icon imports if no longer used.

- [ ] **Step 2: Update App.tsx routes**

Remove routes for `/models`, `/builder`, and `/portfolios/:groupId`. Keep `/portfolios` and update its component:

```tsx
import PortfolioPage from '@/pages/PortfolioPage'

// Replace:
// <Route path="/models" element={<ModelPortfoliosPage />} />
// <Route path="/builder" element={<PortfolioBuilderPage />} />
// <Route path="/portfolios" element={<PortfolioGroupsPage />} />
// <Route path="/portfolios/:groupId" element={<PortfolioGroupDetailPage />} />

// With:
<Route path="/portfolios" element={<PortfolioPage />} />
```

Remove the old page imports (`ModelPortfoliosPage`, `PortfolioGroupsPage`, `PortfolioGroupDetailPage`, `PortfolioBuilderPage`).

- [ ] **Step 3: Commit**

```bash
git add frontend/src/components/layout/AppSidebar.tsx frontend/src/App.tsx
git commit -m "feat: replace 3 portfolio nav items with single Portfolio entry, update routes"
```

---

## Task 10: Frontend — PortfolioPage with Model Cards

**Files:**
- Create: `frontend/src/pages/PortfolioPage.tsx`
- Create: `frontend/src/pages/PortfolioPage.css`
- Create: `frontend/src/components/portfolios/ModelPortfolioCard.tsx`
- Create: `frontend/src/components/portfolios/ModelPortfolioCard.css`

- [ ] **Step 1: Create ModelPortfolioCard component**

Create `frontend/src/components/portfolios/ModelPortfolioCard.tsx` — a card showing model name, risk level label, risk bar (color-coded), and selected state. Accept props: `model: ModelPortfolioSummary | null` (null = custom slot), `isSelected: boolean`, `onClick: () => void`. Use the risk bar colors: LOW=green (#22c55e, 25%), MODERATE=blue (#3b82f6, 50%), HIGH=amber (#f59e0b, 75%), EXTRA_HIGH=red (#ef4444, 100%). When model is null, show "+" icon with "Build Your Own" label and dashed border.

- [ ] **Step 2: Create ModelPortfolioCard.css**

Style the card with dark background (`var(--card-bg)`), rounded corners, hover state, selected state with accent border (`var(--accent)`), risk bar as a thin horizontal bar at the bottom.

- [ ] **Step 3: Create PortfolioPage component**

Create `frontend/src/pages/PortfolioPage.tsx`:
- Use `useModelPortfolios()` hook to fetch models
- Separate system models (sorted by risk: LOW→EXTRA_HIGH) from custom models
- Display 5 cards in a row: 4 system + 1 custom slot (show first custom model or "+" if none)
- Track `selectedModelId` state — when a card is clicked, set it and show the analysis panel below
- Conditionally render `ModelAnalysisPanel` or `CustomPortfolioBuilder` based on selection

- [ ] **Step 4: Create PortfolioPage.css**

Style the card grid (`display: grid; grid-template-columns: repeat(5, 1fr); gap: 16px`), analysis panel area, page header.

- [ ] **Step 5: Verify it renders**

Run from `frontend/`: `npm run dev`
Navigate to `/portfolios`, verify 5 cards appear.

- [ ] **Step 6: Commit**

```bash
git add frontend/src/pages/PortfolioPage.tsx frontend/src/pages/PortfolioPage.css
git add frontend/src/components/portfolios/ModelPortfolioCard.tsx frontend/src/components/portfolios/ModelPortfolioCard.css
git commit -m "feat: add PortfolioPage with model portfolio card grid"
```

---

## Task 11: Frontend — Model Analysis Panel

**Files:**
- Create: `frontend/src/components/portfolios/ModelAnalysisPanel.tsx`
- Create: `frontend/src/components/portfolios/ModelAnalysisPanel.css`

- [ ] **Step 1: Create ModelAnalysisPanel component**

Create `frontend/src/components/portfolios/ModelAnalysisPanel.tsx`:
- Accept prop: `modelId: number`
- Use `useModelAnalysis(modelId)` to fetch analysis data
- Display 4 widget-style sections in a 2x2 grid:
  - Sector & Industry — reuse display pattern from `SectorExposureWidget` (dual donut chart with percentages)
  - Geographic Exposure — reuse display pattern from `GeographyExposureWidget`
  - Risk Profile — reuse display pattern from `RiskProfileWidget` (gauge + risk factors)
  - Holdings — table of model allocations (symbol, target %)
- Include an "Apply to Account" button at the bottom that opens `ApplyToAccountModal`
- Track modal open state locally

- [ ] **Step 2: Create ModelAnalysisPanel.css**

Style with orange accent border-left (matching the analysis widget row), 2x2 grid layout, smooth expand/collapse animation.

- [ ] **Step 3: Verify it renders**

Click a model card on `/portfolios`, verify the analysis panel expands and shows loading state then data.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/portfolios/ModelAnalysisPanel.tsx frontend/src/components/portfolios/ModelAnalysisPanel.css
git commit -m "feat: add ModelAnalysisPanel with sector, geography, risk, holdings widgets"
```

---

## Task 12: Frontend — Custom Portfolio Builder

**Files:**
- Create: `frontend/src/components/portfolios/CustomPortfolioBuilder.tsx`
- Create: `frontend/src/components/portfolios/CustomPortfolioBuilder.css`

- [ ] **Step 1: Create CustomPortfolioBuilder component**

Create `frontend/src/components/portfolios/CustomPortfolioBuilder.tsx`:
- If a custom model exists, load it via `useModelPortfolio(id)` for editing
- Reuse `InstrumentTabs` component from existing portfolio builder for instrument search
- Show an allocations table: Symbol, Name, Weight %, Remove button
- Total weight display (green at 100%, red otherwise)
- "Normalize to 100%" button
- Name input field for the custom model
- Risk level selector (4 buttons: LOW, MODERATE, HIGH, EXTRA_HIGH)
- "Save" button — calls `useCreateModelPortfolio()` or `useUpdateModelPortfolio()` depending on create/edit
- Below the builder: show `ModelAnalysisPanel` with live-updating analysis as allocations change
- For live updates: use a debounced local analysis computation or pass allocations to analysis endpoint

- [ ] **Step 2: Create CustomPortfolioBuilder.css**

Style with the builder section using dashed border, instrument search area, allocations table.

- [ ] **Step 3: Verify it works**

Click the custom "+" card on `/portfolios`, add some instruments, verify the builder appears and analysis updates.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/portfolios/CustomPortfolioBuilder.tsx frontend/src/components/portfolios/CustomPortfolioBuilder.css
git commit -m "feat: add CustomPortfolioBuilder with inline instrument search and live analysis"
```

---

## Task 13: Frontend — Apply to Accounts Modal

**Files:**
- Create: `frontend/src/components/portfolios/ApplyToAccountModal.tsx`
- Create: `frontend/src/components/portfolios/ApplyToAccountModal.css`

- [ ] **Step 1: Create ApplyToAccountModal component**

Create `frontend/src/components/portfolios/ApplyToAccountModal.tsx`:
- Accept props: `modelId: number`, `modelName: string`, `isOpen: boolean`, `onClose: () => void`
- Fetch connected accounts using existing broker connections hook
- Display list of accounts: each with checkbox, account name, broker, masked number, total value
- Show current model badge for accounts that already have a model applied
- Warning text when selecting an account with existing model: "This will replace the current model"
- Multi-select: track `selectedConnectionIds` in local state
- "Apply Model" button — calls `useApplyModelToAccounts()` mutation
- On success: show toast notification ("Model applied to N accounts"), call `onClose()`
- Empty state: if no accounts, show message "No connected accounts" with link to broker connection page
- Loading state while applying

- [ ] **Step 2: Create ApplyToAccountModal.css**

Style the modal overlay, account list items with checkboxes, warning badges, action buttons.

- [ ] **Step 3: Verify it works**

Click "Apply to Account" on a model's analysis panel, verify modal shows connected accounts, select multiple, apply, verify toast.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/portfolios/ApplyToAccountModal.tsx frontend/src/components/portfolios/ApplyToAccountModal.css
git commit -m "feat: add ApplyToAccountModal with multi-select connected accounts"
```

---

## Task 14: Frontend — Rebalancing Progress Widget

**Files:**
- Create: `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.tsx`
- Create: `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.css`
- Modify: `frontend/src/components/dashboard/WidgetRegistry.ts`

- [ ] **Step 1: Create RebalancingProgressWidget**

Create `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.tsx`:
- Accept prop: `connectionId?: number`
- Use `useRebalanceProgress(connectionId)` hook
- Header: "REBALANCING PROGRESS" with legend (● Target blue, ● Actual green)
- Single-column list of entries, each showing:
  - Symbol + abbreviated security name
  - Target % (color: #3b82f6) and Actual % (color: #22c55e)
  - Dual progress bar: top bar = target (blue), bottom bar = actual (green)
  - Bar background: `var(--bg-secondary)`, height 14px, border-radius 3px
- Empty state when no model applied: "No model portfolio applied"
- Blue accent border-left: `border-left: 3px solid #3b82f6`

- [ ] **Step 2: Create RebalancingProgressWidget.css**

Style the progress bars, entry rows, legend. Use CSS custom properties for colors.

- [ ] **Step 3: Register in WidgetRegistry**

Add to `WidgetRegistry.ts`:

```typescript
const RebalancingProgressWidget = lazy(() => import('./widgets/RebalancingProgressWidget'))

// Add to WIDGET_REGISTRY:
REBALANCING_PROGRESS: { key: 'REBALANCING_PROGRESS', title: 'Rebalancing Progress', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 12, category: 'CATEGORY_1', component: RebalancingProgressWidget },
```

Add `'REBALANCING_PROGRESS'` to the `WidgetKey` type in `frontend/src/types/dashboard.ts`.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/widgets/RebalancingProgressWidget.tsx
git add frontend/src/components/dashboard/widgets/RebalancingProgressWidget.css
git add frontend/src/components/dashboard/WidgetRegistry.ts
git add frontend/src/types/dashboard.ts
git commit -m "feat: add RebalancingProgressWidget with target vs actual progress bars"
```

---

## Task 15: Frontend — Pending Orders Widget

**Files:**
- Create: `frontend/src/components/dashboard/widgets/PendingOrdersWidget.tsx`
- Create: `frontend/src/components/dashboard/widgets/PendingOrdersWidget.css`
- Modify: `frontend/src/components/dashboard/WidgetRegistry.ts`

- [ ] **Step 1: Create PendingOrdersWidget**

Create `frontend/src/components/dashboard/widgets/PendingOrdersWidget.tsx`:
- Accept prop: `connectionId?: number`
- Use `usePendingOrders(connectionId)` hook
- Header: "PENDING ORDERS" with "Preview Orders" button
- Group orders by action: BUY section (green header "#22c55e"), SELL section (red header "#ef4444")
- Each order row: grid with columns: Price, Units, Amount, Security symbol
- Footer: "Show Explanation ▼" toggle + Total amount
- Empty state: "No pending orders"
- Blue accent border-left: `border-left: 3px solid #3b82f6`

- [ ] **Step 2: Create PendingOrdersWidget.css**

Style the order rows, section headers, footer, action button.

- [ ] **Step 3: Register in WidgetRegistry**

Add to `WidgetRegistry.ts`:

```typescript
const PendingOrdersWidget = lazy(() => import('./widgets/PendingOrdersWidget'))

PENDING_ORDERS: { key: 'PENDING_ORDERS', title: 'Pending Orders', defaultVisible: true, defaultColumnSpan: 1, defaultSortOrder: 13, category: 'CATEGORY_1', component: PendingOrdersWidget },
```

Add `'PENDING_ORDERS'` to the `WidgetKey` type.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/widgets/PendingOrdersWidget.tsx
git add frontend/src/components/dashboard/widgets/PendingOrdersWidget.css
git add frontend/src/components/dashboard/WidgetRegistry.ts
git add frontend/src/types/dashboard.ts
git commit -m "feat: add PendingOrdersWidget with BUY/SELL order sections"
```

---

## Task 16: Frontend — Account Detail Page Updates

**Files:**
- Modify: `frontend/src/pages/AccountDetailPage.tsx`
- Modify: `frontend/src/pages/AccountDetailPage.css` (or related CSS)

- [ ] **Step 1: Read AccountDetailPage.tsx**

Read the current file to understand its structure.

- [ ] **Step 2: Add model badge to header**

In the header section, add the model badge to the right side next to the accuracy circle:

```tsx
{connection?.modelPortfolio && (
  <span className="model-badge">{connection.modelPortfolio.name} Model</span>
)}
```

Style `.model-badge` with: `background: #1e3a5f; color: #60a5fa; padding: 3px 10px; border-radius: 4px; font-size: 12px;`

- [ ] **Step 3: Add new widgets row (Row 3) for Rebalancing Progress + Pending Orders**

After the Risk Profile/Sector/Geography row in the DashboardGrid, add a conditional row that only renders when a model is applied:

```tsx
{connection?.modelPortfolio && (
  <div className="widget-row widget-row--model">
    <RebalancingProgressWidget connectionId={connectionId} />
    <PendingOrdersWidget connectionId={connectionId} />
  </div>
)}
```

Style `.widget-row--model` with: `display: grid; grid-template-columns: 1fr 1fr; gap: 16px;`

- [ ] **Step 4: Verify it renders**

Navigate to an account detail page, verify:
- Model badge appears in header when model applied
- Rebalancing Progress + Pending Orders appear in row 3
- Both hidden when no model applied

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/AccountDetailPage.tsx frontend/src/pages/AccountDetailPage.css
git commit -m "feat: add model badge and rebalancing/pending orders widgets to account detail"
```

---

## Task 17: Frontend — Unified Color Scheme

**Files:**
- Modify: `frontend/src/components/dashboard/DashboardGrid.css`
- Modify: `frontend/src/components/dashboard/DashboardGrid.tsx`

- [ ] **Step 1: Add widget accent color CSS classes**

Add to `DashboardGrid.css`:

```css
/* Unified widget color scheme */
.widget-accent--teal { border-left: 3px solid #14b8a6; }
.widget-accent--orange { border-left: 3px solid #f97316; }
.widget-accent--blue { border-left: 3px solid #3b82f6; }
.widget-accent--red { border-left: 3px solid #ef4444; }
.widget-accent--purple { border-left: 3px solid #8b5cf6; }
```

- [ ] **Step 2: Map widgets to accent colors in DashboardGrid.tsx**

Create a color mapping and apply CSS classes to widget wrappers:

```typescript
const WIDGET_ACCENT: Record<WidgetKey, string> = {
  PORTFOLIO_VALUE: 'teal',
  AVAILABLE_CASH: 'teal',
  BUYING_POWER: 'teal',
  RISK_PROFILE: 'orange',
  SECTOR_EXPOSURE: 'orange',
  GEOGRAPHY_EXPOSURE: 'orange',
  CONNECTED_ACCOUNTS: 'blue',
  REBALANCING_PROGRESS: 'blue',
  PENDING_ORDERS: 'blue',
  OPEN_ORDERS: 'red',
  FEES_COMMISSION: 'red',
  DIVIDEND_CALENDAR: 'red',
  POSITIONS_TABLE: 'purple',
  HOLDINGS_TABLE: 'purple',
}
```

Apply `className={`widget-accent--${WIDGET_ACCENT[widget.key]}`}` to each widget wrapper div.

- [ ] **Step 3: Verify colors**

Check dashboard and account detail pages — all widgets should show unified row-based accent colors.

- [ ] **Step 4: Commit**

```bash
git add frontend/src/components/dashboard/DashboardGrid.css frontend/src/components/dashboard/DashboardGrid.tsx
git commit -m "feat: apply unified 5-color accent scheme to all dashboard widgets"
```

---

## Task 18: Frontend — Connected Accounts Widget Enhancement

**Files:**
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.tsx`
- Modify: `frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.css`

- [ ] **Step 1: Read the current ConnectedAccountsWidget**

Read the file to understand the current card layout.

- [ ] **Step 2: Add accuracy circle, model badge, and mini progress bar**

Update each account card to include:
- **Accuracy circle** (top-right): color-coded border (green ≥80%, amber ≥50%, red <50%), shows percentage or "—" if no model
- **Model badge**: shows applied model name (e.g., "Growth") or "No model"
- **Mini progress bar** at bottom: thin 3px bar colored to match accuracy

```tsx
<div className="account-card">
  <div className="account-card__header">
    <div>
      <div className="account-card__name">{account.accountName}</div>
      <div className="account-card__broker">{account.brokerName} · ••••{account.accountNumber?.slice(-4)}</div>
    </div>
    <div className={`accuracy-circle accuracy-circle--${getAccuracyColor(account.modelAccuracy)}`}>
      {account.modelAccuracy != null ? `${Math.round(account.modelAccuracy)}%` : '—'}
    </div>
  </div>
  <div className="account-card__footer">
    <span className="account-card__value">{formatCurrency(account.totalValue)}</span>
    {account.modelName ? (
      <span className="account-card__model-badge">{account.modelName}</span>
    ) : (
      <span className="account-card__no-model">No model</span>
    )}
  </div>
  <div className="account-card__progress-bar">
    <div
      className={`account-card__progress-fill account-card__progress-fill--${getAccuracyColor(account.modelAccuracy)}`}
      style={{ width: `${account.modelAccuracy ?? 0}%` }}
    />
  </div>
</div>
```

Helper function:
```typescript
function getAccuracyColor(accuracy: number | null): string {
  if (accuracy == null) return 'grey'
  if (accuracy >= 80) return 'green'
  if (accuracy >= 50) return 'amber'
  return 'red'
}
```

- [ ] **Step 3: Add CSS for accuracy circle, badge, progress bar**

Add to `ConnectedAccountsWidget.css`:

```css
.accuracy-circle { width: 32px; height: 32px; border-radius: 50%; display: flex; align-items: center; justify-content: center; font-weight: 700; font-size: 9px; flex-shrink: 0; }
.accuracy-circle--green { border: 2px solid #22c55e; color: #22c55e; }
.accuracy-circle--amber { border: 2px solid #f59e0b; color: #f59e0b; }
.accuracy-circle--red { border: 2px solid #ef4444; color: #ef4444; }
.accuracy-circle--grey { border: 2px solid var(--border); color: var(--text-secondary); }

.account-card__model-badge { background: #1e3a5f; color: #60a5fa; padding: 1px 6px; border-radius: 3px; font-size: 10px; }
.account-card__no-model { color: var(--text-secondary); font-size: 10px; }

.account-card__progress-bar { margin-top: 6px; height: 3px; background: var(--bg-secondary); border-radius: 2px; overflow: hidden; }
.account-card__progress-fill { height: 100%; border-radius: 2px; }
.account-card__progress-fill--green { background: #22c55e; }
.account-card__progress-fill--amber { background: #f59e0b; }
.account-card__progress-fill--red { background: #ef4444; }
```

- [ ] **Step 4: Verify it renders**

Check dashboard — connected accounts widget should show accuracy circles, model badges, and mini progress bars.

- [ ] **Step 5: Commit**

```bash
git add frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.tsx
git add frontend/src/components/dashboard/widgets/ConnectedAccountsWidget.css
git commit -m "feat: add accuracy circle, model badge, and progress bar to connected accounts widget"
```

---

## Task 19: Frontend — Cleanup Old Pages and Components

**Files:**
- Delete: old portfolio group and model portfolio pages, components, hooks, services, types

- [ ] **Step 1: Remove old page files**

Delete these files:
- `frontend/src/pages/ModelPortfoliosPage.tsx`
- `frontend/src/pages/ModelPortfoliosPage.css`
- `frontend/src/pages/PortfolioGroupDetailPage.tsx`
- `frontend/src/pages/PortfolioGroupDetailPage.css`
- `frontend/src/pages/PortfolioBuilderPage.tsx` (if exists)
- `frontend/src/pages/PortfolioBuilderPage.css` (if exists)

- [ ] **Step 2: Remove old component directories**

Delete:
- `frontend/src/components/portfolio-groups/` (entire directory)
- `frontend/src/components/model-portfolios/` (entire directory)

- [ ] **Step 3: Remove old hooks and services**

Delete:
- `frontend/src/hooks/usePortfolioGroups.ts`
- `frontend/src/services/portfolioGroupService.ts`
- `frontend/src/types/portfolioGroup.ts`

- [ ] **Step 4: Verify build still passes**

Run from `frontend/`: `npm run build`
Expected: Build succeeds with no import errors

- [ ] **Step 5: Commit**

```bash
git add -A
git commit -m "chore: remove old portfolio groups, model portfolios, and builder pages/components"
```

---

## Task 20: Frontend Tests

**Files:**
- Create: `frontend/src/pages/PortfolioPage.test.tsx`
- Create: `frontend/src/components/portfolios/ApplyToAccountModal.test.tsx`
- Create: `frontend/src/components/dashboard/widgets/RebalancingProgressWidget.test.tsx`
- Create: `frontend/src/components/dashboard/widgets/PendingOrdersWidget.test.tsx`

- [ ] **Step 1: Write PortfolioPage test**

```tsx
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { MemoryRouter } from 'react-router-dom'
import { describe, it, expect, vi } from 'vitest'
import PortfolioPage from './PortfolioPage'

vi.mock('@/hooks/useModelPortfolios', () => ({
  useModelPortfolios: () => ({
    data: {
      models: [
        { id: 1, name: 'Conservative Income', riskLevel: 'LOW', isSystem: true, allocationCount: 5, totalPercent: 100 },
        { id: 2, name: 'Balanced Growth', riskLevel: 'MODERATE', isSystem: true, allocationCount: 5, totalPercent: 100 },
        { id: 3, name: 'Growth', riskLevel: 'HIGH', isSystem: true, allocationCount: 5, totalPercent: 100 },
        { id: 4, name: 'Aggressive Equity', riskLevel: 'EXTRA_HIGH', isSystem: true, allocationCount: 5, totalPercent: 100 },
      ],
    },
    isLoading: false,
  }),
}))

const wrapper = ({ children }: { children: React.ReactNode }) => (
  <QueryClientProvider client={new QueryClient()}>
    <MemoryRouter>{children}</MemoryRouter>
  </QueryClientProvider>
)

describe('PortfolioPage', () => {
  it('renders 4 system model cards plus custom slot', () => {
    render(<PortfolioPage />, { wrapper })
    expect(screen.getByText('Conservative Income')).toBeInTheDocument()
    expect(screen.getByText('Balanced Growth')).toBeInTheDocument()
    expect(screen.getByText('Growth')).toBeInTheDocument()
    expect(screen.getByText('Aggressive Equity')).toBeInTheDocument()
    expect(screen.getByText('Build Your Own')).toBeInTheDocument()
  })
})
```

- [ ] **Step 2: Write RebalancingProgressWidget test**

```tsx
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import RebalancingProgressWidget from './RebalancingProgressWidget'

vi.mock('@/hooks/useModelPortfolios', () => ({
  useRebalanceProgress: () => ({
    data: {
      connectionId: 1,
      modelName: 'Growth',
      accuracy: 11,
      entries: [
        { symbol: 'SOXL', securityName: 'Semiconductor Bull 3X', targetPercent: 10, actualPercent: 1.6 },
        { symbol: 'TECL', securityName: 'Technology Bull 3X', targetPercent: 10, actualPercent: 4.3 },
      ],
    },
    isLoading: false,
  }),
}))

describe('RebalancingProgressWidget', () => {
  it('renders target vs actual entries', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <RebalancingProgressWidget connectionId={1} />
      </QueryClientProvider>
    )
    expect(screen.getByText('SOXL')).toBeInTheDocument()
    expect(screen.getByText('TECL')).toBeInTheDocument()
    expect(screen.getByText('10.0%')).toBeInTheDocument()
  })
})
```

- [ ] **Step 3: Write PendingOrdersWidget test**

```tsx
import { render, screen } from '@testing-library/react'
import { QueryClient, QueryClientProvider } from '@tanstack/react-query'
import { describe, it, expect, vi } from 'vitest'
import PendingOrdersWidget from './PendingOrdersWidget'

vi.mock('@/hooks/useModelPortfolios', () => ({
  usePendingOrders: () => ({
    data: {
      connectionId: 1,
      orders: [
        { action: 'BUY', symbol: 'SOXL', securityName: 'Semiconductor 3X', units: 134, price: 48.89, amount: 6551.26, currency: 'CAD', accountName: 'TFSA' },
      ],
      totalAmount: 6551.26,
    },
    isLoading: false,
  }),
}))

describe('PendingOrdersWidget', () => {
  it('renders BUY orders', () => {
    render(
      <QueryClientProvider client={new QueryClient()}>
        <PendingOrdersWidget connectionId={1} />
      </QueryClientProvider>
    )
    expect(screen.getByText('BUY')).toBeInTheDocument()
    expect(screen.getByText('SOXL')).toBeInTheDocument()
  })
})
```

- [ ] **Step 4: Run all frontend tests**

Run from `frontend/`: `npm run test:run`
Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add frontend/src/pages/PortfolioPage.test.tsx
git add frontend/src/components/portfolios/ApplyToAccountModal.test.tsx
git add frontend/src/components/dashboard/widgets/RebalancingProgressWidget.test.tsx
git add frontend/src/components/dashboard/widgets/PendingOrdersWidget.test.tsx
git commit -m "test: add tests for PortfolioPage, RebalancingProgressWidget, PendingOrdersWidget"
```

---

## Task 21: Backend Tests

**Files:**
- Create/Modify: `backend/src/test/kotlin/com/portfolio/broker/service/ModelPortfolioServiceTest.kt`

- [ ] **Step 1: Add tests for edge cases**

Add tests to the existing `ModelPortfolioServiceTest.kt`:

```kotlin
@Test
fun `applyToAccounts replaces existing model on account`() {
    val oldModel = ModelPortfolio(id = 1, name = "Conservative", riskLevel = RiskLevel.LOW, isSystem = true)
    val newModel = ModelPortfolio(id = 2, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
    val conn = mockk<BrokerConnection>(relaxed = true)

    every { conn.modelPortfolio } returns oldModel
    every { modelRepository.findById(2) } returns java.util.Optional.of(newModel)
    every { connectionRepository.findByIdAndUserId(10, 1) } returns conn
    every { connectionRepository.save(any()) } returnsArgument 0

    service.applyToAccounts(userId = 1, modelId = 2, connectionIds = listOf(10))

    verify { conn.modelPortfolio = newModel }
}

@Test
fun `applyToAccounts with empty list does nothing`() {
    val model = ModelPortfolio(id = 1, name = "Growth", riskLevel = RiskLevel.HIGH, isSystem = true)
    every { modelRepository.findById(1) } returns java.util.Optional.of(model)

    service.applyToAccounts(userId = 1, modelId = 1, connectionIds = emptyList())

    verify(exactly = 0) { connectionRepository.save(any()) }
}
```

- [ ] **Step 2: Run backend tests**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests pass

- [ ] **Step 3: Commit**

```bash
git add backend/src/test/kotlin/
git commit -m "test: add backend tests for applyToAccounts edge cases"
```

---

## Task 22: Documentation Updates

**Files:**
- Modify: `CLAUDE.md`
- Modify: `docs/api.md`
- Modify: `docs/deployment.md`
- Modify: `docs/development.md`
- Modify: `README.md`

- [ ] **Step 1: Update CLAUDE.md**

Read `CLAUDE.md` and make these changes:
- **Backend package structure**: Remove `PortfolioGroupController` from controller listing, add note about model portfolio endpoints
- **Frontend directory structure**: Replace `portfolio-groups/` with `portfolios/`, remove `model-portfolios/`
- **Key entity relationships**: Remove `PortfolioGroup → PortfolioTarget` and `PortfolioGroup → PortfolioGroupAccount → BrokerConnection`. Add `BrokerConnection → ModelPortfolio (many-to-one, nullable)`
- **API Routes table**: Remove `/api/v1/portfolio-groups` row. Update `/api/v1/model-portfolios` to note new endpoints. Add new broker connection endpoints.
- **Adding a new page section**: Update to reflect single portfolio route

- [ ] **Step 2: Update docs/api.md**

Read and update:
- Remove all portfolio-group endpoint documentation
- Add new endpoints: `POST /api/v1/model-portfolios/{id}/apply-to-accounts`, `GET /api/v1/model-portfolios/{id}/analysis`, `GET /api/v1/brokers/connections/{id}/rebalance-progress`, `GET /api/v1/brokers/connections/{id}/pending-orders`
- Update request/response examples

- [ ] **Step 3: Update docs/deployment.md**

Add note about V60 migration and new columns on `broker_connections`.

- [ ] **Step 4: Update docs/development.md**

Update backend package structure, remove PortfolioGroup references.

- [ ] **Step 5: Update README.md**

Update feature descriptions and API endpoint sections if present.

- [ ] **Step 6: Commit**

```bash
git add CLAUDE.md docs/api.md docs/deployment.md docs/development.md README.md
git commit -m "docs: update all documentation for portfolio section redesign"
```

---

## Task 23: Final Verification

- [ ] **Step 1: Run full backend test suite**

Run: `docker compose exec backend ./gradlew test`
Expected: All tests pass

- [ ] **Step 2: Run full frontend test suite**

Run from `frontend/`: `npm run test:run`
Expected: All tests pass

- [ ] **Step 3: Run frontend lint**

Run from `frontend/`: `npm run lint`
Expected: No errors

- [ ] **Step 4: Run frontend build**

Run from `frontend/`: `npm run build`
Expected: Build succeeds

- [ ] **Step 5: Manual smoke test**

1. Navigate to `/portfolios` — 5 model cards render
2. Click each system model — analysis panel expands
3. Click custom "+" — builder appears
4. Add instruments, save custom model
5. Click "Apply to Account" — modal with multi-select accounts
6. Apply model — toast notification
7. Navigate to account detail — model badge, rebalancing progress, pending orders visible
8. Check dashboard — connected accounts show accuracy %, model badges, progress bars
9. Verify unified color scheme across both pages

- [ ] **Step 6: Final commit**

```bash
git add -A
git commit -m "feat: complete portfolio section redesign — merged page, account-level model application, new widgets, unified colors"
```
