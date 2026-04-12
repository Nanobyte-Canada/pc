import { useState, useMemo, useCallback, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef, ICellRendererParams } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import { Search } from 'lucide-react';
import { ScreenerFilters, FilterInput, FilterSelect } from '@/components/screener/ScreenerFilters';
import { Pagination } from '@/components/ui/Pagination';
import { Skeleton } from '@/components/ui/skeleton';
import { useInstrumentScreener, useTypeCounts, useReferenceValues } from '@/hooks/useNewScreener';
import {
  INSTRUMENT_TYPE_CONFIG,
  type InstrumentScreenerItem,
  type ScreenerFilter,
} from '@/types/screener';
import './ScreenerPage.css';

const PAGE_SIZE = 50;

/** Resolve the route param (e.g. "stocks") to the InstrumentType key (e.g. "STOCK"). */
function routeToTypeKey(route: string): string | undefined {
  return Object.keys(INSTRUMENT_TYPE_CONFIG).find(
    (k) => INSTRUMENT_TYPE_CONFIG[k].route === route
  );
}

// ---------------------------------------------------------------------------
// Formatters
// ---------------------------------------------------------------------------

function fmtMarketCap(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  const abs = Math.abs(value);
  if (abs >= 1e12) return `$${(value / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `$${(value / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `$${(value / 1e6).toFixed(1)}M`;
  return `$${value.toLocaleString()}`;
}

function fmtDollar(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  return `$${value.toFixed(2)}`;
}

function fmtPct(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  return `${value.toFixed(2)}%`;
}

function fmtDecimal(value: number | null | undefined, decimals: number): string {
  if (value == null) return '\u2014';
  return value.toFixed(decimals);
}

function fmtAssets(value: number | null | undefined): string {
  if (value == null) return '\u2014';
  const abs = Math.abs(value);
  if (abs >= 1e12) return `$${(value / 1e12).toFixed(2)}T`;
  if (abs >= 1e9) return `$${(value / 1e9).toFixed(2)}B`;
  if (abs >= 1e6) return `$${(value / 1e6).toFixed(1)}M`;
  return `$${value.toLocaleString()}`;
}

// ---------------------------------------------------------------------------
// Column definitions per type
// ---------------------------------------------------------------------------

function getColumnDefs(typeKey: string, navigate: ReturnType<typeof useNavigate>): ColDef<InstrumentScreenerItem>[] {
  const config = INSTRUMENT_TYPE_CONFIG[typeKey];
  const detailRoute = config?.detailRoute ?? 'stock';

  const tickerCol: ColDef<InstrumentScreenerItem> = {
    field: 'ticker',
    headerName: 'Ticker',
    width: 110,
    pinned: 'left',
    cellRenderer: (params: ICellRendererParams<InstrumentScreenerItem>) => {
      if (!params.value) return '\u2014';
      return <span className="ticker-link">{params.value}</span>;
    },
    onCellClicked: (params) => {
      if (params.data) {
        navigate(`/instruments/${detailRoute}/${params.data.ticker}`);
      }
    },
  };

  const nameCol: ColDef<InstrumentScreenerItem> = {
    field: 'name',
    headerName: 'Name',
    flex: 2,
    minWidth: 200,
  };

  switch (typeKey) {
    case 'STOCK':
      return [
        tickerCol,
        nameCol,
        {
          field: 'sector',
          headerName: 'Sector',
          width: 160,
          cellRenderer: (params: ICellRendererParams<InstrumentScreenerItem>) => {
            if (!params.value) return '\u2014';
            return <span className="sector-badge">{params.value}</span>;
          },
        },
        { field: 'country', headerName: 'Country', width: 100 },
        {
          field: 'marketCap',
          headerName: 'Market Cap',
          width: 120,
          type: 'rightAligned',
          valueFormatter: (p) => fmtMarketCap(p.value),
        },
        {
          field: 'pe',
          headerName: 'P/E',
          width: 80,
          type: 'rightAligned',
          valueFormatter: (p) => fmtDecimal(p.value, 1),
        },
        {
          field: 'eps',
          headerName: 'EPS',
          width: 90,
          type: 'rightAligned',
          valueFormatter: (p) => fmtDollar(p.value),
        },
        {
          field: 'dividendYield',
          headerName: 'Div Yield',
          width: 100,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
        },
        {
          headerName: '52wk Range',
          width: 140,
          type: 'rightAligned',
          valueGetter: (p) => {
            const low = p.data?.weekLow52;
            const high = p.data?.weekHigh52;
            if (low == null && high == null) return null;
            return `${fmtDollar(low)} \u2013 ${fmtDollar(high)}`;
          },
          valueFormatter: (p) => p.value ?? '\u2014',
        },
        {
          field: 'beta',
          headerName: 'Beta',
          width: 80,
          type: 'rightAligned',
          valueFormatter: (p) => fmtDecimal(p.value, 2),
        },
      ];

    case 'ETF':
      return [
        tickerCol,
        nameCol,
        { field: 'issuer', headerName: 'Issuer', width: 140, valueFormatter: (p) => p.value ?? '\u2014' },
        { field: 'assetClass', headerName: 'Asset Class', width: 120, valueFormatter: (p) => p.value ?? '\u2014' },
        {
          field: 'expenseRatio',
          headerName: 'Expense Ratio',
          width: 120,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
        },
        {
          field: 'yield',
          headerName: 'Yield',
          width: 90,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
        },
        {
          field: 'totalAssets',
          headerName: 'Total Assets',
          width: 120,
          type: 'rightAligned',
          valueFormatter: (p) => fmtAssets(p.value),
        },
        {
          field: 'holdingsCount',
          headerName: 'Holdings',
          width: 100,
          type: 'rightAligned',
          valueFormatter: (p) => p.value != null ? String(p.value) : '\u2014',
        },
        {
          field: 'return1Y',
          headerName: '1Y Return',
          width: 110,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
          cellClassRules: {
            'return-positive': (params) => params.value != null && params.value >= 0,
            'return-negative': (params) => params.value != null && params.value < 0,
          },
        },
      ];

    case 'MUTUAL_FUND':
      return [
        tickerCol,
        nameCol,
        { field: 'fundCategory', headerName: 'Category', width: 140, valueFormatter: (p) => p.value ?? '\u2014' },
        { field: 'fundStyle', headerName: 'Style', width: 120, valueFormatter: (p) => p.value ?? '\u2014' },
        {
          field: 'expenseRatio',
          headerName: 'Expense Ratio',
          width: 120,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
        },
        {
          field: 'yield',
          headerName: 'Yield',
          width: 90,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
        },
        {
          field: 'nav',
          headerName: 'NAV',
          width: 100,
          type: 'rightAligned',
          valueFormatter: (p) => fmtDollar(p.value),
        },
        {
          field: 'return1Y',
          headerName: '1Y Return',
          width: 110,
          type: 'rightAligned',
          valueFormatter: (p) => fmtPct(p.value),
          cellClassRules: {
            'return-positive': (params) => params.value != null && params.value >= 0,
            'return-negative': (params) => params.value != null && params.value < 0,
          },
        },
      ];

    default:
      // Sparse types: PREFERRED_STOCK, INDEX, BOND
      return [
        tickerCol,
        nameCol,
        { field: 'exchange', headerName: 'Exchange', width: 120, valueFormatter: (p) => p.value ?? '\u2014' },
        { field: 'currency', headerName: 'Currency', width: 100, valueFormatter: (p) => p.value ?? '\u2014' },
        { field: 'country', headerName: 'Country', width: 100, valueFormatter: (p) => p.value ?? '\u2014' },
      ];
  }
}

// ---------------------------------------------------------------------------
// Filter sections per type
// ---------------------------------------------------------------------------

interface FilterSectionProps {
  typeKey: string;
  draftFilter: ScreenerFilter;
  updateDraft: (field: keyof ScreenerFilter, value: string) => void;
}

function TypeFilters({ typeKey, draftFilter, updateDraft }: FilterSectionProps) {
  switch (typeKey) {
    case 'STOCK':
      return (
        <StockFilters draftFilter={draftFilter} updateDraft={updateDraft} />
      );
    case 'ETF':
      return (
        <EtfFilters draftFilter={draftFilter} updateDraft={updateDraft} />
      );
    case 'MUTUAL_FUND':
      return (
        <MutualFundFilters draftFilter={draftFilter} updateDraft={updateDraft} />
      );
    default:
      return (
        <SparseFilters typeKey={typeKey} draftFilter={draftFilter} updateDraft={updateDraft} />
      );
  }
}

function StockFilters({ draftFilter, updateDraft }: Omit<FilterSectionProps, 'typeKey'>) {
  const { data: sectors } = useReferenceValues('STOCK', 'sector');
  const { data: countries } = useReferenceValues('STOCK', 'country');

  return (
    <>
      <FilterSelect
        label="Sector"
        value={draftFilter.sector ?? ''}
        onChange={(v) => updateDraft('sector', v)}
        options={[
          { value: '', label: 'All Sectors' },
          ...(sectors?.map((s) => ({ value: s, label: s })) ?? []),
        ]}
      />
      <FilterSelect
        label="Country"
        value={draftFilter.country ?? ''}
        onChange={(v) => updateDraft('country', v)}
        options={[
          { value: '', label: 'All Countries' },
          ...(countries?.map((c) => ({ value: c, label: c })) ?? []),
        ]}
      />
      <FilterInput
        label="Ticker"
        value={draftFilter.tickerContains ?? ''}
        onChange={(v) => updateDraft('tickerContains', v)}
        placeholder="e.g. AAPL"
      />
      <FilterInput
        label="Name"
        value={draftFilter.nameContains ?? ''}
        onChange={(v) => updateDraft('nameContains', v)}
        placeholder="e.g. Apple"
      />
    </>
  );
}

function EtfFilters({ draftFilter, updateDraft }: Omit<FilterSectionProps, 'typeKey'>) {
  const { data: issuers } = useReferenceValues('ETF', 'issuer');
  const { data: assetClasses } = useReferenceValues('ETF', 'assetClass');

  return (
    <>
      <FilterSelect
        label="Issuer"
        value={draftFilter.issuer ?? ''}
        onChange={(v) => updateDraft('issuer', v)}
        options={[
          { value: '', label: 'All Issuers' },
          ...(issuers?.map((i) => ({ value: i, label: i })) ?? []),
        ]}
      />
      <FilterSelect
        label="Asset Class"
        value={draftFilter.assetClass ?? ''}
        onChange={(v) => updateDraft('assetClass', v)}
        options={[
          { value: '', label: 'All Asset Classes' },
          ...(assetClasses?.map((ac) => ({ value: ac, label: ac })) ?? []),
        ]}
      />
      <FilterInput
        label="Ticker"
        value={draftFilter.tickerContains ?? ''}
        onChange={(v) => updateDraft('tickerContains', v)}
        placeholder="e.g. SPY"
      />
      <FilterInput
        label="Name"
        value={draftFilter.nameContains ?? ''}
        onChange={(v) => updateDraft('nameContains', v)}
        placeholder="e.g. S&P 500"
      />
    </>
  );
}

function MutualFundFilters({ draftFilter, updateDraft }: Omit<FilterSectionProps, 'typeKey'>) {
  const { data: categories } = useReferenceValues('MUTUAL_FUND', 'fundCategory');
  const { data: styles } = useReferenceValues('MUTUAL_FUND', 'fundStyle');

  return (
    <>
      <FilterSelect
        label="Fund Category"
        value={draftFilter.fundCategory ?? ''}
        onChange={(v) => updateDraft('fundCategory', v)}
        options={[
          { value: '', label: 'All Categories' },
          ...(categories?.map((c) => ({ value: c, label: c })) ?? []),
        ]}
      />
      <FilterSelect
        label="Fund Style"
        value={draftFilter.fundStyle ?? ''}
        onChange={(v) => updateDraft('fundStyle', v)}
        options={[
          { value: '', label: 'All Styles' },
          ...(styles?.map((s) => ({ value: s, label: s })) ?? []),
        ]}
      />
      <FilterInput
        label="Ticker"
        value={draftFilter.tickerContains ?? ''}
        onChange={(v) => updateDraft('tickerContains', v)}
        placeholder="e.g. VFIAX"
      />
      <FilterInput
        label="Name"
        value={draftFilter.nameContains ?? ''}
        onChange={(v) => updateDraft('nameContains', v)}
        placeholder="e.g. Vanguard"
      />
    </>
  );
}

function SparseFilters({ typeKey, draftFilter, updateDraft }: FilterSectionProps) {
  const { data: exchanges } = useReferenceValues(typeKey, 'exchange');
  const { data: countries } = useReferenceValues(typeKey, 'country');

  return (
    <>
      <FilterSelect
        label="Exchange"
        value={draftFilter.exchange ?? ''}
        onChange={(v) => updateDraft('exchange', v)}
        options={[
          { value: '', label: 'All Exchanges' },
          ...(exchanges?.map((e) => ({ value: e, label: e })) ?? []),
        ]}
      />
      <FilterSelect
        label="Country"
        value={draftFilter.country ?? ''}
        onChange={(v) => updateDraft('country', v)}
        options={[
          { value: '', label: 'All Countries' },
          ...(countries?.map((c) => ({ value: c, label: c })) ?? []),
        ]}
      />
      <FilterInput
        label="Ticker"
        value={draftFilter.tickerContains ?? ''}
        onChange={(v) => updateDraft('tickerContains', v)}
        placeholder="e.g. AAPL"
      />
      <FilterInput
        label="Name"
        value={draftFilter.nameContains ?? ''}
        onChange={(v) => updateDraft('nameContains', v)}
        placeholder="Search by name"
      />
    </>
  );
}

// ---------------------------------------------------------------------------
// Filter label map
// ---------------------------------------------------------------------------

const FILTER_LABELS: Record<string, string> = {
  sector: 'Sector',
  country: 'Country',
  tickerContains: 'Ticker',
  nameContains: 'Name',
  issuer: 'Issuer',
  assetClass: 'Asset Class',
  fundCategory: 'Category',
  fundStyle: 'Style',
  exchange: 'Exchange',
};

// ---------------------------------------------------------------------------
// Main ScreenerPage component
// ---------------------------------------------------------------------------

export function ScreenerPage() {
  const { type: routeType = 'stocks' } = useParams<{ type: string }>();
  const navigate = useNavigate();

  const typeKey = routeToTypeKey(routeType) ?? 'STOCK';
  const typeConfig = INSTRUMENT_TYPE_CONFIG[typeKey];

  // Filter state
  const [draftFilter, setDraftFilter] = useState<ScreenerFilter>({});
  const [appliedFilter, setAppliedFilter] = useState<ScreenerFilter>({});

  // Pagination and sort state
  const [page, setPage] = useState(0);
  const [sortField, setSortField] = useState('ticker');
  const [sortDirection, setSortDirection] = useState('asc');

  // Data hooks
  const { data, isLoading } = useInstrumentScreener(
    typeKey,
    appliedFilter,
    page,
    PAGE_SIZE,
    sortField,
    sortDirection
  );
  const { data: counts } = useTypeCounts();

  const totalPages = data?.meta.totalPages ?? 0;
  const totalElements = data?.meta.totalElements ?? 0;

  // Reset filters and pagination when type changes
  const prevTypeKeyRef = useRef(typeKey);
  useEffect(() => {
    if (prevTypeKeyRef.current !== typeKey) {
      prevTypeKeyRef.current = typeKey;
      setDraftFilter({});
      setAppliedFilter({});
      setPage(0);
      setSortField('ticker');
      setSortDirection('asc');
    }
  }, [typeKey]);

  const handleApply = useCallback(() => {
    setAppliedFilter(draftFilter);
    setPage(0);
  }, [draftFilter]);

  const handleReset = useCallback(() => {
    setDraftFilter({});
    setAppliedFilter({});
    setPage(0);
  }, []);

  const updateDraft = useCallback((field: keyof ScreenerFilter, value: string) => {
    setDraftFilter((prev) => ({ ...prev, [field]: value || undefined }));
  }, []);

  // Active filters for chip display
  const activeFiltersList = useMemo(() => {
    return Object.entries(appliedFilter)
      .filter(([, v]) => v)
      .map(([key, value]) => ({
        key,
        label: FILTER_LABELS[key] || key,
        value: value!,
      }));
  }, [appliedFilter]);

  const handleRemoveFilter = useCallback((key: string) => {
    const newDraft = { ...draftFilter };
    delete newDraft[key as keyof ScreenerFilter];
    setDraftFilter(newDraft);
    const newApplied = { ...appliedFilter };
    delete newApplied[key as keyof ScreenerFilter];
    setAppliedFilter(newApplied);
    setPage(0);
  }, [draftFilter, appliedFilter]);

  const columnDefs = useMemo<ColDef<InstrumentScreenerItem>[]>(
    () => getColumnDefs(typeKey, navigate),
    [typeKey, navigate]
  );

  const handleRowClicked = useCallback(
    (event: { data: InstrumentScreenerItem | undefined }) => {
      if (event.data) {
        navigate(`/instruments/${typeConfig.detailRoute}/${event.data.ticker}`);
      }
    },
    [navigate, typeConfig.detailRoute]
  );

  return (
    <div className="screener-page">
      {/* Header */}
      <div className="screener-header">
        <div>
          <h1 className="screener-title">{typeConfig.pluralLabel}</h1>
          <p className="screener-title-sub">
            {counts?.[typeKey] != null
              ? `${counts[typeKey].toLocaleString()} instruments available`
              : '\u2014'}
          </p>
        </div>
        <div className="screener-search-wrapper">
          <Search size={16} className="screener-search-icon" />
          <input
            type="text"
            className="screener-search-input"
            placeholder="Quick search by ticker or name..."
            value={draftFilter.tickerContains ?? ''}
            onChange={(e) => updateDraft('tickerContains', e.target.value)}
            onKeyDown={(e) => {
              if (e.key === 'Enter') handleApply();
            }}
          />
        </div>
      </div>

      {/* Filters */}
      <ScreenerFilters
        onApply={handleApply}
        onReset={handleReset}
        activeFilters={activeFiltersList}
        onRemoveFilter={handleRemoveFilter}
      >
        <TypeFilters
          typeKey={typeKey}
          draftFilter={draftFilter}
          updateDraft={updateDraft}
        />
      </ScreenerFilters>

      {/* Results header */}
      <div className="screener-results-header">
        <span className="screener-results-count">
          {isLoading ? (
            <Skeleton style={{ width: 120, height: 16, display: 'inline-block' }} />
          ) : (
            <>{totalElements.toLocaleString()} {typeConfig.pluralLabel.toLowerCase()}</>
          )}
        </span>
        {!isLoading && totalPages > 1 && (
          <span className="screener-results-showing">
            Showing {page * PAGE_SIZE + 1}&ndash;
            {Math.min((page + 1) * PAGE_SIZE, totalElements)}
          </span>
        )}
      </div>

      {/* Grid */}
      {isLoading ? (
        <div className="screener-grid-skeleton">
          <Skeleton style={{ width: '100%', height: 40, borderRadius: 0 }} />
          {Array.from({ length: 8 }).map((_, i) => (
            <Skeleton key={i} style={{ width: '100%', height: 40, borderRadius: 0 }} />
          ))}
        </div>
      ) : (
        <div className="screener-grid-card">
          <div className="ag-theme-quartz screener-grid-container">
            <AgGridReact
              rowData={data?.data ?? []}
              columnDefs={columnDefs}
              defaultColDef={{ sortable: true, resizable: true }}
              domLayout="autoHeight"
              animateRows={true}
              suppressCellFocus={true}
              onRowClicked={handleRowClicked}
              rowStyle={{ cursor: 'pointer' }}
            />
          </div>
        </div>
      )}

      {/* Pagination */}
      {!isLoading && totalPages > 1 && (
        <div className="screener-pagination">
          <span className="pagination-info">
            Showing {page * PAGE_SIZE + 1}&ndash;
            {Math.min((page + 1) * PAGE_SIZE, totalElements)} of{' '}
            {totalElements.toLocaleString()} {typeConfig.pluralLabel.toLowerCase()}
          </span>
          <Pagination
            currentPage={page}
            totalPages={totalPages}
            onPageChange={setPage}
          />
        </div>
      )}
    </div>
  );
}
