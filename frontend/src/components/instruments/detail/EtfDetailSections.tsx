import { useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgChartOptions } from 'ag-charts-community';
import { useChartTheme } from '@/hooks/useChartTheme';
import type { InstrumentDetail } from '@/types/screener';
import './EtfDetailSections.css';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Props {
  data: InstrumentDetail;
}

interface HoldingEntry {
  ticker: string;
  name: string;
  weight: number;
}

interface CapEntry {
  size: string;
  pct: number;
}

interface SectorEntry {
  name: string;
  weight: number;
}

interface RegionEntry {
  name: string;
  weight: number;
}

interface AssetEntry {
  type: string;
  pct: number;
}

interface ValuationRow {
  metric: string;
  portfolio: number | null;
  category: number | null;
  isDividendYield?: boolean;
}

interface GrowthRow {
  label: string;
  value: number | null;
}

// ---------------------------------------------------------------------------
// Constants
// ---------------------------------------------------------------------------

const CHART_COLORS = [
  '#6366f1', '#34d399', '#38bdf8', '#fbbf24', '#f472b6',
  '#ef4444', '#a78bfa', '#fb923c', '#14b8a6', '#e879f9', '#374151',
];

const GEO_COLORS = [
  '#6366f1', '#34d399', '#fbbf24', '#f472b6', '#38bdf8',
  '#ef4444', '#a78bfa', '#fb923c', '#14b8a6', '#e879f9',
];

const CAP_COLORS: Record<string, string> = {
  Giant: '#6366f1',
  Large: '#818cf8',
  Medium: '#34d399',
  Small: '#fbbf24',
  Micro: '#f472b6',
};

const ASSET_COLORS = ['#6366f1', '#34d399', '#fbbf24', '#374151', '#38bdf8', '#ef4444'];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function parseNum(v: unknown): number | null {
  if (v == null || v === '' || v === 'None' || v === 'N/A') return null;
  const n = Number(v);
  return isNaN(n) ? null : n;
}

function fmtPct(v: number | null, decimals = 1): string {
  if (v == null) return '\u2014';
  return `${v.toFixed(decimals)}%`;
}

function fmtNum(v: number | null, decimals = 2): string {
  if (v == null) return '\u2014';
  return v.toFixed(decimals);
}

function fmtLargeNumber(v: number | null): string {
  if (v == null) return '\u2014';
  const abs = Math.abs(v);
  if (abs >= 1e6) return `$${(v / 1e6).toFixed(0)}T`;
  if (abs >= 1e3) return `$${(v / 1e3).toFixed(0)}B`;
  return `$${v.toFixed(0)}M`;
}

function namedWeightsToArray(obj: Record<string, unknown> | null | undefined, pctKey = 'Equity_%'): { name: string; weight: number }[] {
  if (!obj || typeof obj !== 'object') return [];
  const result: { name: string; weight: number }[] = [];
  for (const [name, val] of Object.entries(obj)) {
    if (val && typeof val === 'object') {
      const w = parseNum(val[pctKey]);
      if (w != null && w > 0) {
        result.push({ name, weight: w });
      }
    }
  }
  result.sort((a, b) => b.weight - a.weight);
  return result;
}

// ---------------------------------------------------------------------------
// Data extraction
// ---------------------------------------------------------------------------

function extractPerformance(etfData: Record<string, unknown> | null): { period: string; value: number }[] {
  if (!etfData?.Performance) return [];
  const perf = etfData.Performance;
  const mapping: [string, string][] = [
    ['YTD', 'Returns_YTD'],
    ['1Y', 'Returns_1Y'],
    ['3Y', 'Returns_3Y'],
    ['5Y', 'Returns_5Y'],
    ['10Y', 'Returns_10Y'],
  ];
  const result: { period: string; value: number }[] = [];
  for (const [label, key] of mapping) {
    const v = parseNum(perf[key]);
    if (v != null) {
      result.push({ period: label, value: v });
    }
  }
  return result;
}

