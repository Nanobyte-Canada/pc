import { useParams, useNavigate } from 'react-router-dom';
import { useQuery } from '@tanstack/react-query';
import { apiFetch } from '../services/api';

interface EtfDetail {
  id: number;
  symbol: string;
  name: string;
  issuer: string | null;
  assetClass: string | null;
  inceptionDate: string | null;
  etfcomEnrichmentStatus: string;
  etfcomRawPayload: string | null;
}

async function fetchEtf(symbol: string): Promise<EtfDetail> {
  const res = await apiFetch(`/api/v1/etfs/symbol/${encodeURIComponent(symbol)}`);
  if (!res.ok) throw new Error('ETF not found');
  return res.json();
}

function RawPayloadSection({ payload }: { payload: string }) {
  let parsed: Record<string, unknown>;
  try {
    parsed = JSON.parse(payload);
  } catch {
    return <pre style={{ fontSize: '0.8rem', overflowX: 'auto' }}>{payload}</pre>;
  }

  return (
    <div style={{ display: 'flex', flexDirection: 'column', gap: '0' }}>
      {Object.entries(parsed).map(([sectionKey, sectionValue]) => {
        if (typeof sectionValue === 'object' && sectionValue !== null) {
          const entries = Object.entries(sectionValue as Record<string, unknown>);
          return (
            <details key={sectionKey} style={{ marginBottom: '1rem' }} open>
              <summary style={{ fontWeight: 600, fontSize: '0.9375rem', cursor: 'pointer', padding: '0.5rem 0', borderBottom: '2px solid var(--color-border)' }}>
                {sectionKey}
              </summary>
              <div style={{ paddingTop: '0.5rem' }}>
                {entries.map(([key, value]) => (
                  <div key={key} style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '0.5rem', padding: '0.375rem 0', borderBottom: '1px solid var(--color-border)' }}>
                    <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 500 }}>{key}</span>
                    <span style={{ fontSize: '0.875rem', wordBreak: 'break-word' }}>
                      {value === null || value === undefined || value === '' ? '—' : String(value)}
                    </span>
                  </div>
                ))}
              </div>
            </details>
          );
        }
        return (
          <div key={sectionKey} style={{ display: 'grid', gridTemplateColumns: '220px 1fr', gap: '0.5rem', padding: '0.375rem 0', borderBottom: '1px solid var(--color-border)' }}>
            <span style={{ fontSize: '0.8rem', color: 'var(--color-text-muted)', fontWeight: 500 }}>{sectionKey}</span>
            <span style={{ fontSize: '0.875rem', wordBreak: 'break-word' }}>
              {sectionValue === null || sectionValue === undefined || sectionValue === '' ? '—' : String(sectionValue)}
            </span>
          </div>
        );
      })}
    </div>
  );
}

export function EtfDetailPage() {
  const { symbol } = useParams<{ symbol: string }>();
  const navigate = useNavigate();

  const { data, isLoading, isError } = useQuery({
    queryKey: ['etf-detail', symbol],
    queryFn: () => fetchEtf(symbol!),
    enabled: !!symbol,
  });

  if (isLoading) {
    return <div style={{ padding: '2rem', color: 'var(--color-text-muted)' }}>Loading…</div>;
  }

  if (isError || !data) {
    return <div style={{ padding: '2rem', color: 'var(--color-destructive)' }}>ETF not found.</div>;
  }

  return (
    <div style={{ padding: '1.5rem', maxWidth: '960px', margin: '0 auto' }}>
      <button
        onClick={() => navigate('/etfs')}
        style={{ fontSize: '0.8125rem', color: 'var(--color-text-muted)', background: 'none', border: 'none', cursor: 'pointer', marginBottom: '1rem', padding: 0 }}
      >
        ← Back to ETFs
      </button>

      <div style={{ marginBottom: '1.5rem' }}>
        <h1 style={{ fontSize: '1.5rem', fontWeight: 700, margin: 0 }}>{data.symbol}</h1>
        <p style={{ color: 'var(--color-text-muted)', margin: '0.25rem 0 0' }}>{data.name}</p>
      </div>

      {/* Basic Info */}
      <section style={{ marginBottom: '2rem' }}>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>Basic Info</h2>
        <div style={{ display: 'grid', gridTemplateColumns: 'repeat(3, 1fr)', gap: '0.75rem' }}>
          {[
            { label: 'Issuer', value: data.issuer ?? '—' },
            { label: 'Asset Class', value: data.assetClass ?? '—' },
            { label: 'Inception Date', value: data.inceptionDate ?? '—' },
            { label: 'Enrichment Status', value: data.etfcomEnrichmentStatus },
          ].map(({ label, value }) => (
            <div key={label} style={{ background: 'var(--color-surface-secondary)', borderRadius: '0.5rem', padding: '0.75rem' }}>
              <div style={{ fontSize: '0.75rem', color: 'var(--color-text-muted)' }}>{label}</div>
              <div style={{ fontWeight: 600, marginTop: '0.25rem' }}>{value}</div>
            </div>
          ))}
        </div>
      </section>

      {/* Raw Payload */}
      <section>
        <h2 style={{ fontSize: '1rem', fontWeight: 600, marginBottom: '0.75rem' }}>etf.com Data</h2>
        {data.etfcomRawPayload ? (
          <RawPayloadSection payload={data.etfcomRawPayload} />
        ) : (
          <p style={{ color: 'var(--color-text-muted)' }}>No enrichment data available yet.</p>
        )}
      </section>
    </div>
  );
}
