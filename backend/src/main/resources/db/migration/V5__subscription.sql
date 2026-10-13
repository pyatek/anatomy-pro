-- Backend spec §8.1. A subscription as the store that sold it describes it, keyed by the
-- store's own id for it: Google's purchase token, Apple's original transaction id. The key
-- is what makes the same purchase, presented twice or by two accounts, one row with one owner.
CREATE TABLE subscription (
    store      TEXT        NOT NULL CHECK (store IN ('GOOGLE', 'APPLE')),
    store_id   TEXT        NOT NULL,
    product_id TEXT        NOT NULL,
    -- What the store last said (SubscriptionState). Whether that still lets anyone in is
    -- this and `expires_at` together, asked at the moment of asking.
    state      TEXT        NOT NULL,
    expires_at TIMESTAMPTZ,
    -- When the store said it. Something the store said earlier never overwrites this row,
    -- so an old signed transaction cannot bring back a subscription since refunded.
    stated_at  TIMESTAMPTZ NOT NULL,
    -- Null once the owning account was deleted. Deleting an account cancels nothing (§5.4),
    -- so the row stays and goes on being told of refunds and renewals; it lets nobody in
    -- until someone presents the purchase again and the store vouches for it again.
    account_id UUID REFERENCES account (id) ON DELETE SET NULL,
    created_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (store, store_id)
);

CREATE INDEX subscription_account ON subscription (account_id);
-- The re-check asks for what expires soon (§8.1).
CREATE INDEX subscription_expiry ON subscription (expires_at);

-- The notifications already acted on, by the id the store gave each, so that one delivered
-- twice changes nothing the second time (§8.1).
CREATE TABLE store_notification (
    store           TEXT        NOT NULL,
    notification_id TEXT        NOT NULL,
    received_at     TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (store, notification_id)
);
