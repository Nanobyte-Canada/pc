# Seamless Broker Sync & Post-Connection Flow

## Problem

After connecting a brokerage through SnapTrade, the user experience is broken:

1. User returns to the connections page, not the dashboard
2. Dashboard shows stale/empty data because position and activity sync hasn't completed
3. Sidebar doesn't show new accounts until manual refresh (cache not invalidated)
4. Two separate buttons ("Fetch Now" and "Sync Activities") require multiple clicks for a complete sync
5. No success notification or smooth transition after connection

## Goal

Make the broker connection and sync experience seamless: connect, sync everything automatically, show progress, notify on completion, and redirect to a fully-populated dashboard.

## Design

### 1. Unified Sync Backend Endpoint

**New endpoint:** `POST /api/v1/brokers/connections/{connectionId}/sync-all`

**File:** `backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt`

Runs the full sync pipeline sequentially for a single connection:
1. Position fetch (existing `positionFetchService.triggerManualFetch`)
2. Activity sync (existing `activityIngestionService.syncActivitiesForConnection`)
3. Balance sync (existing `activityIngestionService.syncBalanceForConnection`)

**Response:**
```json
{
  "connectionId": 43,
  "positionsFetched": 8,
  "activitiesSynced": 150,
  "balanceSynced": true,
  "message": "Sync completed successfully"
}
```

The existing `/fetch` and `/sync-activities` endpoints remain for backward compatibility but are no longer called by the frontend's primary flow.

### 2. Post-Connection Flow

**File:** `frontend/src/pages/BrokerConnectionsPage.tsx`

When SnapTrade redirects back with `success=true` or `status=SUCCESS`:

**Step 1 — Syncing state:** Show a syncing overlay/card on the connections page with a message: "Setting up your accounts..." and a progress indicator. Disable all action buttons during sync.

**Step 2 — Sync connections:** Call `POST /api/v1/brokers/connections/sync` to discover new accounts from SnapTrade.

**Step 3 — Sync all data per connection:** For each newly discovered connection (those with `positionsCount === 0`), call the new `POST /api/v1/brokers/connections/{id}/sync-all` endpoint sequentially (one after another to avoid deadlocks).

**Step 4 — Success notification:** Show a success toast/notification: "Connected! Synced X positions and Y activities across Z accounts."

**Step 5 — Cache invalidation:** Invalidate all broker and dashboard cache keys so the dashboard and sidebar have fresh data.

**Step 6 — Redirect:** After a 1.5-second delay (so user sees the success message), redirect to `/dashboard` using `navigate('/dashboard')`.

### 3. Cache Invalidation Fix

**File:** `frontend/src/hooks/useBrokerConnections.ts`

Current problem: broker mutations only invalidate `brokerKeys.*`, but the sidebar uses `dashboardKeys.accounts()` and dashboard widgets use `dashboardKeys.*`. After syncing broker data, dashboard and sidebar remain stale.

**Fix:** After the sync-all mutation succeeds, invalidate BOTH key families:
```typescript
queryClient.invalidateQueries({ queryKey: brokerKeys.all })
queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
```

This applies to:
- The new `useSyncAll()` hook
- The existing `useSyncConnections()` hook
- The existing `useDisconnectBroker()` hook

Import `dashboardKeys` from `useDashboardWidgets.ts` into `useBrokerConnections.ts`.

### 4. Connections Page Button Consolidation

**Files:**
- `frontend/src/components/broker/BrokerConnectionCard.tsx`
- `frontend/src/pages/BrokerConnectionsPage.tsx`

Replace "Fetch Now" + "Sync Activities" with a single **"Sync All"** button.

- On click: calls new `POST /api/v1/brokers/connections/{id}/sync-all`
- Button state: "Syncing..." with disabled state while in progress
- On success: show notification with counts ("Synced 8 positions, 150 activities")
- On success: invalidate all broker + dashboard cache keys
- On error: show error notification

Remove the `onSyncActivities`, `isSyncingActivities` props from `BrokerConnectionCard`. Replace `onFetch` with `onSyncAll`.

### 5. Dashboard Refresh Button Enhancement

**File:** `frontend/src/hooks/useDashboardWidgets.ts`

The sidebar's refresh button (`useRefreshAll`) currently calls `POST /api/v1/dashboard/refresh` which only fetches positions. It should also invalidate broker keys to ensure consistency:

```typescript
onSuccess: () => {
  queryClient.invalidateQueries({ queryKey: dashboardKeys.all })
  queryClient.invalidateQueries({ queryKey: brokerKeys.all })
}
```

## Files to Modify

### Backend
- `backend/portfolio/src/main/kotlin/com/portfolio/broker/controller/BrokerController.kt` — add sync-all endpoint

### Frontend
- `frontend/src/services/brokerService.ts` — add `syncAll()` API function
- `frontend/src/hooks/useBrokerConnections.ts` — add `useSyncAll()` hook, update cache invalidation
- `frontend/src/hooks/useDashboardWidgets.ts` — update `useRefreshAll` invalidation
- `frontend/src/pages/BrokerConnectionsPage.tsx` — rewrite post-connection flow, syncing state, redirect
- `frontend/src/components/broker/BrokerConnectionCard.tsx` — consolidate to single "Sync All" button

## Verification

1. Connect a new brokerage through SnapTrade
2. Verify syncing state appears on connections page
3. Verify success notification shows correct counts
4. Verify redirect to dashboard
5. Verify dashboard shows correct data immediately (no manual refresh needed)
6. Verify sidebar shows new accounts immediately
7. Verify "Sync All" button works on individual connections
8. Verify dashboard refresh button still works correctly
