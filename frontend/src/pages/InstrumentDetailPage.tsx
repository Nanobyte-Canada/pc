import { useState, useCallback, useMemo } from 'react';
import { useParams, Link } from 'react-router-dom';
import { useInstrumentDetail } from '@/hooks/useNewScreener';
import { usePortfolioStore } from '@/store/portfolioStore';
import {
  INSTRUMENT_TYPE_CONFIG,
  type InstrumentDetail,
  type InstrumentType,
} from '@/types/screener';
import { StockDetailSections } from '@/components/instruments/detail/StockDetailSections';
import { EtfDetailSections } from '@/components/instruments/detail/EtfDetailSections';
import { MutualFundDetailSections } from '@/components/instruments/detail/MutualFundDetailSections';
import { BasicDetailSections } from '@/components/instruments/detail/BasicDetailSections';
import './InstrumentDetailPage.css';

// ---------------------------------------------------------------------------
// Helpers
// ---------------------------------------------------------------------------

/** Map route param (e.g. "stock") to the InstrumentType key (e.g. "STOCK"). */
function detailRouteToTypeKey(detailRoute: string): string | undefined {
  return Object.keys(INSTRUMENT_TYPE_CONFIG).find(
    (k) => INSTRUMENT_TYPE_CONFIG[k].detailRoute === detailRoute
  );
}

