package com.portfolio.broker.repository

import com.portfolio.broker.entity.DashboardContextType
import com.portfolio.broker.entity.DashboardPreference
import com.portfolio.broker.entity.WidgetKey
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.stereotype.Repository

@Repository
interface DashboardPreferenceRepository : JpaRepository<DashboardPreference, Long> {

    fun findByUserIdAndContextTypeAndContextIdIsNullOrderBySortOrder(
        userId: Long, contextType: DashboardContextType
    ): List<DashboardPreference>

    fun findByUserIdAndContextTypeAndContextIdOrderBySortOrder(
        userId: Long, contextType: DashboardContextType, contextId: Long
    ): List<DashboardPreference>

    fun findByUserIdAndContextTypeAndContextIdIsNullAndWidgetKey(
        userId: Long, contextType: DashboardContextType, widgetKey: WidgetKey
    ): DashboardPreference?

    fun deleteByUserIdAndContextTypeAndContextIdIsNull(
        userId: Long, contextType: DashboardContextType
    )

    fun deleteByUserIdAndContextTypeAndContextId(
        userId: Long, contextType: DashboardContextType, contextId: Long
    )
}
