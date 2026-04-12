# Ingestion Microservice Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create a separate Spring Boot microservice module (`ingestion-service/`) that ingests stock, ETF, mutual fund, index, and bond data from EODHD into a dedicated `ingestion` PostgreSQL schema.

**Architecture:** Strategy pattern with `DataProvider` interface. EODHD is the sole provider for Milestone 1. Three-step pipeline: Exchange Sync → Universe Discovery → Raw Data Fetch (fundamentals stored as JSONB). Batch processing with coroutine-based concurrency, rate limiting, and hash-based change detection via Redis.

**Tech Stack:** Kotlin 2.0.21, Spring Boot 3.3.5, JDK 21, JPA/Hibernate, Flyway, Redis, PostgreSQL, Coroutines 1.8.1, MockK, Testcontainers

**Spec:** `docs/superpowers/specs/2026-04-11-ingestion-microservice-design.md`

**Important:** No JDK is installed on the local machine. All Gradle commands must run inside Docker containers.

---

## File Map

```
ingestion-service/
├── build.gradle.kts
├── settings.gradle.kts
├── Dockerfile
├── .dockerignore
├── gradle/wrapper/gradle-wrapper.jar
├── gradle/wrapper/gradle-wrapper.properties
├── gradlew
├── gradlew.bat
└── src/
    ├── main/
    │   ├── kotlin/com/portfolio/ingestion/
    │   │   ├── IngestionServiceApplication.kt
    │   │   ├── config/
    │   │   │   ├── IngestionProperties.kt
    │   │   │   ├── HttpClientConfig.kt
    │   │   │   └── RedisConfig.kt
    │   │   ├── provider/
    │   │   │   ├── DataProvider.kt
    │   │   │   ├── ProviderCapability.kt
    │   │   │   ├── ProviderRegistry.kt
    │   │   │   └── eodhd/
    │   │   │       ├── EodhdProvider.kt
    │   │   │       ├── EodhdClient.kt
    │   │   │       ├── EodhdDtos.kt
    │   │   │       └── EodhdRateLimiter.kt
    │   │   ├── pipeline/
    │   │   │   ├── IngestionOrchestrator.kt
    │   │   │   ├── ExchangeSyncStep.kt
    │   │   │   ├── UniverseSyncStep.kt
    │   │   │   ├── RawDataFetchStep.kt
    │   │   │   └── FundamentalsBatchProcessor.kt
    │   │   ├── persistence/
    │   │   │   ├── entity/
    │   │   │   │   ├── Exchange.kt
    │   │   │   │   ├── Instrument.kt
    │   │   │   │   ├── InstrumentExchange.kt
    │   │   │   │   ├── ProviderRawData.kt
    │   │   │   │   ├── ProviderConfig.kt
    │   │   │   │   ├── IngestionRun.kt
    │   │   │   │   ├── IngestionStep.kt
    │   │   │   │   └── IngestionError.kt
    │   │   │   └── repository/
    │   │   │       ├── ExchangeRepository.kt
    │   │   │       ├── InstrumentRepository.kt
    │   │   │       ├── InstrumentExchangeRepository.kt
    │   │   │       ├── ProviderRawDataRepository.kt
    │   │   │       ├── ProviderConfigRepository.kt
    │   │   │       ├── IngestionRunRepository.kt
    │   │   │       ├── IngestionStepRepository.kt
    │   │   │       └── IngestionErrorRepository.kt
    │   │   ├── tracking/
    │   │   │   ├── IngestionTrackingService.kt
    │   │   │   └── HashCacheService.kt
    │   │   ├── scheduler/
    │   │   │   └── IngestionScheduler.kt
    │   │   └── controller/
    │   │       └── AdminIngestionController.kt
    │   └── resources/
    │       ├── application.yml
    │       └── db/migration/
    │           └── V1__initial_schema.sql
    └── test/
        └── kotlin/com/portfolio/ingestion/
            ├── provider/eodhd/
            │   ├── EodhdClientTest.kt
            │   └── EodhdRateLimiterTest.kt
            ├── pipeline/
            │   ├── UniverseSyncStepTest.kt
            │   └── FundamentalsBatchProcessorTest.kt
            └── tracking/
                └── HashCacheServiceTest.kt
```

---

## Task 1: Module Scaffolding

**Files:**
- Create: `ingestion-service/build.gradle.kts`
- Create: `ingestion-service/settings.gradle.kts`
- Create: `ingestion-service/Dockerfile`
- Create: `ingestion-service/.dockerignore`
- Copy: `ingestion-service/gradle/` (wrapper from backend)
- Copy: `ingestion-service/gradlew`, `ingestion-service/gradlew.bat`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/IngestionServiceApplication.kt`
- Create: `ingestion-service/src/main/resources/application.yml`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
// ingestion-service/settings.gradle.kts
rootProject.name = "ingestion-service"
```

- [ ] **Step 2: Create build.gradle.kts**

```kotlin
// ingestion-service/build.gradle.kts
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("org.springframework.boot") version "3.3.5"
    id("io.spring.dependency-management") version "1.1.6"
    kotlin("jvm") version "2.0.21"
    kotlin("plugin.spring") version "2.0.21"
    kotlin("plugin.jpa") version "2.0.21"
}

group = "com.portfolio"
version = "0.0.1-SNAPSHOT"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

dependencies {
    // Spring Boot
    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    // Kotlin
    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    // Coroutines
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-core:1.8.1")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-reactor:1.8.1")

    // Database
    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    // Metrics
    implementation("io.micrometer:micrometer-registry-prometheus")

    // Logging
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    // Testing
    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.9")
    testImplementation("com.squareup.okhttp3:mockwebserver:4.12.0")
    testImplementation("org.jetbrains.kotlinx:kotlinx-coroutines-test:1.8.1")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict")
        jvmTarget.set(JvmTarget.JVM_21)
    }
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.bootJar {
    archiveFileName.set("app.jar")
}
```

- [ ] **Step 3: Copy Gradle wrapper from backend**

```bash
cp -r backend/gradle ingestion-service/gradle
cp backend/gradlew ingestion-service/gradlew
cp backend/gradlew.bat ingestion-service/gradlew.bat
```

- [ ] **Step 4: Create .dockerignore**

```
.gradle/
build/
!gradle/wrapper/gradle-wrapper.jar
!gradle/wrapper/gradle-wrapper.properties
.idea/
*.iws
*.iml
*.ipr
.vscode/
*.log
test-results/
*.local
.env*
.DS_Store
Thumbs.db
```

- [ ] **Step 5: Create Dockerfile**

```dockerfile
# ingestion-service/Dockerfile
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /app
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle
RUN gradle dependencies --no-daemon || true
COPY src ./src
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser
COPY --from=build /app/build/libs/app.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8081
HEALTHCHECK --interval=30s --timeout=3s --start-period=30s --retries=3 \
    CMD curl --fail --silent http://localhost:8081/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 6: Create Spring Boot application class**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/IngestionServiceApplication.kt
package com.portfolio.ingestion

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class IngestionServiceApplication

fun main(args: Array<String>) {
    runApplication<IngestionServiceApplication>(*args)
}
```

- [ ] **Step 7: Create application.yml**

