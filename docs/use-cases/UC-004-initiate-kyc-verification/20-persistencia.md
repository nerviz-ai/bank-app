# UC-004 · Persistence

> Partial of `docs/use-cases/UC-004-initiate-kyc-verification/`. Owner: `persistence-architect`.
> Inherits the aggregate and ports from `10-dominio.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/persistence-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Requirement lists read | `30-rest.md` § 4 (none) · `25-mensageria.md` § 6 (`outbox_events`) · `35-jobs.md` § 3 (more than one replica) and § 6 (leased/locked claim, oldest-pending read, bounded prune, retention value) |
| Aggregate | `Customer` (CHANGE) |
| Engine | PostgreSQL 16 — inherited from `UC-001` |
| Expected volume at 12 months | `customers`: unchanged from `UC-001`. `outbox_events`: one row per customer created, pruned after 7 days — a few thousand live rows at most |
| Divergences from `10-dominio.md` | none |
| Requirement on `25-mensageria.md` | producer `max.block.ms` bounded to `10000` (§ 1 · Claim strategy) — the lease must outlive the worst-case pass, and the client default (60 s) blocks that long on metadata when the broker is down |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.
Existing migrations: `V1__create_customers.sql`, `V2__create_idempotency_keys.sql`. No
`outbox_events` on disk — NEW here, shared by every later event.

## 1 · Schema

**Table `customers`** — CHANGE: one column added.

| Column | Type | Nullable | Default | Constraint |
|---|---|---|---|---|
| `status` | `varchar(20)` | no | none after the migration (the backfill uses `'ACTIVE'` once, then the default is dropped) | `ck_customers_status CHECK (status IN ('KYC_IN_PROGRESS','ACTIVE','REJECTED_BY_KYC'))` |

Every existing row becomes `ACTIVE` (user's answer, `00-caso-de-uso.md`). The default is dropped
in the same migration so that no insert can silently take a status: the application always
writes it (`Customer.register` sets `KYC_IN_PROGRESS`). On PostgreSQL 16, `ADD COLUMN … NOT NULL
DEFAULT <constant>` is metadata-only — no table rewrite. The `CHECK` keeps an unknown value out
of the table, so the mapping never meets one on read.

**Rolling deploy note.** Between the migration and the last old pod stopping, an old pod's insert
omits `status` and fails on `NOT NULL` (500, rolled back, retryable with the same
`Idempotency-Key`). Accepted: the alternative — keeping a default — would let an old pod create a
customer with no KYC request, which is the state this case exists to prevent.

No index on `status`: no query filters by it in this case (`BL-03` updates by primary key).

**Table `outbox_events`** — NEW, shared, one for the whole project (step 4b, from
`templates/OutboxEventTable.sql.example`, with two columns added for the guarantees below).

| Column | Type | Nullable | Default | Constraint / note |
|---|---|---|---|---|
| `event_id` | `uuid` | no | — | PK — assigned by `OutboxAppender` (UUID v7), the receiver's dedupe key |
| `aggregate_id` | `varchar(200)` | no | — | partition key source (`customerId` text) |
| `event_type` | `varchar(120)` | no | — | resolved to a topic by `TopicResolver` |
| `payload` | `jsonb` | no | — | JSON text written by `OutboxAppender` — **personal data in clear** |
| `occurred_at` | `timestamptz` | no | — | from the event |
| `published_at` | `timestamptz` | yes | `null` | null = pending |
| `attempts` | `smallint` | no | `0` | bounded by `app.outbox.max-attempts` |
| `next_attempt_at` | `timestamptz` | no | — | **added** — per-row exponential backoff (`25-mensageria.md` § 4); set to `occurred_at` on append |
| `claimed_until` | `timestamptz` | yes | `null` | **added** — lease (claim strategy below) |
| `last_error` | `text` | yes | `null` | truncated to 1000 chars by the adapter |
| `dead_lettered` | `boolean` | no | `false` | exhausted rows; never pruned |

**Indexes**

| Index | Columns | Why |
|---|---|---|
| `pk_outbox_events` | `event_id` | PK |
| `ix_outbox_events_pending` | `(next_attempt_at, occurred_at)` `WHERE published_at IS NULL AND NOT dead_lettered` | serves the claim alone — pending rows only, leaves the index on publish or dead-letter |
| `ix_outbox_events_published_at` | `published_at` `WHERE published_at IS NOT NULL` | serves the prune |

**Claim strategy — `outbox_events`**

| Decision | Value |
|---|---|
| Strategy | **Lease column** — `claimed_until`, set in one `UPDATE … WHERE event_id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING …` |
| Why | `35-jobs.md` § 3: more than one replica, no lock around the trigger; § 6 requires a locked or leased claim. The lease commits immediately, so sends happen outside any row lock, and a crashed instance's rows return after the lease — `FOR UPDATE SKIP LOCKED` alone would hold the transaction open across up to 10 broker sends |
| Lease length | `app.outbox.lease = PT8M` — outlives the worst-case pass: `batch-size` (10) × (`max.block.ms` 10 s + `delivery.timeout.ms` 30 s) = 400 s, plus margin. Requires `max.block.ms = 10000` on the producer (requirement on `25-mensageria.md`, header) |
| What it costs | a row whose instance dies mid-pass waits up to 8 min before another instance claims it; one write per claimed row |
| Who absorbs the duplicate | the KYC application — idempotency **unknown** (`25-mensageria.md` § 3). Duplicates arise from a send that succeeded with a mark that failed, or a lease that expired mid-send |
| Name honesty | `claimPending` claims: the lease is written before it returns |

**Pacing the columns encode** — values this partial owns (`app.outbox.*`):

| Value | Setting | Guarantee it delivers (`25-mensageria.md` § 4) |
|---|---|---|
| batch size | `10` | bounds one pass and the lease |
| attempt ceiling | `10` (`max-attempts`) | then `dead_lettered = true`, row no longer claimed |
| backoff | on failure `next_attempt_at = now + min(backoff-base × 2^(attempts−1), backoff-max)`, `backoff-base = PT1S`, `backoff-max = PT5M`; `claimed_until` cleared | per-row exponential backoff; other rows keep flowing |
| retention | **`P7D`** (`app.outbox.prune-after`) — user's answer | published rows kept a week to reconcile a KYC delivery complaint against the rows themselves; accepted with the payload's personal data in mind |
| prune batch | `500` (`prune-batch-size`) | bounded delete |

**Personal data at rest**

| Column | Holds | Retention | Why it is stored at all |
|---|---|---|---|
| `customers.status` | KYC status — not personal data under § Masking candidates | life of the record | — |
| `outbox_events.payload` | `name`, `securityNumber`, `birthDate` of the customer, in clear | **7 days after publish** (pending and dead-lettered rows until resolved) | the KYC application needs the real values (`25-mensageria.md` § 8); the outbox holds the message until it is delivered |
| `outbox_events.last_error` | broker error text — must never contain the payload | as the row | adapter writes the exception message only, truncated |

## 2 · Mapping

**`infrastructure/persistence/customer/CustomerEntity`** — CHANGE

| Field | Column | Mapping |
|---|---|---|
| `status` (`CustomerStatus`) | `status` | `@Enumerated(EnumType.STRING)`, `@Column(name = "status", nullable = false, length = 20)` — updatable (`BL-03` changes it) |

The constructor gains `status`. Enum constant names are the column values — renaming a constant
is a migration.

**`infrastructure/persistence/customer/CustomerPersistenceMapper`** — CHANGE: `toEntity` passes
`customer.status()`; `toDomain` passes `entity.getStatus()` to `Customer.rehydrate(…, status)`.

**`infrastructure/persistence/outbox/OutboxEventEntity`** — NEW, extends `AssignedIdEntity<UUID>`
(REUSE). `payload` `@JdbcTypeCode(SqlTypes.JSON)` on `String`; `attempts`
`@JdbcTypeCode(SqlTypes.SMALLINT)` on `int`; `last_error` plain `String` with
`columnDefinition` not needed (`text` validates as `varchar` in Hibernate 7 — no `@Lob`).
Package-private.

## 3 · Adapter and ports

**`CustomerRepository`** — REUSE; `CustomerRepositoryJpaAdapter` unchanged except through the
mapper. `save` writes `status`; `findById` reads it.

**`OutboxRelayGateway`** (declared by `25-mensageria.md`) and **`OutboxRetentionGateway`**
(declared by `35-jobs.md`) — both implemented by
`infrastructure/persistence/outbox/OutboxEventStore` (NEW, `public` only as Spring wiring
requires), over `OutboxEventJpaRepository` (Spring Data, package-private, native queries):

| Operation | Query | Transaction |
|---|---|---|
| append(record) | `INSERT` with `next_attempt_at = occurred_at` | joins the caller's (`MANDATORY` — fails loudly if called outside one) |
| claimPending(limit, now, lease) | `UPDATE outbox_events SET claimed_until = :now + :lease WHERE event_id IN (SELECT event_id FROM outbox_events WHERE published_at IS NULL AND NOT dead_lettered AND next_attempt_at <= :now AND (claimed_until IS NULL OR claimed_until < :now) ORDER BY next_attempt_at, occurred_at LIMIT :limit FOR UPDATE SKIP LOCKED) RETURNING *`, then sorted by `occurred_at` | own, `REQUIRES_NEW`, commits before sends |
| markPublished(eventId, now) | `UPDATE … SET published_at = :now, claimed_until = NULL WHERE event_id = :id` | own |
| recordFailure(eventId, error, now, maxAttempts, base, max) | `attempts = attempts + 1`, `last_error`, `claimed_until = NULL`, `next_attempt_at` per § 1 backoff, `dead_lettered = (attempts + 1 >= :maxAttempts)` | own |
| oldestPendingAge(now) | `SELECT min(occurred_at) … WHERE published_at IS NULL AND NOT dead_lettered` → `Optional<Duration>` | read-only |
| deletePublishedBefore(cutoff, limit) | `DELETE FROM outbox_events WHERE event_id IN (SELECT event_id FROM outbox_events WHERE published_at < :cutoff ORDER BY published_at LIMIT :limit FOR UPDATE SKIP LOCKED)` — never pending, never dead-lettered (both have `published_at IS NULL`) | own |

`now` comes from the application `Clock` passed by the use case, not `now()` in SQL, so tests
control it. Any `DataAccessException` is translated before crossing the port — no framework
exception leaves the adapter (`@.claude/rules/error-handling.md`); a failure in `append`
propagates and rolls `CreateCustomerUseCase` back.

## 4 · Migrations

Next numbers after `V2`. Two logical changes, two files.

`src/main/resources/db/migration/V3__add_customers_status.sql`

```sql
-- UC-004: customers gain a KYC status. Existing rows are grandfathered as ACTIVE
-- (user's decision); the default is dropped so every later insert states its status.
ALTER TABLE customers
    ADD COLUMN status varchar(20) NOT NULL DEFAULT 'ACTIVE';

ALTER TABLE customers
    ALTER COLUMN status DROP DEFAULT;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_status
        CHECK (status IN ('KYC_IN_PROGRESS', 'ACTIVE', 'REJECTED_BY_KYC'));
```

`src/main/resources/db/migration/V4__create_outbox_events.sql`

```sql
-- Shared transactional outbox (Form B) — one table for the whole project.
-- Personal data: payload may hold personal data in clear (UC-004: name, security number,
-- birth date for the KYC application). Retention: published rows pruned after
-- app.outbox.prune-after (P7D) by OutboxPruneJob; pending and dead-lettered rows are kept.
CREATE TABLE outbox_events (
    event_id        uuid          NOT NULL,
    aggregate_id    varchar(200)  NOT NULL,
    event_type      varchar(120)  NOT NULL,
    payload         jsonb         NOT NULL,
    occurred_at     timestamptz   NOT NULL,
    published_at    timestamptz   NULL,
    attempts        smallint      NOT NULL DEFAULT 0,
    next_attempt_at timestamptz   NOT NULL,
    claimed_until   timestamptz   NULL,
    last_error      text          NULL,
    dead_lettered   boolean       NOT NULL DEFAULT false,
    CONSTRAINT pk_outbox_events PRIMARY KEY (event_id)
);

CREATE INDEX ix_outbox_events_pending
    ON outbox_events (next_attempt_at, occurred_at)
    WHERE published_at IS NULL AND NOT dead_lettered;

CREATE INDEX ix_outbox_events_published_at
    ON outbox_events (published_at)
    WHERE published_at IS NOT NULL;
```

## 5 · Configuration

Added to `src/main/resources/application.yml`; existing datasource / JPA / Flyway values stay.

| Property | Value | Why |
|---|---|---|
| `app.outbox.batch-size` | `10` | § 1 pacing |
| `app.outbox.max-attempts` | `10` | § 1 pacing |
| `app.outbox.backoff-base` | `PT1S` | § 1 pacing |
| `app.outbox.backoff-max` | `PT5M` | § 1 pacing |
| `app.outbox.lease` | `PT8M` | § 1 claim strategy |
| `app.outbox.prune-after` | `P7D` | § 1 retention — user's answer |
| `app.outbox.prune-batch-size` | `500` | § 1 pacing |

Bound by `OutboxProperties` (`35-jobs.md` § 8) — the properties record gains `max-attempts`,
`backoff-base`, `backoff-max`, `lease`. Pool size unchanged (10): the relay holds a connection
only for its short claim / mark statements.

## 6 · Declared dependencies

none — `spring-boot-starter-data-jpa`, `postgresql`, `flyway-database-postgresql` already in
`pom.xml`; `jsonb` mapping is Hibernate's own. The Postgres service exists in
`docker-compose.yml` (`postgres:16-alpine`): no pending compose service from this partial (the
pending `kafka` service is `25-mensageria.md` § 6's).

## Design patterns

none — the outbox store follows the mandated exemplar; the customer adapter gains one mapped
field. No skeleton repeated across adapters.

## Deferred

none — the prune ships with its job (`35-jobs.md`); dead-letter re-drive is `25-mensageria.md`
§ 9's row.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `customers` gains `status` (`V3`), existing rows `ACTIVE`; `CustomerEntity` and `CustomerPersistenceMapper` map it | `10-dominio.md` adds `status` to `Customer` |
| `UC-003-get-customer` | `findById` returns the status through the same mapper — no query change | same |
