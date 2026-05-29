package com.portfolio.broker.entity

import com.portfolio.auth.entity.User
import jakarta.persistence.*
import java.math.BigDecimal
import java.time.OffsetDateTime
import java.util.UUID

enum class OrderStatus {
    PENDING, SUBMITTED, FILLED, PARTIALLY_FILLED, REJECTED, CANCELLED, FAILED
}

enum class OrderAction {
    BUY, SELL
}

enum class OrderType {
    MARKET, LIMIT
}

enum class TimeInForce {
    DAY, GTC
}

@Entity
@Table(name = "trade_orders")
class TradeOrder(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    val user: User,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "group_id", nullable = true)
    val group: PortfolioGroup? = null,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @Column(name = "batch_id")
    val batchId: UUID? = null,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Enumerated(EnumType.STRING)
    @Column(name = "action", nullable = false, length = 4)
    val action: OrderAction,

    @Enumerated(EnumType.STRING)
    @Column(name = "order_type", nullable = false, length = 10)
    var orderType: OrderType = OrderType.MARKET,

    @Enumerated(EnumType.STRING)
    @Column(name = "time_in_force", nullable = false, length = 3)
    var timeInForce: TimeInForce = TimeInForce.DAY,

    @Column(name = "requested_units", nullable = false, precision = 18, scale = 6)
    val requestedUnits: BigDecimal,

    @Column(name = "requested_price", nullable = false, precision = 18, scale = 6)
    val requestedPrice: BigDecimal,

    @Column(name = "requested_amount", nullable = false, precision = 18, scale = 2)
    val requestedAmount: BigDecimal,

    @Column(name = "limit_price", precision = 18, scale = 6)
    var limitPrice: BigDecimal? = null,

    @Column(name = "option_type")
    var optionType: String? = null,

    @Column(name = "strike_price")
    var strikePrice: BigDecimal? = null,

    @Column(name = "expiration_date")
    var expirationDate: java.time.LocalDate? = null,

    @Column(name = "symbol_id")
    var symbolId: Long? = null,

    @Column(name = "stop_price")
    var stopPrice: BigDecimal? = null,

    @Column(name = "filled_units", precision = 18, scale = 6)
    var filledUnits: BigDecimal? = null,

    @Column(name = "filled_price", precision = 18, scale = 6)
    var filledPrice: BigDecimal? = null,

    @Column(name = "filled_amount", precision = 18, scale = 2)
    var filledAmount: BigDecimal? = null,

    @Column(name = "currency", nullable = false, length = 3)
    val currency: String = "CAD",

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    var status: OrderStatus = OrderStatus.PENDING,

    @Column(name = "broker_order_id", length = 255)
    var brokerOrderId: String? = null,

    @Column(name = "account_id_external", length = 100)
    var accountIdExternal: String? = null,

    @Column(name = "error_message", columnDefinition = "TEXT")
    var errorMessage: String? = null,

    @Column(name = "error_code", length = 50)
    var errorCode: String? = null,

    @Column(name = "submitted_at")
    var submittedAt: OffsetDateTime? = null,

    @Column(name = "filled_at")
    var filledAt: OffsetDateTime? = null,

    @Column(name = "cancelled_at")
    var cancelledAt: OffsetDateTime? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now(),

    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now()
)
