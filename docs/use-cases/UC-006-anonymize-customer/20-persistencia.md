# UC-006 · Persistence

> Partial of `docs/use-cases/UC-006-anonymize-customer/`. Owner: `persistence-architect`.
> Inherits the aggregate and ports from `10-dominio.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/persistence-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Other partials read | `30-rest.md` § 4 (schema requirements: none), `32-seguranca.md` § 7 (none) |
| Aggregate | `Customer` (REUSE) |
| Engine | PostgreSQL 16 — `postgres:16-alpine` (REUSE, `UC-001`) |
| Expected volume at 12 months | not material here: one `UPDATE` by primary key per call, no listing |
| Divergences from `10-dominio.md` | none |

## 1 · Schema

**Table `customers`** — REUSE, altered (expand only). Columns after this case; changes in bold.

| Column | Type | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | `uuid` | no | — | PK |
| `name` | `varchar(120)` | no | — | — (holds `ANONYMIZED` once anonymized) |
| `security_number` | `char(11)` | **yes** (was no) | — | `UNIQUE (security_number)` — unchanged. PostgreSQL treats `NULL`s as distinct, so any number of anonymized rows coexist, and the number is free for a new registration (`00-caso-de-uso.md` § Out of scope) |
| `birth_date` | `date` | **yes** (was no) | — | — |
| `registered_at` | `timestamptz` | no | — | — |
| `status` | `varchar(20)` | no | — | `ck_customers_status` — unchanged |
| **`anonymized_at`** | **`timestamptz`** | **yes** | **`NULL`** | **NEW** — filled by the application's `Clock`; `NULL` = identified |
| `version` | `bigint` | no | `0` | optimistic lock — unchanged |

**New constraint `ck_customers_identity`** — the domain's sealed `CustomerIdentity` held by the
database too, so no write path (a script, a future adapter) can leave a half-anonymized row:

```
(anonymized_at IS NULL     AND security_number IS NOT NULL AND birth_date IS NOT NULL)
OR
(anonymized_at IS NOT NULL AND security_number IS NULL     AND birth_date IS NULL AND name = 'ANONYMIZED')
```

Mirrors `10-dominio.md` invariants 1–3. Every existing row satisfies the first branch.

**Indexes** — none added. The case reads by primary key (`pk_customers`). No query filters by
`anonymized_at`; an index on it would be write cost with no reader.

**Personal data at rest** — `@.claude/rules/personal-data.md` § At rest.

| Column | Holds | Retention | Why it is stored at all |
|---|---|---|---|
| `customers.security_number` | national identification number | **until anonymized** (was: life of the record — `UC-001`); then `NULL` | business key while the customer is identified |
| `customers.birth_date` | birth date | **until anonymized**; then `NULL` | `UC-001`'s age rule; sent to KYC |
| `customers.name` | full name | **until anonymized**; then the placeholder `ANONYMIZED` | identifies the customer |
| `customers.anonymized_at` | an instant — no personal data | life of the record | proves when the erasure happened; the only trace kept (user's answer: time, not who) |

Copies this partial does not erase, stated so nobody assumes otherwise:

- `outbox_events.payload` — the KYC request (`UC-004`) holds name, security number and birth
  date; published rows are pruned after `app.outbox.prune-after: P7D`. A **dead-lettered** row is
  never pruned (`UC-004` `20-persistencia.md`) and keeps the values until `BL-04` resets it and it
  publishes, then 7 days more.
- The old row versions: PostgreSQL's `UPDATE` leaves the previous tuple in the heap until
  autovacuum reclaims it, and in WAL and backups for their own retention. Those are operations'
  retention windows, outside this project — named here because they are copies.

## 2 · Mapping

Aggregate `Customer` → entity `CustomerEntity` in `infrastructure/persistence/customer/`
(REUSE, CHANGE). Package-private, as today (`@.claude/rules/persistence.md` § Boundary).

| Aggregate field | Domain type | Column(s) | Translation |
|---|---|---|---|
| `id` | `CustomerId` | `id` | unchanged |
| `name` | `String` | `name` | unchanged — now updatable by the anonymization path |
| `identity` | `CustomerIdentity.Identified` | `security_number`, `birth_date`; `anonymized_at` = `NULL` | `toEntity`: `securityNumber().value()`, `birthDate()`. `toDomain`: `anonymized_at IS NULL` → `CustomerIdentity.identified(SecurityNumber.of(securityNumber), birthDate)` |
| `identity` | `CustomerIdentity.Anonymized` | `anonymized_at`; `security_number`, `birth_date` = `NULL` | `toEntity`: `anonymizedAt()`, both others `null`. `toDomain`: `anonymized_at` not `NULL` → `CustomerIdentity.anonymized(anonymizedAt)` |
| `registeredAt` | `Instant` | `registered_at` | unchanged |
| `status` | `CustomerStatus` | `status` | unchanged |
| — | — | `version` | entity-only, `@Version` |

`CustomerEntity` changes:

| Member | Change |
|---|---|
| `securityNumber` | `@Column(nullable = true, updatable = true)` — keeps `@JdbcTypeCode(SqlTypes.CHAR)`, length 11 |
| `birthDate` | `@Column(nullable = true, updatable = true)` |
| `anonymizedAt` | NEW — `@Column(name = "anonymized_at")` `Instant`, nullable |
| constructor | gains `Instant anonymizedAt` |
| `anonymize(String placeholderName, Instant at)` | NEW — sets `name`, `anonymizedAt`, and `securityNumber` and `birthDate` to `null`. The only mutation besides `changeStatus` |

The `null`s live in the entity only; the mapper turns them into the sealed type before anything
leaves the package.

## 3 · Adapter and ports

| Port | Signature (from `10-dominio.md`) | Query | Fetch | Index serving it |
|---|---|---|---|---|
| `CustomerRepository.findById` | `Optional<Customer> findById(CustomerId id)` | `SELECT` by PK — REUSE | — | `pk_customers` |
| `CustomerRepository.save` | `Customer save(Customer customer)` | load-and-mutate `UPDATE` by PK, `version` checked — **CHANGE** | — | `pk_customers` |
| `CustomerRepository.existsBySecurityNumber` | unchanged | `SELECT EXISTS … WHERE security_number = ?` — REUSE; never matches an anonymized row | — | `uq_customers_security_number` |

**`CustomerRepositoryJpaAdapter.save` — CHANGE.** The existing-row branch today applies only
`changeStatus`. It now applies the whole mutable state of the aggregate:

1. `existing.changeStatus(customer.status())` — as today;
2. when `customer.identity()` is `Anonymized` and `existing.getAnonymizedAt()` is `null`:
   `existing.anonymize(customer.name(), anonymized.anonymizedAt())`.

An identified aggregate never un-anonymizes a row: the sealed type has no way back
(`10-dominio.md` State reachability), and the adapter has no path that writes the values back.

**Concurrency:** REUSE — `@Version` on the row. A concurrent KYC verdict (`UC-005`) or a second
anonymization makes the later flush fail with `ObjectOptimisticLockingFailureException`, which
the adapter already translates to `ConflictException` `CUSTOMER_CONCURRENT_UPDATE`. The
`saveAndFlush` keeps the failure inside the adapter's `try`.

**Transaction:** `AnonymizeCustomerUseCase.anonymize` owns it; the adapter has none
(`@.claude/rules/persistence.md` § Boundary).

## 4 · Migrations

| File | Type | Content |
|---|---|---|
| `V5__anonymize_customers.sql` | expand | `security_number` and `birth_date` nullable, `anonymized_at` added, `ck_customers_identity` added `NOT VALID` then validated |

The table exists in production, so only expand steps: dropping `NOT NULL` and adding a
nullable column are metadata-only in PostgreSQL 16. The check is added `NOT VALID` (no scan
under the `ACCESS EXCLUSIVE` lock) and validated in a second statement, which scans under
`SHARE UPDATE EXCLUSIVE` and does not block reads or writes. No contract step: nothing is
removed. The executor re-checks `<N>` against the migrations on disk — `V4__create_outbox_events.sql`
is the highest today.

**`src/main/resources/db/migration/V5__anonymize_customers.sql`**

```sql
-- UC-006: a customer can be anonymized. security_number and birth_date (personal data) become
-- nullable and are erased on anonymization; name becomes the placeholder 'ANONYMIZED';
-- anonymized_at records when (UC-006 20-persistencia.md § 1). The row and its id stay.
ALTER TABLE customers
    ALTER COLUMN security_number DROP NOT NULL,
    ALTER COLUMN birth_date DROP NOT NULL,
    ADD COLUMN anonymized_at timestamptz NULL;

