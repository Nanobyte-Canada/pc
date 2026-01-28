import { useState, useCallback } from 'react';
import { ColDef } from 'ag-grid-community';
import { ScreenerGrid } from '../components/screener/ScreenerGrid';
import { ScreenerFilters, FilterInput, FilterSelect } from '../components/screener/ScreenerFilters';
import { useMutualFundScreener } from '../hooks/useScreener';
import { MutualFund } from '../types/instrument';
import { MutualFundFilter } from '../services/instrumentService';
import './ScreenerPage.css';

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'CLOSED', label: 'Closed' },
  { value: 'SUSPENDED', label: 'Suspended' },
];

const FUND_TYPE_OPTIONS = [
  { value: '', label: 'All Fund Types' },
  { value: 'Index', label: 'Index' },
  { value: 'Active', label: 'Active' },
  { value: 'Target Date', label: 'Target Date' },
  { value: 'Money Market', label: 'Money Market' },
];

const ASSET_CLASS_OPTIONS = [
  { value: '', label: 'All Asset Classes' },
  { value: 'Equity', label: 'Equity' },
  { value: 'Fixed Income', label: 'Fixed Income' },
  { value: 'Balanced', label: 'Balanced' },
  { value: 'Money Market', label: 'Money Market' },
];

const columnDefs: ColDef<MutualFund>[] = [
  { field: 'symbol', headerName: 'Symbol', width: 100 },
  { field: 'name', headerName: 'Fund Name', flex: 1, minWidth: 200 },
  { field: 'issuer', headerName: 'Issuer', width: 150 },
  { field: 'fundType', headerName: 'Fund Type', width: 120 },
  { field: 'assetClass', headerName: 'Asset Class', width: 120 },
  {
    field: 'expenseRatio',
    headerName: 'Expense Ratio',
    width: 120,
    valueFormatter: (params) => params.value ? `${(params.value * 100).toFixed(2)}%` : '-'
  },
  {
    field: 'minimumInvestment',
    headerName: 'Min Investment',
    width: 130,
    valueFormatter: (params) => params.value ? `$${params.value.toLocaleString()}` : '-'
  },
  { field: 'status', headerName: 'Status', width: 100 },
];

export function MutualFundScreenerPage() {
  const [appliedFilter, setAppliedFilter] = useState<MutualFundFilter>({});
  const [draftFilter, setDraftFilter] = useState<MutualFundFilter>({});

  const { data, isLoading } = useMutualFundScreener(appliedFilter);

  const handleApply = useCallback(() => {
    setAppliedFilter({ ...draftFilter });
  }, [draftFilter]);

  const handleReset = useCallback(() => {
    setDraftFilter({});
    setAppliedFilter({});
  }, []);

  return (
    <div className="screener-page">
      <h1 className="page-title">Mutual Fund Screener</h1>
      <p className="page-subtitle">Filter and search mutual funds by issuer, fund type, asset class, and more</p>

      <section className="filters-section">
        <h2>Filters</h2>
        <ScreenerFilters onApply={handleApply} onReset={handleReset}>
          <FilterInput
            label="Symbol Contains"
            value={draftFilter.symbolContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, symbolContains: v || undefined })}
            placeholder="e.g. VFIAX"
          />
          <FilterInput
            label="Name Contains"
            value={draftFilter.nameContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, nameContains: v || undefined })}
            placeholder="e.g. Growth"
          />
          <FilterInput
            label="Issuer"
            value={draftFilter.issuer || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, issuer: v || undefined })}
            placeholder="e.g. Fidelity"
          />
          <FilterSelect
            label="Fund Type"
            value={draftFilter.fundType || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, fundType: v || undefined })}
            options={FUND_TYPE_OPTIONS}
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
          {data && <span className="results-count">{data.meta.totalElements} mutual funds found</span>}
        </div>
        <ScreenerGrid
          rowData={data?.data || []}
          columnDefs={columnDefs}
          loading={isLoading}
          instrumentType="MUTUAL_FUND"
          getRowId={(d) => d.id}
          getTicker={(d) => d.symbol}
          getName={(d) => d.name}
        />
      </section>
    </div>
  );
}
