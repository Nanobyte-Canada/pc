package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.Quote
import com.portfolio.marketdata.api.dto.QuoteResponse
import com.portfolio.marketdata.db.repository.UnderlyingPriceRepository
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.IbkrClient
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.web.bind.annotation.GetMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RestController
import java.math.BigDecimal
import java.time.Instant

@RestController
@RequestMapping("/api/v1/quotes")
class QuoteController(
    private val quoteCacheService: QuoteCacheService,
    private val underlyingPriceRepository: UnderlyingPriceRepository,
    private val ibkrClient: IbkrClient
) {

    private val logger = LoggerFactory.getLogger(javaClass)

    @GetMapping("/{symbol}")
    fun getQuote(@PathVariable symbol: String): ResponseEntity<QuoteResponse> {
        val cachedQuote = quoteCacheService.getQuote(symbol)
        if (cachedQuote != null) {
            return ResponseEntity.ok(QuoteResponse.fromDomain(cachedQuote))
        }

        val prices = underlyingPriceRepository.findByTickerOrderByObservedAtDesc(symbol)
        val latestPrice = prices.firstOrNull()

        val quote = if (latestPrice != null) {
            Quote(
                symbol = latestPrice.ticker, bid = latestPrice.price, ask = latestPrice.price,
                last = latestPrice.price, volume = latestPrice.volume ?: 0L, timestamp = latestPrice.observedAt
            )
        } else if (!ibkrClient.isConnected()) {
            logger.warn("IBKR not connected, cannot fetch quote for {}", symbol)
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).build()
        } else {
            val contracts = ibkrClient.requestContractDetails(symbol, "STK")
            val conId = contracts.firstOrNull()?.conId
                ?: return ResponseEntity.notFound().build()
            val snapshot = ibkrClient.requestMarketDataSnapshot(conId)
                ?: return ResponseEntity.notFound().build()
            val price = snapshot.last ?: snapshot.bid ?: snapshot.ask ?: return ResponseEntity.notFound().build()
            Quote(
                symbol = symbol,
                bid = BigDecimal.valueOf(price),
                ask = BigDecimal.valueOf(snapshot.ask ?: price),
                last = BigDecimal.valueOf(price),
                volume = snapshot.volume ?: 0L,
                timestamp = Instant.now()
            )
        }

        quoteCacheService.cacheQuote(quote)
        return ResponseEntity.ok(QuoteResponse.fromDomain(quote))
    }
}
