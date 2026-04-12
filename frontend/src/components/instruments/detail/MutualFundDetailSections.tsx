import { useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgChartOptions } from 'ag-charts-community';
import { useChartTheme } from '@/hooks/useChartTheme';
import type { InstrumentDetail } from '@/types/screener';
import './MutualFundDetailSections.css';

// ---------------------------------------------------------------------------
// Types
// ---------------------------------------------------------------------------

interface Props {
  data: InstrumentDetail;
}

interface HoldingEntry {
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

interface CountryEntry {
  country: string;
  weight: number;
}

interface AssetEntry {
  type: string;
  pct: number;
}

interface ValuationRow {
  metric: string;
  portfolio: number | null;
  benchmark: number | null;
  categoryAvg: number | null;
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

const REGION_COLORS = [
  '#6366f1', '#fbbf24', '#34d399', '#f472b6', '#38bdf8',
  '#ef4444', '#a78bfa', '#fb923c', '#14b8a6', '#e879f9', '#374151',
];

const COUNTRY_COLORS = [
  '#818cf8', '#a78bfa', '#14b8a6', '#38bdf8', '#fb923c',
  '#e879f9', '#ef4444', '#94a3b8', '#059669', '#d97706', '#374151',
];

const CAP_COLORS: Record<string, string> = {
  Giant: '#6366f1',
  Large: '#818cf8',
  Medium: '#34d399',
  Small: '#fbbf24',
  Micro: '#f472b6',
};

const ASSET_COLORS = ['#6366f1', '#818cf8', '#34d399', '#fbbf24', '#374151', '#38bdf8', '#ef4444'];

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function parseNum(v: unknown): number | null {
  if (v == null || v === '' || v === 'None' || v === 'N/A') return null;
  const n = Number(v);
  return isNaN(n) ? null : n;
}

/** Parse a percentage string like "12.28%" to a number (12.28). */
function parsePctString(v: unknown): number | null {
  if (v == null || v === '' || v === 'None' || v === 'N/A') return null;
  const s = String(v).replace('%', '').trim();
  const n = Number(s);
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

/** Extract an object with numbered keys ("0", "1", ...) to an array. */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function numberedObjectToArray(obj: Record<string, any> | null | undefined): any[] {
  if (!obj || typeof obj !== 'object') return [];
  const entries: [number, unknown][] = [];
  for (const key of Object.keys(obj)) {
    const idx = parseInt(key, 10);
    if (!isNaN(idx)) {
      entries.push([idx, obj[key]]);
    }
  }
  entries.sort((a, b) => a[0] - b[0]);
  return entries.map((e) => e[1]);
}

// ---------------------------------------------------------------------------
// Data extraction
// ---------------------------------------------------------------------------

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractReturns(mfData: Record<string, any> | null): { period: string; value: number }[] {
  if (!mfData) return [];
  const mapping: [string, string][] = [
    ['1Y', 'Yield_1Year_YTD'],
    ['3Y', 'Yield_3Year_YTD'],
    ['5Y', 'Yield_5Year_YTD'],
  ];
  const result: { period: string; value: number }[] = [];
  for (const [label, key] of mapping) {
    const v = parseNum(mfData[key]);
    if (v != null) {
      result.push({ period: label, value: v });
    }
  }
  return result;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractFundProfile(mfData: Record<string, any> | null) {
  if (!mfData) return [];
  const items: { label: string; value: string | null }[] = [
    { label: 'Fund Category', value: mfData.Fund_Category ?? mfData.Morning_Star_Category ?? null },
    { label: 'Fund Style', value: mfData.Fund_Style ?? null },
    { label: 'Domicile', value: mfData.Domicile ?? null },
    { label: 'Expense Ratio Date', value: mfData.Expense_Ratio_Date ?? null },
  ];
  return items.filter((item) => item.value != null && item.value !== '' && item.value !== 'None' && item.value !== 'N/A');
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractTopHoldings(mfData: Record<string, any> | null): HoldingEntry[] {
  const raw = numberedObjectToArray(mfData?.Top_Holdings);
  return raw
    .map((h) => ({
      name: (h?.Name ?? '') as string,
      weight: parsePctString(h?.Weight) ?? parseNum(h?.['Assets_%']) ?? 0,
    }))
    .filter((h) => h.name && h.weight > 0);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractMarketCap(mfData: Record<string, any> | null): { caps: CapEntry[]; avgCap: number | null } {
  const raw = numberedObjectToArray(mfData?.Market_Capitalization ?? mfData?.Market_Capitalisation);
  const caps: CapEntry[] = raw
    .map((c) => ({
      size: (c?.Size ?? '') as string,
      pct: parseNum(c?.['Portfolio_%']) ?? 0,
    }))
    .filter((c) => c.size && c.pct > 0);
  const avgCap = parseNum(mfData?.Average_Mkt_Cap_Mil);
  return { caps, avgCap };
}

/**
 * Flatten Sector_Weights from sub-groups (Cyclical, Defensive, Sensitive, Bond Sector)
 * Each sub-group has numbered items with Name and Amount_%
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractSectorWeights(mfData: Record<string, any> | null): SectorEntry[] {
  const sw = mfData?.Sector_Weights;
  if (!sw || typeof sw !== 'object') return [];

  const result: SectorEntry[] = [];
  const subGroups = ['Cyclical', 'Defensive', 'Sensitive', 'Bond Sector'];

  for (const groupKey of subGroups) {
    const group = sw[groupKey];
    if (!group || typeof group !== 'object') continue;

    const items = numberedObjectToArray(group);
    for (const item of items) {
      if (!item || typeof item !== 'object') continue;
      const name = item.Name as string;
      const weight = parsePctString(item['Amount_%']) ?? parseNum(item['Amount_%']) ?? 0;
      if (name && weight > 0) {
        result.push({ name, weight });
      }
    }
  }

  result.sort((a, b) => b.weight - a.weight);
  return result;
}

/**
 * Flatten World_Regions from sub-groups (Americas, Greater Asia, Greater Europe)
 * Each sub-group has numbered items with Name and Stocks_%
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractRegions(mfData: Record<string, any> | null): RegionEntry[] {
  const wr = mfData?.World_Regions;
  if (!wr || typeof wr !== 'object') return [];

  const result: RegionEntry[] = [];
  for (const groupKey of Object.keys(wr)) {
    const group = wr[groupKey];
    if (!group || typeof group !== 'object') continue;

    const items = numberedObjectToArray(group);
    for (const item of items) {
      if (!item || typeof item !== 'object') continue;
      const name = item.Name as string;
      const weight = parsePctString(item['Stocks_%']) ?? parseNum(item['Stocks_%']) ?? 0;
      if (name && weight > 0) {
        result.push({ name, weight });
      }
    }
  }

  result.sort((a, b) => b.weight - a.weight);
  return result;
}

/**
 * Top_Countries: numbered keys, each has Country + Amount_%
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractTopCountries(mfData: Record<string, any> | null): CountryEntry[] {
  const raw = numberedObjectToArray(mfData?.Top_Countries);
  return raw
    .map((c) => ({
      country: (c?.Country ?? '') as string,
      weight: parsePctString(c?.['Amount_%']) ?? parseNum(c?.['Amount_%']) ?? 0,
    }))
    .filter((c) => c.country && c.weight > 0)
    .sort((a, b) => b.weight - a.weight);
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractAssetAllocation(mfData: Record<string, any> | null): AssetEntry[] {
  const raw = numberedObjectToArray(mfData?.Asset_Allocation);
  return raw
    .map((a) => ({
      type: (a?.Type ?? '') as string,
      pct: parsePctString(a?.['Net_%']) ?? parseNum(a?.['Net_%']) ?? 0,
    }))
    .filter((a) => a.type && a.pct > 0)
    .sort((a, b) => b.pct - a.pct);
}

/**
 * Value_Growth: numbered keys, each has Name, Stock_Portfolio, Benchmark, Category_Average
 * Separate valuation metrics from growth metrics by heuristic.
 */
// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractValuationRows(mfData: Record<string, any> | null): ValuationRow[] {
  const raw = numberedObjectToArray(mfData?.Value_Growth);
  if (raw.length === 0) return [];

  // Only include ratio/yield metrics in the butterfly table, not growth metrics
  const butterflyMetrics = [
    'Price/Prospective Earnings',
    'Price/Book',
    'Price/Sales',
    'Price/Cash Flow',
    'Dividend-Yield Factor',
    'Long-Term Projected Earnings Growth',
  ];

  const metricShortNames: Record<string, string> = {
    'Price/Prospective Earnings': 'P/E',
    'Price/Book': 'P/B',
    'Price/Sales': 'P/S',
    'Price/Cash Flow': 'P/CF',
    'Dividend-Yield Factor': 'Div Yield %',
    'Long-Term Projected Earnings Growth': 'LT Earnings %',
  };

  const result: ValuationRow[] = [];

  for (const item of raw) {
    if (!item || typeof item !== 'object') continue;
    const name = (item.Name ?? '') as string;
    if (!name || !butterflyMetrics.includes(name)) continue;

    result.push({
      metric: metricShortNames[name] ?? name,
      portfolio: parseNum(item.Stock_Portfolio),
      benchmark: parseNum(item.Benchmark),
      categoryAvg: parseNum(item.Category_Average),
      isDividendYield: name === 'Dividend-Yield Factor',
    });
  }

  // Maintain the order from butterflyMetrics
  const orderedResult: ValuationRow[] = [];
  for (const metricName of butterflyMetrics) {
    const found = result.find((r) => r.metric === (metricShortNames[metricName] ?? metricName));
    if (found) orderedResult.push(found);
  }

  return orderedResult;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function extractGrowthRates(mfData: Record<string, any> | null): GrowthRow[] {
  const raw = numberedObjectToArray(mfData?.Value_Growth);
  if (raw.length === 0) return [];

  const growthMetrics = [
    'Historical Earnings Growth',
    'Long-Term Projected Earnings Growth',
    'Sales Growth',
    'Cash-Flow Growth',
    'Book-Value Growth',
  ];

  const shortNames: Record<string, string> = {
    'Historical Earnings Growth': 'Hist. Earnings',
    'Long-Term Projected Earnings Growth': 'LT Proj. Earnings',
    'Sales Growth': 'Sales Growth',
    'Cash-Flow Growth': 'Cash Flow Growth',
    'Book-Value Growth': 'Book Value Growth',
  };

  const result: GrowthRow[] = [];

  for (const metricName of growthMetrics) {
    const item = raw.find((r) => r && r.Name === metricName);
    if (item) {
      const value = parseNum(item.Stock_Portfolio);
      if (value != null) {
        result.push({ label: shortNames[metricName] ?? metricName, value });
      }
    }
  }

  return result;
}

// ---------------------------------------------------------------------------
// Sub-components
// ---------------------------------------------------------------------------

function PerformanceSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const mfData = data.mutualFundData;

  const returns = useMemo(() => extractReturns(mfData), [mfData]);
  const profileItems = useMemo(() => extractFundProfile(mfData), [mfData]);

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
  const hasProfile = profileItems.length > 0;

  if (!hasReturns && !hasProfile) {
    return (
      <div id="section-performance">
        <h2 className="detail-section-title">Performance</h2>
        <div className="mf-section-empty">No performance data available</div>
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
          renderer: (params: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
            content: `${params.datum.period}: ${params.datum.value}%`,
          }),
        },
      },
    ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
    axes: [
      { type: 'category', position: 'bottom' },
      {
        type: 'number',
        position: 'left',
        label: { formatter: (params: any) => `${params.value}%` },  // eslint-disable-line @typescript-eslint/no-explicit-any
      },
    ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
    legend: { enabled: false },
  };

  return (
    <div id="section-performance">
      <h2 className="detail-section-title">Performance</h2>
      <div className="mf-two-col">
        {/* Returns bar chart */}
        <div className="mf-card">
          <div className="mf-card-title">Returns</div>
          {hasReturns ? (
            <AgCharts options={barOptions} />
          ) : (
            <div className="mf-section-empty">No return data available</div>
          )}
        </div>

        {/* Fund Profile cards */}
        <div className="mf-card">
          <div className="mf-card-title">Fund Profile</div>
          {hasProfile ? (
            <div className="mf-profile-grid">
              {profileItems.map((item) => (
                <div key={item.label} className="mf-profile-card">
                  <div className="mf-profile-card-label">{item.label}</div>
                  <div className="mf-profile-card-value">{item.value}</div>
                </div>
              ))}
            </div>
          ) : (
            <div className="mf-section-empty">No profile data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

function HoldingsSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const mfData = data.mutualFundData;

  const holdings = useMemo(() => extractTopHoldings(mfData), [mfData]);
  const { caps, avgCap } = useMemo(() => extractMarketCap(mfData), [mfData]);

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
            renderer: (params: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, donutData]
  );

  if (!hasHoldings && !hasCaps) {
    return (
      <div id="section-holdings">
        <h2 className="detail-section-title">Top Holdings</h2>
        <div className="mf-section-empty">No holdings data available</div>
      </div>
    );
  }

  return (
    <div id="section-holdings">
      <h2 className="detail-section-title">Top Holdings</h2>
      <div className="mf-two-col">
        {/* Horizontal bar chart */}
        <div className="mf-card">
          <div className="mf-card-title">
            Top {holdings.length} Holdings
          </div>
          {hasHoldings ? (
            <div className="mf-holding-bars">
              {holdings.map((h, i) => {
                const widthPct = topWeight > 0 ? (h.weight / topWeight) * 100 : 0;
                return (
                  <div key={`${h.name}-${i}`} className="mf-holding-bar-row">
                    <div className="mf-holding-bar-name" title={h.name}>{h.name}</div>
                    <div className="mf-holding-bar-track">
                      <div
                        className="mf-holding-bar-fill"
                        style={{ width: `${Math.max(8, widthPct)}%` }}
                      >
                        {h.weight.toFixed(2)}%
                      </div>
                    </div>
                  </div>
                );
              })}
              {otherWeight > 0 && (
                <div className="mf-holding-other-row">
                  <div className="mf-holding-other-label">Other</div>
                  <div className="mf-holding-other-bar" />
                  <div className="mf-holding-other-value">{otherWeight.toFixed(1)}%</div>
                </div>
              )}
            </div>
          ) : (
            <div className="mf-section-empty">No holdings data available</div>
          )}
        </div>

        {/* Market Cap donut */}
        <div className="mf-card">
          <div className="mf-card-title">Market Cap Breakdown</div>
          {hasCaps ? (
            <div className="mf-donut-layout">
              <div className="mf-donut-chart">
                <AgCharts options={donutOptions} />
              </div>
              <div className="mf-donut-legend">
                {donutData.map((d) => (
                  <div key={d.label} className="mf-legend-item">
                    <div className="mf-legend-dot" style={{ backgroundColor: d.color }} />
                    <span className="mf-legend-label">{d.label}</span>
                    <span className="mf-legend-value">{d.value}%</span>
                  </div>
                ))}
                {avgCap != null && (
                  <div
                    className="mf-legend-item"
                    style={{ marginTop: '0.5rem', paddingTop: '0.5rem', borderTop: '1px solid var(--border)' }}
                  >
                    <span className="mf-legend-label" style={{ fontWeight: 600 }}>
                      Avg Market Cap
                    </span>
                    <span className="mf-legend-value" style={{ color: 'var(--accent-secondary)' }}>
                      {avgCap >= 1e6 ? `$${(avgCap / 1e6).toFixed(0)}T` :
                       avgCap >= 1e3 ? `$${(avgCap / 1e3).toFixed(0)}B` :
                       `$${avgCap.toFixed(0)}M`}
                    </span>
                  </div>
                )}
              </div>
            </div>
          ) : (
            <div className="mf-section-empty">No market cap data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

function SectorGeographicSection({ data }: Props) {
  const chartTheme = useChartTheme();
  const mfData = data.mutualFundData;

  const sectors = useMemo(() => extractSectorWeights(mfData), [mfData]);
  const regions = useMemo(() => extractRegions(mfData), [mfData]);
  const countries = useMemo(() => extractTopCountries(mfData), [mfData]);

  const hasSectors = sectors.length > 0;
  const hasRegions = regions.length > 0;
  const hasCountries = countries.length > 0;

  // Sector donut
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
            renderer: (params: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, sectorDonutData]
  );

  // Concentric chart: inner pie (regions, solid) + outer donut (countries)
  const regionPieData = useMemo(
    () =>
      regions.map((r, i) => ({
        label: r.name,
        value: r.weight,
        color: REGION_COLORS[i % REGION_COLORS.length],
      })),
    [regions]
  );

  // Show top 5 countries, group rest as "Other"
  const countryDonutData = useMemo(() => {
    const MAX_SHOWN = 5;
    if (countries.length <= MAX_SHOWN) {
      return countries.map((c, i) => ({
        label: c.country,
        value: c.weight,
        color: COUNTRY_COLORS[i % COUNTRY_COLORS.length],
      }));
    }
    const top = countries.slice(0, MAX_SHOWN);
    const rest = countries.slice(MAX_SHOWN);
    const otherVal = rest.reduce((sum, c) => sum + c.weight, 0);
    const result = top.map((c, i) => ({
      label: c.country,
      value: c.weight,
      color: COUNTRY_COLORS[i % COUNTRY_COLORS.length],
    }));
    if (otherVal > 0) {
      result.push({
        label: `+ ${rest.length} more`,
        value: Number(otherVal.toFixed(1)),
        color: '#374151',
      });
    }
    return result;
  }, [countries]);

  // Inner pie: regions (solid, no hole)
  const innerPieOptions: AgChartOptions = useMemo(
    () => ({
      theme: chartTheme.theme,
      height: 136,
      width: 136,
      background: { fill: 'transparent' },
      series: [
        {
          type: 'pie',
          calloutLabelKey: 'label',
          calloutLabel: { enabled: false },
          sectorLabel: { enabled: false },
          angleKey: 'value',
          data: regionPieData,
          fills: regionPieData.map((d) => d.color),
          strokeWidth: 1,
          tooltip: {
            renderer: (params: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, regionPieData]
  );

  // Outer donut: countries
  const outerDonutOptions: AgChartOptions = useMemo(
    () => ({
      theme: chartTheme.theme,
      height: 200,
      width: 200,
      background: { fill: 'transparent' },
      series: [
        {
          type: 'donut',
          calloutLabelKey: 'label',
          calloutLabel: { enabled: false },
          angleKey: 'value',
          innerRadiusRatio: 0.72,
          data: countryDonutData,
          fills: countryDonutData.map((d) => d.color),
          strokeWidth: 1,
          tooltip: {
            renderer: (params: any) => ({  // eslint-disable-line @typescript-eslint/no-explicit-any
              content: `${params.datum.label}: ${params.datum.value}%`,
            }),
          },
        },
      ] as any,  // eslint-disable-line @typescript-eslint/no-explicit-any
      legend: { enabled: false },
      padding: { top: 0, right: 0, bottom: 0, left: 0 },
    }),
    [chartTheme.theme, countryDonutData]
  );

  if (!hasSectors && !hasRegions && !hasCountries) {
    return (
      <>
        <div id="section-sectors">
          <h2 className="detail-section-title">Sector & Geographic Allocation</h2>
          <div className="mf-section-empty">No sector or geographic data available</div>
        </div>
        <div id="section-regions" />
      </>
    );
  }

  return (
    <>
      <div id="section-sectors">
        <h2 className="detail-section-title">Sector & Geographic Allocation</h2>
        <div className="mf-two-col">
          {/* Sector donut */}
          <div className="mf-card">
            <div className="mf-card-title">Sector Weights</div>
            {hasSectors ? (
              <div className="mf-sector-donut-layout">
                <div className="mf-donut-chart">
                  <AgCharts options={sectorDonutOptions} />
                </div>
                <div className="mf-sector-legend">
                  {sectorDonutData.map((s) => (
                    <div key={s.label} className="mf-legend-item">
                      <div className="mf-legend-dot" style={{ backgroundColor: s.color }} />
                      <span className="mf-legend-label">{s.label}</span>
                      <span className="mf-legend-value">{s.value}%</span>
                    </div>
                  ))}
                </div>
              </div>
            ) : (
              <div className="mf-section-empty">No sector data available</div>
            )}
          </div>

          {/* Concentric Geographic chart */}
          <div className="mf-card">
            <div className="mf-card-title">Geographic Exposure</div>
            {hasRegions || hasCountries ? (
              <div className="mf-concentric-layout">
                <div className="mf-concentric-container">
                  {/* Outer donut: countries */}
                  {hasCountries && (
                    <div className="mf-concentric-outer">
                      <AgCharts options={outerDonutOptions} />
                    </div>
                  )}
                  {/* Gap between outer and inner */}
                  {hasCountries && hasRegions && <div className="mf-concentric-gap" />}
                  {/* Inner pie: regions (solid, no hole) */}
                  {hasRegions && (
                    <div className="mf-concentric-inner">
                      <AgCharts options={innerPieOptions} />
                    </div>
                  )}
                </div>
                <div className="mf-concentric-legend">
                  {/* Regions legend */}
                  {hasRegions && (
                    <div>
                      <div className="mf-legend-section-header">Regions (inner)</div>
                      {regionPieData.map((r) => (
                        <div key={r.label} className="mf-legend-item">
                          <div className="mf-legend-dot" style={{ backgroundColor: r.color }} />
                          <span className="mf-legend-label">{r.label}</span>
                          <span className="mf-legend-value">{r.value}%</span>
                        </div>
                      ))}
                    </div>
                  )}
                  {/* Countries legend */}
                  {hasCountries && (
                    <div className={hasRegions ? 'mf-legend-separator' : ''}>
                      <div className="mf-legend-section-header">Top Countries (outer)</div>
                      {countryDonutData.map((c) => (
                        <div key={c.label} className="mf-legend-item">
                          <div className="mf-legend-dot" style={{ backgroundColor: c.color }} />
                          <span className="mf-legend-label">{c.label}</span>
                          <span className="mf-legend-value">{c.value}%</span>
                        </div>
                      ))}
                    </div>
                  )}
                </div>
              </div>
            ) : (
              <div className="mf-section-empty">No geographic data available</div>
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
  const mfData = data.mutualFundData;

  const valuationRows = useMemo(() => extractValuationRows(mfData), [mfData]);
  const growthRates = useMemo(() => extractGrowthRates(mfData), [mfData]);
  const assets = useMemo(() => extractAssetAllocation(mfData), [mfData]);

  const hasValuation = valuationRows.length > 0;
  const hasAssets = assets.length > 0;
  const hasGrowth = growthRates.length > 0;

  // Compute max value for butterfly bar scaling (portfolio vs benchmark)
  const maxVal = useMemo(() => {
    let m = 0;
    for (const r of valuationRows) {
      if (r.portfolio != null) m = Math.max(m, Math.abs(r.portfolio));
      if (r.benchmark != null) m = Math.max(m, Math.abs(r.benchmark));
    }
    return m || 1;
  }, [valuationRows]);

  if (!hasValuation && !hasAssets && !hasGrowth) {
    return (
      <div id="section-valuation">
        <h2 className="detail-section-title">Valuation & Asset Allocation</h2>
        <div className="mf-section-empty">No valuation data available</div>
      </div>
    );
  }

  return (
    <div id="section-valuation">
      <h2 className="detail-section-title">Valuation & Asset Allocation</h2>
      <div className="mf-two-col">
        {/* Butterfly table: Portfolio vs Benchmark + Category Average */}
        <div className="mf-card">
          <div className="mf-card-title">Valuation &mdash; Portfolio vs Benchmark</div>
          {hasValuation ? (
            <>
              <table className="mf-valuation-table">
                <thead>
                  <tr>
                    <th style={{ textAlign: 'left' }}>Metric</th>
                    <th className="portfolio-header">Portfolio</th>
                    <th className="bars-header" />
                    <th className="benchmark-header">Benchmark</th>
                  </tr>
                </thead>
                <tbody>
                  {valuationRows.map((row) => {
                    const pVal = row.portfolio;
                    const bVal = row.benchmark;
                    const pBar = pVal != null ? (Math.abs(pVal) / maxVal) * 100 : 0;
                    const bBar = bVal != null ? (Math.abs(bVal) / maxVal) * 100 : 0;
                    const isDy = row.isDividendYield;

                    return (
                      <tr key={row.metric}>
                        <td className="mf-valuation-metric">{row.metric}</td>
                        <td
                          className="mf-valuation-portfolio-val"
                          style={isDy ? { color: 'var(--success-text, #22c55e)' } : undefined}
                        >
                          {pVal != null ? (isDy ? `${pVal}%` : fmtNum(pVal)) : '\u2014'}
                        </td>
                        <td style={{ padding: '0.5rem 0.25rem' }}>
                          <div className="mf-butterfly-bar">
                            <div className="mf-butterfly-left">
                              <div
                                className="mf-butterfly-left-bar"
                                style={{
                                  width: `${pBar}%`,
                                  background: isDy ? 'var(--success-text, #059669)' : undefined,
                                }}
                              />
                            </div>
                            <div className="mf-butterfly-divider" />
                            <div className="mf-butterfly-right">
                              <div className="mf-butterfly-right-bar" style={{ width: `${bBar}%` }} />
                            </div>
                          </div>
                        </td>
                        <td className="mf-valuation-benchmark-val">
                          {bVal != null ? (isDy ? `${bVal}%` : fmtNum(bVal)) : '\u2014'}
                        </td>
                      </tr>
                    );
                  })}
                </tbody>
              </table>

              {/* Category Average compact reference row */}
              {valuationRows.some((r) => r.categoryAvg != null) && (
                <div className="mf-category-avg-box">
                  <div className="mf-category-avg-title">Category Average</div>
                  <div className="mf-category-avg-row">
                    {valuationRows
                      .filter((r) => r.categoryAvg != null)
                      .map((r) => (
                        <span key={r.metric} className="mf-category-avg-item">
                          {r.metric}
                          <span className="mf-category-avg-value">
                            {r.isDividendYield ? fmtPct(r.categoryAvg) : fmtNum(r.categoryAvg)}
                          </span>
                        </span>
                      ))}
                  </div>
                </div>
              )}

              <div className="mf-valuation-legend">
                <div className="mf-valuation-legend-item">
                  <div
                    className="mf-valuation-legend-dot"
                    style={{ background: 'var(--accent-secondary, #6366f1)' }}
                  />
                  Portfolio
                </div>
                <div className="mf-valuation-legend-item">
                  <div
                    className="mf-valuation-legend-dot"
                    style={{ background: 'var(--warning-text, #d97706)' }}
                  />
                  Benchmark
                </div>
                <div className="mf-valuation-legend-item">
                  <div
                    className="mf-valuation-legend-dot"
                    style={{ background: 'var(--text-muted)', borderRadius: 0, width: '0.75rem', height: '0.1875rem' }}
                  />
                  Category (below)
                </div>
              </div>
            </>
          ) : (
            <div className="mf-section-empty">No valuation data available</div>
          )}
        </div>

        {/* Asset Allocation + Growth */}
        <div className="mf-card">
          <div className="mf-card-title">Asset Allocation</div>
          {hasAssets && (
            <>
              <div className="mf-asset-stacked-bar">
                {assets.map((a, i) => (
                  <div
                    key={a.type}
                    className="mf-asset-segment"
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
              <div className="mf-asset-detail-grid">
                <div className="mf-asset-detail-card">
                  {assets.map((a, i) => (
                    <div key={a.type} className="mf-legend-item">
                      <div
                        className="mf-legend-dot"
                        style={{ backgroundColor: ASSET_COLORS[i % ASSET_COLORS.length] }}
                      />
                      <span className="mf-legend-label">{a.type}</span>
                      <span className="mf-legend-value">{a.pct}%</span>
                    </div>
                  ))}
                </div>
                {hasGrowth && (
                  <div className="mf-asset-detail-card">
                    <div className="mf-growth-header">Growth Rates (Portfolio)</div>
                    {growthRates.map((g) => (
                      <div key={g.label} className="mf-growth-row">
                        <span className="mf-growth-label">{g.label}</span>
                        <span
                          className={`mf-growth-value ${
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
            <div className="mf-section-empty">No asset allocation data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export function MutualFundDetailSections({ data }: Props) {
  return (
    <div className="mf-sections">
      {/* Overview section anchor -- hero metrics + section nav handle this */}
      <div id="section-overview" />

      <PerformanceSection data={data} />
      <HoldingsSection data={data} />
      <SectorGeographicSection data={data} />
      <ValuationSection data={data} />
    </div>
  );
}
