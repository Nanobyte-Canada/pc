import { Suspense, useState } from 'react'
import { Skeleton } from '@/components/ui/skeleton'
import { ErrorBoundary } from '@/components/ui/ErrorBoundary'
import { WidgetWrapper } from './WidgetWrapper'
import { DashboardEditMode } from './DashboardEditMode'
import { WIDGET_REGISTRY, DEFAULT_WIDGET_ORDER, CONFIGURABLE_WIDGETS } from './WidgetRegistry'
import { useDashboardPreferences, useUpdateDashboardPreferences } from '@/hooks/useDashboardPreferences'
import type { WidgetPreference, WidgetKey } from '@/types/dashboard'
import './DashboardGrid.css'

interface DashboardGridProps {
  connectionId?: number
  contextType?: string
}

// Dashboard context: 4-column grid
const DASHBOARD_WIDGET_ORDER: WidgetKey[] = [
  'PORTFOLIO_SUMMARY', 'RISK_PROFILE',
  'SECTOR_EXPOSURE', 'GEOGRAPHY_EXPOSURE', 'FEES_COMMISSION', 'DIVIDEND_CALENDAR',
  'ORDERS', 'REBALANCING_PROGRESS',
  'CONNECTED_ACCOUNTS',
  'POSITIONS_HOLDINGS',
]

const DASHBOARD_COL_SPANS: Partial<Record<WidgetKey, number>> = {
  PORTFOLIO_SUMMARY: 3,
  RISK_PROFILE: 1,
  SECTOR_EXPOSURE: 1,
  GEOGRAPHY_EXPOSURE: 1,
  FEES_COMMISSION: 1,
  DIVIDEND_CALENDAR: 1,
  ORDERS: 2,
  REBALANCING_PROGRESS: 2,
  CONNECTED_ACCOUNTS: 4,
  POSITIONS_HOLDINGS: 4,
}

// Account context: 4-column grid
const ACCOUNT_WIDGET_ORDER: WidgetKey[] = [
  'ACCOUNT_SUMMARY', 'RISK_PROFILE',
  'SECTOR_EXPOSURE', 'GEOGRAPHY_EXPOSURE', 'FEES_COMMISSION', 'DIVIDEND_CALENDAR',
  'ORDERS', 'REBALANCING_PROGRESS',
  'POSITIONS_HOLDINGS',
]

const ACCOUNT_COL_SPANS: Partial<Record<WidgetKey, number>> = {
  ACCOUNT_SUMMARY: 3,
  RISK_PROFILE: 1,
  SECTOR_EXPOSURE: 1,
  GEOGRAPHY_EXPOSURE: 1,
  FEES_COMMISSION: 1,
  DIVIDEND_CALENDAR: 1,
  ORDERS: 2,
  REBALANCING_PROGRESS: 2,
  POSITIONS_HOLDINGS: 4,
}

export function DashboardGrid({ connectionId, contextType = 'DASHBOARD' }: DashboardGridProps) {
  const [editMode, setEditMode] = useState(false)
  const isAccountContext = contextType === 'ACCOUNT'
  const { data: prefsData, isLoading: prefsLoading } = useDashboardPreferences(contextType, connectionId ? connectionId : undefined)
  const updatePrefs = useUpdateDashboardPreferences()

  const preferences: WidgetPreference[] = (() => {
    const defaults = DEFAULT_WIDGET_ORDER.map(key => {
      const reg = WIDGET_REGISTRY[key]
      return { key, visible: reg.defaultVisible, sortOrder: reg.defaultSortOrder, columnSpan: reg.defaultColumnSpan }
    })
    const rawPrefs = prefsData?.widgets
    if (!rawPrefs || rawPrefs.length === 0) return defaults

    const savedKeys = new Set(rawPrefs.map(p => p.key))
    const newWidgets = defaults.filter(d => !savedKeys.has(d.key))
    const validSaved = rawPrefs.filter(p => WIDGET_REGISTRY[p.key as WidgetKey])
    return [...validSaved, ...newWidgets]
  })()

  const configurablePrefs = preferences.filter(p =>
    CONFIGURABLE_WIDGETS.includes(p.key as WidgetKey)
  )

  const handleSavePreferences = (newPrefs: WidgetPreference[]) => {
    const alwaysVisible = preferences
      .filter(p => !CONFIGURABLE_WIDGETS.includes(p.key as WidgetKey))
      .map(p => ({ ...p, visible: true }))
    updatePrefs.mutate({
      request: { widgets: [...newPrefs, ...alwaysVisible] },
      contextType,
      contextId: connectionId,
    })
  }

  if (prefsLoading) {
    return (
      <div className="dashboard-grid-loading">
        {[...Array(6)].map((_, i) => (
          <div key={i} className={i === 0 ? 'dashboard-grid-loading-full' : ''}>
            <Skeleton style={{ height: '9rem', width: '100%', borderRadius: '0.75rem' }} />
          </div>
        ))}
      </div>
    )
  }

  // Shared 4-column grid renderer for both contexts
  const widgetOrder = isAccountContext ? ACCOUNT_WIDGET_ORDER : DASHBOARD_WIDGET_ORDER
  const colSpans = isAccountContext ? ACCOUNT_COL_SPANS : DASHBOARD_COL_SPANS

  const widgetsToRender = widgetOrder
    .map(key => {
      const pref = preferences.find(p => p.key === key)
      const entry = WIDGET_REGISTRY[key]
      if (!pref || !entry || !pref.visible) return null
      return { key, pref, entry }
    })
    .filter(Boolean) as { key: WidgetKey; pref: WidgetPreference; entry: typeof WIDGET_REGISTRY[WidgetKey] }[]

  return (
    <div>
      <div className="dashboard-grid-4col">
        {widgetsToRender.map(({ key, entry }) => {
          const Component = entry.component
          const colSpan = colSpans[key] ?? 1
          return (
            <WidgetWrapper
              key={key}
              title={entry.title}
              columnSpan={colSpan}
              className={colSpan === 4 ? 'widget-col-span-4' : colSpan === 3 ? 'widget-col-span-3' : colSpan === 2 ? 'widget-col-span-2' : undefined}
            >
              <ErrorBoundary>
                <Suspense fallback={<Skeleton style={{ height: '6rem', width: '100%' }} />}>
                  <Component connectionId={connectionId} />
                </Suspense>
              </ErrorBoundary>
            </WidgetWrapper>
          )
        })}
      </div>

      <DashboardEditMode
        open={editMode}
        onOpenChange={setEditMode}
        preferences={configurablePrefs}
        onSave={handleSavePreferences}
      />
    </div>
  )
}
