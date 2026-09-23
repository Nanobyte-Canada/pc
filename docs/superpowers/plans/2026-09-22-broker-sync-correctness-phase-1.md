# Broker Sync Correctness — Phase 1 (Restore Activity Ingestion) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make Questrade activity ingestion work again — fetch long ranges in bounded windows, deduplicate activities that have no broker-assigned id, and repair existing duplicate/NULL-key rows.

**Architecture:** The broker-gateway splits any requested activities range into contiguous 30-day windows aligned to `America/Toronto` (fixing the hardcoded `-05:00` DST bug), merges and sorts the results. The portfolio computes a SHA-256 fingerprint over the canonical activity fields when the broker provides no `externalId`, stores it in `broker_activities.external_id`, so the existing lookup + `uq_activity_external` constraint become effective. A one-time Flyway migration (V77) backfills fingerprints and removes existing duplicates.

**Tech Stack:** Kotlin, Spring Boot, Gradle (per-module wrappers), JUnit 5 + kotlin-test + MockK, Flyway, PostgreSQL.

**Spec:** `docs/superpowers/specs/2026-09-22-broker-sync-correctness-design.md` (Phase 1 section).

## Global Constraints

- **Never add `Co-Authored-By:` or AI attribution to commits** (`AGENTS.md`). Imperative commit messages.
- **Shared working tree**: concurrent strategy work (PR #272) is in progress. Stage only the files listed in each task; never `git add -A`.
- **Implement in an isolated worktree** off `main` (spec §5.5); `broker-gateway` is also touched by PR #272 — expect one rebase when both land.
- Test commands: gateway module → `cd backend/broker-gateway && ./gradlew test`; portfolio module → `cd backend/portfolio && ./gradlew test -PexcludeIntegration`.
- **Schema note (verified):** `broker_activities.external_id` is `VARCHAR(100)` (`V36__broker_activities.sql:4`) and `BrokerActivity.externalId` is `@Column(length = 100)` — 64-char fingerprints fit; no DDL change is needed.
- Do not change existing test expectations in `ActivityIngestionServiceTest` (the "incremental sync fetches from last known date" test stays valid — portfolio still makes one gateway call per sync; windowing is a gateway-internal concern).
- The repair migration runs against a fresh DB backup on prod, UAT first (spec §5.3).
- ADR entry ships in the same PR as the change (`AGENTS.md` contract).

---

### Task 1: ET-aware activity window planner (gateway, pure)

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeActivityWindows.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeActivityWindowsTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `data class DateWindow(val start: LocalDate, val endInclusive: LocalDate)`; `object QuestradeActivityWindows` with `val ZONE: ZoneId`, `const val MAX_WINDOW_DAYS = 30`, `const val MAX_WINDOWS = 400`, `fun build(start: LocalDate, endInclusive: LocalDate, maxWindowDays: Int = MAX_WINDOW_DAYS): List<DateWindow>`, `fun formatStart(date: LocalDate): String`, `fun formatEnd(date: LocalDate): String`. Task 2 uses all of these.

- [ ] **Step 1: Write the failing test**

Create `QuestradeActivityWindowsTest.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

import org.junit.jupiter.api.Test
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class QuestradeActivityWindowsTest {

    @Test
    fun `single window for a 30-day inclusive span`() {
        val windows = QuestradeActivityWindows.build(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))
        assertEquals(listOf(DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30))), windows)
    }

    @Test
    fun `31-day span splits into 30 plus 1`() {
        val windows = QuestradeActivityWindows.build(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))
        assertEquals(
            listOf(
                DateWindow(LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 30)),
                DateWindow(LocalDate.of(2026, 1, 31), LocalDate.of(2026, 1, 31))
            ),
            windows
        )
    }

    @Test
    fun `windows are contiguous, non-overlapping and cover a 75-day span`() {
        val start = LocalDate.of(2026, 7, 1)
        val end = LocalDate.of(2026, 9, 13) // 75 days inclusive
        val windows = QuestradeActivityWindows.build(start, end)
        assertEquals(3, windows.size)
        assertEquals(start, windows.first().start)
        assertEquals(end, windows.last().endInclusive)
        windows.zipWithNext().forEach { (a, b) -> assertEquals(a.endInclusive.plusDays(1), b.start) }
    }

    @Test
    fun `rejects spans requiring more than 400 windows`() {
        assertFailsWith<IllegalArgumentException> {
            QuestradeActivityWindows.build(LocalDate.of(1900, 1, 1), LocalDate.of(2026, 1, 1))
        }
    }

    @Test
    fun `rejects an inverted span`() {
        assertFailsWith<IllegalArgumentException> {
            QuestradeActivityWindows.build(LocalDate.of(2026, 1, 10), LocalDate.of(2026, 1, 1))
        }
    }

    @Test
    fun `boundaries use the offset of their own timestamp across DST`() {
        // DST 2026: starts Sun Mar 8, ends Sun Nov 1
        assertEquals("2026-03-01T00:00:00-05:00", QuestradeActivityWindows.formatStart(LocalDate.of(2026, 3, 1)))
        assertEquals("2026-03-30T23:59:59-04:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 3, 30)))
        assertEquals("2026-11-01T00:00:00-04:00", QuestradeActivityWindows.formatStart(LocalDate.of(2026, 11, 1)))
        assertEquals("2026-11-01T23:59:59-05:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 11, 1)))
        assertEquals("2026-11-02T23:59:59-05:00", QuestradeActivityWindows.formatEnd(LocalDate.of(2026, 11, 2)))
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/broker-gateway && ./gradlew test --tests "com.portfolio.brokergateway.adapter.questrade.QuestradeActivityWindowsTest"`
Expected: compilation failure — `QuestradeActivityWindows` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `QuestradeActivityWindows.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

import java.time.LocalDate
import java.time.LocalTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.temporal.ChronoUnit
import java.time.format.DateTimeFormatter

data class DateWindow(val start: LocalDate, val endInclusive: LocalDate)

/**
 * Questrade's activities endpoint rejects ranges longer than 31 days (error 1003).
 * Plans contiguous, non-overlapping 30-day inclusive windows aligned to Eastern Time —
 * 30 (not 31) absorbs timezone/boundary slop. Offsets are computed per boundary so DST
 * is handled correctly.
 */
object QuestradeActivityWindows {

    const val MAX_WINDOW_DAYS = 30
    const val MAX_WINDOWS = 400
    val ZONE: ZoneId = ZoneId.of("America/Toronto")

    fun build(start: LocalDate, endInclusive: LocalDate, maxWindowDays: Int = MAX_WINDOW_DAYS): List<DateWindow> {
        require(maxWindowDays >= 1) { "maxWindowDays must be >= 1" }
        require(!start.isAfter(endInclusive)) { "start $start must not be after end $endInclusive" }
        val totalDays = ChronoUnit.DAYS.between(start, endInclusive) + 1
        val windowCount = (totalDays + maxWindowDays - 1) / maxWindowDays
        require(windowCount <= MAX_WINDOWS) {
            "Requested range $start..$endInclusive requires $windowCount windows, exceeding the $MAX_WINDOWS-window cap"
        }
        val windows = mutableListOf<DateWindow>()
        var windowStart = start
        while (!windowStart.isAfter(endInclusive)) {
            val windowEnd = minOf(windowStart.plusDays((maxWindowDays - 1).toLong()), endInclusive)
            windows += DateWindow(windowStart, windowEnd)
            windowStart = windowEnd.plusDays(1)
        }
        return windows
    }

    fun formatStart(date: LocalDate): String = format(date, LocalTime.MIDNIGHT)

    fun formatEnd(date: LocalDate): String = format(date, LocalTime.of(23, 59, 59))

    private fun format(date: LocalDate, time: LocalTime): String =
        OffsetDateTime.of(date, time, ZONE.rules.getOffset(date.atTime(time)))
            .format(DateTimeFormatter.ISO_OFFSET_DATE_TIME)
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/broker-gateway && ./gradlew test --tests "com.portfolio.brokergateway.adapter.questrade.QuestradeActivityWindowsTest"`
Expected: `BUILD SUCCESSFUL`, 6 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeActivityWindows.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeActivityWindowsTest.kt
git commit -m "fix(gateway): add ET-aware 30-day activity window planner"
```

---

### Task 2: Windowed activities fetch in QuestradeAdapter

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt` (replace `getActivities`, lines 101-127; update imports; update `capabilities()` at ~line 353)
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapterTest.kt` (add tests)

**Interfaces:**
- Consumes: `DateWindow`, `QuestradeActivityWindows` (Task 1).
- Produces: `internal fun activitiesPath(accountId: String, window: DateWindow): String` and `internal fun fetchActivitiesWindowed(windows: List<DateWindow>, fetch: (DateWindow) -> JsonNode): List<UnifiedActivity>`. No public API change — `getActivities(credentials, accountId, startDate, endDate)` keeps its exact signature.

- [ ] **Step 1: Write the failing tests**

Modify `QuestradeAdapterTest.kt` — add these imports at the top of the file (all existing imports stay):

```kotlin
import com.fasterxml.jackson.databind.ObjectMapper
import java.time.LocalDate
```

Then append these test methods inside the `QuestradeAdapterTest` class:

```kotlin
    @Test
    fun `activitiesPath renders each boundary with its own ET offset across DST`() {
        val path = adapter.activitiesPath(
            "12345",
            DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30))
        )
        assertEquals(
            "/v1/accounts/12345/activities?startTime=2026-03-01T00:00:00-05:00&endTime=2026-03-30T23:59:59-04:00",
            path
        )
    }

    @Test
    fun `fetchActivitiesWindowed merges all windows and sorts by trade date`() {
        val windows = listOf(
            DateWindow(LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 30)),
            DateWindow(LocalDate.of(2026, 3, 31), LocalDate.of(2026, 4, 29))
        )
        val mapper = ObjectMapper()
        val responses = mapOf(
            windows[0] to mapper.readTree(
                """{"activities":[
                    {"tradeDate":"2026-03-10","type":"Trades","action":"Buy","symbol":"AAA","netAmount":-10,"currency":"CAD"},
                    {"tradeDate":"2026-03-02","type":"Trades","action":"Buy","symbol":"BBB","netAmount":-20,"currency":"CAD"}
                ]}"""
            ),
            windows[1] to mapper.readTree(
                """{"activities":[
                    {"tradeDate":"2026-04-01","type":"Dividends","symbol":"CCC","netAmount":5,"currency":"CAD"}
                ]}"""
            )
        )

        val merged = adapter.fetchActivitiesWindowed(windows) { responses.getValue(it) }

        assertEquals(
            listOf(LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 10), LocalDate.of(2026, 4, 1)),
            merged.map { it.tradeDate }
        )
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend/broker-gateway && ./gradlew test --tests "com.portfolio.brokergateway.adapter.questrade.QuestradeAdapterTest"`
Expected: compilation failure — `activitiesPath` / `fetchActivitiesWindowed` unresolved.

- [ ] **Step 3: Implement**

In `QuestradeAdapter.kt`:

1. Add imports: `com.fasterxml.jackson.databind.JsonNode`; remove the now-unused `java.time.format.DateTimeFormatter`.
2. Replace the `getActivities` body (currently lines 101-127) with:

```kotlin
    override fun getActivities(
        credentials: BrokerCredentials, accountId: String,
        startDate: LocalDate?, endDate: LocalDate?
    ): List<UnifiedActivity> {
        val creds = credentials as BrokerCredentials.QuestradeCredentials
        val end = endDate ?: LocalDate.now(QuestradeActivityWindows.ZONE)
        val start = startDate ?: end.minusDays((QuestradeActivityWindows.MAX_WINDOW_DAYS - 1).toLong())
        val windows = QuestradeActivityWindows.build(start, end)
        return fetchActivitiesWindowed(windows) { window ->
            restClient.get(creds.apiServerUrl, creds.accessToken, activitiesPath(accountId, window))
        }
    }

    internal fun activitiesPath(accountId: String, window: DateWindow): String =
        "/v1/accounts/$accountId/activities" +
            "?startTime=${QuestradeActivityWindows.formatStart(window.start)}" +
            "&endTime=${QuestradeActivityWindows.formatEnd(window.endInclusive)}"

    internal fun fetchActivitiesWindowed(
        windows: List<DateWindow>,
        fetch: (DateWindow) -> JsonNode
    ): List<UnifiedActivity> =
        windows.flatMap { mapActivities(fetch(it)) }.sortedBy { it.tradeDate }

    private fun mapActivities(response: JsonNode): List<UnifiedActivity> {
        val activities = response.get("activities") ?: return emptyList()
        return activities.map { act ->
            UnifiedActivity(
                externalId = null,
                type = QuestradeDtoMappers.mapActivityType(act.get("type")?.asText(), act.get("action")?.asText()),
                symbol = act.get("symbol")?.asText(),
                description = act.get("description")?.asText(),
                quantity = act.get("quantity")?.decimalValue(),
                price = act.get("price")?.decimalValue(),
                amount = act.get("netAmount")?.decimalValue() ?: BigDecimal.ZERO,
                fee = act.get("commission")?.decimalValue(),
                currency = act.get("currency")?.asText() ?: "CAD",
                tradeDate = act.get("tradeDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) } ?: LocalDate.now(),
                settlementDate = act.get("settlementDate")?.asText()?.let { LocalDate.parse(it.substring(0, 10)) },
                optionType = null
            )
        }
    }
```

3. In `capabilities()`, change `activityHistoryDepth = "Unlimited"` to `activityHistoryDepth = "Full history via 30-day windows (31-day API cap)"` (spec §5.1).

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend/broker-gateway && ./gradlew test --tests "com.portfolio.brokergateway.adapter.questrade.QuestradeAdapterTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

Then run the full module suite: `cd backend/broker-gateway && ./gradlew test`
Expected: `BUILD SUCCESSFUL`.

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapter.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeAdapterTest.kt
git commit -m "fix(gateway): fetch Questrade activities in bounded ET-aligned windows"
```

---

### Task 3: Activity fingerprint utility (portfolio, pure)

**Files:**
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityFingerprint.kt`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityFingerprintTest.kt`

**Interfaces:**
- Consumes: nothing.
- Produces: `object ActivityFingerprint` with `fun of(type: String, symbol: String?, description: String?, quantity: BigDecimal?, price: BigDecimal?, amount: BigDecimal, fee: BigDecimal?, currency: String?, tradeDate: LocalDate, settlementDate: LocalDate?, optionType: String?): String` (64-char lowercase SHA-256 hex). Tasks 4 and 5 use it.

- [ ] **Step 1: Write the failing test**

Create `ActivityFingerprintTest.kt`:

```kotlin
package com.portfolio.broker.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals

class ActivityFingerprintTest {

    @Test
    fun `golden vector - fully populated trade`() {
        val fingerprint = ActivityFingerprint.of(
            type = "BUY", symbol = "AAPL", description = "Buy 10 AAPL",
            quantity = BigDecimal("10.000000"), price = BigDecimal("150.500000"),
            amount = BigDecimal("-1505.00"), fee = BigDecimal("1.0000"),
            currency = "USD", tradeDate = LocalDate.of(2026, 3, 2),
            settlementDate = LocalDate.of(2026, 3, 4), optionType = null
        )
        assertEquals("e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6", fingerprint)
    }

    @Test
    fun `golden vector - null fields collapse to empty strings`() {
        val fingerprint = ActivityFingerprint.of(
            type = "DIVIDEND", symbol = null, description = null,
            quantity = null, price = null, amount = BigDecimal("12.34"), fee = null,
            currency = "CAD", tradeDate = LocalDate.of(2026, 1, 5),
            settlementDate = null, optionType = null
        )
        assertEquals("3abff73fcb7ff9f6b1abd724af73610663fb9b9b9f702439a3e6ebc5b2b0d154", fingerprint)
    }

    @Test
    fun `decimal scale does not change the fingerprint`() {
        val a = ActivityFingerprint.of(
            "BUY", "AAPL", "Buy 10 AAPL", BigDecimal("10"), BigDecimal("150.5"),
            BigDecimal("-1505"), BigDecimal("1"), "USD", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), null
        )
        val b = ActivityFingerprint.of(
            "BUY", "AAPL", "Buy 10 AAPL", BigDecimal("10.000000"), BigDecimal("150.500000"),
            BigDecimal("-1505.00"), BigDecimal("1.0000"), "USD", LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 4), null
        )
        assertEquals(a, b)
        assertEquals("e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6", a)
    }

    @Test
    fun `different amount produces a different fingerprint`() {
        val base = ActivityFingerprint.of(
            "BUY", "AAPL", null, null, null, BigDecimal("-10"), null, "CAD",
            LocalDate.of(2026, 3, 2), null, null
        )
        val other = ActivityFingerprint.of(
            "BUY", "AAPL", null, null, null, BigDecimal("-20"), null, "CAD",
            LocalDate.of(2026, 3, 2), null, null
        )
        assertNotEquals(base, other)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityFingerprintTest"`
Expected: compilation failure — `ActivityFingerprint` unresolved.

- [ ] **Step 3: Write minimal implementation**

Create `ActivityFingerprint.kt`:

```kotlin
package com.portfolio.broker.service

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Stable dedup key for broker activities that carry no broker-assigned id (Questrade).
 * The canonical string is fixed-order and unit-separator joined; decimals are normalized
 * with stripTrailingZeros so the stored DECIMAL scale never changes the hash.
 */
object ActivityFingerprint {

    private const val SEPARATOR = "\u001F"

    fun of(
        type: String,
        symbol: String?,
        description: String?,
        quantity: BigDecimal?,
        price: BigDecimal?,
        amount: BigDecimal,
        fee: BigDecimal?,
        currency: String?,
        tradeDate: LocalDate,
        settlementDate: LocalDate?,
        optionType: String?
    ): String {
        val canonical = listOf(
            type,
            symbol ?: "",
            description ?: "",
            decimal(quantity),
            decimal(price),
            decimal(amount),
            decimal(fee),
            currency ?: "",
            tradeDate.toString(),
            settlementDate?.toString() ?: "",
            optionType ?: ""
        ).joinToString(SEPARATOR)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun decimal(value: BigDecimal?): String =
        value?.stripTrailingZeros()?.toPlainString() ?: ""
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityFingerprintTest"`
Expected: `BUILD SUCCESSFUL`, 4 tests passed.

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityFingerprint.kt backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityFingerprintTest.kt
git commit -m "feat(portfolio): add stable activity fingerprint key"
```

---

### Task 4: Use the fingerprint in activity ingestion

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt` (`processAndSaveActivities`, lines 144-194)
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt` (add tests)

**Interfaces:**
- Consumes: `ActivityFingerprint.of(...)` (Task 3); existing `BrokerActivityRepository.findByConnectionIdAndExternalId`.
- Produces: no signature change; behavior: rows without a broker `externalId` get the fingerprint stored in `BrokerActivity.externalId`.

- [ ] **Step 1: Write the failing tests**

Append to `ActivityIngestionServiceTest.kt`:

```kotlin
    private fun questradeActivityWithoutExternalId(): Map<String, Any?> = mapOf(
        "type" to "BUY",
        "symbol" to "AAPL",
        "description" to "Buy 10 AAPL",
        "quantity" to 10.0,
        "price" to 150.5,
        "amount" to -1505.0,
        "fee" to 1.0,
        "currency" to "USD",
        "tradeDate" to "2026-03-02",
        "settlementDate" to "2026-03-04",
        "optionType" to null
    )

    @Test
    fun `incremental sync stores fingerprint as external id when broker id is absent`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2026, 3, 1)
        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns
            buildActivitiesJson(questradeActivityWithoutExternalId())

        val fingerprint = "e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6"
        every { activityRepository.findByConnectionIdAndExternalId(10L, fingerprint) } returns null

        val slot = slot<BrokerActivity>()
        every { activityRepository.save(capture(slot)) } answers { slot.captured }

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(1, count)
        assertEquals(fingerprint, slot.captured.externalId)
    }

    @Test
    fun `incremental sync skips an activity already present by fingerprint`() {
        every { connectionRepository.findById(10L) } returns Optional.of(mockConnection)
        every { activityRepository.findLatestTradeDateByConnectionId(10L) } returns LocalDate.of(2026, 3, 1)
        every { gatewayClient.getActivities("gw-conn-123", "ext-account-123", any(), any()) } returns
            buildActivitiesJson(questradeActivityWithoutExternalId())
        every {
            activityRepository.findByConnectionIdAndExternalId(
                10L, "e23ae395c505668a37360d33d08196a2ff38fed00be4d76e3e2330bf0d6e4da6"
            )
        } returns mockk(relaxed = true)

        val count = service.syncActivitiesForConnection(10L)

        assertEquals(0, count)
        verify(exactly = 0) { activityRepository.save(any<BrokerActivity>()) }
    }
