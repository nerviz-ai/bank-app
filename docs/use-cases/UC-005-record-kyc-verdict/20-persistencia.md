# UC-005 · Persistence

> Partial of `docs/use-cases/UC-005-record-kyc-verdict/`. Owner: `persistence-architect`.
> Inherits the aggregate and ports from `10-dominio.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/persistence-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Requirement lists read | `30-rest.md` § 4 (none) · `25-mensageria.md` § 6 (none — state comparison, no dedupe table). No `35-jobs.md` (no Form B, no schedule), no `28-cliente-http.md` (no `external HTTP` port) |
| Aggregate | `Customer` (CHANGE in the domain; the table is unchanged) |
| Engine | PostgreSQL 16 — inherited from `UC-001` |
| Expected volume at 12 months | unchanged from `UC-001` — one `UPDATE` per customer, at most, for the case's lifetime |
| Divergences from `10-dominio.md` | none — `10-dominio.md` § 3 asked this partial to confirm that `save` updates an existing row. **It does not today**: confirmed and fixed in § 3 |
| Requirement on `25-mensageria.md` | `ConflictException` `CUSTOMER_CONCURRENT_UPDATE` (optimistic-lock failure, § 3) is **retryable** — exempted from the not-retryable `DomainException` classification. A retry reloads the row and the aggregate answers duplicate or conflict correctly; sending it straight to the DLQ would park a verdict that a second attempt applies |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.
Existing migrations: `V1__create_customers.sql` … `V4__create_outbox_events.sql`.

## 1 · Schema

none — no table, column, constraint or index changes.

`customers.status` already exists (`V3__add_customers_status.sql`): `varchar(20) NOT NULL`,
`ck_customers_status` admits the three values. `customers.version` already exists and is mapped
with `@Version`. The update is by primary key — no new index.

No dedupe table (`25-mensageria.md` § 3, state comparison). No outbox (no publication).

## 2 · Mapping

`CustomerEntity` — CHANGE, one method:

| Change | Shape | Why |
|---|---|---|
| `void changeStatus(CustomerStatus status)` | package-private mutator on the managed entity | the update path of § 3 mutates the loaded instance; the class keeps `@Getter` only, no setters |

Every other column is `updatable = false` or unchanged (`name` has no mutation in any case). The
`status` column is already updatable. `CustomerPersistenceMapper` — REUSE: `toEntity` stays the
insert path's, `toDomain` unchanged.

## 3 · Adapter and ports

`CustomerRepository` (kind `persistence`) — signature REUSE. Implementation
`infrastructure/persistence/customer/CustomerRepositoryJpaAdapter` — CHANGE in `save`.

**The defect being closed.** Today `save` always builds a fresh entity with
`CustomerPersistenceMapper.toEntity`: `isNew = true` (from `AssignedIdEntity`) and
`version = 0`. For an existing id that is a `persist` → `INSERT` → primary-key violation,
translated to `ConflictException` `CUSTOMER_PERSISTENCE_CONFLICT`. Every verdict would fail.
Building it detached with `isNew = false` instead would `merge` with `version = 0` and
false-conflict on the row's real version — the exact shape `@.claude/rules/persistence.md`
§ Boundary forbids. So `save` becomes load-and-mutate:

| Port method | Query | Fetch | Index | Paginated |
|---|---|---|---|---|
| `findById(CustomerId)` | REUSE — `CustomerJpaRepository.findById` | by PK | PK | no |
| `save(Customer)` — **update path** | `repository.findById(id)` present → `entity.changeStatus(customer.status())` → `repository.saveAndFlush(entity)` → `toDomain` | by PK; inside `RecordKycVerdictUseCase`'s transaction the entity is already in the persistence context from the use case's `findById`, so no second `SELECT` | PK | no |
| `save(Customer)` — **insert path** | `findById` absent → today's `saveAndFlush(toEntity(customer))`, unchanged | — | PK | no |

Cost on the insert path (`UC-001`): one primary-key lookup before the `INSERT`. The
`persist` of a new entity is unchanged, so the primary-key collision still raises.

`saveAndFlush` keeps both failures inside the adapter's `try` (`@.claude/rules/persistence.md`
§ Boundary). Translation:

| Failure | Translated to | `errorCode` |
|---|---|---|
| `DataIntegrityViolationException`, constraint `uq_customers_security_number` | `SecurityNumberAlreadyRegisteredException` | unchanged |
| `DataIntegrityViolationException`, any other | `ConflictException` | `CUSTOMER_PERSISTENCE_CONFLICT` (unchanged) |
| `ObjectOptimisticLockingFailureException` — **NEW** | `ConflictException` | `CUSTOMER_CONCURRENT_UPDATE` |

`CUSTOMER_CONCURRENT_UPDATE` arises only if two verdicts for one customer are applied at once,
which the partition key prevents in normal operation (`25-mensageria.md` § 1); it is retryable
by the requirement in the header. No `@Transactional` on the adapter — the use case owns it.

## 4 · Migrations

none.

## 5 · Configuration

none — datasource and JPA properties unchanged.

## 6 · Declared dependencies

none

## Compose services

| Service | Image | Decision | Detail |
|---|---|---|---|
| `postgres` | as declared | `exists` | — |

## Design patterns

none — no force in the spec, no symptom on disk. One adapter gains an insert/update branch on
presence of the row; that is the repository's `save` contract, not a pattern.

## Deferred

none

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerRepositoryJpaAdapter.save` loads by id first and branches insert/update; the insert path is unchanged, plus one PK lookup | `save` of an existing customer must update without a false conflict (`@.claude/rules/persistence.md` § Boundary) |
| `UC-001-create-customer` | `CustomerEntity` gains `changeStatus`; the adapter translates `ObjectOptimisticLockingFailureException` to `ConflictException` `CUSTOMER_CONCURRENT_UPDATE` | first update path on `customers` |

No row adds a precondition.

## Implementation order

1. `CustomerEntity.changeStatus`
2. `CustomerRepositoryJpaAdapter.save` — load-and-mutate branch, lock-failure translation
3. Integration test: update an existing customer twice in a row (no false conflict) — `40-testes.md`
