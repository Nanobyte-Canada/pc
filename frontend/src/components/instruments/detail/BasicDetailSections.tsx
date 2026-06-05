import type { InstrumentDetail } from '@/types/screener';

interface Props {
  data: InstrumentDetail;
}

export function BasicDetailSections({ data }: Props) {
  return (
    <div>
      {/* Overview Section */}
      <div id="section-overview">
        <h2 className="detail-section-title">Overview</h2>
        {data.general ? (
          <div className="detail-section-content">
            <div className="detail-info-grid">
              {data.general.Description && (
                <div className="detail-info-full">
                  <span className="detail-info-label">Description</span>
                  <p className="detail-info-value">{data.general.Description}</p>
                </div>
              )}
              {data.general.Officers && data.general.Officers.length > 0 && (
                <div className="detail-info-full">
                  <span className="detail-info-label">Officers</span>
                  <ul className="detail-officers-list">
                    {data.general.Officers.map((officer: { Name?: string; Title?: string }, idx: number) => (
                      <li key={idx}>
                        <strong>{officer.Name}</strong> - {officer.Title}
                      </li>
                    ))}
                  </ul>
                </div>
              )}
              {data.general.ISIN && (
                <div className="detail-info-item">
                  <span className="detail-info-label">ISIN</span>
                  <span className="detail-info-value">{data.general.ISIN}</span>
                </div>
              )}
              {data.general.CUSIP && (
                <div className="detail-info-item">
                  <span className="detail-info-label">CUSIP</span>
                  <span className="detail-info-value">{data.general.CUSIP}</span>
                </div>
              )}
              {data.general.Sector && (
                <div className="detail-info-item">
                  <span className="detail-info-label">Sector</span>
                  <span className="detail-info-value">{data.general.Sector}</span>
                </div>
              )}
              {data.general.Industry && (
                <div className="detail-info-item">
                  <span className="detail-info-label">Industry</span>
                  <span className="detail-info-value">{data.general.Industry}</span>
                </div>
              )}
              {data.general.Country && (
                <div className="detail-info-item">
                  <span className="detail-info-label">Country</span>
                  <span className="detail-info-value">{data.general.Country}</span>
                </div>
              )}
            </div>
          </div>
        ) : (
          <div className="detail-section-placeholder">
            No detailed data available for this instrument.
          </div>
        )}
      </div>

      {/* Technicals Section */}
      {data.technicals && (
        <div id="section-technicals">
          <h2 className="detail-section-title">Technicals</h2>
          <div className="detail-section-content">
            <div className="detail-info-grid">
              {data.technicals.Beta != null && (
                <div className="detail-info-item">
                  <span className="detail-info-label">Beta</span>
                  <span className="detail-info-value">{Number(data.technicals.Beta).toFixed(2)}</span>
                </div>
              )}
              {data.technicals['52WeekHigh'] != null && (
                <div className="detail-info-item">
                  <span className="detail-info-label">52-Week High</span>
                  <span className="detail-info-value">${Number(data.technicals['52WeekHigh']).toFixed(2)}</span>
                </div>
              )}
              {data.technicals['52WeekLow'] != null && (
                <div className="detail-info-item">
                  <span className="detail-info-label">52-Week Low</span>
                  <span className="detail-info-value">${Number(data.technicals['52WeekLow']).toFixed(2)}</span>
                </div>
              )}
              {data.technicals['50DayMA'] != null && (
                <div className="detail-info-item">
                  <span className="detail-info-label">50-Day MA</span>
                  <span className="detail-info-value">${Number(data.technicals['50DayMA']).toFixed(2)}</span>
                </div>
              )}
              {data.technicals['200DayMA'] != null && (
                <div className="detail-info-item">
                  <span className="detail-info-label">200-Day MA</span>
                  <span className="detail-info-value">${Number(data.technicals['200DayMA']).toFixed(2)}</span>
                </div>
              )}
            </div>
          </div>
        </div>
      )}
    </div>
  );
}
