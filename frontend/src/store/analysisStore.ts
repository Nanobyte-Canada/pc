import { create } from 'zustand';
import { PortfolioAnalysis } from '../types/portfolio';

interface AnalysisStore {
  analysis: PortfolioAnalysis | null;
  isLoading: boolean;
  error: string | null;
  setAnalysis: (analysis: PortfolioAnalysis) => void;
  setLoading: (loading: boolean) => void;
  setError: (error: string | null) => void;
  clearAnalysis: () => void;
}

export const useAnalysisStore = create<AnalysisStore>((set) => ({
  analysis: null,
  isLoading: false,
  error: null,

  setAnalysis: (analysis) => set({ analysis, error: null }),
  setLoading: (isLoading) => set({ isLoading }),
  setError: (error) => set({ error }),
  clearAnalysis: () => set({ analysis: null, error: null }),
}));
