# Brokerage Connection Types & Feature Matrix

## Context

The app currently connects to brokerages through SnapTrade but only uses a fraction of the available SDK data. All connections default to `read` mode (no trading), and the brokerage list shows only name/logo/description — missing critical feature flags like trading support, real-time data, fractional shares, and authorization types (OAuth, credentials, API key).

The immediate trigger is enabling Questrade OAuth (which offers trading + real-time data vs the current read-only API connection), but the solution is designed to work for all SnapTrade-supported brokerages at scale.

## Prerequisites (Manual — Not Code)

Before Questrade OAuth works:
1. Register an app on Questrade's API portal (https://login.questrade.com/APIAccess/) to get a Questrade app key
2. Add the Questrade app key to SnapTrade dashboard (https://dashboard.snaptrade.com)
3. Verify Questrade OAuth appears in `listAllBrokerages()` response

## SDK Data Currently Unused

### `Brokerage` model fields (from `listAllBrokerages()`)
Currently mapped: `name`, `slug`, `logoUrl`, `description`

**Not mapped (need to add):**
- `id` (UUID) — SnapTrade brokerage ID
- `displayName` (String) — User-friendly display name
- `url` (String) — Broker website
- `openUrl` (String) — URL to open new account
- `enabled` (Boolean) — Whether brokerage is available
- `maintenanceMode` (Boolean) — Currently in maintenance
- `isDegraded` (Boolean) — Degraded performance
- `allowsTrading` (Boolean) — Supports order placement
- `allowsFractionalUnits` (Boolean) — Fractional share trading
- `hasReporting` (Boolean) — Activity/reporting data
- `isRealTimeConnection` (Boolean) — Real-time vs cached
- `brokerageType` (object: id + name) — Category

### `BrokerageAuthorizationTypeReadOnly` (from `listAllBrokerageAuthorizationType()`)
**Not used at all.** Returns per-brokerage:
- `type`: `read` or `trade` (permission level)
- `authType`: `OAUTH`, `SCRAPE`, or `UNOFFICIAL_API` (auth method)

### `loginSnapTradeUser` connectionType parameter
**Not passed.** Defaults to `read`. Options:
- `read` — Data access only (current default)
- `trade` — Data + trading access
- `trade-if-available` — Attempts trade, falls back to read

## Changes

### Backend

#### 1. Enrich `SnapTradeBrokerageDto` — `SnapTradeDtos.kt`

Add all feature fields:

```kotlin
data class SnapTradeBrokerageDto(
    val id: UUID? = null,
    val name: String?,
    val slug: String?,
    val displayName: String? = null,
    val logoUrl: String?,
    val description: String?,
    val url: String? = null,
    val openUrl: String? = null,
    val enabled: Boolean? = null,
    val maintenanceMode: Boolean? = null,
    val isDegraded: Boolean? = null,
    val allowsTrading: Boolean? = null,
    val allowsFractionalUnits: Boolean? = null,
    val hasReporting: Boolean? = null,
    val isRealTimeConnection: Boolean? = null,
    val brokerageType: BrokerageTypeDto? = null
)

data class BrokerageTypeDto(
    val id: UUID?,
    val name: String?
)
```

Update `Brokerage.toDto()` mapper in `SnapTradeAdapterImpl.kt` to map all fields.

#### 2. Add authorization types adapter method — `SnapTradeAdapter.kt` + `SnapTradeAdapterImpl.kt`

```kotlin
// New DTO
data class SnapTradeBrokerageAuthTypeDto(
    val id: UUID?,
    val type: String?,      // "read" or "trade"
    val authType: String?,  // "OAUTH", "SCRAPE", "UNOFFICIAL_API"
    val brokerageId: UUID?
)

// New adapter method
fun listBrokerageAuthorizationTypes(brokerageSlug: String? = null): List<SnapTradeBrokerageAuthTypeDto>
```

Implementation calls `snaptrade.referenceData.listAllBrokerageAuthorizationType()` with optional `.brokerage(slug)`.

#### 3. Add `connectionType` to login redirect flow

Thread `connectionType` parameter through the entire chain:

- `ConnectBrokerRequest` — add `connectionType: String?` field (default `null` → backend defaults to `trade-if-available`)
- `BrokerController.connectBroker()` — pass `request?.connectionType` to service
- `BrokerService.getConnectionPortalUrl()` — add `connectionType` param
- `SnapTradeService.getConnectionPortalUrl()` — add `connectionType` param
- `SnapTradeAdapter.getLoginRedirectUrl()` — add `connectionType` param
- `SnapTradeAdapterImpl.getLoginRedirectUrl()` — call `request.connectionType(connectionType ?: "trade-if-available")`

#### 4. Enrich `BrokerDto` — `BrokerDtos.kt`

```kotlin
data class BrokerDto(
    val id: Long? = null,
    val code: String? = null,
    val name: String,
    val slug: String? = null,
    val status: String? = null,
    val logoUrl: String? = null,
    val description: String? = null,
    // New fields
    val url: String? = null,
    val openUrl: String? = null,
    val enabled: Boolean? = null,
    val maintenanceMode: Boolean? = null,
    val isDegraded: Boolean? = null,
    val allowsTrading: Boolean? = null,
    val allowsFractionalUnits: Boolean? = null,
    val hasReporting: Boolean? = null,
    val isRealTimeConnection: Boolean? = null,
    val brokerageType: String? = null,
    val authTypes: List<BrokerAuthTypeDto>? = null
)

data class BrokerAuthTypeDto(
    val type: String,    // "read" or "trade"
    val authType: String // "OAUTH", "SCRAPE", "UNOFFICIAL_API"
)
```

#### 5. Update `BrokerService.getAvailableBrokers()`

Batch-fetch brokerage auth types and merge into broker list:

```kotlin
fun getAvailableBrokers(): List<BrokerDto> {
    val brokerages = snapTradeService.listAvailableBrokerages()
    val authTypes = snapTradeService.listBrokerageAuthorizationTypes()
    // Group auth types by brokerage ID
    // Map each brokerage to enriched BrokerDto with auth types
}
```

Cache this response (brokerage list rarely changes).

#### 6. Flyway migration — `V62__connection_type_column.sql`

```sql
ALTER TABLE broker_connections ADD COLUMN connection_type VARCHAR(20);
COMMENT ON COLUMN broker_connections.connection_type IS 'SnapTrade connection type: read or trade';
```

No NOT NULL constraint — existing connections will have `NULL` (unknown/legacy).

#### 7. Update `BrokerConnection` entity

Add `connectionType` field. Set during `syncConnections` if available from SnapTrade authorization data.

#### 8. New endpoint — `GET /api/v1/brokers/authorization-types`

Returns raw authorization types for all brokerages. Useful for the feature matrix. Cached with `@Cacheable`.

### Frontend

#### 1. Enrich `Broker` type — `types/broker.ts`

```typescript
export interface Broker {
  id?: number
  name: string
  slug?: string
  status?: string
  logoUrl: string | null
  description: string | null
  // New fields
  url?: string
  openUrl?: string
  enabled?: boolean
  maintenanceMode?: boolean
  isDegraded?: boolean
  allowsTrading?: boolean
  allowsFractionalUnits?: boolean
  hasReporting?: boolean
  isRealTimeConnection?: boolean
  brokerageType?: string
  authTypes?: BrokerAuthType[]
}

export interface BrokerAuthType {
  type: 'read' | 'trade'
  authType: 'OAUTH' | 'SCRAPE' | 'UNOFFICIAL_API'
}
```

#### 2. Add `connectionType` to `ConnectBrokerRequest`

```typescript
export interface ConnectBrokerRequest {
  broker?: string
  reconnectAuthId?: string
  connectionType?: 'read' | 'trade' | 'trade-if-available'
}
```

#### 3. Redesign `BrokerCard` component

Add feature badges below the broker name:
- Auth type badge: "OAuth" (green), "Credentials" (yellow), "API Key" (blue)
- Feature badges: "Trading" (if `allowsTrading`), "Real-time" (if `isRealTimeConnection`), "Fractional" (if `allowsFractionalUnits`)
- Status overlay: "Maintenance" or "Degraded" if applicable
- Dim disabled brokerages (`enabled: false`)

Keep existing layout (logo + name + click to connect).

#### 4. Create `BrokerageMatrix` component

New component in `components/broker/BrokerageMatrix.tsx` with AG Grid table:
- Columns: Logo, Name, Auth Type, Trading, Fractional, Real-time, Reporting, Status
- Boolean columns rendered as checkmark/cross icons
- Auth type column shows badge
- Filterable/sortable via AG Grid
- Row click navigates to connection flow
- CSS file: `BrokerageMatrix.css`

#### 5. Integrate into `BrokerConnectionsPage`

Add a toggle/tab to switch between:
- **Card view** (current grid of BrokerCards — default)
- **Matrix view** (new BrokerageMatrix table)

#### 6. Pass `connectionType` when connecting

When user clicks a broker card, pass `connectionType: 'trade-if-available'` in the request. This gives trading access when the broker supports it, automatically falling back to read-only otherwise.

## Files Modified

| File | Change |
|------|--------|
| `backend/.../adapter/SnapTradeDtos.kt` | Enrich `SnapTradeBrokerageDto`, add `SnapTradeBrokerageAuthTypeDto`, add `BrokerageTypeDto` |
| `backend/.../adapter/SnapTradeAdapter.kt` | Add `listBrokerageAuthorizationTypes()`, add `connectionType` param to `getLoginRedirectUrl` |
| `backend/.../adapter/SnapTradeAdapterImpl.kt` | Implement new method, update `toDto()` mapper, use `connectionType` in login |
| `backend/.../dto/BrokerDtos.kt` | Enrich `BrokerDto`, add `BrokerAuthTypeDto`, add `connectionType` to `ConnectBrokerRequest` |
| `backend/.../service/BrokerService.kt` | Enrich `getAvailableBrokers()` with auth types, add `connectionType` param to `getConnectionPortalUrl` |
| `backend/.../service/SnapTradeService.kt` | Add `listBrokerageAuthorizationTypes()`, add `connectionType` param |
| `backend/.../controller/BrokerController.kt` | Pass `connectionType`, add `/authorization-types` endpoint |
| `backend/.../entity/BrokerConnection.kt` | Add `connectionType` field |
| `backend/.../resources/db/migration/V62__connection_type_column.sql` | Add column |
| `frontend/src/types/broker.ts` | Enrich `Broker` type, add `BrokerAuthType`, update `ConnectBrokerRequest` |
| `frontend/src/components/broker/BrokerCard.tsx` | Add feature badges and auth type indicator |
| `frontend/src/components/broker/BrokerCard.css` | Badge styles |
| `frontend/src/components/broker/BrokerageMatrix.tsx` | **New** — AG Grid feature matrix |
| `frontend/src/components/broker/BrokerageMatrix.css` | **New** — Matrix styles |
| `frontend/src/pages/BrokerConnectionsPage.tsx` | Add card/matrix view toggle, pass connectionType |
| `frontend/src/services/brokerService.ts` | Update `connectBroker` to pass connectionType |

## Verification

1. **Backend**: `docker compose exec backend ./gradlew build` — compile + test
2. **Frontend**: `npm run build && npm run lint` from `frontend/`
3. **Manual test — Brokerage list**:
   - Call `GET /api/v1/brokers` and verify response includes feature flags and auth types
   - Verify Questrade entries show correct capabilities
4. **Manual test — Connection flow**:
   - Connect a broker and verify `connectionType=trade-if-available` is passed to SnapTrade
   - Check that `broker_connections.connection_type` is populated after sync
5. **Manual test — Feature matrix UI**:
   - Open broker connections page
   - Toggle between card and matrix views
   - Verify feature badges render correctly
   - Verify filtering/sorting works in matrix view
