package com.portfolio.brokergateway.adapter.ibkr

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class IbkrDtoMappersTest {

    @Test
    fun `mapAccountType normalizes IBKR account types`() {
        assertEquals(AccountType.CASH, IbkrDtoMappers.mapAccountType("Individual"))
        assertEquals(AccountType.MARGIN, IbkrDtoMappers.mapAccountType("Margin"))
        assertEquals(AccountType.TFSA, IbkrDtoMappers.mapAccountType("TFSA"))
        assertEquals(AccountType.RRSP, IbkrDtoMappers.mapAccountType("RRSP"))
        assertEquals(AccountType.FHSA, IbkrDtoMappers.mapAccountType("FHSA"))
        assertEquals(AccountType.LIRA, IbkrDtoMappers.mapAccountType("LIRA"))
        assertEquals(AccountType.LIF, IbkrDtoMappers.mapAccountType("LIF"))
        assertEquals(AccountType.RIF, IbkrDtoMappers.mapAccountType("RIF"))
        assertEquals(AccountType.OTHER, IbkrDtoMappers.mapAccountType("SomethingNew"))
    }

    @Test
    fun `mapInstrumentType normalizes IBKR secType`() {
        assertEquals(InstrumentType.STOCK, IbkrDtoMappers.mapInstrumentType("STK"))
        assertEquals(InstrumentType.OPTION, IbkrDtoMappers.mapInstrumentType("OPT"))
        assertEquals(InstrumentType.BOND, IbkrDtoMappers.mapInstrumentType("BOND"))
        assertEquals(InstrumentType.MUTUAL_FUND, IbkrDtoMappers.mapInstrumentType("FUND"))
        assertEquals(InstrumentType.CASH, IbkrDtoMappers.mapInstrumentType("CASH"))
        assertEquals(InstrumentType.CRYPTO, IbkrDtoMappers.mapInstrumentType("CRYPTO"))
        assertEquals(InstrumentType.OTHER, IbkrDtoMappers.mapInstrumentType("WAR"))
    }

    @Test
    fun `mapOrderStatus normalizes IBKR order states`() {
        assertEquals(OrderStatus.PENDING, IbkrDtoMappers.mapOrderStatus("PendingSubmit"))
        assertEquals(OrderStatus.SUBMITTED, IbkrDtoMappers.mapOrderStatus("Submitted"))
        assertEquals(OrderStatus.SUBMITTED, IbkrDtoMappers.mapOrderStatus("PreSubmitted"))
        assertEquals(OrderStatus.FILLED, IbkrDtoMappers.mapOrderStatus("Filled"))
        assertEquals(OrderStatus.CANCELLED, IbkrDtoMappers.mapOrderStatus("Cancelled"))
        assertEquals(OrderStatus.CANCELLED, IbkrDtoMappers.mapOrderStatus("ApiCancelled"))
        assertEquals(OrderStatus.REJECTED, IbkrDtoMappers.mapOrderStatus("Inactive"))
        assertEquals(OrderStatus.FAILED, IbkrDtoMappers.mapOrderStatus("Error"))
        assertEquals(OrderStatus.PENDING, IbkrDtoMappers.mapOrderStatus("UnknownState"))
    }

    @Test
    fun `mapActivityType normalizes IBKR Flex codes`() {
        assertEquals(ActivityType.BUY, IbkrDtoMappers.mapActivityType("BUY"))
        assertEquals(ActivityType.BUY, IbkrDtoMappers.mapActivityType("BOT"))
        assertEquals(ActivityType.SELL, IbkrDtoMappers.mapActivityType("SELL"))
        assertEquals(ActivityType.SELL, IbkrDtoMappers.mapActivityType("SLD"))
        assertEquals(ActivityType.DIVIDEND, IbkrDtoMappers.mapActivityType("DIV"))
        assertEquals(ActivityType.DIVIDEND, IbkrDtoMappers.mapActivityType("CDIV"))
        assertEquals(ActivityType.TRANSFER_IN, IbkrDtoMappers.mapActivityType("DEP"))
        assertEquals(ActivityType.TRANSFER_OUT, IbkrDtoMappers.mapActivityType("WITH"))
        assertEquals(ActivityType.FEE, IbkrDtoMappers.mapActivityType("COMM"))
        assertEquals(ActivityType.INTEREST, IbkrDtoMappers.mapActivityType("INT"))
        assertEquals(ActivityType.OPTION_EXPIRATION, IbkrDtoMappers.mapActivityType("EXP"))
        assertEquals(ActivityType.OPTION_ASSIGNMENT, IbkrDtoMappers.mapActivityType("ASSIGN"))
        assertEquals(ActivityType.OPTION_EXERCISE, IbkrDtoMappers.mapActivityType("EXER"))
        assertEquals(ActivityType.STOCK_SPLIT, IbkrDtoMappers.mapActivityType("SPLIT"))
        assertEquals(ActivityType.CORPORATE_ACTION, IbkrDtoMappers.mapActivityType("CA"))
        assertEquals(ActivityType.OTHER, IbkrDtoMappers.mapActivityType("UNKNOWN"))
    }

    @Test
    fun `mapOptionRight normalizes C and P`() {
        assertEquals("CALL", IbkrDtoMappers.mapOptionRight("C"))
        assertEquals("PUT", IbkrDtoMappers.mapOptionRight("P"))
        assertEquals(null, IbkrDtoMappers.mapOptionRight(null))
        assertEquals(null, IbkrDtoMappers.mapOptionRight(""))
    }
}
