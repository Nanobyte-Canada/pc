import { SearchResponse, Stock, Etf, MutualFund, InstrumentType } from '../types/instrument';
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
  exchange?: string;
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

export interface MutualFundFilter {
  issuer?: string;
  fundType?: string;
  assetClass?: string;
  status?: string;
  symbolContains?: string;
  nameContains?: string;
  maxExpenseRatio?: number;
  maxMinimumInvestment?: number;
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
  if (filter.exchange) params.set('exchange', filter.exchange);
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

export async function getMutualFunds(
  filter: MutualFundFilter = {},
  page: number = 0,
  size: number = 50,
  sort: string = 'symbol:asc'
): Promise<PagedResponse<MutualFund>> {
  const params = new URLSearchParams({ page: String(page), size: String(size), sort });

  if (filter.issuer) params.set('issuer', filter.issuer);
  if (filter.fundType) params.set('fundType', filter.fundType);
  if (filter.assetClass) params.set('assetClass', filter.assetClass);
  if (filter.status) params.set('status', filter.status);
  if (filter.symbolContains) params.set('symbolContains', filter.symbolContains);
  if (filter.nameContains) params.set('nameContains', filter.nameContains);
  if (filter.maxExpenseRatio) params.set('maxExpenseRatio', String(filter.maxExpenseRatio));
  if (filter.maxMinimumInvestment) params.set('maxMinimumInvestment', String(filter.maxMinimumInvestment));

  const response = await apiFetch(`/api/v1/mutual-funds?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getMutualFundById(id: number): Promise<MutualFund> {
  const response = await apiFetch(`/api/v1/mutual-funds/${id}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}
