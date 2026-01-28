import { useState, useCallback } from 'react';
import { ColDef } from 'ag-grid-community';
import { ScreenerGrid } from '../components/screener/ScreenerGrid';
import { ScreenerFilters, FilterInput, FilterSelect } from '../components/screener/ScreenerFilters';
import { useEtfScreener } from '../hooks/useScreener';
import { Etf } from '../types/instrument';
import { EtfFilter } from '../services/instrumentService';
import './ScreenerPage.css';

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'DELISTED', label: 'Delisted' },
  { value: 'SUSPENDED', label: 'Suspended' },
];

const ASSET_CLASS_OPTIONS = [
  { value: '', label: 'All Asset Classes' },
  { value: 'Equity', label: 'Equity' },
  { value: 'Fixed Income', label: 'Fixed Income' },
  { value: 'Commodity', label: 'Commodity' },
  { value: 'Multi-Asset', label: 'Multi-Asset' },
  { value: 'Alternative', label: 'Alternative' },
];

const columnDefs: ColDef<Etf>[] = [
  { field: 'symbol', headerName: 'Symbol', width: 100 },
  { field: 'name', headerName: 'ETF Name', flex: 1, minWidth: 200 },
  { field: 'exchange', headerName: 'Exchange', width: 100 },
  { field: 'issuer', headerName: 'Issuer', width: 150 },
  { field: 'assetClass', headerName: 'Asset Class', width: 120 },
  {
    field: 'expenseRatio',
    headerName: 'Expense Ratio',
    width: 120,
    valueFormatter: (params) => params.value ? `${(params.value * 100).toFixed(2)}%` : '-'
  },
  { field: 'status', headerName: 'Status', width: 100 },
];

export function EtfScreenerPage() {
  const [appliedFilter, setAppliedFilter] = useState<EtfFilter>({});
  const [draftFilter, setDraftFilter] = useState<EtfFilter>({});

  const { data, isLoading } = useEtfScreener(appliedFilter);

  const handleApply = useCallback(() => {
    setAppliedFilter({ ...draftFilter });
  }, [draftFilter]);

  const handleReset = useCallback(() => {
    setDraftFilter({});
    setAppliedFilter({});
  }, []);

  return (
    <div className="screener-page">
      <h1 className="page-title">ETF Screener</h1>
      <p className="page-subtitle">Filter and search ETFs by issuer, asset class, expense ratio, and more</p>

      <section className="filters-section">
        <h2>Filters</h2>
        <ScreenerFilters onApply={handleApply} onReset={handleReset}>
          <FilterInput
            label="Symbol Contains"
            value={draftFilter.symbolContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, symbolContains: v || undefined })}
            placeholder="e.g. SPY"
          />
          <FilterInput
            label="Name Contains"
            value={draftFilter.nameContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, nameContains: v || undefined })}
            placeholder="e.g. S&P 500"
          />
          <FilterInput
            label="Issuer"
            value={draftFilter.issuer || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, issuer: v || undefined })}
            placeholder="e.g. Vanguard"
          />
          <FilterSelect
            label="Asset Class"
            value={draftFilter.assetClass || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, assetClass: v || undefined })}
            options={ASSET_CLASS_OPTIONS}
          />
          <FilterSelect
            label="Status"
            value={draftFilter.status || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, status: v || undefined })}
            options={STATUS_OPTIONS}
          />
        </ScreenerFilters>
      </section>

      <section className="results-section">
        <div className="results-header">
          <h2>Results</h2>
          {data && <span className="results-count">{data.meta.totalElements} ETFs found</span>}
        </div>
        <ScreenerGrid
          rowData={data?.data || []}
          columnDefs={columnDefs}
          loading={isLoading}
          instrumentType="ETF"
          getRowId={(d) => d.id}
          getTicker={(d) => d.symbol}
          getName={(d) => d.name}
        />
      </section>
    </div>
  );
}
