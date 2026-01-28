import { useNavigate } from 'react-router-dom';
import { useAnalysisStore } from '../store/analysisStore';
import { SummaryCards } from '../components/analytics/SummaryCards';
import { SectorChart } from '../components/analytics/SectorChart';
import { GeographyChart } from '../components/analytics/GeographyChart';
import { TopHoldingsGrid } from '../components/analytics/TopHoldingsGrid';
import { RiskProfileChart } from '../components/analytics/RiskProfileChart';
import './AnalyticsPage.css';

export function AnalyticsPage() {
  const navigate = useNavigate();
  const { analysis, isLoading, error } = useAnalysisStore();

  if (isLoading) {
    return (
      <div className="analytics-page">
        <h1 className="page-title">Portfolio Analytics</h1>
        <div className="loading-state">
          <div className="loading-spinner"></div>
          <p>Analyzing your portfolio...</p>
        </div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="analytics-page">
        <h1 className="page-title">Portfolio Analytics</h1>
        <div className="error-state">
          <p className="error-message">Error: {error}</p>
          <button className="action-button" onClick={() => navigate('/builder')}>
            Back to Portfolio Builder
          </button>
        </div>
      </div>
    );
  }

  if (!analysis) {
    return (
      <div className="analytics-page">
        <h1 className="page-title">Portfolio Analytics</h1>
        <div className="empty-state">
          <p>No portfolio analysis available.</p>
          <p className="empty-hint">Build a portfolio and click "Analyze Portfolio" to see analytics.</p>
          <button className="action-button primary" onClick={() => navigate('/builder')}>
            Go to Portfolio Builder
          </button>
        </div>
      </div>
    );
  }

  return (
    <div className="analytics-page">
      <div className="analytics-header">
        <div>
          <h1 className="page-title">Portfolio Analytics</h1>
          <p className="page-subtitle">
            Analyze your portfolio's sector exposure, geography, holdings, and risk metrics
          </p>
        </div>
        <button className="action-button secondary" onClick={() => navigate('/builder')}>
          Edit Portfolio
        </button>
      </div>

      <div className="analytics-grid">
        <section className="analytics-card summary-card">
          <h2>Summary</h2>
          <SummaryCards analysis={analysis} />
        </section>

        <section className="analytics-card">
          <h2>Sector Exposure</h2>
          {analysis.sectorExposure.length > 0 ? (
            <SectorChart data={analysis.sectorExposure} />
          ) : (
            <p className="no-data">No sector data available</p>
          )}
        </section>

        <section className="analytics-card">
          <h2>Geography Exposure</h2>
          {analysis.geographyExposure.length > 0 ? (
            <GeographyChart data={analysis.geographyExposure} />
          ) : (
            <p className="no-data">No geography data available</p>
          )}
        </section>

        <section className="analytics-card full-width">
          <h2>Top Holdings (Look-Through)</h2>
          {analysis.topHoldings.length > 0 ? (
            <TopHoldingsGrid data={analysis.topHoldings} />
          ) : (
            <p className="no-data">No holdings data available</p>
          )}
        </section>

        <section className="analytics-card full-width">
          <h2>Risk Profile</h2>
          <RiskProfileChart metrics={analysis.riskMetrics} />
        </section>
      </div>
    </div>
  );
}
