# Anatomy Pro — Backend Design (Phase 3)

**Date:** 2026-10-11
**Status:** Draft for the owner's review
**Builds on:** `2026-08-29-anatomy-pro-design.md` §9 (daily, leaderboard, identity), §10 as
amended by §29 (packs are free), §11 (backend), §14 (errors), §15 (testing);
`2026-09-24-all-screens-mocked-design.md` §4.3 to §4.6 (the contracts), §6 (the seam).

## 1. Purpose

Four of the app's eight repositories still refuse in a release build: the daily quiz, the
leaderboard, entitlements and packs. This document specifies the server and the hosted
content that stand behind them, and the client code that replaces each refusal. When it is
built, a release build has no placeholder tab.

It is written against the contracts as they exist in `shared/core-data`. Where a contract
cannot be honoured as written, §4 says so and says what changes.

## 2. Decided by the owner, 2026-10-11

| Question | Decision | What it costs |
|---|---|---|
| Where it runs | One rented VPS, Docker Compose | Backups, updates and uptime are ours to do (§11) |
| Identity in v1 | Anonymous first, **with** Google and Apple sign-in | Two provider integrations, account linking and merge rules in v1 (§5) |
| Who is a subscriber | The server verifies store purchases itself | Billing verification is specified here, not left to Phase 4 (§8) |
| Where packs live | A public object-storage bucket behind a CDN | A publish step in the pipeline; nothing on the server (§9) |

Already locked (design §2): Ktor, PostgreSQL, Flyway; a single container; horizontal scaling
is not a v1 concern.

## 3. Shape

```
                       ┌────────────────────────── VPS, Docker Compose ───────────────────────┐
  app ── HTTPS ──────► │ caddy (TLS) ──► backend (Ktor, JVM) ──► postgres                      │
   │                   │                     │                      ▲                          │
   │                   │                     └── nightly pg_dump ───┴──► object storage (private)│
   │                   └───────────────────────────────────────────────────────────────────────┘
   └── HTTPS ──► CDN ──► object storage (public): packs, their index, attribution
```

The app talks to two things, and they fail separately. Packs are fetched from the CDN with
no server involved, so the atlas installs and updates while the backend is down. The backend
is needed only for what is shared between people: the daily quiz, the boards, an account,
and whether someone has paid.

### 3.1 Where the code goes

One repository, as now.

| Module | Targets | What it is |
|---|---|---|
| `shared/core-quiz` (new, split out of `core-data/quiz`) | Android, iOS, **JVM** | `QuizNode`, `QuizGraph`: the generator, pure Kotlin. The server and the app must produce the same set from the same seed, and the only way to be sure of that is to run the same code. |
| `shared/api-contract` (new) | Android, iOS, JVM | `@Serializable` requests, responses and error codes. One definition, compiled into both ends. |
| `shared/core-network` (new) | Android, iOS | Ktor client; `KtorDailyRepository`, `KtorLeaderboardRepository`, `ServerEntitlementRepository`, `CdnPackRepository`; token storage. |
| `backend` (new) | JVM | The Ktor server, Flyway migrations, the content ingest command. |
| `pipeline` (existing) | Python | Gains `publish`: uploads packs and rewrites the index. |

`core-model` gains a JVM target for the id types. `core-data` stays as it is; it depends on
`core-quiz` instead of holding the generator.

## 4. What the contracts can and cannot promise

### 4.1 "Answer keys never leave the server" is not achievable, and that is acceptable

Design §9.1 lists it first among the integrity measures. It does not survive contact with the
client. A "name the highlighted structure" question tells the app which structure to
highlight; the app holds the atlas, and the atlas says what that structure is called. A "find
the structure" question shows a name, and the atlas maps names to structures. **Anyone with
the app's database can answer every daily question without the server's help**, whatever the
wire format hides.

What server authority does buy, and all it buys:

- **A score cannot be forged.** The server scores the answers it receives; a client cannot
  submit "10 of 10".
- **One attempt a day**, enforced where the client cannot reach.
- **The clock is the server's**, from handing out the questions to receiving the answers.
- **A floor on speed**, below which an attempt is not ranked.

