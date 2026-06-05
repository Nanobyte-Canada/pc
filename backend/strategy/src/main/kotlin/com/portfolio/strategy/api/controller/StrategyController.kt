package com.portfolio.strategy.api.controller

import com.portfolio.common.domain.OptionType
import com.portfolio.strategy.api.dto.*
import com.portfolio.strategy.engine.*
import com.portfolio.strategy.model.Leg
import com.portfolio.strategy.model.LegAction
import com.portfolio.strategy.model.StrategyType
import org.springframework.http.HttpStatus
import org.springframework.web.bind.annotation.*
import org.springframework.web.server.ResponseStatusException
import java.time.LocalDate

@RestController
@RequestMapping("/api/v1/strategies")
class StrategyController(
    private val registry: StrategyRegistry,
    private val calculator: StrategyCalculator,
    private val educationEngine: EducationEngine,
    private val legValidator: LegValidator
) {

    @GetMapping
    fun listStrategies(): List<StrategyListResponse> {
        return registry.listAll().map { d ->
            StrategyListResponse(d.type.name, d.displayName, d.description, d.outlook, d.riskProfile, d.legCount)
        }
    }

    @GetMapping("/{name}")
    fun getStrategyInfo(@PathVariable name: String): StrategyInfoResponse {
        val strategyType = try { StrategyType.valueOf(name) } catch (e: IllegalArgumentException) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid strategy name: $name")
        }
        val definition = registry.getDefinition(strategyType)
        val education = educationEngine.getContent(strategyType)
        return StrategyInfoResponse(definition.type.name, definition.displayName, definition.description,
            definition.outlook, definition.riskProfile, definition.legCount, education)
    }

    @PostMapping("/calculate")
    fun calculate(@RequestBody request: CalculateRequest): CalculateResponse {
        val legs = request.legs.map { lr ->
            val action = try { LegAction.valueOf(lr.action.uppercase()) } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid action: ${lr.action}")
            }
            val optionType = lr.optionType?.let { try { OptionType.valueOf(it.uppercase()) } catch (e: IllegalArgumentException) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid option type: $it")
            }}
            val expiry = lr.expiry?.let { try { LocalDate.parse(it) } catch (e: Exception) {
                throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid expiry: $it")
            }}
            Leg(action = action, optionType = optionType, strike = lr.strike, expiry = expiry,
                quantity = lr.quantity, bid = lr.bid, ask = lr.ask, mid = lr.mid, delta = lr.delta)
        }

        val validation = legValidator.validate(legs)
        if (!validation.valid) {
            throw ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid legs: ${validation.errors.joinToString(", ")}")
        }

        val result = calculator.calculate(legs, request.spotPrice)
        val warnings = educationEngine.generateWarnings(legs, request.spotPrice)

        return CalculateResponse(
            netDebitCredit = result.netDebitCredit, maxProfit = result.maxProfit, maxLoss = result.maxLoss,
            breakEvenPrices = result.breakEvenPrices, riskRewardRatio = result.riskRewardRatio,
            probabilityOfProfit = result.probabilityOfProfit,
            pnlCurve = result.pnlCurve.map { PnlPointDto(it.underlyingPrice, it.pnl) },
            netGreeks = NetGreeksDto(result.netGreeks.delta, result.netGreeks.gamma, result.netGreeks.theta, result.netGreeks.vega),
            warnings = warnings
        )
    }

    @PostMapping("/suggest")
    fun suggest(@RequestBody request: SuggestRequest): List<StrategyListResponse> {
        val outlook = request.outlook.lowercase()
        return registry.listAll().filter { d ->
            when (outlook) {
                "bullish" -> d.outlook.contains("Bullish", ignoreCase = true)
                "bearish" -> d.outlook.contains("Bearish", ignoreCase = true)
                "neutral" -> d.outlook.contains("Neutral", ignoreCase = true) || d.outlook.contains("Range-Bound", ignoreCase = true)
                else -> false
            }
        }.map { d -> StrategyListResponse(d.type.name, d.displayName, d.description, d.outlook, d.riskProfile, d.legCount) }
    }
}
