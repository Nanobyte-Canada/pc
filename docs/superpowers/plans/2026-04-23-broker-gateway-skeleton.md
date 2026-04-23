# Broker Gateway Skeleton — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Create the `broker-gateway` microservice skeleton — project structure, BrokerAdapter interface, unified DTOs, credential storage, REST API controllers, error handling, health check, Docker Compose integration. No broker adapters yet — this is the foundation they plug into.

**Architecture:** New Spring Boot 3.3.5 / Kotlin 2.0.21 microservice at `backend/broker-gateway/` on port 8084. Uses its own Flyway schema `broker_gateway`. Follows the exact build/Docker/config patterns from `market-data` and `strategy` services. The `BrokerAdapter` interface defines the contract that IBKR, Questrade, and Wealthsimple adapters will implement in subsequent plans.

**Tech Stack:** Spring Boot 3.3.5, Kotlin 2.0.21, JDK 21, PostgreSQL (Flyway), Redis, Spring Data JPA, Spring WebFlux (WebClient for future HTTP brokers), MockK for testing.

**Spec:** `docs/superpowers/specs/2026-04-23-broker-gateway-design.md`

---

## File Structure

### New files to create

```
backend/broker-gateway/
  settings.gradle.kts
  build.gradle.kts
  Dockerfile
  src/main/kotlin/com/portfolio/brokergateway/
    BrokerGatewayApplication.kt
    adapter/
      BrokerAdapter.kt
      BrokerType.kt
      BrokerCapabilities.kt
      BrokerCredentials.kt
      dto/
        UnifiedAccount.kt
        UnifiedBalance.kt
        UnifiedPosition.kt
        UnifiedActivity.kt
        UnifiedOrder.kt
        OrderRequest.kt
    api/
      controller/
        ConnectionController.kt
        DataController.kt
        OrderController.kt
        HealthController.kt
      dto/
        ApiDtos.kt
    config/
      AppConfig.kt
      AdapterRegistry.kt
      GatewayProperties.kt
    credential/
      CredentialEntity.kt
      CredentialRepository.kt
      CredentialService.kt
      TokenEncryptionService.kt
    exception/
      Exceptions.kt
      GlobalExceptionHandler.kt
  src/main/resources/
    application.yml
    db/migration/
      V1__broker_gateway_schema.sql
  src/test/kotlin/com/portfolio/brokergateway/
    credential/
      TokenEncryptionServiceTest.kt
      CredentialServiceTest.kt
    config/
      AdapterRegistryTest.kt
    api/controller/
      HealthControllerTest.kt
      ConnectionControllerTest.kt
```

### Files to modify

```
docker-compose.yml  — add broker-gateway-service definition
config/.env.example — add new environment variables
```

---

## Task 1: Project Build Files

**Files:**
- Create: `backend/broker-gateway/settings.gradle.kts`
- Create: `backend/broker-gateway/build.gradle.kts`

- [ ] **Step 1: Create settings.gradle.kts**

```kotlin
// backend/broker-gateway/settings.gradle.kts
rootProject.name = "broker-gateway-service"

includeBuild("../common")
```

- [ ] **Step 2: Create build.gradle.kts**

```kotlin
// backend/broker-gateway/build.gradle.kts
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
    implementation("com.portfolio:common")

    implementation("org.springframework.boot:spring-boot-starter-web")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-actuator")
    implementation("org.springframework.boot:spring-boot-starter-validation")

    implementation("com.fasterxml.jackson.module:jackson-module-kotlin")
    implementation("org.jetbrains.kotlin:kotlin-reflect")

    implementation("org.flywaydb:flyway-core")
    implementation("org.flywaydb:flyway-database-postgresql")
    runtimeOnly("org.postgresql:postgresql")

    implementation("io.micrometer:micrometer-registry-prometheus")
    implementation("net.logstash.logback:logstash-logback-encoder:7.4")

    testImplementation("org.springframework.boot:spring-boot-starter-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:postgresql")
    testImplementation("org.testcontainers:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testImplementation("io.mockk:mockk:1.13.9")
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

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/settings.gradle.kts backend/broker-gateway/build.gradle.kts
git commit -m "feat(broker-gateway): add Gradle build files"
```

---

## Task 2: Application Class and Configuration

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/BrokerGatewayApplication.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/config/GatewayProperties.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/config/AppConfig.kt`
- Create: `backend/broker-gateway/src/main/resources/application.yml`

- [ ] **Step 1: Create the application class**

```kotlin
// BrokerGatewayApplication.kt
package com.portfolio.brokergateway

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.context.properties.ConfigurationPropertiesScan
import org.springframework.boot.runApplication
import org.springframework.scheduling.annotation.EnableScheduling

@SpringBootApplication
@EnableScheduling
@ConfigurationPropertiesScan
class BrokerGatewayApplication

fun main(args: Array<String>) {
    runApplication<BrokerGatewayApplication>(*args)
}
```

- [ ] **Step 2: Create GatewayProperties**

```kotlin
// config/GatewayProperties.kt
package com.portfolio.brokergateway.config

import org.springframework.boot.context.properties.ConfigurationProperties

@ConfigurationProperties(prefix = "broker-gateway")
data class GatewayProperties(
    val encryption: EncryptionProperties = EncryptionProperties(),
    val serviceAuth: ServiceAuthProperties = ServiceAuthProperties()
)

data class EncryptionProperties(
    val secretKey: String = ""
)

data class ServiceAuthProperties(
    val apiKey: String = "dev-gateway-key"
)
```

- [ ] **Step 3: Create AppConfig**

```kotlin
// config/AppConfig.kt
package com.portfolio.brokergateway.config

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import org.springframework.context.annotation.Bean
import org.springframework.context.annotation.Configuration
import org.springframework.data.redis.connection.RedisConnectionFactory
import org.springframework.data.redis.core.RedisTemplate
import org.springframework.data.redis.serializer.StringRedisSerializer

@Configuration
class AppConfig {

    @Bean
    fun objectMapper(): ObjectMapper = jacksonObjectMapper().findAndRegisterModules()

    @Bean
    fun redisTemplate(connectionFactory: RedisConnectionFactory): RedisTemplate<String, String> {
        val template = RedisTemplate<String, String>()
        template.connectionFactory = connectionFactory
        template.keySerializer = StringRedisSerializer()
        template.valueSerializer = StringRedisSerializer()
        template.hashKeySerializer = StringRedisSerializer()
        template.hashValueSerializer = StringRedisSerializer()
        return template
    }
}
```

- [ ] **Step 4: Create application.yml**

```yaml
# src/main/resources/application.yml
spring:
  application:
    name: broker-gateway-service
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/portfolio}
    username: ${DATABASE_USERNAME:portfolio}
    password: ${DATABASE_PASSWORD:portfolio}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
      initialization-fail-timeout: -1
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        dialect: org.hibernate.dialect.PostgreSQLDialect
        default_schema: broker_gateway
  flyway:
    enabled: true
    schemas: broker_gateway
    default-schema: broker_gateway
    locations: classpath:db/migration
    baseline-on-migrate: true
  data:
    redis:
      host: ${REDIS_HOST:localhost}
      port: ${REDIS_PORT:6379}
  mvc:
    problemdetails:
      enabled: true