function extractRiskMetrics(etfData: Record<string, unknown> | null, technicals: Record<string, unknown> | null) {
  const perf = etfData?.Performance ?? {};
  return {
    vol1y: parseNum(perf['1y_Volatility']),
    vol3y: parseNum(perf['3y_Volatility']),
    sharpe3y: parseNum(perf['3y_SharpRatio']),
    beta: parseNum(technicals?.Beta),
  };
}

function extractTopHoldings(etfData: Record<string, unknown> | null): { holdings: HoldingEntry[]; holdingsCount: number | null } {
  const holdingsCount = parseNum(etfData?.Holdings_Count);
  // EODHD holdings are keyed by ticker (e.g., "AAPL.US": { Code, Name, Assets_% })
  const holdingsObj = etfData?.Top_10_Holdings ?? etfData?.Holdings;
  if (!holdingsObj || typeof holdingsObj !== 'object') return { holdings: [], holdingsCount };

  const holdings: HoldingEntry[] = Object.values(holdingsObj)
    .map((h: unknown) => {
      const holding = h as Record<string, unknown>;
      return {
        ticker: (holding?.Code ?? '') as string,
        name: (holding?.Name ?? '') as string,
        weight: parseNum(holding?.['Assets_%']) ?? 0,
      };
    })
    .filter((h) => h.name && h.weight > 0)
    .sort((a, b) => b.weight - a.weight)
    .slice(0, 10);
  return { holdings, holdingsCount };
}

function extractMarketCap(etfData: Record<string, unknown> | null): { caps: CapEntry[]; avgCap: number | null } {
  const capObj = etfData?.Market_Capitalisation;
  let caps: CapEntry[] = [];
  if (capObj && typeof capObj === 'object') {
    // EODHD format: { "Big": "39.15", "Mega": "30.97", "Small": "4.18", ... }
    caps = Object.entries(capObj)
      .map(([size, val]) => ({
        size,
        pct: parseNum(val) ?? 0,
      }))
      .filter((c) => c.size && c.pct > 0)
      .sort((a, b) => b.pct - a.pct);
  }
  const avgCap = parseNum(etfData?.Average_Mkt_Cap_Mil);
  return { caps, avgCap };
}

function extractSectorWeights(etfData: Record<string, unknown> | null): SectorEntry[] {
  return namedWeightsToArray(etfData?.Sector_Weights);
}

function extractRegions(etfData: Record<string, unknown> | null): RegionEntry[] {
  return namedWeightsToArray(etfData?.World_Regions);
}

function extractAssetAllocation(etfData: Record<string, unknown> | null): AssetEntry[] {
  const alloc = etfData?.Asset_Allocation;
  if (!alloc || typeof alloc !== 'object') return [];
  // EODHD asset allocation keyed by category ("Bond", "Cash", "Stock US", etc.)
  // Each value has Net_Assets_% or Net_%
  return Object.entries(alloc)
    .map(([type, val]: [string, unknown]) => {
      const entry = val as Record<string, unknown>;
      return {
        type,
        pct: parseNum(entry?.['Net_Assets_%']) ?? parseNum(entry?.['Net_%']) ?? 0,
      };
    })
    .filter((a) => a.type && a.pct > 0)
    .sort((a, b) => b.pct - a.pct);
}

function extractValuationRows(etfData: Record<string, unknown> | null): ValuationRow[] {
  const vg = etfData?.Valuations_Growth;
  if (!vg) return [];
  const portfolio = vg.Valuations_Rates_Portfolio ?? {};
  const category = vg.Valuations_Rates_To_Category ?? {};

  const mapping: { metric: string; key: string; altKey?: string; isDividendYield?: boolean }[] = [
    { metric: 'Price/Earnings', key: 'Price/Prospective Earnings', altKey: 'Price/Earnings' },
    { metric: 'Price/Book', key: 'Price/Book' },
    { metric: 'Price/Sales', key: 'Price/Sales' },
    { metric: 'Price/Cash Flow', key: 'Price/Cash Flow' },
    { metric: 'Dividend Yield', key: 'Dividend-Yield Factor', isDividendYield: true },
  ];

  return mapping
    .map(({ metric, key, altKey, isDividendYield }) => ({
      metric,
      portfolio: parseNum(portfolio[key]) ?? (altKey ? parseNum(portfolio[altKey]) : null),
      category: parseNum(category[key]) ?? (altKey ? parseNum(category[altKey]) : null),
      isDividendYield,
    }))
    .filter((r) => r.portfolio != null || r.category != null);
}

