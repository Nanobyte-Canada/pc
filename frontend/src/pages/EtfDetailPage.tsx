import { useState, useMemo } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';
import { useChartTheme } from '@/hooks/useChartTheme';
import { AgCharts } from 'ag-charts-react';
import type { AgChartOptions } from 'ag-charts-community';
import './EtfDetailPage.css';

interface LabeledField {
  label: string;
  value: string;
}

interface PerformancePeriod {
  period: string;
  navReturn: number | null;
  priceReturn: number | null;
}

interface TopHolding {
  ticker: string;
  name: string;
  weight: number;
}

interface SectorEntry {
  name: string;
  weight: number;
}

interface IndustryEntry {
  name: string;
  weight: number;
  parentSector: string | null;
}

interface EtfDetail {
  id: number;
  symbol: string;
  name: string;
  issuer: string | null;
  assetClass: string | null;
  inceptionDate: string | null;
  enrichmentStatus: string;
  summary: LabeledField[] | null;
  description: string | null;
  portfolio: LabeledField[] | null;
  performance: PerformancePeriod[] | null;
  topHoldings: TopHolding[] | null;
  holdingsCount: number | null;
  sectorBreakdown: {
    sectors: SectorEntry[];
    industries: IndustryEntry[];
  } | null;
}

async function fetchEtf(symbol: string): Promise<EtfDetail> {
  const res = await apiFetch(`/api/v1/etfs/symbol/${encodeURIComponent(symbol)}`);
  if (!res.ok) throw new Error('ETF not found');
  return res.json();
}

const SECTOR_COLORS = [
  '#2a8a81', '#3b82f6', '#f59e0b', '#ef4444', '#8b5cf6',
  '#06b6d4', '#ec4899', '#84cc16', '#f97316', '#6366f1', '#14b8a6',
];

const HOLDINGS_PER_PAGE = 15;

function pct(v: number): string {
  return `${(v * 100).toFixed(2)}%`;
}

// ---------------------------------------------------------------------------
// Sections
// ---------------------------------------------------------------------------

function SummarySection({ summary, description, portfolio }: { summary: LabeledField[] | null; description: string | null; portfolio: LabeledField[] | null }) {
  if (!summary && !portfolio) return <div className="etf-empty-section">No fund summary data available</div>;

  const renderFields = (fields: LabeledField[] | null, sectionLabel?: string) => {
    if (!fields || fields.length === 0) return null;
    return (
      <>
        {sectionLabel && <div className="etf-section-subtitle">{sectionLabel}</div>}
        <div className="etf-summary-grid">
          {fields.map((f) => (
            <div key={f.label} className="etf-summary-card">
              <div className="etf-summary-card-label">{f.label}</div>
              <div className="etf-summary-value">{f.value}</div>
            </div>
          ))}
        </div>
      </>
    );
  };

  return (
    <div className="etf-section">
      <h2 className="etf-section-title">Fund Overview</h2>
      {renderFields(summary)}
      {description && <p className="etf-description">{description}</p>}
      {renderFields(portfolio, 'Portfolio Characteristics')}
    </div>
  );
}

function PerformanceSection({ performance }: { performance: PerformancePeriod[] | null }) {
  const chartTheme = useChartTheme();

  const chartData = useMemo(() => {
    if (!performance || performance.length === 0) return null;
    return performance.map(p => ({
      period: p.period,
      nav: p.navReturn != null ? Number((p.navReturn * 100).toFixed(2)) : null,
      price: p.priceReturn != null ? Number((p.priceReturn * 100).toFixed(2)) : null,
    }));
  }, [performance]);

  if (!chartData || chartData.length === 0) {
    return (
      <div className="etf-section">
        <h2 className="etf-section-title">Performance</h2>
        <div className="etf-empty-section">No performance data available</div>
      </div>
    );
  }

  const options: AgChartOptions = {
    theme: chartTheme.theme,
    height: 320,
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar',
        xKey: 'period',
        yKey: 'nav',
        yName: 'NAV Return',
        fill: '#2a8a81',
      },
      {
        type: 'bar',
        xKey: 'period',
        yKey: 'price',
        yName: 'Price Return',
        fill: '#3b82f6',
      },
    ],
    axes: [
      { type: 'category', position: 'bottom' },
      {
        type: 'number',
        position: 'left',
        label: { formatter: (params: any) => `${params.value}%` },
      },
    ],
    legend: { position: 'bottom' },
  };

  return (
    <div className="etf-section">
      <h2 className="etf-section-title">Performance</h2>
      <AgCharts options={options} />
    </div>
  );
}

