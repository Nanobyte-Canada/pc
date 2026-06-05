package com.portfolio.brokergateway.adapter.questrade

import com.portfolio.brokergateway.adapter.*
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals

class QuestradeDtoMappersTest {

    @Test
    fun `mapAccountType normalizes Questrade account types`() {
        assertEquals(AccountType.CASH, QuestradeDtoMappers.mapAccountType("Cash"))
        assertEquals(AccountType.MARGIN, QuestradeDtoMappers.mapAccountType("Margin"))
        assertEquals(AccountType.TFSA, QuestradeDtoMappers.mapAccountType("TFSA"))
        assertEquals(AccountType.RRSP, QuestradeDtoMappers.mapAccountType("RRSP"))
        assertEquals(AccountType.RRSP, QuestradeDtoMappers.mapAccountType("SRRSP"))
        assertEquals(AccountType.FHSA, QuestradeDtoMappers.mapAccountType("FHSA"))
        assertEquals(AccountType.RESP, QuestradeDtoMappers.mapAccountType("RESP"))
        assertEquals(AccountType.LIRA, QuestradeDtoMappers.mapAccountType("LIRA"))
        assertEquals(AccountType.LIF, QuestradeDtoMappers.mapAccountType("LIF"))
        assertEquals(AccountType.RIF, QuestradeDtoMappers.mapAccountType("RIF"))
        assertEquals(AccountType.RIF, QuestradeDtoMappers.mapAccountType("RRIF"))
        assertEquals(AccountType.OTHER, QuestradeDtoMappers.mapAccountType("Unknown"))
    }

    @Test
    fun `mapInstrumentType normalizes Questrade symbol types`() {
        assertEquals(InstrumentType.STOCK, QuestradeDtoMappers.mapInstrumentType("Stock"))
        assertEquals(InstrumentType.ETF, QuestradeDtoMappers.mapInstrumentType("ETF"))
        assertEquals(InstrumentType.OPTION, QuestradeDtoMappers.mapInstrumentType("Option"))
        assertEquals(InstrumentType.BOND, QuestradeDtoMappers.mapInstrumentType("Bond"))
        assertEquals(InstrumentType.MUTUAL_FUND, QuestradeDtoMappers.mapInstrumentType("MutualFund"))
        assertEquals(InstrumentType.OTHER, QuestradeDtoMappers.mapInstrumentType("Warrant"))
    }

    @Test
    fun `mapOrderStatus normalizes Questrade order states`() {
        assertEquals(OrderStatus.PENDING, QuestradeDtoMappers.mapOrderStatus("Pending"))
        assertEquals(OrderStatus.SUBMITTED, QuestradeDtoMappers.mapOrderStatus("Accepted"))
        assertEquals(OrderStatus.SUBMITTED, QuestradeDtoMappers.mapOrderStatus("Open"))
        assertEquals(OrderStatus.FILLED, QuestradeDtoMappers.mapOrderStatus("Executed"))
        assertEquals(OrderStatus.PARTIALLY_FILLED, QuestradeDtoMappers.mapOrderStatus("PartiallyExecuted"))
        assertEquals(OrderStatus.CANCELLED, QuestradeDtoMappers.mapOrderStatus("Canceled"))
        assertEquals(OrderStatus.CANCELLED, QuestradeDtoMappers.mapOrderStatus("Expired"))
        assertEquals(OrderStatus.REJECTED, QuestradeDtoMappers.mapOrderStatus("Rejected"))
    }

    @Test
    fun `mapActivityType normalizes Questrade activity types`() {
        assertEquals(ActivityType.BUY, QuestradeDtoMappers.mapActivityType("Trades", "Buy"))
        assertEquals(ActivityType.SELL, QuestradeDtoMappers.mapActivityType("Trades", "Sell"))
        assertEquals(ActivityType.DIVIDEND, QuestradeDtoMappers.mapActivityType("Dividends", null))
        assertEquals(ActivityType.TRANSFER_IN, QuestradeDtoMappers.mapActivityType("Deposits", null))
        assertEquals(ActivityType.TRANSFER_OUT, QuestradeDtoMappers.mapActivityType("Withdrawals", null))
        assertEquals(ActivityType.FEE, QuestradeDtoMappers.mapActivityType("Fees", null))
        assertEquals(ActivityType.COMMISSION, QuestradeDtoMappers.mapActivityType("Commissions", null))
        assertEquals(ActivityType.INTEREST, QuestradeDtoMappers.mapActivityType("Interest", null))
        assertEquals(ActivityType.CORPORATE_ACTION, QuestradeDtoMappers.mapActivityType("Corporate actions", null))
        assertEquals(ActivityType.OTHER, QuestradeDtoMappers.mapActivityType("Unknown", null))
    }

    @Test
    fun `mapOrderAction normalizes Questrade sides`() {
        assertEquals(OrderAction.BUY, QuestradeDtoMappers.mapOrderAction("Buy"))
        assertEquals(OrderAction.BUY, QuestradeDtoMappers.mapOrderAction("BTO"))
        assertEquals(OrderAction.SELL, QuestradeDtoMappers.mapOrderAction("Sell"))
        assertEquals(OrderAction.SELL, QuestradeDtoMappers.mapOrderAction("STC"))
    }
}
