import { useState } from 'react';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { RefreshCw, Play, ChevronDown, ChevronUp } from 'lucide-react';
import {
  triggerFullIngestion,
  triggerStockIngestion,
  triggerStockEnrichment,
  triggerEtfComUniverse,
  triggerEtfComEnrichment,
  getIngestionRuns,
  getRunSteps,
  getRecentErrors,
  getErrorSummary,
  getIngestionStats,
  type IngestionRun,
  type IngestionStep,
  type IngestionError,
  type ErrorSummary,
  type IngestionStats,
  type TriggerIngestionResponse,
} from '../../services/adminService';
import { Card, CardContent } from '../../components/ui/card';
import { Badge } from '../../components/ui/badge';
import { Button } from '../../components/ui/button';
import { Skeleton } from '../../components/ui/skeleton';

const PAGE_SIZE = 10;
const STEP_NAMES = ['EODHD_UNIVERSE', 'AV_STOCK_INGESTION', 'ETFCOM_ETF_UNIVERSE', 'ETFCOM_ETF_ENRICHMENT'];
const ERROR_TYPES = ['API_ERROR', 'PARSE_ERROR', 'DUPLICATE_ISIN', 'VALIDATION_ERROR', 'UNKNOWN'];

function formatDate(dateString: string | null) {
  if (!dateString) return '—';
  return new Date(dateString).toLocaleString();
}

function statusVariant(status: string): 'default' | 'secondary' | 'destructive' | 'outline' {
  switch (status.toUpperCase()) {
    case 'COMPLETED':
    case 'SUCCESS':
      return 'default';
    case 'FAILED':
    case 'ERROR':
      return 'destructive';
    case 'RUNNING':
    case 'PENDING':
      return 'secondary';
    default:
      return 'outline';
  }
}

// ─────────────────────────────────────────────
// Stat Card
// ─────────────────────────────────────────────

function StatCard({ label, value }: { label: string; value: number | string }) {
  return (
    <Card>
      <CardContent style={{ padding: '1rem' }}>
        <div style={{ fontSize: '1.75rem', fontWeight: 700, fontVariantNumeric: 'tabular-nums' }}>
          {value}
        </div>
        <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)', marginTop: '0.25rem' }}>
          {label}
        </div>
      </CardContent>
    </Card>
  );
}

function IngestionStatsPanel() {
  const { data, isLoading } = useQuery<IngestionStats>({
    queryKey: ['ingestion-stats'],
    queryFn: getIngestionStats,
    refetchInterval: 30_000,
  });

  if (isLoading || !data) {
    return (
      <div style={{ display: 'grid', gridTemplateColumns: 'repeat(4, 1fr)', gap: '1rem', marginBottom: '1.5rem' }}>
        {[...Array(4)].map((_, i) => <Skeleton key={i} style={{ height: '80px' }} />)}
      </div>
    );
  }

  return (
    <div style={{ display: 'grid', gridTemplateColumns: 'repeat(2, 1fr)', gap: '0.75rem', marginBottom: '1.5rem' }}>
      <StatCard label="Stocks with raw data" value={`${data.stocksWithRawData.toLocaleString()} / ${data.totalStocks.toLocaleString()}`} />
      <StatCard label="Stocks pending ingestion" value={data.stocksPendingIngestion.toLocaleString()} />
      <StatCard label="ETFs enriched" value={`${data.etfsEnriched.toLocaleString()} / ${data.totalEtfs.toLocaleString()}`} />
      <StatCard label="Errors (last 24h)" value={data.errorsLast24h.toLocaleString()} />
    </div>
  );
}

// ─────────────────────────────────────────────
// Workflow Card
// ─────────────────────────────────────────────

