package com.portfolio.broker.controller

import com.portfolio.auth.repository.UserRepository
import com.portfolio.auth.security.UserPrincipal
import com.portfolio.broker.dto.*
import com.portfolio.broker.service.OrderExecutionService
import org.slf4j.LoggerFactory
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.web.bind.annotation.*
import java.util.UUID

@RestController
@RequestMapping("/api/v1/trading")
@PreAuthorize("isAuthenticated()")
class TradingController(
    private val orderExecutionService: OrderExecutionService,
    private val userRepository: UserRepository
) {
    private val log = LoggerFactory.getLogger(javaClass)

    @PostMapping("/execute")
    fun executeTrades(
        @RequestBody request: ExecuteTradesRequest,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<ExecuteTradesResponse> {
        val user = userRepository.findById(principal.id).orElseThrow {
            IllegalArgumentException("User not found")
        }
        val response = orderExecutionService.executeTradesForGroup(user, request)
        return ResponseEntity.status(HttpStatus.CREATED).body(response)
    }

    @PostMapping("/groups/{groupId}/execute-single")
    fun executeSingleTrade(
        @PathVariable groupId: Long,
        @RequestBody tradeInput: TradeExecutionInput,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<TradeOrderDto> {
        val user = userRepository.findById(principal.id).orElseThrow {
            IllegalArgumentException("User not found")
        }
        val order = orderExecutionService.executeSingleTrade(user, groupId, tradeInput)
        return ResponseEntity.status(HttpStatus.CREATED).body(order)
    }

    @GetMapping("/groups/{groupId}/orders")
    fun getGroupOrders(
        @PathVariable groupId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<OrderStatusResponse> {
        val response = orderExecutionService.getOrdersForGroup(principal.id, groupId)
        return ResponseEntity.ok(response)
    }

    @GetMapping("/batches/{batchId}")
    fun getBatchOrders(
        @PathVariable batchId: UUID,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<OrderStatusResponse> {
        val response = orderExecutionService.getOrdersForBatch(principal.id, batchId)
        return ResponseEntity.ok(response)
    }

    @PostMapping("/orders/{orderId}/cancel")
    fun cancelOrder(
        @PathVariable orderId: Long,
        @AuthenticationPrincipal principal: UserPrincipal
    ): ResponseEntity<TradeOrderDto> {
        val user = userRepository.findById(principal.id).orElseThrow {
            IllegalArgumentException("User not found")
        }
        val order = orderExecutionService.cancelOrder(user, orderId)
        return ResponseEntity.ok(order)
    }

}
