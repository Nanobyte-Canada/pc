package com.portfolio.service

import com.portfolio.dto.response.CountryDto
import com.portfolio.dto.response.ExchangeDto
import com.portfolio.dto.response.GicsSectorDto
import com.portfolio.dto.response.GicsSectorSimpleDto
import com.portfolio.dto.response.RegionDto
import com.portfolio.repository.CountryRepository
import com.portfolio.repository.GicsSectorRepository
import com.portfolio.repository.RegionRepository
import com.portfolio.repository.StockRepository
import org.springframework.cache.annotation.Cacheable
import org.springframework.stereotype.Service

@Service
class ReferenceDataService(
    private val gicsSectorRepository: GicsSectorRepository,
    private val stockRepository: StockRepository,
    private val regionRepository: RegionRepository,
    private val countryRepository: CountryRepository
) {
    @Cacheable("gics-hierarchy")
    fun getGicsHierarchy(): List<GicsSectorDto> {
        return gicsSectorRepository.findAllWithHierarchy()
            .distinctBy { it.code }
            .sortedBy { it.code }
            .map { GicsSectorDto.from(it) }
    }

    @Cacheable("gics-sectors")
    fun getGicsSectors(): List<GicsSectorSimpleDto> {
        return gicsSectorRepository.findAll()
            .sortedBy { it.code }
            .map { GicsSectorSimpleDto.from(it) }
    }

    @Cacheable("countries")
    fun getCountries(): List<CountryDto> {
        return countryRepository.findAllWithRegion()
            .map { CountryDto.from(it) }
    }

    @Cacheable("regions")
    fun getRegions(): List<RegionDto> {
        return regionRepository.findAllWithCountries()
            .distinctBy { it.code }
            .map { RegionDto.from(it, includeCountries = true) }
    }

    @Cacheable("regions-simple")
    fun getRegionsSimple(): List<RegionDto> {
        return regionRepository.findAll()
            .sortedBy { it.name }
            .map { RegionDto.from(it, includeCountries = false) }
    }

    fun getCountriesByRegion(regionCode: String): List<CountryDto> {
        return countryRepository.findByRegionCode(regionCode)
            .map { CountryDto.from(it) }
    }

    /**
     * Get the region for a given country code.
     * Returns "OTHER" region if country is not found.
     */
    @Cacheable("country-region-map")
    fun getCountryToRegionMap(): Map<String, String> {
        return countryRepository.findAllWithRegion()
            .associate { it.code to it.region.name }
    }

    /**
     * Get the country name for a given country code.
     * Returns the code itself if not found.
     */
    @Cacheable("country-name-map")
    fun getCountryNameMap(): Map<String, String> {
        return countryRepository.findAll()
            .associate { it.code to it.name }
    }

    /**
     * Get region name for a country code.
     * Returns "Other" if the country is not found in the database.
     */
    fun getRegionForCountry(countryCode: String): String {
        val country = countryRepository.findByCodeWithRegion(countryCode)
        return country?.region?.name ?: "Other"
    }

    /**
     * Get country name for a country code.
     * Returns the code itself if not found.
     */
    fun getCountryName(countryCode: String): String {
        return countryRepository.findByCode(countryCode)?.name ?: countryCode
    }

    @Cacheable("exchanges")
    fun getExchanges(): List<ExchangeDto> {
        // Common exchanges - could be moved to database in the future
        return listOf(
            ExchangeDto("NYSE", "New York Stock Exchange"),
            ExchangeDto("NASDAQ", "NASDAQ"),
            ExchangeDto("AMEX", "NYSE American"),
            ExchangeDto("ARCA", "NYSE Arca"),
            ExchangeDto("BATS", "BATS Global Markets"),
            ExchangeDto("LSE", "London Stock Exchange"),
            ExchangeDto("TSE", "Tokyo Stock Exchange"),
            ExchangeDto("HKEX", "Hong Kong Stock Exchange"),
            ExchangeDto("SSE", "Shanghai Stock Exchange"),
            ExchangeDto("SZSE", "Shenzhen Stock Exchange")
        )
    }
}
