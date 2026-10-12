-- Backend spec §5.1, §5.2. All that is held about a person at this stage: a nickname.

CREATE TABLE account (
    id                    UUID PRIMARY KEY,
    nickname              TEXT        NOT NULL,
    -- The nickname as it is compared: two that differ only in case are the same one.
    nickname_key          TEXT        NOT NULL UNIQUE,
    -- Set when a nickname was upheld as abusive and replaced; the account may not choose
    -- another until this day has passed.
    nickname_locked_until DATE,
    created_at            TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- One row per device that may speak for an account. The token itself is never stored:
-- this is its SHA-256, so a copy of this table opens nothing.
CREATE TABLE device_token (
    token_hash BYTEA PRIMARY KEY,
    account_id UUID        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX device_token_account ON device_token (account_id);
