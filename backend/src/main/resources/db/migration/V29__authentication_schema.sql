-- ============================================================================
-- Authentication & Authorization Schema
-- Migration V29: Users, tokens, roles, and audit logging
-- ============================================================================

-- ----------------------------------------------------------------------------
-- CORE USER TABLE
-- ----------------------------------------------------------------------------
CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    email_verified_at TIMESTAMPTZ,
    password_hash VARCHAR(255),  -- NULL for OAuth-only users
    name VARCHAR(255),
    avatar_url VARCHAR(500),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    failed_login_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_login_at TIMESTAMPTZ,
    last_login_ip VARCHAR(45),  -- IPv6 compatible
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_users_email UNIQUE (email),
    CONSTRAINT chk_users_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'SUSPENDED', 'DELETED'))
);

CREATE INDEX idx_users_email ON users(email);
CREATE INDEX idx_users_status ON users(status);
CREATE INDEX idx_users_email_verified ON users(email_verified) WHERE email_verified = FALSE;

COMMENT ON TABLE users IS 'Core user accounts for authentication';
COMMENT ON COLUMN users.password_hash IS 'Argon2id hash, NULL for OAuth-only accounts';

-- ----------------------------------------------------------------------------
-- USER IDENTITIES (OAuth/Social Providers)
-- ----------------------------------------------------------------------------
CREATE TABLE user_identities (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,  -- 'google', 'github', etc.
    provider_user_id VARCHAR(255) NOT NULL,
    provider_email VARCHAR(255),
    provider_name VARCHAR(255),
    provider_avatar_url VARCHAR(500),
    access_token_encrypted VARCHAR(1000),  -- Encrypted provider access token
    refresh_token_encrypted VARCHAR(1000), -- Encrypted provider refresh token
    token_expires_at TIMESTAMPTZ,
    raw_profile JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_identities_provider UNIQUE (provider, provider_user_id),
    CONSTRAINT fk_user_identities_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_user_identities_user ON user_identities(user_id);
CREATE INDEX idx_user_identities_provider ON user_identities(provider, provider_user_id);

COMMENT ON TABLE user_identities IS 'OAuth provider identities linked to users';

-- ----------------------------------------------------------------------------
-- REFRESH TOKENS (for JWT rotation)
-- ----------------------------------------------------------------------------
CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 of token
    device_info VARCHAR(255),  -- User-Agent or device identifier
    ip_address VARCHAR(45),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    revoked_reason VARCHAR(100),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_refresh_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_refresh_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_hash ON refresh_tokens(token_hash);
CREATE INDEX idx_refresh_tokens_expires ON refresh_tokens(expires_at) WHERE revoked_at IS NULL;

COMMENT ON TABLE refresh_tokens IS 'Refresh tokens for JWT rotation and revocation';

-- ----------------------------------------------------------------------------
-- PASSWORD RESET TOKENS
-- ----------------------------------------------------------------------------
CREATE TABLE password_reset_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,  -- SHA-256 of token
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_password_reset_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_password_reset_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_password_reset_tokens_user ON password_reset_tokens(user_id);
CREATE INDEX idx_password_reset_tokens_hash ON password_reset_tokens(token_hash);

COMMENT ON TABLE password_reset_tokens IS 'Time-limited tokens for password reset';

-- ----------------------------------------------------------------------------
-- EMAIL VERIFICATION TOKENS
-- ----------------------------------------------------------------------------
CREATE TABLE email_verification_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    new_email VARCHAR(255),  -- For email change verification
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_email_verification_tokens_hash UNIQUE (token_hash),
    CONSTRAINT fk_email_verification_tokens_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_email_verification_tokens_user ON email_verification_tokens(user_id);
CREATE INDEX idx_email_verification_tokens_hash ON email_verification_tokens(token_hash);

COMMENT ON TABLE email_verification_tokens IS 'Tokens for email verification and email change';

-- ----------------------------------------------------------------------------
-- OAUTH STATE TOKENS (CSRF protection for OAuth flows)
-- ----------------------------------------------------------------------------
CREATE TABLE oauth_states (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    state_hash VARCHAR(64) NOT NULL,
    provider VARCHAR(50) NOT NULL,
    redirect_uri VARCHAR(500),
    code_verifier VARCHAR(128),  -- For PKCE
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_oauth_states_hash UNIQUE (state_hash)
);

CREATE INDEX idx_oauth_states_hash ON oauth_states(state_hash);
CREATE INDEX idx_oauth_states_expires ON oauth_states(expires_at);

COMMENT ON TABLE oauth_states IS 'OAuth2 state tokens for CSRF protection';

-- ----------------------------------------------------------------------------
-- ROLES AND PERMISSIONS
-- ----------------------------------------------------------------------------
CREATE TABLE roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    name VARCHAR(50) NOT NULL,
    description VARCHAR(255),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_roles_name UNIQUE (name)
);