function WorkflowCard({ title, description, onTrigger }: {
  title: string;
  description: string;
  onTrigger: () => Promise<TriggerIngestionResponse>;
}) {
  const queryClient = useQueryClient();
  const mutation = useMutation({
    mutationFn: onTrigger,
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['ingestion-runs'] });
      queryClient.invalidateQueries({ queryKey: ['ingestion-stats'] });
    },
  });

  return (
    <Card>
      <CardContent style={{ padding: '1rem', display: 'flex', flexDirection: 'column', gap: '0.5rem' }}>
        <div>
          <div style={{ fontWeight: 600, fontSize: '0.875rem' }}>{title}</div>
          <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>{description}</div>
        </div>
        <Button
          size="sm"
          variant="outline"
          onClick={() => mutation.mutate()}
          disabled={mutation.isPending}
          style={{ alignSelf: 'flex-start' }}
        >
          <Play size={12} style={{ marginRight: '0.25rem' }} />
          {mutation.isPending ? 'Running…' : 'Trigger'}
        </Button>
        {mutation.isSuccess && (
          <div style={{ fontSize: '0.75rem', color: 'var(--color-success)' }}>
            Run #{mutation.data.runId} started
          </div>
        )}
        {mutation.isError && (
          <div style={{ fontSize: '0.75rem', color: 'var(--color-destructive)' }}>
            {(mutation.error as Error).message}
          </div>
        )}
      </CardContent>
    </Card>
  );
}

// ─────────────────────────────────────────────
// Run Detail (expandable steps)
// ─────────────────────────────────────────────

function RunDetailRow({ run }: { run: IngestionRun }) {
  const [expanded, setExpanded] = useState(false);
  const { data: steps, isLoading } = useQuery<IngestionStep[]>({
    queryKey: ['run-steps', run.id],
    queryFn: () => getRunSteps(run.id),
    enabled: expanded,
  });

  return (
    <>
      <tr style={{ cursor: 'pointer', userSelect: 'none' }} onClick={() => setExpanded((e) => !e)}>
        <td style={{ padding: '0.5rem 0.75rem' }}>#{run.id}</td>
        <td style={{ padding: '0.5rem 0.75rem' }}>{run.runType}</td>
        <td style={{ padding: '0.5rem 0.75rem' }}>
          <Badge variant={statusVariant(run.status)}>{run.status}</Badge>
        </td>
        <td style={{ padding: '0.5rem 0.75rem', fontSize: '0.75rem' }}>{formatDate(run.startedAt)}</td>
        <td style={{ padding: '0.5rem 0.75rem', fontSize: '0.75rem' }}>{formatDate(run.completedAt)}</td>
        <td style={{ padding: '0.5rem 0.75rem' }}>{run.stepCount} steps</td>
        <td style={{ padding: '0.5rem 0.75rem' }}>
          {expanded ? <ChevronUp size={14} /> : <ChevronDown size={14} />}
        </td>
      </tr>
      {expanded && (
        <tr>
          <td colSpan={7} style={{ padding: '0 0.75rem 0.75rem 2rem', background: 'var(--color-surface-secondary)' }}>
            {isLoading ? (
              <Skeleton style={{ height: '60px' }} />
            ) : steps && steps.length > 0 ? (
              <table style={{ width: '100%', fontSize: '0.75rem', borderCollapse: 'collapse' }}>
                <thead>
                  <tr style={{ color: 'var(--color-text-muted)' }}>
                    <th style={{ textAlign: 'left', padding: '0.25rem 0.5rem' }}>Step</th>
                    <th style={{ textAlign: 'left', padding: '0.25rem 0.5rem' }}>Status</th>
                    <th style={{ textAlign: 'right', padding: '0.25rem 0.5rem' }}>Processed</th>
                    <th style={{ textAlign: 'right', padding: '0.25rem 0.5rem' }}>Created</th>
                    <th style={{ textAlign: 'right', padding: '0.25rem 0.5rem' }}>Updated</th>
                    <th style={{ textAlign: 'right', padding: '0.25rem 0.5rem' }}>Failed</th>
                    <th style={{ textAlign: 'right', padding: '0.25rem 0.5rem' }}>Errors</th>
                  </tr>
                </thead>
                <tbody>
                  {steps.map((step) => (
                    <tr key={step.id}>
                      <td style={{ padding: '0.25rem 0.5rem' }}>{step.stepName}</td>
                      <td style={{ padding: '0.25rem 0.5rem' }}>
                        <Badge variant={statusVariant(step.status)} style={{ fontSize: '0.65rem' }}>{step.status}</Badge>
                      </td>
                      <td style={{ padding: '0.25rem 0.5rem', textAlign: 'right' }}>{step.recordsProcessed}</td>
                      <td style={{ padding: '0.25rem 0.5rem', textAlign: 'right' }}>{step.recordsCreated}</td>
                      <td style={{ padding: '0.25rem 0.5rem', textAlign: 'right' }}>{step.recordsUpdated}</td>
                      <td style={{ padding: '0.25rem 0.5rem', textAlign: 'right' }}>{step.recordsFailed}</td>
                      <td style={{ padding: '0.25rem 0.5rem', textAlign: 'right' }}>{step.errorCount}</td>
                    </tr>
                  ))}
                </tbody>
              </table>
            ) : (
              <div style={{ color: 'var(--color-text-muted)', padding: '0.5rem' }}>No steps recorded</div>
            )}
          </td>
        </tr>
      )}
    </>
  );
}

