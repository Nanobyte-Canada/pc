import { useQuery } from '@tanstack/react-query';
import { searchInstruments } from '../services/instrumentService';
import { InstrumentType } from '../types/instrument';

export function useInstrumentSearch(
  query: string,
  type?: InstrumentType | 'all',
  limit: number = 10
) {
  return useQuery({
    queryKey: ['instrument-search', query, type, limit],
    queryFn: () => searchInstruments(query, type, limit),
    enabled: query.length >= 1,
    staleTime: 1000 * 60 * 5, // 5 minutes
    placeholderData: (previousData) => previousData,
  });
}
