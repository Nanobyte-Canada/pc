-- Remove IBKR broker connections before dropping the enum value from app code.
DELETE FROM broker_gateway.connections WHERE broker_type = 'IBKR';
ALTER TABLE broker_gateway.connections DROP CONSTRAINT chk_broker_type;
ALTER TABLE broker_gateway.connections ADD CONSTRAINT chk_broker_type
  CHECK (broker_type IN ('QUESTRADE', 'WEALTHSIMPLE'));
