package com.portfolio.ingestion.dto.alphavantage

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.KotlinModule
import com.fasterxml.jackson.module.kotlin.readValue
import org.junit.jupiter.api.Test
import java.math.BigDecimal
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNotNull
import kotlin.test.assertNull
import kotlin.test.assertTrue

class AVEtfProfileResponseTest {

    private val objectMapper = ObjectMapper().registerModule(KotlinModule.Builder().build())

    @Test
    fun `parses sectors as array of objects correctly`() {
        val json = """
        {
            "symbol": "SPY",
            "name": "SPDR S&P 500 ETF Trust",
            "sectors": [
                {"sector": "Information Technology", "weight": "0.35"},
                {"sector": "Health Care", "weight": "0.12"},
                {"sector": "Financials", "weight": "0.10"}
            ]
        }
        """.trimIndent()

        val response: AVEtfProfileResponse = objectMapper.readValue(json)

        assertEquals("SPY", response.symbol)
        assertEquals("SPDR S&P 500 ETF Trust", response.name)
        assertNotNull(response.sectors)
        assertEquals(3, response.sectors?.size)

        // Verify sector allocations are accessible
        assertEquals(BigDecimal("0.35"), response.getSectorAllocation("Information Technology"))
        assertEquals(BigDecimal("0.12"), response.getSectorAllocation("Health Care"))
        assertEquals(BigDecimal("0.10"), response.getSectorAllocation("Financials"))
    }

    @Test
    fun `getSectorAllocation is case insensitive`() {
        val response = AVEtfProfileResponse(
            symbol = "QQQ",
            sectors = listOf(
                AVSector(sector = "Information Technology", weight = "0.50")
            )
        )

        assertEquals(BigDecimal("0.50"), response.getSectorAllocation("information technology"))
        assertEquals(BigDecimal("0.50"), response.getSectorAllocation("INFORMATION TECHNOLOGY"))
        assertEquals(BigDecimal("0.50"), response.getSectorAllocation("Information Technology"))
    }

    @Test
    fun `getSectorAllocation returns null for non-existent sector`() {
        val response = AVEtfProfileResponse(
            symbol = "QQQ",
            sectors = listOf(
                AVSector(sector = "Information Technology", weight = "0.50")
            )
        )

        assertNull(response.getSectorAllocation("Real Estate"))
    }

    @Test
    fun `getSectorAllocation handles null sectors list`() {
        val response = AVEtfProfileResponse(
            symbol = "TEST",
            sectors = null
        )

        assertNull(response.getSectorAllocation("Information Technology"))
    }

    @Test
    fun `getSectorAllocation handles invalid weight values`() {
        val response = AVEtfProfileResponse(
            symbol = "TEST",
            sectors = listOf(
                AVSector(sector = "Tech", weight = "None"),
                AVSector(sector = "Health", weight = "N/A"),
                AVSector(sector = "Finance", weight = "-"),
                AVSector(sector = "Energy", weight = "")
            )
        )

        assertNull(response.getSectorAllocation("Tech"))
        assertNull(response.getSectorAllocation("Health"))
        assertNull(response.getSectorAllocation("Finance"))
        assertNull(response.getSectorAllocation("Energy"))
    }

    @Test
    fun `SectorAllocations fromResponse maps all GICS sectors`() {
        val response = AVEtfProfileResponse(
            symbol = "SPY",
            sectors = listOf(
                AVSector(sector = "Information Technology", weight = "0.30"),
                AVSector(sector = "Communication Services", weight = "0.08"),
                AVSector(sector = "Consumer Discretionary", weight = "0.10"),
                AVSector(sector = "Consumer Staples", weight = "0.06"),
                AVSector(sector = "Health Care", weight = "0.12"),
                AVSector(sector = "Industrials", weight = "0.08"),
                AVSector(sector = "Utilities", weight = "0.02"),
                AVSector(sector = "Materials", weight = "0.03"),
                AVSector(sector = "Energy", weight = "0.04"),
                AVSector(sector = "Financials", weight = "0.13"),
                AVSector(sector = "Real Estate", weight = "0.04")
            )
        )

        val allocations = SectorAllocations.fromResponse(response)

        assertEquals(BigDecimal("0.30"), allocations.informationTechnology)
        assertEquals(BigDecimal("0.08"), allocations.communicationServices)
        assertEquals(BigDecimal("0.10"), allocations.consumerDiscretionary)
        assertEquals(BigDecimal("0.06"), allocations.consumerStaples)
        assertEquals(BigDecimal("0.12"), allocations.healthCare)
        assertEquals(BigDecimal("0.08"), allocations.industrials)
        assertEquals(BigDecimal("0.02"), allocations.utilities)
        assertEquals(BigDecimal("0.03"), allocations.materials)
        assertEquals(BigDecimal("0.04"), allocations.energy)
        assertEquals(BigDecimal("0.13"), allocations.financials)
        assertEquals(BigDecimal("0.04"), allocations.realEstate)
    }

