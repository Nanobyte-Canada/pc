import { PortfolioAnalysis, AnalyzeRequest } from '../types/portfolio';
import { InstrumentType } from '../types/instrument';
import { apiFetch } from './api';

export interface PortfolioPosition {
  instrumentType: InstrumentType;
  instrumentId: number;
  weight: number;
}

export interface ValidateResponse {
  isValid: boolean;
  totalWeight: number;
  errors: { code: string; message: string }[];
  warnings: string[];
}

export interface NormalizedPosition {
  instrumentType: string;
  instrumentId: number;
  originalWeight: number;
  normalizedWeight: number;
}

export interface NormalizeResponse {
  originalTotal: number;
  normalizedPositions: NormalizedPosition[];
}

export async function analyzePortfolio(request: AnalyzeRequest): Promise<{ data: PortfolioAnalysis }> {
  const response = await apiFetch('/api/v1/portfolio/analyze', {
    method: 'POST',
    body: JSON.stringify(request),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  const data = await response.json();
  return { data };
}

export async function validatePortfolio(positions: PortfolioPosition[]): Promise<ValidateResponse> {
  const response = await apiFetch('/api/v1/portfolio/validate', {
    method: 'POST',
    body: JSON.stringify({ positions }),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.json();
}

export async function normalizePortfolio(positions: PortfolioPosition[]): Promise<NormalizeResponse> {
  const response = await apiFetch('/api/v1/portfolio/normalize', {
    method: 'POST',
    body: JSON.stringify({ positions }),
  });

  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }

  return response.json();
}
