# Broker Sync Correctness — Phase 2 (Sync Robustness) Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make broker sync robust — per-chunk transactions with durable resume, honest watermarks, gateway HTTP timeouts/retry/rate-limiting, expiry-based token maintenance, accurate error propagation, single-flight sync guards, ET schedulers, and a 5-year lookback.

**Architecture:** Two modules change. Portfolio (`backend/portfolio`) restructures `ActivityIngestionService` from one-transaction-per-full-history into per-chunk `TransactionTemplate` units with a durable `broker_sync_progress` table (V78), adds sync-status fields surfaced through `BrokerConnectionDto` and the UI, maps `GatewayApiException` to 502 with broker detail, and guards sync entry points with an in-process per-connection lock. Broker-gateway (`backend/broker-gateway`) gives every outbound WebClient a connect+response timeout, adds retry-with-backoff (429 honoring `Retry-After`, transient 5xx) plus a fixed-rate pacer wired to the existing dead `rate-limit-per-second` config, and replaces the 5-minute full-`validateConnection` polling in `TokenRefreshScheduler` with expiry-aware lightweight maintenance plus failure backoff.

**Tech Stack:** Kotlin, Spring Boot, WebClient (reactor-netty), Flyway (SQL + class-based), MockK, MockWebServer (new gateway test dep), JUnit5.

## Global Constraints

