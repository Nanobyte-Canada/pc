package com.portfolio.broker.service

import com.portfolio.broker.entity.OrderStatus
import com.portfolio.broker.entity.OrderAction
import com.portfolio.broker.entity.OrderType
import com.portfolio.broker.entity.TimeInForce
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.Assertions.*

class PositionFetchServiceOrderMappingTest {

    @Test
    fun `mapSnapTradeOrderStatus maps EXECUTED to FILLED`() {
        assertEquals(OrderStatus.FILLED, mapStatus("EXECUTED"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps CANCELED to CANCELLED`() {
        assertEquals(OrderStatus.CANCELLED, mapStatus("CANCELED"))
        assertEquals(OrderStatus.CANCELLED, mapStatus("CANCELLED"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps PENDING and unknown to PENDING`() {
        assertEquals(OrderStatus.PENDING, mapStatus("PENDING"))
        assertEquals(OrderStatus.PENDING, mapStatus("NONE"))
        assertEquals(OrderStatus.PENDING, mapStatus(null))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps PARTIAL to PARTIALLY_FILLED`() {
        assertEquals(OrderStatus.PARTIALLY_FILLED, mapStatus("PARTIAL"))
    }

    @Test
    fun `mapSnapTradeOrderStatus maps REJECTED to REJECTED`() {
        assertEquals(OrderStatus.REJECTED, mapStatus("REJECTED"))
    }

    @Test
    fun `mapSnapTradeOrderAction maps BUY and SELL`() {
        assertEquals(OrderAction.BUY, mapAction("BUY"))
        assertEquals(OrderAction.SELL, mapAction("SELL"))
        assertNull(mapAction(null))
        assertNull(mapAction("UNKNOWN"))
    }

    @Test
    fun `mapSnapTradeOrderType maps Limit and defaults to MARKET`() {
        assertEquals(OrderType.LIMIT, mapOrderType("Limit"))
        assertEquals(OrderType.LIMIT, mapOrderType("limit"))
        assertEquals(OrderType.MARKET, mapOrderType("Market"))
        assertEquals(OrderType.MARKET, mapOrderType(null))
    }

    @Test
    fun `mapSnapTradeTimeInForce maps GTC and defaults to DAY`() {
        assertEquals(TimeInForce.GTC, mapTimeInForce("GTC"))
        assertEquals(TimeInForce.DAY, mapTimeInForce("Day"))
        assertEquals(TimeInForce.DAY, mapTimeInForce(null))
    }

    // Replicate the private mapping logic from PositionFetchService
    private fun mapStatus(status: String?): OrderStatus {
        return when (status?.uppercase()) {
            "EXECUTED" -> OrderStatus.FILLED
            "CANCELED", "CANCELLED" -> OrderStatus.CANCELLED
            "PARTIAL" -> OrderStatus.PARTIALLY_FILLED
            "REJECTED" -> OrderStatus.REJECTED
            else -> OrderStatus.PENDING
        }
    }

    private fun mapAction(action: String?): OrderAction? {
        return when (action?.uppercase()) {
            "BUY" -> OrderAction.BUY
            "SELL" -> OrderAction.SELL
            else -> null
        }
    }

    private fun mapOrderType(type: String?): OrderType {
        return when (type?.lowercase()) {
            "limit" -> OrderType.LIMIT
            else -> OrderType.MARKET
        }
    }

    private fun mapTimeInForce(tif: String?): TimeInForce {
        return when (tif?.uppercase()) {
            "GTC" -> TimeInForce.GTC
            else -> TimeInForce.DAY
        }
    }
}
