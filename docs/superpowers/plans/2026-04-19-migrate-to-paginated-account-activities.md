# Migrate Activity Sync to Paginated Account-Level Endpoint

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the deprecated `getActivities` endpoint with the paginated `getAccountActivities` endpoint to reliably fetch all historical broker activities.

**Architecture:** Add a new `getAccountActivities` method to the SnapTrade adapter layer that supports offset/limit pagination and returns a `PaginatedActivitiesResult` containing `total` count. The `ActivityIngestionService` will loop through pages until all activities are fetched. The existing `getActivities` method is preserved for backward compatibility but the sync logic switches to the new method.

**Tech Stack:** Kotlin, Spring Boot, SnapTrade Java SDK 5.0.168, MockK for testing

**Key discovery from SnapTrade API docs:**
- Deprecated endpoint `GET /activities` — no pagination, max 10K results
- New endpoint `GET /accounts/{accountId}/activities` — supports `offset`/`limit`, returns `PaginatedUniversalActivity` with `total` count, default page size 1000, max 1000 per request
- The new endpoint uses `AccountUniversalActivity` (vs `UniversalActivity`) — fields are structurally similar but `currency` is an object with `.code`, `symbol` is an object with `.symbol`, and dates are `OffsetDateTime`

---

### Task 1: Add Paginated Activities DTO and Adapter Interface Method

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt`

- [ ] **Step 1: Add PaginatedActivitiesResult DTO to SnapTradeDtos.kt**

Add at the end of the Activity section (after `SnapTradeActivityDto`):

```kotlin
data class PaginatedActivitiesResult(
    val activities: List<SnapTradeActivityDto>,
    val total: Int,
    val offset: Int,
    val limit: Int
)
```

- [ ] **Step 2: Add getAccountActivities to SnapTradeAdapter interface**

Add after the existing `getActivities` method:

```kotlin
fun getAccountActivities(
    userId: String,
    userSecret: String,
    accountId: String,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    offset: Int = 0,
    limit: Int = 1000,
    type: String? = null
): PaginatedActivitiesResult
```

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeDtos.kt backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapter.kt
git commit -m "feat: add paginated getAccountActivities to adapter interface"
```

---

### Task 2: Implement getAccountActivities in SnapTradeAdapterImpl

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt`

- [ ] **Step 1: Implement the new method in SnapTradeAdapterImpl**

Add after the existing `getActivities` method (around line 168):

```kotlin
override fun getAccountActivities(
    userId: String,
    userSecret: String,
    accountId: String,
    startDate: LocalDate?,
    endDate: LocalDate?,
    offset: Int,
    limit: Int,
    type: String?
): PaginatedActivitiesResult {
    return try {
        val request = snaptrade.accountInformation.getAccountActivities(
            UUID.fromString(accountId), userId, userSecret
        )
        startDate?.let { request.startDate(it) }
        endDate?.let { request.endDate(it) }
        request.offset(offset)
        request.limit(limit)
        type?.let { request.type(it) }

        val response = request.execute()
        val activities = response.data?.map { it.toAccountActivityDto() } ?: emptyList()
        val pagination = response.pagination

        PaginatedActivitiesResult(
            activities = activities,
            total = pagination?.total ?: activities.size,
            offset = pagination?.offset ?: offset,
            limit = pagination?.limit ?: limit
        )
    } catch (e: ApiException) {
        throw mapApiException(e)
    }
}
```

- [ ] **Step 2: Add the AccountUniversalActivity mapper**

Add after the existing `UniversalActivity.toDto()` mapper (around line 424):

```kotlin
private fun AccountUniversalActivity.toAccountActivityDto(): SnapTradeActivityDto {
    val tradeDateLocal = tradeDate?.let {
        try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
    }
    val settlementDateLocal = settlementDate?.let {
        try { LocalDate.parse(it.toString().take(10)) } catch (e: Exception) { null }
    }
    val rawJson = try { objectMapper.writeValueAsString(this) } catch (e: Exception) { null }

    return SnapTradeActivityDto(
        id = id,
        type = type,
        symbol = symbol?.symbol,
        description = description,
        units = units,
        price = price,
        amount = amount,
        fee = fee,
        currency = currency?.code,
        tradeDate = tradeDateLocal,
        settlementDate = settlementDateLocal,
        optionType = optionType,
        rawJson = rawJson
    )
}
```

- [ ] **Step 3: Add the new import**

Add to the imports at the top of `SnapTradeAdapterImpl.kt`:

```kotlin
import com.snaptrade.client.model.AccountUniversalActivity
```

Note: `PaginatedUniversalActivity` and `AccountUniversalActivity` should already be available via the wildcard import `import com.snaptrade.client.model.*`.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/adapter/SnapTradeAdapterImpl.kt
git commit -m "feat: implement paginated getAccountActivities in adapter"
```