function extractGrowthRates(etfData: Record<string, unknown> | null): GrowthRow[] {
  const vg = etfData?.Valuations_Growth;
  const portfolio = vg?.Growth_Rates_Portfolio;
  if (!portfolio || typeof portfolio !== 'object') return [];
  return Object.entries(portfolio)
    .map(([label, v]) => ({
      label,
      value: parseNum(v),
    }))
    .filter((g) => g.value != null);
}

// ---------------------------------------------------------------------------
// Risk gauge helpers
// ---------------------------------------------------------------------------

function getVolatilityGauge(vol: number | null): { pct: number; level: string; color: string } {
  if (vol == null) return { pct: 0, level: '\u2014', color: 'neutral' };
  // Heuristic: 0-10% Low, 10-20% Moderate, 20%+ High
  if (vol <= 10) return { pct: Math.max(10, (vol / 30) * 100), level: 'Low', color: 'low' };
  if (vol <= 20) return { pct: Math.max(20, (vol / 30) * 100), level: 'Moderate', color: 'moderate' };
  return { pct: Math.min(100, (vol / 30) * 100), level: 'High', color: 'high' };
}

function getSharpeGauge(sharpe: number | null): { pct: number; level: string; color: string } {
  if (sharpe == null) return { pct: 0, level: '\u2014', color: 'neutral' };
  // Heuristic: <0.5 Poor, 0.5-1.0 Average, >1.0 Good
  const bounded = Math.max(0, Math.min(3, sharpe));
  const pct = (bounded / 3) * 100;
  if (sharpe < 0.5) return { pct: Math.max(10, pct), level: 'Poor', color: 'high' };
  if (sharpe < 1.0) return { pct, level: 'Average', color: 'neutral' };
  return { pct, level: 'Good', color: 'low' };
}

function getBetaGauge(beta: number | null): { pct: number; level: string; color: string } {
  if (beta == null) return { pct: 0, level: '\u2014', color: 'neutral' };
  const bounded = Math.max(0, Math.min(2, beta));
  const pct = (bounded / 2) * 100;
  if (beta < 0.8) return { pct, level: 'Defensive', color: 'low' };
  if (beta <= 1.2) return { pct, level: 'Market', color: 'neutral' };
  return { pct, level: 'Aggressive', color: 'high' };
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function PerformanceSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const etfData = data.etfData;
  const technicals = data.technicals;

  const returns = useMemo(() => extractPerformance(etfData), [etfData]);
  const risk = useMemo(() => extractRiskMetrics(etfData, technicals), [etfData, technicals]);

  const chartData = useMemo(
    () =>
      returns.map((r) => ({
        period: r.period,
        value: Number(r.value.toFixed(2)),
        fill: r.value >= 0 ? '#22c55e' : '#ef4444',
      })),
    [returns]
  );

  const hasReturns = chartData.length > 0;
  const hasRisk = risk.vol1y != null || risk.vol3y != null || risk.sharpe3y != null || risk.beta != null;

  if (!hasReturns && !hasRisk) {
    return (
      <div id="section-performance">
        <h2 className="detail-section-title">Performance</h2>
        <div className="etf-section-empty">No performance data available</div>
      </div>
    );
  }

  const barOptions: AgChartOptions = {
    theme: chartTheme.theme,
    height: 260,
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar',
        xKey: 'period',
        yKey: 'value',
        yName: 'Return',
        fill: '#22c55e',
        tooltip: {
          renderer: (params: { datum: { period: string; value: number } }) => ({
            content: `${params.datum.period}: ${params.datum.value}%`,
          }),
        },
      },
    ],
    axes: [
      { type: 'category', position: 'bottom' },
      {
        type: 'number',
        position: 'left',
        label: { formatter: (params: { value: number }) => `${params.value}%` },
      },
    ],
    legend: { enabled: false },
  };

  const vol1yGauge = getVolatilityGauge(risk.vol1y);
  const vol3yGauge = getVolatilityGauge(risk.vol3y);
  const sharpeGauge = getSharpeGauge(risk.sharpe3y);
  const betaGauge = getBetaGauge(risk.beta);

  return (
    <div id="section-performance">
      <h2 className="detail-section-title">Performance</h2>
      <div className="etf-two-col">
        {/* Returns bar chart */}
        <div className="etf-card">
          <div className="etf-card-title">Returns</div>
          {hasReturns ? (
            <AgCharts options={barOptions} />
          ) : (
            <div className="etf-section-empty">No return data available</div>
          )}
        </div>

        {/* Risk profile */}
        <div className="etf-card">
          <div className="etf-card-title">Risk Profile</div>
          <div className="etf-risk-grid">
            <RiskCard label="1Y Volatility" value={fmtPct(risk.vol1y)} gauge={vol1yGauge} />
            <RiskCard label="3Y Volatility" value={fmtPct(risk.vol3y)} gauge={vol3yGauge} />
            <RiskCard label="3Y Sharpe Ratio" value={fmtNum(risk.sharpe3y)} gauge={sharpeGauge} />
            <RiskCard label="Beta" value={fmtNum(risk.beta)} gauge={betaGauge} />
          </div>
        </div>
      </div>
    </div>
  );
}

