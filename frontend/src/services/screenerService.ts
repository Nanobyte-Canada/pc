import { apiFetch } from '@/services/api';
import {
  InstrumentScreenerItem,
  InstrumentDetail,
  PagedResponse,
  ScreenerFilter,
  SearchResponse,
  TypeCounts,
} from '@/types/screener';

export async function getScreenerInstruments(
  type: string,
  filter: ScreenerFilter = {},
  page: number = 0,
  size: number = 50,
  sortField?: string,
  sortDirection?: string
): Promise<PagedResponse<InstrumentScreenerItem>> {
  const params = new URLSearchParams({ page: String(page), size: String(size) });

  if (sortField) {
    const direction = sortDirection || 'asc';
    params.set('sort', `${sortField}:${direction}`);
  }

  if (filter.tickerContains) params.set('tickerContains', filter.tickerContains);
  if (filter.nameContains) params.set('nameContains', filter.nameContains);
  if (filter.country) params.set('country', filter.country);
  if (filter.exchange) params.set('exchange', filter.exchange);
  if (filter.sector) params.set('sector', filter.sector);
  if (filter.issuer) params.set('issuer', filter.issuer);
  if (filter.assetClass) params.set('assetClass', filter.assetClass);
  if (filter.fundCategory) params.set('fundCategory', filter.fundCategory);
  if (filter.fundStyle) params.set('fundStyle', filter.fundStyle);

  const response = await apiFetch(`/api/v1/screener/${type}?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getInstrumentDetail(
  type: string,
  ticker: string
): Promise<InstrumentDetail> {
  const response = await apiFetch(`/api/v1/screener/detail/${type}/${encodeURIComponent(ticker)}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function searchInstruments(
  query: string,
  types?: string[],
  limit?: number
): Promise<SearchResponse> {
  const params = new URLSearchParams({ q: query });

  if (types && types.length > 0) {
    params.set('types', types.join(','));
  }
  if (limit !== undefined) {
    params.set('limit', String(limit));
  }

  const response = await apiFetch(`/api/v1/screener/search?${params}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getReferenceData(
  type: string,
  field: string
): Promise<string[]> {
  const response = await apiFetch(`/api/v1/screener/reference/${type}/${field}`);
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}

export async function getTypeCounts(): Promise<TypeCounts> {
  const response = await apiFetch('/api/v1/screener/counts');
  if (!response.ok) {
    throw new Error(`HTTP error! status: ${response.status}`);
  }
  return response.json();
}