server:
  port: ${PORT:8084}

management:
  endpoints:
    web:
      exposure:
        include: health,info,metrics
  endpoint:
    health:
      show-details: when-authorized
      show-components: when-authorized

broker-gateway:
  encryption:
    secret-key: ${BROKER_ENCRYPTION_KEY:}
  service-auth:
    api-key: ${GATEWAY_API_KEY:dev-gateway-key}
```

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/
git commit -m "feat(broker-gateway): add application class and configuration"
```

---

## Task 3: Flyway Migration

**Files:**
- Create: `backend/broker-gateway/src/main/resources/db/migration/V1__broker_gateway_schema.sql`

- [ ] **Step 1: Create the migration**

```sql
-- V1__broker_gateway_schema.sql
CREATE SCHEMA IF NOT EXISTS broker_gateway;

CREATE TABLE broker_gateway.connections (
    id              VARCHAR(36) PRIMARY KEY DEFAULT gen_random_uuid()::text,
    user_id         BIGINT NOT NULL,
    broker_type     VARCHAR(20) NOT NULL,
    status          VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    credentials_encrypted TEXT NOT NULL,
    accounts_json   JSONB,
    last_validated_at TIMESTAMPTZ,
    last_refreshed_at TIMESTAMPTZ,
    error_message   TEXT,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT chk_broker_type CHECK (broker_type IN ('IBKR', 'QUESTRADE', 'WEALTHSIMPLE')),
    CONSTRAINT chk_status CHECK (status IN ('ACTIVE', 'ERROR', 'EXPIRED', 'DISCONNECTED'))
);

CREATE INDEX idx_gw_conn_user ON broker_gateway.connections(user_id);
CREATE INDEX idx_gw_conn_user_type ON broker_gateway.connections(user_id, broker_type);
CREATE INDEX idx_gw_conn_status ON broker_gateway.connections(status);
```

- [ ] **Step 2: Commit**

```bash
git add backend/broker-gateway/src/main/resources/db/migration/
git commit -m "feat(broker-gateway): add Flyway schema migration"
```

---

## Task 4: BrokerAdapter Interface, Types, and Unified DTOs

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/BrokerType.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/BrokerCredentials.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/BrokerCapabilities.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/BrokerAdapter.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/UnifiedAccount.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/UnifiedBalance.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/UnifiedPosition.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/UnifiedActivity.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/UnifiedOrder.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/dto/OrderRequest.kt`

- [ ] **Step 1: Create BrokerType**

```kotlin
// adapter/BrokerType.kt
package com.portfolio.brokergateway.adapter

enum class BrokerType {
    IBKR,
    QUESTRADE,
    WEALTHSIMPLE
}
```

- [ ] **Step 2: Create BrokerCredentials sealed hierarchy**

```kotlin
// adapter/BrokerCredentials.kt
package com.portfolio.brokergateway.adapter

import com.fasterxml.jackson.annotation.JsonSubTypes
import com.fasterxml.jackson.annotation.JsonTypeInfo

@JsonTypeInfo(use = JsonTypeInfo.Id.NAME, property = "brokerType")
@JsonSubTypes(
    JsonSubTypes.Type(value = BrokerCredentials.IbkrCredentials::class, name = "IBKR"),
    JsonSubTypes.Type(value = BrokerCredentials.QuestradeCredentials::class, name = "QUESTRADE"),
    JsonSubTypes.Type(value = BrokerCredentials.WealthsimpleCredentials::class, name = "WEALTHSIMPLE")
)
sealed class BrokerCredentials {
    abstract val brokerType: BrokerType

    data class IbkrCredentials(
        val host: String,
        val port: Int,
        val clientId: Int
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.IBKR
    }

    data class QuestradeCredentials(
        val accessToken: String,
        val refreshToken: String,
        val apiServerUrl: String,
        val expiresAtEpochSeconds: Long
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.QUESTRADE
    }

    data class WealthsimpleCredentials(
        val accessToken: String,
        val refreshToken: String,
        val expiresAtEpochSeconds: Long,
        val email: String? = null,
        val passwordEncrypted: String? = null
    ) : BrokerCredentials() {
        override val brokerType = BrokerType.WEALTHSIMPLE
    }
}
```

- [ ] **Step 3: Create BrokerCapabilities**

```kotlin
// adapter/BrokerCapabilities.kt
package com.portfolio.brokergateway.adapter

data class BrokerCapabilities(
    val brokerType: BrokerType,
    val supportsOrders: Boolean,
    val supportedOrderTypes: List<OrderType>,
    val supportsOptionPositions: Boolean,
    val supportsFractionalShares: Boolean,
    val supportsRealTimeData: Boolean,
    val supportsHistoricalActivities: Boolean,
    val activityHistoryDepth: String?,
    val orderRateLimit: String?,
    val isOfficialApi: Boolean,
    val notes: String?
)

enum class OrderType {
    MARKET, LIMIT, STOP, STOP_LIMIT
}

enum class OrderAction {
    BUY, SELL
}

enum class TimeInForce {
    DAY, GTC, IOC, FOK
}

enum class OrderStatus {
    PENDING, SUBMITTED, FILLED, PARTIALLY_FILLED, CANCELLED, REJECTED, FAILED
}

enum class ActivityType {
    BUY, SELL, DIVIDEND, TRANSFER_IN, TRANSFER_OUT,
    FEE, COMMISSION, INTEREST,
    OPTION_EXPIRATION, OPTION_ASSIGNMENT, OPTION_EXERCISE,
    STOCK_SPLIT, CORPORATE_ACTION, OTHER
}

enum class InstrumentType {
    STOCK, ETF, MUTUAL_FUND, OPTION, BOND, CASH, CRYPTO, OTHER
}

enum class AccountType {
    CASH, MARGIN, TFSA, RRSP, FHSA, RESP, LIRA, LIF, RIF, CRYPTO, OTHER
}
```

- [ ] **Step 4: Create unified DTOs**

```kotlin
// adapter/dto/UnifiedAccount.kt
package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.AccountType
import com.portfolio.brokergateway.adapter.BrokerType

data class UnifiedAccount(
    val accountId: String,
    val accountNumber: String?,
    val accountName: String?,
    val accountType: AccountType,
    val currency: String?,
    val brokerType: BrokerType,
    val status: String?
)
```

```kotlin
// adapter/dto/UnifiedBalance.kt
package com.portfolio.brokergateway.adapter.dto

import java.math.BigDecimal

data class UnifiedBalance(
    val accountId: String,
    val totalEquity: BigDecimal?,
    val totalValue: BigDecimal?,
    val cashBalances: List<CashBalance>,
    val buyingPower: BigDecimal?,
    val currency: String
)

data class CashBalance(
    val currency: String,
    val amount: BigDecimal
)
```

```kotlin
// adapter/dto/UnifiedPosition.kt
package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.InstrumentType
import java.math.BigDecimal
import java.time.LocalDate

