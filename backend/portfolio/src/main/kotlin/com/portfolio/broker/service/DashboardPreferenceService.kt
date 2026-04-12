package com.portfolio.broker.service

import com.portfolio.auth.repository.UserRepository
import com.portfolio.broker.dto.DashboardPreferencesResponse
import com.portfolio.broker.dto.UpdateDashboardPreferencesRequest
import com.portfolio.broker.dto.WidgetPreferenceDto
import com.portfolio.broker.entity.DashboardContextType
import com.portfolio.broker.entity.DashboardPreference
import com.portfolio.broker.entity.WidgetKey
import com.portfolio.broker.repository.DashboardPreferenceRepository
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.time.OffsetDateTime

@Service
class DashboardPreferenceService(
    private val preferenceRepository: DashboardPreferenceRepository,
    private val userRepository: UserRepository
) {
    companion object {
        // No backend defaults — frontend WidgetRegistry.ts is the source of truth
    }

    fun getPreferences(
        userId: Long,
        contextType: DashboardContextType = DashboardContextType.DASHBOARD,
        contextId: Long? = null
    ): DashboardPreferencesResponse {
        val prefs = if (contextId != null) {
            preferenceRepository.findByUserIdAndContextTypeAndContextIdOrderBySortOrder(userId, contextType, contextId)
        } else {
            preferenceRepository.findByUserIdAndContextTypeAndContextIdIsNullOrderBySortOrder(userId, contextType)
        }

        if (prefs.isEmpty()) {
            return DashboardPreferencesResponse(widgets = emptyList())
        }

        return DashboardPreferencesResponse(
            widgets = prefs.map { p ->
                WidgetPreferenceDto(
                    key = p.widgetKey.name,
                    visible = p.isVisible,
                    sortOrder = p.sortOrder,
                    columnSpan = p.columnSpan
                )
            }
        )
    }

    @Transactional
    fun updatePreferences(
        userId: Long,
        request: UpdateDashboardPreferencesRequest,
        contextType: DashboardContextType = DashboardContextType.DASHBOARD,
        contextId: Long? = null
    ): DashboardPreferencesResponse {
        val user = userRepository.findById(userId).orElseThrow { IllegalArgumentException("User not found") }

        // Delete existing prefs for this context
        if (contextId != null) {
            preferenceRepository.deleteByUserIdAndContextTypeAndContextId(userId, contextType, contextId)
        } else {
            preferenceRepository.deleteByUserIdAndContextTypeAndContextIdIsNull(userId, contextType)
        }
        // Flush DELETEs to DB before INSERTs — Hibernate's ActionQueue processes
        // INSERTs before DELETEs, which would violate the unique constraint.
        preferenceRepository.flush()

        // Create new prefs
        val prefs = request.widgets.mapNotNull { w ->
            val widgetKey = try { WidgetKey.valueOf(w.key) } catch (_: IllegalArgumentException) { return@mapNotNull null }
            DashboardPreference(
                user = user,
                contextType = contextType,
                contextId = contextId,
                widgetKey = widgetKey,
                isVisible = w.visible,
                sortOrder = w.sortOrder,
                columnSpan = w.columnSpan
            )
        }

        preferenceRepository.saveAll(prefs)
        return getPreferences(userId, contextType, contextId)
    }

    @Transactional
    fun resetPreferences(
        userId: Long,
        contextType: DashboardContextType = DashboardContextType.DASHBOARD,
        contextId: Long? = null
    ): DashboardPreferencesResponse {
        if (contextId != null) {
            preferenceRepository.deleteByUserIdAndContextTypeAndContextId(userId, contextType, contextId)
        } else {
            preferenceRepository.deleteByUserIdAndContextTypeAndContextIdIsNull(userId, contextType)
        }
        return DashboardPreferencesResponse(widgets = emptyList())
    }
}
