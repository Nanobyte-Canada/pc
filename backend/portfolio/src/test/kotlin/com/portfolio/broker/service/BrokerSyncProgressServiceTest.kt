package com.portfolio.broker.service

import com.portfolio.broker.entity.BrokerSyncProgress
import com.portfolio.broker.repository.BrokerSyncProgressRepository
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import kotlin.test.assertNull
import org.junit.jupiter.api.BeforeEach
import org.junit.jupiter.api.Test
import java.time.LocalDate

class BrokerSyncProgressServiceTest {

    private val repo = mockk<BrokerSyncProgressRepository>(relaxed = true)
    private val service = BrokerSyncProgressService(repo)

    @BeforeEach
    fun setup() {
        // JpaRepository.save() has generic signature <S extends T> S save(S); a relaxed mock
        // can't resolve it and returns Object(), so answer with the argument (as
        // ActivityIngestionServiceTest does for BrokerConnection).
        every { repo.save(any<BrokerSyncProgress>()) } answers { firstArg() }
    }

    @Test
    fun `advance upserts the next chunk end`() {
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns null
        service.advance(7L, "ACTIVITIES_FULL", LocalDate.of(2026, 9, 1))
        verify {
            repo.save(match<BrokerSyncProgress> {
                it.connectionId == 7L && it.syncKind == "ACTIVITIES_FULL" &&
                    it.nextChunkEnd == LocalDate.of(2026, 9, 1)
            })
        }
    }

    @Test
    fun `get returns null when no progress row exists`() {
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns null
        assertNull(service.get(7L, "ACTIVITIES_FULL"))
    }

    @Test
    fun `clear deletes the row`() {
        val row = BrokerSyncProgress(connectionId = 7L, syncKind = "ACTIVITIES_FULL", nextChunkEnd = LocalDate.now())
        every { repo.findByConnectionIdAndSyncKind(7L, "ACTIVITIES_FULL") } returns row
        service.clear(7L, "ACTIVITIES_FULL")
        verify { repo.delete(row) }
    }
}
