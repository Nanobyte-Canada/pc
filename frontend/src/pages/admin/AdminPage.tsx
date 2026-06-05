import { useState } from 'react'
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query'
import { RefreshCw, Play, ChevronDown, ChevronRight, Check, Minus, Circle, AlertTriangle } from 'lucide-react'
import {
  getIngestionStats,
  getActiveRun,
  getIngestionRuns,
  getRunSteps,
  getRunErrors,
  triggerExchangeSync,
  triggerFullIngestion,
  type IngestionStats,
  type ActiveRun,
  type IngestionRun,
  type IngestionStep,
  type IngestionError,
} from '../../services/adminService'
import { useToast } from '../../stores/toastStore'
import './AdminPage.css'

// ─── Helpers ───

function formatDate(dateString: string | null): string {
  if (!dateString) return '--'
  return new Date(dateString).toLocaleString()
}

function timeAgo(dateString: string | null): string {
  if (!dateString) return '--'
  const seconds = Math.floor((Date.now() - new Date(dateString).getTime()) / 1000)
  if (seconds < 60) return `${seconds}s ago`
  const minutes = Math.floor(seconds / 60)
  if (minutes < 60) return `${minutes}m ago`
  const hours = Math.floor(minutes / 60)
  if (hours < 24) return `${hours}h ago`
  const days = Math.floor(hours / 24)
  return `${days}d ago`
}

function duration(start: string, end: string | null): string {
  if (!end) return 'running...'
  const ms = new Date(end).getTime() - new Date(start).getTime()
  const totalSec = Math.floor(ms / 1000)
  const m = Math.floor(totalSec / 60)
  const s = totalSec % 60
  return `${m}m ${s.toString().padStart(2, '0')}s`
}

const INSTRUMENT_TYPES = ['STOCK', 'ETF', 'MUTUAL_FUND', 'PREFERRED_STOCK', 'INDEX', 'BOND'] as const

const INSTRUMENT_TYPE_LABELS: Record<string, string> = {
  STOCK: 'Stocks',
  ETF: 'ETFs',
  MUTUAL_FUND: 'Mutual Funds',
  PREFERRED_STOCK: 'Preferred',
  INDEX: 'Indices',
  BOND: 'Bonds',
}

function badgeClass(status: string): string {
  const s = status.toUpperCase()
  if (s === 'COMPLETED' || s === 'SUCCESS') return 'admin-badge admin-badge--completed'
  if (s === 'RUNNING') return 'admin-badge admin-badge--running'
  if (s === 'FAILED' || s === 'ERROR') return 'admin-badge admin-badge--failed'
  if (s === 'PARTIAL') return 'admin-badge admin-badge--partial'
  return 'admin-badge admin-badge--idle'
}

function triggerBadgeClass(source: string | null): string {
  if (source && source.startsWith('api')) return 'admin-badge admin-badge--api'
  return 'admin-badge admin-badge--idle'
}

function statusDotClass(status: string): string {
  const s = status.toUpperCase()
  if (s === 'COMPLETED') return 'admin-status-dot admin-status-dot--completed'
  if (s === 'RUNNING') return 'admin-status-dot admin-status-dot--running'
  if (s === 'FAILED') return 'admin-status-dot admin-status-dot--failed'
  if (s === 'PARTIAL') return 'admin-status-dot admin-status-dot--partial'
  return 'admin-status-dot'
}

function lastRunStatusColor(status: string): string {
  const s = status.toUpperCase()
  if (s === 'COMPLETED') return 'var(--success)'
  if (s === 'RUNNING') return 'var(--warning)'
  if (s === 'FAILED') return 'var(--error)'
  if (s === 'PARTIAL') return 'var(--warning)'
  return 'var(--text-muted)'
}

// ─── Summary Stats Section ───

