import { create } from 'zustand'
import type {
  StrategyType,
  StrategyInfo,
  Leg,
  CalculationResult,
  StrategyEducation,
} from '@/types/options'

interface StrategyState {
  strategies: StrategyInfo[]
  selectedStrategy: StrategyType | null
  selectedStrategyEducation: StrategyEducation | null
  legs: Leg[]
  calculationResult: CalculationResult | null
  isCalculating: boolean
  connectionStatus: { connected: boolean; brokerType: string | null }
  tradeInProgress: boolean
  setStrategies: (strategies: StrategyInfo[]) => void
  setSelectedStrategy: (type: StrategyType | null) => void
  setSelectedStrategyEducation: (education: StrategyEducation | null) => void
  setLegs: (legs: Leg[]) => void
  addLeg: (leg: Leg) => void
  removeLeg: (index: number) => void
  updateLeg: (index: number, leg: Leg) => void
  setCalculationResult: (result: CalculationResult | null) => void
  setIsCalculating: (calculating: boolean) => void
  setConnectionStatus: (connected: boolean, brokerType: string | null) => void
  setTradeInProgress: (inProgress: boolean) => void
  clearStrategy: () => void
}

export const useStrategyStore = create<StrategyState>()((set) => ({
  strategies: [],
  selectedStrategy: null,
  selectedStrategyEducation: null,
  legs: [],
  calculationResult: null,
  isCalculating: false,
  connectionStatus: { connected: false, brokerType: null },
  tradeInProgress: false,
  setStrategies: (strategies) => set({ strategies }),
  setSelectedStrategy: (type) =>
    set({ selectedStrategy: type, legs: [], calculationResult: null, selectedStrategyEducation: null }),
  setSelectedStrategyEducation: (education) => set({ selectedStrategyEducation: education }),
  setLegs: (legs) => set({ legs }),
  addLeg: (leg) => set((state) => ({ legs: [...state.legs, leg] })),
  removeLeg: (index) => set((state) => ({ legs: state.legs.filter((_, i) => i !== index) })),
  updateLeg: (index, leg) =>
    set((state) => ({ legs: state.legs.map((l, i) => (i === index ? leg : l)) })),
  setCalculationResult: (result) => set({ calculationResult: result }),
  setIsCalculating: (calculating) => set({ isCalculating: calculating }),
  setConnectionStatus: (connected, brokerType) =>
    set({ connectionStatus: { connected, brokerType } }),
  setTradeInProgress: (inProgress) => set({ tradeInProgress: inProgress }),
  clearStrategy: () =>
    set({ selectedStrategy: null, selectedStrategyEducation: null, legs: [], calculationResult: null }),
}))