---

### Task 3: Add Paginated Method to SnapTradeService

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt`

- [ ] **Step 1: Add getAllAccountActivities method**

This method handles the pagination loop internally, fetching all pages and returning the complete list. Add after the existing `getActivities` method:

```kotlin
fun getAllAccountActivities(
    user: User,
    accountId: String,
    startDate: LocalDate? = null,
    endDate: LocalDate? = null,
    type: String? = null
): List<SnapTradeActivityDto> {
    val snapUser = ensureUserRegistered(user)
    val allActivities = mutableListOf<SnapTradeActivityDto>()
    var offset = 0
    val pageSize = 1000

    do {
        val page = adapter.getAccountActivities(
            userId = snapUser.userId,
            userSecret = snapUser.userSecret,
            accountId = accountId,
            startDate = startDate,
            endDate = endDate,
            offset = offset,
            limit = pageSize,
            type = type
        )
        allActivities.addAll(page.activities)
        offset += page.activities.size
    } while (allActivities.size < page.total && page.activities.isNotEmpty())

    return allActivities
}
```

- [ ] **Step 2: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/SnapTradeService.kt
git commit -m "feat: add paginated getAllAccountActivities to SnapTradeService"
```

---

### Task 4: Update ActivityIngestionService to Use New Endpoint

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt`

- [ ] **Step 1: Update syncFullHistory to use getAllAccountActivities**

Replace the existing `syncFullHistory` method:

```kotlin
private fun syncFullHistory(connection: com.portfolio.broker.entity.BrokerConnection, user: com.portfolio.auth.entity.User): Int {
    val startDate = LocalDate.now().minusYears(maxLookbackYears.toLong())
    val endDate = LocalDate.now()
    val accountId = connection.accountIdExternal
        ?: run {
            log.warn("Connection {} has no external account ID, skipping historical sync", connection.id)
            return 0
        }

    log.info("Full historical sync for connection {}: fetching {} to {} (paginated)", connection.id, startDate, endDate)

    val activities = try {
        snapTradeService.getAllAccountActivities(
            user = user,
            accountId = accountId,
            startDate = startDate,
            endDate = endDate
        )
    } catch (e: Exception) {
        log.error("Historical sync failed for connection {}: {}", connection.id, e.message)
        return 0
    }

    val inserted = processAndSaveActivities(activities, connection)

    log.info("Historical sync complete for connection {}: {} activities ({} new)",
        connection.id, activities.size, inserted)

    return inserted
}
```

- [ ] **Step 2: Update syncIncremental to also use the new endpoint**

Replace the existing `syncIncremental` method:

```kotlin
private fun syncIncremental(connection: com.portfolio.broker.entity.BrokerConnection, user: com.portfolio.auth.entity.User, startDate: LocalDate): Int {
    val accountId = connection.accountIdExternal
        ?: run {
            log.warn("Connection {} has no external account ID, skipping incremental sync", connection.id)
            return 0
        }

    val activities = try {
        snapTradeService.getAllAccountActivities(
            user = user,
            accountId = accountId,
            startDate = startDate
        )
    } catch (e: Exception) {
        log.error("Failed to fetch activities for connection {}: {}", connection.id, e.message)
        throw e
    }

    return processAndSaveActivities(activities, connection)
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt
git commit -m "feat: switch activity sync to paginated account-level endpoint"
```

---

### Task 5: Update Tests

**Files:**
- Modify: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt`

- [ ] **Step 1: Update test setup to mock the new method**

In every test that mocks `snapTradeService.getActivities(...)`, change to mock `snapTradeService.getAllAccountActivities(...)`. The mock setup line changes from:

```kotlin
every { snapTradeService.getActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
```

To:

```kotlin
every { snapTradeService.getAllAccountActivities(any(), any(), any(), any(), any()) } returns listOf(activity)
```

Apply this change in every test method that uses `getActivities`:
- `syncActivitiesForConnection maps fields correctly` (line 92)
- `syncActivitiesForConnection incremental sync fetches from last known date` (line 118)
- `syncActivitiesForConnection does not duplicate existing activities` (line 154)
- `syncActivitiesForConnection handles null optional fields` (line 184)
- `maps TRANSFERS to TRANSFER_IN` (line 248)
- `maps WITHDRAWALS to TRANSFER_OUT` (line 269)
- `converts USD amount to CAD using exchange rate` (line 290)
- `keeps CAD amount unchanged with exchange rate of 1` (line 314)
- `handles zero amount without FX lookup` (line 340)
- `falls back to raw amount when exchange rate unavailable` (line 361)

- [ ] **Step 2: Update the incremental sync verify call**

In the test `syncActivitiesForConnection incremental sync fetches from last known date`, update the verify block from:

```kotlin
verify {
    snapTradeService.getActivities(
        user = mockUser,
        startDate = LocalDate.of(2024, 4, 30),
        endDate = null,
        accounts = "ext-account-123",
        type = null
    )
}
```

To:

```kotlin
verify {
    snapTradeService.getAllAccountActivities(
        user = mockUser,
        accountId = "ext-account-123",
        startDate = LocalDate.of(2024, 4, 30),
        endDate = null,
        type = null
    )
}
```

- [ ] **Step 3: Update the syncAllConnections test**

In the test `syncAllConnections continues on individual connection error`, change:

```kotlin
every { snapTradeService.getActivities(any(), any(), any(), eq("acc-1"), any()) } throws RuntimeException("API down")
every { snapTradeService.getActivities(any(), any(), any(), eq("acc-2"), any()) } returns emptyList()
```

To:

```kotlin
every { snapTradeService.getAllAccountActivities(any(), eq("acc-1"), any(), any(), any()) } throws RuntimeException("API down")
every { snapTradeService.getAllAccountActivities(any(), eq("acc-2"), any(), any(), any()) } returns emptyList()
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `docker compose exec backend ./gradlew test`

Expected: All tests pass

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt
git commit -m "test: update activity ingestion tests for paginated endpoint"
```

---

### Task 6: Update Reference Documentation

**Files:**
- Modify: `docs/reference/backend-services.md`

- [ ] **Step 1: Update SnapTradeAdapter interface docs**

In the SnapTradeAdapter interface table (around line 557), add a new row after `getActivities`:

```
| `getAccountActivities` | `(userId, userSecret, accountId, startDate?, endDate?, offset, limit, type?): PaginatedActivitiesResult` |
```

- [ ] **Step 2: Update SnapTradeService docs**

In the SnapTradeService table (around line 248), add after `getActivities`:

```
| `getAllAccountActivities` | `(user, accountId, startDate?, endDate?, type?): List<SnapTradeActivityDto>` | Fetches all activities for an account using paginated API (loops until all pages fetched) |
```

- [ ] **Step 3: Update ActivityIngestionService docs**

Update the description for `syncActivitiesForConnection` to mention it uses the paginated account-level endpoint.

- [ ] **Step 4: Commit**

```bash
git add docs/reference/backend-services.md
git commit -m "docs: update reference docs for paginated activity endpoint"
```

---

### Task 7: Validate End-to-End

- [ ] **Step 1: Run full backend test suite**

Run: `docker compose exec backend ./gradlew test`

Expected: All tests pass

- [ ] **Step 2: Trigger a manual activity sync**

Via the API (or frontend), trigger a sync for a connection and check the logs:

```
docker compose logs backend --tail=50 | grep -i "historical sync\|paginated\|activities"
```

Expected: Logs show paginated fetch with total count, and activities older than 2025 appear.

- [ ] **Step 3: Verify activity count in database**

Check the total activity count for the synced connection to see if it increased from the previous 150/8 counts.
