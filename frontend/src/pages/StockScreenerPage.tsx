import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';

interface StockRow {
  id: number;
  ticker: string;
  name: string;
  currency: string;
  country: string;
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

  return (
    <div style={{ padding: '1.5rem', maxWidth: '1280px', margin: '0 auto' }}>
      <h1 style={{ fontSize: '1.5rem', fontWeight: 700, marginBottom: '0.25rem' }}>Stocks</h1>
      <p style={{ color: 'var(--color-text-muted)', marginBottom: '1.5rem', fontSize: '0.875rem' }}>
        {data ? `${data.meta.totalElements.toLocaleString()} stocks` : '—'}
      </p>

      {isLoading ? (
        <div style={{ color: 'var(--color-text-muted)' }}>Loading…</div>
      ) : (
        <>
          <div style={{ overflowX: 'auto' }}>
            <table style={{ width: '100%', borderCollapse: 'collapse', fontSize: '0.875rem' }}>
              <thead>
                <tr style={{ borderBottom: '2px solid var(--color-border)', color: 'var(--color-text-muted)', textAlign: 'left' }}>
                  <th style={{ padding: '0.625rem 0.75rem' }}>Ticker</th>
                  <th style={{ padding: '0.625rem 0.75rem' }}>Name</th>
                  <th style={{ padding: '0.625rem 0.75rem' }}>Country</th>
                  <th style={{ padding: '0.625rem 0.75rem' }}>Currency</th>
                  <th style={{ padding: '0.625rem 0.75rem' }}>Status</th>
                </tr>
              </thead>
              <tbody>
                {(data?.data ?? []).map((stock) => (
                  <tr
                    key={stock.id}
                    style={{ borderBottom: '1px solid var(--color-border)', cursor: 'pointer' }}
                    onClick={() => navigate(`/stocks/${stock.ticker}`)}
                    onMouseEnter={(e) => (e.currentTarget.style.background = 'var(--color-surface-secondary)')}
                    onMouseLeave={(e) => (e.currentTarget.style.background = '')}
                  >
                    <td style={{ padding: '0.625rem 0.75rem', fontWeight: 600, fontFamily: 'monospace' }}>{stock.ticker}</td>
                    <td style={{ padding: '0.625rem 0.75rem' }}>{stock.name}</td>
                    <td style={{ padding: '0.625rem 0.75rem' }}>{stock.country}</td>
                    <td style={{ padding: '0.625rem 0.75rem' }}>{stock.currency}</td>
                    <td style={{ padding: '0.625rem 0.75rem' }}>{stock.status}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>

          {totalPages > 1 && (
            <div style={{ display: 'flex', gap: '0.5rem', alignItems: 'center', marginTop: '1rem', justifyContent: 'flex-end' }}>
              <button
                onClick={() => setPage((p) => Math.max(0, p - 1))}
                disabled={page === 0}
                style={{ padding: '0.375rem 0.75rem', borderRadius: '0.375rem', border: '1px solid var(--color-border)', background: 'var(--color-surface)', cursor: page === 0 ? 'not-allowed' : 'pointer' }}
              >
                Prev
              </button>
              <span style={{ fontSize: '0.8125rem', color: 'var(--color-text-muted)' }}>
                Page {page + 1} of {totalPages}
              </span>
              <button
                onClick={() => setPage((p) => Math.min(totalPages - 1, p + 1))}
                disabled={page >= totalPages - 1}
                style={{ padding: '0.375rem 0.75rem', borderRadius: '0.375rem', border: '1px solid var(--color-border)', background: 'var(--color-surface)', cursor: page >= totalPages - 1 ? 'not-allowed' : 'pointer' }}
              >
                Next
              </button>
            </div>
          )}
        </>
      )}
    </div>
  );
}