```yaml
# ingestion-service/src/main/resources/application.yml
spring:
  application:
    name: ingestion-service
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/portfolio}
    username: ${DATABASE_USERNAME:portfolio}
    password: ${DATABASE_PASSWORD:portfolio}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: ingestion
  flyway:
    enabled: true
    schemas: ingestion
    default-schema: ingestion
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}

server:
  port: ${PORT:8081}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics

ingestion:
  enabled: ${INGESTION_ENABLED:true}
  schedule: ${INGESTION_SCHEDULE:0 0 22 * * *}
  stale-threshold-days: ${INGESTION_STALE_DAYS:7}
  target-exchanges:
    - US
    - TO
    - V
    - INDX
    - GBOND
  eodhd:
    base-url: https://eodhd.com/api
    api-key: ${EODHD_API_KEY:}
    rate-limit-per-second: 5
    daily-quota: 100000
    fundamentals-cost: 10
    batch-size: 500
```

- [ ] **Step 8: Add ingestion-service to docker-compose.yml**

Add after the `backend` service in `docker-compose.yml`:

```yaml
  ingestion-service:
    build:
      context: ./ingestion-service
      dockerfile: Dockerfile
    container_name: portfolio-ingestion
    environment:
      SPRING_PROFILES_ACTIVE: local
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-portfolio}
      DATABASE_USERNAME: ${POSTGRES_USER:-portfolio}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-portfolio}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      EODHD_API_KEY: ${EODHD_API_KEY:-}
    ports:
      - "8081:8081"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    networks:
      - portfolio-network
```

- [ ] **Step 9: Verify the module builds inside Docker**

```bash
docker compose build ingestion-service
```