This is the same position §9.1 already takes on scripting ("accepted for v1"). It is
restated here because the contract's `QuizQuestion.NameTheHighlighted` carries
`correctIndex`, and the wire format in §6 omits it only so that the lie is not written down
twice. The client fills it in from the highlighted structure's id, which it has to know.

### 4.2 The daily asks only about what everyone has

A ranked question about a structure the player's device cannot draw is unanswerable. The
daily therefore draws only from the pack onboarding installs for everyone, `skeletal-body`,
and only from names verified in every locale the server offers the daily in (design §7).
Until the skeleton is verified the daily has no eligible topic and `GET /daily/today`
reports it as unavailable — the same designed state as being offline.

### 4.3 Two different streaks

`ProgressRepository.streak` counts the user's own days on which anything was studied, and
lives on the device (all-screens §4.2). The **streak board** cannot rank that: the server
never sees a local session and must not trust a device's word for it. The board ranks
consecutive UTC days with a completed daily. The two numbers will differ for the same
person, and screen 16 labels the board's as daily-quiz days.

### 4.4 Sign-in carries the account, not the study history

Design §9.3 says sign-in "carries streaks across devices". In v1 that is true of everything
the server owns: the nickname, daily results, the daily streak, and the subscription.
Sessions, mastery and the local-day streak stay on the device where they were made. Syncing
them is its own design (conflict rules, what the server may believe) and is left out; §13
lists it.

### 4.5 Contract changes this requires

- `EntitlementRepository.purchase` and `restore` return what the **server** concluded, not
  what the store SDK said: a purchase is not an entitlement until the server has verified it.
- `PackRepository` is implemented without the server. `PackState.entitlement` and
  `PackFailure.NOT_ENTITLED` have no meaning left (all-screens §15.2) and are removed with
  this work.
- `DailyRepository.start` gains nothing in its signature; "timed or not" travels as a
  setting the implementation reads (§6.2).

## 5. Identity

### 5.1 Accounts and tokens

An account is created the first time the app needs the server, without asking the user
anything. `POST /v1/auth/anonymous` returns an account id and a **device token**: 256 random
bits, shown once. The server stores only its SHA-256. The app keeps it in the Keychain (iOS)
or the Keystore-backed store (Android), never in the settings database, and sends it as
`Authorization: Bearer`.

A token names one device's access to one account. An account may have several tokens — one
per device after sign-in. Tokens do not expire on a timer; they are revoked by deleting the
account, by signing out, or by the user removing a device.

### 5.2 Nicknames

A new account gets a generated nickname (`student-4821`) so it can be ranked at once, and can
change it: 3 to 20 characters, letters, digits, `_` and `-`, unique without regard to case.
A blocklist is checked on change; a nickname reported and upheld is replaced with a generated
one and the account loses the ability to change it for thirty days. The blocklist and the
report queue are files and a table in v1, not a moderation product.

### 5.3 Sign-in

`POST /v1/auth/link` takes a provider and its ID token. The server verifies the token against
the provider's published keys — Google's and Apple's JWKS, audience pinned to our client ids —
and reads only the stable subject. No email, name or picture is requested or stored.

Offering Google sign-in on iOS obliges offering Apple's (App Store guideline 4.8); both ship
together.

Three cases:

1. **The identity is new.** It is attached to the calling account. Nothing else changes.
2. **The identity already belongs to the calling account.** No-op.
3. **The identity belongs to another account** — the user is signing in on a second device.
   The calling device's token is re-pointed at that account, and the anonymous account it
   came from is merged in and deleted:
   - Daily attempts: one per date survives. Where both accounts played the same date, the
     attempt that was submitted first is kept, because that is the one the board has been
     showing.
   - The daily streak is recomputed from the surviving attempts.
   - The nickname and nickname state of the signed-in account are kept.
   - A subscription on either account now belongs to the surviving one.

   The merge is one transaction and is logged with both account ids.

### 5.4 Leaving

`DELETE /v1/account` removes the account, its tokens, identities, attempts and nickname, and
its rows on every board. Both stores require an in-app way to do this. A subscription is not
cancelled by deleting an account — only the store can do that — and the response says so.

