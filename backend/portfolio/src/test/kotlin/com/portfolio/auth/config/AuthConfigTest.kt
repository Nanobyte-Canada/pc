package com.portfolio.auth.config

import ch.qos.logback.classic.Level
import ch.qos.logback.classic.Logger
import ch.qos.logback.classic.spi.ILoggingEvent
import ch.qos.logback.core.read.ListAppender
import org.junit.jupiter.api.AfterEach
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import org.slf4j.LoggerFactory
import kotlin.test.assertContains
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class AuthConfigTest {

    private lateinit var authConfig: AuthConfig
    private lateinit var listAppender: ListAppender<ILoggingEvent>
    private lateinit var logger: Logger

    @BeforeEach
    fun setup() {
        authConfig = AuthConfig()
        logger = LoggerFactory.getLogger(AuthConfig::class.java) as Logger
        listAppender = ListAppender<ILoggingEvent>()
        listAppender.start()
        logger.addAppender(listAppender)
    }

    @AfterEach
    fun tearDown() {
        logger.detachAppender(listAppender)
    }

    @Test
    fun `warns when non-local profile with blank clientId and clientSecret`() {
        authConfig.appEnvironment = "prod"
        authConfig.oauth2.google.clientId = ""
        authConfig.oauth2.google.clientSecret = ""

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isNotEmpty(), "Expected at least one WARN log entry")
        val warnMsg = warnings.first().formattedMessage
        assertContains(warnMsg, "AUTH_GOOGLE_CREDENTIALS_MISSING")
        assertContains(warnMsg, "GOOGLE_CLIENT_ID")
        assertContains(warnMsg, "GOOGLE_CLIENT_SECRET")
    }

    @Test
    fun `warns when non-local profile with blank clientId only`() {
        authConfig.appEnvironment = "dev"
        authConfig.oauth2.google.clientId = ""
        authConfig.oauth2.google.clientSecret = "set"

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isNotEmpty(), "Expected a WARN for missing clientId")
        val warnMsg = warnings.first().formattedMessage
        assertContains(warnMsg, "AUTH_GOOGLE_CREDENTIALS_MISSING")
        assertContains(warnMsg, "GOOGLE_CLIENT_ID")
        assertFalse(warnMsg.contains("GOOGLE_CLIENT_SECRET"), "Should only mention the missing env var")
    }

    @Test
    fun `warns when non-local profile with blank clientSecret only`() {
        authConfig.appEnvironment = "uat"
        authConfig.oauth2.google.clientId = "set"
        authConfig.oauth2.google.clientSecret = ""

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isNotEmpty(), "Expected a WARN for missing clientSecret")
        val warnMsg = warnings.first().formattedMessage
        assertContains(warnMsg, "AUTH_GOOGLE_CREDENTIALS_MISSING")
        assertContains(warnMsg, "GOOGLE_CLIENT_SECRET")
        assertFalse(warnMsg.contains("GOOGLE_CLIENT_ID"), "Should only mention the missing env var")
    }

    @Test
    fun `does not warn on local profile with blank credentials`() {
        authConfig.appEnvironment = "local"
        authConfig.oauth2.google.clientId = ""
        authConfig.oauth2.google.clientSecret = ""

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isEmpty(), "Expected no WARN for local profile with blank credentials")
    }

    @Test
    fun `does not warn on non-local profile with populated credentials`() {
        authConfig.appEnvironment = "prod"
        authConfig.oauth2.google.clientId = "my-client-id"
        authConfig.oauth2.google.clientSecret = "my-client-secret"

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isEmpty(), "Expected no WARN for prod profile with populated credentials")
    }

    @Test
    fun `warns in dev profile with blank credentials`() {
        authConfig.appEnvironment = "dev"
        authConfig.oauth2.google.clientId = ""
        authConfig.oauth2.google.clientSecret = ""

        authConfig.validateGoogleCredentials()

        val warnings = listAppender.list.filter { it.level == Level.WARN }
        assertTrue(warnings.isNotEmpty(), "Expected WARN in dev profile with blank credentials")
        val warnMsg = warnings.first().formattedMessage
        assertContains(warnMsg, "AUTH_GOOGLE_CREDENTIALS_MISSING")
    }
}
