# UC-007 · Tests

> Partial of every other partial in this folder. Owner: `test-architect`. Norms:
> `@.claude/rules/testing.md`. HTTP contract cases are `30-rest.md` § 5 and `32-security.md`
> § 10 — cited, not rewritten. Every existing `@SpringBootTest` already runs under
> `@ActiveProfiles("test")`; ArchUnit is installed (`ArchitectureTest`).

## 1 · Distribution by level

| Behavior | Level | Why |
|---|---|---|
| `AccountId`, `Money` formation rules | domain unit | value-object invariants |
| `Account.open`: zero balance, `ACTIVE`, eligibility | domain unit | aggregate invariant |
| `Customer.canHoldAccount` | domain unit | aggregate query |
| `Actor.mayActFor`, `AccountStatus.acceptsMovements` | domain unit | closed-set behavior |
| use case: not found, other actor, operator override, save called once | application unit | orchestration with mocked ports |
| `CurrentActorResolver`: Jwt → `Actor` | adapter unit | translation, no context |
| controller mapping, status, `errorCode`, body (`30-rest.md` § 5) | web slice `@WebMvcTest` with the project's chain imported | transport contract |
| access 401 / 403 / 201 (`32-security.md` § 10) | HTTP through the real chain (`@SpringBootTest`, `jwt()`) | `@.claude/rules/authorization.md` § Tests — never filters off |
| replay with same key returns same account, one row stored | integration (`@SpringBootTest` + PostgreSQL) | idempotency spans aspect, store and DB |
| mapping, FK, CHECK constraints, migration `V6` | persistence integration (Testcontainers PostgreSQL) | SQL and mapping |

## 2 · Cases per test class

| Class | Method | Asserts |
|---|---|---|
| `domain/model/AccountIdTest` | `rejectsNullValue` | `ValidationException`, `ACCOUNT_ID_REQUIRED` |
| `domain/model/MoneyTest` | `rejectsInvalidAmount(BigDecimal, String errorCode)` — parameterized: `null`/`MONEY_REQUIRED`, `-0.01`/`MONEY_NEGATIVE`, `1.001`/`MONEY_SCALE_INVALID` | type and `errorCode` |
| | `zeroIsZeroWithScaleTwo` | `Money.zero().amount()` equals `0.00` |
| | `acceptsTwoDecimals` | `Money.of(10.50)` |
| `domain/model/AccountTest` | `opensWithZeroBalanceAndActiveForActiveCustomer` | balance `0.00`, `ACTIVE`, owner = customer id |
| | `rejectsIneligibleCustomer(CustomerStatus)` — parameterized: `KYC_IN_PROGRESS`, `REJECTED_BY_KYC` | `BusinessRuleViolationException`, `CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT` |
| | `rejectsAnonymizedCustomer` | same |
| `domain/model/CustomerTest` (CHANGE) | `canHoldAccountOnlyWhenActiveAndNotAnonymized` | true / false for each status and anonymized |
| `domain/model/ActorTest` | `customerMayActOnlyForItself`, `operatorMayActForAnyone` | booleans |
| `domain/model/AccountStatusTest` | `onlyActiveAcceptsMovements` | `ACTIVE` true, `INACTIVE` false |
| `application/usecase/account/OpenAccountUseCaseTest` | `opensAndSavesAccountForOwner` | `save` once, returned account |
| | `operatorOpensForAnyCustomer` | `save` once |
| | `rejectsAbsentCustomerAsNotFound` | `CustomerNotFoundException`, no `save` |
| | `rejectsOtherCustomerActorAsNotFound` | `CustomerNotFoundException`, no `save` (ownership row) |
| | `propagatesIneligibility` | `CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT`, no `save` |
| `application/usecase/account/OpenAccountCommandTest` | `rejectsMissingFields` | null id / null actor |
| `infrastructure/rest/CurrentActorResolverTest` | `resolvesOperator`, `resolvesCustomerFromClaim`, `deniesCustomerWithoutClaim` | `32-security.md` § 10 last row |
| `infrastructure/rest/AccountControllerTest` | one method per row of `30-rest.md` § 5 except replay and access | status, `errorCode`, body |
| `infrastructure/rest/OpenAccountIT` | access rows of `32-security.md` § 10 (parameterized over token shapes → 401/403/201), `replayReturnsSameAccount` | real chain; one row in `accounts` after replay |
| `infrastructure/persistence/account/AccountRepositoryJpaAdapterIT` | `savesAndReadsBack`, `rejectsUnknownCustomerByForeignKey`, `rejectsNegativeBalanceByCheck` | mapping round trip; FK and CHECK enforced by `V6` |

No `UNIQUE` over a business key in `20-persistence.md` — no duplicate-key test. No DTO field
matches the masking derivation — no masking test.

## 3 · Data and doubles

| Item | Value |
|---|---|
| Customer factory | `CustomerFixtures` (REUSE) — gains `activeCustomer()`, `anonymizedCustomer()` only if absent; states built through `Customer.register` + `recordKycVerdict` / `anonymize`, never `rehydrate` for a state a transition produces |
| Account factory | `AccountFixtures` (NEW, beside `CustomerFixtures`) — `openFor(Customer)` via `Account.open` |
| Doubles | `CustomerRepository`, `AccountRepository` mocked in the use case test only |
| Clock | fixed `Clock` in tests that persist (`opened_at`) |
| Tokens | `jwt().authorities(ROLE_CUSTOMER).jwt(j -> j.claim("customer_id", id))`, `ROLE_OPERATOR`, `ROLE_DATA_PROTECTION_OFFICER` |
| Database | PostgreSQL via `TestcontainersConfiguration` (REUSE) |

## 4 · Coverage and gaps

Every invariant of `10-domain.md` § 2 has a named test. Deliberately uncovered: `INACTIVE`
reachability (`BL-08`). JaCoCo gate inherited.

## 5 · Test dependencies

none — `spring-boot-starter-security-test` and the resource-server test starter
(`pom.xml:227`, `:232`), Testcontainers PostgreSQL and AssertJ already declared.