### 5.5 What is held about a person

A nickname, a provider subject if they signed in, their daily attempts, and their purchase
tokens. No email, no device identifier beyond the token's hash, no location, no analytics.
This is what the privacy policy (Phase 4) has to describe, and it is short on purpose.

## 6. The daily quiz

### 6.1 The day's set

The set for a UTC date is generated the first time anyone asks for it and **stored**. It is
not recomputed: a content update at noon must not hand the afternoon's players different
questions from the morning's.

Generation, in `core-quiz`:

1. Eligible topics are groups in `skeletal-body` with at least four names verified in every
   daily locale (§4.2).
2. The topic is picked by a seed over the date from the eligible topics, skipping any used
   in the previous six days where there are enough to allow it.
3. `QuizGraph.questions(topic, NAME_THE_HIGHLIGHTED, 10, seed)` — the generator the app
   already runs, with its tiers, its one-answer-per-name rule and its golden test.

Ten questions, one format, in v1. "Find the structure" is not offered in the daily until
tap accuracy has been seen on devices (design §41 covers the mechanism, not the fairness).

### 6.2 The flow

| Step | Request | Server |
|---|---|---|
| Look | `GET /v1/daily/today` | Reports the date and this account's state for it: not started (with the question count), in progress, or completed (with the result). Unavailable when there is no eligible topic. |
| Start | `POST /v1/daily/{date}/start` `{ locale, timed }` | Creates the attempt, records the time, returns the questions. Idempotent: a second call returns the same attempt and the **original** start time. |
| Submit | `POST /v1/daily/{date}/submit` `{ answers }` | Scores, records, returns the result. Idempotent: a second call returns the stored result. |

A question on the wire is its id, the structure to highlight, and four options, each an id
and a name in the requested locale. Names come from the server so that every ranked player
reads the same verified text whatever version of the pack they hold.

`{date}` must be the server's current UTC date at start. An attempt belongs to the date it
started on.

### 6.3 Scoring, and when an attempt is not ranked

`correct` is counted against the stored set. `elapsedMillis` is the server's receive time
minus the start time; the times the client reports are ignored.

An attempt is stored but **unranked** — `rank` is null and it appears on no board — when:

- it was started with `timed: false` (design §12: an untimed session is unranked; turning
  the timer off is an accessibility setting and must never cost a result, only a place);
- it arrived faster than one second per question;
- it arrived after 00:15 UTC on the day following its date. Design §14 says "ranked if
  received within the day"; the quarter hour is for a submit that was retrying across
  midnight.

Rank is correctness first, then less time, then earlier submission (design §9.2). It is a
count of better ranked attempts plus one, computed on read; with an index on
`(date, correct desc, elapsed)` that holds well past any v1 number of players.

### 6.4 Failure, as the client meets it

| What happens | What the repository returns |
|---|---|
| No connection, or the server unreachable, on `today` or `start` | `DailyQuiz.Unavailable` — the designed state, with practice offered |
| `start` after the date has rolled over | The new day's `NotStarted`; the lobby re-reads |
| `submit` fails | An exception; the ViewModel keeps the answers and offers to send them again (already built) |
| `submit` again after it had in fact arrived | The stored result |
| Not subscribed | A distinct error the ViewModel never sees, because a free user's device never calls (all-screens §15.3); if it does, 403 `not_subscribed` |

## 7. Boards

`GET /v1/leaderboard/daily/{date}` and `GET /v1/leaderboard/streaks` each return the first
fifty rows, the caller's own row when they have one, and the number of ranked players — the
shape of `Leaderboard`. The caller's row is in the same response so that "your place" cannot
disagree with the board (all-screens §4.4).

The streak board's score is consecutive UTC days with a completed daily, ending today or
yesterday. It is maintained on submit, not counted on read.

Boards are for subscribers; a free account gets 403 `not_subscribed` and, as with the daily,
its device never asks.

## 8. Subscriptions

### 8.1 The server is the only authority

The app's store SDK completes a purchase and hands the proof to the server. The server
verifies it with the store and records the subscription against the account. Only then does
`GET /v1/entitlements` say `subscribed: true`, and only that makes the app unlock anything.
A client that merely claims to have paid gains nothing.

