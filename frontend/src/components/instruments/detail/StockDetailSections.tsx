import { useState, useMemo } from 'react';
import { AgCharts } from 'ag-charts-react';
import type { AgChartOptions } from 'ag-charts-community';
import { useChartTheme } from '@/hooks/useChartTheme';
import type { InstrumentDetail } from '@/types/screener';
import './StockDetailSections.css';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

function formatLargeNumber(value: number | null | undefined): string {
  if (value == null || isNaN(Number(value))) return '\u2014';
  const v = Number(value);
  const abs = Math.abs(v);
  if (abs >= 1e12) return `$${(v / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `$${(v / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `$${(v / 1e6).toFixed(1)}M`;
  if (abs >= 1e3) return `$${(v / 1e3).toFixed(1)}K`;
  return `$${v.toLocaleString()}`;
}

function formatPercent(value: number | null | undefined): string {
  if (value == null || isNaN(Number(value))) return '\u2014';
  return `${(Number(value) * 100).toFixed(1)}%`;
}

function formatPercentRaw(value: number | null | undefined): string {
  if (value == null || isNaN(Number(value))) return '\u2014';
  return `${Number(value).toFixed(1)}%`;
}

function formatCurrency(value: number | null | undefined): string {
  if (value == null || isNaN(Number(value))) return '\u2014';
  return `$${Number(value).toFixed(2)}`;
}

function formatDecimal(value: number | null | undefined, decimals = 2): string {
  if (value == null || isNaN(Number(value))) return '\u2014';
  return Number(value).toFixed(decimals);
}

function formatDate(value: string | null | undefined): string {
  if (!value) return '\u2014';
  try {
    return new Date(value).toLocaleDateString('en-US', {
      year: 'numeric',
      month: 'short',
      day: 'numeric',
    });
  } catch {
    return value;
  }
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function num(obj: Record<string, any> | null | undefined, key: string): number | null {
  if (!obj) return null;
  const v = obj[key];
  if (v == null || v === '' || v === 'None' || v === 'N/A') return null;
  const n = Number(v);
  return isNaN(n) ? null : n;
}

// eslint-disable-next-line @typescript-eslint/no-explicit-any
function str(obj: Record<string, any> | null | undefined, key: string): string | null {
  if (!obj) return null;
  const v = obj[key];
  if (v == null || v === '' || v === 'None' || v === 'N/A') return null;
  return String(v);
}

/** Compute YoY % change */
function yoyChange(current: number | null, previous: number | null): number | null {
  if (current == null || previous == null || previous === 0) return null;
  return ((current - previous) / Math.abs(previous)) * 100;
}

// ---------------------------------------------------------------------------
// Props
// ---------------------------------------------------------------------------

interface StockDetailSectionsProps {
  data: InstrumentDetail;
}

// ---------------------------------------------------------------------------
// SECTION 1: About
// ---------------------------------------------------------------------------

function AboutSection({ data }: { data: InstrumentDetail }) {
  const [expanded, setExpanded] = useState(false);
  const general = data.general;
  if (!general) return null;

  const description = str(general, 'Description');

  // Extract first officer as CEO
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const officers = (general as any).Officers;
  let ceo: string | null = null;
  if (officers && typeof officers === 'object') {
    const officerKeys = Object.keys(officers);
    for (const key of officerKeys) {
      const officer = officers[key];
      if (officer && typeof officer === 'object') {
        const title = officer.Title || '';
        if (/CEO|Chief Executive/i.test(title)) {
          ceo = officer.Name || null;
          break;
        }
      }
    }
    // Fallback to first officer if no CEO found
    if (!ceo && officerKeys.length > 0) {
      const first = officers[officerKeys[0]];
      if (first && first.Name) {
        ceo = `${first.Name}${first.Title ? ` (${first.Title})` : ''}`;
      }
    }
  }

  const employees = str(general, 'FullTimeEmployees');
  const ipoDate = str(general, 'IPODate');
  const fiscalYearEnd = str(general, 'FiscalYearEnd');

  const infoItems = [
    { label: 'CEO', value: ceo },
    { label: 'Employees', value: employees ? Number(employees).toLocaleString() : null },
    { label: 'IPO Date', value: ipoDate ? formatDate(ipoDate) : null },
    { label: 'Fiscal Year End', value: fiscalYearEnd },
  ].filter((item) => item.value != null);

  return (
    <div className="stock-section" id="section-overview">
      <h2 className="detail-section-title">About</h2>

      {description && (
        <>
          <p className={`stock-description ${!expanded ? 'truncated' : ''}`}>{description}</p>
          {description.length > 300 && (
            <button className="stock-description-toggle" onClick={() => setExpanded(!expanded)}>
              {expanded ? 'Show less' : 'Read more'}
            </button>
          )}
        </>
      )}

      {infoItems.length > 0 && (
        <div className="stock-info-grid">
          {infoItems.map((item) => (
            <div key={item.label} className="stock-info-item">
              <div className="stock-info-label">{item.label}</div>
              <div className="stock-info-value">{item.value}</div>
            </div>
          ))}
        </div>
      )}

      {!description && infoItems.length === 0 && (
        <div className="stock-empty">No overview data available</div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// SECTION 2: Financials
// ---------------------------------------------------------------------------

type FinPeriod = 'annual' | 'quarterly';
type FinTab = 'income' | 'balance' | 'cashflow';

interface FinancialRow {
  label: string;
  key: string;
}

const INCOME_ROWS: FinancialRow[] = [
  { label: 'Revenue', key: 'totalRevenue' },
  { label: 'Gross Profit', key: 'grossProfit' },
  { label: 'EBITDA', key: 'ebitda' },
  { label: 'Op Income', key: 'operatingIncome' },
  { label: 'Net Income', key: 'netIncome' },
  { label: 'EPS', key: 'dilutedEPS' },
];

const BALANCE_ROWS: FinancialRow[] = [
  { label: 'Total Assets', key: 'totalAssets' },
  { label: 'Total Liabilities', key: 'totalLiab' },
  { label: 'Total Equity', key: 'totalStockholderEquity' },
  { label: 'Cash', key: 'cash' },
  { label: 'Long Term Debt', key: 'longTermDebt' },
];

const CASHFLOW_ROWS: FinancialRow[] = [
  { label: 'Operating CF', key: 'totalCashFromOperatingActivities' },
  { label: 'CapEx', key: 'capitalExpenditures' },
  { label: 'Free Cash Flow', key: 'freeCashFlow' },
  { label: 'Dividends Paid', key: 'dividendsPaid' },
];

function getFinancialYears(
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  statements: Record<string, any> | null | undefined,
  period: FinPeriod
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
): { dates: string[]; data: Record<string, any> } {
  if (!statements) return { dates: [], data: {} };

  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  const periodData = (period === 'annual' ? statements.yearly : statements.quarterly) as any;
  if (!periodData || typeof periodData !== 'object') return { dates: [], data: {} };

  const dates = Object.keys(periodData).sort().reverse();
  return { dates, data: periodData };
}

function FinancialsSection({ data }: { data: InstrumentDetail }) {
  const chartTheme = useChartTheme();
  const [period, setPeriod] = useState<FinPeriod>('annual');
  const [tab, setTab] = useState<FinTab>('income');

  const financials = data.financials;
  if (!financials) {
    return (
      <div className="stock-section" id="section-financials">
        <h2 className="detail-section-title">Financials</h2>
        <div className="stock-empty">No financial data available</div>
      </div>
    );
  }

  return (
    <div className="stock-section" id="section-financials">
      <h2 className="detail-section-title">Financials</h2>
      <div className="stock-section-grid">
        <RevenueBreakdownChart
          financials={financials}
          period={period}
          setPeriod={setPeriod}
          chartTheme={chartTheme}
        />
        <YoYTable financials={financials} period={period} tab={tab} setTab={setTab} />
      </div>
    </div>
  );
}

function RevenueBreakdownChart({
  financials,
  period,
  setPeriod,
  chartTheme,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  financials: Record<string, any>;
  period: FinPeriod;
  setPeriod: (p: FinPeriod) => void;
  chartTheme: ReturnType<typeof useChartTheme>;
}) {
  const chartData = useMemo(() => {
    const incomeStatement = financials.Income_Statement;
    if (!incomeStatement) return [];

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const periodData = (period === 'annual' ? incomeStatement.yearly : incomeStatement.quarterly) as any;
    if (!periodData || typeof periodData !== 'object') return [];

    const dates = Object.keys(periodData).sort().reverse();
    const recentDates = dates.slice(0, period === 'annual' ? 5 : 8).reverse();

    return recentDates.map((date) => {
      const entry = periodData[date] || {};
      const totalRevenue = num(entry, 'totalRevenue') || 0;
      const grossProfit = num(entry, 'grossProfit') || 0;
      const operatingIncome = num(entry, 'operatingIncome') || 0;
      const netIncome = num(entry, 'netIncome') || 0;
      const costs = totalRevenue - grossProfit;

      // Format date label
      const label = period === 'annual' ? date.substring(0, 4) : date.substring(0, 7);

      return {
        period: label,
        'Cost of Revenue': Math.max(0, costs) / 1e9,
        'Gross Profit': Math.max(0, grossProfit - operatingIncome) / 1e9,
        'Operating Income': Math.max(0, operatingIncome - netIncome) / 1e9,
        'Net Income': netIncome / 1e9,
      };
    });
  }, [financials, period]);

  if (chartData.length === 0) {
    return (
      <div className="stock-panel">
        <div className="stock-panel-title">Revenue Breakdown</div>
        <div className="stock-empty">No income data available</div>
      </div>
    );
  }

  const options: AgChartOptions = {
    theme: chartTheme.theme,
    height: 320,
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      { type: 'bar', xKey: 'period', yKey: 'Cost of Revenue', yName: 'Cost of Revenue', fill: '#94a3b8', stacked: true },
      { type: 'bar', xKey: 'period', yKey: 'Gross Profit', yName: 'Gross Profit', fill: '#546d84', stacked: true },
      { type: 'bar', xKey: 'period', yKey: 'Operating Income', yName: 'Operating Income', fill: '#3b82f6', stacked: true },
      { type: 'bar', xKey: 'period', yKey: 'Net Income', yName: 'Net Income', fill: '#10b981', stacked: true },
    ],
    axes: [
      { type: 'category', position: 'bottom' },
      {
        type: 'number',
        position: 'left',
        label: { formatter: (params: { value: number }) => `$${params.value.toFixed(0)}B` },
      },
    ],
    legend: { position: 'bottom', item: { label: { fontSize: 11 } } },
    tooltip: { enabled: true },
  };

  return (
    <div className="stock-panel">
      <div className="stock-toggle-row">
        <div className="stock-panel-title">Revenue Breakdown</div>
        <div className="stock-toggle-group">
          <button
            className={`stock-toggle-btn ${period === 'annual' ? 'active' : ''}`}
            onClick={() => setPeriod('annual')}
          >
            Annual
          </button>
          <button
            className={`stock-toggle-btn ${period === 'quarterly' ? 'active' : ''}`}
            onClick={() => setPeriod('quarterly')}
          >
            Quarterly
          </button>
        </div>
      </div>
      <div className="stock-chart-wrapper">
        <AgCharts options={options} />
      </div>
    </div>
  );
}

function YoYTable({
  financials,
  period,
  tab,
  setTab,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  financials: Record<string, any>;
  period: FinPeriod;
  tab: FinTab;
  setTab: (t: FinTab) => void;
}) {
  const statementKey =
    tab === 'income' ? 'Income_Statement' : tab === 'balance' ? 'Balance_Sheet' : 'Cash_Flow';

  const rows = tab === 'income' ? INCOME_ROWS : tab === 'balance' ? BALANCE_ROWS : CASHFLOW_ROWS;

  const { dates, data: periodData } = getFinancialYears(
    financials[statementKey],
    period
  );

  // Show last 3 periods + compute YoY for last column
  const displayDates = dates.slice(0, 3);

  return (
    <div className="stock-panel">
      <div className="stock-panel-title">Year-over-Year Changes</div>
      <div className="stock-tabs">
        <button
          className={`stock-tab-btn ${tab === 'income' ? 'active' : ''}`}
          onClick={() => setTab('income')}
        >
          Income Statement
        </button>
        <button
          className={`stock-tab-btn ${tab === 'balance' ? 'active' : ''}`}
          onClick={() => setTab('balance')}
        >
          Balance Sheet
        </button>
        <button
          className={`stock-tab-btn ${tab === 'cashflow' ? 'active' : ''}`}
          onClick={() => setTab('cashflow')}
        >
          Cash Flow
        </button>
      </div>

      {displayDates.length === 0 ? (
        <div className="stock-empty">No data available</div>
      ) : (
        <table className="stock-fin-table">
          <thead>
            <tr>
              <th>Metric</th>
              {displayDates.map((d) => (
                <th key={d}>{period === 'annual' ? d.substring(0, 4) : d.substring(0, 7)}</th>
              ))}
              <th>YoY</th>
            </tr>
          </thead>
          <tbody>
            {rows.map((row) => {
              const values = displayDates.map((d) => {
                const entry = periodData[d];
                if (!entry) return null;
                // Special handling for free cash flow
                if (row.key === 'freeCashFlow') {
                  const opCf = num(entry, 'totalCashFromOperatingActivities');
                  const capex = num(entry, 'capitalExpenditures');
                  if (opCf != null && capex != null) return opCf + capex; // capex is negative
                  return num(entry, 'freeCashFlow');
                }
                return num(entry, row.key);
              });

              const change =
                values.length >= 2 ? yoyChange(values[0], values[1]) : null;

              return (
                <tr key={row.key}>
                  <td>{row.label}</td>
                  {values.map((v, i) => (
                    <td key={displayDates[i]}>
                      {row.key === 'dilutedEPS'
                        ? formatCurrency(v)
                        : formatLargeNumber(v)}
                    </td>
                  ))}
                  <td>
                    {change != null ? (
                      <span
                        className={`stock-yoy-badge ${
                          change > 0 ? 'positive' : change < 0 ? 'negative' : 'neutral'
                        }`}
                      >
                        {change > 0 ? '+' : ''}
                        {change.toFixed(1)}%
                      </span>
                    ) : (
                      '\u2014'
                    )}
                  </td>
                </tr>
              );
            })}
          </tbody>
        </table>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// SECTION 3: Valuation
// ---------------------------------------------------------------------------

function ValuationSection({ data }: { data: InstrumentDetail }) {
  const chartTheme = useChartTheme();
  const valuation = data.valuation;
  const highlights = data.highlights;
  const financials = data.financials;

  if (!valuation && !highlights) {
    return (
      <div className="stock-section" id="section-valuation">
        <h2 className="detail-section-title">Valuation</h2>
        <div className="stock-empty">No valuation data available</div>
      </div>
    );
  }

  return (
    <div className="stock-section" id="section-valuation">
      <h2 className="detail-section-title">Valuation</h2>
      <div className="stock-section-grid">
        <ValuationBarChart valuation={valuation} chartTheme={chartTheme} />
        <MarginTrends highlights={highlights} financials={financials} />
      </div>
    </div>
  );
}

function ValuationBarChart({
  valuation,
  chartTheme,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  valuation: Record<string, any> | null;
  chartTheme: ReturnType<typeof useChartTheme>;
}) {
  const chartData = useMemo(() => {
    if (!valuation) return [];

    const metrics = [
      { label: 'Trailing PE', key: 'TrailingPE' },
      { label: 'Forward PE', key: 'ForwardPE' },
      { label: 'Price/Book', key: 'PriceBookMRQ' },
      { label: 'Price/Sales', key: 'PriceSalesTTM' },
      { label: 'EV/Revenue', key: 'EnterpriseValueRevenue' },
      { label: 'EV/EBITDA', key: 'EnterpriseValueEbitda' },
    ];

    return metrics
      .map((m) => {
        const v = num(valuation, m.key);
        return v != null ? { metric: m.label, value: Number(v.toFixed(2)) } : null;
      })
      .filter((d): d is { metric: string; value: number } => d != null);
  }, [valuation]);

  if (chartData.length === 0) {
    return (
      <div className="stock-panel">
        <div className="stock-panel-title">Valuation Ratios</div>
        <div className="stock-empty">No valuation ratios available</div>
      </div>
    );
  }

  const options: AgChartOptions = {
    theme: chartTheme.theme,
    height: 300,
    background: { fill: 'transparent' },
    data: chartData,
    series: [
      {
        type: 'bar',
        direction: 'horizontal',
        xKey: 'metric',
        yKey: 'value',
        yName: 'Ratio',
        fill: '#546d84',
        tooltip: {
          renderer: (params: { datum: { metric: string; value: number } }) => ({
            content: `${params.datum.metric}: ${params.datum.value.toFixed(2)}`,
          }),
        },
      },
    ],
    axes: [
      { type: 'category', position: 'left' },
      { type: 'number', position: 'bottom' },
    ],
    legend: { enabled: false },
  };

  return (
    <div className="stock-panel">
      <div className="stock-panel-title">Valuation Ratios</div>
      <div className="stock-chart-wrapper">
        <AgCharts options={options} />
      </div>
    </div>
  );
}

function MarginTrends({
  highlights,
  financials,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  highlights: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  financials: Record<string, any> | null;
}) {
  // Compute margins from financials yearly data
  const margins = useMemo(() => {
    const result: {
      label: string;
      value: number | null;
      color: string;
    }[] = [];

    // Compute current margins from most recent yearly data
    let profitMargin: number | null = null;
    let operatingMargin: number | null = null;
    let grossMargin: number | null = null;

    if (financials?.Income_Statement?.yearly) {
      // eslint-disable-next-line @typescript-eslint/no-explicit-any
      const yearly = financials.Income_Statement.yearly as any;
      const dates = Object.keys(yearly).sort().reverse();
      if (dates.length > 0) {
        const latest = yearly[dates[0]];
        const revenue = num(latest, 'totalRevenue');
        if (revenue && revenue !== 0) {
          const netIncome = num(latest, 'netIncome');
          const opIncome = num(latest, 'operatingIncome');
          const gross = num(latest, 'grossProfit');
          if (netIncome != null) profitMargin = (netIncome / revenue) * 100;
          if (opIncome != null) operatingMargin = (opIncome / revenue) * 100;
          if (gross != null) grossMargin = (gross / revenue) * 100;
        }
      }
    }

    // Fallback to highlights
    if (profitMargin == null && highlights) {
      const v = num(highlights, 'ProfitMargin');
      if (v != null) profitMargin = v * 100;
    }
    if (operatingMargin == null && highlights) {
      const v = num(highlights, 'OperatingMarginTTM');
      if (v != null) operatingMargin = v * 100;
    }

    result.push({ label: 'Gross Margin', value: grossMargin, color: '#3b82f6' });
    result.push({ label: 'Operating Margin', value: operatingMargin, color: '#546d84' });
    result.push({ label: 'Profit Margin', value: profitMargin, color: '#10b981' });

    // ROE and ROA from highlights
    const roe = num(highlights, 'ReturnOnEquityTTM');
    const roa = num(highlights, 'ReturnOnAssetsTTM');
    result.push({ label: 'ROE (TTM)', value: roe != null ? roe * 100 : null, color: '#8b5cf6' });
    result.push({ label: 'ROA (TTM)', value: roa != null ? roa * 100 : null, color: '#f59e0b' });

    return result;
  }, [highlights, financials]);

  const hasData = margins.some((m) => m.value != null);

  if (!hasData) {
    return (
      <div className="stock-panel">
        <div className="stock-panel-title">Margins & Returns</div>
        <div className="stock-empty">No margin data available</div>
      </div>
    );
  }

  // Find max for bar scaling
  const maxVal = Math.max(...margins.filter((m) => m.value != null).map((m) => Math.abs(m.value!)));
  const scale = maxVal > 0 ? 100 / maxVal : 1;

  return (
    <div className="stock-panel">
      <div className="stock-panel-title">Margins & Returns</div>
      <div className="stock-margin-list">
        {margins.map((m) => (
          <div key={m.label} className="stock-margin-row">
            <span className="stock-margin-label">{m.label}</span>
            <div className="stock-margin-bar-wrap">
              {m.value != null && (
                <div
                  className="stock-margin-bar-fill"
                  style={{
                    width: `${Math.min(100, Math.abs(m.value) * scale)}%`,
                    backgroundColor: m.value >= 0 ? m.color : 'var(--danger-text)',
                  }}
                />
              )}
            </div>
            <span
              className="stock-margin-value"
              style={{ color: m.value != null && m.value < 0 ? 'var(--danger-text)' : undefined }}
            >
              {m.value != null ? `${m.value.toFixed(1)}%` : '\u2014'}
            </span>
          </div>
        ))}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// SECTION 4: Technicals
// ---------------------------------------------------------------------------

function TechnicalsSection({ data }: { data: InstrumentDetail }) {
  const technicals = data.technicals;

  if (!technicals) {
    return (
      <div className="stock-section" id="section-technicals">
        <h2 className="detail-section-title">Technicals</h2>
        <div className="stock-empty">No technical data available</div>
      </div>
    );
  }

  const beta = num(technicals, 'Beta');
  const ma50 = num(technicals, '50DayMA');
  const ma200 = num(technicals, '200DayMA');
  const shortRatio = num(technicals, 'ShortRatio');
  const low52 = num(technicals, '52WeekLow');
  const high52 = num(technicals, '52WeekHigh');
  const shortPercent = num(technicals, 'ShortPercent');

  const techMetrics = [
    { label: 'Beta', value: formatDecimal(beta, 2) },
    { label: '50-Day MA', value: formatCurrency(ma50) },
    { label: '200-Day MA', value: formatCurrency(ma200) },
    { label: 'Short Ratio', value: formatDecimal(shortRatio, 2) },
    { label: 'Short % of Float', value: shortPercent != null ? formatPercent(shortPercent) : '\u2014' },
  ];

  // 52-week range position
  let rangePosition = 50;
  let currentPrice: number | null = null;
  if (low52 != null && high52 != null && high52 > low52) {
    currentPrice = ma50 ?? (low52 + high52) / 2;
    rangePosition = Math.max(0, Math.min(100, ((currentPrice - low52) / (high52 - low52)) * 100));
  }

  // MA positions on the range
  let ma50Position: number | null = null;
  let ma200Position: number | null = null;
  if (low52 != null && high52 != null && high52 > low52) {
    if (ma50 != null) {
      ma50Position = Math.max(0, Math.min(100, ((ma50 - low52) / (high52 - low52)) * 100));
    }
    if (ma200 != null) {
      ma200Position = Math.max(0, Math.min(100, ((ma200 - low52) / (high52 - low52)) * 100));
    }
  }

  return (
    <div className="stock-section" id="section-technicals">
      <h2 className="detail-section-title">Technicals</h2>
      <div className="stock-tech-grid">
        <div className="stock-panel">
          <div className="stock-panel-title">Key Technical Metrics</div>
          <table className="stock-tech-table">
            <tbody>
              {techMetrics.map((m) => (
                <tr key={m.label}>
                  <td>{m.label}</td>
                  <td>{m.value}</td>
                </tr>
              ))}
            </tbody>
          </table>
        </div>

        <div className="stock-panel">
          <div className="stock-panel-title">52-Week Range</div>
          {low52 != null && high52 != null ? (
            <div className="stock-range-full">
              <div className="stock-range-track">
                <div className="stock-range-track-fill" />
                {ma200Position != null && (
                  <div
                    className="stock-range-ma-marker"
                    style={{ left: `${ma200Position}%` }}
                    title={`200-Day MA: ${formatCurrency(ma200)}`}
                  />
                )}
                {ma50Position != null && (
                  <div
                    className="stock-range-ma-marker"
                    style={{ left: `${ma50Position}%` }}
                    title={`50-Day MA: ${formatCurrency(ma50)}`}
                  />
                )}
                <div className="stock-range-marker" style={{ left: `${rangePosition}%` }} />
              </div>
              <div className="stock-range-labels">
                <span>{formatCurrency(low52)}</span>
                <span>{formatCurrency(high52)}</span>
              </div>
              <div className="stock-range-ma-legend">
                {ma50 != null && (
                  <span>
                    <span className="stock-ma-dot" style={{ backgroundColor: '#3b82f6' }} />
                    50-Day MA: {formatCurrency(ma50)}
                  </span>
                )}
                {ma200 != null && (
                  <span>
                    <span className="stock-ma-dot" style={{ backgroundColor: '#f59e0b' }} />
                    200-Day MA: {formatCurrency(ma200)}
                  </span>
                )}
              </div>
            </div>
          ) : (
            <div className="stock-empty">No range data available</div>
          )}
        </div>
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// SECTION 5: Dividends & Ownership
// ---------------------------------------------------------------------------

function DividendsOwnershipSection({ data }: { data: InstrumentDetail }) {
  const chartTheme = useChartTheme();
  const splitsDividends = data.splitsDividends;
  const sharesStats = data.sharesStats;

  if (!splitsDividends && !sharesStats) {
    return (
      <div className="stock-section" id="section-dividends">
        <h2 className="detail-section-title">Dividends & Ownership</h2>
        <div className="stock-empty">No dividend or ownership data available</div>
      </div>
    );
  }

  return (
    <div className="stock-section" id="section-dividends">
      <h2 className="detail-section-title">Dividends & Ownership</h2>
      <div className="stock-section-grid">
        <DividendHistoryPanel splitsDividends={splitsDividends} chartTheme={chartTheme} />
        <OwnershipDonutPanel sharesStats={sharesStats} chartTheme={chartTheme} />
      </div>
    </div>
  );
}

function DividendHistoryPanel({
  splitsDividends,
  chartTheme,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  splitsDividends: Record<string, any> | null;
  chartTheme: ReturnType<typeof useChartTheme>;
}) {
  const chartData = useMemo(() => {
    if (!splitsDividends?.NumberDividendsByYear) return [];

    // eslint-disable-next-line @typescript-eslint/no-explicit-any
    const byYear = splitsDividends.NumberDividendsByYear as any;
    if (typeof byYear !== 'object') return [];

    const entries: { year: string; count: number }[] = [];
    for (const key of Object.keys(byYear)) {
      const entry = byYear[key];
      if (entry && entry.Year != null && entry.Count != null) {
        entries.push({ year: String(entry.Year), count: Number(entry.Count) });
      }
    }

    return entries.sort((a, b) => Number(a.year) - Number(b.year)).slice(-10);
  }, [splitsDividends]);

  const payoutRatio = num(splitsDividends, 'PayoutRatio');
  const exDivDate = str(splitsDividends, 'ExDividendDate');
  const forwardRate = num(splitsDividends, 'ForwardAnnualDividendRate');
  const forwardYield = num(splitsDividends, 'ForwardAnnualDividendYield');

  const divStats = [
    { label: 'Payout Ratio', value: payoutRatio != null ? formatPercentRaw(payoutRatio * 100) : '\u2014' },
    { label: 'Ex-Dividend Date', value: exDivDate ? formatDate(exDivDate) : '\u2014' },
    { label: 'Forward Rate', value: forwardRate != null ? formatCurrency(forwardRate) : '\u2014' },
    { label: 'Forward Yield', value: forwardYield != null ? formatPercent(forwardYield) : '\u2014' },
  ];

  const chartOptions: AgChartOptions | null =
    chartData.length > 0
      ? {
          theme: chartTheme.theme,
          height: 200,
          background: { fill: 'transparent' },
          data: chartData,
          series: [
            {
              type: 'bar',
              xKey: 'year',
              yKey: 'count',
              yName: 'Dividends',
              fill: '#546d84',
              tooltip: {
                renderer: (params: { datum: { year: string; count: number } }) => ({
                  content: `${params.datum.year}: ${params.datum.count} dividends`,
                }),
              },
            },
          ],
          axes: [
            { type: 'category', position: 'bottom' },
            {
              type: 'number',
              position: 'left',
              label: { formatter: (params: { value: number }) => String(Math.round(params.value)) },
            },
          ],
          legend: { enabled: false },
        }
      : null;

  return (
    <div className="stock-panel">
      <div className="stock-panel-title">Dividend History</div>
      {chartOptions ? (
        <div className="stock-chart-wrapper">
          <AgCharts options={chartOptions} />
        </div>
      ) : (
        <div className="stock-empty">No dividend history data</div>
      )}
      <div className="stock-div-stats">
        {divStats.map((s) => (
          <div key={s.label} className="stock-div-stat">
            <div className="stock-div-stat-label">{s.label}</div>
            <div className="stock-div-stat-value">{s.value}</div>
          </div>
        ))}
      </div>
    </div>
  );
}

const OWNERSHIP_COLORS = {
  institutions: '#3b82f6',
  insiders: '#8b5cf6',
  publicFloat: '#94a3b8',
};

function OwnershipDonutPanel({
  sharesStats,
  chartTheme,
}: {
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  sharesStats: Record<string, any> | null;
  chartTheme: ReturnType<typeof useChartTheme>;
}) {
  const donutData = useMemo(() => {
    if (!sharesStats) return [];

    const institutions = num(sharesStats, 'PercentInstitutions') ?? 0;
    const insiders = num(sharesStats, 'PercentInsiders') ?? 0;
    const publicFloat = Math.max(0, 100 - institutions - insiders);

    // Only show if we have at least some data
    if (institutions === 0 && insiders === 0) return [];

    return [
      { label: 'Institutions', value: Number(institutions.toFixed(1)), color: OWNERSHIP_COLORS.institutions },
      { label: 'Insiders', value: Number(insiders.toFixed(1)), color: OWNERSHIP_COLORS.insiders },
      { label: 'Public Float', value: Number(publicFloat.toFixed(1)), color: OWNERSHIP_COLORS.publicFloat },
    ];
  }, [sharesStats]);

  const sharesOutstanding = num(sharesStats, 'SharesOutstanding');
  const shortRatio = num(sharesStats, 'ShortRatio');

  const chartOptions: AgChartOptions | null =
    donutData.length > 0
      ? {
          theme: chartTheme.theme,
          height: 240,
          background: { fill: 'transparent' },
          series: [
            {
              type: 'donut' as const,
              calloutLabelKey: 'label',
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
        }
      : null;

  return (
    <div className="stock-panel">
      <div className="stock-panel-title">Ownership Breakdown</div>
      {chartOptions ? (
        <>
          <div className="stock-chart-wrapper">
            <AgCharts options={chartOptions} />
          </div>
          <div className="stock-ownership-center">
            <div className="stock-ownership-center-label">Shares Outstanding</div>
            <div className="stock-ownership-center-value">
              {sharesOutstanding != null ? formatLargeNumber(sharesOutstanding) : '\u2014'}
            </div>
          </div>
          <div className="stock-ownership-legend">
            {donutData.map((d) => (
              <div key={d.label} className="stock-ownership-legend-item">
                <span className="stock-ownership-dot" style={{ backgroundColor: d.color }} />
                <span>{d.label}: {d.value}%</span>
              </div>
            ))}
          </div>
        </>
      ) : (
        <div className="stock-empty">No ownership data available</div>
      )}
      {shortRatio != null && (
        <div className="stock-short-interest">
          Short Interest Ratio: <strong>{formatDecimal(shortRatio, 2)}</strong>
        </div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// SECTION 6: Analyst Ratings
// ---------------------------------------------------------------------------

const ANALYST_COLORS = {
  strongBuy: '#059669',
  buy: '#34d399',
  hold: '#94a3b8',
  sell: '#f87171',
  strongSell: '#dc2626',
};

function AnalystRatingsSection({ data }: { data: InstrumentDetail }) {
  const ratings = data.analystRatings;

  // Check if we have meaningful analyst data
  if (!ratings) return null;

  const strongBuy = num(ratings, 'StrongBuy');
  const buy = num(ratings, 'Buy');
  const hold = num(ratings, 'Hold');
  const sell = num(ratings, 'Sell');
  const strongSell = num(ratings, 'StrongSell');
  const targetPrice = num(ratings, 'TargetPrice');
  const ratingText = str(ratings, 'Rating');

  // If all counts are null or zero, skip
  const total = (strongBuy ?? 0) + (buy ?? 0) + (hold ?? 0) + (sell ?? 0) + (strongSell ?? 0);
  if (total === 0 && targetPrice == null) return null;

  const segments = [
    { label: 'Strong Buy', value: strongBuy ?? 0, color: ANALYST_COLORS.strongBuy },
    { label: 'Buy', value: buy ?? 0, color: ANALYST_COLORS.buy },
    { label: 'Hold', value: hold ?? 0, color: ANALYST_COLORS.hold },
    { label: 'Sell', value: sell ?? 0, color: ANALYST_COLORS.sell },
    { label: 'Strong Sell', value: strongSell ?? 0, color: ANALYST_COLORS.strongSell },
  ];

  return (
    <div className="stock-section" id="section-earnings">
      <h2 className="detail-section-title">Analyst Ratings</h2>
      <div className="stock-analyst-container">
        {total > 0 && (
          <div className="stock-analyst-bar-container">
            <div className="stock-analyst-bar">
              {segments.map(
                (seg) =>
                  seg.value > 0 && (
                    <div
                      key={seg.label}
                      className="stock-analyst-segment"
                      style={{
                        flex: seg.value,
                        backgroundColor: seg.color,
                      }}
                    >
                      {seg.value}
                    </div>
                  )
              )}
            </div>
            <div className="stock-analyst-legend">
              {segments.map(
                (seg) =>
                  seg.value > 0 && (
                    <div key={seg.label} className="stock-analyst-legend-item">
                      <span className="stock-analyst-dot" style={{ backgroundColor: seg.color }} />
                      <span>{seg.label} ({seg.value})</span>
                    </div>
                  )
              )}
            </div>
          </div>
        )}

        {targetPrice != null && (
          <div className="stock-analyst-target">
            <div className="stock-analyst-target-label">Target Price</div>
            <div className="stock-analyst-target-value">{formatCurrency(targetPrice)}</div>
            {ratingText && <div className="stock-analyst-rating">{ratingText}</div>}
          </div>
        )}
      </div>
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Component
// ---------------------------------------------------------------------------

export function StockDetailSections({ data }: StockDetailSectionsProps) {
  return (
    <div className="stock-sections">
      <AboutSection data={data} />
      <FinancialsSection data={data} />
      <ValuationSection data={data} />
      <TechnicalsSection data={data} />
      <DividendsOwnershipSection data={data} />
      <AnalystRatingsSection data={data} />
    </div>
  );
}