function HoldingsSection({
  holdings,
  holdingsCount,
}: {
  holdings: TopHolding[] | null;
  holdingsCount: number | null;
}) {
  const chartTheme = useChartTheme();
  const [page, setPage] = useState(0);

  const totalPages = Math.ceil((holdings?.length ?? 0) / HOLDINGS_PER_PAGE);
  const pageHoldings = holdings?.slice(page * HOLDINGS_PER_PAGE, (page + 1) * HOLDINGS_PER_PAGE) ?? [];

  // Donut chart data: top 10 + Other
  const donutData = useMemo(() => {
    if (!holdings || holdings.length === 0) return [];
    const top10 = holdings.slice(0, 10).map((h, i) => ({
      label: h.ticker,
      value: Number((h.weight * 100).toFixed(2)),
      color: SECTOR_COLORS[i % SECTOR_COLORS.length],
    }));
    const otherWeight = holdings.slice(10).reduce((sum, h) => sum + h.weight, 0);
    if (otherWeight > 0) {
      top10.push({
        label: 'Other',
        value: Number((otherWeight * 100).toFixed(2)),
        color: '#94a3b8',
      });
    }
    return top10;
  }, [holdings]);

  if (!holdings || holdings.length === 0) {
    return (
      <div className="etf-section">
        <h2 className="etf-section-title">Holdings</h2>
        <div className="etf-empty-section">No holdings data available</div>
      </div>
    );
  }

  const donutOptions: AgChartOptions = {
    theme: chartTheme.theme,
    height: 340,
    background: { fill: 'transparent' },
    series: [
      {
        type: 'donut',
        calloutLabelKey: 'label',
        angleKey: 'value',
        innerRadiusRatio: 0.5,
        data: donutData,
        fills: donutData.map(d => d.color),
        tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
      },
    ] as any,
    legend: { enabled: false },
  };

  return (
    <div className="etf-section">
      <h2 className="etf-section-title">
        Holdings{holdingsCount != null ? ` (${holdingsCount})` : ` (${holdings.length})`}
      </h2>
      <div className="etf-holdings-layout">
        <div>
          <table className="etf-holdings-table">
            <thead>
              <tr>
                <th>#</th>
                <th>Ticker</th>
                <th>Name</th>
                <th style={{ textAlign: 'right' }}>Weight %</th>
              </tr>
            </thead>
            <tbody>
              {pageHoldings.map((h, i) => (
                <tr key={h.ticker + i}>
                  <td>{page * HOLDINGS_PER_PAGE + i + 1}</td>
                  <td style={{ fontWeight: 600 }}>{h.ticker}</td>
                  <td>{h.name}</td>
                  <td style={{ textAlign: 'right' }}>{pct(h.weight)}</td>
                </tr>
              ))}
            </tbody>
          </table>
          {totalPages > 1 && (
            <div className="etf-holdings-pagination">
              <span className="pagination-info">
                Page {page + 1} of {totalPages}
              </span>
              <div className="pagination-controls">
                <button disabled={page === 0} onClick={() => setPage(p => p - 1)}>Prev</button>
                <button disabled={page >= totalPages - 1} onClick={() => setPage(p => p + 1)}>Next</button>
              </div>
            </div>
          )}
        </div>
        <div>
          <AgCharts options={donutOptions} />
          <div className="etf-legend">
            {donutData.map(d => (
              <div key={d.label} className="etf-legend-item">
                <div className="etf-legend-dot" style={{ backgroundColor: d.color }} />
                <span className="etf-legend-label">{d.label}</span>
                <span className="etf-legend-value">{d.value}%</span>
              </div>
            ))}
          </div>
        </div>
      </div>
    </div>
  );
}