// ─────────────────────────────────────────────
// Main Page
// ─────────────────────────────────────────────

export function AdminPage() {
  const queryClient = useQueryClient();
  const [stepNameFilter, setStepNameFilter] = useState('');
  const [errorTypeFilter, setErrorTypeFilter] = useState('');
  const [runsPage, setRunsPage] = useState(0);

  const { data: runs = [], isLoading: runsLoading } = useQuery<IngestionRun[]>({
    queryKey: ['ingestion-runs'],
    queryFn: () => getIngestionRuns(100),
    refetchInterval: 30_000,
  });

  const { data: errorSummary = [] } = useQuery<ErrorSummary[]>({
    queryKey: ['error-summary'],
    queryFn: getErrorSummary,
    refetchInterval: 60_000,
  });

  const { data: errors = [], isLoading: errorsLoading } = useQuery<IngestionError[]>({
    queryKey: ['ingestion-errors', stepNameFilter, errorTypeFilter],
    queryFn: () => getRecentErrors({
      stepName: stepNameFilter || undefined,
      errorType: errorTypeFilter || undefined,
      limit: 100,
    }),
    refetchInterval: 60_000,
  });

  const handleRefresh = () => {
    queryClient.invalidateQueries({ queryKey: ['ingestion-runs'] });
    queryClient.invalidateQueries({ queryKey: ['ingestion-stats'] });
    queryClient.invalidateQueries({ queryKey: ['error-summary'] });
    queryClient.invalidateQueries({ queryKey: ['ingestion-errors'] });
  };

  const workflows = [
    { title: 'Full Pipeline', description: 'Stock + ETF pipelines in parallel', trigger: triggerFullIngestion },
    { title: 'Stock Ingestion', description: 'Build stock universe from EODHD', trigger: triggerStockIngestion },
    { title: 'AV Stock Ingestion', description: 'Fetch raw data from Alpha Vantage', trigger: triggerStockEnrichment },
    { title: 'ETF Ingestion', description: 'Build ETF universe from etf.com', trigger: triggerEtfComUniverse },
    { title: 'ETF Enrichment', description: 'Enrich ETFs with holdings & sectors', trigger: triggerEtfComEnrichment },
  ];

  const totalPages = Math.ceil(runs.length / PAGE_SIZE);
  const pageRuns = runs.slice(runsPage * PAGE_SIZE, (runsPage + 1) * PAGE_SIZE);

  return (
    <div style={{ padding: '1.5rem', maxWidth: '1280px', margin: '0 auto' }}>
      {/* Header */}
      <div style={{ display: 'flex', justifyContent: 'space-between', alignItems: 'center', marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>Admin — Ingestion Pipeline</h1>
        <Button variant="outline" size="sm" onClick={handleRefresh}>
          <RefreshCw size={14} style={{ marginRight: '0.25rem' }} />
          Refresh
        </Button>
      </div>

      {/* Stats Panel */}
      <IngestionStatsPanel />

      {/* Workflow Cards */}
      <section style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Workflows</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(5, 1fr)', gap: '0.75rem' }}>
          {workflows.map((w) => (
            <WorkflowCard
              key={w.title}
              title={w.title}
              description={w.description}
              onTrigger={w.trigger}
            />
          ))}
        </div>
      </section>

      {/* Error Summary */}
      <section style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Error Summary (last 24h)</h2>
        {errorSummary.length === 0 ? (
          <p style={{ color: 'var(--color-text-muted)' }}>No errors in the last 24 hours</p>
        ) : (
          <div style={{ display: 'flex', gap: '0.5rem', flexWrap: 'wrap', marginBottom: '1rem' }}>
            {errorSummary.map((s) => (
              <Badge
                key={s.errorType}
                variant="destructive"
                style={{ cursor: 'pointer' }}
                onClick={() => setErrorTypeFilter(s.errorType === errorTypeFilter ? '' : s.errorType)}
              >
                {s.errorType}: {s.count}
              </Badge>
            ))}
          </div>
        )}

        <div style={{ display: 'flex', gap: '0.75rem', marginBottom: '0.75rem', alignItems: 'center' }}>
          <select
            value={stepNameFilter}
            onChange={(e) => setStepNameFilter(e.target.value)}
            style={{ padding: '0.375rem 0.5rem', borderRadius: '0.375rem', border: '1px solid var(--color-border)', background: 'var(--color-surface)', fontSize: '0.8125rem' }}
          >
            <option value="">All steps</option>
            {STEP_NAMES.map((s) => <option key={s} value={s}>{s}</option>)}
          </select>
          <select
            value={errorTypeFilter}
            onChange={(e) => setErrorTypeFilter(e.target.value)}
            style={{ padding: '0.375rem 0.5rem', borderRadius: '0.375rem', border: '1px solid var(--color-border)', background: 'var(--color-surface)', fontSize: '0.8125rem' }}
          >
            <option value="">All error types</option>
            {ERROR_TYPES.map((t) => <option key={t} value={t}>{t}</option>)}
          </select>
          {(stepNameFilter || errorTypeFilter) && (
            <Button size="sm" variant="ghost" onClick={() => { setStepNameFilter(''); setErrorTypeFilter(''); }}>
              Clear filters
            </Button>
          )}
        </div>

        {errorsLoading ? (
          <Skeleton style={{ height: '100px' }} />
        ) : errors.length === 0 ? (
          <p style={{ color: 'var(--color-text-muted)' }}>No errors found</p>
        ) : (
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.8125rem' }}>
              <thead>
                <tr style={{ borderBottom: '1px solid var(--color-border)', color: 'var(--color-text-muted)' }}>
                  <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Step ID</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Error Type</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Code</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem', maxWidth: '320px' }}>Message</th>
                  <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Time</th>
                </tr>
              </thead>
              <tbody>
                {errors.map((err) => (
                  <tr key={err.id} style={{ borderBottom: '1px solid var(--color-border)' }}>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{err.stepId}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{err.errorType}</td>
                    <td style={{ padding: '0.5rem 0.75rem' }}>{err.errorCode ?? '—'}</td>
                    <td style={{ padding: '0.5rem 0.75rem', maxWidth: '320px', overflow: 'hidden', textOverflow: 'ellipsis', whiteSpace: 'nowrap' }} title={err.errorMessage ?? ''}>
                      {err.errorMessage ?? '—'}
                    </td>
                    <td style={{ padding: '0.5rem 0.75rem', fontSize: '0.75rem' }}>{formatDate(err.createdAt)}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
        )}
      </section>

      {/* Recent Runs */}
      <section>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Recent Runs</h2>
        {runsLoading ? (
          <Skeleton style={{ height: '200px' }} />
        ) : runs.length === 0 ? (
          <p style={{ color: 'var(--color-text-muted)' }}>No ingestion runs found</p>
        ) : (
          <>
            <div style={{ overflowX: 'auto' }}>
              <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
                <thead>
                  <tr style={{ borderBottom: '1px solid var(--color-border)', color: 'var(--color-text-muted)' }}>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>ID</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Type</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Status</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Started</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Completed</th>
                    <th style={{ textAlign: 'left', padding: '0.5rem 0.75rem' }}>Steps</th>
                    <th style={{ padding: '0.5rem 0.75rem' }}></th>
                  </tr>
                </thead>
                <tbody>
                  {pageRuns.map((run) => (
                    <RunDetailRow key={run.id} run={run} />
                  ))}
                </tbody>
              </table>
            </div>
            {totalPages > 1 && (
              <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '0.75rem', justifyContent: 'flex-end' }}>
                <Button size="sm" variant="outline" onClick={() => setRunsPage((p) => Math.max(0, p - 1))} disabled={runsPage === 0}>
                  Prev
                </Button>
                <span style={{ fontSize: '0.8125rem', color: 'var(--color-text-muted)' }}>
                  Page {runsPage + 1} of {totalPages}
                </span>
                <Button size="sm" variant="outline" onClick={() => setRunsPage((p) => Math.min(totalPages - 1, p + 1))} disabled={runsPage >= totalPages - 1}>
                  Next
                </Button>
              </div>
            )}
          </>
        )}
      </section>
    </div>
  );
}
