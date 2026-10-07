# UC-001 · Persistence

> Partial of `docs/use-cases/UC-001-create-customer/`. Owner: `persistence-architect`.
> Inherits the aggregate and ports from `10-dominio.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/persistence-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/persistence.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| REST partial | `30-rest.md` — block 4 schema requirements read: `idempotency_keys` (NEW), its retention |
| Aggregate | `Customer` |
| Engine | PostgreSQL 16 — `postgres:16-alpine` in `docker-compose.yml` and in `TestcontainersConfiguration` |
| Expected volume at 12 months | not given; one insert per creation, no listing — sizing below doesn't depend on it |
| Divergences from `10-dominio.md` | none |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`. Survey:
no migration, no `@Entity`, no `idempotency_keys` in the project — everything here is NEW.

## 1 · Schema

**Table `customers`** — one per aggregate root.

| Column | Type | Nullable | Default | Constraint |
|---|---|---|---|---|
| `id` | `uuid` | no | — | PK — v7, assigned by the application |
| `name` | `varchar(120)` | no | — | — trimmed value, 2 to 120 characters (domain) |
| `security_number` | `char(11)` | no | — | `uq_customers_security_number UNIQUE (security_number)` — business key, arbiter of invariant 11 |
| `birth_date` | `date` | no | — | — |
| `registered_at` | `timestamptz` | no | — | filled by the application's `Clock`, never by the database |
| `version` | `bigint` | no | `0` | optimistic lock — no update case yet; present so the first one doesn't need an expand migration |

**Indexes**

| Index | Columns | Why |
|---|---|---|
| `pk_customers` | `id` | PK |
| `uq_customers_security_number` | `security_number` | Uniqueness, and serves `existsBySecurityNumber` |

No index on `name` or `birth_date`: no query filters by them.

**Table `idempotency_keys`** — shared by every `Idempotency-Key` endpoint, NEW, first in the
project. Columns, types and index exactly as `templates/IdempotencyKeyTable.sql.example` —
the set is `@.claude/rules/api-rest.md` § Idempotency's, not redecided here: TTL 24 h
(`expires_at = created_at + 24 h`), `IN_PROGRESS` lease 5 minutes, `caller_identity` =
`anonymous` for this public endpoint (`30-rest.md` § 4).

**Personal data at rest** — `@.claude/rules/personal-data.md` § At rest.

