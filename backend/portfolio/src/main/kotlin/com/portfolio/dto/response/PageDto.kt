package com.portfolio.dto.response

import org.springframework.data.domain.Page

data class PageMetaDto(
    val page: Int,
    val size: Int,
    val totalElements: Long,
    val totalPages: Int
)

data class PagedResponseDto<T>(
    val data: List<T>,
    val meta: PageMetaDto
) {
    companion object {
        fun <T, R> from(page: Page<T>, mapper: (T) -> R): PagedResponseDto<R> {
            return PagedResponseDto(
                data = page.content.map(mapper),
                meta = PageMetaDto(
                    page = page.number,
                    size = page.size,
                    totalElements = page.totalElements,
                    totalPages = page.totalPages
                )
            )
        }
    }
}
