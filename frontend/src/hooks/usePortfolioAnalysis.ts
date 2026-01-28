import { useMutation } from '@tanstack/react-query';
import { analyzePortfolio } from '../services/portfolioService';
import { AnalyzeRequest } from '../types/portfolio';

export function usePortfolioAnalysis() {
  return useMutation({
    mutationFn: (request: AnalyzeRequest) => analyzePortfolio(request),
  });
}
