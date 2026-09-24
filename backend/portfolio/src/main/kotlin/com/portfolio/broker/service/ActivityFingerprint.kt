package com.portfolio.broker.service

import java.math.BigDecimal
import java.security.MessageDigest
import java.time.LocalDate

/**
 * Stable dedup key for broker activities that carry no broker-assigned id (Questrade).
 * The canonical string is fixed-order and unit-separator joined; decimals are normalized
 * with stripTrailingZeros so the stored DECIMAL scale never changes the hash.
 */
object ActivityFingerprint {

    private const val SEPARATOR = "\u001F"

    /**
     * Computes the fingerprint for an activity. The canonical field join below is a frozen
     * compatibility contract: fingerprints are permanent once written, and any change to the
     * canonicalization requires a new re-backfill migration, otherwise dedup breaks against
     * historical rows.
     */
    fun of(
        type: String,
        symbol: String?,
        description: String?,
        quantity: BigDecimal?,
        price: BigDecimal?,
        amount: BigDecimal,
        fee: BigDecimal?,
        currency: String?,
        tradeDate: LocalDate,
        settlementDate: LocalDate?,
        optionType: String?
    ): String {
        val canonical = listOf(
            type,
            symbol ?: "",
            description ?: "",
            decimal(quantity),
            decimal(price),
            decimal(amount),
            decimal(fee),
            currency ?: "",
            tradeDate.toString(),
            settlementDate?.toString() ?: "",
            optionType ?: ""
        ).joinToString(SEPARATOR)
        return MessageDigest.getInstance("SHA-256")
            .digest(canonical.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun decimal(value: BigDecimal?): String =
        value?.stripTrailingZeros()?.toPlainString() ?: ""
}
