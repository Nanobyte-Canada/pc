import { useQuery } from '@tanstack/react-query';
import {
  getStocks,
  getEtfs,
  getMutualFunds,
  StockFilter,
  EtfFilter,
  MutualFundFilter
} from '../services/instrumentService';

export function useStockScreener(
  filter: StockFilter,
  page: number = 0,
  size: number = 50,
  sort: string = 'ticker:asc'
) {
  return useQuery({
    queryKey: ['stocks', filter, page, size, sort],
    queryFn: () => getStocks(filter, page, size, sort),
    staleTime: 1000 * 60 * 2, // 2 minutes
  });
}

export function useEtfScreener(
  filter: EtfFilter,
  page: number = 0,
  size: number = 50,
  sort: string = 'symbol:asc'
) {
  return useQuery({
    queryKey: ['etfs', filter, page, size, sort],
    queryFn: () => getEtfs(filter, page, size, sort),
    staleTime: 1000 * 60 * 2,
  });
}

export function useMutualFundScreener(
  filter: MutualFundFilter,
  page: number = 0,
  size: number = 50,
  sort: string = 'symbol:asc'
) {
  return useQuery({
    queryKey: ['mutual-funds', filter, page, size, sort],
    queryFn: () => getMutualFunds(filter, page, size, sort),
    staleTime: 1000 * 60 * 2,
  });
}