data class UnifiedPosition(
    val symbol: String,
    val symbolId: String?,
    val securityName: String?,
    val instrumentType: InstrumentType,
    val quantity: BigDecimal,
    val averageCost: BigDecimal?,
    val currentPrice: BigDecimal?,
    val currentValue: BigDecimal?,
    val totalPnl: BigDecimal?,
    val totalPnlPercent: BigDecimal?,
    val currency: String,
    val strikePrice: BigDecimal? = null,
    val expirationDate: LocalDate? = null,
    val optionType: String? = null,
    val underlyingSymbol: String? = null
)
```

```kotlin
// adapter/dto/UnifiedActivity.kt
package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.ActivityType
import java.math.BigDecimal
import java.time.LocalDate

data class UnifiedActivity(
    val externalId: String?,
    val type: ActivityType,
    val symbol: String?,
    val description: String?,
    val quantity: BigDecimal?,
    val price: BigDecimal?,
    val amount: BigDecimal,
    val fee: BigDecimal?,
    val currency: String,
    val tradeDate: LocalDate,
    val settlementDate: LocalDate?,
    val optionType: String?
)
```

```kotlin
// adapter/dto/UnifiedOrder.kt
package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderStatus
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal
import java.time.OffsetDateTime

data class UnifiedOrder(
    val brokerOrderId: String,
    val symbol: String,
    val action: OrderAction,
    val orderType: OrderType,
    val timeInForce: TimeInForce,
    val totalQuantity: BigDecimal,
    val filledQuantity: BigDecimal?,
    val executionPrice: BigDecimal?,
    val limitPrice: BigDecimal?,
    val stopPrice: BigDecimal?,
    val status: OrderStatus,
    val currency: String?,
    val submittedAt: OffsetDateTime?,
    val filledAt: OffsetDateTime?
)
```

```kotlin
// adapter/dto/OrderRequest.kt
package com.portfolio.brokergateway.adapter.dto

import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal

data class OrderRequest(
    val symbol: String,
    val action: OrderAction,
    val quantity: BigDecimal,
    val orderType: OrderType,
    val limitPrice: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
    val currency: String? = null
)

data class OrderResult(
    val brokerOrderId: String?,
    val status: com.portfolio.brokergateway.adapter.OrderStatus,
    val message: String? = null
)

data class CancelResult(
    val success: Boolean,
    val message: String? = null
)

data class ConnectionValidationResult(
    val connected: Boolean,
    val message: String? = null,
    val needsReauth: Boolean = false
)
```

- [ ] **Step 5: Create BrokerAdapter interface**

```kotlin
// adapter/BrokerAdapter.kt
package com.portfolio.brokergateway.adapter

import com.portfolio.brokergateway.adapter.dto.*
import java.time.LocalDate

interface BrokerAdapter {
    val brokerType: BrokerType

    fun validateConnection(credentials: BrokerCredentials): ConnectionValidationResult
    fun refreshAuth(credentials: BrokerCredentials): BrokerCredentials
    fun listAccounts(credentials: BrokerCredentials): List<UnifiedAccount>
    fun getBalances(credentials: BrokerCredentials, accountId: String): UnifiedBalance
    fun getPositions(credentials: BrokerCredentials, accountId: String): List<UnifiedPosition>
    fun getActivities(
        credentials: BrokerCredentials,
        accountId: String,
        startDate: LocalDate?,
        endDate: LocalDate?
    ): List<UnifiedActivity>
    fun getOrders(
        credentials: BrokerCredentials,
        accountId: String,
        status: OrderStatusFilter? = null
    ): List<UnifiedOrder>
    fun placeOrder(
        credentials: BrokerCredentials,
        accountId: String,
        request: OrderRequest
    ): OrderResult
    fun cancelOrder(
        credentials: BrokerCredentials,
        accountId: String,
        brokerOrderId: String
    ): CancelResult
    fun capabilities(): BrokerCapabilities
}

data class OrderStatusFilter(
    val statuses: List<OrderStatus>? = null
)
```

- [ ] **Step 6: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/adapter/
git commit -m "feat(broker-gateway): add BrokerAdapter interface and unified DTOs"
```

---

## Task 5: Exception Hierarchy and Global Error Handler

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/exception/Exceptions.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/exception/GlobalExceptionHandler.kt`

- [ ] **Step 1: Create exception hierarchy**

```kotlin
// exception/Exceptions.kt
package com.portfolio.brokergateway.exception

import com.portfolio.brokergateway.adapter.BrokerType

sealed class BrokerGatewayException(
    val errorCode: String,
    override val message: String,
    override val cause: Throwable? = null
) : RuntimeException(message, cause)

