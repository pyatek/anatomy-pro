-- Backend spec §6 and §7.

-- The set for a UTC date: generated the first time anyone asks, then never recomputed, so
-- a content update at noon cannot hand the afternoon different questions (§6.1). The
-- questions carry their names in every language the daily is offered in, for that reason.
CREATE TABLE daily_set (
    date       DATE PRIMARY KEY,
    topic_id   TEXT        NOT NULL,
    seed       BIGINT      NOT NULL,
    questions  JSONB       NOT NULL,
    created_at TIMESTAMPTZ NOT NULL
);

-- One attempt per account per date; the primary key is what enforces it (§9.1).
CREATE TABLE daily_attempt (
    account_id     UUID        NOT NULL REFERENCES account (id) ON DELETE CASCADE,
    date           DATE        NOT NULL REFERENCES daily_set (date),
    locale         TEXT        NOT NULL,
    timed          BOOLEAN     NOT NULL,
    -- The server's clock at both ends; nothing the client says about time is kept (§6.3).
    started_at     TIMESTAMPTZ NOT NULL,
    submitted_at   TIMESTAMPTZ,
    answers        JSONB,
    correct        INT,
    elapsed_millis BIGINT,
    ranked         BOOLEAN     NOT NULL DEFAULT FALSE,
    PRIMARY KEY (account_id, date)
);

-- The order of a day's board: correctness, then time, then who submitted first (§9.2).
CREATE INDEX daily_attempt_board ON daily_attempt (date, correct DESC, elapsed_millis, submitted_at) WHERE ranked;

-- Consecutive UTC days with a completed daily, kept up on submit rather than counted on
-- read (§7). It is the daily's streak, not the device's study streak (§4.3).
ALTER TABLE account ADD COLUMN streak_days INT NOT NULL DEFAULT 0;
ALTER TABLE account ADD COLUMN streak_last DATE;

CREATE INDEX account_streak ON account (streak_days DESC) WHERE streak_days > 0;
