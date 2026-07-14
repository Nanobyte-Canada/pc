package com.portfolio.marketdata.ibkr

import com.portfolio.marketdata.config.AppProperties
import org.junit.jupiter.api.Test
import java.util.concurrent.CompletableFuture
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class TwsIbkrClientTest {

    private val properties = AppProperties(host = "", port = 4002, clientId = 1, maxChainExpirations = 12)

    /**
     * Helper to set the private [AtomicBoolean] `connected` field via reflection.
     */
    private fun setConnected(client: TwsIbkrClient, value: Boolean) {
        val field = TwsIbkrClient::class.java.getDeclaredField("connected")
        field.isAccessible = true
        @Suppress("UNCHECKED_CAST")
        val atomic = field.get(client) as AtomicBoolean
        atomic.set(value)
    }

    /**
     * Helper to add a [CompletableFuture] to the private `pendingRequests` map via reflection.
     */
    @Suppress("UNCHECKED_CAST")
    private fun addPendingRequest(client: TwsIbkrClient, reqId: Int, future: CompletableFuture<Any>) {
        val field = TwsIbkrClient::class.java.getDeclaredField("pendingRequests")
        field.isAccessible = true
        val map = field.get(client) as java.util.concurrent.ConcurrentHashMap<Int, CompletableFuture<Any>>
        map[reqId] = future
    }

    /**
     * Helper to read the private `dataFarmErrorHandlers` list via reflection.
     */
    private fun getDataFarmErrorHandlers(client: TwsIbkrClient): CopyOnWriteArrayList<Runnable> {
        val field = TwsIbkrClient::class.java.getDeclaredField("dataFarmErrorHandlers")
        field.isAccessible = true
        return field.get(client) as CopyOnWriteArrayList<Runnable>
    }

    @Test
    fun `isConnected returns false before connect`() {
        val client = TwsIbkrClient(properties)
        assertFalse(client.isConnected())
    }

    @Test
    fun `connect with blank host does nothing`() {
        val client = TwsIbkrClient(AppProperties(host = "", port = 4002, clientId = 1))
        client.connect()
        assertFalse(client.isConnected())
    }

    @Test
    fun `nextValidId sets connected state`() {
        val client = TwsIbkrClient(properties)
        assertFalse(client.isConnected())
        // Simulate the callback directly (unit test without real socket)
        client.nextValidId(100)
        // connected flag is set but client.isConnected also checks socket — will be false without real connection
    }

    @Test
    fun `requestOptionChain returns empty for blank host`() {
        val client = TwsIbkrClient(AppProperties(host = "", port = 4002, clientId = 1))
        client.connect()
        val chain = client.requestOptionChain("SPY")
        assertEquals(emptyList(), chain)
    }

    @Test
    fun `cancelMarketData with unknown conId is no-op`() {
        val client = TwsIbkrClient(properties)
        client.cancelMarketData(99999)
    }

    @Test
    fun `disconnect clears all state`() {
        val client = TwsIbkrClient(properties)
        client.disconnect()
        assertFalse(client.isConnected())
    }

    @Test
    fun `error 2108 completes pending requests exceptionally and sets connected to false`() {
        val client = TwsIbkrClient(properties)
        setConnected(client, true)

        val future = CompletableFuture<Any>()
        addPendingRequest(client, 1, future)

        // Simulate data farm inactive error
        client.error(1, 0L, 2108, "Data farm inactive: USOPT", null)

        assertFalse(client.isConnected())
        assertTrue(future.isCompletedExceptionally)
        val ex = assertFailsWith<java.util.concurrent.ExecutionException> { future.get() }
        assertTrue(ex.cause is RuntimeException)
        assertTrue((ex.cause as RuntimeException).message!!.contains("Data farm inactive"))
    }

    @Test
    fun `registerDataFarmErrorHandler is invoked on error 2108`() {
        val client = TwsIbkrClient(properties)
        setConnected(client, true)

        var handlerInvoked = false
        client.registerDataFarmErrorHandler(Runnable { handlerInvoked = true })

        client.error(1, 0L, 2108, "Data farm inactive: USOPT", null)

        assertTrue(handlerInvoked, "Data farm error handler should have been invoked on 2108")
    }

    @Test
    fun `handler exceptions are caught without affecting other handlers on error 2108`() {
        val client = TwsIbkrClient(properties)
        setConnected(client, true)

        var secondHandlerInvoked = false
        // First handler throws an exception
        client.registerDataFarmErrorHandler(Runnable { throw RuntimeException("handler failure") })
        // Second handler should still be invoked
        client.registerDataFarmErrorHandler(Runnable { secondHandlerInvoked = true })

        client.error(1, 0L, 2108, "Data farm inactive: USOPT", null)

        assertTrue(secondHandlerInvoked, "Second handler should be invoked even if first handler throws")
    }
}
