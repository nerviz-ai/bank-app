# UC-007 · Persistence

> Partial of `00-use-case.md` and `10-domain.md`. Owner: `persistence-architect`. Family:
> relational (`Storage` row). Engine: PostgreSQL — inherited from the project (`postgres` service,
> `V1`–`V5`), not re-asked. Norms: `@.claude/rules/persistence.md`.

Requirements read: `30-rest.md` § 4 — none (`idempotency_keys` REUSE). `32-security.md` § 7 —
none. No messaging, jobs, HTTP client or search partial applies.

## 1 · Schema

**`accounts`** — NEW.

| Column | Type | Null | Default | Constraint |
|---|---|---|---|---|
| `id` | `uuid` | NOT NULL | — | `pk_accounts` |
| `customer_id` | `uuid` | NOT NULL | — | `fk_accounts_customer` → `customers(id)` |
| `balance` | `numeric(19,2)` | NOT NULL | — | `ck_accounts_balance_non_negative` (`balance >= 0`) |
| `status` | `varchar(20)` | NOT NULL | — | `ck_accounts_status` (`'ACTIVE'`, `'INACTIVE'`) |
| `opened_at` | `timestamptz` | NOT NULL | — | — |
| `version` | `bigint` | NOT NULL | `0` | optimistic lock |

Index `ix_accounts_customer_id` on `customer_id` — serves the foreign key (the `customers` side
never deletes rows: `UC-006` anonymizes in place) and the future "accounts of a customer" read.
Volume: at most a few accounts per customer — the table tracks `customers`; no partitioning.

`version` is there now, though this case only inserts: every later movement (`BL-06`, `BL-07`,
`BL-08`) updates the balance or status concurrently, and adding the column then would be an
expand migration on a live table.

`opened_at`: the domain has no such field. Divergence — persistence-only column, filled by the
adapter from the injected `Clock` on insert, never mapped back into `Account`. It records when
money could first exist in the row, which the business will ask; cost is one column.

**Retention:** life of the customer — no physical delete in any case so far. No personal data.

## 2 · Mapping

`AccountEntity extends AssignedIdEntity<UUID>` (REUSE, assigned UUIDv7 id, `Persistable`).

| Domain | Entity | Note |
|---|---|---|
| `Account.id: AccountId` | `UUID id` | `AccountId.value()` |
| `Account.owner: CustomerId` | `UUID customerId` | id only — no `@ManyToOne`; the aggregates reference each other by id |
| `Account.balance: Money` | `BigDecimal balance`, `@Column(precision = 19, scale = 2)` | `Money.of` on read |
| `Account.status: AccountStatus` | `@Enumerated(STRING)`, length 20 | |
| — | `Instant openedAt`, `updatable = false` | adapter-only |
| — | `@Version long version` | adapter-only |

Mapper `AccountPersistenceMapper`, in the project's mechanism (`@.claude/rules/mapping.md`); reads
through `Account.rehydrate`.

## 3 · Adapter and ports

| Port | Method | Served by | Fetch | Index |
|---|---|---|---|---|
| `AccountRepository` (NEW) | `Account save(Account)` | `AccountRepositoryJpaAdapter` → `AccountJpaRepository.save` (persist: new id) | — | PK |
| `CustomerRepository` (REUSE) | `findById` | `CustomerRepositoryJpaAdapter` | by PK | PK |

No collection read, no N+1. Transaction: `OpenAccountUseCase` (`@Transactional`) — the customer
read and the insert share it. The FK guarantees the owner exists at commit even if the
application check raced.

## 4 · Migrations

`src/main/resources/db/migration/V6__create_accounts.sql`

```sql
-- UC-007: accounts of a customer. Single currency (USD); balance never negative.
-- No personal data. Retention: life of the customer.
CREATE TABLE accounts (
    id          uuid          NOT NULL,
    customer_id uuid          NOT NULL,
    balance     numeric(19,2) NOT NULL,
    status      varchar(20)   NOT NULL,
    opened_at   timestamptz   NOT NULL,
    version     bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_accounts PRIMARY KEY (id),
    CONSTRAINT fk_accounts_customer FOREIGN KEY (customer_id) REFERENCES customers (id),
    CONSTRAINT ck_accounts_balance_non_negative CHECK (balance >= 0),
    CONSTRAINT ck_accounts_status CHECK (status IN ('ACTIVE', 'INACTIVE'))
);

CREATE INDEX ix_accounts_customer_id ON accounts (customer_id);
```

New table — no expand/contract.

## 5 · Configuration

none — datasource, pool and JPA properties inherited; one small insert per request changes no
sizing.

## Audit

none — `Audit` row is `none`.

## 6 · Declared dependencies

none.

## Compose services

| Service | Decision | Source |
|---|---|---|
| `postgres` | exists | `docker-compose.yml` `services:` |

## Design patterns

none — the adapter follows `CustomerRepositoryJpaAdapter`'s shape; no repeated skeleton worth
extracting with two adapters.

## Deferred

none.

## Impact on approved use cases

none.

## Implementation order

1. `V6__create_accounts.sql`
2. `AccountEntity`, `AccountJpaRepository`, `AccountPersistenceMapper`
3. `AccountRepositoryJpaAdapter`
4. integration test against PostgreSQL (Testcontainers)
