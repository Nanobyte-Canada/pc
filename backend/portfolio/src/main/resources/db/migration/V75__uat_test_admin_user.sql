-- UAT Test Admin User Seeding
-- Credentials sourced from Vault: secret/data/uat/test-admin
-- This migration only runs in UAT environment (when app.environment = 'uat')
-- In other environments, the migration is a no-op

-- Insert test admin user (only in UAT environment and only if not exists)
DO $$
BEGIN
    IF current_setting('app.environment', true) = 'uat' THEN
        INSERT INTO users (email, password_hash, name, email_verified, email_verified_at, status, created_at, updated_at)
        VALUES (
            COALESCE(current_setting('app.test_admin_email', true), 'test-admin@localhost'),
            COALESCE(current_setting('app.test_admin_password_hash', true), '$argon2id$v=19$m=65536,t=3,p=4$placeholder'),
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
        WHERE u.email = COALESCE(current_setting('app.test_admin_email', true), 'test-admin@localhost')
          AND r.name = 'ADMIN'
          AND NOT EXISTS (
              SELECT 1 FROM user_roles ur
              WHERE ur.user_id = u.id AND ur.role_id = r.id
          );
    END IF;
END $$;
