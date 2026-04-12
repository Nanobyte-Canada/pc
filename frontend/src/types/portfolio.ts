import { InstrumentType } from './screener';

export interface PortfolioPosition {
  instrumentType: InstrumentType;
  instrumentId: number;
  symbol: string;
  name: string;
  weight: number;
}

export interface PortfolioValidation {
  isValid: boolean;
  totalWeight: number;
  errors: ValidationError[];
  warnings: string[];
}

export interface ValidationError {
  code: string;
  message: string;
}

export interface SectorExposure {
  sectorCode: string;
  sectorName: string;
  weight: number;
}

export interface GeographyExposure {
  country: string;
  countryName?: string;
  region: string;
  weight: number;
}

export interface TopHolding {
  stockId: number;
  ticker: string;
  name: string;
  effectiveWeight: number;
  sources: ExposureSource[];
}

export interface ExposureSource {
  type: 'DIRECT' | 'ETF' | 'NESTED_ETF';
  instrumentId?: number;
  instrumentSymbol?: string;
  contribution: number;
}

export interface RiskMetrics {
  concentrationHHI: number;
  top10Concentration: number;
  sectorConcentrationHHI: number;
  estimatedVolatility: number;
  volatilitySource: string;
}

export interface PortfolioAnalysis {
  summary: {
    totalPositions: number;
    directStockCount: number;
    etfCount: number;
    lookThroughStockCount: number;
    analysisDate: string;
  };
  validation: PortfolioValidation;
  sectorExposure: SectorExposure[];
  geographyExposure: GeographyExposure[];
  topHoldings: TopHolding[];
  riskMetrics: RiskMetrics;
}

export interface AnalyzeRequest {
  positions: {
    instrumentType: InstrumentType;
    instrumentId: number;
    weight: number;
  }[];
  analysisDate?: string;
}