Expected: Build succeeds (may fail on tests since no DB schema exists yet — that's fine, we used `-x test`).

- [ ] **Step 10: Commit**

```bash
git add ingestion-service/ docker-compose.yml
git commit -m "feat(ingestion): scaffold ingestion-service module with Spring Boot, Gradle, Docker"
```

---

## Task 2: Database Schema (Flyway Migration)

**Files:**
- Create: `ingestion-service/src/main/resources/db/migration/V1__initial_schema.sql`

- [ ] **Step 1: Create the initial migration**

```sql
-- ingestion-service/src/main/resources/db/migration/V1__initial_schema.sql

-- Exchanges reference table
CREATE TABLE exchanges (
    id SERIAL PRIMARY KEY,
    code VARCHAR(10) NOT NULL UNIQUE,
    name VARCHAR(200) NOT NULL,
    country VARCHAR(100),
    currency VARCHAR(3),
    operating_mic VARCHAR(10),
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

-- Instruments (one row per instrument globally)
CREATE TABLE instruments (
    id BIGSERIAL PRIMARY KEY,
    ticker VARCHAR(20) NOT NULL,
    name VARCHAR(500) NOT NULL,
    instrument_type VARCHAR(20) NOT NULL,
    isin VARCHAR(12),
    cusip VARCHAR(9),
    currency VARCHAR(3),
    country VARCHAR(3),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    source_last_seen_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_instruments_isin UNIQUE (isin)
);

CREATE INDEX idx_instruments_ticker ON instruments (ticker);
CREATE INDEX idx_instruments_type ON instruments (instrument_type);
CREATE INDEX idx_instruments_status ON instruments (status);

-- Many-to-many: instruments ↔ exchanges
CREATE TABLE instrument_exchanges (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    exchange_id INT NOT NULL REFERENCES exchanges(id) ON DELETE CASCADE,
    local_ticker VARCHAR(20),
    is_primary BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uq_instrument_exchange UNIQUE (instrument_id, exchange_id)
);

CREATE INDEX idx_ie_instrument ON instrument_exchanges (instrument_id);
CREATE INDEX idx_ie_exchange ON instrument_exchanges (exchange_id);

-- Raw data from providers (latest only, overwritten)
CREATE TABLE provider_raw_data (
    id BIGSERIAL PRIMARY KEY,
    instrument_id BIGINT NOT NULL REFERENCES instruments(id) ON DELETE CASCADE,
    provider VARCHAR(50) NOT NULL,
    data_type VARCHAR(30) NOT NULL,
    raw_payload JSONB NOT NULL,
    payload_hash VARCHAR(64),
    fetched_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    CONSTRAINT uq_provider_raw UNIQUE (instrument_id, provider, data_type)
);

CREATE INDEX idx_prd_instrument ON provider_raw_data (instrument_id);
CREATE INDEX idx_prd_provider_type ON provider_raw_data (provider, data_type);

-- Provider configuration and quota tracking
CREATE TABLE provider_config (
    id SERIAL PRIMARY KEY,
    provider_name VARCHAR(50) NOT NULL UNIQUE,
    enabled BOOLEAN NOT NULL DEFAULT true,
    priority INT NOT NULL DEFAULT 0,
    daily_quota INT,
    requests_used_today INT NOT NULL DEFAULT 0,
    last_quota_reset DATE,
    config_json JSONB
);

-- Ingestion run tracking
CREATE TABLE ingestion_runs (
    id BIGSERIAL PRIMARY KEY,
    run_type VARCHAR(20) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    trigger_source VARCHAR(100)
);

-- Ingestion step tracking
CREATE TABLE ingestion_steps (
    id BIGSERIAL PRIMARY KEY,
    run_id BIGINT NOT NULL REFERENCES ingestion_runs(id) ON DELETE CASCADE,
    step_name VARCHAR(50) NOT NULL,
    started_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    completed_at TIMESTAMPTZ,
    status VARCHAR(20) NOT NULL DEFAULT 'RUNNING',
    records_processed INT NOT NULL DEFAULT 0,
    records_created INT NOT NULL DEFAULT 0,
    records_updated INT NOT NULL DEFAULT 0,
    records_failed INT NOT NULL DEFAULT 0,
    metadata JSONB
);

CREATE INDEX idx_steps_run ON ingestion_steps (run_id);

-- Ingestion error tracking
CREATE TABLE ingestion_errors (
    id BIGSERIAL PRIMARY KEY,
    step_id BIGINT NOT NULL REFERENCES ingestion_steps(id) ON DELETE CASCADE,
    error_type VARCHAR(30) NOT NULL,
    error_code VARCHAR(50),
    error_message TEXT,
    context JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX idx_errors_step ON ingestion_errors (step_id);
CREATE INDEX idx_errors_type ON ingestion_errors (error_type);

-- Seed EODHD provider config
INSERT INTO provider_config (provider_name, enabled, priority, daily_quota, requests_used_today, last_quota_reset)
VALUES ('EODHD', true, 1, 100000, 0, CURRENT_DATE);
```

- [ ] **Step 2: Verify migration runs by starting the service**

```bash
docker compose up -d postgres redis
docker compose build ingestion-service
docker compose up ingestion-service
```

Check logs for: `Successfully applied 1 migration to schema "ingestion"`.

- [ ] **Step 3: Commit**

```bash
git add ingestion-service/src/main/resources/db/migration/
git commit -m "feat(ingestion): add V1 initial schema migration for ingestion schema"
```

---

## Task 3: JPA Entities & Repositories

**Files:**
- Create: All files under `ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/`
- Create: All files under `ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/`

- [ ] **Step 1: Create enum types**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/Enums.kt
package com.portfolio.ingestion.persistence.entity

enum class InstrumentType {
    STOCK, PREFERRED_STOCK, ETF, MUTUAL_FUND, INDEX, BOND
}

enum class InstrumentStatus {
    ACTIVE, DELISTED, SUSPENDED, PENDING
}

enum class RunType {
    SCHEDULED, MANUAL
}

enum class RunStatus {
    RUNNING, COMPLETED, FAILED, PARTIAL
}

enum class StepName {
    EXCHANGE_SYNC, UNIVERSE_SYNC, RAW_DATA_FETCH
}

enum class StepStatus {
    RUNNING, COMPLETED, FAILED, SKIPPED
}

enum class ErrorType {
    API_ERROR, PARSE_ERROR, DB_ERROR, RATE_LIMIT, VALIDATION_ERROR, DUPLICATE_ISIN, NOT_FOUND
}
```

- [ ] **Step 2: Create Exchange entity**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/Exchange.kt
package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "exchanges", schema = "ingestion")
class Exchange(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(nullable = false, unique = true, length = 10)
    val code: String,

    @Column(nullable = false, length = 200)
    var name: String,

    @Column(length = 100)
    var country: String? = null,

    @Column(length = 3)
    var currency: String? = null,

    @Column(name = "operating_mic", length = 10)
    var operatingMic: String? = null,

    @Column(name = "is_active", nullable = false)
    var isActive: Boolean = true,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 3: Create Instrument entity**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/Instrument.kt
package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "instruments", schema = "ingestion")
class Instrument(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Column(nullable = false, length = 20)
    var ticker: String,

    @Column(nullable = false, length = 500)
    var name: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", nullable = false, length = 20)
    var instrumentType: InstrumentType,

    @Column(length = 12, unique = true)
    var isin: String? = null,

    @Column(length = 9)
    var cusip: String? = null,

    @Column(length = 3)
    var currency: String? = null,

    @Column(length = 3)
    var country: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: InstrumentStatus = InstrumentStatus.ACTIVE,

    @Column(name = "source_last_seen_at")
    var sourceLastSeenAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 4: Create InstrumentExchange entity**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/InstrumentExchange.kt
package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*

@Entity
@Table(
    name = "instrument_exchanges", schema = "ingestion",
    uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "exchange_id"])]
)
class InstrumentExchange(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    val instrument: Instrument,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "exchange_id", nullable = false)
    val exchange: Exchange,

    @Column(name = "local_ticker", length = 20)
    var localTicker: String? = null,

    @Column(name = "is_primary", nullable = false)
    var isPrimary: Boolean = false
)
```

- [ ] **Step 5: Create ProviderRawData entity**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/ProviderRawData.kt
package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(
    name = "provider_raw_data", schema = "ingestion",
    uniqueConstraints = [UniqueConstraint(columnNames = ["instrument_id", "provider", "data_type"])]
)
class ProviderRawData(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instrument_id", nullable = false)
    val instrument: Instrument,

    @Column(nullable = false, length = 50)
    val provider: String,

    @Column(name = "data_type", nullable = false, length = 30)
    val dataType: String,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "raw_payload", nullable = false, columnDefinition = "jsonb")
    var rawPayload: JsonNode,

    @Column(name = "payload_hash", length = 64)
    var payloadHash: String? = null,

    @Column(name = "fetched_at", nullable = false)
    var fetchedAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 6: Create ProviderConfig entity**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/ProviderConfig.kt
package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.LocalDate

@Entity
@Table(name = "provider_config", schema = "ingestion")
class ProviderConfig(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Int = 0,

    @Column(name = "provider_name", nullable = false, unique = true, length = 50)
    val providerName: String,

    @Column(nullable = false)
    var enabled: Boolean = true,

    @Column(nullable = false)
    var priority: Int = 0,

    @Column(name = "daily_quota")
    var dailyQuota: Int? = null,

    @Column(name = "requests_used_today", nullable = false)
    var requestsUsedToday: Int = 0,

    @Column(name = "last_quota_reset")
    var lastQuotaReset: LocalDate? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "config_json", columnDefinition = "jsonb")
    var configJson: JsonNode? = null
)
```

- [ ] **Step 7: Create tracking entities (IngestionRun, IngestionStep, IngestionError)**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/IngestionRun.kt
package com.portfolio.ingestion.persistence.entity

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_runs", schema = "ingestion")
class IngestionRun(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @Enumerated(EnumType.STRING)
    @Column(name = "run_type", nullable = false, length = 20)
    val runType: RunType,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: RunStatus = RunStatus.RUNNING,

    @Column(name = "trigger_source", length = 100)
    val triggerSource: String? = null,

    @OneToMany(mappedBy = "run", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val steps: MutableList<IngestionStep> = mutableListOf()
)
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/IngestionStep.kt
package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_steps", schema = "ingestion")
class IngestionStep(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "run_id", nullable = false)
    val run: IngestionRun,

    @Enumerated(EnumType.STRING)
    @Column(name = "step_name", nullable = false, length = 50)
    val stepName: StepName,

    @Column(name = "started_at", nullable = false)
    val startedAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "completed_at")
    var completedAt: OffsetDateTime? = null,

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    var status: StepStatus = StepStatus.RUNNING,

    @Column(name = "records_processed", nullable = false)
    var recordsProcessed: Int = 0,

    @Column(name = "records_created", nullable = false)
    var recordsCreated: Int = 0,

    @Column(name = "records_updated", nullable = false)
    var recordsUpdated: Int = 0,

    @Column(name = "records_failed", nullable = false)
    var recordsFailed: Int = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    var metadata: JsonNode? = null,

    @OneToMany(mappedBy = "step", cascade = [CascadeType.ALL], fetch = FetchType.LAZY)
    val errors: MutableList<IngestionError> = mutableListOf()
)
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/entity/IngestionError.kt
package com.portfolio.ingestion.persistence.entity

import com.fasterxml.jackson.databind.JsonNode
import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.time.OffsetDateTime

@Entity
@Table(name = "ingestion_errors", schema = "ingestion")
class IngestionError(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "step_id", nullable = false)
    val step: IngestionStep,

    @Enumerated(EnumType.STRING)
    @Column(name = "error_type", nullable = false, length = 30)
    val errorType: ErrorType,

    @Column(name = "error_code", length = 50)
    val errorCode: String? = null,

    @Column(name = "error_message", columnDefinition = "text")
    val errorMessage: String? = null,

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(columnDefinition = "jsonb")
    val context: JsonNode? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 8: Create all repositories**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/ExchangeRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.Exchange
import org.springframework.data.jpa.repository.JpaRepository

interface ExchangeRepository : JpaRepository<Exchange, Int> {
    fun findByCode(code: String): Exchange?
    fun findByIsActiveTrue(): List<Exchange>
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/InstrumentRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.Instrument
import com.portfolio.ingestion.persistence.entity.InstrumentType
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query

interface InstrumentRepository : JpaRepository<Instrument, Long> {
    fun findByIsin(isin: String): Instrument?
    fun findByTickerAndInstrumentType(ticker: String, type: InstrumentType): Instrument?

    @Query("""
        SELECT i FROM Instrument i
        LEFT JOIN ProviderRawData prd ON prd.instrument.id = i.id
            AND prd.provider = 'EODHD' AND prd.dataType = 'FUNDAMENTALS'
        WHERE i.status = 'ACTIVE'
        ORDER BY prd.fetchedAt ASC NULLS FIRST,
            CASE i.instrumentType
                WHEN 'STOCK' THEN 1
                WHEN 'ETF' THEN 2
                WHEN 'MUTUAL_FUND' THEN 3
                WHEN 'PREFERRED_STOCK' THEN 4
                WHEN 'BOND' THEN 5
                WHEN 'INDEX' THEN 6
                ELSE 7
            END
    """)
    fun findStaleInstruments(pageable: Pageable): List<Instrument>
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/InstrumentExchangeRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.InstrumentExchange
import org.springframework.data.jpa.repository.JpaRepository

interface InstrumentExchangeRepository : JpaRepository<InstrumentExchange, Long> {
    fun findByInstrumentIdAndExchangeId(instrumentId: Long, exchangeId: Int): InstrumentExchange?
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/ProviderRawDataRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.ProviderRawData
import org.springframework.data.jpa.repository.JpaRepository

interface ProviderRawDataRepository : JpaRepository<ProviderRawData, Long> {
    fun findByInstrumentIdAndProviderAndDataType(instrumentId: Long, provider: String, dataType: String): ProviderRawData?
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/ProviderConfigRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.ProviderConfig
import org.springframework.data.jpa.repository.JpaRepository

interface ProviderConfigRepository : JpaRepository<ProviderConfig, Int> {
    fun findByProviderName(providerName: String): ProviderConfig?
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/IngestionRunRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionRun
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionRunRepository : JpaRepository<IngestionRun, Long> {
    fun findAllByOrderByStartedAtDesc(pageable: Pageable): List<IngestionRun>
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/IngestionStepRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionStep
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionStepRepository : JpaRepository<IngestionStep, Long> {
    fun findByRunId(runId: Long): List<IngestionStep>
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/repository/IngestionErrorRepository.kt
package com.portfolio.ingestion.persistence.repository

import com.portfolio.ingestion.persistence.entity.IngestionError
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository

interface IngestionErrorRepository : JpaRepository<IngestionError, Long> {
    fun findByStepId(stepId: Long): List<IngestionError>
    fun findByStepRunIdOrderByCreatedAtDesc(runId: Long, pageable: Pageable): List<IngestionError>
}
```

