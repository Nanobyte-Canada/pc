import { useState, useCallback } from 'react';
import { ColDef } from 'ag-grid-community';
import { ScreenerGrid } from '../components/screener/ScreenerGrid';
import { ScreenerFilters, FilterInput, FilterSelect } from '../components/screener/ScreenerFilters';
import { useStockScreener } from '../hooks/useScreener';
import { Stock } from '../types/instrument';
import { StockFilter } from '../services/instrumentService';
import './ScreenerPage.css';

const STATUS_OPTIONS = [
  { value: '', label: 'All Statuses' },
  { value: 'ACTIVE', label: 'Active' },
  { value: 'DELISTED', label: 'Delisted' },
  { value: 'SUSPENDED', label: 'Suspended' },
];

const COUNTRY_OPTIONS = [
  { value: '', label: 'All Countries' },
  { value: 'USA', label: 'United States' },
  { value: 'CAN', label: 'Canada' },
  { value: 'GBR', label: 'United Kingdom' },
  { value: 'DEU', label: 'Germany' },
  { value: 'JPN', label: 'Japan' },
  { value: 'CHN', label: 'China' },
];

const EXCHANGE_OPTIONS = [
  { value: '', label: 'All Exchanges' },
  { value: 'NYSE', label: 'NYSE' },
  { value: 'NASDAQ', label: 'NASDAQ' },
  { value: 'AMEX', label: 'AMEX' },
];

const columnDefs: ColDef<Stock>[] = [
  { field: 'ticker', headerName: 'Ticker', width: 100 },
  { field: 'name', headerName: 'Company Name', flex: 1, minWidth: 200 },
  { field: 'exchange', headerName: 'Exchange', width: 100 },
  {
    field: 'sector',
    headerName: 'Sector',
    width: 150,
    valueGetter: (params) => params.data?.sector?.name || '-'
  },
  { field: 'country', headerName: 'Country', width: 80 },
  { field: 'status', headerName: 'Status', width: 100 },
];

export function StockScreenerPage() {
  const [appliedFilter, setAppliedFilter] = useState<StockFilter>({});
  const [draftFilter, setDraftFilter] = useState<StockFilter>({});

  const { data, isLoading } = useStockScreener(appliedFilter);

  const handleApply = useCallback(() => {
    setAppliedFilter({ ...draftFilter });
  }, [draftFilter]);

  const handleReset = useCallback(() => {
    setDraftFilter({});
    setAppliedFilter({});
  }, []);

  return (
    <div className="screener-page">
      <h1 className="page-title">Stock Screener</h1>
      <p className="page-subtitle">Filter and search stocks by sector, country, exchange, and more</p>

      <section className="filters-section">
        <h2>Filters</h2>
        <ScreenerFilters onApply={handleApply} onReset={handleReset}>
          <FilterInput
            label="Ticker Contains"
            value={draftFilter.tickerContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, tickerContains: v || undefined })}
            placeholder="e.g. AAP"
          />
          <FilterInput
            label="Name Contains"
            value={draftFilter.nameContains || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, nameContains: v || undefined })}
            placeholder="e.g. Apple"
          />
          <FilterSelect
            label="Country"
            value={draftFilter.country || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, country: v || undefined })}
            options={COUNTRY_OPTIONS}
          />
          <FilterSelect
            label="Exchange"
            value={draftFilter.exchange || ''}
            onChange={(v) => setDraftFilter({ ...draftFilter, exchange: v || undefined })}
            options={EXCHANGE_OPTIONS}
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
          {data && <span className="results-count">{data.meta.totalElements} stocks found</span>}
        </div>
        <ScreenerGrid
          rowData={data?.data || []}
          columnDefs={columnDefs}
          loading={isLoading}
          instrumentType="STOCK"
          getRowId={(d) => d.id}
          getTicker={(d) => d.ticker}
          getName={(d) => d.name}
        />
      </section>
    </div>
  );
}