INSERT INTO roles (name, description) VALUES
    ('USER', 'Standard user with access to portfolio features'),
    ('ADMIN', 'Administrator with full system access');

CREATE TABLE user_roles (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    role_id BIGINT NOT NULL,
    granted_by BIGINT,
    granted_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_user_roles UNIQUE (user_id, role_id),
    CONSTRAINT fk_user_roles_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_role FOREIGN KEY (role_id) REFERENCES roles(id) ON DELETE CASCADE,
    CONSTRAINT fk_user_roles_granted_by FOREIGN KEY (granted_by) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_user_roles_user ON user_roles(user_id);
CREATE INDEX idx_user_roles_role ON user_roles(role_id);

COMMENT ON TABLE roles IS 'Application roles for RBAC';
COMMENT ON TABLE user_roles IS 'User-to-role assignments';

-- ----------------------------------------------------------------------------
-- AUDIT LOG
-- ----------------------------------------------------------------------------
CREATE TABLE audit_log (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT,
    event_type VARCHAR(50) NOT NULL,
    event_subtype VARCHAR(50),
    ip_address VARCHAR(45),
    user_agent VARCHAR(500),
    resource_type VARCHAR(50),
    resource_id VARCHAR(100),
    details JSONB,
    success BOOLEAN NOT NULL DEFAULT TRUE,
    error_message VARCHAR(500),
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_audit_log_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE SET NULL
);

CREATE INDEX idx_audit_log_user ON audit_log(user_id);
CREATE INDEX idx_audit_log_event ON audit_log(event_type);
CREATE INDEX idx_audit_log_created ON audit_log(created_at);
CREATE INDEX idx_audit_log_resource ON audit_log(resource_type, resource_id);

COMMENT ON TABLE audit_log IS 'Security and compliance audit trail';

-- Event types:
-- AUTH_LOGIN, AUTH_LOGOUT, AUTH_SIGNUP, AUTH_FAILED_LOGIN
-- PASSWORD_RESET_REQUEST, PASSWORD_RESET_COMPLETE, PASSWORD_CHANGE
-- EMAIL_VERIFICATION, EMAIL_CHANGE
-- OAUTH_LINK, OAUTH_UNLINK
-- ROLE_GRANT, ROLE_REVOKE
-- USER_LOCK, USER_UNLOCK, USER_SUSPEND

-- ----------------------------------------------------------------------------
-- EXTERNAL CONNECTIONS (Future: Brokerage OAuth)
-- ----------------------------------------------------------------------------
CREATE TABLE external_connections (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL,
    provider VARCHAR(50) NOT NULL,  -- 'plaid', 'questrade', 'ibkr'
    provider_account_id VARCHAR(255),
    display_name VARCHAR(255),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE',
    last_sync_at TIMESTAMPTZ,
    last_sync_status VARCHAR(20),
    metadata JSONB,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT uq_external_connections UNIQUE (user_id, provider, provider_account_id),
    CONSTRAINT fk_external_connections_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE,
    CONSTRAINT chk_external_connections_status CHECK (status IN ('ACTIVE', 'INACTIVE', 'EXPIRED', 'ERROR'))
);

CREATE INDEX idx_external_connections_user ON external_connections(user_id);
CREATE INDEX idx_external_connections_provider ON external_connections(provider);

-- ----------------------------------------------------------------------------
-- EXTERNAL CONNECTION TOKENS (Encrypted OAuth tokens for brokerages)
-- ----------------------------------------------------------------------------
CREATE TABLE external_connection_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    connection_id BIGINT NOT NULL,
    access_token_encrypted TEXT NOT NULL,  -- AES-256-GCM encrypted
    refresh_token_encrypted TEXT,
    token_type VARCHAR(50) DEFAULT 'Bearer',
    scope VARCHAR(500),
    expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),

    CONSTRAINT fk_external_connection_tokens_conn FOREIGN KEY (connection_id)
        REFERENCES external_connections(id) ON DELETE CASCADE
);

CREATE INDEX idx_external_connection_tokens_conn ON external_connection_tokens(connection_id);

COMMENT ON TABLE external_connections IS 'User connections to external trading platforms';
COMMENT ON TABLE external_connection_tokens IS 'Encrypted OAuth tokens for brokerage connections';

-- ----------------------------------------------------------------------------
-- TRIGGERS FOR UPDATED_AT
-- ----------------------------------------------------------------------------
CREATE TRIGGER trg_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_user_identities_updated_at
    BEFORE UPDATE ON user_identities
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_external_connections_updated_at
    BEFORE UPDATE ON external_connections
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();

CREATE TRIGGER trg_external_connection_tokens_updated_at
    BEFORE UPDATE ON external_connection_tokens
    FOR EACH ROW EXECUTE FUNCTION update_updated_at_column();
