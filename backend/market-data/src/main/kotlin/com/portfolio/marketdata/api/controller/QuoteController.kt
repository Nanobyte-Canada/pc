package com.portfolio.marketdata.api.controller

import com.portfolio.common.domain.Quote
import com.portfolio.marketdata.api.dto.QuoteResponse
import com.portfolio.marketdata.db.repository.UnderlyingPriceRepository
import com.portfolio.marketdata.distribution.QuoteCacheService
import com.portfolio.marketdata.ibkr.FakeIbkrClient
import com.portfolio.marketdata.ibkr.IbkrClient
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
        } else {
            val spot = (ibkrClient as? FakeIbkrClient)?.getSpotPrice(symbol)
                ?: return ResponseEntity.notFound().build()
            val price = BigDecimal.valueOf(spot)
            Quote(symbol = symbol, bid = price, ask = price, last = price, volume = 50_000_000L, timestamp = Instant.now())
        }

        quoteCacheService.cacheQuote(quote)
        return ResponseEntity.ok(QuoteResponse.fromDomain(quote))
    }
}