    @Test
    fun `parses complete ETF profile response with all fields`() {
        val json = """
        {
            "symbol": "QQQ",
            "name": "Invesco QQQ Trust",
            "asset_type": "ETF",
            "description": "Tracks the Nasdaq-100 Index",
            "net_assets": "200000000000",
            "net_expense_ratio": "0.20",
            "portfolio_turnover": "8.5",
            "dividend_yield": "0.55",
            "inception_date": "1999-03-10",
            "leveraged": "NO",
            "holdings_count": "101",
            "sectors": [
                {"sector": "Information Technology", "weight": "0.58"}
            ],
            "holdings": [
                {"symbol": "AAPL", "name": "Apple Inc.", "weight": "0.12"},
                {"symbol": "MSFT", "name": "Microsoft Corp", "weight": "0.10"}
            ]
        }
        """.trimIndent()

        val response: AVEtfProfileResponse = objectMapper.readValue(json)

        assertEquals("QQQ", response.symbol)
        assertEquals("Invesco QQQ Trust", response.name)
        assertEquals("ETF", response.assetType)
        assertEquals("200000000000", response.netAssets)
        assertEquals("0.20", response.netExpenseRatio)
        assertEquals("101", response.holdingsCount)
        assertEquals(false, response.isLeveraged())
        assertEquals(1, response.sectors?.size)
        assertEquals(2, response.holdings?.size)
    }

    @Test
    fun `isValid returns true for actual API response without symbol field`() {
        // This matches the actual Alpha Vantage ETF_PROFILE response format
        // which does NOT include symbol, name, asset_type, or description
        val json = """
        {
            "net_assets": "13500000",
            "net_expense_ratio": "0.0065",
            "portfolio_turnover": "0.1",
            "dividend_yield": "n/a",
            "inception_date": "2021-08-04",
            "leveraged": "NO",
            "sectors": [
                {"sector": "INFORMATION TECHNOLOGY", "weight": "0.519"}
            ],
            "holdings": [
                {"symbol": "AAPL", "description": "APPLE INC", "weight": "0.09"}
            ]
        }
        """.trimIndent()

        val response: AVEtfProfileResponse = objectMapper.readValue(json)

        // Symbol should be null (not in response)
        assertNull(response.symbol)
        // But isValid should return true because we have data
        assertTrue(response.isValid())
        assertEquals("13500000", response.netAssets)
        assertEquals(1, response.sectors?.size)
        assertEquals(1, response.holdings?.size)
    }

    @Test
    fun `isValid returns true when only netAssets is present`() {
        val response = AVEtfProfileResponse(
            netAssets = "1000000"
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns true when only sectors are present`() {
        val response = AVEtfProfileResponse(
            sectors = listOf(AVSector(sector = "Tech", weight = "0.5"))
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns true when only holdings are present`() {
        val response = AVEtfProfileResponse(
            holdings = listOf(AVHolding(symbol = "AAPL", weight = "0.1"))
        )
        assertTrue(response.isValid())
    }

    @Test
    fun `isValid returns false when no data fields are present`() {
        val response = AVEtfProfileResponse()
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when response contains error note`() {
        val response = AVEtfProfileResponse(
            netAssets = "1000000",
            note = "API call frequency exceeded"
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `isValid returns false when response contains information message`() {
        val response = AVEtfProfileResponse(
            sectors = listOf(AVSector(sector = "Tech", weight = "0.5")),
            information = "Invalid API call"
        )
        assertFalse(response.isValid())
    }

    @Test
    fun `getSectorAllocation handles uppercase sector names from API`() {
        // Alpha Vantage returns sector names in uppercase
        val response = AVEtfProfileResponse(
            sectors = listOf(
                AVSector(sector = "INFORMATION TECHNOLOGY", weight = "0.519"),
                AVSector(sector = "HEALTH CARE", weight = "0.12")
            )
        )

        // Case-insensitive lookup should work
        assertEquals(BigDecimal("0.519"), response.getSectorAllocation("Information Technology"))
        assertEquals(BigDecimal("0.12"), response.getSectorAllocation("Health Care"))
    }
}
