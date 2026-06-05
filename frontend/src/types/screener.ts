export type InstrumentType = 'STOCK' | 'ETF' | 'MUTUAL_FUND' | 'PREFERRED_STOCK' | 'INDEX' | 'BOND';

export interface InstrumentScreenerItem {
  id: number;
  ticker: string;
  name: string;
  instrumentType: InstrumentType;
  isin: string | null;
  currency: string | null;
  country: string | null;
  exchange: string | null;
  // Stock
  sector: string | null;
  marketCap: number | null;
  pe: number | null;
  eps: number | null;
  dividendYield: number | null;
  weekHigh52: number | null;
  weekLow52: number | null;
  beta: number | null;
  // ETF
  issuer: string | null;
  assetClass: string | null;
  expenseRatio: number | null;
  yield: number | null;
  totalAssets: number | null;
  holdingsCount: number | null;
  return1Y: number | null;
  // Mutual fund
  fundCategory: string | null;
  fundStyle: string | null;
  nav: number | null;
}

export interface InstrumentDetail {
  id: number;
  ticker: string;
  name: string;
  instrumentType: InstrumentType;
  isin: string | null;
  currency: string | null;
  country: string | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  general: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  highlights: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  valuation: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  technicals: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  financials: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  earnings: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  splitsDividends: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  sharesStats: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  analystRatings: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  etfData: Record<string, any> | null;
  // eslint-disable-next-line @typescript-eslint/no-explicit-any
  mutualFundData: Record<string, any> | null;
}

export interface ScreenerFilter {
  tickerContains?: string;
  nameContains?: string;
  country?: string;
  exchange?: string;
  sector?: string;
  issuer?: string;
  assetClass?: string;
  fundCategory?: string;
  fundStyle?: string;
}

export interface PagedResponse<T> {
  data: T[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface SearchResult {
  id: number;
  type: InstrumentType;
  ticker: string;
  name: string;
  exchange: string | null;
  matchType: string;
}

export interface SearchResponse {
  data: SearchResult[];
  meta: { query: string; resultCount: number; searchTimeMs: number };
}

export interface TypeCounts {
  [key: string]: number;
}

export const INSTRUMENT_TYPE_CONFIG: Record<string, {
  label: string;
  pluralLabel: string;
  route: string;
  detailRoute: string;
}> = {
  STOCK: { label: 'Stock', pluralLabel: 'Stocks', route: 'stocks', detailRoute: 'stock' },
  ETF: { label: 'ETF', pluralLabel: 'ETFs', route: 'etfs', detailRoute: 'etf' },
  MUTUAL_FUND: { label: 'Mutual Fund', pluralLabel: 'Mutual Funds', route: 'mutual-funds', detailRoute: 'mutual-fund' },
  PREFERRED_STOCK: { label: 'Preferred Stock', pluralLabel: 'Preferred Stocks', route: 'preferred-stocks', detailRoute: 'preferred-stock' },
  INDEX: { label: 'Index', pluralLabel: 'Indices', route: 'indices', detailRoute: 'index' },
  BOND: { label: 'Bond', pluralLabel: 'Bonds', route: 'bonds', detailRoute: 'bond' },
};