```

Note the JSON numbers use Double literal width (`10.0`, `150.5`, `-1505.0`, `1.0`) — `BigDecimal(value).stripTrailingZeros()` normalizes them to `10`, `150.5`, `-1505`, `1`, matching the golden vector.

- [ ] **Step 2: Run tests to verify they fail**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityIngestionServiceTest"`
Expected: the two new tests FAIL — saved `externalId` is `null` (fingerprint not implemented yet).

- [ ] **Step 3: Implement**

Replace `processAndSaveActivities` (currently lines 144-194) with:

```kotlin
    private fun processAndSaveActivities(activities: JsonNode, connection: BrokerConnection): Int {
        var insertedCount = 0
        for (activity in activities) {
            val tradeDate = parseJsonLocalDate(activity.path("tradeDate")) ?: continue

            val rawAmount = if (activity.has("amount") && !activity.path("amount").isNull)
                BigDecimal(activity.path("amount").asText()) else BigDecimal.ZERO
            val currency = activity.path("currency").asText("CAD")
            val type = activity.path("type").asText("OTHER")
            val symbol = activity.path("symbol").asText(null)
            val description = activity.path("description").asText(null)
            val quantity = if (activity.has("quantity") && !activity.path("quantity").isNull)
                BigDecimal(activity.path("quantity").asText()) else null
            val price = if (activity.has("price") && !activity.path("price").isNull)
                BigDecimal(activity.path("price").asText()) else null
            val fee = if (activity.has("fee") && !activity.path("fee").isNull)
                BigDecimal(activity.path("fee").asText()) else null
            val settlementDate = parseJsonLocalDate(activity.path("settlementDate"))
            val optionType = activity.path("optionType").asText(null)

            // Questrade activities carry no broker-assigned id; derive a stable fingerprint so
            // re-syncing overlapping windows stays idempotent. Wealthsimple supplies a
            // canonicalId as externalId and keeps using it directly.
            val key = activity.path("externalId").asText(null)
                ?: ActivityFingerprint.of(
                    type, symbol, description, quantity, price, rawAmount, fee,
                    currency, tradeDate, settlementDate, optionType
                )

            if (activityRepository.findByConnectionIdAndExternalId(connection.id, key) != null) continue

            val (amountCad, exchangeRate) = computeCadAmount(rawAmount, currency, tradeDate, type)

            val entity = BrokerActivity(
                connection = connection,
                externalId = key,
                type = type,
                symbol = symbol,
                description = description,
                quantity = quantity,
                price = price,
                amount = rawAmount,
                fee = fee,
                currency = currency,
                tradeDate = tradeDate,
                settlementDate = settlementDate,
                accountName = connection.accountName,
                optionType = optionType,
                amountCad = amountCad,
                exchangeRate = exchangeRate,
                rawPayload = objectMapper.writeValueAsString(activity)
            )
            activityRepository.save(entity)
            insertedCount++
        }
        return insertedCount
    }
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityIngestionServiceTest"`
Expected: `BUILD SUCCESSFUL` — all existing tests plus the two new ones pass.

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt
git commit -m "fix(portfolio): dedupe broker activities without broker ids"
```

---

### Task 5: Backfill planner + V77 repair migration

**Files:**
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityBackfillPlanner.kt`
- Create: `backend/portfolio/src/main/kotlin/db/migration/V77__Activity_fingerprint_backfill.kt`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityBackfillPlannerTest.kt`

**Interfaces:**
- Consumes: `ActivityFingerprint.of(...)` (Task 3).
- Produces: `data class ActivityRowForBackfill(...)` and `object ActivityBackfillPlanner { data class BackfillPlan(val fingerprintById: Map<Long, String>, val duplicateIdsToDelete: List<Long>); fun plan(rows: List<ActivityRowForBackfill>): BackfillPlan }`. The migration is the only consumer.

**Before starting:** check the latest migration on `origin/main` — if `V77` is already taken by concurrent work, renumber both the file and the class name (`V78__...`). Run: `git ls-tree --name-only origin/main backend/portfolio/src/main/resources/db/migration/ | tail -3`.

- [ ] **Step 1: Write the failing test**

Create `ActivityBackfillPlannerTest.kt`:

```kotlin
package com.portfolio.broker.service

