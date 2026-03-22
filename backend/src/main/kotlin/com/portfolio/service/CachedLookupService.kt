package com.portfolio.service

import com.portfolio.entity.EtfSectorAllocationFactset
import com.portfolio.repository.EtfSectorAllocationFactsetRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class CachedLookupService(
    private val sectorAllocationFactsetRepository: EtfSectorAllocationFactsetRepository
) {
    @Cacheable(value = ["etf-sector-allocations"], key = "#etfId")
    fun getSectorAllocations(etfId: Long): List<EtfSectorAllocationFactset> {
        return sectorAllocationFactsetRepository.findLatestByEtfId(etfId)
    }
}
