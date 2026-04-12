import { useQuery } from '@tanstack/react-query';
import {
  getScreenerInstruments,
  getInstrumentDetail,
  getReferenceData,
  getTypeCounts,
  searchInstruments,
} from '../services/screenerService';
import type { ScreenerFilter } from '../types/screener';

export function useInstrumentScreener(
  type: string,
  filter: ScreenerFilter,
  page: number,
  size: number,
  sortField: string = 'ticker',
  sortDirection: string = 'asc'
) {
  return useQuery({
    queryKey: ['screener', type, filter, page, size, sortField, sortDirection],
    queryFn: () => getScreenerInstruments(type, filter, page, size, sortField, sortDirection),
    staleTime: 1000 * 60 * 2, // 2 minutes
  });
}

export function useInstrumentDetail(type: string, ticker: string) {
  return useQuery({
    queryKey: ['instrument-detail', type, ticker],
    queryFn: () => getInstrumentDetail(type, ticker),
    enabled: !!ticker && !!type,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

export function useReferenceValues(type: string, field: string) {
  return useQuery({
    queryKey: ['reference-values', type, field],
    queryFn: () => getReferenceData(type, field),
    staleTime: 1000 * 60 * 10, // 10 minutes
  });
}

export function useTypeCounts() {
  return useQuery({
    queryKey: ['type-counts'],
    queryFn: getTypeCounts,
    staleTime: 1000 * 60 * 5, // 5 minutes
  });
}

export function useNewInstrumentSearch(query: string, types?: string[], limit?: number) {
  return useQuery({
    queryKey: ['new-instrument-search', query, types, limit],
    queryFn: () => searchInstruments(query, types, limit),
    enabled: query.length >= 1,
    staleTime: 1000 * 60 * 5, // 5 minutes
    placeholderData: (previousData) => previousData,
  });
}
