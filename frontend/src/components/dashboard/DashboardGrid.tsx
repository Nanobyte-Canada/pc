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

// Dashboard context: 5-column grid
// Row 1: Connected accounts strip (full width)
// Row 2: 5 KPI cards (Investment, Cash, Buying Power, Returns, Sectors)
// Row 3: Positions table (full width)
const DASHBOARD_WIDGET_ORDER: WidgetKey[] = [
  'CONNECTED_ACCOUNTS',
  'PORTFOLIO_SUMMARY', 'IRR', 'SECTOR_EXPOSURE',
  'POSITIONS_HOLDINGS',
  'ORDERS',
]

const DASHBOARD_COL_SPANS: Partial<Record<WidgetKey, number>> = {
  CONNECTED_ACCOUNTS: 5,
  PORTFOLIO_SUMMARY: 3,
  IRR: 1,
  SECTOR_EXPOSURE: 1,
  POSITIONS_HOLDINGS: 5,
  ORDERS: 5,
}

// Account context: 5-column grid
const ACCOUNT_WIDGET_ORDER: WidgetKey[] = [
  'ACCOUNT_SUMMARY',
  'IRR', 'SECTOR_EXPOSURE', 'FEES_COMMISSION', 'DIVIDEND_CALENDAR',
  'ORDERS', 'REBALANCING_PROGRESS',
  'POSITIONS_HOLDINGS',
]

const ACCOUNT_COL_SPANS: Partial<Record<WidgetKey, number>> = {
  ACCOUNT_SUMMARY: 5,
  IRR: 1,
  SECTOR_EXPOSURE: 1,
  FEES_COMMISSION: 1,
  DIVIDEND_CALENDAR: 1,
  ORDERS: 3,
  REBALANCING_PROGRESS: 2,
  POSITIONS_HOLDINGS: 5,
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
        <div className="dashboard-grid-loading-full">
          <Skeleton style={{ height: '4rem', width: '100%', borderRadius: 'var(--radius-md)' }} />
        </div>
        {[...Array(5)].map((_, i) => (
          <div key={i}>
            <Skeleton style={{ height: '8rem', width: '100%', borderRadius: 'var(--radius-md)' }} />
          </div>
        ))}
        <div className="dashboard-grid-loading-full">
          <Skeleton style={{ height: '16rem', width: '100%', borderRadius: 'var(--radius-md)' }} />
        </div>
      </div>
    )
  }

  // Shared 5-column grid renderer for both contexts
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
          const spanClass = [
            colSpan === 5 ? 'widget-col-span-5' :
            colSpan === 4 ? 'widget-col-span-4' :
            colSpan === 3 ? 'widget-col-span-3' :
            colSpan === 2 ? 'widget-col-span-2' : undefined,
            key === 'PORTFOLIO_SUMMARY' ? 'widget-content-fit' : undefined,
          ].filter(Boolean).join(' ') || undefined
          return (
            <WidgetWrapper
              key={key}
              title={entry.title}
              columnSpan={colSpan}
              className={spanClass}
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
