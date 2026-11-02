---
status: implemented
---

# UC-007 — Open account

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block holds only the final decisions and points to the
partial that details it. Where a block here and its partial disagree, **this file wins**; the
losing value is in § Resolved divergences.

| Partial | Owner | Status |
|---|---|---|
| `00-use-case.md` | `use-case-design` | ✅ |
| `10-domain.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ |
| `31-graphql.md` | `graphql-api-architect` | — not applicable (REST trigger only) |
| `32-security.md` | `security-architect` | ✅ |
| `28-http-client.md` | `http-client-architect` | — not applicable (no `external HTTP` port) |
| `29-ai.md` | `ai-architect` | — not applicable (no AI port) |
| `23-search.md` | `search-architect` | — not applicable (`Search index` none) |
| `25-messaging.md` | `messaging-architect` | — not applicable (no event) |
| `35-jobs.md` | `jobs-architect` | — not applicable (no Form B, no schedule, no deferred job) |
| `20-persistence.md` | `persistence-architect` | ✅ |
| `22-cache.md` | `cache-architect` | — not applicable (`Freshness` none) |
| `40-tests.md` | `test-architect` | ✅ |

Package root `src/main/java/dev/nerviz/bankapp/` (clean-architecture, single-module).

---

## 1. Use case

Detail: `00-use-case.md`.

| Decision | Value |
|---|---|
| Trigger | HTTP, inbound REST adapter |
| Access | role `CUSTOMER` for its own customer, or `OPERATOR` for any (§ 4.5) |
| Side effects | 1 — `INSERT` into `accounts`; the customer is read only |
| Transaction | opens at `OpenAccountUseCase.open`, closes before the response |
| Repetition | retry with the same `Idempotency-Key` opens no second account; distinct requests open distinct accounts (many per customer) |
| Freshness | none |

---

## 2. Domain model — implement first

Detail: `10-domain.md` § 1-3.

| Decision | Value |
|---|---|
| Aggregate | `Account` (NEW) — `AccountId id`, `CustomerId owner`, `Money balance`, `AccountStatus status`; `open(AccountId, Customer)`, `rehydrate(...)` |
| Value objects / enums | `AccountId` (UUID), `Money` (BigDecimal, scale 2, ≥ 0, single currency USD), `AccountStatus` enum (`ACTIVE`, `INACTIVE`; `acceptsMovements()`), `Actor` sealed (`Customer(CustomerId)`, `Operator()`; `mayActFor`) |
| Customer | CHANGE — adds `boolean canHoldAccount()` (`ACTIVE` and not anonymized) |
| Use case | `application/usecase/account/OpenAccountUseCase` + `OpenAccountCommand(UUID customerId, Actor actor)`; id UUIDv7 generated in the use case |
| Output ports | `AccountRepository.save` (NEW); `CustomerRepository.findById` (REUSE) |
| Exceptions | REUSE family. `CustomerNotFoundException` `CUSTOMER_NOT_FOUND` (absent **or** not the actor's); `BusinessRuleViolationException` `CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT`; `ValidationException` `ACCOUNT_ID_REQUIRED`, `MONEY_REQUIRED`, `MONEY_NEGATIVE`, `MONEY_SCALE_INVALID` |
| Events | none |
| `INACTIVE` reachability | `BL-08` (backlog) — no transition in this case |

---

## 3. Persistence — implement after 2

Detail: `20-persistence.md` § 1-4. Migration SQL: § 4 of that partial, materialized verbatim.

| Decision | Value |
|---|---|
| Engine | PostgreSQL (inherited) |
| Table | `accounts` (NEW): `id uuid` PK, `customer_id uuid` FK → `customers`, `balance numeric(19,2)` CHECK ≥ 0, `status varchar(20)` CHECK, `opened_at timestamptz`, `version bigint` |
| Index | `ix_accounts_customer_id` |
| Migration | `src/main/resources/db/migration/V6__create_accounts.sql` |
| Mapping | `AccountEntity extends AssignedIdEntity<UUID>`; owner by id, no association; `opened_at` and `version` adapter-only |
| Adapter | `AccountRepositoryJpaAdapter`, `AccountJpaRepository`, `AccountPersistenceMapper` under `infrastructure/persistence/account/` |
| Declared dependencies | none |

## 3.3 Outbound HTTP — none
## 3.4 Cache — none
## 3.7 AI — none
## 3.8 Search — none

## 4. REST API — implement after 2

Detail: `30-rest.md` § 1-8.

| Decision | Value |
|---|---|
| Endpoint | `POST /api/v1/customers/{customerId}/accounts`, version `1+`, `operationId` `openAccount`, no body → 201 + `Location: /api/v1/accounts/{accountId}` + `AccountResponse` |
| Classes | `AccountApi` (+ `@OpenAccountOpenApiDocs`), `AccountController`, `AccountMapper`, `dto/AccountResponse` (`id`, `customerId`, `balance`, `currency` = `USD`, `status`) |
| Idempotency | `@Idempotent`, REUSE store; **CHANGE** `IdempotencyAspect` scopes the key by the authenticated principal's name, `anonymous` when unauthenticated |
| Errors | 400 malformed id / missing key; 404 `CUSTOMER_NOT_FOUND`; 422 `CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT`; 409 `IDEMPOTENCY_KEY_REUSED` / `IDEMPOTENCY_KEY_IN_PROGRESS`; 500 + `traceId`; 401/403 per § 4.5 |
| Pagination / versioning / admission | none / inherited from `UC-001` / none |
| Personal data | none |

## 4.1 GraphQL — none

## 4.5 Security — implement right after 4

Detail: `32-security.md` § 1-10.

| Decision | Value |
|---|---|
| Mechanism | inherited — JWT resource server (`UC-006`) |
| Rule | `SecurityConfig` CHANGE: `POST /api/v1/customers/*/accounts` → `hasAnyRole(CUSTOMER, OPERATOR)`, before `anyRequest().authenticated()` |
| Roles | `Roles.CUSTOMER` NEW |
| Actor | `infrastructure/rest/CurrentActorResolver` NEW: `ROLE_OPERATOR` → `Actor.Operator()`; `ROLE_CUSTOMER` + claim `customer_id` → `Actor.Customer`; customer without a valid claim → 403 |
| Configuration | `app.security.token.customer-id-claim: customer_id` (`TokenAuthoritiesProperties` CHANGE) |
| Ownership | not owner → 404 `CUSTOMER_NOT_FOUND` |
| `permitAll` | none added |
| Schema requirements / dependencies | none / none |

## 6. Messaging — none
## 7. Jobs — none

---

## 5. Tests — validates 1-4.5

Detail: `40-tests.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `AccountIdTest`, `MoneyTest`, `AccountTest`, `CustomerTest` (CHANGE), `ActorTest`, `AccountStatusTest`, `OpenAccountUseCaseTest`, `OpenAccountCommandTest`, `CurrentActorResolverTest` |
| Slice | `AccountControllerTest` — `30-rest.md` § 5 |
| Integration | `OpenAccountIT` (real chain, access + replay), `AccountRepositoryJpaAdapterIT` (PostgreSQL) |
| Fixtures | `CustomerFixtures` REUSE, `AccountFixtures` NEW — states via transitions, never `rehydrate` |
| Test dependencies | none |

---

## Design patterns

none — every partial recorded `none`.

## Impact on approved use cases

| Approved case | Change | Decided in | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `Customer` gains read-only `canHoldAccount()` | `10-domain.md` | — (no precondition) |
| `UC-001-create-customer` | `IdempotencyAspect` scopes keys by authenticated principal; public endpoints keep `anonymous` | `30-rest.md` | — (no precondition) |

## Infrastructure

| Service | Image | Decision | Detail | Decided in |
|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `exists` | — | `20-persistence.md` |

## Out of scope

- `BL-06` transfer between accounts — the original request; designed in a later run.
- `BL-07` deposit — until it ships, every account holds USD 0.00.
- `BL-08` deactivate account — until it ships, nothing produces `INACTIVE`.
- The identity provider must put `customer_id` and role `CUSTOMER` in customer tokens (user's
  answer, 2026-10-30; owner: the IdP's operator, outside this project). Until it does, customer
  calls answer 403 and only `OPERATOR` can open accounts. Local realm: `BL-05` extended.

## Implementation order (checklist)

- [x] 1. Domain exceptions — REUSE, nothing new
- [x] 2. Value objects: `AccountId`, `Money`, `AccountStatus`, `Actor`
- [x] 3. Aggregate `Account` + `Customer.canHoldAccount()`
- [x] 4. Events — n/a, none
- [x] 5. `OpenAccountCommand`
- [x] 6. `OpenAccountUseCase`
- [x] 7. `AccountRepository`
- [x] 8. Migration `V6__create_accounts.sql` — from `20-persistence.md` § 4
- [x] 9. `AccountEntity`
- [x] 10. `AccountJpaRepository`
- [x] 11. `AccountPersistenceMapper` + `AccountRepositoryJpaAdapter`
- [x] 12. Persistence properties — n/a, inherited
- [x] 13. `AccountResponse` + `AccountMapper`
- [x] 14. `AccountApi`, `OpenAccountOpenApiDocs`, `AccountController`; versioning inherited
- [x] 15. `@Idempotent` on the endpoint + `IdempotencyAspect` caller scope CHANGE
- [x] 16. Test fixtures
- [x] 17. Unit tests
- [x] 18. Integration + contract tests (incl. Block S: `Roles.CUSTOMER`, `SecurityConfig` rule, `CurrentActorResolver`, property)
- [x] 19. `./mvnw clean verify` — green build, coverage gate

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| caller who is a customer but not the owner | "access denied" (`00-use-case.md`) | 404 `CUSTOMER_NOT_FOUND` | `10-domain.md` / `32-security.md` (ownership hides existence) |
| `Customer.java` state | REUSE (`00-use-case.md`) | CHANGE — `canHoldAccount()` | `10-domain.md` |
| persisted fields of `Account` | four domain fields | plus `opened_at`, `version` (adapter-only) | `20-persistence.md` |
| caller rule | "customer or OPERATOR" (`00-use-case.md`) | role `CUSTOMER` or `OPERATOR` + `customer_id` claim | `32-security.md` (user's answer) |
