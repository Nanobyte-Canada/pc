# Legacy Test Disposition

Date: 2026-09-17.

## Existing Vitest tests (15 files)

| File | Type | Disposition | Phase |
|---|---|---|---|
| frontend/src/App.test.tsx | App render | Rewrite with scenario IDs | Phase 1 |
| frontend/src/pages/auth/LoginPage.test.tsx | Component | Rewrite with scenario IDs | Phase 2 |
| frontend/src/pages/PortfolioPage.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/pages/admin/AdminPage.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/ui/ErrorBoundary.test.tsx | Component | Rewrite with scenario IDs | Phase 1 |
| frontend/src/components/broker/BrokerCard.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/ConnectBrokerDialog.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/ConnectionStatus.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/components/broker/BrokerConnectionCard.test.tsx | Component | Rewrite with scenario IDs | Phase 3 |
| frontend/src/stores/quoteStore.test.ts | Store | Rewrite with scenario IDs | Phase 3 |
| frontend/src/store/analysisStore.test.ts | Store | Rewrite with scenario IDs | Phase 6 |
| frontend/src/store/portfolioStore.test.ts | Store | Rewrite with scenario IDs | Phase 3 |
| frontend/src/services/api.test.ts | Service | Rewrite with scenario IDs | Phase 1 |
| frontend/src/services/brokerService.test.ts | Service | Rewrite with scenario IDs | Phase 3 |
| frontend/src/hooks/__tests__/useWheelPositions.test.ts | Hook | Rewrite with scenario IDs | Phase 3 |

## Existing e2e specs (1 file)

| File | Type | Disposition | Phase |
|---|---|---|---|
| frontend/e2e/questrade-connection.spec.ts | Browser (local-dev target, env-var gated) | Rewrite with scenario IDs and tags against deployed UAT; retire the localhost config | Phase 3 |

## Rewrite rules

- Each test is rewritten feature by feature; old specs deleted only after parity demonstrated in CI.
- Scenario IDs follow the `<FEATURE-ID>-<NNN>` pattern from the plan.
- No coverage drop mid-flight: new test passes before old test is deleted.