- [ ] **Step 9: Verify entities compile by building**

```bash
docker compose build ingestion-service
```

Expected: Build succeeds.

- [ ] **Step 10: Commit**

```bash
git add ingestion-service/src/main/kotlin/com/portfolio/ingestion/persistence/
git commit -m "feat(ingestion): add JPA entities and repositories for ingestion schema"
```

---

## Task 4: Configuration & Infrastructure

**Files:**
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/IngestionProperties.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/HttpClientConfig.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/RedisConfig.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/tracking/IngestionTrackingService.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/tracking/HashCacheService.kt`

- [ ] **Step 1: Create IngestionProperties**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/IngestionProperties.kt
package com.portfolio.ingestion.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "ingestion")
data class IngestionProperties(
    val enabled: Boolean = true,
    val schedule: String = "0 0 22 * * *",
    val staleThresholdDays: Int = 7,
    val targetExchanges: List<String> = listOf("US", "TO", "V", "INDX", "GBOND"),
    val eodhd: EodhdProperties = EodhdProperties()
)

data class EodhdProperties(
    val baseUrl: String = "https://eodhd.com/api",
    val apiKey: String = "",
    val rateLimitPerSecond: Int = 5,
    val dailyQuota: Int = 100000,
    val fundamentalsCost: Int = 10,
    val batchSize: Int = 500
)
```

- [ ] **Step 2: Create HttpClientConfig**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/HttpClientConfig.kt
package com.portfolio.ingestion.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.web.reactive.function.client.WebClient

@Configuration
class HttpClientConfig {

    @Bean
    fun eodhdWebClient(props: IngestionProperties): WebClient =
        WebClient.builder()
            .baseUrl(props.eodhd.baseUrl)
            .defaultHeader("Accept", "application/json")
            .codecs { it.defaultCodecs().maxInMemorySize(16 * 1024 * 1024) }
            .build()
}
```

- [ ] **Step 3: Create RedisConfig**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/RedisConfig.kt
package com.portfolio.ingestion.config

import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.StringRedisTemplate

@Configuration
class RedisConfig {

    @Bean
    fun stringRedisTemplate(connectionFactory: RedisConnectionFactory): StringRedisTemplate =
        StringRedisTemplate(connectionFactory)
}
```

- [ ] **Step 4: Create IngestionTrackingService**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/tracking/IngestionTrackingService.kt
package com.portfolio.ingestion.tracking

