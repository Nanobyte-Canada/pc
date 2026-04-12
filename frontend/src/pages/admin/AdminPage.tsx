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

function statusBadgeStyle(status: string): React.CSSProperties {
  const s = status.toUpperCase()
  if (s === 'COMPLETED' || s === 'SUCCESS')
    return { background: 'rgba(34,197,94,0.15)', color: '#22c55e', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
  if (s === 'RUNNING')
    return { background: 'rgba(96,165,250,0.15)', color: '#60a5fa', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
  if (s === 'FAILED' || s === 'ERROR')
    return { background: 'rgba(239,68,68,0.15)', color: '#ef4444', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
  if (s === 'PARTIAL')
    return { background: 'rgba(245,158,11,0.15)', color: '#f59e0b', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
  return { background: 'var(--color-surface-secondary, #252833)', color: 'var(--color-text-muted)', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
}

function triggerBadgeStyle(source: string | null): React.CSSProperties {
  if (source && source.startsWith('api'))
    return { background: 'rgba(132,107,84,0.15)', color: '#846b54', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
  return { background: 'var(--color-surface-secondary, #252833)', color: 'var(--color-text-muted)', padding: '0.125rem 0.5rem', borderRadius: '999px', fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em' }
}

function lastRunStatusColor(status: string): string {
  const s = status.toUpperCase()
  if (s === 'COMPLETED') return '#22c55e'
  if (s === 'RUNNING') return '#60a5fa'
  if (s === 'FAILED') return '#ef4444'
  if (s === 'PARTIAL') return '#f59e0b'
  return 'var(--color-text-muted)'
}

// ─── Summary Stats Section ───

function SummaryStats({ stats }: { stats: IngestionStats | undefined }) {
  if (!stats) {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '0.75rem', marginBottom: '1.5rem' }}>
        {Array.from({ length: 6 }).map((_, i) => (
          <div key={i} style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem', height: '80px' }} />
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
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(auto-fit, minmax(140px, 1fr))', gap: '0.75rem', marginBottom: '1.5rem' }}>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Total Instruments</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{stats.totalInstruments.toLocaleString()}</div>
      </div>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Enriched</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#22c55e' }}>{stats.enrichedInstruments.toLocaleString()}</div>
        <div style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginTop: '0.125rem' }}>{enrichedPct}% coverage</div>
      </div>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Pending</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 700, color: '#f59e0b' }}>{stats.pendingInstruments.toLocaleString()}</div>
      </div>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Daily Quota</div>
        <div style={{ fontSize: '1.125rem', fontWeight: 700 }}>
          {stats.remainingDailyQuota.toLocaleString()}
          <span style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)' }}> / {stats.totalDailyQuota.toLocaleString()}</span>
        </div>
        <div style={{ height: '4px', background: 'var(--color-surface-secondary, #252833)', borderRadius: '2px', overflow: 'hidden', marginTop: '0.375rem' }}>
          <div style={{ height: '100%', background: '#22c55e', borderRadius: '2px', width: `${quotaPct}%` }} />
        </div>
      </div>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Exchanges</div>
        <div style={{ fontSize: '1.5rem', fontWeight: 700 }}>{stats.exchangeCount}</div>
        <div style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginTop: '0.125rem' }}>{stats.exchanges.join(', ')}</div>
      </div>
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1rem' }}>
        <div style={{ fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.375rem' }}>Last Run</div>
        <div style={{ fontSize: '1.125rem', fontWeight: 700, color: lastRunStatusColor(stats.lastRunStatus) }}>
          {stats.lastRunStatus || '--'}
        </div>
        <div style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginTop: '0.125rem' }}>
          {timeAgo(stats.lastRunCompletedAt)}
        </div>
      </div>
    </div>
  )
}

// ─── Instrument Type Stats Section ───

function InstrumentTypeStats({ stats }: { stats: IngestionStats | undefined }) {
  if (!stats) return null

  return (
    <div style={{ marginBottom: '1.5rem' }}>
      <div style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
        Instruments by Type
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(6, 1fr)', gap: '0.5rem' }}>
        {INSTRUMENT_TYPES.map((type) => {
          const data = stats.instrumentsByType[type]
          return (
            <div key={type} style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.5rem', padding: '0.75rem', textAlign: 'center' }}>
              <div style={{ fontSize: '0.5625rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.25rem' }}>
                {INSTRUMENT_TYPE_LABELS[type] || type}
              </div>
              <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>{data ? data.total.toLocaleString() : '0'}</div>
              <div style={{ fontSize: '0.5625rem', color: '#22c55e', marginTop: '0.125rem' }}>
                {data ? data.enriched.toLocaleString() : '0'} enriched
              </div>
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
      <div style={{ width: '16px', height: '16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(34,197,94,0.15)', color: '#22c55e', flexShrink: 0 }}>
        <Check size={9} strokeWidth={3} />
      </div>
    )
  if (s === 'RUNNING')
    return (
      <div style={{ width: '16px', height: '16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(96,165,250,0.15)', color: '#60a5fa', flexShrink: 0 }}>
        <Circle size={7} fill="currentColor" />
      </div>
    )
  if (s === 'FAILED')
    return (
      <div style={{ width: '16px', height: '16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'rgba(239,68,68,0.15)', color: '#ef4444', flexShrink: 0 }}>
        <AlertTriangle size={9} />
      </div>
    )
  return (
    <div style={{ width: '16px', height: '16px', borderRadius: '50%', display: 'flex', alignItems: 'center', justifyContent: 'center', background: 'var(--color-surface-secondary, #252833)', color: 'var(--color-text-muted)', flexShrink: 0 }}>
      <Minus size={9} />
    </div>
  )
}

function ActiveRunProgress({ activeRun }: { activeRun: ActiveRun | undefined }) {
  if (!activeRun || !activeRun.isRunning || !activeRun.steps) return null

  return (
    <div style={{ marginBottom: '0.75rem' }}>
      <div style={{ display: 'flex', flexDirection: 'column', gap: '0.375rem', marginBottom: '0.75rem' }}>
        {activeRun.steps.map((step, i) => (
          <div key={i} style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.6875rem', padding: '0.375rem 0.5rem', background: 'rgba(255,255,255,0.02)', borderRadius: '0.375rem' }}>
            <StepIcon status={step.status} />
            <span style={{ flex: 1 }}>{step.name}</span>
            <span style={{ color: 'var(--color-text-muted)', fontSize: '0.5625rem' }}>
              {step.processed.toLocaleString()} processed
              {step.created > 0 && <> &middot; {step.created.toLocaleString()} new</>}
              {step.failed > 0 && <> &middot; <span style={{ color: '#ef4444' }}>{step.failed} errors</span></>}
            </span>
          </div>
        ))}
      </div>
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
    <div style={{ marginBottom: '1.5rem' }}>
      <div style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
        Workflows
      </div>
      <div style={{ display: 'grid', gridTemplateColumns: '1fr 1fr', gap: '0.75rem' }}>
        {/* Exchange Sync */}
        <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Exchange Sync</div>
            <span style={statusBadgeStyle(syncExchangesMutation.isPending ? 'RUNNING' : 'IDLE')}>
              {syncExchangesMutation.isPending ? 'Running' : 'Idle'}
            </span>
          </div>
          <div style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginBottom: '1rem', lineHeight: 1.4 }}>
            Fetch and update the list of supported exchanges from EODHD. One-time setup, re-run when adding new exchanges.
          </div>
          <button
            onClick={() => syncExchangesMutation.mutate()}
            disabled={syncExchangesMutation.isPending || isRunning}
            style={{
              padding: '0.5rem 1rem', borderRadius: '0.375rem', fontSize: '0.75rem', fontWeight: 600,
              cursor: syncExchangesMutation.isPending || isRunning ? 'not-allowed' : 'pointer',
              border: '1px solid var(--color-border)', background: 'none',
              color: 'var(--color-text-secondary, #9ca3af)',
              opacity: syncExchangesMutation.isPending || isRunning ? 0.5 : 1,
              display: 'inline-flex', alignItems: 'center', gap: '0.375rem',
              transition: 'all 0.15s',
            }}
          >
            <Play size={12} />
            Run Exchange Sync
          </button>
        </div>

        {/* Full Ingestion */}
        <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', padding: '1.25rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '0.75rem' }}>
            <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Full Ingestion</div>
            <span style={statusBadgeStyle(isRunning ? 'RUNNING' : 'IDLE')}>
              {isRunning ? 'Running' : 'Idle'}
            </span>
          </div>
          <div style={{ fontSize: '0.6875rem', color: 'var(--color-text-muted)', marginBottom: '1rem', lineHeight: 1.4 }}>
            Universe discovery + fundamentals fetch for all target exchanges. Processes instruments by staleness, then type priority.
          </div>

          <ActiveRunProgress activeRun={activeRun} />

          <button
            onClick={() => fullIngestionMutation.mutate()}
            disabled={fullIngestionMutation.isPending || isRunning}
            style={{
              padding: '0.5rem 1rem', borderRadius: '0.375rem', fontSize: '0.75rem', fontWeight: 600,
              cursor: fullIngestionMutation.isPending || isRunning ? 'not-allowed' : 'pointer',
              border: '1px solid #846b54', background: '#846b54',
              color: 'white',
              opacity: fullIngestionMutation.isPending || isRunning ? 0.5 : 1,
              display: 'inline-flex', alignItems: 'center', gap: '0.375rem',
              transition: 'all 0.15s',
            }}
          >
            <Play size={12} />
            {isRunning ? 'Running...' : 'Run Full Ingestion'}
          </button>
        </div>
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
      <td colSpan={7} style={{ padding: 0, background: 'rgba(255,255,255,0.01)' }}>
        {/* Steps */}
        {steps && steps.length > 0 && (
          <div style={{ padding: '0.75rem 1rem 0.75rem 2.5rem' }}>
            {steps.map((step) => (
              <div key={step.id} style={{ display: 'grid', gridTemplateColumns: '150px 80px 1fr', gap: '1rem', padding: '0.375rem 0', fontSize: '0.6875rem', borderBottom: '1px solid rgba(255,255,255,0.02)' }}>
                <span>{step.stepName}</span>
                <span><span style={statusBadgeStyle(step.status)}>{step.status}</span></span>
                <div style={{ display: 'flex', gap: '1rem', fontSize: '0.625rem', color: 'var(--color-text-muted)' }}>
                  <span><span style={{ color: 'var(--color-text-muted)' }}>Processed:</span> <span style={{ fontWeight: 600, color: 'var(--color-text)' }}>{step.recordsProcessed.toLocaleString()}</span></span>
                  <span><span style={{ color: 'var(--color-text-muted)' }}>Created:</span> <span style={{ fontWeight: 600, color: 'var(--color-text)' }}>{step.recordsCreated.toLocaleString()}</span></span>
                  <span><span style={{ color: 'var(--color-text-muted)' }}>Updated:</span> <span style={{ fontWeight: 600, color: 'var(--color-text)' }}>{step.recordsUpdated.toLocaleString()}</span></span>
                  <span><span style={{ color: 'var(--color-text-muted)' }}>Failed:</span> <span style={{ fontWeight: 600, color: step.recordsFailed > 0 ? '#ef4444' : 'var(--color-text)' }}>{step.recordsFailed.toLocaleString()}</span></span>
                </div>
              </div>
            ))}
          </div>
        )}

        {/* Errors */}
        {errors && errors.length > 0 && (
          <div>
            {errors.map((err) => (
              <div key={err.id} style={{ padding: '0.5rem 1rem 0.5rem 3.5rem', fontSize: '0.6875rem', display: 'flex', gap: '0.75rem', borderBottom: '1px solid rgba(255,255,255,0.02)' }}>
                <span style={{ fontSize: '0.5rem', fontWeight: 700, padding: '0.0625rem 0.375rem', borderRadius: '0.25rem', background: 'rgba(239,68,68,0.1)', color: '#ef4444', whiteSpace: 'nowrap', alignSelf: 'flex-start' }}>
                  {err.errorType}
                </span>
                <span style={{ color: 'var(--color-text-secondary, #9ca3af)', flex: 1 }}>
                  {err.errorMessage ?? '--'}
                </span>
                <span style={{ color: 'var(--color-text-muted)', fontSize: '0.5625rem', whiteSpace: 'nowrap' }}>
                  {formatDate(err.createdAt)}
                </span>
              </div>
            ))}
          </div>
        )}

        {(!steps || steps.length === 0) && (!errors || errors.length === 0) && (
          <div style={{ padding: '0.75rem 1rem 0.75rem 2.5rem', color: 'var(--color-text-muted)', fontSize: '0.6875rem' }}>
            No step or error data available
          </div>
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
      <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', overflow: 'hidden' }}>
        <div style={{ padding: '1rem 1.25rem', borderBottom: '1px solid var(--color-border)', fontSize: '0.875rem', fontWeight: 600 }}>Recent Runs</div>
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-muted)', fontSize: '0.8125rem' }}>Loading...</div>
      </div>
    )
  }

  return (
    <div style={{ background: 'var(--color-surface, #1e2130)', border: '1px solid var(--color-border)', borderRadius: '0.75rem', overflow: 'hidden' }}>
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', padding: '1rem 1.25rem', borderBottom: '1px solid var(--color-border)' }}>
        <div style={{ fontSize: '0.875rem', fontWeight: 600 }}>Recent Runs</div>
      </div>

      {runs.length === 0 ? (
        <div style={{ padding: '2rem', textAlign: 'center', color: 'var(--color-text-muted)', fontSize: '0.8125rem' }}>
          No ingestion runs found
        </div>
      ) : (
        <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.75rem' }}>
          <thead>
            <tr>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>ID</th>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>Type</th>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>Status</th>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>Trigger</th>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>Started</th>
              <th style={{ textAlign: 'left', fontSize: '0.625rem', fontWeight: 600, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }}>Duration</th>
              <th style={{ width: '24px', padding: '0.625rem 1rem', borderBottom: '1px solid var(--color-border)' }} />
            </tr>
          </thead>
          <tbody>
            {runs.map((run) => {
              const isExpanded = expandedRunId === run.id
              return (
                <>
                  <tr
                    key={run.id}
                    onClick={() => setExpandedRunId(isExpanded ? null : run.id)}
                    style={{ cursor: 'pointer', userSelect: 'none' }}
                  >
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>#{run.id}</td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      <span style={triggerBadgeStyle(run.triggerSource)}>
                        {run.runType}
                      </span>
                    </td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      <span style={statusBadgeStyle(run.status)}>{run.status}</span>
                    </td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)', color: 'var(--color-text-muted)' }}>
                      {run.triggerSource ?? '--'}
                    </td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      {formatDate(run.startedAt)}
                    </td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      {duration(run.startedAt, run.completedAt)}
                    </td>
                    <td style={{ padding: '0.625rem 1rem', borderBottom: '1px solid rgba(255,255,255,0.03)' }}>
                      {isExpanded ? <ChevronDown size={14} /> : <ChevronRight size={14} />}
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
    <div style={{ padding: '1.5rem', maxWidth: '1280px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', alignItems: 'center', justifyContent: 'space-between', marginBottom: '1.5rem' }}>
        <div style={{ fontSize: '1.25rem', fontWeight: 700 }}>Ingestion Management</div>
        <div style={{ display: 'flex', alignItems: 'center', gap: '0.75rem' }}>
          <div style={{ display: 'flex', alignItems: 'center', gap: '0.5rem', fontSize: '0.6875rem', color: 'var(--color-text-muted)', background: 'var(--color-surface-secondary, #252833)', padding: '0.375rem 0.75rem', borderRadius: '999px' }}>
            <span style={{ width: '6px', height: '6px', borderRadius: '50%', background: '#22c55e', animation: 'pulse 2s infinite' }} />
            Auto-refresh: 10s
          </div>
          <button
            onClick={handleRefresh}
            style={{
              padding: '0.375rem 0.75rem', borderRadius: '0.375rem', fontSize: '0.75rem', fontWeight: 600,
              cursor: 'pointer', border: '1px solid var(--color-border)', background: 'none',
              color: 'var(--color-text-secondary, #9ca3af)', display: 'inline-flex', alignItems: 'center', gap: '0.375rem',
            }}
          >
            <RefreshCw size={12} />
            Refresh
          </button>
        </div>
      </div>

      {/* Pulse animation */}
      <style>{`
        @keyframes pulse {
          0%, 100% { opacity: 1; }
          50% { opacity: 0.3; }
        }
      `}</style>

      {/* Summary Stats */}
      <SummaryStats stats={stats} />

      {/* Instrument Type Stats */}
      <InstrumentTypeStats stats={stats} />

      {/* Workflows */}
      <WorkflowsSection activeRun={activeRun} />

      {/* Recent Runs */}
      <div style={{ marginBottom: '1.5rem' }}>
        <div style={{ fontSize: '0.75rem', fontWeight: 700, textTransform: 'uppercase', letterSpacing: '0.05em', color: 'var(--color-text-muted)', marginBottom: '0.75rem' }}>
          Recent Runs
        </div>
        <RecentRunsTable runs={runs} />
      </div>
    </div>
  )
}
