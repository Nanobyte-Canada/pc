import { describe, it, expect, beforeEach } from 'vitest';
import { usePortfolioStore } from './portfolioStore';

describe('portfolioStore', () => {
  beforeEach(() => {
    usePortfolioStore.getState().clearAll();
  });

  it('should add a position to the portfolio', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    const { positions } = usePortfolioStore.getState();
    expect(positions).toHaveLength(1);
    expect(positions[0].symbol).toBe('AAPL');
    expect(positions[0].weight).toBe(0);
  });

  it('should not add duplicate positions', () => {
    const store = usePortfolioStore.getState();

    store.addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    expect(usePortfolioStore.getState().positions).toHaveLength(1);
  });

  it('should remove a position from the portfolio', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    usePortfolioStore.getState().removePosition('STOCK', 1);

    expect(usePortfolioStore.getState().positions).toHaveLength(0);
  });

  it('should update position weight', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    usePortfolioStore.getState().updateWeight('STOCK', 1, 0.5);

    expect(usePortfolioStore.getState().positions[0].weight).toBe(0.5);
  });

  it('should calculate total weight', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });
    usePortfolioStore.getState().updateWeight('STOCK', 1, 0.3);

    usePortfolioStore.getState().addPosition({
      instrumentType: 'ETF',
      instrumentId: 2,
      symbol: 'SPY',
      name: 'SPDR S&P 500 ETF',
    });
    usePortfolioStore.getState().updateWeight('ETF', 2, 0.7);

    expect(usePortfolioStore.getState().totalWeight()).toBe(1.0);
  });

  it('should normalize weights to 100%', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });
    usePortfolioStore.getState().updateWeight('STOCK', 1, 0.3);

    usePortfolioStore.getState().addPosition({
      instrumentType: 'ETF',
      instrumentId: 2,
      symbol: 'SPY',
      name: 'SPDR S&P 500 ETF',
    });
    usePortfolioStore.getState().updateWeight('ETF', 2, 0.6);

    // Total is 0.9, normalize to 1.0
    usePortfolioStore.getState().normalizeWeights();

    const { positions } = usePortfolioStore.getState();
    const total = positions.reduce((sum, p) => sum + p.weight, 0);
    expect(total).toBeCloseTo(1.0, 5);
  });

  it('should clear all positions', () => {
    usePortfolioStore.getState().addPosition({
      instrumentType: 'STOCK',
      instrumentId: 1,
      symbol: 'AAPL',
      name: 'Apple Inc.',
    });

    usePortfolioStore.getState().addPosition({
      instrumentType: 'ETF',
      instrumentId: 2,
      symbol: 'SPY',
      name: 'SPDR S&P 500 ETF',
    });

    usePortfolioStore.getState().clearAll();

    expect(usePortfolioStore.getState().positions).toHaveLength(0);
  });
});