| Column | Holds | Retention | Why it is stored at all |
|---|---|---|---|
| `customers.security_number` | national identification number, personal data | life of the customer record (user's decision). Removal comes with a deletion / anonymization case — Deferred, below | Business key: uniqueness needs the full value; a reduced form cannot serve a unique lookup |
| `customers.birth_date` | birth date, personal data | life of the customer record | The registration rule (strictly over 18) is the case's own invariant; a later case may need the age again |
| `customers.name` | name | life of the customer record | Identifies the customer |
| `idempotency_keys.*` | no personal data — `body_hash` is a SHA-256 of the request body, `response_body` is `{"id": …}` only | 24 h TTL; rows past it are not deleted yet — Deferred, below | Replay of the original response |

Both personal-data columns are named in the migration's own comment (block 4).

## 2 · Mapping

Aggregate `Customer` → entity `CustomerEntity` in `infrastructure/persistence/customer/`.
Entity, Spring Data interface, mapper and adapter live there, package-private
(`@.claude/rules/persistence.md` § Boundary).

| Aggregate field | Domain type | Column | Translation |
|---|---|---|---|
| `id` | `CustomerId` | `id` | `CustomerId.value()` ↔ `CustomerId.of(uuid)` |
| `name` | `String` | `name` | direct |
| `securityNumber` | `SecurityNumber` | `security_number` | `SecurityNumber.value()` ↔ `SecurityNumber.of(text)`; field carries `@JdbcTypeCode(SqlTypes.CHAR)` and `@Column(length = 11)` — `char(11)` is not `String`'s default inference |
| `birthDate` | `LocalDate` | `birth_date` | direct |
| `registeredAt` | `Instant` | `registered_at` | direct, UTC |
| — | — | `version` | entity-only, `@Version long version`, invisible to the domain |

Reconstruction goes through `Customer.rehydrate(...)`, never `register` — the date rules
must not re-run against today (`10-dominio.md` § 1). Mapper: `CustomerPersistenceMapper`,
static, package-private.

`CustomerEntity extends AssignedIdEntity<UUID>` — the id is assigned, so the entity is
`Persistable` (`@.claude/rules/persistence.md` § Identity and keys). `AssignedIdEntity` is NEW,
written once in `infrastructure/persistence/shared/AssignedIdEntity.java`, `public abstract`
(`templates/JpaEntity.java.example`, last block); `IdempotencyKeyEntity` extends it too.

## 3 · Adapter and ports

| Port | Signature (from `10-dominio.md`) | Query | Fetch | Index serving it |
|---|---|---|---|---|
| `CustomerRepository.existsBySecurityNumber` | `boolean existsBySecurityNumber(SecurityNumber securityNumber)` | Spring Data derived `existsBySecurityNumber(String)` — `SELECT … LIMIT 1` | — | `uq_customers_security_number` |
| `CustomerRepository.save` | `Customer save(Customer customer)` | `saveAndFlush` — `INSERT` (new entity, `isNew = true`) | — | `pk_customers` |

`CustomerRepositoryJpaAdapter` (blueprint convention `<Capability><Technology>Adapter`)
implements `CustomerRepository`; `CustomerJpaRepository extends JpaRepository<CustomerEntity,
UUID>`. No `@Transactional` here — the use case owns the transaction.

**Constraint translation.** `save` calls `saveAndFlush` so the `INSERT` reaches the database
inside the adapter's `try`. A `DataIntegrityViolationException` whose constraint name is
`uq_customers_security_number` becomes `SecurityNumberAlreadyRegisteredException` with the
original as `cause`; any other integrity violation is rethrown as a `ConflictException`
with `errorCode` `CUSTOMER_PERSISTENCE_CONFLICT`. The message never carries the security
number.

**Concurrency:** two requests with the same number — both pass `existsBySecurityNumber`, the
second `INSERT` hits the unique constraint and is translated as above. No collection
returned, so no pagination or `JOIN FETCH`.

**Idempotency** — NEW, shapes in `templates/IdempotencyKeyStore.java.example` and
`templates/IdempotentExecution.java.example`, packages per the blueprint's `packages.map`:

| Class | Path | Role |
|---|---|---|
| `IdempotencyKeyPort`, `IdempotencyRequest`, `StoredResponse`, `IdempotencyClaim` | `application/port/` | output port and its types |
| `IdempotentExecution`, `IdempotentOutcome` | `application/shared/` | the two-transaction shape (`api-rest.md` § Idempotency) |
| `IdempotencyKeyEntity`, `IdempotencyKeyJpaRepository`, `IdempotencyKeyStore` | `infrastructure/persistence/idempotency/` | table mapping and the adapter implementing `IdempotencyKeyPort` |

`IdempotencyKeyEntity` carries `@JdbcTypeCode(SqlTypes.CHAR)` on `body_hash` and
`@JdbcTypeCode(SqlTypes.SMALLINT)` on `response_status`; no `@Lob` on the `text` columns.

## 4 · Migrations

| File | Type | Content |
|---|---|---|
| `V1__create_customers.sql` | expand | `customers`, its PK and unique constraint |
| `V2__create_idempotency_keys.sql` | expand | shared `idempotency_keys` and its `expires_at` index |

New tables, no expand/contract pair. The executor creates each file verbatim from these
blocks, re-checking `<N>` against the disk.

**`src/main/resources/db/migration/V1__create_customers.sql`**

```sql
-- Personal data: security_number (national identification number) and birth_date.
-- Retention: life of the customer record (UC-001 20-persistencia.md § 1).
CREATE TABLE customers (
    id              uuid          NOT NULL,
    name            varchar(120)  NOT NULL,
    security_number char(11)      NOT NULL,
    birth_date      date          NOT NULL,
    registered_at   timestamptz   NOT NULL,
    version         bigint        NOT NULL DEFAULT 0,
    CONSTRAINT pk_customers PRIMARY KEY (id),
    CONSTRAINT uq_customers_security_number UNIQUE (security_number)
);
```

**`src/main/resources/db/migration/V2__create_idempotency_keys.sql`**

```sql
-- Shared by every endpoint that requires Idempotency-Key. No personal data:
-- body_hash is a SHA-256 of the request body; response_body holds the stored response.
CREATE TABLE idempotency_keys (
    idempotency_key  uuid          NOT NULL,
    route            varchar(200)  NOT NULL,
    caller_identity  varchar(200)  NOT NULL,
    body_hash        char(64)      NOT NULL,
    status           varchar(20)   NOT NULL,
    response_status  smallint      NULL,
    response_body    text          NULL,
    response_headers text          NULL,
    claimed_at       timestamptz   NOT NULL,
    created_at       timestamptz   NOT NULL,
    expires_at       timestamptz   NOT NULL,
    version          bigint        NOT NULL,
    CONSTRAINT pk_idempotency_keys PRIMARY KEY (idempotency_key)
);

CREATE INDEX ix_idempotency_keys_expires_at ON idempotency_keys (expires_at);
```

No earlier migration exists, none is edited.

## 5 · Configuration

Added to `src/main/resources/application.yml`; what is already there (`open-in-view: false`,
`ddl-auto: validate`, Flyway enabled, datasource variables) stays as is.

| Property | Value | Why |
|---|---|---|
| `spring.datasource.hikari.maximum-pool-size` | `10` | One short insert per request; a pool bigger than Postgres's default `max_connections` share only moves the queue |
| `spring.datasource.hikari.minimum-idle` | `10` | Equal to the max — no latency spike opening connections |
| `spring.datasource.hikari.connection-timeout` | `3000` | Rule — fail fast instead of holding the request |
| `spring.datasource.hikari.validation-timeout` | `2000` | Rule |
| `spring.datasource.hikari.max-lifetime` | `1800000` | Below the database side's connection timeout |
| `spring.datasource.hikari.pool-name` | `bank-app-pool` | Readable pool metrics |
| `spring.jpa.show-sql` | `false` | Rule |
| `spring.jpa.properties.jakarta.persistence.query.timeout` | `5000` | Rule — query timeout defined; the case's two queries are by unique key |
| `spring.jpa.properties.hibernate.jdbc.time_zone` | `UTC` | Rule — instants stored in UTC |
| `spring.jpa.properties.hibernate.jdbc.batch_size` | `50` | Harmless with single inserts; batching ready for later cases |
| `spring.jpa.properties.hibernate.order_inserts` | `true` | Rule when batching |
| `spring.jpa.properties.hibernate.query.fail_on_pagination_over_collection_fetch` | `true` | Rule |
| `spring.flyway.validate-on-migrate` | `true` | Rule |
| `spring.flyway.out-of-order` | `false` | Rule |
| `logging.level.org.hibernate.orm.jdbc.bind` | `WARN` | `TRACE` would log the bound security number and birth date |

Shape in `templates/application-persistence.yml.example`.

## 6 · Declared dependencies

none — `spring-boot-starter-data-jpa`, `postgresql`, `flyway-database-postgresql` and
`com.fasterxml.uuid:java-uuid-generator` are already in `pom.xml`. The Postgres service
exists in `docker-compose.yml` (`postgres:16-alpine`): no pending compose service.

## Design patterns

none — no force in the spec, no symptom on disk: one aggregate, no existing adapter whose
skeleton this one would repeat. The idempotency store is the mandated shared mechanism.

## Deferred

| Decided | Missing | Norm | Intended owner |
|---|---|---|---|
| `idempotency_keys` rows expire after 24 h | a scheduled, batched `DELETE FROM idempotency_keys WHERE expires_at < now()` — nothing deletes expired rows; the table grows by one row per creation call. No personal data in it | `@.claude/rules/api-rest.md` § Idempotency (TTL), `@.claude/rules/personal-data.md` § At rest ("a retention window nobody reads is no retention") | `backlog` — user's decision |
| Customer personal data kept for the life of the record | a use case that deletes or anonymizes a customer — until it exists, `security_number` and `birth_date` are kept indefinitely | `@.claude/rules/personal-data.md` § At rest | `backlog` |

## Impact on approved use cases

none — UC-001 is the first case.

## Implementation order

1. `V1__create_customers.sql`, `V2__create_idempotency_keys.sql`
2. `infrastructure/persistence/shared/AssignedIdEntity`
3. `CustomerEntity` + `CustomerPersistenceMapper` + `CustomerJpaRepository` + `CustomerRepositoryJpaAdapter`
4. `IdempotencyKeyEntity` + `IdempotencyKeyJpaRepository` + `IdempotencyKeyStore`
5. Properties in `application.yml`
6. Integration tests against the real database (Testcontainers) — `40-testes.md`