| Store | Proof the app sends | How the server verifies | How it hears of changes |
|---|---|---|---|
| Google Play | purchase token, product id | Play Developer API (`purchases.subscriptionsv2.get`), service account | Real-time developer notifications, pushed to `/v1/webhooks/google` |
| App Store | the signed transaction (JWS) | Signature against Apple's root certificates; App Store Server API for status | Server notifications v2, to `/v1/webhooks/apple` |

A subscription row holds the store, the product, the store's own id for it, its state and
its expiry. It is keyed by the store's id, so the same purchase presented twice — or by two
accounts — resolves to one row and one owner. A purchase already owned by another account is
a restore on a new anonymous account: the server answers with what §5.3 case 3 would do and
tells the client to sign in, rather than moving a subscription silently.

Webhooks are verified (Google's push token, Apple's JWS) and are idempotent. A daily job
re-checks every subscription that expires within a day, so a missed notification cannot
leave someone locked out or let in for long.

### 8.2 What is gated

Per design §29: the daily quiz, both boards, and quiz topics outside the skeleton. The
server gates the first two. Topic quizzes run on the device and are gated there by
`Entitlements.allows`; a determined user can unlock them locally, and nothing the server
could do would change that for an offline feature.

### 8.3 What is still undecided

Prices and trial length (ticket 3C91). The server needs only product ids, which are
configuration; the prices shown come from the store SDK on the device. `GET /v1/plans`
returns the product ids on sale and their period.

## 9. Packs

### 9.1 Layout

```
<bucket>/
  index.json                          every pack on offer
  ATTRIBUTION.md                      Z-Anatomy, CC BY-SA 4.0; Wikipedia extracts, CC BY-SA 3.0
  packs/<pack-id>/<version>/pack.glb
  packs/<pack-id>/<version>/manifest.json
```

A version's files are never rewritten. `index.json` is the only file that changes, and it is
the last thing a publish writes, so a client never reads an index that points at a file not
yet there.

Each index entry: id, version, labels by locale, byte size, SHA-256, and the two URLs. That
is `PackState` less its status. The bucket is public and listed in the app's attribution
screen, which is what CC BY-SA asks for (design §29).

### 9.2 The client

`CdnPackRepository` reads the index, and for a download:

- writes to a temporary file, asking for the bytes it does not yet have (`Range`), so an
  interrupted download resumes;
- verifies the SHA-256 of the whole file;
- installs the manifest into the database and moves the file into place in one step, so a
  partial or corrupt pack is never visible to the renderer (design §10);
- on a checksum failure deletes what it has and reports `CHECKSUM`, not resumable.

This is ticket C010. It depends on nothing in §5 to §8 and can be built first.

### 9.3 Publishing

`pipeline publish <pack>` uploads the version's two files, verifies them by reading them
back, then rewrites the index. It refuses to overwrite an existing version.

## 10. Content on the server

The server generates the daily, so it needs the skeleton's graph, its names and what has
been verified. It gets them the way the app does: `backend ingest <manifest.json>` reads a
pack manifest into `structure` and `structure_text`, and `backend verify <file>` reads
verifications into `structure_verification`, each with the hash of the name it covers
(design §24.1). A verification whose hash no longer matches the name is not counted — the
rule the app's database already applies.

**Where the verification file comes from is the reviewer tool's design (ticket 4082), which
does not exist.** This document fixes only what the server reads: structure id, locale,
state, the name's hash, when, and by whom.

## 11. Running it

### 11.1 The box

One VPS. Docker Compose with three services: `caddy` (TLS from Let's Encrypt, the only thing
listening publicly), `backend`, and `postgres` with its data on a named volume. The backend
container runs Flyway on start and refuses to serve if a migration fails.

Configuration is environment variables from a file on the server that is not in the
repository: database credentials, the Google and Apple client ids, the Play service account,
the App Store key, the object-storage keys for backups.

### 11.2 What "ours to do" comes to

- **Backups.** Nightly `pg_dump`, encrypted, to a private bucket; fourteen kept. A restore
  into a scratch database is run and timed before launch and once a quarter. A backup never
  restored is a hope.
