package com.portfolio.broker.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.IdClass
import jakarta.persistence.Table
import java.io.Serializable
import java.time.LocalDate
import java.time.OffsetDateTime

class BrokerSyncProgressId(
    val connectionId: Long = 0,
    val syncKind: String = "",
) : Serializable {
    override fun equals(other: Any?) = other is BrokerSyncProgressId &&
        other.connectionId == connectionId && other.syncKind == syncKind
    override fun hashCode() = 31 * connectionId.hashCode() + syncKind.hashCode()
}

@Entity
@IdClass(BrokerSyncProgressId::class)
@Table(name = "broker_sync_progress")
class BrokerSyncProgress(
    @Id
    @Column(name = "connection_id")
    val connectionId: Long,
    @Id
    @Column(name = "sync_kind")
    val syncKind: String,
    @Column(name = "next_chunk_end", nullable = false)
    var nextChunkEnd: LocalDate,
    @Column(name = "updated_at", nullable = false)
    var updatedAt: OffsetDateTime = OffsetDateTime.now(),
)