- Spec: `docs/superpowers/specs/2026-09-22-broker-sync-correctness-design.md` §6 (lines 136–144) is the requirements source; Phase 1 (PR #273, merged `cba70c4`) is already on `main`.
- Build needs `export JAVA_HOME=/tmp/opencode/jdk21` (no system java).
- Portfolio tests: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain`. Gateway tests: `cd backend/broker-gateway && ./gradlew test --console=plain`.
- Never add `Co-Authored-By:` or any AI-attribution lines to commits. Never push unless the human asks.
- DB schema change (V78) requires an ADR entry (**ADR-0036** — ADR-0035 was taken by "Regression UI tests target deployed UAT" after the Phase 1 baseline; re-check the tail of `docs/adr.md` before writing) and `docs/reference/database-schema.md` update in the same PR.
- `broker.sync.max-lookback-years` default changes 30 → **5** (human decision 2026-09-24; Questrade retention ~16 months).
- Schedulers get ET wall-clock via `@Scheduled(zone = "America/Toronto")` in code — **no compose/TZ changes** (avoids infra ADR).
- Single-flight is in-process only (single-replica deployment); note ShedLock as the multi-replica path in a comment — do not add the dependency.
- Do not change the incremental-sync call contract (`endDate = null` is pinned by an existing test and is correct — gateway-side windowing owns the range).
- Existing test style: plain unit tests, MockK, no Spring context. Portfolio has `mockwebserver 4.12.0` available; gateway does not (Task 1 adds it).
- Phase 1 established class-based Flyway in `backend/portfolio/src/main/kotlin/db/migration/`; plain SQL migrations live in `backend/portfolio/src/main/resources/db/migration/` (latest: V76; V77 used).

---

## File Structure

```
backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/
  adapter/questrade/QuestradeConfig.kt          — add responseTimeoutMs/connectTimeoutMs
  adapter/questrade/QuestradeRestClient.kt      — timeouts, Retry-After parsing, retry+backoff, pacer
  adapter/questrade/QuestradeTokenManager.kt    — timeout
  adapter/questrade/QuestradeRateLimiter.kt     — NEW: fixed-rate pacer (reads rateLimitPerSecond)
  adapter/questrade/HttpRetryPolicy.kt          — NEW: pure backoff computation
  adapter/wealthsimple/WealthsimpleConfig.kt    — add responseTimeoutMs/connectTimeoutMs
  adapter/wealthsimple/WealthsimpleGraphQlClient.kt — timeouts
  adapter/wealthsimple/WealthsimpleTokenManager.kt  — timeout
  scheduler/TokenRefreshScheduler.kt            — expiry-aware, no validate polling, backoff
backend/broker-gateway/src/main/resources/application.yml — timeout keys
backend/broker-gateway/build.gradle.kts         — mockwebserver test dep
backend/portfolio/src/main/kotlin/com/portfolio/
  broker/service/ActivityIngestionService.kt    — per-chunk tx, progress/resume, honest watermark, single-flight
  broker/service/BrokerSyncProgressService.kt   — NEW: durable progress CRUD
  broker/entity/BrokerSyncProgress.kt           — NEW entity
  broker/repository/BrokerSyncProgressRepository.kt — NEW repo
  broker/entity/BrokerConnection.kt             — add lastActivitiesSyncStatus/lastBalanceSyncStatus
  broker/service/BrokerService.kt               — single-flight for manual fetch, /sync-all per-step
  broker/controller/BrokerController.kt         — /sync-all accurate per-step results
  config/GlobalExceptionHandler.kt              — GatewayApiException → 502
  broker/client/BrokerGatewayClient.kt          — (read-only; exception already carries detail)
backend/portfolio/src/main/resources/
  db/migration/V78__broker_sync_progress.sql    — NEW table
  application.yml                               — max-lookback-years 5
backend/portfolio/src/test/kotlin/...           — per-task tests
frontend/src/services/brokerService.ts          — DTO fields
frontend/src/components/broker/BrokerConnectionCard.tsx — staleness display
docs/adr.md                                     — ADR-0036
docs/reference/database-schema.md               — broker_sync_progress table
```

---

### Task 1: Gateway HTTP timeouts on all outbound broker clients

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeConfig.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClient.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeTokenManager.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleConfig.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleGraphQlClient.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/wealthsimple/WealthsimpleTokenManager.kt`
- Modify: `backend/broker-gateway/src/main/resources/application.yml`
- Modify: `backend/broker-gateway/build.gradle.kts` (add mockwebserver test dep)
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClientTimeoutTest.kt`

**Interfaces:**
- Consumes: existing `QuestradeConfig`/`WealthsimpleConfig` data classes (read by adapters via ctor).
- Produces: every gateway→broker HTTP call now fails fast with `BrokerConnectionException` (or `WebClientRequestException` cause) instead of hanging indefinitely; config keys `response-timeout-ms` / `connect-timeout-ms` under `broker-gateway.questrade` and `broker-gateway.wealthsimple`.

- [ ] **Step 1: Add mockwebserver to gateway test deps**

In `backend/broker-gateway/build.gradle.kts`, inside `dependencies { }` (after the mockk line, `build.gradle.kts:48-53`):

```kotlin
testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
```

- [ ] **Step 2: Write the failing timeout test**

Create `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClientTimeoutTest.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test
import java.util.concurrent.TimeUnit

class QuestradeRestClientTimeoutTest {

    private lateinit var server: MockWebServer

    @AfterEach
    fun tearDown() {
        if (::server.isInitialized) server.shutdown()
    }

    @Test
    fun `get aborts when the broker never responds within the response timeout`() {
        server = MockWebServer()
        server.enqueue(MockResponse().setBodyDelay(3, TimeUnit.SECONDS).setBody("{}"))
        server.start()

        // the client builds its own connector from responseTimeoutMs (Step 3);
        // keep the existing get(apiServerUrl, accessToken, path) parameter order
        val client = QuestradeRestClient(responseTimeoutMs = 500)

        val started = System.nanoTime()
        val result = runCatching {
            client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts")
        }
        val elapsedMs = (System.nanoTime() - started) / 1_000_000

        assertThat(result.isFailure).isTrue()
        assertThat(elapsedMs).isLessThan(2500) // aborted well before the 3s body
    }
}
```

Note: apply Step 3's constructor change first (adds `responseTimeoutMs`/`connectTimeoutMs` params and the connector), then run this test.

- [ ] **Step 3: Add timeout config and wire connectors**

`QuestradeConfig.kt` — add two fields (keep existing ones and yml binding):

```kotlin
val responseTimeoutMs: Long = 30_000,
val connectTimeoutMs: Int = 10_000,
```

`WealthsimpleConfig.kt` — same two fields with the same defaults.

`QuestradeRestClient.kt` — replace the ctor-default builder and `buildClient` (`:14-16`, `:66-71`) so every request client carries timeouts:

```kotlin
class QuestradeRestClient(
    private val webClientBuilder: WebClient.Builder = WebClient.builder(),
    private val responseTimeoutMs: Long = 30_000,
    private val connectTimeoutMs: Int = 10_000,
) {
    // existing call sites keep their signatures; buildClient becomes:
    private fun buildClient(baseUrl: String, token: String): WebClient {
        val httpClient = HttpClient.create()
            .option(ChannelOption.CONNECT_TIMEOUT_MILLIS, connectTimeoutMs)
            .responseTimeout(Duration.ofMillis(responseTimeoutMs))
        return webClientBuilder
            .baseUrl(baseUrl)
            .clientConnector(ReactorClientHttpConnector(httpClient))
            .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer $token")
            .build()
    }
}
```

with imports `io.netty.channel.ChannelOption`, `org.springframework.http.client.reactive.ReactorClientHttpConnector`, `reactor.netty.http.client.HttpClient`, `java.time.Duration`. Every `.block()` in this client stays as-is — the connector now bounds it.

`QuestradeTokenManager.kt` — change `.block()` (`:27`) to `.block(Duration.ofSeconds(10))`.
`WealthsimpleGraphQlClient.kt` — same connector pattern in its client build (`:34-40`), using `WealthsimpleConfig`'s new fields.
`WealthsimpleTokenManager.kt` — `.block(Duration.ofSeconds(10))` (`:34`).

`application.yml` — under `broker-gateway.questrade` (`:55-60`) add:

```yaml
  response-timeout-ms: ${BROKER_GATEWAY_HTTP_TIMEOUT_MS:30000}
  connect-timeout-ms: ${BROKER_GATEWAY_HTTP_CONNECT_TIMEOUT_MS:10000}
```

and the same two keys under `broker-gateway.wealthsimple` (`:61-66`). Adapters pass the config values into the clients they construct — update the hard-new in `QuestradeAdapter.kt:20` to `private val restClient = QuestradeRestClient(responseTimeoutMs = config.responseTimeoutMs, connectTimeoutMs = config.connectTimeoutMs)` and the analogous constructions in `QuestradeAdapter.kt:21`, `WealthsimpleAdapter.kt:20` (token managers keep their 10s block timeout).

- [ ] **Step 4: Run tests**

Run: `cd backend/broker-gateway && ./gradlew test --console=plain`
Expected: PASS (new timeout test + full existing suite green).

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/build.gradle.kts backend/broker-gateway/src/main backend/broker-gateway/src/test
git commit -m "fix(gateway): bound outbound broker HTTP with connect and response timeouts"
```

---

### Task 2: Gateway retry with backoff + Retry-After + wired rate limiter

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/HttpRetryPolicy.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRateLimiter.kt`
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClient.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/HttpRetryPolicyTest.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/adapter/questrade/QuestradeRestClientRetryTest.kt`

**Interfaces:**
- Consumes: `BrokerRateLimitException(retryAfterSeconds: Int?)` (`Exceptions.kt:24-27`); `QuestradeConfig.rateLimitPerSecond` (currently dead, `QuestradeConfig.kt:11`).
- Produces: `HttpRetryPolicy.delayMs(attempt: Int, retryAfterSeconds: Int?, jitter: Double): Long` (pure); `QuestradeRateLimiter.acquire()`; `QuestradeRestClient.get(apiServerUrl, accessToken, path)` (existing parameter order unchanged) retries 429/transient-5xx up to 3 attempts total before propagating. **POST/DELETE are paced but never retried** (order placement must not double-submit).

- [ ] **Step 1: Write the failing pure-policy test**

Create `HttpRetryPolicyTest.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test

class HttpRetryPolicyTest {
    private val policy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 1000, capDelayMs = 60_000)

    @Test
    fun `honors Retry-After over exponential when larger`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = 5, jitter = 0.0)).isEqualTo(5000)
    }
    @Test
    fun `exponential backoff doubles per attempt`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(1000)
        assertThat(policy.delayMs(attempt = 2, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(2000)
        assertThat(policy.delayMs(attempt = 3, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(4000)
    }
    @Test
    fun `caps at 60 seconds`() {
        assertThat(policy.delayMs(attempt = 9, retryAfterSeconds = null, jitter = 0.0)).isEqualTo(60_000)
    }
    @Test
    fun `jitter widens by plus-minus 20 percent`() {
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = 0.2)).isEqualTo(1200)
        assertThat(policy.delayMs(attempt = 1, retryAfterSeconds = null, jitter = -0.2)).isEqualTo(800)
    }
    @Test
    fun `retryable statuses are 429 and transient 5xx`() {
        assertThat(policy.isRetryable(429)).isTrue()
        assertThat(policy.isRetryable(502)).isTrue()
        assertThat(policy.isRetryable(503)).isTrue()
        assertThat(policy.isRetryable(504)).isTrue()
        assertThat(policy.isRetryable(400)).isFalse()
        assertThat(policy.isRetryable(401)).isFalse()
        assertThat(policy.isRetryable(500)).isFalse()
    }
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend/broker-gateway && ./gradlew test --tests "*HttpRetryPolicyTest*" --console=plain`
Expected: FAIL (class not defined).

- [ ] **Step 3: Implement HttpRetryPolicy**

Create `HttpRetryPolicy.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

/** Pure retry/backoff computation for outbound broker HTTP. Unit: milliseconds. */
class HttpRetryPolicy(
    private val maxAttempts: Int = 3,
    private val baseDelayMs: Long = 1000,
    private val capDelayMs: Long = 60_000,
) {
    fun isRetryable(status: Int): Boolean =
        status == 429 || status == 502 || status == 503 || status == 504

    fun delayMs(attempt: Int, retryAfterSeconds: Int?, jitter: Double): Long {
        val exponential = (baseDelayMs shl (attempt - 1).coerceAtMost(20))
            .coerceAtMost(capDelayMs)
        val chosen = retryAfterSeconds?.let { (it * 1000L).coerceAtMost(capDelayMs) } ?: exponential
        return (chosen * (1.0 + jitter)).toLong().coerceIn(0, capDelayMs)
    }

    fun jitter(): Double = (random.nextDouble() * 0.4) - 0.2 // ±20%
    fun shouldRetry(attempt: Int): Boolean = attempt < maxAttempts

    private companion object { val random = java.util.Random() }
}
```

- [ ] **Step 4: Write the failing retry integration test (MockWebServer)**

Create `QuestradeRestClientRetryTest.kt`:

```kotlin
package com.portfolio.brokergateway.adapter.questrade

import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.Test

class QuestradeRestClientRetryTest {

    private lateinit var server: MockWebServer

    @AfterEach
    fun tearDown() { if (::server.isInitialized) server.shutdown() }

    @Test
    fun `retries a 429 honoring Retry-After then succeeds`() {
        server = MockWebServer()
        server.enqueue(MockResponse().setResponseCode(429).setHeader("Retry-After", "0").setBody("{}"))
        server.enqueue(MockResponse().setResponseCode(200).setBody("""{"accounts":[]}"""))
        server.start()
        val client = QuestradeRestClient(
            responseTimeoutMs = 5000,
            retryPolicy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 10, capDelayMs = 60_000),
        )

        val result = client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts")

        assertThat(result).isNotNull()
        assertThat(server.requestCount).isEqualTo(2)
    }

    @Test
    fun `gives up after max attempts on persistent 502`() {
        server = MockWebServer()
        repeat(3) { server.enqueue(MockResponse().setResponseCode(502).setBody("bad gateway")) }
        server.start()
        val client = QuestradeRestClient(
            responseTimeoutMs = 5000,
            retryPolicy = HttpRetryPolicy(maxAttempts = 3, baseDelayMs = 10, capDelayMs = 60_000),
        )

        val result = runCatching { client.get(server.url("/").toString().trimEnd('/'), "token", "/v1/accounts") }

        assertThat(result.isFailure).isTrue()
        assertThat(server.requestCount).isEqualTo(3)
    }
}
```

Note: `retryPolicy` is a ctor param (`private val retryPolicy: HttpRetryPolicy = HttpRetryPolicy()`) so tests inject a fast-delay policy; `Retry-After: 0` in the first test keeps it fast regardless.

- [ ] **Step 5: Implement retry + Retry-After parsing + pacer in QuestradeRestClient**

**Critical detail:** `handleError` (`:73-85`) converts every HTTP error before it escapes `get`, so a `catch (e: WebClientResponseException)` at the retry site would be **dead code**. Retry must key off the *converted* exception types. Add a transient type first:

- `Exceptions.kt` — add, mirroring `BrokerDataException`'s shape (`:45`):

```kotlin
class BrokerTransientException(
    message: String,
    val brokerType: BrokerType,
    cause: Throwable? = null,
) : RuntimeException(message, cause)
```

- `QuestradeRestClient.handleError` (`:73-85`): 429 → parse the `Retry-After` header (seconds; non-numeric → null) into `BrokerRateLimitException(retryAfterSeconds = parsed)`; **502/503/504 → throw `BrokerTransientException(...)`**; everything else (incl. 500 — deliberate) stays `BrokerDataException`.
- Gateway `GlobalExceptionHandler.kt` — add a handler mapping `BrokerTransientException` to **502 BROKER_CONNECTION_FAILED** (same shape as the existing `BrokerConnectionException` branch, `:17-40`).
- Keep the public `get` signature **unchanged** (`get(apiServerUrl: String, accessToken: String, path: String): JsonNode`) — rename only the current body to `private fun fetchOnce(apiServerUrl, accessToken, path): JsonNode` and wrap it:

```kotlin
fun get(apiServerUrl: String, accessToken: String, path: String): JsonNode {
    rateLimiter.acquire()
    var attempt = 1
    while (true) {
        try {
            return fetchOnce(apiServerUrl, accessToken, path)
        } catch (e: BrokerRateLimitException) {
            if (!retryPolicy.shouldRetry(attempt)) throw e
            Thread.sleep(retryPolicy.delayMs(attempt, e.retryAfterSeconds, retryPolicy.jitter()))
            attempt++
        } catch (e: BrokerTransientException) {
            if (!retryPolicy.shouldRetry(attempt)) throw e
            Thread.sleep(retryPolicy.delayMs(attempt, null, retryPolicy.jitter()))
            attempt++
        }
    }
}
```

- `post` and `delete` (`:35-64`): add `rateLimiter.acquire()` at the top of each — **pace only, never retry** (order submission must not double-submit). All `QuestradeAdapter` call sites (`:29, :43, :60, :82, :110, :149, :182`) remain untouched because the signature is unchanged.
- Add ctor params: `private val retryPolicy: HttpRetryPolicy = HttpRetryPolicy()` and `private val rateLimiter: QuestradeRateLimiter = QuestradeRateLimiter(1)`; `QuestradeAdapter.kt:20` passes `QuestradeRateLimiter(config.rateLimitPerSecond)`.

Create `QuestradeRateLimiter.kt` (fixed-rate pacer; replaces the dead config key):

```kotlin
package com.portfolio.brokergateway.adapter.questrade

/** Simple fixed-rate pacer: enforces a minimum interval between outbound calls. */
class QuestradeRateLimiter(perSecond: Int) {
    private val minIntervalMs: Long = if (perSecond <= 0) 0 else 1000L / perSecond
    private val lock = Object()
    private var nextAllowedAt = 0L

    fun acquire() {
        if (minIntervalMs <= 0) return
        synchronized(lock) {
            val now = System.currentTimeMillis()
            val wait = nextAllowedAt - now
            if (wait > 0) {
                Thread.sleep(wait)
                nextAllowedAt += minIntervalMs
            } else {
                nextAllowedAt = now + minIntervalMs
            }
        }
    }
}
```

- [ ] **Step 6: Run tests**

Run: `cd backend/broker-gateway && ./gradlew test --console=plain`
Expected: PASS (policy + retry tests; full suite green).

- [ ] **Step 7: Commit**

```bash
git add backend/broker-gateway/src
git commit -m "fix(gateway): retry transient broker HTTP failures and enforce Questrade rate limit"
```

---

### Task 3: TokenRefreshScheduler — expiry-aware, no validate polling, failure backoff

**Files:**
- Modify: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/scheduler/TokenRefreshScheduler.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/scheduler/TokenRefreshSchedulerTest.kt` (create)

**Interfaces:**
- Consumes: `CredentialService.getCredentialsWithRefresh` (refreshes only when `expiresAtEpochSeconds - now <= 300`, `CredentialService.kt:44-58`); `GatewayConnection.lastValidatedAt/refreshFailureCount/status` (`CredentialEntity.kt:31-41`); `adapter.validateConnection`.
- Produces: scheduler no longer calls `validateConnection` for connections whose token is fresh and status ACTIVE with a recent validation; failures back off exponentially in-memory (5 min × 2^failures, cap 60 min).

- [ ] **Step 1: Write the failing scheduler test**

Create `TokenRefreshSchedulerTest.kt`:

Verified real APIs (from `TokenRefreshScheduler.kt`, `CredentialService.kt`, `AdapterRegistry.kt`, `CredentialEntity.kt`): method is `refreshExpiringTokens()`; ctor is `(connectionRepository, credentialService, adapterRegistry)`; repo is `com.portfolio.brokergateway.credential.GatewayConnectionRepository`; **`status` is a String** (`"ACTIVE"`/`"ERROR"` — no enum); **`id` is a String UUID**; `lastValidatedAt` is `OffsetDateTime?`; `getCredentialsWithRefresh(connectionId, adapter)` takes two args; registry method is `getAdapter(brokerType)`.

```kotlin
package com.portfolio.brokergateway.scheduler

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.BrokerCredentials
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import com.portfolio.brokergateway.credential.GatewayConnectionRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class TokenRefreshSchedulerTest {

    private val connectionRepository = mockk<GatewayConnectionRepository>(relaxed = true)
    private val credentialService = mockk<CredentialService>(relaxed = true)
    private val adapterRegistry = mockk<AdapterRegistry>(relaxed = true)
    private val adapter = mockk<BrokerAdapter>(relaxed = true)
    private val scheduler = TokenRefreshScheduler(connectionRepository, credentialService, adapterRegistry)

    private fun credentials(): BrokerCredentials = mockk(relaxed = true) {
        every { brokerType } returns BrokerType.QUESTRADE
    }

    private fun activeConnection(
        id: String = "uuid-1",
        lastValidatedAt: OffsetDateTime? = OffsetDateTime.now().minusHours(1),
    ) = mockk<GatewayConnection>(relaxed = true) {
        every { this@mockk.id } returns id
        every { this@mockk.status } returns "ACTIVE"
        every { this@mockk.lastValidatedAt } returns lastValidatedAt
        every { this@mockk.refreshFailureCount } returns 0
    }

    private fun wireCommon(conn: GatewayConnection) {
        every { connectionRepository.findByStatusIn(listOf("ACTIVE", "ERROR")) } returns listOf(conn)
        every { credentialService.getCredentials(conn.id) } returns credentials()
        every { adapterRegistry.getAdapter(any()) } returns adapter
        every { credentialService.getCredentialsWithRefresh(conn.id, adapter) } returns credentials()
    }

    @Test
    fun `fresh token with recent validation skips broker validation`() {
        val conn = activeConnection()
        wireCommon(conn)

        scheduler.refreshExpiringTokens()

        verify(exactly = 0) { adapter.validateConnection(any()) }
    }

    @Test
    fun `stale validation performs one broker call and stamps lastValidatedAt`() {
        val conn = activeConnection(lastValidatedAt = OffsetDateTime.now().minusHours(25))
        wireCommon(conn)
        every { adapter.validateConnection(any()) } returns mockk(relaxed = true) { every { connected } returns true }

        scheduler.refreshExpiringTokens()

        verify(exactly = 1) { adapter.validateConnection(any()) }
        verify { conn.lastValidatedAt = any() }
    }

    @Test
    fun `validation failure backs off so the next run skips the connection`() {
        val conn = activeConnection(lastValidatedAt = null)   // forces validation
        wireCommon(conn)
        every { adapter.validateConnection(any()) } throws RuntimeException("broker down")

        scheduler.refreshExpiringTokens()
        scheduler.refreshExpiringTokens()

        verify(exactly = 1) { adapter.validateConnection(any()) }   // second run was inside the backoff window
    }
}
```

(If `BrokerCredentials` lives in a different package, adjust the import — it is the type returned by `CredentialService.getCredentials`.)

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend/broker-gateway && ./gradlew test --tests "*TokenRefreshScheduler*" --console=plain`
Expected: FAIL (current scheduler validates unconditionally).

- [ ] **Step 3: Rewrite the scheduler loop**

Keep the class shape (`@Component @EnableScheduling`, `@Scheduled(fixedRate = 5 * 60 * 1000, initialDelay = 60 * 1000)`, `@Transactional`, existing counters). Replace the per-connection body of `refreshExpiringTokens` (`TokenRefreshScheduler.kt:33-80`) with:

```kotlin
val now = Instant.now()
for (conn in connections) {
    if (backoffUntil[conn.id]?.isAfter(now) == true) { skipped++; continue }   // failure backoff
    try {
        val credentials = credentialService.getCredentials(conn.id)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        // cheap: refreshes only when the access token is within 300s of expiry; no broker call otherwise
        val updated = credentialService.getCredentialsWithRefresh(conn.id, adapter)

        // full broker validation only when needed: ERROR status, never validated, or stale (>24h)
        val stale = conn.lastValidatedAt == null ||
            conn.lastValidatedAt.isBefore(OffsetDateTime.now().minusHours(24))
        if (conn.status == "ERROR" || stale) {
            val validation = adapter.validateConnection(updated)
            if (!validation.connected) {
                log.warn("Token valid by timestamp but rejected by broker for {}, force-refreshing", conn.id)
                val forceRefreshed = credentialService.forceRefresh(conn.id, adapter)
                val retry = adapter.validateConnection(forceRefreshed)
                if (!retry.connected) {
                    throw BrokerAuthenticationException(
                        "Validation failed after force-refresh: ${retry.message}", credentials.brokerType)
                }
            }
            conn.lastValidatedAt = OffsetDateTime.now()   // nothing writes this today; required or staleness never clears
        }

        if (conn.status == "ERROR") {
            conn.status = "ACTIVE"
            conn.errorMessage = null
            recovered++
            log.info("Connection {} recovered from ERROR to ACTIVE", conn.id)
        }
        conn.refreshFailureCount = 0
        backoffUntil.remove(conn.id)
        connectionRepository.save(conn)
        refreshed++
    } catch (e: Exception) {
        conn.refreshFailureCount++
        failed++
        // 5/10/20/40/60 min: 5 min × 2^(failures−1), capped at 60 min (300s shl n, capped 3600s)
        val backoffSeconds = minOf(300L shl minOf(conn.refreshFailureCount - 1, 4), 3600L)
        backoffUntil[conn.id] = now.plusSeconds(backoffSeconds)
        // keep the EXISTING escalation block verbatim (>=10 EXPIRED, >=3 ERROR, else warn) and save
    }
}
```

with `private val backoffUntil = ConcurrentHashMap<String, Instant>()` and a `skipped` counter added to the final log line. In-memory backoff (single-replica deployment; multi-replica would need ShedLock, noted in a comment). The current force-refresh-on-invalid branch (`:40-50`) is kept deliberately — do not drop it. Nothing currently writes `lastValidatedAt` (only read in `ConnectionController.kt:152`); this step adds the write, without which the staleness skip never triggers and the 429-storm fix won't materialize.

- [ ] **Step 4: Run tests**

Run: `cd backend/broker-gateway && ./gradlew test --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src
git commit -m "fix(gateway): validate broker tokens only when stale and back off on failures"
```

---

### Task 4: Portfolio per-chunk transactions with durable progress and resume

**Files:**
- Create: `backend/portfolio/src/main/resources/db/migration/V78__broker_sync_progress.sql`
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/BrokerSyncProgress.kt`
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/repository/BrokerSyncProgressRepository.kt`
- Create: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/BrokerSyncProgressService.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/BrokerSyncProgressServiceTest.kt`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt` (extend)

**Interfaces:**
- Consumes: Phase 1's fingerprint dedup (re-fetched rows are skipped, making resume safe); `ActivityIngestionService` ctor mocks in `ActivityIngestionServiceTest:37-50`.
- Produces: table `broker_sync_progress(connection_id, sync_kind, next_chunk_end, updated_at)` PK `(connection_id, sync_kind)`; `BrokerSyncProgressService.get(connectionId, kind): BrokerSyncProgress?`, `advance(connectionId, kind, nextChunkEnd: LocalDate)`, `clear(connectionId, kind)`; `ActivityIngestionService.syncFullHistory` runs one DB transaction **per chunk** (iterating backward from now, as today) and resumes from stored progress after a failure.

- [ ] **Step 1: Write the migration**

Create `backend/portfolio/src/main/resources/db/migration/V78__broker_sync_progress.sql`:

```sql
CREATE TABLE broker_sync_progress (
    connection_id   BIGINT       NOT NULL,
    sync_kind       VARCHAR(20)  NOT NULL,
    next_chunk_end  DATE         NOT NULL,   -- next (earlier) chunk end to attempt; sync iterates backward
    updated_at      TIMESTAMPTZ  NOT NULL DEFAULT now(),
    PRIMARY KEY (connection_id, sync_kind),
    CONSTRAINT fk_sync_progress_connection
        FOREIGN KEY (connection_id) REFERENCES broker_connections (id) ON DELETE CASCADE
);
```

- [ ] **Step 2: Write the failing progress-service test**

Create `BrokerSyncProgressServiceTest.kt`:

```kotlin
package com.portfolio.broker.service

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.repository.BrokerSyncProgressRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BrokerSyncProgressServiceTest {

    private val repo = mockk<BrokerSyncProgressRepository>(relaxed = true)
    private val service = BrokerSyncProgressService(repo)

    @Test
    fun `advance upserts the next chunk end`() {
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns null
        service.advance(7L, "ACTIVITIES_FULL", LocalDate.of(2026, 9, 1))
        verify {
            repo.save(match<BrokerSyncProgress> {
                it.connectionId == 7L && it.syncKind == "ACTIVITIES_FULL" &&
                    it.nextChunkEnd == LocalDate.of(2026, 9, 1)
            })
        }
    }

    @Test
    fun `get returns null when no progress row exists`() {
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns null
        assert(service.get(7L, "ACTIVITIES_FULL") == null)
    }

    @Test
    fun `clear deletes the row`() {
        val row = BrokerSyncProgress(connectionId = 7L, syncKind = "ACTIVITIES_FULL", nextChunkEnd = LocalDate.now())
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns row
        service.clear(7L, "ACTIVITIES_FULL")
        verify { repo.delete(row) }
    }
}
```

- [ ] **Step 3: Implement entity, repository, service**

`BrokerSyncProgress.kt`:

**Important:** the repository's ID type must be the entity's declared id type — two bare `@Id` fields are not enough; JPA requires an explicit `@IdClass` (there is no precedent in `backend/` to copy). Include the id class in the entity file:

```kotlin
package com.portfolio.broker.entity

import jakarta.persistence.*
import java.io.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

class BrokerSyncProgressId(
    val connectionId: Long = 0,
    val syncKind: String = "",
) : Serializable {
    override fun equals(other: Any?) = other is BrokerSyncProgressId &&
        other.connectionId == connectionId && other.syncKind == syncKind
    override fun hashCode() = 31 * connectionId.hashCode() + syncKind.hashCode()
}

@Entity
@IdClass(BrokerSyncProgressId::class)
@Table(name = "broker_sync_progress")
class BrokerSyncProgress(
    @Id
    @Column(name = "connection_id")
    val connectionId: Long,
    @Id
    @Column(name = "sync_kind")
    val syncKind: String,
    @Column(name = "next_chunk_end", nullable = false)
    var nextChunkEnd: LocalDate,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
```

`BrokerSyncProgressRepository.kt`:

```kotlin
package com.portfolio.broker.repository

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.entity.BrokerSyncProgressId
import org.springframework.data.jpa.repository.JpaRepository

interface BrokerSyncProgressRepository : JpaRepository<BrokerSyncProgress, BrokerSyncProgressId> {
    fun findByConnectionIdAndSyncKind(connectionId: Long, syncKind: String): BrokerSyncProgress?
    fun deleteByConnectionIdAndSyncKind(connectionId: Long, syncKind: String)
}
```

`BrokerSyncProgressService.kt`:

```kotlin
package com.portfolio.broker.service

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.repository.BrokerSyncProgressRepository
import org.springframework.stereotype.Service
import java.time.LocalDate

@Service
class BrokerSyncProgressService(private val repo: BrokerSyncProgressRepository) {

    companion object { const val ACTIVITIES_FULL = "ACTIVITIES_FULL" }

    fun get(connectionId: Long, kind: String): BrokerSyncProgress? =
        repo.findByConnectionIdAndSyncKind(connectionId, kind)

    fun advance(connectionId: Long, kind: String, nextChunkEnd: LocalDate) {
        val row = repo.findByConnectionIdAndSyncKind(connectionId, kind)
            ?: BrokerSyncProgress(connectionId = connectionId, syncKind = kind, nextChunkEnd = nextChunkEnd)
        row.nextChunkEnd = nextChunkEnd
        row.updatedAt = OffsetDateTime.now()
        repo.save(row)
    }

    fun clear(connectionId: Long, kind: String) {
        repo.findByConnectionIdAndSyncKind(connectionId, kind)?.let { repo.delete(it) }
    }
}
```

- [ ] **Step 4: Restructure syncFullHistory to per-chunk transactions + resume**

In `ActivityIngestionService.kt`:
- Remove `@Transactional(propagation = REQUIRES_NEW)` from `syncActivitiesForConnection` (`:37`) — it becomes the non-transactional entry point.
- Inject two new ctor params: `transactionOperations: TransactionOperations` (Spring auto-configures the `TransactionTemplate` bean, which implements this interface) and `progressService: BrokerSyncProgressService`.
- Keep the loop **backward** (now → earliest), as today — forward iteration would break at the 12-empty-chunk rule before ever reaching the retained window (a new connection would fetch nothing). Backward, the near-now chunks contain data and empties only appear at the retention horizon.
- Reuse the **existing** `processAndSaveActivities(body, connection)` (`:144-200`) unchanged — do NOT rename or duplicate it (it already exists and is used by incremental sync). Fetch each chunk **outside** the transaction so no Hikari connection is held across broker HTTP:

```kotlin
internal fun syncFullHistory(connectionId: Long, gwConnId: Long, accountId: String): Int {
    val earliest = LocalDate.now(ZONE).minusYears(lookbackYears.toLong())   // ZONE = America/Toronto
    val resumeFrom = progressService.get(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL)?.nextChunkEnd
    var chunkEnd = resumeFrom ?: LocalDate.now(ZONE)
    var totalInserted = 0
    var emptyChunksInRow = 0
    while (!chunkEnd.isBefore(earliest)) {
        val chunkStart = maxOf(chunkEnd.minusDays((chunkDays - 1).toLong()), earliest)
        val inserted = try {
            val body = gatewayClient.getActivities(gwConnId, accountId, chunkStart, chunkEnd)   // network, outside tx
            transactionOperations.execute<Int> {
                val connection = connectionRepository.findById(connectionId).orElseThrow()      // reload inside tx (FK/accountName)
                val count = processAndSaveActivities(body, connection)
                progressService.advance(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL, chunkStart.minusDays(1))
                count
            } ?: 0
        } catch (e: Exception) {
            log.warn("Activity chunk {}..{} failed for connection {}; resuming from saved progress next run", chunkStart, chunkEnd, connectionId, e)
            throw SyncInterruptedException(totalInserted)
        }
        totalInserted += inserted
        emptyChunksInRow = if (inserted == 0) emptyChunksInRow + 1 else 0
        if (emptyChunksInRow >= 12) break   // retention horizon reached
        chunkEnd = chunkStart.minusDays(1)
    }
    progressService.clear(connectionId, BrokerSyncProgressService.ACTIVITIES_FULL)
    return totalInserted
}
```

- `class SyncInterruptedException(val insertedSoFar: Int) : RuntimeException()` in the same file. Contract: `syncActivitiesForConnection` catches it, records the sync status (Task 5), and **returns `insertedSoFar` without rethrowing** (scheduled runs stay resilient; `/sync-all` reports PARTIAL). Incremental failures keep rethrowing as today. Note the `.toLong()` conversions — Kotlin date arithmetic will not widen Int.
- `syncIncremental` (`:121-142`): wrap its existing body in `transactionOperations.execute { ... }` (it currently relies on the removed outer `@Transactional`). Its `endDate = null` call shape is pinned by an existing test — do not change it.

- [ ] **Step 5: Write the failing resume test + update the test harness**

First update the service construction site in `ActivityIngestionServiceTest.kt:37-50` to pass the two new ctor args, using a hand-written inline transaction implementation:

```kotlin
private val inlineTx = object : TransactionOperations {
    override fun <T : Any?> execute(action: TransactionCallback<T>): T? =
        action.doInTransaction(TransactionStatus())
}
```

Without this, the six existing full-history tests (`findLatestTradeDateByConnectionId → null` at `:245,270,300,328,357,394`) would hit a relaxed `TransactionTemplate` mock returning null, silently skip every chunk, and fail confusingly.

Then add to `ActivityIngestionServiceTest.kt`:

```kotlin
@Test
fun `full history resumes from saved progress after a chunk failure`() {
    // seed a mid-history resume point: next chunk to attempt is 2026-09-03 .. 2026-10-01 (chunkDays = 29)
    every { progressService.get(10L, "ACTIVITIES_FULL") } returns BrokerSyncProgress(
        connectionId = 10L, syncKind = "ACTIVITIES_FULL", nextChunkEnd = LocalDate.of(2026, 10, 1))
    // catch-all first, then override the failing chunk
    every { gatewayClient.getActivities(any(), any(), any(), any()) } returns emptyJsonBody()
    every { gatewayClient.getActivities(eq(99L), any(), eq(LocalDate.of(2026, 9, 3)), eq(LocalDate.of(2026, 10, 1))) } throws RuntimeException("boom")

    service.syncActivitiesForConnection(10L)

    verify(exactly = 0) { progressService.advance(10L, "ACTIVITIES_FULL", any()) }  // failed chunk never advances
    verify(exactly = 0) { progressService.clear(10L, "ACTIVITIES_FULL") }           // progress survives for resume

    // second run resumes at the SAME saved point and succeeds
    every { gatewayClient.getActivities(eq(99L), any(), eq(LocalDate.of(2026, 9, 3)), eq(LocalDate.of(2026, 10, 1))) } returns jsonBodyOf(questradeActivityWithoutExternalId())
    service.syncActivitiesForConnection(10L)

    verify { progressService.advance(10L, "ACTIVITIES_FULL", LocalDate.of(2026, 9, 2)) }
}
```

(Adapt helper names to the file's existing stubs — `jsonBodyOf`/`emptyJsonBody` are placeholders for whatever helpers the test file already uses to build `JsonNode` bodies. Numbers assume `chunkDays = 29`: `maxOf(2026-10-01 − 28d, earliest)` = 2026-09-03; the catch-all keeps the loop terminating.)

- [ ] **Step 6: Run tests**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain`
Expected: PASS (new tests + existing suite; the pre-existing `incremental sync fetches from last known date` test must stay green — the incremental call contract is unchanged).

- [ ] **Step 7: Commit**

```bash
git add backend/portfolio/src
git commit -m "fix(portfolio): sync activities in per-chunk transactions with durable resume"
```

---

### Task 5: Honest watermarks + sync status surfaced in DTO and UI

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/entity/BrokerConnection.kt:78-82`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt:58`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/dto/BrokerDtos.kt:39-57`
- Modify: `frontend/src/types/broker.ts:30-48` (the `BrokerConnection` interface lives here, not in brokerService.ts)
- Modify: `frontend/src/components/broker/BrokerConnectionCard.tsx:112-115`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt` (extend)

**Interfaces:**
- Consumes: Task 4's `SyncInterruptedException` and per-chunk loop.
- Produces: `BrokerConnection.lastActivitiesSyncStatus: String?` (`SUCCESS|PARTIAL|FAILED`), same for balance; DTO fields `lastActivitiesFetchedAt`, `lastActivitiesSyncStatus`, `lastBalanceFetchedAt`, `lastBalanceSyncStatus`; UI shows activities staleness + status.

- [ ] **Step 1: Write the failing watermark tests**

Add to `ActivityIngestionServiceTest.kt`:

```kotlin
@Test
fun `activities watermark is NOT advanced when the first chunk fails mid-history`() {
    every { progressService.get(10L, "ACTIVITIES_FULL") } returns null
    every { gatewayClient.getActivities(any(), any(), any(), any()) } throws RuntimeException("boom")
    val connSlot = slot<BrokerConnection>()
    every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

    service.syncActivitiesForConnection(10L)

    assertThat(connSlot.captured.lastActivitiesFetchedAt).isNull()
    assertThat(connSlot.captured.lastActivitiesSyncStatus).isEqualTo("FAILED")
}

@Test
fun `activities watermark records PARTIAL when some chunks succeeded before a failure`() {
    every { progressService.get(10L, "ACTIVITIES_FULL") } returns null
    var calls = 0
    every { gatewayClient.getActivities(any(), any(), any(), any()) } answers {
        if (++calls == 1) jsonBodyOf(questradeActivityWithoutExternalId()) else throw RuntimeException("boom")
    }
    val connSlot = slot<BrokerConnection>()
    every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

    service.syncActivitiesForConnection(10L)

    assertThat(connSlot.captured.lastActivitiesFetchedAt).isNull()      // only genuine success advances it
    assertThat(connSlot.captured.lastActivitiesSyncStatus).isEqualTo("PARTIAL")
    verify { activityRepository.save(any()) }                            // chunk 1 rows persisted
}

@Test
fun `activities watermark set to SUCCESS only after a clean full run`() {
    every { progressService.get(10L, "ACTIVITIES_FULL") } returns null
    every { gatewayClient.getActivities(any(), any(), any(), any()) } returns emptyJsonBody()
    val connSlot = slot<BrokerConnection>()
    every { connectionRepository.save(capture(connSlot)) } answers { firstArg() }

    service.syncActivitiesForConnection(10L)

    assertThat(connSlot.captured.lastActivitiesFetchedAt).isNotNull()
    assertThat(connSlot.captured.lastActivitiesSyncStatus).isEqualTo("SUCCESS")
}
```

(`jsonBodyOf`/`emptyJsonBody` stand in for the file's existing `JsonNode` body helpers; `activityRepository`/`connectionRepository` for the existing mocked repo names in the test class.)

- [ ] **Step 2: Run them to verify they fail**

Run: `cd backend/portfolio && ./gradlew test --tests "*ActivityIngestionServiceTest*" --console=plain`
Expected: FAIL (watermark currently set unconditionally at `:58`; no status fields).

- [ ] **Step 3: Implement**

`BrokerConnection.kt` — add after `:82`:

```kotlin
@Column(name = "last_activities_sync_status", length = 20)
var lastActivitiesSyncStatus: String? = null,

@Column(name = "last_balance_sync_status", length = 20)
var lastBalanceSyncStatus: String? = null,
```

`ActivityIngestionService.kt` — replace the unconditional `:58` write:

```kotlin
val status = when {
    failure == null -> "SUCCESS"
    totalInserted > 0 -> "PARTIAL"
    else -> "FAILED"
}
if (status == "SUCCESS") connection.lastActivitiesFetchedAt = OffsetDateTime.now()
connection.lastActivitiesSyncStatus = status
connectionRepository.save(connection)
```

where `failure` is the caught `SyncInterruptedException` (Task 4) or null. Incremental sync failures must also land in this status — catch at the same site so `lastActivitiesSyncStatus` is honest for both full-history and incremental paths (incremental still rethrows after recording). Balance side (`:259`): keep success-only watermark, add `lastBalanceSyncStatus = "SUCCESS"` on success / `"FAILED"` in the existing catch (`:214-217`).

Migration: add the two columns to a new `V79__connection_sync_status.sql`:

```sql
ALTER TABLE broker_connections
    ADD COLUMN last_activities_sync_status VARCHAR(20),
    ADD COLUMN last_balance_sync_status VARCHAR(20);
```

`BrokerDtos.kt` — extend `BrokerConnectionDto` (`:39-57`) with `lastActivitiesFetchedAt: OffsetDateTime?`, `lastActivitiesSyncStatus: String?`, `lastBalanceFetchedAt: OffsetDateTime?`, `lastBalanceSyncStatus: String?` and map them in the DTO construction site at `BrokerDtos.kt:162` (the `toDto()` extension) — **not** in `BrokerService`.

Frontend: add the four fields to the `BrokerConnection` interface in `frontend/src/types/broker.ts:30-48`. In `BrokerConnectionCard.tsx`, the file imports `getRelativeTime` from `brokerService` (`:4`) and uses plain spans/divs — do **not** introduce `Typography` or `formatDistanceToNow`. Match the existing caption markup around (`:112-115`):

```tsx
{connection.lastActivitiesFetchedAt && (
  <span className="text-xs text-gray-500 dark:text-gray-400">
    Activities: {getRelativeTime(connection.lastActivitiesFetchedAt)}
    {connection.lastActivitiesSyncStatus && connection.lastActivitiesSyncStatus !== "SUCCESS" &&
      ` (${connection.lastActivitiesSyncStatus.toLowerCase()})`}
  </span>
)}
```

Render the same caption for balance using `lastBalanceFetchedAt`/`lastBalanceSyncStatus`.

- [ ] **Step 4: Run tests**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain` and `cd frontend && npm test -- --run`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src frontend/src
git commit -m "fix(portfolio): record honest sync watermarks and surface staleness in API and UI"
```

---

### Task 6: Error propagation — GatewayApiException → 502 with broker detail; accurate /sync-all

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/config/GlobalExceptionHandler.kt`
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt:189-221`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/config/GlobalExceptionHandlerTest.kt` (extend)

**Interfaces:**
- Consumes: `GatewayApiException(gatewayStatusCode, gatewayErrorCode, gatewayDetail)` (`client/GatewayApiException.kt:10-28`).
- Produces: any uncaught `GatewayApiException` → HTTP 502, ProblemDetail `title = "Broker gateway error"`, `detail` = broker detail, `code` property = gateway error code; `/sync-all` returns per-step `status` per connection and never aborts on the first failure.

- [ ] **Step 1: Write the failing handler test**

Add to `GlobalExceptionHandlerTest.kt`:

```kotlin
@Test
fun `GatewayApiException maps to 502 with broker detail`() {
    val ex = GatewayApiException(
        gatewayStatusCode = 502,
        gatewayErrorCode = "BROKER_DATA_ERROR",
        gatewayDetail = "Questrade error 502 on /v1/accounts: {\"code\":1003}",
    )
    val response = handler.handleGatewayApi(ex)

    assertThat(response.statusCode).isEqualTo(HttpStatus.BAD_GATEWAY)
    assertThat(response.body!!.detail).contains("1003")
    assertThat(response.body!!.properties!!["code"]).isEqualTo("BROKER_DATA_ERROR")
}
```

- [ ] **Step 2: Run it to verify it fails**

Run: `cd backend/portfolio && ./gradlew test --tests "*GlobalExceptionHandlerTest*" --console=plain`
Expected: FAIL (currently falls through to the generic 500 handler, `:80-89`).

- [ ] **Step 3: Implement the handler + /sync-all per-step results**

`GlobalExceptionHandler.kt` — add:

```kotlin
@ExceptionHandler(GatewayApiException::class)
fun handleGatewayApi(e: GatewayApiException): ResponseEntity<ProblemDetail> {
    // GatewayApiException exposes gatewayStatusCode/gatewayErrorCode as properties only —
    // the broker detail lives in message (set by the exception's buildMessage)
    val problem = ProblemDetail.forStatusAndDetail(
        HttpStatus.BAD_GATEWAY,
        e.message ?: "Broker gateway error",
    )
    problem.title = "Broker gateway error"
    problem.setProperty("code", e.gatewayErrorCode ?: "GATEWAY_ERROR")
    return ResponseEntity.status(HttpStatus.BAD_GATEWAY).body(problem)
}
```

(`problem.property(...)` is not an API — existing handlers use `setProperty`, `GlobalExceptionHandler.kt:38`.)

`BrokerController.kt` `/sync-all` (`:189-221`) — wrap `triggerManualFetch` (`:196`) in try/catch recording `positionsStatus = "FAILED"` instead of aborting; **keep the existing `positionsFetched` and `balanceSynced` keys** (the frontend reads them: `BrokerConnectionsPage.tsx:91` sums `result.positionsFetched`, and `SyncAllResponse` in `frontend/src/services/brokerService.ts:158-164` requires both) and ADD the status keys:

```kotlin
val response = mapOf(
    "connectionId" to connectionId,
    "positionsFetched" to positionsFetched,    // KEPT — frontend sums this
    "positionsStatus" to positionsStatus,      // SUCCESS | FAILED
    "activitiesSynced" to activitiesSynced,    // Int (0 on failure, as today)
    "activitiesStatus" to activitiesStatus,    // SUCCESS | FAILED
    "balanceSynced" to balanceSynced,          // KEPT — frontend type requires it
    "balanceStatus" to balanceStatus,
    "status" to overall,                       // SUCCESS if all succeeded else PARTIAL
    "message" to message,                      // accurate: names the failed steps
)
```

No frontend change is needed (all previously-consumed keys remain). Remove the always-success message. Keep `/sync-activities`'s 200-on-success path; its failure now surfaces as 502 via the new handler (remove any local catch that would mask it).

- [ ] **Step 4: Run tests**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add backend/portfolio/src
git commit -m "fix(portfolio): map gateway failures to 502 with broker detail and report per-step sync results"
```

---

### Task 7: Single-flight guard for per-connection syncs

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt` (guard `syncActivitiesForConnection :38` and `syncBalanceForConnection :203`)
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/PositionFetchService.kt` (guard `triggerManualFetch :39`)
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ConnectionSyncGuardTest.kt` (create)

**Interfaces:**
- Consumes: the real convergence points — scheduled `syncAllConnections` (`ActivityIngestionService.kt:265-277`), manual `POST /sync-activities` (`BrokerController.kt:182`), `POST /sync-all` (`:200`), `/fetch` (`:124`), dashboard refresh (`DashboardDataService.kt:631`). All converge on the three guarded service methods.
- Produces: `ConnectionSyncGuard.tryAcquire(connectionId): Boolean` / `release(connectionId)`; overlapping runs for the same connection are skipped (logged, reported as skipped), never queued.

- [ ] **Step 1: Write the failing guard test**

Create `ConnectionSyncGuardTest.kt`:

```kotlin
@Test
fun `second acquire for the same connection fails while the first is held`() {
    val guard = ConnectionSyncGuard()
    assertThat(guard.tryAcquire(7L)).isTrue()
    assertThat(guard.tryAcquire(7L)).isFalse()
    guard.release(7L)
    assertThat(guard.tryAcquire(7L)).isTrue()
}

@Test
fun `different connections do not interfere`() {
    val guard = ConnectionSyncGuard()
    assertThat(guard.tryAcquire(1L)).isTrue()
    assertThat(guard.tryAcquire(2L)).isTrue()
}
```

- [ ] **Step 2: Implement**

Create `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ConnectionSyncGuard.kt`:

```kotlin
package com.portfolio.broker.service

import org.springframework.stereotype.Component
import java.util.concurrent.ConcurrentHashMap

/** In-process single-flight guard per connection. Single-replica deployment;
 *  multi-replica would require ShedLock (out of scope). */
@Component
class ConnectionSyncGuard {
    private val locks = ConcurrentHashMap<Long, ReentrantLock>()

    fun tryAcquire(connectionId: Long): Boolean =
        locks.computeIfAbsent(connectionId) { ReentrantLock() }.let { it.tryLock() }

    fun release(connectionId: Long) {
        locks[connectionId]?.let { if (it.isHeldByCurrentThread) it.unlock() }
    }
}
```

Guard at the **actual service entry points** (all manual and scheduled paths converge there — `BrokerService` is not one of them):

- `ActivityIngestionService.syncActivitiesForConnection` (`:38`) — returns `Int`; when skipped, log and `return 0`.
- `ActivityIngestionService.syncBalanceForConnection` (`:203`) — returns `Unit`; when skipped, log and `return`.
- `PositionFetchService.triggerManualFetch` (`:39`) — returns its existing log/result type; when skipped, return the same failure-shaped value the method already builds for a failed fetch, with message `"already in progress; skipped"` (read its return type and reuse its existing failure construction).

```kotlin
if (!syncGuard.tryAcquire(connectionId)) {
    log.info("Sync already in progress for connection {}; skipping", connectionId)
    return /* method-appropriate skip value */
}
try { /* existing body */ } finally { syncGuard.release(connectionId) }
```

No deadlock risk: `/sync-all` calls the three methods sequentially (never nested), and `ReentrantLock` is reentrant anyway — keep them unnested.

Return-shape note: `syncActivitiesForConnection` returning `0` cannot distinguish "skipped" from "zero new rows"; acceptable for Phase 2 (the log line records the skip). If Task 6's per-step statuses are in place, a skipped step reports `"SKIPPED_ALREADY_RUNNING"` (extend Task 6's response shape).

- [ ] **Step 3: Run tests**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio/src
git commit -m "fix(portfolio): prevent overlapping syncs for the same broker connection"
```

---

### Task 8: ET schedulers + 5-year lookback

**Files:**
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/scheduler/AccountDataSyncScheduler.kt:28,34` (package is `broker.scheduler`, not `broker.service`)
- Modify: `backend/portfolio/src/main/kotlin/com/portfolio/broker/service/ActivityIngestionService.kt:30-31,219` (lookback default + balance's `LocalDate.now()` → `now(ZONE)`)
- Modify: `backend/portfolio/src/main/resources/application.yml:93`
- Test: `backend/portfolio/src/test/kotlin/com/portfolio/broker/service/ActivityIngestionServiceTest.kt` (assert lookback start)

**Interfaces:**
- Produces: `runPostMarketSync`/`runMorningSync` fire on ET wall-clock regardless of container TZ; full-history starts at `today(ET) − 5y` by default.

- [ ] **Step 1: Write the failing lookback test**

```kotlin
@Test
fun `full history starts five years back by default`() {
    every { progressService.get(10L, "ACTIVITIES_FULL") } returns null
    val starts = mutableListOf<LocalDate>()
    every { gatewayClient.getActivities(eq(99L), any(), capture(starts), any()) } returns emptyJsonBody()

    service.syncActivitiesForConnection(10L)

    // newest chunk is [today(ET)-28d .. today(ET)]; the walked-back window must reach ~5y back
    val earliest = LocalDate.now(ZoneId.of("America/Toronto")).minusYears(5)
    assertThat(starts.min()).isAfterOrEqualTo(earliest)
    assertThat(starts.min()).isBeforeOrEqualTo(earliest.plusDays(30))   // 29-day chunks: last chunk clamps to earliest
}
```

- [ ] **Step 2: Implement**

`AccountDataSyncScheduler.kt` — add `zone = "America/Toronto"` to both `@Scheduled` annotations (`:28`, `:34`):

```kotlin
@Scheduled(cron = "\${broker.sync.cron:0 20 16 * * *}", zone = "America/Toronto")
```

`application.yml:93` — `max-lookback-years: ${BROKER_SYNC_MAX_LOOKBACK_YEARS:5}`; `ActivityIngestionService.kt:30-31` — `@Value("\${broker.sync.max-lookback-years:5}")`.

- [ ] **Step 3: Run tests**

Run: `cd backend/portfolio && ./gradlew test -PexcludeIntegration --console=plain`
Expected: PASS.

- [ ] **Step 4: Commit**

```bash
git add backend/portfolio
git commit -m "fix(portfolio): run sync schedulers on ET wall-clock and default to 5-year lookback"
```

---

### Task 9: Docs — ADR-0036 + schema reference

**Files:**
- Modify: `docs/adr.md` (append ADR-0036 — **not** ADR-0035: that number is taken by "Regression UI tests target deployed UAT, not the PR artifact", `docs/adr.md:330`; confirm the tail before writing)
- Modify: `docs/reference/database-schema.md` (add `broker_sync_progress` + new `broker_connections` columns)

**Interfaces:** none — docs only.

- [ ] **Step 1: Append ADR-0036**

Follow the file's existing format (blank lines between heading/Status/Context/Decision/Consequences). Content: durable per-chunk sync progress table `broker_sync_progress` (PK `(connection_id, sync_kind)`, `next_chunk_end`), per-chunk transactions replacing the single full-history transaction, honest watermark/status columns on `broker_connections`, in-process single-flight (ShedLock deferred until multi-replica), ET scheduler zones (broker sync only; `RebalanceScheduler` explicitly out of scope), 5-year default lookback (Questrade retention ~16 months). Status: Accepted. Date: 2026-09-24.

- [ ] **Step 2: Update database-schema.md**

Add the `broker_sync_progress` table section and the two new `broker_connections` columns, matching the doc's existing table format.

- [ ] **Step 3: Commit**

```bash
git add docs/adr.md docs/reference/database-schema.md
git commit -m "docs: record ADR-0036 sync robustness semantics and schema changes"
```

---

## Execution notes

- Task order: 1→2→3 (gateway lane) and 4→5→7 (portfolio lane) are independent; 6 and 8 can run anytime after 4; 9 last. The Phase 1 note about PR #272 is stale — it merged as `58d274c`; no rebase expected.
- **Before execution:** re-verify all cited line numbers and the ADR number against current `main` (`cba70c4`+), since `main` has moved past this plan's baseline.
- The pre-existing test pinning `endDate = null` on incremental sync (`ActivityIngestionServiceTest`) must remain green — do not change the incremental call contract.
- V78/V79 run on deploy; verify live post-deploy (flyway history, progress-table behavior on a forced failure, watermark honesty) — add a Task 10-style live verification step at execution time if desired, mirroring Phase 1's Task 7.
- Gateway `libs/TwsApi.jar` + protobuf (removed IBKR adapter) and the WS client-id hardcoding are pre-existing cleanup candidates — out of Phase 2 scope.
