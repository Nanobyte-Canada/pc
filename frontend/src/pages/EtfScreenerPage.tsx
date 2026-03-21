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

interface EtfRow {
  id: number;
  symbol: string;
  name: string;
  isin: string | null;
  cusip: string | null;
  issuer: string | null;
  currency: string;
  domicile: string;
  inceptionDate: string | null;
  assetClass: string | null;
  status: string;
}

interface PagedResponse {
  data: EtfRow[];
  meta: { totalElements: number; totalPages: number; page: number; size: number };
}

const PAGE_SIZE = 50;

async function fetchEtfs(page: number): Promise<PagedResponse> {
  const res = await apiFetch(`/api/v1/etfs?page=${page}&size=${PAGE_SIZE}&sort=symbol:asc`);
  if (!res.ok) throw new Error('Failed to fetch ETFs');
  return res.json();
}

export function EtfScreenerPage() {
  const navigate = useNavigate();
  const [page, setPage] = useState(0);

  const { data, isLoading } = useQuery({
    queryKey: ['etfs-list', page],
    queryFn: () => fetchEtfs(page),
  });

  const totalPages = data?.meta.totalPages ?? 0;
  const totalElements = data?.meta.totalElements ?? 0;

  const columnDefs = useMemo<ColDef<EtfRow>[]>(() => [
    { field: 'symbol', headerName: 'Symbol', width: 100, pinned: 'left', cellStyle: { fontWeight: 600, fontFamily: 'monospace' } },
    { field: 'name', headerName: 'Name', flex: 1, minWidth: 200 },
    { field: 'issuer', headerName: 'Issuer', width: 140, valueFormatter: p => p.value ?? '—' },
    { field: 'assetClass', headerName: 'Asset Class', width: 120, valueFormatter: p => p.value ?? '—' },
    { field: 'currency', headerName: 'Currency', width: 90 },
    { field: 'domicile', headerName: 'Domicile', width: 90 },
  ], []);

  return (
    <div className="screener-page">
      <h1>ETFs</h1>
      <p className="screener-subtitle">
        {data ? `${totalElements.toLocaleString()} ETFs` : '—'}
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
                if (event.data) navigate(`/etfs/${event.data.symbol}`);
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
