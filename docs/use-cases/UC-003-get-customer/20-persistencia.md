# UC-003 · Persistence

> Partial of `docs/use-cases/UC-003-get-customer/`. Owner: `persistence-architect`.
> Inherits the aggregate and ports from `10-dominio.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/persistence-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| REST partial | `30-rest.md` — block 4 schema requirements: none |
| Aggregate | `Customer` — REUSE |
| Engine | PostgreSQL 16 — REUSE, fixed by UC-001 |
| Expected volume at 12 months | not given (UC-001 neither); irrelevant here — a primary-key lookup costs the same at any volume |
| Divergences from `10-dominio.md` | none |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

## 1 · Schema

No change. Table `customers` — REUSE, as `V1__create_customers.sql` created it (UC-001): `id
uuid` PK, `name varchar(120)`, `security_number char(11)` `UNIQUE`, `birth_date date`,
`registered_at timestamptz`, `version bigint`.

**Indexes:** no new one. The lookup by id is served by `pk_customers`, created by the primary
key constraint.

**Personal data at rest** — `@.claude/rules/personal-data.md` § At rest. No new column; the
table's record stands as UC-001 wrote it:

| Column | Holds | Retention | Why it is stored at all |
|---|---|---|---|
| `security_number` | national identification number | life of the customer record; erasure is `BL-02` | REUSE — UC-001's business key |
| `birth_date` | birth date | life of the customer record; erasure is `BL-02` | REUSE — UC-001's age rule |

This case reads both and stores nothing new.

## 2 · Mapping

No change. `CustomerEntity` ↔ `Customer` through `CustomerPersistenceMapper` — REUSE.
`toDomain(CustomerEntity)` already rebuilds the aggregate with `Customer.rehydrate(...)`, which
is exactly what the lookup returns.

## 3 · Adapter and ports

| Port | Signature (from `10-dominio.md`) | Query | Fetch | Index serving it | State |
|---|---|---|---|---|---|
| `CustomerRepository` | `boolean existsBySecurityNumber(SecurityNumber)` | `SELECT EXISTS` by `security_number` | — | `uq_customers_security_number` | REUSE |
| `CustomerRepository` | `Customer save(Customer)` | `INSERT` by PK, `saveAndFlush` | — | `pk_customers` | REUSE |
| `CustomerRepository` | `Optional<Customer> findById(CustomerId id)` | `SELECT` by PK — `CustomerJpaRepository.findById(UUID)`, inherited from `JpaRepository` | entity has no association, nothing to fetch | `pk_customers` | **NEW** |

`CustomerRepositoryJpaAdapter.findById` (CHANGE):
`repository.findById(id.value()).map(CustomerPersistenceMapper::toDomain)`. No
`@Transactional` on the adapter — the use case owns the read-only transaction
(`@.claude/rules/persistence.md` § Boundary). No exception translation: a read raises no
integrity violation, and absence is `Optional.empty()`, never an exception.

`CustomerJpaRepository` — REUSE, unchanged: `findById` comes from `JpaRepository`, no derived
query added.

No port returns a collection: no pagination, no `JOIN FETCH`, no N+1 to resolve.

**Concurrency:** none — a read. `@Version` stays as UC-001 left it, irrelevant here.

## 4 · Migrations

none — no table, column or index changes. `V1` and `V2` are untouched; no `V3` is created.

## 5 · Configuration

No change. Pool, timeouts, query timeout, UTC and Flyway validation as UC-001 decided
(`docs/use-cases/UC-001-create-customer/20-persistencia.md` § 5). `open-in-view: false` already
holds, so the mapping to the DTO happens on the rehydrated aggregate, after the transaction —
no lazy association exists to trip on.

## 6 · Declared dependencies

none — `spring-boot-starter-data-jpa` and the PostgreSQL driver are already in `pom.xml`.

**Compose service:** `postgres` already declared in `docker-compose.yml` — no pending service.

## Design patterns

none — no force in the spec, no symptom on disk: one inherited lookup and one mapping call on
an existing adapter.

## Deferred

none — erasure of `security_number` and `birth_date` is already `BL-02`, owned by the backlog
since UC-001; this case adds no new personal data at rest.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerRepositoryJpaAdapter` implements the new `findById(CustomerId)` of `CustomerRepository` | the port gained the method in `10-dominio.md`; UC-001's `existsBySecurityNumber` and `save` are untouched |

## Implementation order

1. `CustomerRepositoryJpaAdapter.findById` — after `CustomerRepository` gains the method
   (`10-dominio.md`)
2. Integration test against the real database (Testcontainers) — `40-testes.md`
