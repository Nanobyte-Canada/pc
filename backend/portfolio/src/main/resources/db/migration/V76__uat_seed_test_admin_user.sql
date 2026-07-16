-- UAT Test Admin User Seeding (corrected)
-- V75 was a no-op because the 'app.environment' PostgreSQL GUC was never set.
-- This migration unconditionally inserts the test admin user for UAT.
-- Password hash generated with Argon2id (m=65536, t=3, p=4) for password 'ZD0udQtGayM4On4tHiIMMA'
-- Credentials: test-admin@nanobyte.ca / ZD0udQtGayM4On4tHiIMMA

INSERT INTO users (email, password_hash, name, email_verified, email_verified_at, status, created_at, updated_at)
VALUES (
    'test-admin@nanobyte.ca',
    '$argon2id$v=19$m=65536,t=3,p=4$+X7kZFDDYOMyUx1OLZ+bgQ$+7lDHAvkjLXgaxQoOyDGdxfhOMrLzX42+SOtlk0Q43w',
    'Test Admin',
    true,
    NOW(),
    'ACTIVE',
    NOW(),
    NOW()
)
ON CONFLICT (email) DO NOTHING;

-- Assign ADMIN role to test user (only if not exists)
INSERT INTO user_roles (user_id, role_id)
SELECT u.id, r.id
FROM users u, roles r
WHERE u.email = 'test-admin@nanobyte.ca'
  AND r.name = 'ADMIN'
  AND NOT EXISTS (
      SELECT 1 FROM user_roles ur
      WHERE ur.user_id = u.id AND ur.role_id = r.id
  );
