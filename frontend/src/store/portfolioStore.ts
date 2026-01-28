import { create } from 'zustand';
import { InstrumentType } from '../types/instrument';

export interface PortfolioPosition {
  instrumentType: InstrumentType;
  instrumentId: number;
  symbol: string;
  name: string;
  weight: number;
}

interface PortfolioStore {
  positions: PortfolioPosition[];
  addPosition: (pos: Omit<PortfolioPosition, 'weight'>) => void;
  removePosition: (instrumentType: InstrumentType, instrumentId: number) => void;
  updateWeight: (instrumentType: InstrumentType, instrumentId: number, weight: number) => void;
  normalizeWeights: () => void;
  clearAll: () => void;
  totalWeight: () => number;
  hasPosition: (instrumentType: InstrumentType, instrumentId: number) => boolean;
}

export const usePortfolioStore = create<PortfolioStore>((set, get) => ({
  positions: [],

  addPosition: (pos) => {
    const { positions } = get();
    const exists = positions.some(
      p => p.instrumentType === pos.instrumentType && p.instrumentId === pos.instrumentId
    );
    if (!exists) {
      set((state) => ({
        positions: [...state.positions, { ...pos, weight: 0 }]
      }));
    }
  },

  removePosition: (instrumentType, instrumentId) => set((state) => ({
    positions: state.positions.filter(
      p => !(p.instrumentType === instrumentType && p.instrumentId === instrumentId)
    )
  })),

  updateWeight: (instrumentType, instrumentId, weight) => set((state) => ({
    positions: state.positions.map(p =>
      p.instrumentType === instrumentType && p.instrumentId === instrumentId
        ? { ...p, weight: Math.max(0, Math.min(1, weight)) }
        : p
    )
  })),

  normalizeWeights: () => set((state) => {
    const total = state.positions.reduce((sum, p) => sum + p.weight, 0);
    if (total === 0) return state;
    return {
      positions: state.positions.map(p => ({
        ...p,
        weight: p.weight / total
      }))
    };
  }),

  clearAll: () => set({ positions: [] }),

  totalWeight: () => get().positions.reduce((sum, p) => sum + p.weight, 0),

  hasPosition: (instrumentType, instrumentId) =>
    get().positions.some(
      p => p.instrumentType === instrumentType && p.instrumentId === instrumentId
    )
}));
