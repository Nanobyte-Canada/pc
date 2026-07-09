export const brokerKeys = {
  all: ['brokers'] as const,
  available: () => [...brokerKeys.all, 'available'] as const,
  connections: () => [...brokerKeys.all, 'connections'] as const,
  positions: () => [...brokerKeys.all, 'positions'] as const,
  connectionPositions: (id: number) => [...brokerKeys.positions(), id] as const,
  aggregatedPositions: () => [...brokerKeys.positions(), 'aggregated'] as const,
  activities: (id: number) => [...brokerKeys.all, 'activities', id] as const,
  balanceHistory: (id: number) => [...brokerKeys.all, 'balance-history', id] as const,
  gatewayHealth: () => [...brokerKeys.all, 'gateway-health'] as const
}

export const dashboardKeys = {
  all: ['dashboard'] as const,
  summary: (connectionId?: number) => [...dashboardKeys.all, 'summary', connectionId] as const,
  cash: (connectionId?: number) => [...dashboardKeys.all, 'cash', connectionId] as const,
  sectorExposure: (connectionId?: number) => [...dashboardKeys.all, 'sectorExposure', connectionId] as const,
  geographyExposure: (connectionId?: number) => [...dashboardKeys.all, 'geographyExposure', connectionId] as const,
  riskProfile: (connectionId?: number) => [...dashboardKeys.all, 'riskProfile', connectionId] as const,
  openOrders: () => [...dashboardKeys.all, 'openOrders'] as const,
  fees: (connectionId?: number) => [...dashboardKeys.all, 'fees', connectionId] as const,
  dividendCalendar: (month?: string, connectionId?: number) =>
    [...dashboardKeys.all, 'dividendCalendar', month, connectionId] as const,
  positions: (connectionId?: number) => [...dashboardKeys.all, 'positions', connectionId] as const,
  holdings: (connectionId?: number) => [...dashboardKeys.all, 'holdings', connectionId] as const,
  accounts: () => [...dashboardKeys.all, 'accounts'] as const,
  irr: (connectionId?: number) => [...dashboardKeys.all, 'irr', connectionId] as const,
}
