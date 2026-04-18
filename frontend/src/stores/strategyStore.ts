import { create } from 'zustand'
import type {
  StrategyType,
  StrategyInfo,
  Leg,
  CalculationResult,
} from '@/types/options'

interface StrategyState {
  strategies: StrategyInfo[]
  selectedStrategy: StrategyType | null
  legs: Leg[]
  calculationResult: CalculationResult | null
  isCalculating: boolean
  setStrategies: (strategies: StrategyInfo[]) => void
  setSelectedStrategy: (type: StrategyType | null) => void
  setLegs: (legs: Leg[]) => void
  addLeg: (leg: Leg) => void
  removeLeg: (index: number) => void
  updateLeg: (index: number, leg: Leg) => void
  setCalculationResult: (result: CalculationResult | null) => void
  setIsCalculating: (calculating: boolean) => void
  clearStrategy: () => void
}

export const useStrategyStore = create<StrategyState>()((set) => ({
  strategies: [],
  selectedStrategy: null,
  legs: [],
  calculationResult: null,
  isCalculating: false,
  setStrategies: (strategies) => set({ strategies }),
  setSelectedStrategy: (type) => set({ selectedStrategy: type, legs: [], calculationResult: null }),
  setLegs: (legs) => set({ legs }),
  addLeg: (leg) => set((state) => ({ legs: [...state.legs, leg] })),
  removeLeg: (index) => set((state) => ({ legs: state.legs.filter((_, i) => i !== index) })),
  updateLeg: (index, leg) =>
    set((state) => ({ legs: state.legs.map((l, i) => (i === index ? leg : l)) })),
  setCalculationResult: (result) => set({ calculationResult: result }),
  setIsCalculating: (calculating) => set({ isCalculating: calculating }),
  clearStrategy: () => set({ selectedStrategy: null, legs: [], calculationResult: null }),
}))
