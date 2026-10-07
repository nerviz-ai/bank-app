# UC-002 · Persistence

> Partial of `docs/use-cases/UC-002-prune-expired-idempotency-keys/`. Owner: `persistence-architect`.
> Inherits the port from `10-dominio.md` and the coordination requirement from `35-jobs.md` —
> doesn't reinvent them. Contains no code beyond the statement below.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` — `IdempotencyKeyPort.deleteExpired(Instant cutoff, int limit)` |
| REST partial | `30-rest.md` — block 4 schema requirements: none |
| Jobs partial | `35-jobs.md` — § 3: more than one replica, no lock; § 6: bounded delete that skips rows a concurrent transaction holds |
| Aggregate | none — `idempotency_keys` is the shared application table `UC-001` created |
| Engine | PostgreSQL 16 — inherited from `UC-001` (`postgres` service in `docker-compose.yml`, `TestcontainersConfiguration`) |
| Expected volume | one row per `Idempotency-Key` call, 24 h TTL — the live set is one day of creation calls. Not asked: the bounded delete's shape does not depend on it |
| Divergences from `10-dominio.md` | none |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`. Survey:
`V1__create_customers.sql`, `V2__create_idempotency_keys.sql`; entities `CustomerEntity`,
`IdempotencyKeyEntity`. `idempotency_keys` exists with `ix_idempotency_keys_expires_at` —
REUSE, not touched again.

No question asked: engine inherited, no new table, the statement's shape is fixed by
`35-jobs.md` § 6, and retention (24 h) is `@.claude/rules/api-rest.md` § Idempotency's,
already written into `expires_at` by `UC-001`.

## 1 · Schema

No change. `idempotency_keys` as `V2__create_idempotency_keys.sql` created it.

| Index | Columns | Why |
|---|---|---|
| `ix_idempotency_keys_expires_at` | `expires_at` | REUSE — serves the range `expires_at < :cutoff` of the bounded delete (§ 3). It was created for this pass in `UC-001` |

**Personal data at rest** — `@.claude/rules/personal-data.md` § At rest.

| Column | Holds | Retention | Why it is stored at all |
|---|---|---|---|
| `idempotency_keys.*` | no personal data (`UC-001` `20-persistencia.md` § 1) | 24 h TTL — **now enforced**: rows past `expires_at` are deleted within one pass interval (`35-jobs.md` § 1: `PT1H`) | Replay of the original response |

**Coordination strategy** — the prune's counterpart of an outbox claim strategy.

| Decision | Value |
|---|---|
| Strategy | `FOR UPDATE SKIP LOCKED` in the sub-select of each bounded delete — no extra column, no lease |
| Why | `35-jobs.md` § 3: more than one replica runs the pass with no lock around the trigger, and § 6 requires the statement to skip rows a concurrent transaction holds. A lease column would add a write per row to rows about to be deleted |
| What it guarantees | two concurrent passes never wait on each other's rows, never deadlock, never fail each other, and never delete a row with `expires_at >= cutoff` — the condition is in the statement (`10-dominio.md` § 3 port contract) |
| What it costs | a row held by another transaction — a concurrent prune, or a `claim`/`reclaim` of the same key in `IdempotencyKeyStore` — is skipped and deleted by the next pass. Accepted |

**Concurrency with the request path.** The prune only reaches rows past `expires_at`, i.e.
24 h after creation, long after their 5-minute `IN_PROGRESS` lease. A request arriving with
such a key races the prune in two ways, both already absorbed: (1) `claim`'s insert collides
before the delete commits → `findById` reads the expired row and replays or reclaims it, as
today; a `reclaim` whose row was deleted meanwhile fails its version check →
`IDEMPOTENCY_KEY_IN_PROGRESS`, and the client's retry inserts fresh. (2) the delete commits
first → the insert succeeds as a new key. Neither path produces a wrong replay.

## 2 · Mapping

No change. `IdempotencyKeyEntity` is not loaded by the prune — the delete is a single native
statement, no entity is hydrated.

## 3 · Adapter and ports

| Port | Signature (from `10-dominio.md`) | Query | Fetch | Index serving it |
|---|---|---|---|---|
| `IdempotencyKeyPort.deleteExpired` | `int deleteExpired(Instant cutoff, int limit)` | native bounded delete, below — one statement per call | none — no row is read into memory | `ix_idempotency_keys_expires_at` (range scan, `LIMIT` stops it early) |

**`IdempotencyKeyJpaRepository`** — CHANGE.

- **Add** `deleteExpiredBatch(Instant cutoff, int limit)`, returning `int`, annotated
  `@Modifying` + `@Transactional` + `@Query(nativeQuery = true)` with this statement:

  ```sql
  DELETE FROM idempotency_keys
  WHERE idempotency_key IN (
      SELECT idempotency_key
      FROM idempotency_keys
      WHERE expires_at < :cutoff
      LIMIT :limit
      FOR UPDATE SKIP LOCKED
  )
  ```

  `@Transactional` on the repository method is what makes each call its own transaction —
  the port's "commits on its own". No `ORDER BY`: the oldest-first order buys nothing when
  every selected row is deleted, and a sort would defeat `LIMIT`'s early stop on the index.
  `@Modifying` without `clearAutomatically` — the call runs with no persistence context of
  its own to clear.

- **Remove** `findByExpiresAtBefore(Instant)`. It has no caller, its Javadoc reserves it for
  "a future cleanup job (`BL-01`)" — this case — and it is the unbounded, entity-loading
  shape `@.claude/rules/scheduling.md` § Execution guarantees forbids. Left in place it is an
  invitation to the wrong pass.

**`IdempotencyKeyStore`** — CHANGE. Implements `deleteExpired(cutoff, limit)`:
`requireNoTransaction("deleteExpired")` — the same guard `claim` and `release` already use —
then returns `repository.deleteExpiredBatch(cutoff, limit)`. No other change; `claim`,
`complete`, `release`, `TTL` and `IN_PROGRESS_LEASE` untouched.

**Timeouts.** The project's query timeout (5 s, `application.yml`) applies to the native
statement. A 1 000-row delete over an indexed range is far under it; a statement that hits it
fails the pass, which `35-jobs.md` § 4 already handles.

## 4 · Migrations

none — no table, column or index is added. `V2__create_idempotency_keys.sql` already created
`ix_idempotency_keys_expires_at`; no migration is edited.

## 5 · Configuration

| Property | Value | Why |
|---|---|---|
| `app.jobs.prune-idempotency-keys.batch-size` | `1000` | Rows carry a UUID, short strings and a `{"id": …}` body: 1 000 per statement keeps each transaction to a few milliseconds of row locks, and `max-batches` (`35-jobs.md` § 8: `100`) gives 100 000 rows per pass — far above one day of creation calls |

Everything else in `application.yml` stays as is.

## 6 · Declared dependencies

none — Spring Data JPA and the PostgreSQL driver are already declared.

Pending compose service: none — `postgres` is in `docker-compose.yml`.

## Design patterns

none — one native statement behind one existing adapter; no adapter skeleton repeated, no
Specification decided upstream.

## Deferred

none

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `IdempotencyKeyJpaRepository`: `deleteExpiredBatch` added, unused `findByExpiresAtBefore` removed. `IdempotencyKeyStore`: `deleteExpired` added. The `idempotency_keys` retention `UC-001` `20-persistencia.md` § 1 deferred ("rows past it are not deleted yet") is enforced | `UC-001` § Deferred row 1 named this job, owner `backlog` → `BL-01` → this case |

## Implementation order

1. `IdempotencyKeyJpaRepository` — add `deleteExpiredBatch`, remove `findByExpiresAtBefore`
2. `IdempotencyKeyStore.deleteExpired`
3. `app.jobs.prune-idempotency-keys.batch-size` in `application.yml`
4. Integration test against the real database (Testcontainers) — `40-testes.md`