function fmtLargeNumber(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  const abs = Math.abs(value);
  if (abs >= 1e12) return `$${(value / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `$${(value / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `$${(value / 1e6).toFixed(1)}M`;
  return `$${value.toLocaleString()}`;
}

function fmtDollar(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  return `$${Number(value).toFixed(2)}`;
}

function fmtPct(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  return `${Number(value).toFixed(2)}%`;
}

function fmtDecimal(value: number | null | undefined, decimals = 2): string {
  if (value == null) return '\u2014';
  return Number(value).toFixed(decimals);
}

function fmtDate(value: string | null | undefined): string {
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
function getNestedValue(data: Record<string, any> | null | undefined, ...keys: string[]): any {
  if (!data) return null;
  for (const key of keys) {
    if (data[key] !== undefined && data[key] !== null && data[key] !== '' && data[key] !== 'None' && data[key] !== '0') {
      return data[key];
    }
  }
  return null;
}

// ---------------------------------------------------------------------------
// Subtitle builder
// ---------------------------------------------------------------------------

function buildSubtitle(detail: InstrumentDetail, typeKey: string): string {
  const parts: string[] = [];
  const g = detail.general;

  switch (typeKey) {
    case 'STOCK': {
      const sector = getNestedValue(g, 'GicSector', 'Sector');
      const subIndustry = getNestedValue(g, 'GicSubIndustry', 'Industry');
      const exchange = getNestedValue(g, 'Exchange') ?? detail.general?.Exchange;
      const currency = detail.currency ?? getNestedValue(g, 'CurrencyCode');
      if (sector) parts.push(sector);
      if (subIndustry) parts.push(subIndustry);
      if (exchange) parts.push(exchange);
      if (currency) parts.push(currency);
      break;
    }
    case 'ETF': {
      const companyName = getNestedValue(g, 'Company_Name');
      const assetCategory = getNestedValue(detail.etfData, 'Asset_Class') ?? getNestedValue(g, 'Category');
      const exchange = getNestedValue(g, 'Exchange');
      const currency = detail.currency ?? getNestedValue(g, 'CurrencyCode');
      if (companyName) parts.push(companyName);
      if (assetCategory) parts.push(assetCategory);
      if (exchange) parts.push(exchange);
      if (currency) parts.push(currency);
      break;
    }
    case 'MUTUAL_FUND': {
      const fundCategory = getNestedValue(detail.mutualFundData, 'Fund_Category') ?? getNestedValue(g, 'Category');
      const fundStyle = getNestedValue(detail.mutualFundData, 'Fund_Style');
      const currency = detail.currency ?? getNestedValue(g, 'CurrencyCode');
      if (fundCategory) parts.push(fundCategory);
      if (fundStyle) parts.push(fundStyle);
      if (currency) parts.push(currency);
      break;
    }
    default: {
      const exchange = getNestedValue(g, 'Exchange');
      const currency = detail.currency ?? getNestedValue(g, 'CurrencyCode');
      const country = detail.country ?? getNestedValue(g, 'CountryName');
      if (exchange) parts.push(exchange);
      if (currency) parts.push(currency);
      if (country) parts.push(country);
      break;
    }
  }

  return parts.filter(Boolean).join(' \u00B7 ');
}

// ---------------------------------------------------------------------------
// Hero Metrics
// ---------------------------------------------------------------------------

interface MetricDef {
  label: string;
  value: string;
  sub?: string;
  subClass?: string;
  rangeBar?: { low: number; high: number; current: number };
}

function getStockMetrics(detail: InstrumentDetail): MetricDef[] {
  const h = detail.highlights;
  const t = detail.technicals;

  const marketCap = getNestedValue(h, 'MarketCapitalization');
  const pe = getNestedValue(h, 'PERatio');
  const forwardPE = getNestedValue(h, 'ForwardPE');
  const eps = getNestedValue(h, 'EarningsShare');
  const epsYoYChange = getNestedValue(h, 'EPSEstimateNextYear');
  const divYield = getNestedValue(h, 'DividendYield');
  const divPerShare = getNestedValue(h, 'DividendShare');
  const beta = getNestedValue(t, 'Beta');
  const low52 = getNestedValue(t, '52WeekLow');
  const high52 = getNestedValue(t, '52WeekHigh');

  // Compute current price position in 52-week range
  let rangeBar: MetricDef['rangeBar'] = undefined;
  if (low52 != null && high52 != null && Number(high52) > Number(low52)) {
    // Use 50-day MA as a proxy for current if available
    const current = getNestedValue(t, '50DayMA') ?? ((Number(low52) + Number(high52)) / 2);
    rangeBar = { low: Number(low52), high: Number(high52), current: Number(current) };
  }

  const metrics: MetricDef[] = [
    {
      label: 'Market Cap',
      value: fmtLargeNumber(marketCap != null ? Number(marketCap) : null),
      sub: marketCap != null ? getCapCategory(Number(marketCap)) : undefined,
    },
    {
      label: 'P/E Ratio',
      value: pe != null ? fmtDecimal(Number(pe), 1) : '\u2014',
      sub: forwardPE != null ? `Forward: ${fmtDecimal(Number(forwardPE), 1)}` : undefined,
    },
    {
      label: 'EPS (TTM)',
      value: eps != null ? fmtDollar(Number(eps)) : '\u2014',
      sub: epsYoYChange != null ? `Est next yr: $${fmtDecimal(Number(epsYoYChange), 2)}` : undefined,
    },
    {
      label: 'Dividend Yield',
      value: divYield != null ? fmtPct(Number(divYield) * 100) : '\u2014',
      sub: divPerShare != null ? `$${fmtDecimal(Number(divPerShare), 2)}/share` : undefined,
    },
    {
      label: 'Beta',
      value: beta != null ? fmtDecimal(Number(beta), 2) : '\u2014',
      sub: 'vs. S&P 500',
    },
    {
      label: '52-Week Range',
      value: '',
      rangeBar,
    },
  ];

  return metrics;
}

function getCapCategory(marketCap: number): string {
  if (marketCap >= 200e9) return 'Mega Cap';
  if (marketCap >= 10e9) return 'Large Cap';
  if (marketCap >= 2e9) return 'Mid Cap';
  if (marketCap >= 300e6) return 'Small Cap';
  return 'Micro Cap';
}

function getEtfMetrics(detail: InstrumentDetail): MetricDef[] {
  const etf = detail.etfData;
  const g = detail.general;

  const totalAssets = getNestedValue(etf, 'Net_Assets') ?? getNestedValue(etf, 'TotalAssets') ?? getNestedValue(g, 'TotalAssets');
  const expenseRatio = getNestedValue(etf, 'Expense_Ratio') ?? getNestedValue(etf, 'NetExpenseRatio');
  const yieldVal = getNestedValue(etf, 'Yield') ?? getNestedValue(g, 'Yield');
  const holdingsCount = getNestedValue(etf, 'Holdings_Count');
  const inception = getNestedValue(etf, 'Inception_Date') ?? getNestedValue(g, 'InceptionDate');
  const turnover = getNestedValue(etf, 'Annual_Holdings_Turnover') ?? getNestedValue(etf, 'Turnover');

  return [
    {
      label: 'Total Assets',
      value: fmtLargeNumber(totalAssets != null ? Number(totalAssets) : null),
    },
    {
      label: 'Expense Ratio',
      value: expenseRatio != null ? fmtPct(Number(expenseRatio) * (Number(expenseRatio) > 1 ? 1 : 100)) : '\u2014',
    },
    {
      label: 'Yield',
      value: yieldVal != null ? fmtPct(Number(yieldVal) * (Number(yieldVal) > 1 ? 1 : 100)) : '\u2014',
    },
    {
      label: 'Holdings',
      value: holdingsCount != null ? String(Number(holdingsCount).toLocaleString()) : '\u2014',
    },
    {
      label: 'Inception Date',
      value: fmtDate(inception),
    },
    {
      label: 'Turnover',
      value: turnover != null ? fmtPct(Number(turnover) * (Number(turnover) > 1 ? 1 : 100)) : '\u2014',
    },
  ];
}

function getMutualFundMetrics(detail: InstrumentDetail): MetricDef[] {
  const mf = detail.mutualFundData;
  const g = detail.general;

  const nav = getNestedValue(mf, 'Net_Assets') ?? getNestedValue(g, 'PreviousClose');
  const expenseRatio = getNestedValue(mf, 'Expense_Ratio') ?? getNestedValue(mf, 'NetExpenseRatio');
  const yieldVal = getNestedValue(mf, 'Yield') ?? getNestedValue(g, 'Yield');
  const netAssets = getNestedValue(mf, 'Net_Assets') ?? getNestedValue(g, 'TotalAssets');
  const inception = getNestedValue(mf, 'Inception_Date') ?? getNestedValue(g, 'InceptionDate');
  const prevClose = getNestedValue(g, 'PreviousClose');

  return [
    {
      label: 'NAV',
      value: prevClose != null ? fmtDollar(Number(prevClose)) : '\u2014',
      sub: nav != null ? `Net assets: ${fmtLargeNumber(Number(nav))}` : undefined,
    },
    {
      label: 'Expense Ratio',
      value: expenseRatio != null ? fmtPct(Number(expenseRatio) * (Number(expenseRatio) > 1 ? 1 : 100)) : '\u2014',
    },
    {
      label: 'Yield',
      value: yieldVal != null ? fmtPct(Number(yieldVal) * (Number(yieldVal) > 1 ? 1 : 100)) : '\u2014',
    },
    {
      label: 'Net Assets',
      value: fmtLargeNumber(netAssets != null ? Number(netAssets) : null),
    },
    {
      label: 'Inception Date',
      value: fmtDate(inception),
    },
    {
      label: 'Prev Close',
      value: prevClose != null ? fmtDollar(Number(prevClose)) : '\u2014',
    },
  ];
}

function getSparseMetrics(detail: InstrumentDetail): MetricDef[] {
  const g = detail.general;

  const metrics: MetricDef[] = [];

  const prevClose = getNestedValue(g, 'PreviousClose');
  if (prevClose != null) {
    metrics.push({ label: 'Previous Close', value: fmtDollar(Number(prevClose)) });
  }

  const open = getNestedValue(g, 'Open');
  if (open != null) {
    metrics.push({ label: 'Open', value: fmtDollar(Number(open)) });
  }

  const dayLow = getNestedValue(g, 'DayLow');
  const dayHigh = getNestedValue(g, 'DayHigh');
  if (dayLow != null || dayHigh != null) {
    metrics.push({
      label: 'Day Range',
      value: `${fmtDollar(dayLow != null ? Number(dayLow) : null)} - ${fmtDollar(dayHigh != null ? Number(dayHigh) : null)}`,
    });
  }

  const exchange = getNestedValue(g, 'Exchange');
  if (exchange) {
    metrics.push({ label: 'Exchange', value: exchange });
  }

  const currency = detail.currency ?? getNestedValue(g, 'CurrencyCode');
  if (currency) {
    metrics.push({ label: 'Currency', value: currency });
  }

  const country = detail.country ?? getNestedValue(g, 'CountryName');
  if (country) {
    metrics.push({ label: 'Country', value: country });
  }

  // Pad to 6 if needed
  while (metrics.length < 6) {
    metrics.push({ label: '', value: '' });
  }

  return metrics.slice(0, 6);
}

function getMetrics(detail: InstrumentDetail, typeKey: string): MetricDef[] {
  switch (typeKey) {
    case 'STOCK':
      return getStockMetrics(detail);
    case 'ETF':
      return getEtfMetrics(detail);
    case 'MUTUAL_FUND':
      return getMutualFundMetrics(detail);
    default:
      return getSparseMetrics(detail);
  }
}

// ---------------------------------------------------------------------------
// Section nav config
// ---------------------------------------------------------------------------

const SECTION_NAV: Record<string, { id: string; label: string }[]> = {
  STOCK: [
    { id: 'overview', label: 'Overview' },
    { id: 'financials', label: 'Financials' },
    { id: 'valuation', label: 'Valuation' },
    { id: 'technicals', label: 'Technicals' },
    { id: 'dividends', label: 'Dividends' },
    { id: 'ownership', label: 'Ownership' },
    { id: 'earnings', label: 'Earnings' },
  ],
  ETF: [
    { id: 'overview', label: 'Overview' },
    { id: 'performance', label: 'Performance' },
    { id: 'holdings', label: 'Holdings' },
    { id: 'sectors', label: 'Sectors' },
    { id: 'regions', label: 'Regions' },
    { id: 'valuation', label: 'Valuation' },
  ],
  MUTUAL_FUND: [
    { id: 'overview', label: 'Overview' },
    { id: 'performance', label: 'Performance' },
    { id: 'holdings', label: 'Holdings' },
    { id: 'sectors', label: 'Sectors' },
    { id: 'regions', label: 'Regions' },
    { id: 'valuation', label: 'Valuation' },
  ],
};

const DEFAULT_SECTIONS = [{ id: 'overview', label: 'Overview' }];

// ---------------------------------------------------------------------------
// MetricCard component
// ---------------------------------------------------------------------------

function MetricCard({ metric }: { metric: MetricDef }) {
  if (metric.rangeBar) {
    const { low, high, current } = metric.rangeBar;
    const range = high - low;
    const position = range > 0 ? Math.max(0, Math.min(100, ((current - low) / range) * 100)) : 50;

    return (
      <div className="metric-card">
        <div className="metric-label">{metric.label}</div>
        <div className="range-bar-container">
          <div className="range-bar">
            <div className="range-bar-fill" />
            <div className="range-bar-marker" style={{ left: `${position}%` }} />
          </div>
          <div className="range-bar-labels">
            <span>{fmtDollar(low)}</span>
            <span>{fmtDollar(high)}</span>
          </div>
        </div>
      </div>
    );
  }

  // Hide empty filler cards
  if (!metric.label && !metric.value) {
    return <div className="metric-card" style={{ visibility: 'hidden' }} />;
  }

  return (
    <div className="metric-card">
      <div className="metric-label">{metric.label}</div>
      <div className="metric-value">{metric.value}</div>
      {metric.sub && (
        <div className={`metric-sub ${metric.subClass ?? ''}`}>{metric.sub}</div>
      )}
    </div>
  );
}

// ---------------------------------------------------------------------------
// Main Page Component
// ---------------------------------------------------------------------------

export function InstrumentDetailPage() {
  const { type: routeType = '', ticker = '' } = useParams<{ type: string; ticker: string }>();
  const typeKey = detailRouteToTypeKey(routeType) ?? 'STOCK';
  const instrumentType = typeKey as InstrumentType;
  const config = INSTRUMENT_TYPE_CONFIG[typeKey];

  const { data: detail, isLoading, isError } = useInstrumentDetail(typeKey, ticker);

  const [activeSection, setActiveSection] = useState('overview');

  const addPosition = usePortfolioStore((s) => s.addPosition);
  const hasPosition = usePortfolioStore((s) => s.hasPosition);

  // Portfolio store uses old InstrumentType ('STOCK' | 'ETF'), so only enable for those
  const canAddToPortfolio = typeKey === 'STOCK' || typeKey === 'ETF';
  const isInPortfolio = detail ? hasPosition(instrumentType as 'STOCK' | 'ETF', detail.id) : false;

  const handleAddToPortfolio = useCallback(() => {
    if (!detail || !canAddToPortfolio) return;
    addPosition({
      instrumentType: instrumentType as 'STOCK' | 'ETF',
      instrumentId: detail.id,
      symbol: detail.ticker,
      name: detail.name,
    });
  }, [detail, instrumentType, addPosition, canAddToPortfolio]);

  const subtitle = useMemo(
    () => (detail ? buildSubtitle(detail, typeKey) : ''),
    [detail, typeKey]
  );

  const metrics = useMemo(
    () => (detail ? getMetrics(detail, typeKey) : []),
    [detail, typeKey]
  );

  const sections = SECTION_NAV[typeKey] ?? DEFAULT_SECTIONS;

  const handleNavClick = useCallback((sectionId: string) => {
    setActiveSection(sectionId);
    const el = document.getElementById(`section-${sectionId}`);
    if (el) {
      el.scrollIntoView({ behavior: 'smooth', block: 'start' });
    }
  }, []);

  const updatedAt = useMemo(() => {
    if (!detail?.general?.UpdatedAt) return null;
    return fmtDate(detail.general.UpdatedAt);
  }, [detail]);

  // Loading state
  if (isLoading) {
    return (
      <div className="detail-page">
        <div className="detail-loading">Loading instrument data...</div>
      </div>
    );
  }

  // Error state
  if (isError || !detail) {
    return (
      <div className="detail-page">
        <div className="detail-error">
          Instrument not found. The ticker "{ticker}" may not exist or data is unavailable.
        </div>
      </div>
    );
  }

  return (
    <div className="detail-page">
      {/* Breadcrumb */}
      <nav className="detail-breadcrumb">
        <Link to={`/screener/${config?.route ?? 'stocks'}`}>Screener</Link>
        <span className="separator">&rsaquo;</span>
        <Link to={`/screener/${config?.route ?? 'stocks'}`}>
          {config?.pluralLabel ?? 'Instruments'}
        </Link>
        <span className="separator">&rsaquo;</span>
        <span>{detail.ticker}</span>
      </nav>

      {/* Header */}
      <div className="detail-header">
        <div className="detail-header-left">
          <h1>
            {detail.name}
            <span className="detail-badge detail-badge-ticker">{detail.ticker}</span>
            <span className={`detail-badge detail-badge-type-${typeKey}`}>
              {config?.label ?? typeKey}
            </span>
          </h1>
          {subtitle && <div className="detail-subtitle">{subtitle}</div>}
        </div>
        {canAddToPortfolio && (
          <button
            className={`detail-btn-add ${isInPortfolio ? 'added' : ''}`}
            onClick={handleAddToPortfolio}
            disabled={isInPortfolio}
          >
            {isInPortfolio ? 'In Portfolio' : '+ Add to Portfolio'}
          </button>
        )}
      </div>

      {/* Hero Metrics */}
      {metrics.length > 0 && (
        <div className="hero-metrics">
          {metrics.map((m, i) => (
            <MetricCard key={m.label || i} metric={m} />
          ))}
        </div>
      )}

      {/* Section Navigation */}
      {sections.length > 1 && (
        <div className="section-nav">
          {sections.map((s) => (
            <button
              key={s.id}
              className={`section-nav-item ${activeSection === s.id ? 'active' : ''}`}
              onClick={() => handleNavClick(s.id)}
            >
              {s.label}
            </button>
          ))}
        </div>
      )}

      {/* Type-specific sections */}
      {instrumentType === 'STOCK' && <StockDetailSections data={detail} />}
      {instrumentType === 'ETF' && <EtfDetailSections data={detail} />}
      {instrumentType === 'MUTUAL_FUND' && <MutualFundDetailSections data={detail} />}
      {(['PREFERRED_STOCK', 'INDEX', 'BOND'] as string[]).includes(instrumentType) && (
        <BasicDetailSections data={detail} />
      )}

      {/* Footer */}
      <div className="detail-footer">
        Data sourced from EODHD{updatedAt ? ` \u00B7 Last updated: ${updatedAt}` : ''}
      </div>
    </div>
  );
}
