package com.portfolio.broker.service

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.repository.BrokerSyncProgressRepository
import org.springframework.stereotype.Service
import java.time.LocalDate
import java.time.OffsetDateTime

@Service
class BrokerSyncProgressService(private val repo: BrokerSyncProgressRepository) {

    companion object { const val ACTIVITIES_FULL = "ACTIVITIES_FULL" }

    fun get(connectionId: Long, kind: String): BrokerSyncProgress? =
        repo.findByConnectionIdAndSyncKind(connectionId, kind)

    fun advance(connectionId: Long, kind: String, nextChunkEnd: LocalDate) {
        val row = repo.findByConnectionIdAndSyncKind(connectionId, kind)
            ?: BrokerSyncProgress(connectionId = connectionId, syncKind = kind, nextChunkEnd = nextChunkEnd)
        row.nextChunkEnd = nextChunkEnd
        row.updatedAt = OffsetDateTime.now()
        repo.save(row)
    }

    fun clear(connectionId: Long, kind: String) {
        repo.findByConnectionIdAndSyncKind(connectionId, kind)?.let { repo.delete(it) }
    }
}
