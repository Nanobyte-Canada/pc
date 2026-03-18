export type InstrumentType = 'STOCK' | 'ETF';

export interface Stock {
  id: number;
  ticker: string;
  exchange?: string;
  name: string;
  isin?: string;
  cusip?: string;
  sedol?: string;
  currency: string;
  country: string;
  status: string;
  sector?: {
    code: string;
    name: string;
  };
}

export interface Etf {
  id: number;
  symbol: string;
  exchange: string;
  name: string;
  isin?: string;
  cusip?: string;
  issuer?: string;
  currency: string;
  domicile: string;
  inceptionDate?: string;
  expenseRatio?: number;
  assetClass?: string;
  status: string;
}

export interface SearchResult {
  id: string;
  type: InstrumentType;
  ticker: string;
  name: string;
  exchange?: string;
  matchType: 'TICKER_EXACT' | 'TICKER_PREFIX' | 'NAME_CONTAINS';
}

export interface SearchResponse {
  data: SearchResult[];
  meta: {
    query: string;
    resultCount: number;
    searchTimeMs: number;
  };
}
