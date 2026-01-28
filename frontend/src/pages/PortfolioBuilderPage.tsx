import { useNavigate } from 'react-router-dom';
import { InstrumentTabs } from '../components/instruments/InstrumentTabs';
import { usePortfolioStore } from '../store/portfolioStore';
import { useAnalysisStore } from '../store/analysisStore';
import { usePortfolioAnalysis } from '../hooks/usePortfolioAnalysis';
import './PortfolioBuilderPage.css';

export function PortfolioBuilderPage() {
  const navigate = useNavigate();
  const { positions, removePosition, updateWeight, normalizeWeights, clearAll, totalWeight } = usePortfolioStore();
  const { setAnalysis, setLoading, setError } = useAnalysisStore();
  const { mutate: analyze, isPending } = usePortfolioAnalysis();

  const handleWeightChange = (instrumentType: string, instrumentId: number, value: string) => {
    const weight = parseFloat(value) / 100;
    if (!isNaN(weight)) {
      updateWeight(instrumentType as 'STOCK' | 'ETF' | 'MUTUAL_FUND', instrumentId, weight);
    }
  };

  const total = totalWeight();
  const isValid = Math.abs(total - 1) < 0.0001;

  const handleAnalyze = () => {
    if (positions.length === 0) return;

    const request = {
      positions: positions.map((pos) => ({
        instrumentType: pos.instrumentType,
        instrumentId: pos.instrumentId,
        weight: pos.weight,
      })),
    };

    setLoading(true);
    analyze(request, {
      onSuccess: (result) => {
        setAnalysis(result.data);
        setLoading(false);
        navigate('/analytics');
      },
      onError: (error) => {
        setError(error.message);
        setLoading(false);
      },
    });
  };

  return (
    <div className="portfolio-builder-page">
      <h1 className="page-title">Portfolio Builder</h1>
      <p className="page-subtitle">Build and analyze your portfolio with stocks, ETFs, and mutual funds</p>

      <div className="builder-sections">
        <section className="search-section">
          <h2>Add Instruments</h2>
          <InstrumentTabs />
        </section>

        <section className="basket-section">
          <div className="basket-header">
            <h2>Portfolio Basket</h2>
            <div className="basket-summary">
              <span className={`total-weight ${isValid ? 'valid' : total > 0 ? 'invalid' : ''}`}>
                Total: {(total * 100).toFixed(1)}%
              </span>
            </div>
          </div>

          {positions.length === 0 ? (
            <p className="empty-basket">No instruments added. Use the search above to add stocks, ETFs, or mutual funds.</p>
          ) : (
            <>
              <table className="basket-table">
                <thead>
                  <tr>
                    <th>Type</th>
                    <th>Symbol</th>
                    <th>Name</th>
                    <th>Weight (%)</th>
                    <th>Actions</th>
                  </tr>
                </thead>
                <tbody>
                  {positions.map((pos) => (
                    <tr key={`${pos.instrumentType}-${pos.instrumentId}`}>
                      <td>
                        <span className={`type-badge type-${pos.instrumentType.toLowerCase()}`}>
                          {pos.instrumentType === 'MUTUAL_FUND' ? 'MF' : pos.instrumentType}
                        </span>
                      </td>
                      <td className="symbol-cell">{pos.symbol}</td>
                      <td className="name-cell">{pos.name}</td>
                      <td>
                        <input
                          type="number"
                          min="0"
                          max="100"
                          step="0.1"
                          value={(pos.weight * 100).toFixed(1)}
                          onChange={(e) => handleWeightChange(pos.instrumentType, pos.instrumentId, e.target.value)}
                          className="weight-input"
                        />
                      </td>
                      <td>
                        <button
                          className="remove-button"
                          onClick={() => removePosition(pos.instrumentType, pos.instrumentId)}
                        >
                          Remove
                        </button>
                      </td>
                    </tr>
                  ))}
                </tbody>
              </table>

              <div className="basket-actions">
                <button className="action-button secondary" onClick={normalizeWeights}>
                  Normalize to 100%
                </button>
                <button className="action-button secondary" onClick={clearAll}>
                  Clear All
                </button>
                <button
                  className="action-button primary"
                  disabled={positions.length === 0 || isPending}
                  onClick={handleAnalyze}
                >
                  {isPending ? 'Analyzing...' : 'Analyze Portfolio'}
                </button>
              </div>
            </>
          )}
        </section>
      </div>
    </div>
  );
}
