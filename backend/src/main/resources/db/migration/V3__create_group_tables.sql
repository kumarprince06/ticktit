-- V3: Groups (STORAGE.md "Groups and bookings", api.md "Groups").
-- A group is formed for one bookable item (a show, for movies) before any seat is picked.
-- What is booked is item_type + item_id with no foreign key: the vertical module validates it (ADR-007).
-- Status/role/type columns hold Java enum names as text; no CHECK lists.

CREATE TABLE groups (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    public_id UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),   -- the only id in URLs and the API
    code VARCHAR(6) NOT NULL,                       -- invite code shared on WhatsApp, e.g. K7Q2MA
    item_type VARCHAR(20) NOT NULL,                 -- ItemType enum (SHOW for movies)
    item_id BIGINT NOT NULL,
    leader_id BIGINT NOT NULL REFERENCES users(id),
    status VARCHAR(20) NOT NULL DEFAULT 'OPEN',     -- GroupStatus enum
    max_members INT NOT NULL DEFAULT 8,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_group_size CHECK (max_members > 0)
);
CREATE UNIQUE INDEX ux_groups_code ON groups (code);
CREATE INDEX idx_groups_leader ON groups (leader_id);

CREATE TABLE group_members (
    id BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    group_id BIGINT NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    role VARCHAR(20) NOT NULL,                      -- MemberRole enum
    status VARCHAR(20) NOT NULL DEFAULT 'JOINED',   -- MemberStatus enum
    joined_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ux_group_member UNIQUE (group_id, user_id)
);
CREATE INDEX idx_group_members_user ON group_members (user_id);

CREATE TRIGGER update_groups_updated_at
    BEFORE UPDATE ON groups
    FOR EACH ROW
    EXECUTE FUNCTION update_updated_at_column();