- **Updates.** Unattended security updates for the OS; images rebuilt and redeployed monthly
  and on any advisory for the JVM, Ktor or PostgreSQL.
- **Watching.** `GET /health` checks the database; an external uptime check calls it every
  minute and sends an email. Logs are structured, kept on disk for fourteen days, and never
  contain a token, an ID token or a purchase token.
- **Deploying.** An image is built and pushed to a registry; the server pulls it and
  restarts the one container. A deploy during play costs a retried request, which every
  client call in §6 is written to survive.

### 11.3 What is deliberately absent

No second server, no load balancer, no queue, no cache. Design §11 says scaling is not a v1
concern; a daily quiz for a few thousand students is tens of requests a second at its peak.
The first thing that would change this is the box being down when the daily turns over at
00:00 UTC, and the answer to that is a managed database, not more machinery.

## 12. Testing

Per design §15, with real PostgreSQL through Testcontainers — no in-memory stand-in, because
ranking, uniqueness and the merge are things the database does.

- **Daily integrity:** a second start returns the first start's clock; a second submit
  returns the first result; a submit faster than the floor is stored unranked; an untimed
  attempt is unranked; a submit after the cut-off is unranked; two accounts submitting at
  once are both recorded.
- **Ranking:** correctness before time before submission order; the caller's row agrees with
  the board; an unranked attempt appears on no board.
- **The same set everywhere:** a golden test in `core-quiz` runs on the JVM, Android and iOS
  and pins one seed's set. The server's stored set for a date equals what the generator
  gives for that date's seed.
- **Identity:** a token's hash is what is stored; a revoked token is refused; each of
  §5.3's three cases; a merge where both accounts played the same day; account deletion
  leaves no row behind.
- **Subscriptions:** verified against recorded store responses, good and bad; a webhook
  delivered twice changes nothing the second time; a purchase presented by a second account.
- **Client against server:** `core-network`'s repositories run against the real server
  in-process on the JVM, through the same `api-contract` types. This is the test that would
  have caught a field renamed on one side.
- **Packs:** resume after an interruption, a checksum failure, the atomic swap, an index
  that lists a version the client already has.

## 13. What this does not do

- **Sync study progress between devices** (§4.4).
- **`GET /structures/delta`** from design §11. Names reach the device in packs; a separate
  metadata channel has nothing to carry until verification edits need to arrive faster than
  a pack does.
- **Attestation** (Play Integrity, App Attest) and outlier detection. Design §9.1 defers them
  to v2.
- **Cohort and university boards** (design §9.3).
- **A moderation tool.** v1 has a blocklist, a report table and a person.

## 14. Risks

1. **The daily has nothing to ask until the skeleton is verified.** Everything here can be
   built and tested on fixture verifications, and none of it can launch without the real
   ones. The reviewer tool was moved to the lowest priority on 2026-10-11; this document is
   the reason it will have to come back.
2. **Store verification is the least familiar code in the project and the most expensive to
   get wrong.** A bug lets people in free or locks out people who paid. It is tested against
   recorded responses, and the nightly re-check is the safety net for a missed notification.
3. **One box.** An outage at midnight UTC costs that day's daily for whoever was playing.
   Accepted for v1 (§11.3), and the reason every client call is idempotent.
4. **The merge.** It deletes an account. It is one transaction, logged, and tested with both
   accounts having played the same day — the case most likely to be got wrong.
5. **Sign in with Apple needs a paid developer account and a configured service id**, which
   the project does not have yet (the same blocker as the iPhone run, ticket 8119).

## 15. Order of work

The implementation plan is `docs/superpowers/plans/2026-10-11-backend.md`. In outline:

1. Packs: the publish step and `CdnPackRepository`. Independent of the server.
2. Shared modules: `core-quiz` with a JVM target, `api-contract`.
3. The server's skeleton: Compose, Flyway, health, anonymous accounts.
4. Content ingest, the daily, the boards.
5. The client's network repositories, and the dependency seam swapped.
6. Sign-in and the merge.
7. Subscriptions.
8. The box: backups, the restore drill, the uptime check, the deploy.