function SectorBreakdownSection({ sectorBreakdown }: { sectorBreakdown: EtfDetail['sectorBreakdown'] }) {
  const chartTheme = useChartTheme();

  const { sectors, industries } = useMemo(() => {
    if (!sectorBreakdown) return { sectors: [], industries: [] };

    const sectorList = sectorBreakdown.sectors.map((s, i) => ({
      label: s.name,
      value: Number((s.weight * 100).toFixed(1)),
      color: SECTOR_COLORS[i % SECTOR_COLORS.length],
    }));

    const industryList = sectorBreakdown.industries.map((ind, i) => {
      const sectorIndex = sectorList.findIndex(s => s.label === ind.parentSector);
      const baseColor = sectorIndex >= 0 ? SECTOR_COLORS[sectorIndex % SECTOR_COLORS.length] : SECTOR_COLORS[i % SECTOR_COLORS.length];
      return {
        label: ind.name,
        value: Number((ind.weight * 100).toFixed(1)),
        color: baseColor + 'cc',
      };
    });

    return { sectors: sectorList, industries: industryList };
  }, [sectorBreakdown]);

  if (sectors.length === 0) {
    return (
      <div className="etf-section">
        <h2 className="etf-section-title">Sector & Industry Breakdown</h2>
        <div className="etf-empty-section">No sector breakdown data available</div>
      </div>
    );
  }

  const seriesList: any[] = [
    {
      type: 'donut',
      calloutLabelKey: 'label',
      angleKey: 'value',
      innerRadiusRatio: 0,
      outerRadiusRatio: industries.length > 0 ? 0.55 : 0.85,
      data: sectors,
      fills: sectors.map(d => d.color),
      tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
    },
  ];

  if (industries.length > 0) {
    seriesList.push({
      type: 'donut',
      calloutLabelKey: 'label',
      angleKey: 'value',
      innerRadiusRatio: 0.6,
      outerRadiusRatio: 1,
      data: industries,
      fills: industries.map(d => d.color),
      tooltip: { renderer: (params: any) => ({ content: `${params.datum.label}: ${params.datum.value}%` }) },
    });
  }

  const options: AgChartOptions = {
    theme: chartTheme.theme,
    height: 380,
    background: { fill: 'transparent' },
    series: seriesList as any,
    legend: { enabled: false },
  };

  const top5 = sectors.slice(0, 5);

  return (
    <div className="etf-section">
      <h2 className="etf-section-title">Sector & Industry Breakdown</h2>
      <AgCharts options={options} />
      <div className="etf-legend">
        {top5.map(s => (
          <div key={s.label} className="etf-legend-item">
            <div className="etf-legend-dot" style={{ backgroundColor: s.color }} />
            <span className="etf-legend-label">{s.label}</span>
            <span className="etf-legend-value">{s.value}%</span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Page
// ---------------------------------------------------------------------------

export function EtfDetailPage() {
  const { symbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['etf-detail', symbol],
    queryFn: () => fetchEtf(symbol!),
    enabled: !!symbol,
  });

  if (isLoading) {
    return <div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading...</div>;
  }

  if (isError || !data) {
    return <div style={{ padding: '2rem', color: 'var(--destructive)' }}>ETF not found.</div>;
  }

  return (
    <div className="etf-detail-page">
      <button className="etf-detail-back" onClick={() => navigate('/screener/etfs')}>
        &larr; Back to ETFs
      </button>

      <div className="etf-detail-header">
        <h1>{data.symbol}</h1>
        <p>{data.name}</p>
      </div>

      {/* Basic Info */}
      <div className="etf-summary-grid">
        {[
          { label: 'Issuer', value: data.issuer },
          { label: 'Asset Class', value: data.assetClass },
          { label: 'Inception Date', value: data.inceptionDate },
          { label: 'Enrichment Status', value: data.enrichmentStatus },
        ]
          .filter(item => item.value)
          .map(({ label, value }) => (
            <div key={label} className="etf-summary-card">
              <div className="etf-summary-card-label">{label}</div>
              <div className="etf-summary-value">{value}</div>
            </div>
          ))}
      </div>

      <SummarySection summary={data.summary} description={data.description} portfolio={data.portfolio} />

      <PerformanceSection performance={data.performance} />

      <HoldingsSection
        holdings={data.topHoldings}
        holdingsCount={data.holdingsCount}
      />

      <SectorBreakdownSection sectorBreakdown={data.sectorBreakdown} />
    </div>
  );
}
