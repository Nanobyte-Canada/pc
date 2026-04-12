package com.portfolio.service

import org.springframework.stereotype.Service

/**
 * Static country/region lookup service that replaces the old Country/Region JPA entities
 * and CountryRepository/RegionRepository. Provides the same country-to-region mapping
 * that was previously seeded in the countries/regions database tables (V23 migration).
 *
 * Supports both ISO 3166-1 alpha-2 (US, CA, GB) and alpha-3 (USA, CAN, GBR) codes.
 */
@Service
class CountryRegionLookupService {

    data class CountryInfo(
        val code: String,
        val name: String,
        val regionName: String
    )

    /**
     * Get the region name for a given country code.
     * Accepts both alpha-2 and alpha-3 codes (case-insensitive).
     * Returns "Other" if the country is not found.
     */
    fun getRegionForCountry(countryCode: String): String {
        val normalized = countryCode.uppercase()
        return COUNTRY_MAP[normalized]?.regionName ?: "Other"
    }

    /**
     * Get the country name for a given country code.
     * Accepts both alpha-2 and alpha-3 codes (case-insensitive).
     * Returns the code itself if not found.
     */
    fun getCountryName(countryCode: String): String {
        val normalized = countryCode.uppercase()
        return COUNTRY_MAP[normalized]?.name ?: countryCode
    }

    /**
     * Get full country info for a given country code.
     * Accepts both alpha-2 and alpha-3 codes (case-insensitive).
     * Returns null if not found.
     */
    fun getCountryInfo(countryCode: String): CountryInfo? {
        val normalized = countryCode.uppercase()
        return COUNTRY_MAP[normalized]
    }

    /**
     * Get a map of all country codes to their region names.
     */
    fun getCountryToRegionMap(): Map<String, String> = COUNTRY_TO_REGION

    /**
     * Get a map of all country codes to their display names.
     */
    fun getCountryNameMap(): Map<String, String> = COUNTRY_TO_NAME

    companion object {
        // Master list: alpha-2, alpha-3, name, region
        private data class CountryEntry(
            val alpha2: String,
            val alpha3: String,
            val name: String,
            val region: String
        )

        private val COUNTRIES = listOf(
            // North America
            CountryEntry("US", "USA", "United States", "North America"),
            CountryEntry("CA", "CAN", "Canada", "North America"),

            // Europe
            CountryEntry("GB", "GBR", "United Kingdom", "Europe"),
            CountryEntry("DE", "DEU", "Germany", "Europe"),
            CountryEntry("FR", "FRA", "France", "Europe"),
            CountryEntry("CH", "CHE", "Switzerland", "Europe"),
            CountryEntry("NL", "NLD", "Netherlands", "Europe"),
            CountryEntry("IT", "ITA", "Italy", "Europe"),
            CountryEntry("ES", "ESP", "Spain", "Europe"),
            CountryEntry("SE", "SWE", "Sweden", "Europe"),
            CountryEntry("NO", "NOR", "Norway", "Europe"),
            CountryEntry("DK", "DNK", "Denmark", "Europe"),
            CountryEntry("FI", "FIN", "Finland", "Europe"),
            CountryEntry("BE", "BEL", "Belgium", "Europe"),
            CountryEntry("AT", "AUT", "Austria", "Europe"),
            CountryEntry("IE", "IRL", "Ireland", "Europe"),
            CountryEntry("PT", "PRT", "Portugal", "Europe"),
            CountryEntry("PL", "POL", "Poland", "Europe"),
            CountryEntry("GR", "GRC", "Greece", "Europe"),
            CountryEntry("CZ", "CZE", "Czech Republic", "Europe"),
            CountryEntry("HU", "HUN", "Hungary", "Europe"),
            CountryEntry("RU", "RUS", "Russia", "Europe"),
            CountryEntry("LU", "LUX", "Luxembourg", "Europe"),

            // Asia Pacific
            CountryEntry("CN", "CHN", "China", "Asia Pacific"),
            CountryEntry("JP", "JPN", "Japan", "Asia Pacific"),
            CountryEntry("KR", "KOR", "South Korea", "Asia Pacific"),
            CountryEntry("TW", "TWN", "Taiwan", "Asia Pacific"),
            CountryEntry("IN", "IND", "India", "Asia Pacific"),
            CountryEntry("AU", "AUS", "Australia", "Asia Pacific"),
            CountryEntry("HK", "HKG", "Hong Kong", "Asia Pacific"),
            CountryEntry("SG", "SGP", "Singapore", "Asia Pacific"),
            CountryEntry("NZ", "NZL", "New Zealand", "Asia Pacific"),
            CountryEntry("TH", "THA", "Thailand", "Asia Pacific"),
            CountryEntry("MY", "MYS", "Malaysia", "Asia Pacific"),
            CountryEntry("ID", "IDN", "Indonesia", "Asia Pacific"),
            CountryEntry("PH", "PHL", "Philippines", "Asia Pacific"),
            CountryEntry("VN", "VNM", "Vietnam", "Asia Pacific"),
            CountryEntry("PK", "PAK", "Pakistan", "Asia Pacific"),

            // Latin America
            CountryEntry("BR", "BRA", "Brazil", "Latin America"),
            CountryEntry("MX", "MEX", "Mexico", "Latin America"),
            CountryEntry("AR", "ARG", "Argentina", "Latin America"),
            CountryEntry("CL", "CHL", "Chile", "Latin America"),
            CountryEntry("CO", "COL", "Colombia", "Latin America"),
            CountryEntry("PE", "PER", "Peru", "Latin America"),

            // Middle East & Africa
            CountryEntry("ZA", "ZAF", "South Africa", "Middle East & Africa"),
            CountryEntry("IL", "ISR", "Israel", "Middle East & Africa"),
            CountryEntry("SA", "SAU", "Saudi Arabia", "Middle East & Africa"),
            CountryEntry("AE", "ARE", "United Arab Emirates", "Middle East & Africa"),
            CountryEntry("QA", "QAT", "Qatar", "Middle East & Africa"),
            CountryEntry("KW", "KWT", "Kuwait", "Middle East & Africa"),
            CountryEntry("EG", "EGY", "Egypt", "Middle East & Africa"),
            CountryEntry("NG", "NGA", "Nigeria", "Middle East & Africa"),
            CountryEntry("TR", "TUR", "Turkey", "Middle East & Africa")
        )

        /** Lookup map keyed by both alpha-2 and alpha-3 codes */
        private val COUNTRY_MAP: Map<String, CountryInfo> = buildMap {
            for (entry in COUNTRIES) {
                val info = CountryInfo(entry.alpha2, entry.name, entry.region)
                put(entry.alpha2, info)
                put(entry.alpha3, info)
            }
        }

        /** Country code -> region name (both alpha-2 and alpha-3 keys) */
        private val COUNTRY_TO_REGION: Map<String, String> = COUNTRY_MAP.mapValues { it.value.regionName }

        /** Country code -> display name (both alpha-2 and alpha-3 keys) */
        private val COUNTRY_TO_NAME: Map<String, String> = COUNTRY_MAP.mapValues { it.value.name }
    }
}
