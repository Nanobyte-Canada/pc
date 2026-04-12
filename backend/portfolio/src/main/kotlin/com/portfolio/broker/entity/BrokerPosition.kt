package com.portfolio.broker.entity

import jakarta.persistence.*
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.math.BigDecimal
import java.time.LocalDate
import java.time.OffsetDateTime

enum class InstrumentType {
    STOCK, ETF, MUTUAL_FUND, OPTION, BOND, CASH, OTHER
}

@Entity
@Table(name = "broker_positions")
class BrokerPosition(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    val id: Long = 0,

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "connection_id", nullable = false)
    val connection: BrokerConnection,

    @Column(name = "symbol", nullable = false, length = 20)
    val symbol: String,

    @Column(name = "symbol_id_external", length = 50)
    val symbolIdExternal: String? = null,

    @Enumerated(EnumType.STRING)
    @Column(name = "instrument_type", length = 20)
    val instrumentType: InstrumentType? = null,

    @Column(name = "security_name", length = 255)
    val securityName: String? = null,

    @Column(name = "quantity", nullable = false, precision = 18, scale = 6)
    val quantity: BigDecimal,

    @Column(name = "average_cost", precision = 18, scale = 6)
    val averageCost: BigDecimal? = null,

    @Column(name = "current_price", precision = 18, scale = 6)
    val currentPrice: BigDecimal? = null,

    @Column(name = "current_value", precision = 18, scale = 2)
    val currentValue: BigDecimal? = null,

    @Column(name = "day_pnl", precision = 18, scale = 2)
    val dayPnl: BigDecimal? = null,

    @Column(name = "total_pnl", precision = 18, scale = 2)
    val totalPnl: BigDecimal? = null,

    @Column(name = "total_pnl_percent", precision = 10, scale = 4)
    val totalPnlPercent: BigDecimal? = null,

    @Column(name = "currency", length = 3)
    val currency: String = "CAD",

    @Column(name = "as_of_date", nullable = false)
    val asOfDate: LocalDate,

    @Column(name = "as_of_timestamp")
    val asOfTimestamp: OffsetDateTime? = null,

    @Column(name = "strike_price", precision = 18, scale = 6)
    var strikePrice: BigDecimal? = null,

    @Column(name = "expiration_date")
    var expirationDate: LocalDate? = null,

    @Column(name = "option_type", length = 10)
    var optionType: String? = null,

    @Column(name = "underlying_symbol", length = 20)
    var underlyingSymbol: String? = null,

    @Column(name = "is_current", nullable = false)
    var isCurrent: Boolean = true,

    @Column(name = "raw_payload", columnDefinition = "jsonb")
    @JdbcTypeCode(SqlTypes.JSON)
    val rawPayload: String? = null,

    @Column(name = "created_at", nullable = false, updatable = false)
    val createdAt: OffsetDateTime = OffsetDateTime.now()
)
