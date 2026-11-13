-- V1: Auth - users, verification codes, refresh tokens (ADR-017)
-- Sign-in: email + password + email OTP, or Google

CREATE TABLE users (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,   -- internal only, never in API or JWT
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),  -- exposed id (JWT sub, API)
    name VARCHAR(255) NOT NULL,
    email VARCHAR(255) NOT NULL,            -- stored lower-case
    phone VARCHAR(13) NULL,                 -- +91XXXXXXXXXX, optional, not used for login
    password_hash VARCHAR(255) NULL,        -- BCrypt; NULL for Google-only users
    google_sub VARCHAR(255) NULL,           -- Google's stable user id ("sub" claim)
    avatar_url VARCHAR(512) NULL,           -- Google picture URLs can exceed 255
    role VARCHAR(30) NOT NULL DEFAULT 'USER',      -- values come from the Role enum, no DB check so new roles need no migration
    status VARCHAR(30) NOT NULL DEFAULT 'PENDING_VERIFICATION',  -- values come from the UserStatus enum, no DB check
    email_verified_at TIMESTAMPTZ NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_user_phone  CHECK (phone ~ '^\+91[6-9][0-9]{9}$'),
    -- Every live account has a way to sign in
    CONSTRAINT ck_user_login_method CHECK (status = 'DELETED' OR password_hash IS NOT NULL OR google_sub IS NOT NULL),
    -- ACTIVE means the email is proven (verification link or Google)
    CONSTRAINT ck_user_active_verified CHECK (status <> 'ACTIVE' OR email_verified_at IS NOT NULL)
);

CREATE UNIQUE INDEX ux_users_email  ON users (lower(email));
CREATE UNIQUE INDEX ux_users_google ON users (google_sub) WHERE google_sub IS NOT NULL;

CREATE TABLE verification_codes (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    code_hash VARCHAR(64) NOT NULL,          -- SHA-256 hex of OTP or email-link token, never raw
    purpose VARCHAR(30) NOT NULL,            -- values come from the CodePurpose enum, no DB check
    attempts INT NOT NULL DEFAULT 0,         -- reject after 5 wrong tries
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ NULL,                -- NULL = not used yet
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_verification_codes_user_purpose ON verification_codes (user_id, purpose);

CREATE TABLE refresh_tokens (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    family_id UUID NOT NULL,                  -- one per sign-in (one device)
    token_hash VARCHAR(64) NOT NULL UNIQUE,   -- SHA-256 hex, never the raw token
    family_expires_at TIMESTAMPTZ NOT NULL,   -- sign-in + 7 days, rotation never extends it
    used_at TIMESTAMPTZ NULL,                 -- set when rotated; reuse => revoke the family
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE INDEX idx_refresh_tokens_user_id   ON refresh_tokens (user_id);
CREATE INDEX idx_refresh_tokens_family_id ON refresh_tokens (family_id);

CREATE OR REPLACE FUNCTION update_updated_at_column()
RETURNS TRIGGER AS $$
BEGIN
    NEW.updated_at = CURRENT_TIMESTAMP;
    RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER update_users_updated_at
    BEFORE UPDATE ON users
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
