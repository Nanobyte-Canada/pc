package com.portfolio.dto.response

import com.portfolio.entity.gics.GicsIndustry
import com.portfolio.entity.gics.GicsIndustryGroup
import com.portfolio.entity.gics.GicsSector
import com.portfolio.entity.gics.GicsSubIndustry

data class GicsSubIndustryDto(
    val code: String,
    val name: String
) {
    companion object {
        fun from(entity: GicsSubIndustry): GicsSubIndustryDto {
            return GicsSubIndustryDto(
                code = entity.code,
                name = entity.name
            )
        }
    }
}

data class GicsIndustryDto(
    val code: String,
    val name: String,
    val subIndustries: List<GicsSubIndustryDto>
) {
    companion object {
        fun from(entity: GicsIndustry): GicsIndustryDto {
            return GicsIndustryDto(
                code = entity.code,
                name = entity.name,
                subIndustries = entity.subIndustries.map { GicsSubIndustryDto.from(it) }
            )
        }
    }
}

data class GicsIndustryGroupDto(
    val code: String,
    val name: String,
    val industries: List<GicsIndustryDto>
) {
    companion object {
        fun from(entity: GicsIndustryGroup): GicsIndustryGroupDto {
            return GicsIndustryGroupDto(
                code = entity.code,
                name = entity.name,
                industries = entity.industries.map { GicsIndustryDto.from(it) }
            )
        }
    }
}

data class GicsSectorDto(
    val code: String,
    val name: String,
    val industryGroups: List<GicsIndustryGroupDto>
) {
    companion object {
        fun from(entity: GicsSector): GicsSectorDto {
            return GicsSectorDto(
                code = entity.code,
                name = entity.name,
                industryGroups = entity.industryGroups.map { GicsIndustryGroupDto.from(it) }
            )
        }
    }
}

data class GicsSectorSimpleDto(
    val code: String,
    val name: String
) {
    companion object {
        fun from(entity: GicsSector): GicsSectorSimpleDto {
            return GicsSectorSimpleDto(
                code = entity.code,
                name = entity.name
            )
        }
    }
}

data class ExchangeDto(
    val code: String,
    val name: String
)

data class CountryDto(
    val code: String,
    val name: String,
    val regionCode: String? = null,
    val regionName: String? = null
) {
    companion object {
        fun from(entity: com.portfolio.entity.Country): CountryDto {
            return CountryDto(
                code = entity.code,
                name = entity.name,
                regionCode = entity.region.code,
                regionName = entity.region.name
            )
        }
    }
}

data class RegionDto(
    val code: String,
    val name: String,
    val countries: List<CountryDto> = emptyList()
) {
    companion object {
        fun from(entity: com.portfolio.entity.Region, includeCountries: Boolean = false): RegionDto {
            return RegionDto(
                code = entity.code,
                name = entity.name,
                countries = if (includeCountries) {
                    entity.countries.map { CountryDto(it.code, it.name, entity.code, entity.name) }
                } else {
                    emptyList()
                }
            )
        }
    }
}
