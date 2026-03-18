package com.portfolio.ingestion.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.portfolio.entity.Stock
import com.portfolio.ingestion.client.EodhdClient
import com.portfolio.ingestion.config.IngestionConfig
import com.portfolio.ingestion.dto.eodhd.EodhdInstrumentDto
import com.portfolio.ingestion.entity.IngestionStep
import com.portfolio.ingestion.entity.StepName
import com.portfolio.ingestion.entity.StepStatus
import com.portfolio.ingestion.service.IngestionTrackingService
import com.portfolio.repository.EtfRepository
import com.portfolio.repository.StockRepository
import io.mockk.*
import jakarta.persistence.EntityManager
import org.junit.jupiter.api.Assertions.*
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.OffsetDateTime

class EodhdIngestionServiceDeduplicationTest {

    private val eodhdClient = mockk<EodhdClient>()
    private val stockRepository = mockk<StockRepository>(relaxed = true)
    private val etfRepository = mockk<EtfRepository>(relaxed = true)
    private val trackingService = mockk<IngestionTrackingService>(relaxed = true)
    private val config = IngestionConfig()
    private val objectMapper = ObjectMapper()
    private val entityManager = mockk<EntityManager>(relaxed = true)

    private lateinit var service: EodhdIngestionService

    private val mockRun = mockk<com.portfolio.ingestion.entity.IngestionRun>(relaxed = true)
    private val mockStep = IngestionStep(
        id = 1L,
        run = mockRun,
        stepName = StepName.EODHD_UNIVERSE,
        status = StepStatus.RUNNING
    )

    @BeforeEach
    fun setUp() {
        config.exchanges.northAmerica = listOf("US", "TO", "V")
        service = EodhdIngestionService(
            eodhdClient, stockRepository, etfRepository, trackingService, config, objectMapper, entityManager
        )
    }

    @Test
    fun `processStock finds existing stock by ticker and updates it`() {
        val existingStock = Stock(id = 1L, ticker = "AAPL", name = "Apple Inc")
        val dto = EodhdInstrumentDto(code = "AAPL", name = "Apple Inc.", type = "Common Stock", exchange = "NASDAQ", isin = null, currency = "USD", country = "USA")

        every { stockRepository.findByTickerIgnoreCase("AAPL") } returns existingStock
        every { stockRepository.save(any()) } returns existingStock
        every { stockRepository.findStaleStocks(any()) } returns emptyList()

        every { eodhdClient.getExchangeSymbols("US") } returns listOf(dto)

        val result = service.refreshUniverse(mockStep)

        assertEquals(0, result.created)
        assertEquals(1, result.updated)
        verify { stockRepository.save(existingStock) }
    }

    @Test
    fun `processStock creates new stock if ticker not found`() {
        val dto = EodhdInstrumentDto(code = "NVDA", name = "Nvidia Corp", type = "Common Stock", exchange = "NASDAQ", isin = null, currency = "USD", country = "USA")

        every { stockRepository.findByTickerIgnoreCase("NVDA") } returns null
        every { stockRepository.findByIsin(any()) } returns null
        every { stockRepository.save(any()) } answers { firstArg() }
        every { stockRepository.findStaleStocks(any()) } returns emptyList()

        every { eodhdClient.getExchangeSymbols("US") } returns listOf(dto)

        val result = service.refreshUniverse(mockStep)

        assertEquals(1, result.created)
        assertEquals(0, result.updated)
    }

    @Test
    fun `processStock skips duplicate ISIN and logs warning`() {
        val dto = EodhdInstrumentDto(code = "AAPL", name = "Apple Inc", type = "Common Stock", exchange = "NASDAQ", isin = "US0378331005", currency = "USD", country = "USA")
        val conflictingStock = Stock(id = 99L, ticker = "AAPL-CA", name = "Apple Canada")

        every { stockRepository.findByTickerIgnoreCase("AAPL") } returns null
        every { stockRepository.findByIsin("US0378331005") } returns conflictingStock
        every { stockRepository.findStaleStocks(any()) } returns emptyList()

        every { eodhdClient.getExchangeSymbols("US") } returns listOf(dto)

        val result = service.refreshUniverse(mockStep)

        assertEquals(0, result.created)
        assertEquals(0, result.updated)
        verify { trackingService.logDuplicateIsin(any(), any(), any(), "US0378331005") }
    }

    @Test
    fun `cross-listed ticker (second exchange call) updates existing record not creates duplicate`() {
        val existingStock = Stock(id = 1L, ticker = "BMO", name = "Bank of Montreal")

        // When TO exchange processes BMO, it already exists from US processing
        every { stockRepository.findByTickerIgnoreCase("BMO") } returns existingStock
        every { stockRepository.save(any()) } returns existingStock

        val dto = EodhdInstrumentDto(code = "BMO", name = "Bank of Montreal", type = "Common Stock", exchange = "TSX", isin = null, currency = "CAD", country = "CAN")
        every { eodhdClient.getExchangeSymbols("TO") } returns listOf(dto)
        every { eodhdClient.getExchangeSymbols("US") } returns emptyList()
        every { eodhdClient.getExchangeSymbols("V") } returns emptyList()
        every { stockRepository.findStaleStocks(any()) } returns emptyList()

        val result = service.refreshUniverse(mockStep)

        // Should update, not create
        assertEquals(0, result.created)
        assertEquals(1, result.updated)
        verify(exactly = 0) { stockRepository.findByIsin(any()) }
    }
}
