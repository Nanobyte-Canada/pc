import { useState, useMemo } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { AgGridReact } from 'ag-grid-react';
import type { ColDef } from 'ag-grid-community';
import 'ag-grid-community/styles/ag-grid.css';
import 'ag-grid-community/styles/ag-theme-quartz.css';
import { apiFetch } from '../services/api';
import { Pagination } from '../components/ui/Pagination';
import './ScreenerPage.css';

interface StockRow {
  id: number;
  ticker: string;
  exchange: string | null;
  name: string;
  isin: string | null;
  cusip: string | null;
  sedol: string | null;
  currency: string;
  country: string;
  sector: { code: string; name: string } | null;
  status: string;
}

interface PagedResponse {
  data: StockRow[];
  meta: { totalElements: number; totalPages: number; page: number; size: number };
}

const PAGE_SIZE = 50;

async function fetchStocks(page: number): Promise<PagedResponse> {
  const res = await apiFetch(`/api/v1/stocks?page=${page}&size=${PAGE_SIZE}&sort=ticker:asc`);
  if (!res.ok) throw new Error('Failed to fetch stocks');
  return res.json();
}

export function StockScreenerPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['stocks-list', page],
    queryFn: () => fetchStocks(page),
  });

  const totalPages = data?.meta.totalPages ?? 0;
  const totalElements = data?.meta.totalElements ?? 0;

  const columnDefs = useMemo<ColDef<StockRow>[]>(() => [
    { field: 'ticker', headerName: 'Ticker', width: 100, pinned: 'left', cellStyle: { fontWeight: 600, fontFamily: 'monospace' } },
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 200 },
    { field: 'country', headerName: 'Country', width: 90 },
    { field: 'currency', headerName: 'Currency', width: 90 },
    { field: 'sector', headerName: 'Sector', width: 160, valueGetter: p => p.data?.sector?.name ?? '—' },
  ], []);

  return (
    <div className="screener-page">
      <h1>Stocks</h1>
      <p className="screener-subtitle">
        {data ? `${totalElements.toLocaleString()} stocks` : '—'}
      </p>

      {isLoading ? (
        <div style={{ color: 'var(--text-muted)' }}>Loading…</div>
      ) : (
        <>
          <div className="ag-theme-quartz screener-grid-container">
            <AgGridReact
              rowData={data?.data ?? []}
              columnDefs={columnDefs}
              defaultColDef={{ sortable: true, resizable: true, filter: true, floatingFilter: true }}
              domLayout="autoHeight"
              animateRows={true}
              suppressCellFocus={true}
              onRowClicked={(event) => {
                if (event.data) navigate(`/stocks/${event.data.ticker}`);
              }}
              rowStyle={{ cursor: 'pointer' }}
            />
          </div>

          {totalPages > 1 && (
            <div className="screener-pagination">
              <span className="pagination-info">
                Showing {page * PAGE_SIZE + 1}–{Math.min((page + 1) * PAGE_SIZE, totalElements)} of {totalElements.toLocaleString()}
              </span>
              <Pagination currentPage={page} totalPages={totalPages} onPageChange={setPage} />
            </div>
          )}
        </>
      )}
    </div>
  );
}