function RiskCard({
  label,
  value,
  gauge,
}: {
  label: string;
  value: string;
  gauge: { pct: number; level: string; color: string };
}) {
  return (
    <div className="etf-risk-card">
      <div className="etf-risk-card-label">{label}</div>
      <div className="etf-risk-card-value">{value}</div>
      <div className="etf-risk-gauge">
        <div
          className={`etf-risk-gauge-fill ${gauge.color}`}
          style={{ width: `${gauge.pct}%` }}
        />
      </div>
      <div className="etf-risk-descriptor">{gauge.level}</div>
    </div>
  );
}

function HoldingsSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const etfData = data.etfData;

  const { holdings, holdingsCount } = useMemo(() => extractTopHoldings(etfData), [etfData]);
  const { caps, avgCap } = useMemo(() => extractMarketCap(etfData), [etfData]);

  const hasHoldings = holdings.length > 0;
  const hasCaps = caps.length > 0;

  const topWeight = useMemo(() => {
    if (holdings.length === 0) return 1;
    return Math.max(...holdings.map((h) => h.weight));
  }, [holdings]);

  const otherWeight = useMemo(() => {
    const sum = holdings.reduce((acc, h) => acc + h.weight, 0);
    return Math.max(0, 100 - sum);
  }, [holdings]);

  const donutData = useMemo(() => {
    return caps.map((c, i) => ({
      label: c.size,
      value: c.pct,
      color: CAP_COLORS[c.size] ?? CHART_COLORS[i % CHART_COLORS.length],
    }));
  }, [caps]);

  const donutOptions: AgChartOptions = useMemo(
    () => ({
      theme: chartTheme.theme,
      height: 180,
      width: 180,
      background: { fill: 'transparent' },
      series: [
        {
          type: 'donut',
          calloutLabelKey: 'label',
          calloutLabel: { enabled: false },
          angleKey: 'value',
          innerRadiusRatio: 0.55,
          data: donutData,
          fills: donutData.map((d) => d.color),
          tooltip: {
            renderer: (params: { datum: { label: string; value: number } }) => ({
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ],
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, donutData]
  );

  if (!hasHoldings && !hasCaps) {
    return (
      <div id="section-holdings">
        <h2 className="detail-section-title">Top Holdings</h2>
        <div className="etf-section-empty">No holdings data available</div>
      </div>
    );
  }

  return (
    <div id="section-holdings">
      <h2 className="detail-section-title">Top Holdings</h2>
      <div className="etf-two-col">
        {/* Horizontal bar chart */}
        <div className="etf-card">
          <div className="etf-card-title">
            Top {holdings.length} Holdings
            {holdingsCount != null && (
              <span className="etf-card-title-sub">
                of {holdingsCount.toLocaleString()} total
              </span>
            )}
          </div>
          <div className="etf-holding-bars">
            {holdings.map((h) => {
              const widthPct = topWeight > 0 ? (h.weight / topWeight) * 100 : 0;
              return (
                <div key={h.ticker} className="etf-holding-bar-row">
                  <div className="etf-holding-bar-ticker">{h.ticker}</div>
                  <div className="etf-holding-bar-track">
                    <div
                      className="etf-holding-bar-fill"
                      style={{ width: `${Math.max(8, widthPct)}%` }}
                    >
                      {h.weight.toFixed(2)}%
                    </div>
                  </div>
                </div>
              );
            })}
            {otherWeight > 0 && (
              <div className="etf-holding-other-row">
                <div className="etf-holding-other-label">Other</div>
                <div className="etf-holding-other-bar" />
                <div className="etf-holding-other-value">{otherWeight.toFixed(1)}%</div>
              </div>
            )}
          </div>
        </div>

        {/* Market Cap donut */}
        <div className="etf-card">
          <div className="etf-card-title">Market Cap Breakdown</div>
          {hasCaps ? (
            <div className="etf-donut-layout">
              <div className="etf-donut-chart">
                <AgCharts options={donutOptions} />
              </div>
              <div className="etf-donut-legend">
                {donutData.map((d) => (
                  <div key={d.label} className="etf-legend-item">
                    <div className="etf-legend-dot" style={{ backgroundColor: d.color }} />
                    <span className="etf-legend-label">{d.label}</span>
                    <span className="etf-legend-value">{d.value}%</span>
                  </div>
                ))}
                {avgCap != null && (
                  <div
                    className="etf-legend-item"
                    style={{ marginTop: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border)' }}
                  >
                    <span className="etf-legend-label" style={{ fontWeight: 600 }}>
                      Avg Market Cap
                    </span>
                    <span className="etf-legend-value" style={{ color: 'var(--accent-secondary)' }}>
                      {fmtLargeNumber(avgCap)}
                    </span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="etf-section-empty">No market cap data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

function SectorGeographicSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const etfData = data.etfData;

  const sectors = useMemo(() => extractSectorWeights(etfData), [etfData]);
  const regions = useMemo(() => extractRegions(etfData), [etfData]);

  const hasSectors = sectors.length > 0;
  const hasRegions = regions.length > 0;

  const sectorDonutData = useMemo(
    () =>
      sectors.map((s, i) => ({
        label: s.name,
        value: s.weight,
        color: CHART_COLORS[i % CHART_COLORS.length],
      })),
    [sectors]
  );

  const sectorDonutOptions: AgChartOptions = useMemo(
    () => ({
      theme: chartTheme.theme,
      height: 180,
      width: 180,
      background: { fill: 'transparent' },
      series: [
        {
          type: 'donut',
          calloutLabelKey: 'label',
          calloutLabel: { enabled: false },
          angleKey: 'value',
          innerRadiusRatio: 0.5,
          data: sectorDonutData,
          fills: sectorDonutData.map((d) => d.color),
          tooltip: {
            renderer: (params: { datum: { label: string; value: number } }) => ({
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ],
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, sectorDonutData]
  );

  // Geographic: donut
  const regionDonutData = useMemo(
    () =>
      regions.map((r, i) => ({
        label: r.name,
        value: r.weight,
        color: GEO_COLORS[i % GEO_COLORS.length],
      })),
    [regions]
  );

  const regionDonutOptions: AgChartOptions = useMemo(
    () => ({
      theme: chartTheme.theme,
      height: 180,
      width: 180,
      background: { fill: 'transparent' },
      series: [
        {
          type: 'donut',
          calloutLabelKey: 'label',
          calloutLabel: { enabled: false },
          angleKey: 'value',
          innerRadiusRatio: 0.5,
          data: regionDonutData,
          fills: regionDonutData.map((d) => d.color),
          tooltip: {
            renderer: (params: { datum: { label: string; value: number } }) => ({
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ],
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, regionDonutData]
  );

  if (!hasSectors && !hasRegions) {
    return (
      <>
        <div id="section-sectors">
          <h2 className="detail-section-title">Sector & Geographic Allocation</h2>
          <div className="etf-section-empty">No sector or geographic data available</div>
        </div>
        <div id="section-regions" />
      </>
    );
  }

  return (
    <>
      <div id="section-sectors">
        <h2 className="detail-section-title">Sector & Geographic Allocation</h2>
        <div className="etf-two-col">
          {/* Sector donut */}
          <div className="etf-card">
            <div className="etf-card-title">Sector Weights</div>
            {hasSectors ? (
              <div className="etf-sector-donut-layout">
                <div className="etf-donut-chart">
                  <AgCharts options={sectorDonutOptions} />
                </div>
                <div className="etf-sector-legend">
                  {sectorDonutData.map((s) => (
                    <div key={s.label} className="etf-legend-item">
                      <div className="etf-legend-dot" style={{ backgroundColor: s.color }} />
                      <span className="etf-legend-label">{s.label}</span>
                      <span className="etf-legend-value">{s.value}%</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="etf-section-empty">No sector data available</div>
            )}
          </div>

          {/* Geographic donut */}
          <div className="etf-card">
            <div className="etf-card-title">Geographic Exposure</div>
            {hasRegions ? (
              <div className="etf-sector-donut-layout">
                <div className="etf-donut-chart">
                  <AgCharts options={regionDonutOptions} />
                </div>
                <div className="etf-sector-legend">
                  {regionDonutData.map((r) => (
                    <div key={r.label} className="etf-legend-item">
                      <div className="etf-legend-dot" style={{ backgroundColor: r.color }} />
                      <span className="etf-legend-label">{r.label}</span>
                      <span className="etf-legend-value">{r.value}%</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="etf-section-empty">No geographic data available</div>
            )}
          </div>
        </div>
      </div>
      {/* Anchor for region nav */}
      <div id="section-regions" />
    </>
  );
}

function ValuationSection({ data }: Props) {
  const etfData = data.etfData;

  const valuationRows = useMemo(() => extractValuationRows(etfData), [etfData]);
  const growthRates = useMemo(() => extractGrowthRates(etfData), [etfData]);
  const assets = useMemo(() => extractAssetAllocation(etfData), [etfData]);

  const hasValuation = valuationRows.length > 0;
  const hasAssets = assets.length > 0;
  const hasGrowth = growthRates.length > 0;

  // Compute max value for butterfly bar scaling
  const maxVal = useMemo(() => {
    let m = 0;
    for (const r of valuationRows) {
      if (r.portfolio != null) m = Math.max(m, Math.abs(r.portfolio));
      if (r.category != null) m = Math.max(m, Math.abs(r.category));
    }
    return m || 1;
  }, [valuationRows]);

  if (!hasValuation && !hasAssets && !hasGrowth) {
    return (
      <div id="section-valuation">
        <h2 className="detail-section-title">Valuation & Asset Allocation</h2>
        <div className="etf-section-empty">No valuation data available</div>
      </div>
    );
  }

  return (
    <div id="section-valuation">
      <h2 className="detail-section-title">Valuation & Asset Allocation</h2>
      <div className="etf-two-col">
        {/* Butterfly table */}
        <div className="etf-card">
          <div className="etf-card-title">Valuation &mdash; Portfolio vs Category</div>
          {hasValuation ? (
            <>
              <table className="etf-valuation-table">
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left' }}>Metric</th>
                    <th className="portfolio-header">Portfolio</th>
                    <th className="bars-header" />
                    <th className="category-header">Category</th>
                  </tr>
                </thead>
                <tbody>
                  {valuationRows.map((row) => {
                    const pVal = row.portfolio;
                    const cVal = row.category;
                    const pBar = pVal != null ? (Math.abs(pVal) / maxVal) * 100 : 0;
                    const cBar = cVal != null ? (Math.abs(cVal) / maxVal) * 100 : 0;
                    const isDy = row.isDividendYield;

                    return (
                      <tr key={row.metric}>
                        <td className="etf-valuation-metric">{row.metric}</td>
                        <td
                          className="etf-valuation-portfolio-val"
                          style={isDy ? { color: 'var(--success-text, #22c55e)' } : undefined}
                        >
                          {pVal != null ? (isDy ? `${pVal}%` : fmtNum(pVal)) : '\u2014'}
                        </td>
                        <td style={{ padding: '0.5rem 0.25rem' }}>
                          <div className="etf-butterfly-bar">
                            <div className="etf-butterfly-left">
                              <div
                                className="etf-butterfly-left-bar"
                                style={{
                                  width: `${pBar}%`,
                                  background: isDy ? 'var(--success-text, #059669)' : undefined,
                                }}
                              />
                            </div>
                            <div className="etf-butterfly-divider" />
                            <div className="etf-butterfly-right">
                              <div className="etf-butterfly-right-bar" style={{ width: `${cBar}%` }} />
                            </div>
                          </div>
                        </td>
                        <td className="etf-valuation-category-val">
                          {cVal != null ? (isDy ? `${cVal}%` : fmtNum(cVal)) : '\u2014'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>
              <div className="etf-valuation-legend">
                <div className="etf-valuation-legend-item">
                  <div
                    className="etf-valuation-legend-dot"
                    style={{ background: 'var(--accent-secondary, #6366f1)' }}
                  />
                  Portfolio
                </div>
                <div className="etf-valuation-legend-item">
                  <div
                    className="etf-valuation-legend-dot"
                    style={{ background: 'var(--bg-tertiary, #374151)' }}
                  />
                  Category Avg
                </div>
              </div>
            </>
          ) : (
            <div className="etf-section-empty">No valuation data available</div>
          )}
        </div>

        {/* Asset Allocation + Growth */}
        <div className="etf-card">
          <div className="etf-card-title">Asset Allocation</div>
          {hasAssets && (
            <>
              <div className="etf-asset-stacked-bar">
                {assets.map((a, i) => (
                  <div
                    key={a.type}
                    className="etf-asset-segment"
                    style={{
                      width: `${a.pct}%`,
                      background: ASSET_COLORS[i % ASSET_COLORS.length],
                      minWidth: a.pct >= 5 ? undefined : '0',
                    }}
                    title={`${a.type}: ${a.pct}%`}
                  >
                    {a.pct >= 8 ? `${a.type} ${a.pct}%` : ''}
                  </div>
                ))}
              </div>
              <div className="etf-asset-detail-grid">
                <div className="etf-asset-detail-card">
                  {assets.map((a, i) => (
                    <div key={a.type} className="etf-legend-item">
                      <div
                        className="etf-legend-dot"
                        style={{ backgroundColor: ASSET_COLORS[i % ASSET_COLORS.length] }}
                      />
                      <span className="etf-legend-label">{a.type}</span>
                      <span className="etf-legend-value">{a.pct}%</span>
                    </div>
                  ))}
                </div>
                {hasGrowth && (
                  <div className="etf-asset-detail-card">
                    <div className="etf-growth-header">Growth Rates (Portfolio)</div>
                    {growthRates.map((g) => (
                      <div key={g.label} className="etf-growth-row">
                        <span className="etf-growth-label">{g.label}</span>
                        <span
                          className={`etf-growth-value ${
                            g.value != null && g.value >= 0 ? 'positive' : 'negative'
                          }`}
                        >
                          {g.value != null ? `${g.value}%` : '\u2014'}
                        </span>
                      </div>
                    ))}
                  </div>
                )}
              </div>
            </>
          )}
          {!hasAssets && (
            <div className="etf-section-empty">No asset allocation data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export function EtfDetailSections({ data }: Props) {
  return (
    <div className="etf-sections">
      {/* Overview section anchor -- hero metrics + section nav handle this */}
      <div id="section-overview" />

      <PerformanceSection data={data} />
      <HoldingsSection data={data} />
      <SectorGeographicSection data={data} />
      <ValuationSection data={data} />
    </div>
  );
}