class BrokerAuthenticationException(
    message: String,
    val brokerType: BrokerType,
    val needsReauth: Boolean = true,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_AUTH_FAILED", message, cause)

class BrokerConnectionException(
    message: String,
    val brokerType: BrokerType,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_CONNECTION_FAILED", message, cause)

class BrokerRateLimitException(
    message: String,
    val brokerType: BrokerType,
    val retryAfterSeconds: Int? = null
) : BrokerGatewayException("BROKER_RATE_LIMITED", message)

class BrokerOrderRejectedException(
    message: String,
    val brokerType: BrokerType,
    val brokerRejectionReason: String? = null
) : BrokerGatewayException("ORDER_REJECTED", message)

class BrokerUnsupportedOperationException(
    message: String,
    val brokerType: BrokerType
) : BrokerGatewayException("UNSUPPORTED_OPERATION", message)

class ConnectionNotFoundException(
    connectionId: String
) : BrokerGatewayException("CONNECTION_NOT_FOUND", "Connection not found: $connectionId")

class BrokerDataException(
    message: String,
    val brokerType: BrokerType,
    cause: Throwable? = null
) : BrokerGatewayException("BROKER_DATA_ERROR", message, cause)
```

- [ ] **Step 2: Create GlobalExceptionHandler**

```kotlin
// exception/GlobalExceptionHandler.kt
package com.portfolio.brokergateway.exception

import jakarta.servlet.http.HttpServletRequest
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ProblemDetail
import org.springframework.web.bind.annotation.ExceptionHandler
import org.springframework.web.bind.annotation.RestControllerAdvice
import java.net.URI
import java.time.OffsetDateTime

@RestControllerAdvice
class GlobalExceptionHandler {

    private val log = LoggerFactory.getLogger(javaClass)

    @ExceptionHandler(BrokerGatewayException::class)
    fun handleGatewayException(ex: BrokerGatewayException, request: HttpServletRequest): ProblemDetail {
        val status = when (ex) {
            is ConnectionNotFoundException -> HttpStatus.NOT_FOUND
            is BrokerAuthenticationException -> HttpStatus.UNAUTHORIZED
            is BrokerConnectionException -> HttpStatus.BAD_GATEWAY
            is BrokerRateLimitException -> HttpStatus.TOO_MANY_REQUESTS
            is BrokerOrderRejectedException -> HttpStatus.UNPROCESSABLE_ENTITY
            is BrokerUnsupportedOperationException -> HttpStatus.NOT_IMPLEMENTED
            is BrokerDataException -> HttpStatus.BAD_GATEWAY
        }

        log.warn("Gateway error [{}] {}: {} at {}", status.value(), ex.errorCode, ex.message, request.requestURI)

        return ProblemDetail.forStatusAndDetail(status, ex.message).apply {
            title = status.reasonPhrase
            instance = URI.create(request.requestURI)
            setProperty("code", ex.errorCode)
            setProperty("timestamp", OffsetDateTime.now())
            if (ex is BrokerRateLimitException && ex.retryAfterSeconds != null) {
                setProperty("retryAfterSeconds", ex.retryAfterSeconds)
            }
        }
    }

    @ExceptionHandler(IllegalArgumentException::class)
    fun handleIllegalArgument(ex: IllegalArgumentException, request: HttpServletRequest): ProblemDetail {
        log.warn("Bad request: {} at {}", ex.message, request.requestURI)
        return ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.message ?: "Invalid request").apply {
            title = "Bad Request"
            instance = URI.create(request.requestURI)
            setProperty("code", "BAD_REQUEST")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }

    @ExceptionHandler(Exception::class)
    fun handleGeneric(ex: Exception, request: HttpServletRequest): ProblemDetail {
        log.error("Unhandled exception at {}: {}", request.requestURI, ex.message, ex)
        return ProblemDetail.forStatusAndDetail(HttpStatus.INTERNAL_SERVER_ERROR, "An unexpected error occurred").apply {
            title = "Internal Server Error"
            instance = URI.create(request.requestURI)
            setProperty("code", "INTERNAL_ERROR")
            setProperty("timestamp", OffsetDateTime.now())
        }
    }
}
```

- [ ] **Step 3: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/exception/
git commit -m "feat(broker-gateway): add exception hierarchy and global error handler"
```

---

## Task 6: Credential Storage — Encryption, Entity, Repository, Service

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/credential/TokenEncryptionService.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/credential/CredentialEntity.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/credential/CredentialRepository.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/credential/CredentialService.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/credential/TokenEncryptionServiceTest.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/credential/CredentialServiceTest.kt`

- [ ] **Step 1: Write TokenEncryptionService test**

```kotlin
// test: credential/TokenEncryptionServiceTest.kt
package com.portfolio.brokergateway.credential

import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import kotlin.test.assertEquals
import kotlin.test.assertNotEquals
import kotlin.test.assertTrue

class TokenEncryptionServiceTest {

    private val service = TokenEncryptionService("")

    @Test
    fun `encrypt and decrypt round-trips correctly`() {
        val plaintext = """{"brokerType":"QUESTRADE","accessToken":"abc123"}"""
        val encrypted = service.encrypt(plaintext)
        val decrypted = service.decrypt(encrypted)
        assertEquals(plaintext, decrypted)
    }

    @Test
    fun `encrypted output differs from plaintext`() {
        val plaintext = "secret-token"
        val encrypted = service.encrypt(plaintext)
        assertNotEquals(plaintext, encrypted)
    }

    @Test
    fun `same plaintext produces different ciphertext each time`() {
        val plaintext = "secret-token"
        val encrypted1 = service.encrypt(plaintext)
        val encrypted2 = service.encrypt(plaintext)
        assertNotEquals(encrypted1, encrypted2)
    }

    @Test
    fun `encrypt rejects blank input`() {
        assertThrows<IllegalArgumentException> { service.encrypt("") }
        assertThrows<IllegalArgumentException> { service.encrypt("   ") }
    }

    @Test
    fun `decrypt rejects blank input`() {
        assertThrows<IllegalArgumentException> { service.decrypt("") }
    }

    @Test
    fun `validateConfiguration returns true`() {
        assertTrue(service.validateConfiguration())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.credential.TokenEncryptionServiceTest" -i`
Expected: FAIL — class not found

- [ ] **Step 3: Create TokenEncryptionService**

```kotlin
// credential/TokenEncryptionService.kt
package com.portfolio.brokergateway.credential

import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.nio.ByteBuffer
import java.security.SecureRandom
import java.util.Base64
import javax.crypto.Cipher
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.SecretKeySpec

@Service
class TokenEncryptionService(
    private val secretKeyBase64: String
) {
    private val log = LoggerFactory.getLogger(javaClass)
    private val secureRandom = SecureRandom()

    companion object {
        private const val ALGORITHM = "AES/GCM/NoPadding"
        private const val KEY_ALGORITHM = "AES"
        private const val GCM_IV_LENGTH = 12
        private const val GCM_TAG_LENGTH = 128
        private const val KEY_LENGTH = 32
    }

    constructor(properties: com.portfolio.brokergateway.config.GatewayProperties) :
        this(properties.encryption.secretKey)

    private val secretKey: SecretKey by lazy {
        if (secretKeyBase64.isBlank()) {
            log.warn("No encryption key configured, generating ephemeral key")
            generateKey()
        } else {
            val keyBytes = Base64.getDecoder().decode(secretKeyBase64)
            require(keyBytes.size == KEY_LENGTH) { "Encryption key must be $KEY_LENGTH bytes" }
            SecretKeySpec(keyBytes, KEY_ALGORITHM)
        }
    }

    fun encrypt(plaintext: String): String {
        require(plaintext.isNotBlank()) { "Cannot encrypt empty value" }

        val iv = ByteArray(GCM_IV_LENGTH)
        secureRandom.nextBytes(iv)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.ENCRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        val ciphertext = cipher.doFinal(plaintext.toByteArray(Charsets.UTF_8))

        val combined = ByteBuffer.allocate(iv.size + ciphertext.size)
            .put(iv)
            .put(ciphertext)
            .array()

        return Base64.getEncoder().encodeToString(combined)
    }

    fun decrypt(encryptedToken: String): String {
        require(encryptedToken.isNotBlank()) { "Cannot decrypt empty value" }

        val combined = Base64.getDecoder().decode(encryptedToken)
        require(combined.size > GCM_IV_LENGTH) { "Invalid encrypted value: too short" }

        val buffer = ByteBuffer.wrap(combined)
        val iv = ByteArray(GCM_IV_LENGTH)
        buffer.get(iv)
        val ciphertext = ByteArray(buffer.remaining())
        buffer.get(ciphertext)

        val cipher = Cipher.getInstance(ALGORITHM)
        cipher.init(Cipher.DECRYPT_MODE, secretKey, GCMParameterSpec(GCM_TAG_LENGTH, iv))
        return String(cipher.doFinal(ciphertext), Charsets.UTF_8)
    }

    fun validateConfiguration(): Boolean {
        return try {
            val test = "validation-test"
            decrypt(encrypt(test)) == test
        } catch (e: Exception) {
            log.error("Encryption validation failed: ${e.message}")
            false
        }
    }

    private fun generateKey(): SecretKey {
        val keyBytes = ByteArray(KEY_LENGTH)
        secureRandom.nextBytes(keyBytes)
        return SecretKeySpec(keyBytes, KEY_ALGORITHM)
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.credential.TokenEncryptionServiceTest" -i`
Expected: PASS — all 6 tests green

- [ ] **Step 5: Create CredentialEntity**

```kotlin
// credential/CredentialEntity.kt
package com.portfolio.brokergateway.credential

import jakarta.persistence.*
import java.time.OffsetDateTime

@Entity
@Table(name = "connections", schema = "broker_gateway")
class GatewayConnection(
    @Id
    @Column(length = 36)
    val id: String = java.util.UUID.randomUUID().toString(),

    @Column(name = "user_id", nullable = false)
    val userId: Long,

    @Column(name = "broker_type", nullable = false, length = 20)
    val brokerType: String,

    @Column(nullable = false, length = 20)
    var status: String = "ACTIVE",

    @Column(name = "credentials_encrypted", nullable = false, columnDefinition = "TEXT")
    var credentialsEncrypted: String,

    @Column(name = "accounts_json", columnDefinition = "jsonb")
    var accountsJson: String? = null,

    @Column(name = "last_validated_at")
    var lastValidatedAt: OffsetDateTime? = null,

    @Column(name = "last_refreshed_at")
    var lastRefreshedAt: OffsetDateTime? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
```

- [ ] **Step 6: Create CredentialRepository**

```kotlin
// credential/CredentialRepository.kt
package com.portfolio.brokergateway.credential

import org.springframework.data.jpa.repository.JpaRepository

interface GatewayConnectionRepository : JpaRepository<GatewayConnection, String> {
    fun findByUserId(userId: Long): List<GatewayConnection>
    fun findByUserIdAndBrokerType(userId: Long, brokerType: String): List<GatewayConnection>
    fun findByStatus(status: String): List<GatewayConnection>
}
```

- [ ] **Step 7: Write CredentialService test**

```kotlin
// test: credential/CredentialServiceTest.kt
package com.portfolio.brokergateway.credential

import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.ConnectionNotFoundException
import io.mockk.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.util.*
import kotlin.test.assertEquals

class CredentialServiceTest {

    private val repository = mockk<GatewayConnectionRepository>()
    private val encryptionService = TokenEncryptionService("")
    private val objectMapper = jacksonObjectMapper().findAndRegisterModules()
    private val service = CredentialService(repository, encryptionService, objectMapper)

    @BeforeEach
    fun setup() {
        clearAllMocks()
    }

    @Test
    fun `createConnection stores encrypted credentials and returns id`() {
        val creds = BrokerCredentials.QuestradeCredentials(
            accessToken = "token", refreshToken = "refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 9999999999
        )

        every { repository.save(any()) } answers { firstArg() }

        val id = service.createConnection(userId = 1L, credentials = creds)

        verify { repository.save(match { it.userId == 1L && it.brokerType == "QUESTRADE" }) }
        assert(id.isNotBlank())
    }

    @Test
    fun `getCredentials decrypts and returns correct subtype`() {
        val creds = BrokerCredentials.IbkrCredentials(host = "127.0.0.1", port = 4002, clientId = 2)
        val json = objectMapper.writeValueAsString(creds)
        val encrypted = encryptionService.encrypt(json)
        val entity = GatewayConnection(
            id = "conn-1", userId = 1L, brokerType = "IBKR", credentialsEncrypted = encrypted
        )

        every { repository.findById("conn-1") } returns Optional.of(entity)

        val result = service.getCredentials("conn-1")
        assert(result is BrokerCredentials.IbkrCredentials)
        assertEquals("127.0.0.1", (result as BrokerCredentials.IbkrCredentials).host)
    }

    @Test
    fun `getCredentials throws ConnectionNotFoundException for unknown id`() {
        every { repository.findById("unknown") } returns Optional.empty()
        assertThrows<ConnectionNotFoundException> { service.getCredentials("unknown") }
    }

    @Test
    fun `getConnection throws ConnectionNotFoundException for unknown id`() {
        every { repository.findById("unknown") } returns Optional.empty()
        assertThrows<ConnectionNotFoundException> { service.getConnection("unknown") }
    }

    @Test
    fun `listConnections returns connections for user`() {
        val entity = GatewayConnection(
            id = "c1", userId = 5L, brokerType = "QUESTRADE", credentialsEncrypted = "x"
        )
        every { repository.findByUserId(5L) } returns listOf(entity)

        val result = service.listConnections(5L)
        assertEquals(1, result.size)
        assertEquals("c1", result[0].id)
    }

    @Test
    fun `deleteConnection removes from repository`() {
        val entity = GatewayConnection(
            id = "c1", userId = 5L, brokerType = "QUESTRADE", credentialsEncrypted = "x"
        )
        every { repository.findById("c1") } returns Optional.of(entity)
        every { repository.delete(entity) } just Runs

        service.deleteConnection("c1")
        verify { repository.delete(entity) }
    }

    @Test
    fun `updateCredentials re-encrypts and persists`() {
        val oldCreds = BrokerCredentials.QuestradeCredentials(
            accessToken = "old", refreshToken = "old-refresh",
            apiServerUrl = "https://api05.iq.questrade.com/", expiresAtEpochSeconds = 1000
        )
        val json = objectMapper.writeValueAsString(oldCreds)
        val encrypted = encryptionService.encrypt(json)
        val entity = GatewayConnection(
            id = "c1", userId = 1L, brokerType = "QUESTRADE", credentialsEncrypted = encrypted
        )

        every { repository.findById("c1") } returns Optional.of(entity)
        every { repository.save(any()) } answers { firstArg() }

        val newCreds = BrokerCredentials.QuestradeCredentials(
            accessToken = "new", refreshToken = "new-refresh",
            apiServerUrl = "https://api06.iq.questrade.com/", expiresAtEpochSeconds = 2000
        )

        service.updateCredentials("c1", newCreds)

        verify {
            repository.save(match {
                it.id == "c1" && it.credentialsEncrypted != encrypted
            })
        }
    }
}
```

- [ ] **Step 8: Create CredentialService**

```kotlin
// credential/CredentialService.kt
package com.portfolio.brokergateway.credential

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.exception.ConnectionNotFoundException
import org.slf4j.LoggerFactory
import org.springframework.stereotype.Service
import java.time.OffsetDateTime

@Service
class CredentialService(
    private val repository: GatewayConnectionRepository,
    private val encryptionService: TokenEncryptionService,
    private val objectMapper: ObjectMapper
) {
    private val log = LoggerFactory.getLogger(javaClass)

    fun createConnection(userId: Long, credentials: BrokerCredentials): String {
        val json = objectMapper.writeValueAsString(credentials)
        val encrypted = encryptionService.encrypt(json)

        val entity = GatewayConnection(
            userId = userId,
            brokerType = credentials.brokerType.name,
            credentialsEncrypted = encrypted
        )

        val saved = repository.save(entity)
        log.info("Created {} connection {} for user {}", credentials.brokerType, saved.id, userId)
        return saved.id
    }

    fun getCredentials(connectionId: String): BrokerCredentials {
        val entity = getConnection(connectionId)
        val json = encryptionService.decrypt(entity.credentialsEncrypted)
        return objectMapper.readValue(json, BrokerCredentials::class.java)
    }

    fun getConnection(connectionId: String): GatewayConnection {
        return repository.findById(connectionId)
            .orElseThrow { ConnectionNotFoundException(connectionId) }
    }

    fun listConnections(userId: Long): List<GatewayConnection> {
        return repository.findByUserId(userId)
    }

    fun updateCredentials(connectionId: String, credentials: BrokerCredentials) {
        val entity = getConnection(connectionId)
        val json = objectMapper.writeValueAsString(credentials)
        entity.credentialsEncrypted = encryptionService.encrypt(json)
        entity.lastRefreshedAt = OffsetDateTime.now()
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
        log.info("Updated credentials for connection {}", connectionId)
    }

    fun updateStatus(connectionId: String, status: String, errorMessage: String? = null) {
        val entity = getConnection(connectionId)
        entity.status = status
        entity.errorMessage = errorMessage
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
    }

    fun updateAccountsJson(connectionId: String, accountsJson: String) {
        val entity = getConnection(connectionId)
        entity.accountsJson = accountsJson
        entity.updatedAt = OffsetDateTime.now()
        repository.save(entity)
    }

    fun deleteConnection(connectionId: String) {
        val entity = getConnection(connectionId)
        repository.delete(entity)
        log.info("Deleted connection {}", connectionId)
    }
}
```

- [ ] **Step 9: Run all credential tests**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.credential.*" -i`
Expected: PASS — all tests green

- [ ] **Step 10: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/credential/ backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/credential/
git commit -m "feat(broker-gateway): add credential storage with AES-256-GCM encryption"
```

---

## Task 7: AdapterRegistry

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/config/AdapterRegistry.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/config/AdapterRegistryTest.kt`

- [ ] **Step 1: Write AdapterRegistry test**

```kotlin
// test: config/AdapterRegistryTest.kt
package com.portfolio.brokergateway.config

import com.portfolio.brokergateway.adapter.*
import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.assertThrows
import java.time.LocalDate
import kotlin.test.assertEquals

class AdapterRegistryTest {

    private fun fakeAdapter(type: BrokerType): BrokerAdapter {
        val adapter = mockk<BrokerAdapter>()
        every { adapter.brokerType } returns type
        return adapter
    }

    @Test
    fun `getAdapter returns correct adapter for registered type`() {
        val ibkr = fakeAdapter(BrokerType.IBKR)
        val registry = AdapterRegistry(listOf(ibkr))
        assertEquals(ibkr, registry.getAdapter(BrokerType.IBKR))
    }

    @Test
    fun `getAdapter throws for unregistered type`() {
        val registry = AdapterRegistry(emptyList())
        assertThrows<BrokerUnsupportedOperationException> {
            registry.getAdapter(BrokerType.QUESTRADE)
        }
    }

    @Test
    fun `getEnabledBrokers returns registered types`() {
        val ibkr = fakeAdapter(BrokerType.IBKR)
        val qt = fakeAdapter(BrokerType.QUESTRADE)
        val registry = AdapterRegistry(listOf(ibkr, qt))
        assertEquals(setOf(BrokerType.IBKR, BrokerType.QUESTRADE), registry.getEnabledBrokers().toSet())
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.config.AdapterRegistryTest" -i`
Expected: FAIL — class not found

- [ ] **Step 3: Create AdapterRegistry**

```kotlin
// config/AdapterRegistry.kt
package com.portfolio.brokergateway.config

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.exception.BrokerUnsupportedOperationException
import org.springframework.stereotype.Component

@Component
class AdapterRegistry(
    adapters: List<BrokerAdapter>
) {
    private val adapterMap: Map<BrokerType, BrokerAdapter> =
        adapters.associateBy { it.brokerType }

    fun getAdapter(brokerType: BrokerType): BrokerAdapter =
        adapterMap[brokerType]
            ?: throw BrokerUnsupportedOperationException(
                "No adapter registered for broker: $brokerType",
                brokerType
            )

    fun getEnabledBrokers(): List<BrokerType> = adapterMap.keys.toList()
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.config.AdapterRegistryTest" -i`
Expected: PASS

- [ ] **Step 5: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/config/AdapterRegistry.kt backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/config/
git commit -m "feat(broker-gateway): add AdapterRegistry for broker routing"
```

---

## Task 8: REST API Controllers

**Files:**
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/dto/ApiDtos.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/HealthController.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/ConnectionController.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/DataController.kt`
- Create: `backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/controller/OrderController.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/api/controller/HealthControllerTest.kt`
- Test: `backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/api/controller/ConnectionControllerTest.kt`

- [ ] **Step 1: Create API DTOs**

```kotlin
// api/dto/ApiDtos.kt
package com.portfolio.brokergateway.api.dto

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.adapter.OrderAction
import com.portfolio.brokergateway.adapter.OrderType
import com.portfolio.brokergateway.adapter.TimeInForce
import java.math.BigDecimal
import java.time.OffsetDateTime

data class CreateConnectionRequest(
    val userId: Long,
    val brokerType: BrokerType,
    val credentials: Map<String, Any>
)

data class ConnectionResponse(
    val connectionId: String,
    val brokerType: BrokerType,
    val status: String,
    val accountsJson: String?,
    val lastValidatedAt: OffsetDateTime?,
    val lastRefreshedAt: OffsetDateTime?,
    val errorMessage: String?,
    val createdAt: OffsetDateTime
)

data class ConnectionListResponse(
    val connections: List<ConnectionResponse>
)

data class PlaceOrderRequest(
    val symbol: String,
    val action: OrderAction,
    val quantity: BigDecimal,
    val orderType: OrderType,
    val limitPrice: BigDecimal? = null,
    val stopPrice: BigDecimal? = null,
    val timeInForce: TimeInForce = TimeInForce.DAY,
    val currency: String? = null
)

data class BrokerHealthResponse(
    val brokerType: BrokerType,
    val enabled: Boolean,
    val status: String
)

data class GatewayHealthResponse(
    val status: String,
    val brokers: List<BrokerHealthResponse>
)
```

- [ ] **Step 2: Create HealthController**

```kotlin
// api/controller/HealthController.kt
package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.api.dto.BrokerHealthResponse
import com.portfolio.brokergateway.api.dto.GatewayHealthResponse
import com.portfolio.brokergateway.config.AdapterRegistry
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController

@RestController
@RequestMapping("/api/v1/gateway/health")
class HealthController(
    private val adapterRegistry: AdapterRegistry
) {
    @GetMapping
    fun health(): ResponseEntity<GatewayHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val brokerStatuses = BrokerType.entries.map { type ->
            BrokerHealthResponse(
                brokerType = type,
                enabled = type in enabledBrokers,
                status = if (type in enabledBrokers) "OK" else "DISABLED"
            )
        }
        return ResponseEntity.ok(GatewayHealthResponse(status = "UP", brokers = brokerStatuses))
    }

    @GetMapping("/{brokerType}")
    fun brokerHealth(@PathVariable brokerType: BrokerType): ResponseEntity<BrokerHealthResponse> {
        val enabledBrokers = adapterRegistry.getEnabledBrokers()
        val enabled = brokerType in enabledBrokers
        return ResponseEntity.ok(
            BrokerHealthResponse(
                brokerType = brokerType,
                enabled = enabled,
                status = if (enabled) "OK" else "DISABLED"
            )
        )
    }
}
```

- [ ] **Step 3: Create ConnectionController**

```kotlin
// api/controller/ConnectionController.kt
package com.portfolio.brokergateway.api.controller

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.brokergateway.adapter.BrokerCredentials
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.api.dto.ConnectionListResponse
import com.portfolio.brokergateway.api.dto.ConnectionResponse
import com.portfolio.brokergateway.api.dto.CreateConnectionRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import com.portfolio.brokergateway.credential.GatewayConnection
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections")
class ConnectionController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry,
    private val objectMapper: ObjectMapper
) {
    @PostMapping
    fun createConnection(@RequestBody request: CreateConnectionRequest): ResponseEntity<ConnectionResponse> {
        val adapter = adapterRegistry.getAdapter(request.brokerType)
        val credentials = parseCredentials(request)
        val connectionId = credentialService.createConnection(request.userId, credentials)
        val entity = credentialService.getConnection(connectionId)

        val validation = adapter.validateConnection(credentials)
        if (!validation.connected) {
            credentialService.updateStatus(connectionId, "ERROR", validation.message)
        } else {
            credentialService.updateStatus(connectionId, "ACTIVE")
            val accounts = adapter.listAccounts(credentials)
            credentialService.updateAccountsJson(connectionId, objectMapper.writeValueAsString(accounts))
        }

        val updated = credentialService.getConnection(connectionId)
        return ResponseEntity.status(HttpStatus.CREATED).body(toResponse(updated))
    }

    @GetMapping
    fun listConnections(@RequestParam userId: Long): ResponseEntity<ConnectionListResponse> {
        val connections = credentialService.listConnections(userId)
        return ResponseEntity.ok(ConnectionListResponse(connections.map { toResponse(it) }))
    }

    @GetMapping("/{connectionId}")
    fun getConnection(@PathVariable connectionId: String): ResponseEntity<ConnectionResponse> {
        val entity = credentialService.getConnection(connectionId)
        return ResponseEntity.ok(toResponse(entity))
    }

    @DeleteMapping("/{connectionId}")
    fun deleteConnection(@PathVariable connectionId: String): ResponseEntity<Void> {
        credentialService.deleteConnection(connectionId)
        return ResponseEntity.noContent().build()
    }

    @PostMapping("/{connectionId}/validate")
    fun validateConnection(@PathVariable connectionId: String): ResponseEntity<Map<String, Any?>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val result = adapter.validateConnection(credentials)
        return ResponseEntity.ok(mapOf(
            "connected" to result.connected,
            "message" to result.message,
            "needsReauth" to result.needsReauth
        ))
    }

    @PostMapping("/{connectionId}/refresh")
    fun refreshConnection(@PathVariable connectionId: String): ResponseEntity<Map<String, String>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val refreshed = adapter.refreshAuth(credentials)
        credentialService.updateCredentials(connectionId, refreshed)
        return ResponseEntity.ok(mapOf("status" to "REFRESHED"))
    }

    private fun parseCredentials(request: CreateConnectionRequest): BrokerCredentials {
        val json = objectMapper.writeValueAsString(
            request.credentials + ("brokerType" to request.brokerType.name)
        )
        return objectMapper.readValue(json, BrokerCredentials::class.java)
    }

    private fun toResponse(entity: GatewayConnection) = ConnectionResponse(
        connectionId = entity.id,
        brokerType = BrokerType.valueOf(entity.brokerType),
        status = entity.status,
        accountsJson = entity.accountsJson,
        lastValidatedAt = entity.lastValidatedAt,
        lastRefreshedAt = entity.lastRefreshedAt,
        errorMessage = entity.errorMessage,
        createdAt = entity.createdAt
    )
}
```

- [ ] **Step 4: Create DataController**

```kotlin
// api/controller/DataController.kt
package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.dto.*
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import org.springframework.format.annotation.DateTimeFormat
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts")
class DataController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    @GetMapping
    fun listAccounts(@PathVariable connectionId: String): ResponseEntity<Map<String, List<UnifiedAccount>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val accounts = adapter.listAccounts(credentials)
        return ResponseEntity.ok(mapOf("accounts" to accounts))
    }

    @GetMapping("/{accountId}/balances")
    fun getBalances(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<UnifiedBalance> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(adapter.getBalances(credentials, accountId))
    }

    @GetMapping("/{accountId}/positions")
    fun getPositions(
        @PathVariable connectionId: String,
        @PathVariable accountId: String
    ): ResponseEntity<Map<String, List<UnifiedPosition>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("positions" to adapter.getPositions(credentials, accountId)))
    }

    @GetMapping("/{accountId}/activities")
    fun getActivities(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) startDate: LocalDate?,
        @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) endDate: LocalDate?
    ): ResponseEntity<Map<String, List<UnifiedActivity>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("activities" to adapter.getActivities(credentials, accountId, startDate, endDate)))
    }

    @GetMapping("/{accountId}/orders")
    fun getOrders(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestParam(required = false) status: String?
    ): ResponseEntity<Map<String, List<UnifiedOrder>>> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        return ResponseEntity.ok(mapOf("orders" to adapter.getOrders(credentials, accountId)))
    }
}
```

- [ ] **Step 5: Create OrderController**

```kotlin
// api/controller/OrderController.kt
package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.dto.CancelResult
import com.portfolio.brokergateway.adapter.dto.OrderRequest
import com.portfolio.brokergateway.adapter.dto.OrderResult
import com.portfolio.brokergateway.api.dto.PlaceOrderRequest
import com.portfolio.brokergateway.config.AdapterRegistry
import com.portfolio.brokergateway.credential.CredentialService
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/v1/gateway/connections/{connectionId}/accounts/{accountId}/orders")
class OrderController(
    private val credentialService: CredentialService,
    private val adapterRegistry: AdapterRegistry
) {
    @PostMapping
    fun placeOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @RequestBody request: PlaceOrderRequest
    ): ResponseEntity<OrderResult> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val orderRequest = OrderRequest(
            symbol = request.symbol,
            action = request.action,
            quantity = request.quantity,
            orderType = request.orderType,
            limitPrice = request.limitPrice,
            stopPrice = request.stopPrice,
            timeInForce = request.timeInForce,
            currency = request.currency
        )
        val result = adapter.placeOrder(credentials, accountId, orderRequest)
        return ResponseEntity.ok(result)
    }

    @DeleteMapping("/{brokerOrderId}")
    fun cancelOrder(
        @PathVariable connectionId: String,
        @PathVariable accountId: String,
        @PathVariable brokerOrderId: String
    ): ResponseEntity<CancelResult> {
        val credentials = credentialService.getCredentials(connectionId)
        val adapter = adapterRegistry.getAdapter(credentials.brokerType)
        val result = adapter.cancelOrder(credentials, accountId, brokerOrderId)
        return ResponseEntity.ok(result)
    }
}
```

- [ ] **Step 6: Write HealthController test**

```kotlin
// test: api/controller/HealthControllerTest.kt
package com.portfolio.brokergateway.api.controller

import com.portfolio.brokergateway.adapter.BrokerAdapter
import com.portfolio.brokergateway.adapter.BrokerType
import com.portfolio.brokergateway.config.AdapterRegistry
import io.mockk.every
import io.mockk.mockk
import org.junit.jupiter.api.Test
import org.springframework.http.HttpStatus
import kotlin.test.assertEquals

class HealthControllerTest {

    @Test
    fun `health returns UP with broker statuses`() {
        val adapter = mockk<BrokerAdapter>()
        every { adapter.brokerType } returns BrokerType.IBKR
        val registry = AdapterRegistry(listOf(adapter))
        val controller = HealthController(registry)

        val response = controller.health()
        assertEquals(HttpStatus.OK, response.statusCode)
        val body = response.body!!
        assertEquals("UP", body.status)
        assertEquals(3, body.brokers.size)

        val ibkr = body.brokers.first { it.brokerType == BrokerType.IBKR }
        assertEquals(true, ibkr.enabled)
        assertEquals("OK", ibkr.status)

        val qt = body.brokers.first { it.brokerType == BrokerType.QUESTRADE }
        assertEquals(false, qt.enabled)
        assertEquals("DISABLED", qt.status)
    }

    @Test
    fun `brokerHealth returns status for specific broker`() {
        val registry = AdapterRegistry(emptyList())
        val controller = HealthController(registry)

        val response = controller.brokerHealth(BrokerType.IBKR)
        assertEquals(HttpStatus.OK, response.statusCode)
        assertEquals(false, response.body!!.enabled)
        assertEquals("DISABLED", response.body!!.status)
    }
}
```

- [ ] **Step 7: Run controller tests**

Run: `docker compose exec broker-gateway-service ./gradlew test --tests "com.portfolio.brokergateway.api.controller.*" -i`
Expected: PASS

- [ ] **Step 8: Commit**

```bash
git add backend/broker-gateway/src/main/kotlin/com/portfolio/brokergateway/api/ backend/broker-gateway/src/test/kotlin/com/portfolio/brokergateway/api/
git commit -m "feat(broker-gateway): add REST API controllers for connections, data, orders, and health"
```

---

## Task 9: Dockerfile

**Files:**
- Create: `backend/broker-gateway/Dockerfile`

- [ ] **Step 1: Create Dockerfile**

```dockerfile
FROM gradle:8.10-jdk21-alpine AS build
WORKDIR /build

COPY common ./common
COPY broker-gateway ./broker-gateway

WORKDIR /build/broker-gateway
RUN gradle dependencies --no-daemon || true
RUN gradle build -x test --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app
RUN apt-get update && \
    apt-get install -y --no-install-recommends ca-certificates curl wget && \
    rm -rf /var/lib/apt/lists/*
RUN groupadd -g 1001 appgroup && \
    useradd -u 1001 -g appgroup -s /bin/bash appuser
COPY --from=build /build/broker-gateway/build/libs/app.jar app.jar
RUN chown -R appuser:appgroup /app
USER appuser
EXPOSE 8084
HEALTHCHECK --interval=30s --timeout=3s --start-period=60s --retries=3 \
    CMD curl --fail --silent http://localhost:8084/actuator/health || exit 1
ENTRYPOINT ["java", "-jar", "app.jar"]
```

- [ ] **Step 2: Commit**

```bash
git add backend/broker-gateway/Dockerfile
git commit -m "feat(broker-gateway): add Dockerfile"
```

---

## Task 10: Docker Compose Integration

**Files:**
- Modify: `docker-compose.yml`

- [ ] **Step 1: Add broker-gateway-service to docker-compose.yml**

Add this service definition after the `strategy-service` block (around line 162):

```yaml
  broker-gateway-service:
    build:
      context: ./backend
      dockerfile: broker-gateway/Dockerfile
    container_name: portfolio-broker-gateway
    environment:
      SPRING_PROFILES_ACTIVE: local
      DATABASE_URL: jdbc:postgresql://postgres:5432/${POSTGRES_DB:-portfolio}
      DATABASE_USERNAME: ${POSTGRES_USER:-portfolio}
      DATABASE_PASSWORD: ${POSTGRES_PASSWORD:-portfolio}
      REDIS_HOST: redis
      REDIS_PORT: 6379
      BROKER_ENCRYPTION_KEY: ${BROKER_ENCRYPTION_KEY:-}
      GATEWAY_API_KEY: ${GATEWAY_API_KEY:-dev-gateway-key}
    ports:
      - "8084:8084"
    depends_on:
      postgres:
        condition: service_healthy
      redis:
        condition: service_healthy
    healthcheck:
      test: ["CMD", "wget", "--no-verbose", "--tries=1", "--spider", "http://localhost:8084/actuator/health"]
      interval: 30s
      timeout: 5s
      retries: 3
      start_period: 60s
    restart: unless-stopped
    networks:
      - portfolio-network
```

- [ ] **Step 2: Add BROKER_GATEWAY_URL to the backend service environment**

In the `backend` service environment block, add:

```yaml
      BROKER_GATEWAY_URL: http://broker-gateway-service:8084
```

- [ ] **Step 3: Commit**

```bash
git add docker-compose.yml
git commit -m "feat(broker-gateway): add service to docker-compose"
```

---

## Task 11: Build and Verify

- [ ] **Step 1: Build the service in Docker**

Run: `docker compose build broker-gateway-service`
Expected: Build succeeds — `app.jar` created

- [ ] **Step 2: Start the full stack**

Run: `docker compose up -d`
Expected: All services start including broker-gateway-service

- [ ] **Step 3: Verify health endpoint**

Run: `curl http://localhost:8084/actuator/health`
Expected: `{"status":"UP"}`

- [ ] **Step 4: Verify gateway health endpoint**

Run: `curl http://localhost:8084/api/v1/gateway/health`
Expected: `{"status":"UP","brokers":[{"brokerType":"IBKR","enabled":false,"status":"DISABLED"},{"brokerType":"QUESTRADE","enabled":false,"status":"DISABLED"},{"brokerType":"WEALTHSIMPLE","enabled":false,"status":"DISABLED"}]}`

- [ ] **Step 5: Verify Flyway migration ran**

Run: `docker compose exec postgres psql -U portfolio -c "SELECT table_name FROM information_schema.tables WHERE table_schema = 'broker_gateway';"`
Expected: Shows `connections` table

- [ ] **Step 6: Run all unit tests**

Run: `docker compose exec broker-gateway-service ./gradlew test`
Expected: All tests pass

- [ ] **Step 7: Commit any fixes if needed**

---

## Task 12: Update Documentation

**Files:**
- Modify: `docs/reference/infrastructure.md`
- Modify: `docs/reference/configurations.md`
- Modify: `docs/reference/api-endpoints.md`

- [ ] **Step 1: Update infrastructure.md**

Add broker-gateway-service to the service table and Docker Compose section.

- [ ] **Step 2: Update configurations.md**

Add the new environment variables: `BROKER_ENCRYPTION_KEY`, `GATEWAY_API_KEY`, `BROKER_GATEWAY_URL`.

- [ ] **Step 3: Update api-endpoints.md**

Add the broker-gateway API endpoints under a new "Broker Gateway Service (port 8084)" section.

- [ ] **Step 4: Commit**

```bash
git add docs/reference/
git commit -m "docs: add broker-gateway service to reference documentation"
```

---

## Verification Checklist

1. `docker compose build broker-gateway-service` succeeds
2. `docker compose up -d` starts broker-gateway on port 8084
3. `curl http://localhost:8084/actuator/health` returns UP
4. `curl http://localhost:8084/api/v1/gateway/health` returns broker statuses
5. Flyway creates `broker_gateway.connections` table
6. `docker compose exec broker-gateway-service ./gradlew test` passes all tests
7. All reference docs are updated