import com.fasterxml.jackson.databind.JsonNode
import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.persistence.repository.IngestionErrorRepository
import com.portfolio.ingestion.persistence.repository.IngestionRunRepository
import com.portfolio.ingestion.persistence.repository.IngestionStepRepository
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class IngestionTrackingService(
    private val runRepo: IngestionRunRepository,
    private val stepRepo: IngestionStepRepository,
    private val errorRepo: IngestionErrorRepository,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun startRun(runType: RunType, triggerSource: String): IngestionRun {
        val run = IngestionRun(runType = runType, triggerSource = triggerSource)
        return runRepo.save(run)
    }

    fun completeRun(run: IngestionRun, status: RunStatus) {
        run.status = status
        run.completedAt = OffsetDateTime.now()
        runRepo.save(run)
    }

    fun startStep(run: IngestionRun, stepName: StepName): IngestionStep {
        val step = IngestionStep(run = run, stepName = stepName)
        return stepRepo.save(step)
    }

    fun completeStep(step: IngestionStep, status: StepStatus, processed: Int, created: Int, updated: Int, failed: Int, metadata: Map<String, Any>? = null) {
        step.status = status
        step.completedAt = OffsetDateTime.now()
        step.recordsProcessed = processed
        step.recordsCreated = created
        step.recordsUpdated = updated
        step.recordsFailed = failed
        step.metadata = metadata?.let { objectMapper.valueToTree(it) }
        stepRepo.save(step)
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    fun logError(step: IngestionStep, errorType: ErrorType, errorCode: String? = null, errorMessage: String? = null, context: Map<String, Any>? = null) {
        val contextNode: JsonNode? = context?.let { objectMapper.valueToTree(it) }
        val error = IngestionError(
            step = step,
            errorType = errorType,
            errorCode = errorCode,
            errorMessage = errorMessage,
            context = contextNode
        )
        errorRepo.save(error)
        log.warn("Ingestion error [{}] {}: {}", errorType, errorCode ?: "", errorMessage ?: "")
    }
}
```

- [ ] **Step 5: Create HashCacheService**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/tracking/HashCacheService.kt
package com.portfolio.ingestion.tracking

import org.slf4j.LoggerFactory
import org.springframework.data.redis.core.StringRedisTemplate
import org.springframework.stereotype.Service
import java.security.MessageDigest
import java.time.Duration

@Service
class HashCacheService(
    private val redisTemplate: StringRedisTemplate
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ttl = Duration.ofHours(36)

    fun computeHash(payload: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        return digest.digest(payload.toByteArray()).joinToString("") { "%02x".format(it) }
    }

    fun isUnchanged(key: String, newHash: String): Boolean {
        return try {
            val cached = redisTemplate.opsForValue().get("ingestion:hash:$key")
            cached == newHash
        } catch (e: Exception) {
            log.debug("Redis unavailable for hash check, treating as changed: {}", e.message)
            false
        }
    }

    fun storeHash(key: String, hash: String) {
        try {
            redisTemplate.opsForValue().set("ingestion:hash:$key", hash, ttl)
        } catch (e: Exception) {
            log.debug("Redis unavailable for hash store: {}", e.message)
        }
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add ingestion-service/src/main/kotlin/com/portfolio/ingestion/config/ ingestion-service/src/main/kotlin/com/portfolio/ingestion/tracking/
git commit -m "feat(ingestion): add configuration, tracking service, and hash cache"
```

---

## Task 5: Provider Interface & EODHD Client

**Files:**
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/DataProvider.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/ProviderCapability.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/ProviderRegistry.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdDtos.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdClient.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdRateLimiter.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdProvider.kt`

- [ ] **Step 1: Create provider interfaces**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/ProviderCapability.kt
package com.portfolio.ingestion.provider

enum class ProviderCapability {
    EXCHANGES, UNIVERSE, FUNDAMENTALS, HOLDINGS, PRICING
}
```

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/DataProvider.kt
package com.portfolio.ingestion.provider

import com.fasterxml.jackson.databind.JsonNode

data class RawExchange(
    val code: String,
    val name: String,
    val country: String?,
    val currency: String?,
    val operatingMic: String?
)

data class RawInstrument(
    val ticker: String,
    val name: String,
    val type: String,
    val exchange: String,
    val currency: String?,
    val country: String?,
    val isin: String?
)

interface DataProvider {
    fun name(): String
    fun capabilities(): Set<ProviderCapability>
    suspend fun fetchExchanges(): List<RawExchange>
    suspend fun fetchUniverse(exchange: String): List<RawInstrument>
    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode?
}
```

- [ ] **Step 2: Create ProviderRegistry**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/ProviderRegistry.kt
package com.portfolio.ingestion.provider

import org.springframework.stereotype.Component

@Component
class ProviderRegistry(providers: List<DataProvider>) {

    private val providerMap = providers.associateBy { it.name() }

    fun getProvider(name: String): DataProvider? = providerMap[name]

    fun getProvidersWithCapability(capability: ProviderCapability): List<DataProvider> =
        providerMap.values.filter { capability in it.capabilities() }

    fun allProviders(): Collection<DataProvider> = providerMap.values
}
```

- [ ] **Step 3: Create EODHD DTOs**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdDtos.kt
package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdExchangeDto(
    @JsonProperty("Code") val code: String,
    @JsonProperty("Name") val name: String,
    @JsonProperty("Country") val country: String?,
    @JsonProperty("Currency") val currency: String?,
    @JsonProperty("OperatingMIC") val operatingMic: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class EodhdSymbolDto(
    @JsonProperty("Code") val code: String,
    @JsonProperty("Name") val name: String?,
    @JsonProperty("Country") val country: String?,
    @JsonProperty("Exchange") val exchange: String?,
    @JsonProperty("Currency") val currency: String?,
    @JsonProperty("Type") val type: String?,
    @JsonProperty("Isin") val isin: String?
)
```

- [ ] **Step 4: Create EodhdRateLimiter**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdRateLimiter.kt
package com.portfolio.ingestion.provider.eodhd

import com.portfolio.ingestion.config.IngestionProperties
import io.micrometer.core.instrument.Counter
import io.micrometer.core.instrument.MeterRegistry
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.util.concurrent.atomic.AtomicInteger

@Component
class EodhdRateLimiter(
    props: IngestionProperties,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val ratePerSecond = props.eodhd.rateLimitPerSecond
    private val semaphore = Semaphore(ratePerSecond)
    private val delayMs = 1000L / ratePerSecond

    private val dailyQuota = props.eodhd.dailyQuota
    private val dailyUsed = AtomicInteger(0)
    private val waitCounter = Counter.builder("eodhd_rate_limiter_wait_total").register(meterRegistry)

    suspend fun acquire(apiCost: Int = 1) {
        semaphore.acquire()
        try {
            delay(delayMs)
            waitCounter.increment()
        } finally {
            semaphore.release()
        }
        dailyUsed.addAndGet(apiCost)
    }

    fun remainingDailyQuota(): Int = (dailyQuota - dailyUsed.get()).coerceAtLeast(0)

    fun recordApiCalls(count: Int) {
        dailyUsed.addAndGet(count)
    }

    fun resetDailyQuota() {
        dailyUsed.set(0)
        log.info("EODHD daily quota reset")
    }
}
```

- [ ] **Step 5: Create EodhdClient**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdClient.kt
package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.ingestion.config.IngestionProperties
import io.micrometer.core.instrument.MeterRegistry
import io.micrometer.core.instrument.Timer
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.web.reactive.function.client.WebClient
import org.springframework.web.reactive.function.client.awaitBody

@Component
class EodhdClient(
    private val eodhdWebClient: WebClient,
    private val props: IngestionProperties,
    meterRegistry: MeterRegistry
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val apiKey = props.eodhd.apiKey
    private val latencyTimer = Timer.builder("eodhd_api_latency_seconds").register(meterRegistry)

    suspend fun fetchExchanges(): List<EodhdExchangeDto> {
        log.debug("Fetching exchange list from EODHD")
        return latencyTimer.recordCallable {
            kotlinx.coroutines.runBlocking {
                eodhdWebClient.get()
                    .uri("/exchanges-list/?api_token=$apiKey&fmt=json")
                    .retrieve()
                    .awaitBody<List<EodhdExchangeDto>>()
            }
        }!!
    }

    suspend fun fetchSymbols(exchange: String): List<EodhdSymbolDto> {
        log.debug("Fetching symbols for exchange: {}", exchange)
        return eodhdWebClient.get()
            .uri("/exchange-symbol-list/$exchange?api_token=$apiKey&fmt=json")
            .retrieve()
            .awaitBody()
    }

    suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode {
        log.debug("Fetching fundamentals for {}.{}", ticker, exchange)
        return eodhdWebClient.get()
            .uri("/fundamentals/$ticker.$exchange?api_token=$apiKey&fmt=json")
            .retrieve()
            .awaitBody()
    }
}
```

- [ ] **Step 6: Create EodhdProvider**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/eodhd/EodhdProvider.kt
package com.portfolio.ingestion.provider.eodhd

import com.fasterxml.jackson.databind.JsonNode
import com.portfolio.ingestion.provider.*
import org.springframework.stereotype.Component

@Component
class EodhdProvider(
    private val client: EodhdClient
) : DataProvider {

    override fun name(): String = "EODHD"

    override fun capabilities(): Set<ProviderCapability> = setOf(
        ProviderCapability.EXCHANGES,
        ProviderCapability.UNIVERSE,
        ProviderCapability.FUNDAMENTALS
    )

    override suspend fun fetchExchanges(): List<RawExchange> =
        client.fetchExchanges().map { dto ->
            RawExchange(
                code = dto.code,
                name = dto.name,
                country = dto.country,
                currency = dto.currency,
                operatingMic = dto.operatingMic
            )
        }

    override suspend fun fetchUniverse(exchange: String): List<RawInstrument> =
        client.fetchSymbols(exchange).mapNotNull { dto ->
            if (dto.name.isNullOrBlank()) return@mapNotNull null
            RawInstrument(
                ticker = dto.code,
                name = dto.name,
                type = dto.type ?: "Unknown",
                exchange = exchange,
                currency = dto.currency,
                country = dto.country,
                isin = dto.isin?.takeIf { it.isNotBlank() && it.length == 12 }
            )
        }

    override suspend fun fetchFundamentals(ticker: String, exchange: String): JsonNode? =
        client.fetchFundamentals(ticker, exchange)
}
```

- [ ] **Step 7: Commit**

```bash
git add ingestion-service/src/main/kotlin/com/portfolio/ingestion/provider/
git commit -m "feat(ingestion): add DataProvider interface, ProviderRegistry, and EODHD adapter"
```

---

## Task 6: Pipeline Steps

**Files:**
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/ExchangeSyncStep.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/UniverseSyncStep.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/RawDataFetchStep.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/FundamentalsBatchProcessor.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/IngestionOrchestrator.kt`

- [ ] **Step 1: Create ExchangeSyncStep**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/ExchangeSyncStep.kt
package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.persistence.entity.Exchange
import com.portfolio.ingestion.persistence.entity.ErrorType
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.repository.ExchangeRepository
import com.portfolio.ingestion.provider.ProviderCapability
import com.portfolio.ingestion.provider.ProviderRegistry
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component

@Component
class ExchangeSyncStep(
    private val providerRegistry: ProviderRegistry,
    private val exchangeRepo: ExchangeRepository,
    private val tracking: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val created: Int, val updated: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val provider = providerRegistry.getProvidersWithCapability(ProviderCapability.EXCHANGES).firstOrNull()
            ?: throw IllegalStateException("No provider with EXCHANGES capability")

        var processed = 0
        var created = 0
        var updated = 0
        var failed = 0

        try {
            val exchanges = provider.fetchExchanges()
            for (raw in exchanges) {
                processed++
                try {
                    val existing = exchangeRepo.findByCode(raw.code)
                    if (existing != null) {
                        existing.name = raw.name
                        existing.country = raw.country
                        existing.currency = raw.currency
                        existing.operatingMic = raw.operatingMic
                        exchangeRepo.save(existing)
                        updated++
                    } else {
                        exchangeRepo.save(Exchange(
                            code = raw.code,
                            name = raw.name,
                            country = raw.country,
                            currency = raw.currency,
                            operatingMic = raw.operatingMic
                        ))
                        created++
                    }
                } catch (e: Exception) {
                    failed++
                    tracking.logError(step, ErrorType.DB_ERROR, errorMessage = "Failed to save exchange ${raw.code}: ${e.message}")
                }
            }
        } catch (e: Exception) {
            tracking.logError(step, ErrorType.API_ERROR, errorMessage = "Failed to fetch exchanges: ${e.message}")
        }

        log.info("Exchange sync: processed={}, created={}, updated={}, failed={}", processed, created, updated, failed)
        return Result(processed, created, updated, failed)
    }
}
```

- [ ] **Step 2: Create UniverseSyncStep**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/UniverseSyncStep.kt
package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.persistence.repository.ExchangeRepository
import com.portfolio.ingestion.persistence.repository.InstrumentExchangeRepository
import com.portfolio.ingestion.persistence.repository.InstrumentRepository
import com.portfolio.ingestion.provider.ProviderCapability
import com.portfolio.ingestion.provider.ProviderRegistry
import com.portfolio.ingestion.provider.RawInstrument
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import java.time.OffsetDateTime

@Component
class UniverseSyncStep(
    private val providerRegistry: ProviderRegistry,
    private val instrumentRepo: InstrumentRepository,
    private val instrumentExchangeRepo: InstrumentExchangeRepository,
    private val exchangeRepo: ExchangeRepository,
    private val tracking: IngestionTrackingService,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val created: Int, val updated: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val provider = providerRegistry.getProvidersWithCapability(ProviderCapability.UNIVERSE).firstOrNull()
            ?: throw IllegalStateException("No provider with UNIVERSE capability")

        var totalProcessed = 0
        var totalCreated = 0
        var totalUpdated = 0
        var totalFailed = 0

        for (exchangeCode in props.targetExchanges) {
            val exchange = exchangeRepo.findByCode(exchangeCode)
            if (exchange == null) {
                log.warn("Exchange {} not found in database, skipping", exchangeCode)
                tracking.logError(step, ErrorType.VALIDATION_ERROR, errorMessage = "Exchange $exchangeCode not found")
                continue
            }

            try {
                val symbols = provider.fetchUniverse(exchangeCode)
                log.info("Fetched {} symbols for exchange {}", symbols.size, exchangeCode)

                for (raw in symbols) {
                    totalProcessed++
                    try {
                        val result = upsertInstrument(raw, exchange)
                        when (result) {
                            UpsertResult.CREATED -> totalCreated++
                            UpsertResult.UPDATED -> totalUpdated++
                            UpsertResult.SKIPPED -> {} // already up to date
                        }
                    } catch (e: Exception) {
                        totalFailed++
                        tracking.logError(step, ErrorType.DB_ERROR, errorMessage = "Failed to upsert ${raw.ticker}: ${e.message}")
                    }
                }
            } catch (e: Exception) {
                tracking.logError(step, ErrorType.API_ERROR, errorMessage = "Failed to fetch universe for $exchangeCode: ${e.message}")
            }
        }

        log.info("Universe sync: processed={}, created={}, updated={}, failed={}", totalProcessed, totalCreated, totalUpdated, totalFailed)
        return Result(totalProcessed, totalCreated, totalUpdated, totalFailed)
    }

    private fun upsertInstrument(raw: RawInstrument, exchange: Exchange): UpsertResult {
        val instrumentType = mapType(raw.type)
        val now = OffsetDateTime.now()

        // Try to find by ISIN first (globally unique), then by ticker + type
        val existing = raw.isin?.let { instrumentRepo.findByIsin(it) }
            ?: instrumentRepo.findByTickerAndInstrumentType(raw.ticker, instrumentType)

        val instrument = if (existing != null) {
            existing.name = raw.name
            existing.sourceLastSeenAt = now
            existing.updatedAt = now
            instrumentRepo.save(existing)
        } else {
            instrumentRepo.save(Instrument(
                ticker = raw.ticker,
                name = raw.name,
                instrumentType = instrumentType,
                isin = raw.isin,
                currency = raw.currency,
                country = raw.country,
                sourceLastSeenAt = now
            ))
        }

        // Link to exchange (many-to-many)
        val link = instrumentExchangeRepo.findByInstrumentIdAndExchangeId(instrument.id, exchange.id)
        if (link == null) {
            instrumentExchangeRepo.save(InstrumentExchange(
                instrument = instrument,
                exchange = exchange,
                localTicker = raw.ticker,
                isPrimary = true
            ))
        }

        return if (existing != null) UpsertResult.UPDATED else UpsertResult.CREATED
    }

    private fun mapType(rawType: String): InstrumentType = when (rawType.lowercase()) {
        "common stock", "stock" -> InstrumentType.STOCK
        "preferred stock" -> InstrumentType.PREFERRED_STOCK
        "etf" -> InstrumentType.ETF
        "fund", "mutual fund" -> InstrumentType.MUTUAL_FUND
        "index" -> InstrumentType.INDEX
        "bond" -> InstrumentType.BOND
        else -> InstrumentType.STOCK // default fallback
    }

    private enum class UpsertResult { CREATED, UPDATED, SKIPPED }
}
```

- [ ] **Step 3: Create FundamentalsBatchProcessor**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/FundamentalsBatchProcessor.kt
package com.portfolio.ingestion.pipeline

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.ErrorType
import com.portfolio.ingestion.persistence.entity.Instrument
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.entity.ProviderRawData
import com.portfolio.ingestion.persistence.repository.ProviderRawDataRepository
import com.portfolio.ingestion.provider.eodhd.EodhdClient
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import com.portfolio.ingestion.tracking.HashCacheService
import com.portfolio.ingestion.tracking.IngestionTrackingService
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Component
import org.springframework.transaction.annotation.Propagation
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Component
class FundamentalsBatchProcessor(
    private val client: EodhdClient,
    private val rateLimiter: EodhdRateLimiter,
    private val hashCache: HashCacheService,
    private val rawDataRepo: ProviderRawDataRepository,
    private val tracking: IngestionTrackingService,
    private val objectMapper: ObjectMapper,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class InstrumentWithExchange(val instrument: Instrument, val exchangeCode: String)
    data class BatchResult(val processed: Int, val updated: Int, val skipped: Int, val failed: Int)

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    suspend fun processBatch(items: List<InstrumentWithExchange>, step: IngestionStep): BatchResult {
        var updated = 0
        var skipped = 0
        var failed = 0
        val cost = props.eodhd.fundamentalsCost

        coroutineScope {
            val results = items.map { (instrument, exchange) ->
                async {
                    try {
                        rateLimiter.acquire(cost)

                        val ticker = instrument.ticker

                        val payload = client.fetchFundamentals(ticker, exchange)
                        val payloadStr = objectMapper.writeValueAsString(payload)
                        val hash = hashCache.computeHash(payloadStr)
                        val cacheKey = "EODHD:FUNDAMENTALS:${instrument.id}"

                        if (hashCache.isUnchanged(cacheKey, hash)) {
                            return@async FetchResult.SKIPPED
                        }

                        // Upsert raw data
                        val existing = rawDataRepo.findByInstrumentIdAndProviderAndDataType(
                            instrument.id, "EODHD", "FUNDAMENTALS"
                        )

                        if (existing != null) {
                            existing.rawPayload = payload
                            existing.payloadHash = hash
                            existing.fetchedAt = OffsetDateTime.now()
                            rawDataRepo.save(existing)
                        } else {
                            rawDataRepo.save(ProviderRawData(
                                instrument = instrument,
                                provider = "EODHD",
                                dataType = "FUNDAMENTALS",
                                rawPayload = payload,
                                payloadHash = hash
                            ))
                        }

                        hashCache.storeHash(cacheKey, hash)
                        FetchResult.UPDATED
                    } catch (e: Exception) {
                        tracking.logError(step, ErrorType.API_ERROR,
                            errorMessage = "Failed fundamentals for ${instrument.ticker}: ${e.message}",
                            context = mapOf("instrumentId" to instrument.id, "ticker" to instrument.ticker)
                        )
                        FetchResult.FAILED
                    }
                }
            }

            for (result in results.awaitAll()) {
                when (result) {
                    FetchResult.UPDATED -> updated++
                    FetchResult.SKIPPED -> skipped++
                    FetchResult.FAILED -> failed++
                }
            }
        }

        log.info("Batch: processed={}, updated={}, skipped={}, failed={}", instruments.size, updated, skipped, failed)
        return BatchResult(instruments.size, updated, skipped, failed)
    }

    private enum class FetchResult { UPDATED, SKIPPED, FAILED }
}
```

- [ ] **Step 4: Create RawDataFetchStep**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/RawDataFetchStep.kt
package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.persistence.entity.IngestionStep
import com.portfolio.ingestion.persistence.repository.InstrumentRepository
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import org.slf4j.LoggerFactory
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Component

@Component
class RawDataFetchStep(
    private val instrumentRepo: InstrumentRepository,
    private val batchProcessor: FundamentalsBatchProcessor,
    private val rateLimiter: EodhdRateLimiter,
    private val props: IngestionProperties
) {
    private val log = LoggerFactory.getLogger(javaClass)

    data class Result(val processed: Int, val updated: Int, val skipped: Int, val failed: Int)

    suspend fun execute(step: IngestionStep): Result {
        val batchSize = props.eodhd.batchSize
        val maxInstruments = props.eodhd.dailyQuota / props.eodhd.fundamentalsCost
        var totalProcessed = 0
        var totalUpdated = 0
        var totalSkipped = 0
        var totalFailed = 0
        var offset = 0

        while (true) {
            if (rateLimiter.remainingDailyQuota() < props.eodhd.fundamentalsCost * batchSize) {
                log.warn("Daily quota nearly exhausted, stopping. Remaining: {}", rateLimiter.remainingDailyQuota())
                break
            }

            val instruments = instrumentRepo.findStaleInstruments(PageRequest.of(offset / batchSize, batchSize))
            if (instruments.isEmpty()) break

            val result = batchProcessor.processBatch(instruments, step)
            totalProcessed += result.processed
            totalUpdated += result.updated
            totalSkipped += result.skipped
            totalFailed += result.failed
            offset += batchSize

            if (totalProcessed >= maxInstruments) {
                log.info("Reached daily instrument limit ({}), stopping", maxInstruments)
                break
            }
        }

        log.info("Raw data fetch: processed={}, updated={}, skipped={}, failed={}", totalProcessed, totalUpdated, totalSkipped, totalFailed)
        return Result(totalProcessed, totalUpdated, totalSkipped, totalFailed)
    }
}
```

- [ ] **Step 5: Create IngestionOrchestrator**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/IngestionOrchestrator.kt
package com.portfolio.ingestion.pipeline

import com.portfolio.ingestion.persistence.entity.*
import com.portfolio.ingestion.tracking.IngestionTrackingService
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class IngestionOrchestrator(
    private val exchangeSyncStep: ExchangeSyncStep,
    private val universeSyncStep: UniverseSyncStep,
    private val rawDataFetchStep: RawDataFetchStep,
    private val tracking: IngestionTrackingService
) {
    private val log = LoggerFactory.getLogger(javaClass)

    suspend fun runExchangeSync(triggerSource: String) {
        val run = tracking.startRun(RunType.MANUAL, triggerSource)
        val step = tracking.startStep(run, StepName.EXCHANGE_SYNC)
        try {
            val result = exchangeSyncStep.execute(step)
            tracking.completeStep(step, StepStatus.COMPLETED, result.processed, result.created, result.updated, result.failed)
            tracking.completeRun(run, RunStatus.COMPLETED)
        } catch (e: Exception) {
            log.error("Exchange sync failed", e)
            tracking.completeStep(step, StepStatus.FAILED, 0, 0, 0, 0)
            tracking.completeRun(run, RunStatus.FAILED)
        }
    }

    suspend fun runFullIngestion(triggerSource: String) {
        val run = tracking.startRun(RunType.SCHEDULED, triggerSource)
        var hasFailures = false

        // Step 1: Universe sync
        val universeStep = tracking.startStep(run, StepName.UNIVERSE_SYNC)
        try {
            val result = universeSyncStep.execute(universeStep)
            val status = if (result.failed > 0) StepStatus.COMPLETED else StepStatus.COMPLETED
            tracking.completeStep(universeStep, status, result.processed, result.created, result.updated, result.failed)
            if (result.failed > 0) hasFailures = true
        } catch (e: Exception) {
            log.error("Universe sync failed", e)
            tracking.completeStep(universeStep, StepStatus.FAILED, 0, 0, 0, 0)
            hasFailures = true
        }

        // Step 2: Raw data fetch
        val fetchStep = tracking.startStep(run, StepName.RAW_DATA_FETCH)
        try {
            val result = rawDataFetchStep.execute(fetchStep)
            tracking.completeStep(fetchStep, StepStatus.COMPLETED, result.processed, 0, result.updated, result.failed)
            if (result.failed > 0) hasFailures = true
        } catch (e: Exception) {
            log.error("Raw data fetch failed", e)
            tracking.completeStep(fetchStep, StepStatus.FAILED, 0, 0, 0, 0)
            hasFailures = true
        }

        tracking.completeRun(run, if (hasFailures) RunStatus.PARTIAL else RunStatus.COMPLETED)
        log.info("Full ingestion complete: status={}", run.status)
    }
}
```

- [ ] **Step 6: Commit**

```bash
git add ingestion-service/src/main/kotlin/com/portfolio/ingestion/pipeline/
git commit -m "feat(ingestion): add pipeline steps - ExchangeSync, UniverseSync, RawDataFetch, Orchestrator"
```

---

## Task 7: Scheduler & Admin Controller

**Files:**
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/scheduler/IngestionScheduler.kt`
- Create: `ingestion-service/src/main/kotlin/com/portfolio/ingestion/controller/AdminIngestionController.kt`

- [ ] **Step 1: Create IngestionScheduler**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/scheduler/IngestionScheduler.kt
package com.portfolio.ingestion.scheduler

import com.portfolio.ingestion.config.IngestionProperties
import com.portfolio.ingestion.pipeline.IngestionOrchestrator
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import kotlinx.coroutines.runBlocking
import org.slf4j.LoggerFactory
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty
import org.springframework.scheduling.annotation.Scheduled
import org.springframework.stereotype.Component

@Component
@ConditionalOnProperty(name = ["ingestion.enabled"], havingValue = "true", matchIfMissing = true)
class IngestionScheduler(
    private val orchestrator: IngestionOrchestrator,
    private val rateLimiter: EodhdRateLimiter
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @Scheduled(cron = "\${ingestion.schedule:0 0 22 * * *}")
    fun runNightlyIngestion() {
        log.info("Starting nightly ingestion")
        rateLimiter.resetDailyQuota()
        try {
            runBlocking {
                orchestrator.runFullIngestion("scheduler")
            }
        } catch (e: Exception) {
            log.error("Nightly ingestion failed", e)
        }
    }
}
```

- [ ] **Step 2: Create AdminIngestionController**

```kotlin
// ingestion-service/src/main/kotlin/com/portfolio/ingestion/controller/AdminIngestionController.kt
package com.portfolio.ingestion.controller

import com.portfolio.ingestion.persistence.repository.IngestionErrorRepository
import com.portfolio.ingestion.persistence.repository.IngestionRunRepository
import com.portfolio.ingestion.persistence.repository.IngestionStepRepository
import com.portfolio.ingestion.persistence.repository.InstrumentRepository
import com.portfolio.ingestion.pipeline.IngestionOrchestrator
import com.portfolio.ingestion.provider.eodhd.EodhdRateLimiter
import kotlinx.coroutines.runBlocking
import org.springframework.data.domain.PageRequest
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/admin/ingestion")
class AdminIngestionController(
    private val orchestrator: IngestionOrchestrator,
    private val runRepo: IngestionRunRepository,
    private val stepRepo: IngestionStepRepository,
    private val errorRepo: IngestionErrorRepository,
    private val instrumentRepo: InstrumentRepository,
    private val rateLimiter: EodhdRateLimiter
) {

    @PostMapping("/exchanges")
    fun syncExchanges(): ResponseEntity<Map<String, String>> {
        runBlocking { orchestrator.runExchangeSync("api:/admin/ingestion/exchanges") }
        return ResponseEntity.ok(mapOf("status" to "completed"))
    }

    @PostMapping("/run")
    fun triggerFullIngestion(): ResponseEntity<Map<String, String>> {
        rateLimiter.resetDailyQuota()
        runBlocking { orchestrator.runFullIngestion("api:/admin/ingestion/run") }
        return ResponseEntity.ok(mapOf("status" to "completed"))
    }

    @GetMapping("/runs")
    fun listRuns(@RequestParam(defaultValue = "10") limit: Int): ResponseEntity<Any> {
        val runs = runRepo.findAllByOrderByStartedAtDesc(PageRequest.of(0, limit))
        return ResponseEntity.ok(runs.map { run ->
            mapOf(
                "id" to run.id,
                "runType" to run.runType,
                "status" to run.status,
                "startedAt" to run.startedAt,
                "completedAt" to run.completedAt,
                "triggerSource" to run.triggerSource
            )
        })
    }

    @GetMapping("/runs/{id}/steps")
    fun getRunSteps(@PathVariable id: Long): ResponseEntity<Any> {
        val steps = stepRepo.findByRunId(id)
        return ResponseEntity.ok(steps.map { step ->
            mapOf(
                "id" to step.id,
                "stepName" to step.stepName,
                "status" to step.status,
                "recordsProcessed" to step.recordsProcessed,
                "recordsCreated" to step.recordsCreated,
                "recordsUpdated" to step.recordsUpdated,
                "recordsFailed" to step.recordsFailed,
                "startedAt" to step.startedAt,
                "completedAt" to step.completedAt
            )
        })
    }

    @GetMapping("/runs/{id}/errors")
    fun getRunErrors(@PathVariable id: Long): ResponseEntity<Any> {
        val errors = errorRepo.findByStepRunIdOrderByCreatedAtDesc(id, PageRequest.of(0, 100))
        return ResponseEntity.ok(errors.map { error ->
            mapOf(
                "id" to error.id,
                "errorType" to error.errorType,
                "errorCode" to error.errorCode,
                "errorMessage" to error.errorMessage,
                "createdAt" to error.createdAt
            )
        })
    }

    @GetMapping("/stats")
    fun getStats(): ResponseEntity<Map<String, Any>> {
        val totalInstruments = instrumentRepo.count()
        val remaining = rateLimiter.remainingDailyQuota()
        return ResponseEntity.ok(mapOf(
            "totalInstruments" to totalInstruments,
            "remainingDailyQuota" to remaining
        ))
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add ingestion-service/src/main/kotlin/com/portfolio/ingestion/scheduler/ ingestion-service/src/main/kotlin/com/portfolio/ingestion/controller/
git commit -m "feat(ingestion): add scheduler and admin REST controller"
```

---

## Task 8: Integration Verification

- [ ] **Step 1: Build the full stack**

```bash
docker compose build ingestion-service
docker compose up -d postgres redis
docker compose up ingestion-service
```

Verify in logs:
- Flyway migration `V1__initial_schema.sql` applies successfully
- Spring Boot starts on port 8081
- Health endpoint responds: `curl http://localhost:8081/actuator/health`

- [ ] **Step 2: Test exchange sync**

```bash
curl -X POST http://localhost:8081/admin/ingestion/exchanges
```

Verify:
- Returns `{"status": "completed"}`
- Check DB: `SELECT COUNT(*) FROM ingestion.exchanges` returns ~60 exchanges

- [ ] **Step 3: Test universe sync**

```bash
curl -X POST http://localhost:8081/admin/ingestion/run
```

Verify:
- Returns `{"status": "completed"}`
- Check DB: `SELECT COUNT(*) FROM ingestion.instruments` shows instruments
- Check DB: `SELECT COUNT(*) FROM ingestion.instrument_exchanges` shows links
- Check runs: `curl http://localhost:8081/admin/ingestion/runs`

- [ ] **Step 4: Commit final verification**

```bash
git add -A
git commit -m "feat(ingestion): complete ingestion-service module with EODHD provider, pipeline, and admin APIs"
```
