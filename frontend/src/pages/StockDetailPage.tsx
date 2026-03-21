import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';

interface StockField {
  key: string;
  label: string;
  value: string;
}

interface StockSection {
  title: string;
  fields: StockField[];
}

interface StockDetail {
  id: number;
  ticker: string;
  name: string;
  currency: string;
  country: string;
  isin: string | null;
  avIngestionStatus: string;
  sections: StockSection[] | null;
}

async function fetchStock(ticker: string): Promise<StockDetail> {
  const res = await apiFetch(`/api/v1/stocks/ticker/${encodeURIComponent(ticker)}`);
  if (!res.ok) throw new Error('Stock not found');
  return res.json();
}

export function StockDetailPage() {
  const { ticker } = useParams<{ ticker: string }>();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['stock-detail', ticker],
    queryFn: () => fetchStock(ticker!),
    enabled: !!ticker,
  });

  if (isLoading) {
    return <div style={{ padding: '2rem', color: 'var(--text-muted)' }}>Loading…</div>;
  }

  if (isError || !data) {
    return <div style={{ padding: '2rem', color: 'var(--destructive)' }}>Stock not found.</div>;
  }

  return (
    <div style={{ padding: '1.5rem' }}>
      <button
        onClick={() => navigate('/screener/stocks')}
        style={{ fontSize: '0.8125rem', color: 'var(--text-muted)', background: 'none', border: 'none', cursor: 'pointer', marginBottom: '1rem', padding: 0 }}
      >
        ← Back to Stocks
      </button>

      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>{data.ticker}</h1>
        <p style={{ color: 'var(--text-muted)', margin: '0.25rem 0 0' }}>{data.name}</p>
      </div>

      {/* Basic Info */}
      <section style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Basic Info</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.75rem' }}>
          {[
            { label: 'Country', value: data.country },
            { label: 'Currency', value: data.currency },
            { label: 'ISIN', value: data.isin ?? '—' },
            { label: 'Enrichment Status', value: data.avIngestionStatus },
          ].map(({ label, value }) => (
            <div key={label} style={{ background: 'var(--secondary-bg)', borderRadius: '0.5rem', padding: '0.75rem' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--text-muted)' }}>{label}</div>
              <div style={{ fontWeight: 600, marginTop: '0.25rem' }}>{value}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Data Sections */}
      <section>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Alpha Vantage Data</h2>
        {data.sections && data.sections.length > 0 ? (
          <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
            {data.sections.map(section => (
              <details key={section.title} style={{ marginBottom: '1rem' }} open>
                <summary style={{ fontWeight: 600, fontSize: '0.9375rem', cursor: 'pointer', padding: '0.5rem 0', borderBottom: '2px solid var(--border)' }}>
                  {section.title}
                </summary>
                <div style={{ paddingTop: '0.5rem' }}>
                  {section.fields.map(({ key, label, value }) => {
                    if (key === 'Description') {
                      return (
                        <div key={key} style={{ padding: '0.5rem 0', borderBottom: '1px solid var(--border)' }}>
                          <div style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 500, marginBottom: '0.375rem' }}>{label}</div>
                          <p style={{ fontSize: '0.875rem', lineHeight: 1.6, margin: 0 }}>{value}</p>
                        </div>
                      );
                    }
                    return (
                      <div key={key} style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '0.5rem', padding: '0.375rem 0', borderBottom: '1px solid var(--border)' }}>
                        <span style={{ fontSize: '0.8rem', color: 'var(--text-muted)', fontWeight: 500 }}>{label}</span>
                        <span style={{ fontSize: '0.875rem', wordBreak: 'break-word' }}>{value}</span>
                      </div>
                    );
                  })}
                </div>
              </details>
            ))}
          </div>
        ) : (
          <p style={{ color: 'var(--text-muted)' }}>No enrichment data available yet.</p>
        )}
      </section>
    </div>
  );
}
