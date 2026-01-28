import { PortfolioAnalysis } from '../../types/portfolio';
import './SummaryCards.css';

interface SummaryCardsProps {
  analysis: PortfolioAnalysis;
}

export function SummaryCards({ analysis }: SummaryCardsProps) {
  const { summary, riskMetrics } = analysis;

  return (
    <div className="summary-cards-grid">
      <div className="summary-stat-card">
        <span className="stat-value">{summary.totalPositions}</span>
        <span className="stat-label">Positions</span>
        <span className="stat-detail">
          {summary.directStockCount} stocks, {summary.etfCount} ETFs, {summary.mutualFundCount} MFs
        </span>
      </div>

      <div className="summary-stat-card">
        <span className="stat-value">{summary.lookThroughStockCount}</span>
        <span className="stat-label">Look-Through Stocks</span>
        <span className="stat-detail">Underlying holdings exposure</span>
      </div>

      <div className="summary-stat-card">
        <span className="stat-value">{(riskMetrics.concentrationHHI * 100).toFixed(1)}%</span>
        <span className="stat-label">Concentration HHI</span>
        <span className="stat-detail">
          {riskMetrics.concentrationHHI < 0.1 ? 'Diversified' : riskMetrics.concentrationHHI < 0.25 ? 'Moderate' : 'Concentrated'}
        </span>
      </div>

      <div className="summary-stat-card">
        <span className="stat-value">{(riskMetrics.estimatedVolatility * 100).toFixed(1)}%</span>
        <span className="stat-label">Est. Volatility</span>
        <span className="stat-detail">Annualized ({riskMetrics.volatilitySource.replace('_', ' ').toLowerCase()})</span>
      </div>

      <div className="summary-stat-card">
        <span className="stat-value">{(riskMetrics.top10Concentration * 100).toFixed(1)}%</span>
        <span className="stat-label">Top 10 Weight</span>
        <span className="stat-detail">
          {riskMetrics.top10Concentration < 0.4 ? 'Well distributed' : riskMetrics.top10Concentration < 0.6 ? 'Moderate' : 'Top heavy'}
        </span>
      </div>

      <div className="summary-stat-card">
        <span className="stat-value">{summary.analysisDate}</span>
        <span className="stat-label">Analysis Date</span>
        <span className="stat-detail">Holdings as of date</span>
      </div>
    </div>
  );
}
