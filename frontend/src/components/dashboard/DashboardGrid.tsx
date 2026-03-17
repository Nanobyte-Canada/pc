import { Suspense, useState } from 'react'
import { Settings, RefreshCw } from 'lucide-react'
import { Button } from '@/components/ui/button'
import { Skeleton } from '@/components/ui/skeleton'
import { WidgetWrapper } from './WidgetWrapper'
import { DashboardEditMode } from './DashboardEditMode'
import { PositionsHoldingsTabs } from './PositionsHoldingsTabs'
import { WIDGET_REGISTRY, DEFAULT_WIDGET_ORDER, ZONE_A_WIDGETS, ZONE_B_WIDGETS, CONFIGURABLE_WIDGETS } from './WidgetRegistry'
import { useDashboardPreferences, useUpdateDashboardPreferences } from '@/hooks/useDashboardPreferences'
import { useRefreshAll } from '@/hooks/useDashboardWidgets'
import type { WidgetPreference, WidgetKey } from '@/types/dashboard'
import './DashboardGrid.css'

interface DashboardGridProps {
  connectionId?: number
  contextType?: string
}

const ACCENT_COLORS: Partial<Record<WidgetKey, string>> = {
  PORTFOLIO_VALUE: '#2a8a81',
  AVAILABLE_CASH: '#059669',
  BUYING_POWER: '#06b6d4',
  RISK_PROFILE: '#f59e0b',
  SECTOR_EXPOSURE: '#2a8a81',
  GEOGRAPHY_EXPOSURE: '#3b82f6',
  CONNECTED_ACCOUNTS: '#8b5cf6',
  OPEN_ORDERS: '#f97316',
  FEES_COMMISSION: '#ef4444',
  DIVIDEND_CALENDAR: '#059669',
}

const ConnectedAccountsComponent = WIDGET_REGISTRY.CONNECTED_ACCOUNTS.component

export function DashboardGrid({ connectionId, contextType = 'DASHBOARD' }: DashboardGridProps) {
  const [editMode, setEditMode] = useState(false)
  const { data: prefsData, isLoading: prefsLoading } = useDashboardPreferences(contextType, connectionId ? connectionId : undefined)
  const updatePrefs = useUpdateDashboardPreferences()
  const { mutate: refresh, isPending: isRefreshing } = useRefreshAll()

  const preferences: WidgetPreference[] = prefsData?.widgets ?? DEFAULT_WIDGET_ORDER.map(key => {
    const reg = WIDGET_REGISTRY[key]
    return { key, visible: reg.defaultVisible, sortOrder: reg.defaultSortOrder, columnSpan: reg.defaultColumnSpan }
  })

  const zoneAWidgets = preferences
    .filter(p => ZONE_A_WIDGETS.includes(p.key as WidgetKey) && p.visible)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  const zoneBWidgets = preferences
    .filter(p => ZONE_B_WIDGETS.includes(p.key as WidgetKey) && p.visible)
    .sort((a, b) => a.sortOrder - b.sortOrder)

  const configurablePrefs = preferences.filter(p =>
    CONFIGURABLE_WIDGETS.includes(p.key as WidgetKey)
  )

  const handleSavePreferences = (newPrefs: WidgetPreference[]) => {
    // Merge configurable prefs back with always-visible prefs (forced visible)
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

  return (
    <div>
      <div className="dashboard-grid-toolbar">
        <Button
          variant="outline"
          size="sm"
          onClick={() => refresh()}
          disabled={isRefreshing}
        >
          <RefreshCw className={isRefreshing ? 'animate-spin' : ''} style={{ height: '1rem', width: '1rem', marginRight: '0.5rem' }} />
          {isRefreshing ? 'Refreshing...' : 'Refresh Data'}
        </Button>
        <Button variant="outline" size="sm" onClick={() => setEditMode(true)}>
          <Settings style={{ height: '1rem', width: '1rem', marginRight: '0.5rem' }} />
          Customize
        </Button>
      </div>

      <div className="dashboard-zones-top">
        {/* Zone A: Category 1 widgets (top-left 2/3) */}
        {zoneAWidgets.length > 0 && (
          <div className="dashboard-zone-a">
            <div className="zone-a-grid">
              {zoneAWidgets.map(pref => {
                const widgetKey = pref.key as WidgetKey
                const entry = WIDGET_REGISTRY[widgetKey]
                if (!entry) return null
                const Component = entry.component
                return (
                  <WidgetWrapper
                    key={pref.key}
                    title={entry.title}
                    columnSpan={1}
                    accentColor={ACCENT_COLORS[widgetKey]}
                  >
                    <Suspense fallback={<Skeleton style={{ height: '6rem', width: '100%' }} />}>
                      <Component connectionId={connectionId} />
                    </Suspense>
                  </WidgetWrapper>
                )
              })}
            </div>
          </div>
        )}

        {/* Zone C: Connected Accounts (always visible, below A) */}
        <div className="dashboard-zone-c">
          <WidgetWrapper
            title="Connected Accounts"
            columnSpan={1}
            accentColor={ACCENT_COLORS.CONNECTED_ACCOUNTS}
          >
            <Suspense fallback={<Skeleton style={{ height: '6rem', width: '100%' }} />}>
              <ConnectedAccountsComponent connectionId={connectionId} />
            </Suspense>
          </WidgetWrapper>
        </div>

        {/* Zone B: Category 2 widgets (right 1/3) */}
        {zoneBWidgets.length > 0 && (
          <div className="dashboard-zone-b">
            {zoneBWidgets.map(pref => {
              const widgetKey = pref.key as WidgetKey
              const entry = WIDGET_REGISTRY[widgetKey]
              if (!entry) return null
              const Component = entry.component
              return (
                <WidgetWrapper
                  key={pref.key}
                  title={entry.title}
                  columnSpan={1}
                  accentColor={ACCENT_COLORS[widgetKey]}
                >
                  <Suspense fallback={<Skeleton style={{ height: '6rem', width: '100%' }} />}>
                    <Component connectionId={connectionId} />
                  </Suspense>
                </WidgetWrapper>
              )
            })}
          </div>
        )}

        {/* Zone D: Positions/Holdings tabs (always visible, full width) */}
        <div className="dashboard-zone-d">
          <PositionsHoldingsTabs connectionId={connectionId} />
        </div>
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
