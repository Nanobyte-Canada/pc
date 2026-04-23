// test: adapter/wealthsimple/WealthsimpleDtoMappersTest.kt
package com.portfolio.brokergateway.adapter.wealthsimple

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class WealthsimpleDtoMappersTest {

    @Test
    fun `mapAccountType normalizes Wealthsimple account types`() {
        assertEquals(AccountType.CASH, WealthsimpleDtoMappers.mapAccountType("ca_non_registered"))
        assertEquals(AccountType.MARGIN, WealthsimpleDtoMappers.mapAccountType("ca_non_registered_margin"))
        assertEquals(AccountType.TFSA, WealthsimpleDtoMappers.mapAccountType("ca_tfsa"))
        assertEquals(AccountType.RRSP, WealthsimpleDtoMappers.mapAccountType("ca_rrsp"))
        assertEquals(AccountType.FHSA, WealthsimpleDtoMappers.mapAccountType("ca_fhsa"))
        assertEquals(AccountType.LIRA, WealthsimpleDtoMappers.mapAccountType("ca_lira"))
        assertEquals(AccountType.CRYPTO, WealthsimpleDtoMappers.mapAccountType("ca_crypto"))
        assertEquals(AccountType.OTHER, WealthsimpleDtoMappers.mapAccountType("unknown"))
    }

    @Test
    fun `mapInstrumentType normalizes Wealthsimple security types`() {
        assertEquals(InstrumentType.STOCK, WealthsimpleDtoMappers.mapInstrumentType("equity"))
        assertEquals(InstrumentType.ETF, WealthsimpleDtoMappers.mapInstrumentType("etf"))
        assertEquals(InstrumentType.MUTUAL_FUND, WealthsimpleDtoMappers.mapInstrumentType("mutual_fund"))
        assertEquals(InstrumentType.CRYPTO, WealthsimpleDtoMappers.mapInstrumentType("crypto"))
        assertEquals(InstrumentType.OTHER, WealthsimpleDtoMappers.mapInstrumentType("bond"))
    }

    @Test
    fun `mapOrderStatus normalizes Wealthsimple order statuses`() {
        assertEquals(OrderStatus.PENDING, WealthsimpleDtoMappers.mapOrderStatus("submitted"))
        assertEquals(OrderStatus.SUBMITTED, WealthsimpleDtoMappers.mapOrderStatus("posted"))
        assertEquals(OrderStatus.FILLED, WealthsimpleDtoMappers.mapOrderStatus("filled"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, WealthsimpleDtoMappers.mapOrderStatus("partial_fill"))
        assertEquals(OrderStatus.CANCELLED, WealthsimpleDtoMappers.mapOrderStatus("cancelled"))
        assertEquals(OrderStatus.REJECTED, WealthsimpleDtoMappers.mapOrderStatus("rejected"))
        assertEquals(OrderStatus.FAILED, WealthsimpleDtoMappers.mapOrderStatus("failed"))
    }

    @Test
    fun `mapActivityType normalizes Wealthsimple activity types`() {
        assertEquals(ActivityType.BUY, WealthsimpleDtoMappers.mapActivityType("buy"))
        assertEquals(ActivityType.SELL, WealthsimpleDtoMappers.mapActivityType("sell"))
        assertEquals(ActivityType.DIVIDEND, WealthsimpleDtoMappers.mapActivityType("dividend"))
        assertEquals(ActivityType.TRANSFER_IN, WealthsimpleDtoMappers.mapActivityType("deposit"))
        assertEquals(ActivityType.TRANSFER_IN, WealthsimpleDtoMappers.mapActivityType("institutional_transfer"))
        assertEquals(ActivityType.TRANSFER_OUT, WealthsimpleDtoMappers.mapActivityType("withdrawal"))
        assertEquals(ActivityType.FEE, WealthsimpleDtoMappers.mapActivityType("fee"))
        assertEquals(ActivityType.INTEREST, WealthsimpleDtoMappers.mapActivityType("interest"))
        assertEquals(ActivityType.STOCK_SPLIT, WealthsimpleDtoMappers.mapActivityType("stock_split"))
        assertEquals(ActivityType.CORPORATE_ACTION, WealthsimpleDtoMappers.mapActivityType("reorganization"))
        assertEquals(ActivityType.OTHER, WealthsimpleDtoMappers.mapActivityType("unknown"))
    }
}