import org.junit.jupiter.api.Test
import java.math.BigDecimal
import java.time.LocalDate
import kotlin.test.assertEquals

class ActivityBackfillPlannerTest {

    private fun row(
        id: Long,
        connectionId: Long = 1L,
        amount: String = "-10.00",
        symbol: String? = "AAPL"
    ) = ActivityRowForBackfill(
        id = id, connectionId = connectionId, type = "BUY", symbol = symbol, description = "Buy",
        quantity = BigDecimal("10.000000"), price = BigDecimal("1.000000"), amount = BigDecimal(amount),
        fee = BigDecimal("1.0000"), currency = "CAD", tradeDate = LocalDate.of(2026, 3, 2),
        settlementDate = LocalDate.of(2026, 3, 4), optionType = null
    )

    @Test
    fun `keeps the earliest id and marks later duplicates for deletion`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(9), row(5)))
        assertEquals(listOf(9L), plan.duplicateIdsToDelete)
        assertEquals(setOf(5L), plan.fingerprintById.keys)
    }

    @Test
    fun `identical activities in different connections are not duplicates`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(1, connectionId = 1L), row(2, connectionId = 2L)))
        assertEquals(emptyList<Long>(), plan.duplicateIdsToDelete)
        assertEquals(setOf(1L, 2L), plan.fingerprintById.keys)
    }

    @Test
    fun `different amounts are not duplicates`() {
        val plan = ActivityBackfillPlanner.plan(listOf(row(1, amount = "-10.00"), row(2, amount = "-20.00")))
        assertEquals(emptyList<Long>(), plan.duplicateIdsToDelete)
        assertEquals(setOf(1L, 2L), plan.fingerprintById.keys)
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityBackfillPlannerTest"`
Expected: compilation failure — `ActivityBackfillPlanner` / `ActivityRowForBackfill` unresolved.

- [ ] **Step 3: Implement the planner**

Create `ActivityBackfillPlanner.kt`:

```kotlin
package com.portfolio.broker.service

import java.math.BigDecimal
import java.time.LocalDate

data class ActivityRowForBackfill(
    val id: Long,
    val connectionId: Long,
    val type: String,
    val symbol: String?,
    val description: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val currency: String?,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate?,
    val optionType: String?
)

/**
 * Computes the fingerprint backfill for rows that predate the dedup key. Rows are grouped
 * per (connection, fingerprint); the earliest id is kept and the rest are deleted.
 */
object ActivityBackfillPlanner {

    data class BackfillPlan(
        val fingerprintById: Map<Long, String>,
        val duplicateIdsToDelete: List<Long>
    )

    fun plan(rows: List<ActivityRowForBackfill>): BackfillPlan {
        val keptByKey = LinkedHashMap<Pair<Long, String>, Long>()
        val fingerprintById = LinkedHashMap<Long, String>()
        val duplicateIds = mutableListOf<Long>()
        for (row in rows.sortedBy { it.id }) {
            val fingerprint = ActivityFingerprint.of(
                row.type, row.symbol, row.description, row.quantity, row.price,
                row.amount, row.fee, row.currency, row.tradeDate, row.settlementDate, row.optionType
            )
            val key = row.connectionId to fingerprint
            if (keptByKey.containsKey(key)) {
                duplicateIds += row.id
            } else {
                keptByKey[key] = row.id
                fingerprintById[row.id] = fingerprint
            }
        }
        return BackfillPlan(fingerprintById, duplicateIds)
    }
}
```

- [ ] **Step 4: Implement the migration**

Create `backend/portfolio/src/main/kotlin/db/migration/V77__Activity_fingerprint_backfill.kt` (rename class + file if renumbering per the note above):

```kotlin
package db.migration

import com.portfolio.broker.service.ActivityBackfillPlanner
import com.portfolio.broker.service.ActivityRowForBackfill
import org.flywaydb.core.api.migration.BaseJavaMigration
import org.flywaydb.core.api.migration.Context
import java.sql.Connection

/**
 * One-time repair for broker_activities rows created before the fingerprint dedup key:
 * backfills external_id for rows without a broker id and removes duplicates, keeping the
 * earliest row per (connection, fingerprint). Only rows with NULL external_id are touched.
 */
class V77__Activity_fingerprint_backfill : BaseJavaMigration() {

    override fun migrate(context: Context) {
        val connection = context.connection
        val rows = loadRows(connection)
        if (rows.isEmpty()) return

        val plan = ActivityBackfillPlanner.plan(rows)

        connection.prepareStatement("UPDATE broker_activities SET external_id = ? WHERE id = ?").use { ps ->
            for ((id, fingerprint) in plan.fingerprintById) {
                ps.setString(1, fingerprint)
                ps.setLong(2, id)
                ps.addBatch()
            }
            ps.executeBatch()
        }

        connection.prepareStatement("DELETE FROM broker_activities WHERE id = ?").use { ps ->
            for (id in plan.duplicateIdsToDelete) {
                ps.setLong(1, id)
                ps.addBatch()
            }
            ps.executeBatch()
        }
    }

    private fun loadRows(connection: Connection): List<ActivityRowForBackfill> {
        val rows = mutableListOf<ActivityRowForBackfill>()
        connection.prepareStatement(
            """
            SELECT id, connection_id, type, symbol, description, quantity, price, amount, fee,
                   currency, trade_date, settlement_date, option_type
            FROM broker_activities
            WHERE external_id IS NULL
            ORDER BY id
            """.trimIndent()
        ).use { statement ->
            statement.executeQuery().use { rs ->
                while (rs.next()) {
                    rows += ActivityRowForBackfill(
                        id = rs.getLong("id"),
                        connectionId = rs.getLong("connection_id"),
                        type = rs.getString("type"),
                        symbol = rs.getString("symbol"),
                        description = rs.getString("description"),
                        quantity = rs.getBigDecimal("quantity"),
                        price = rs.getBigDecimal("price"),
                        amount = rs.getBigDecimal("amount"),
                        fee = rs.getBigDecimal("fee"),
                        currency = rs.getString("currency") ?: "CAD",
                        tradeDate = rs.getDate("trade_date").toLocalDate(),
                        settlementDate = rs.getDate("settlement_date")?.toLocalDate(),
                        optionType = rs.getString("option_type")
                    )
                }
            }
        }
        return rows
    }
}
```

- [ ] **Step 5: Run tests + full portfolio suite**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --tests "com.portfolio.broker.service.ActivityBackfillPlannerTest"`
Expected: `BUILD SUCCESSFUL`, 3 tests passed.

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration`
Expected: `BUILD SUCCESSFUL` (full unit suite).

- [ ] **Step 6: Verify the migration class is discoverable**

Run: `cd backend/portfolio && ./gradlew compileKotlin && find build/classes -name "V77__Activity_fingerprint_backfill.class"`
Expected: prints `.../db/migration/V77__Activity_fingerprint_backfill.class` — package `db.migration` matches `spring.flyway.locations=classpath:db/migration` (`application.yml:32-34`). Actual application against Postgres happens on deploy; verified in Task 7 Step 1 (`flyway_schema_history`).

- [ ] **Step 7: Commit**

```bash
git add backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityBackfillPlanner.kt backend/portfolio/src/main/kotlin/db/migration/V77__Activity_fingerprint_backfill.kt backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityBackfillPlannerTest.kt
git commit -m "fix(portfolio): backfill activity fingerprints and remove duplicates"
```

---

### Task 6: ADR-0034 + schema doc note

**Files:**
- Modify: `docs/adr.md` (append after the last entry)
- Modify: `docs/reference/database-schema.md` (broker_activities section)

**Before starting:** run `grep -n "^## ADR-" docs/adr.md | tail -2` — the local tree already contains `ADR-0033` (concurrent strategy work), so this plan uses **ADR-0034**. If `origin/main`'s last ADR differs at implementation time, renumber to the next free number, and keep the migration number in the ADR text in sync with Task 5's actually-committed V-number.

**Interfaces:**
- Consumes: Task 4 semantics; Task 5 migration.
- Produces: documented decision record (repo contract requires it in the same PR).

- [ ] **Step 1: Append ADR-0034**

Append to `docs/adr.md`:

```markdown
## ADR-0034: Stable activity dedup key for brokers without activity IDs
**Status:** Accepted | **Date:** 2026-09-22
**Context:** Questrade's activities API exposes no unique id per record, and the gateway adapter mapped `externalId = null`. The pre-existing `uq_activity_external UNIQUE (connection_id, external_id)` constraint does not apply to NULLs in PostgreSQL, so the portfolio's dedup lookup never ran: every re-sync of an overlapping window (the incremental path re-fetches one day by design) inserted duplicate rows. UAT had accumulated 29 duplicate groups; UAT activities had also been frozen since 2026-07-17 because the incremental path requested an unbounded range that Questrade rejects beyond 31 days (issue #268).
**Decision:** The gateway now transparently splits any activities range into ET-aligned 30-day windows (merge + sort), and the portfolio computes a SHA-256 fingerprint over the canonical activity fields when the broker supplies no `externalId`, storing it in `broker_activities.external_id`. The existing lookup and unique constraint then apply. Flyway migration `V77` backfills fingerprints for pre-existing rows and deletes duplicates, keeping the earliest row per `(connection, fingerprint)`.
**Consequences:** Re-syncing any window is idempotent for Questrade; activity-derived numbers (dividends, fees, IRR/XIRR) stop inflating. Two byte-identical fills on the same day collapse into one row (accepted and monitored; an occurrence counter would be added only if observed in practice). `external_id` now means "broker-assigned id or stable fingerprint" — consumers must not assume broker provenance.
```

- [ ] **Step 2: Update the schema reference**

Run: `grep -n "external_id" docs/reference/database-schema.md`
Expected: two matches in the `broker_activities` section — a column-table row and the unique-constraint bullet. Replace the constraint bullet's description with the semantics note below (and mirror the fingerprint mention in the column row), keeping surrounding structure:

```markdown
- `external_id` — broker-assigned id when available (e.g. Wealthsimple `canonicalId`); otherwise a SHA-256 fingerprint of the activity fields computed by `ActivityFingerprint` (see ADR-0034). `uq_activity_external UNIQUE (connection_id, external_id)` enforces dedup; note NULLs bypass this constraint, which is why brokers without ids must use the fingerprint.
```

- [ ] **Step 3: Commit**

```bash
git add docs/adr.md docs/reference/database-schema.md
git commit -m "docs: record ADR-0034 activity dedup key semantics"
```

---

### Task 7: UAT live verification (post-deploy)

**Files:** none — operational verification. Runs after the branch merges and UAT auto-deploys.

**Before merging:** record the current fees total and 12-month dividends total (Dashboard Fees card / dividend calendar, or `GET /api/v1/brokers/reporting/performance`) — removing the 29 duplicate rows will shift them; Step 3 compares against this capture.

**Interfaces:**
- Consumes: deployed Task 1-5 changes.
- Produces: evidence that activities resume, duplicates are gone, and the 1003s stop.

- [ ] **Step 1: Confirm the migration applied**

Run: `ssh ssh.nanobyte.ca "PGPW=\$(docker exec uat-postgres printenv POSTGRES_PASSWORD); docker exec -e PGPASSWORD=\"\$PGPW\" uat-postgres psql -U portfolio -d portfolio -c \"SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 3\""`
Expected: top row `77 | Activity fingerprint backfill | t`.

- [ ] **Step 2: Verify duplicates are gone and keys are backfilled**

Run the same psql wrapper with:
```sql
SELECT connection_id, count(*) AS rows,
       count(*) FILTER (WHERE external_id IS NULL) AS null_keys,
       max(trade_date) AS newest
FROM broker_activities GROUP BY connection_id ORDER BY connection_id;
```
Expected: `null_keys = 0` on all connections; `rows` lower by each connection's duplicate count (29 excess rows total across the 6 connections).

- [ ] **Step 3: Trigger a sync and confirm activities resume**

Click "Sync Activities" for the ACTIVE connections (Broker Connections page), or wait for the next scheduled run.
Expected: no error toast; the Activities grid shows transactions through today (previously frozen at July 3-10); `last_activities_fetched_at` on ACTIVE connections advances past 2026-07-17; fees/dividends totals shift vs the pre-merge capture by the removed duplicates.

- [ ] **Step 4: Confirm no range errors in the gateway**

Run: `ssh ssh.nanobyte.ca "docker logs uat-portfolio-broker-gateway --since 30m 2>&1 | grep -c '1003'"`
Expected: `0`. Then run without `grep -c` and confirm lines like `Incremental sync ... startDate=...` in the backend log and no `BROKER_DATA_ERROR`.

- [ ] **Step 5: Re-check idempotency**

Trigger the sync a second time, then re-run the Step 2 query.
Expected: `rows` unchanged (no new duplicates); `newest` unchanged or newer only if new trades occurred.

---

## Execution notes

- Order matters: Tasks 1→2 (gateway) and 3→4→5 (portfolio) are independent lanes; Task 6 depends on 4-5; Task 7 depends on deployment. Tasks 2 and 5 touch files also modified by PR #272 (strategy) — expect a rebase.
- If Task 5's migration numbering collides with concurrent work, renumber (V78+) before committing.
- If Task 7 Step 1 shows no V77 row (Flyway did not discover the class), fall back to spec §5.3's alternative: a guarded one-shot runner reusing `ActivityBackfillPlanner`, invoked manually against a backup.
- **Deviation from spec §5.4 (intentional):** migration logic is unit-tested via the pure `ActivityBackfillPlanner`; a Testcontainers test that seeds duplicates *before* V77 runs would require a bespoke Flyway target-version harness the repo does not have, and portfolio integration tests are excluded from CI unit runs. The migration itself is verified live in Task 7 (flyway history + backfill/duplicate queries).
- All tests must pass before merge: gateway `./gradlew test`, portfolio `./gradlew test -PexcludeIntegration`.
