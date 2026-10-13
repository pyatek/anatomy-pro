-- Backend spec §5.3. A Google or Apple identity is its provider and the provider's stable
-- subject, and nothing else: no email, no name, no picture.
CREATE TABLE identity (
    provider   TEXT        NOT NULL,
    subject    TEXT        NOT NULL,
    account_id UUID        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    linked_at  TIMESTAMPTZ NOT NULL,
    -- One identity opens one account; that is what makes signing in on a second device a merge.
    PRIMARY KEY (provider, subject)
);

CREATE INDEX identity_account ON identity (account_id);

-- A merge deletes an account, so it is written down: which one survived, which one went.
-- Not tied to `account` by a key, because one of the two no longer exists.
CREATE TABLE account_merge (
    id                BIGSERIAL PRIMARY KEY,
    surviving_account UUID        NOT NULL,
    merged_account    UUID        NOT NULL,
    merged_at         TIMESTAMPTZ NOT NULL
);