function SummaryStats({ stats }: { stats: IngestionStats | undefined }) {
  if (!stats) {
    return (
      <div className="admin-stats-grid">
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} className="admin-stat-card admin-stat-card--skeleton" />
        ))}
      </div>
    )
  }

  const enrichedPct = stats.totalInstruments > 0
    ? Math.round((stats.enrichedInstruments / stats.totalInstruments) * 100)
    : 0
  const quotaUsed = stats.totalDailyQuota - stats.remainingDailyQuota
  const quotaPct = stats.totalDailyQuota > 0
    ? Math.round((quotaUsed / stats.totalDailyQuota) * 100)
    : 0

  return (
    <div className="admin-stats-grid">
      {/* Total Instruments */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Total Instruments</div>
        <div className="admin-stat-value">{stats.totalInstruments.toLocaleString()}</div>
      </div>

      {/* Enriched */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Enriched</div>
        <div className="admin-stat-value admin-stat-value--accent">{stats.enrichedInstruments.toLocaleString()}</div>
        <div className="admin-stat-hint">{enrichedPct}% coverage</div>
      </div>

      {/* Pending */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Pending</div>
        <div className="admin-stat-value admin-stat-value--warning">{stats.pendingInstruments.toLocaleString()}</div>
      </div>

      {/* Daily Quota */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Daily Quota</div>
        <div className="admin-stat-value admin-stat-value--sm">
          {stats.remainingDailyQuota.toLocaleString()}
          <span className="admin-stat-hint" style={{ marginLeft: 4 }}>/ {stats.totalDailyQuota.toLocaleString()}</span>
        </div>
        <div className="admin-quota-track">
          <div className="admin-quota-fill" style={{ width: `${quotaPct}%` }} />
        </div>
      </div>

      {/* Exchanges */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Exchanges</div>
        <div className="admin-stat-value">{stats.exchangeCount}</div>
        <div className="admin-stat-hint">{stats.exchanges.join(', ')}</div>
      </div>

      {/* Last Run */}
      <div className="admin-stat-card">
        <div className="admin-stat-label">Last Run</div>
        <div className="admin-status-value" style={{ color: lastRunStatusColor(stats.lastRunStatus) }}>
          <span className={statusDotClass(stats.lastRunStatus)} />
          {stats.lastRunStatus || '--'}
        </div>
        <div className="admin-stat-hint">{timeAgo(stats.lastRunCompletedAt)}</div>
      </div>
    </div>
  )
}

// ─── Instrument Type Stats Section ───

function InstrumentTypeStats({ stats }: { stats: IngestionStats | undefined }) {
  if (!stats) return null

  return (
    <div className="admin-type-section">
      <div className="admin-section-title">Instruments by Type</div>
      <div className="admin-type-grid">
        {INSTRUMENT_TYPES.map((type) => {
          const data = stats.instrumentsByType[type]
          return (
            <div key={type} className="admin-type-card">
              <div className="admin-type-label">{INSTRUMENT_TYPE_LABELS[type] || type}</div>
              <div className="admin-type-total">{data ? data.total.toLocaleString() : '0'}</div>
              <div className="admin-type-enriched">{data ? data.enriched.toLocaleString() : '0'} enriched</div>
            </div>
          )
        })}
      </div>
    </div>
  )
}

// ─── Active Run Progress ───

function StepIcon({ status }: { status: string }) {
  const s = status.toUpperCase()
  if (s === 'COMPLETED')
    return (
      <div className="admin-step-icon admin-step-icon--completed">
        <Check size={9} strokeWidth={3} />
      </div>
    )
  if (s === 'RUNNING')
    return (
      <div className="admin-step-icon admin-step-icon--running">
        <Circle size={7} fill="currentColor" />
      </div>
    )
  if (s === 'FAILED')
    return (
      <div className="admin-step-icon admin-step-icon--failed">
        <AlertTriangle size={9} />
      </div>
    )
  return (
    <div className="admin-step-icon admin-step-icon--pending">
      <Minus size={9} />
    </div>
  )
}

function ActiveRunProgress({ activeRun }: { activeRun: ActiveRun | undefined }) {
  if (!activeRun || !activeRun.isRunning || !activeRun.steps) return null

  return (
    <div className="admin-progress-steps">
      {activeRun.steps.map((step, i) => (
        <div key={i} className="admin-step-row">
          <StepIcon status={step.status} />
          <span className="admin-step-name">{step.name}</span>
          <span className="admin-step-stats">
            {step.processed.toLocaleString()} processed
            {step.created > 0 && <> &middot; {step.created.toLocaleString()} new</>}
            {step.failed > 0 && <> &middot; <span className="error-count">{step.failed} errors</span></>}
          </span>
        </div>
      ))}
    </div>
  )
}

// ─── Workflows Section ───

function WorkflowsSection({ activeRun }: { activeRun: ActiveRun | undefined }) {
  const queryClient = useQueryClient()
  const toast = useToast()

  const syncExchangesMutation = useMutation({
    mutationFn: triggerExchangeSync,
    onSuccess: () => {
      toast.success('Exchange sync started')
      queryClient.invalidateQueries({ queryKey: ['ingestion-active-run'] })
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : 'Failed to start exchange sync')
    },
  })

  const fullIngestionMutation = useMutation({
    mutationFn: triggerFullIngestion,
    onSuccess: () => {
      toast.success('Full ingestion started')
      queryClient.invalidateQueries({ queryKey: ['ingestion-active-run'] })
    },
    onError: (err) => {
      toast.error(err instanceof Error ? err.message : 'Failed to start full ingestion')
    },
  })

  const isRunning = activeRun?.isRunning ?? false

  return (
    <div className="admin-workflows-section">
      <div className="admin-section-title">Workflows</div>

      {/* Exchange Sync */}
      <div className="admin-workflow-card">
        <div className="admin-workflow-header">
          <span className="admin-workflow-name">Exchange Sync</span>
          <span className={badgeClass(syncExchangesMutation.isPending ? 'RUNNING' : 'IDLE')}>
            {syncExchangesMutation.isPending ? 'Running' : 'Idle'}
          </span>
        </div>
        <div className="admin-workflow-desc">
          Fetch and update the list of supported exchanges from EODHD. One-time setup, re-run when adding new exchanges.
        </div>
        <button
          className="admin-run-btn"
          onClick={() => syncExchangesMutation.mutate()}
          disabled={syncExchangesMutation.isPending || isRunning}
        >
          <Play size={12} />
          Run Exchange Sync
        </button>
      </div>

      {/* Full Ingestion */}
      <div className="admin-workflow-card">
        <div className="admin-workflow-header">
          <span className="admin-workflow-name">Full Ingestion</span>
          <span className={badgeClass(isRunning ? 'RUNNING' : 'IDLE')}>
            {isRunning ? 'Running' : 'Idle'}
          </span>
        </div>
        <div className="admin-workflow-desc">
          Universe discovery + fundamentals fetch for all target exchanges. Processes instruments by staleness, then type priority.
        </div>

        <ActiveRunProgress activeRun={activeRun} />

        <button
          className="admin-run-btn admin-run-btn--primary"
          onClick={() => fullIngestionMutation.mutate()}
          disabled={fullIngestionMutation.isPending || isRunning}
        >
          <Play size={12} />
          {isRunning ? 'Running...' : 'Run Full Ingestion'}
        </button>
      </div>
    </div>
  )
}

// ─── Expanded Run Detail ───

function RunExpandedDetail({ runId }: { runId: number }) {
  const { data: steps } = useQuery<IngestionStep[]>({
    queryKey: ['ingestion-steps', runId],
    queryFn: () => getRunSteps(runId),
    enabled: true,
  })

  const { data: errors } = useQuery<IngestionError[]>({
    queryKey: ['ingestion-errors', runId],
    queryFn: () => getRunErrors(runId),
    enabled: true,
  })

  return (
    <tr>
      <td colSpan={7} className="admin-expanded-cell">
        {/* Steps */}
        {steps && steps.length > 0 && (
          <div className="admin-expanded-steps">
            {steps.map((step) => (
              <div key={step.id} className="admin-expanded-step">
                <span>{step.stepName}</span>
                <span><span className={badgeClass(step.status)}>{step.status}</span></span>
                <div className="admin-expanded-step-stats">
                  <span><span>Processed:</span> <strong>{step.recordsProcessed.toLocaleString()}</strong></span>
                  <span><span>Created:</span> <strong>{step.recordsCreated.toLocaleString()}</strong></span>
                  <span><span>Updated:</span> <strong>{step.recordsUpdated.toLocaleString()}</strong></span>
                  <span><span>Failed:</span> <strong className={step.recordsFailed > 0 ? 'failed-count' : ''}>{step.recordsFailed.toLocaleString()}</strong></span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Errors */}
        {errors && errors.length > 0 && (
          <div className="admin-expanded-errors">
            {errors.map((err) => (
              <div key={err.id} className="admin-expanded-error">
                <span className="admin-error-type">{err.errorType}</span>
                <span className="admin-error-message">{err.errorMessage ?? '--'}</span>
                <span className="admin-error-time">{formatDate(err.createdAt)}</span>
              </div>
            ))}
          </div>
        )}

        {(!steps || steps.length === 0) && (!errors || errors.length === 0) && (
          <div className="admin-expanded-empty">No step or error data available</div>
        )}
      </td>
    </tr>
  )
}

// ─── Recent Runs Table ───

function RecentRunsTable({ runs }: { runs: IngestionRun[] | undefined }) {
  const [expandedRunId, setExpandedRunId] = useState<number | null>(null)

  if (!runs) {
    return (
      <div className="admin-runs-card">
        <div className="admin-runs-header">
          <span className="admin-runs-title">Recent Runs</span>
        </div>
        <div className="admin-runs-loading">Loading...</div>
      </div>
    )
  }

  return (
    <div className="admin-runs-card">
      <div className="admin-runs-header">
        <span className="admin-runs-title">Recent Runs</span>
      </div>

      {runs.length === 0 ? (
        <div className="admin-runs-empty">No ingestion runs found</div>
      ) : (
        <table className="admin-runs-table">
          <thead>
            <tr>
              <th>ID</th>
              <th>Type</th>
              <th>Status</th>
              <th>Trigger</th>
              <th>Started</th>
              <th>Duration</th>
              <th />
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => {
              const isExpanded = expandedRunId === run.id
              return (
                <>
                  <tr
                    key={run.id}
                    className="admin-run-row"
                    onClick={() => setExpandedRunId(isExpanded ? null : run.id)}
                  >
                    <td><span className="admin-run-id">#{run.id}</span></td>
                    <td>
                      <span className={triggerBadgeClass(run.triggerSource)}>
                        {run.runType}
                      </span>
                    </td>
                    <td>
                      <span className={badgeClass(run.status)}>{run.status}</span>
                    </td>
                    <td><span className="admin-run-trigger">{run.triggerSource ?? '--'}</span></td>
                    <td>{formatDate(run.startedAt)}</td>
                    <td>{duration(run.startedAt, run.completedAt)}</td>
                    <td>
                      <span className="admin-run-chevron">
                        {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
                      </span>
                    </td>
                  </tr>
                  {isExpanded && <RunExpandedDetail key={`detail-${run.id}`} runId={run.id} />}
                </>
              )
            })}
          </tbody>
        </table>
      )}
    </div>
  )
}

// ─── Main Page ───

export function AdminPage() {
  const queryClient = useQueryClient()

  const { data: stats } = useQuery<IngestionStats>({
    queryKey: ['ingestion-stats'],
    queryFn: getIngestionStats,
    refetchInterval: 10_000,
  })

  const { data: activeRun } = useQuery<ActiveRun>({
    queryKey: ['ingestion-active-run'],
    queryFn: getActiveRun,
    refetchInterval: 10_000,
  })

  const { data: runs } = useQuery<IngestionRun[]>({
    queryKey: ['ingestion-runs'],
    queryFn: () => getIngestionRuns(10),
    refetchInterval: 30_000,
  })

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['ingestion-stats'] })
    queryClient.invalidateQueries({ queryKey: ['ingestion-active-run'] })
    queryClient.invalidateQueries({ queryKey: ['ingestion-runs'] })
  }

  return (
    <div className="admin-page">
      {/* Header */}
      <div className="admin-header">
        <div className="admin-header-left">
          <h1>Admin Panel</h1>
          <div className="admin-header-subtitle">Data ingestion and instrument management</div>
        </div>
        <div className="admin-header-right">
          <div className="admin-auto-refresh">
            <span className="admin-auto-refresh-dot" />
            Auto-refresh 10s
          </div>
          <button className="admin-refresh-btn" onClick={handleRefresh}>
            <RefreshCw size={12} />
            Refresh
          </button>
        </div>
      </div>

      {/* Summary Stats */}
      <SummaryStats stats={stats} />

      {/* Instrument Type Stats */}
      <InstrumentTypeStats stats={stats} />

      {/* Bottom two-column: Workflows + Recent Runs */}
      <div className="admin-bottom-grid">
        <WorkflowsSection activeRun={activeRun} />
        <RecentRunsTable runs={runs} />
      </div>
    </div>
  )
}
