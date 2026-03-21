import { SearchResponse, Stock, Etf, InstrumentType } from '../types/instrument';
import { apiFetch } from './api';

export interface PagedResponse<T> {
  data: T[];
  meta: {
    page: number;
    size: number;
    totalElements: number;
    totalPages: number;
  };
}

export interface StockFilter {
  sector?: string;
  country?: string;
  status?: string;
  tickerContains?: string;
  nameContains?: string;
}

export interface EtfFilter {
  issuer?: string;
  assetClass?: string;
  status?: string;
  symbolContains?: string;
  nameContains?: string;
  maxExpenseRatio?: number;
}

export async function searchInstruments(
  query: string,
  type?: InstrumentType | 'all',
  limit: number = 10
): Promise<SearchResponse> {
  const params = new URLSearchParams({ q: query, limit: String(limit) });
  if (type && type !== 'all') {
    params.set('type', type);
  }

  const response = await apiFetch(`/api/v1/instruments/search?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getStocks(
  filter: StockFilter = {},
  page: number = 0,
  size: number = 50,
  sort: string = 'ticker:asc'
): Promise<PagedResponse<Stock>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });

  if (filter.sector) params.set('sector', filter.sector);
  if (filter.country) params.set('country', filter.country);
  if (filter.status) params.set('status', filter.status);
  if (filter.tickerContains) params.set('tickerContains', filter.tickerContains);
  if (filter.nameContains) params.set('nameContains', filter.nameContains);

  const response = await apiFetch(`/api/v1/stocks?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getStockById(id: number): Promise<Stock> {
  const response = await apiFetch(`/api/v1/stocks/${id}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getEtfs(
  filter: EtfFilter = {},
  page: number = 0,
  size: number = 50,
  sort: string = 'symbol:asc'
): Promise<PagedResponse<Etf>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });

  if (filter.issuer) params.set('issuer', filter.issuer);
  if (filter.assetClass) params.set('assetClass', filter.assetClass);
  if (filter.status) params.set('status', filter.status);
  if (filter.symbolContains) params.set('symbolContains', filter.symbolContains);
  if (filter.nameContains) params.set('nameContains', filter.nameContains);
  if (filter.maxExpenseRatio) params.set('maxExpenseRatio', String(filter.maxExpenseRatio));

  const response = await apiFetch(`/api/v1/etfs?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getEtfById(id: number): Promise<Etf> {
  const response = await apiFetch(`/api/v1/etfs/${id}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export interface EtfHoldingDto {
  stockId: number | null;
  ticker: string;
  name: string;
  weight: number | null;
  shares: number | null;
  marketValue: number | null;
  sector: { code: string; name: string } | null;
  country: string | null;
  holdingType: string | null;
  rank: number | null;
}

export interface EtfHoldingsResponse {
  etfId: number;
  etfSymbol: string;
  asOfDate: string;
  holdingsCount: number;
  holdings: EtfHoldingDto[];
  metadata: { resolvedCount: number; unresolvedCount: number; resolvedPercent: number } | null;
}

export async function getEtfHoldings(etfId: number): Promise<EtfHoldingsResponse> {
  const res = await apiFetch(`/api/v1/etfs/${etfId}/holdings`);
  if (!res.ok) throw new Error(`HTTP ${res.status}`);
  return res.json();
}
