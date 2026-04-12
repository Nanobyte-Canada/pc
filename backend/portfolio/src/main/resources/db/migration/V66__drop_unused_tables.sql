-- Drop unused/orphan tables that have no corresponding Kotlin entities or service code.
-- Identified via comprehensive repository audit (docs/agent-reference/unused-legacy.md).

-- 1. external_connection_tokens: Created in V29, FK to external_connections.
--    No entity, repository, or service. Superseded by broker_connections + TokenEncryptionService.
DROP TABLE IF EXISTS external_connection_tokens CASCADE;

-- 2. external_connections: Created in V29 as "Future: Brokerage OAuth" placeholder.
--    No entity, repository, or service. Superseded by broker_connections.
DROP TABLE IF EXISTS external_connections CASCADE;

-- 3. app_metadata: Created in V1 with a single row ('schema_version', '1.0.0').
--    No entity or service. Schema versioning is handled by flyway_schema_history.
DROP TABLE IF EXISTS app_metadata CASCADE;

-- 4. fund_sector_allocations: Created in V14. Entity and repository exist but
--    no service imports or uses them. ETF sector data comes from etf_sector_allocations_factset instead.
DROP TABLE IF EXISTS fund_sector_allocations CASCADE;
