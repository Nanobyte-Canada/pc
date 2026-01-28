import { describe, it, expect, beforeEach } from 'vitest';
import { useAnalysisStore } from './analysisStore';
import { PortfolioAnalysis } from '../types/portfolio';

describe('analysisStore', () => {
  beforeEach(() => {
    useAnalysisStore.getState().clearAnalysis();
    useAnalysisStore.getState().setLoading(false);
    useAnalysisStore.getState().setError(null);
  });

  const mockAnalysis: PortfolioAnalysis = {
    summary: {
      totalPositions: 2,
      directStockCount: 1,
      etfCount: 1,
      mutualFundCount: 0,
      lookThroughStockCount: 100,
      analysisDate: '2024-01-15',
    },
    validation: {
      isValid: true,
      totalWeight: 1.0,
      errors: [],
      warnings: [],
    },
    sectorExposure: [
      { sectorCode: '45', sectorName: 'Information Technology', weight: 0.5 },
    ],
    geographyExposure: [
      { country: 'USA', region: 'North America', weight: 0.8 },
    ],
    topHoldings: [
      {
        stockId: 1,
        ticker: 'AAPL',
        name: 'Apple Inc.',
        effectiveWeight: 0.1,
        sources: [{ type: 'DIRECT', contribution: 0.1 }],
      },
    ],
    riskMetrics: {
      concentrationHHI: 0.05,
      top10Concentration: 0.4,
      sectorConcentrationHHI: 0.15,
      estimatedVolatility: 0.18,
      volatilitySource: 'CATEGORY_PROXY',
    },
  };

  it('should set analysis data', () => {
    useAnalysisStore.getState().setAnalysis(mockAnalysis);

    const { analysis, error } = useAnalysisStore.getState();
    expect(analysis).toEqual(mockAnalysis);
    expect(error).toBeNull();
  });

  it('should set loading state', () => {
    useAnalysisStore.getState().setLoading(true);
    expect(useAnalysisStore.getState().isLoading).toBe(true);

    useAnalysisStore.getState().setLoading(false);
    expect(useAnalysisStore.getState().isLoading).toBe(false);
  });

  it('should set error message', () => {
    useAnalysisStore.getState().setError('Something went wrong');
    expect(useAnalysisStore.getState().error).toBe('Something went wrong');
  });

  it('should clear analysis data', () => {
    useAnalysisStore.getState().setAnalysis(mockAnalysis);
    useAnalysisStore.getState().setError('test error');

    useAnalysisStore.getState().clearAnalysis();

    const { analysis, error } = useAnalysisStore.getState();
    expect(analysis).toBeNull();
    expect(error).toBeNull();
  });
});