ALTER TABLE customers
    ADD CONSTRAINT ck_customers_identity CHECK (
        (anonymized_at IS NULL
            AND security_number IS NOT NULL
            AND birth_date IS NOT NULL)
        OR
        (anonymized_at IS NOT NULL
            AND security_number IS NULL
            AND birth_date IS NULL
            AND name = 'ANONYMIZED')
    ) NOT VALID;

ALTER TABLE customers
    VALIDATE CONSTRAINT ck_customers_identity;
```

No earlier migration is edited. `V1__create_customers.sql`'s comment "Retention: life of the
customer record" stays as history; this migration's comment supersedes it.

## 5 · Configuration

none — no property changes. Pool, timeouts and JPA settings of `UC-001` serve one more
single-row `UPDATE`. `ddl-auto: validate` (rule) checks the new column against the entity at
boot.

## 6 · Declared dependencies

none — `spring-boot-starter-data-jpa`, `spring-boot-starter-flyway`, `flyway-database-postgresql`
and `postgresql` are already in `pom.xml`.

## Compose services

| Service | Image | Decision | Detail |
|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `exists` | listed in `docker-compose.yml`'s `services:` |

## Deferred

none

## Design patterns

none — no force in the spec, no symptom on disk. One more mutation on an existing entity and a
two-way branch in the mapper over a two-variant sealed type.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `customers.security_number` and `customers.birth_date` become nullable; `customers.anonymized_at` and `ck_customers_identity` added; `CustomerEntity` gains `anonymizedAt` and loses `updatable = false` on the two personal-data columns | anonymization erases them in place |
| `UC-001-create-customer` | `CustomerPersistenceMapper` reads and writes `CustomerIdentity` instead of the two fields | `10-dominio.md` § 1 replaced the record components |
| `UC-001-create-customer`, `UC-005-record-kyc-verdict` | `CustomerRepositoryJpaAdapter.save` applies the anonymization on the update path, besides the status | the update path only knew status changes |
| `UC-001-create-customer` | retention of `security_number`, `birth_date`, `name` changes from "life of the record" to "until anonymized" — the Deferred row of `UC-001` `20-persistencia.md` § Deferred (customer personal data) is closed by this case | `BL-02` retired |

## Implementation order

1. `V5__anonymize_customers.sql`
2. `CustomerEntity` (column changes, `anonymizedAt`, `anonymize`)
3. `CustomerPersistenceMapper` (sealed `CustomerIdentity` both ways)
4. `CustomerRepositoryJpaAdapter.save` (update path)
5. Integration test against the real database (Testcontainers) — `40-testes.md`
