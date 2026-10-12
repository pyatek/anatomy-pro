-- Backend spec §10. The server generates the daily, so it holds the graph, the names and
-- what has been reviewed — read from the same manifest the app installs.

CREATE TABLE structure (
    id         TEXT PRIMARY KEY,
    pack_id    TEXT    NOT NULL,
    parent_id  TEXT,
    system_id  TEXT,
    region_id  TEXT,
    laterality TEXT    NOT NULL,
    is_group   BOOLEAN NOT NULL
);

CREATE INDEX structure_pack ON structure (pack_id);

CREATE TABLE structure_text (
    structure_id TEXT NOT NULL REFERENCES structure (id) ON DELETE CASCADE,
    locale       TEXT NOT NULL,
    name         TEXT NOT NULL,
    -- Which name this is, by the hash the app computes too (PackManifest.contentHash).
    content_hash TEXT NOT NULL,
    PRIMARY KEY (structure_id, locale)
);

-- A review of one name in one language. Deliberately not tied to `structure` by a key: a
-- re-ingest replaces a pack's structures, and a review must outlive that. It counts only
-- while the name it was made of is still the name (design §24.1).
CREATE TABLE structure_verification (
    structure_id       TEXT        NOT NULL,
    locale             TEXT        NOT NULL,
    state              TEXT        NOT NULL,
    verified_text_hash TEXT        NOT NULL,
    verified_at        TIMESTAMPTZ NOT NULL,
    verified_by        TEXT        NOT NULL,
    PRIMARY KEY (structure_id, locale)
);
