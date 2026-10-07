# UC-001 · Create customer

> Parent spec. Fixes the boundary and the names; **doesn't** detail any layer.
> Per-layer detail lives in this folder's partials, each with its own owner.

| Field | Value |
|---|---|
| Identifier | `UC-001-create-customer` |
| Date | 2026-10-07 |
| Trigger | customer creation, over HTTP — inbound REST adapter. **The exact path and verb belong to `rest-api-architect`, in `30-rest.md`** |
| Access | public — anyone may call it, no caller identity required (business fact, user's answer). Mechanism, the `permitAll` reason and status belong to `security-architect`, in `32-seguranca.md` |
| Side effects | **1** — `INSERT` into `customers` |
| External calls | none — no side effect reaches a system this service does not own |
| Publication | none — no other system needs to learn that a customer was created; no domain event is published by this case |
| Transaction | opens at the use case `CreateCustomerUseCase`, closes before the response |
| Repetition | repeating the request with the same security number must **not** create a second customer (business fact): one customer per security number, the repeat is a conflict. The mechanism — `Idempotency-Key` or none — is delegated to `rest-api-architect` |
| Concurrency | the uniqueness of the security number is arbitrated by a unique constraint on the stored value; no application-level lock. Two concurrent requests with the same number: one is created, the other is a conflict |
| Personal data | `securityNumber` (national identification number, 11 digits) and `birthDate` are personal data. They never appear in clear in logs — `@.claude/rules/personal-data.md`, `@.claude/rules/logging.md`. The response carries only a reference, so neither leaves the service in a response body |
| Active blueprint | `clean-architecture-single-module` |

## Trigger, payload, and response

**In**

| Field | Type | Required | Rule |
|---|---|---|---|
| `name` | string | ✅ | leading and trailing whitespace trimmed; after trimming, not blank and 2 to 120 characters |
| `securityNumber` | string | ✅ | exactly 11 numeric digits (`0`–`9`); no check-digit validation; unique in the system |
| `birthDate` | date | ✅ | the customer's 18th birthday is **strictly before** today (a customer whose 18th birthday is today is rejected); the customer is **not over 130** years old today; a date in the future is rejected |

How "today" and the anniversaries are computed:

- "Today" is the current calendar date taken from the application's clock, never from the
  request. Where the clock comes from (a `java.time.Clock` or a port) is `domain-modeling`'s
  decision.
- The N-th birthday is `birthDate` plus N calendar years. For a `birthDate` of 29 February,
  the anniversary in a non-leap year falls on 28 February (calendar addition, as
  `java.time.LocalDate.plusYears` computes it).
- Accepted when `birthDate + 18 years < today` **and** `birthDate + 131 years > today` —
  that is, the completed age is at most 130.

**Out**

Situations, not statuses. The HTTP code for each is `rest-api-architect`'s decision.

| Situation | Result |
|---|---|
| created | reference to the created customer only (its `id`, and where to find it) — no customer data echoed back (user's answer) |
| malformed payload | rejected without touching the domain |
| a field breaks its rule (name, security number format, birth date) | validation error naming the field |
| customer not strictly over 18 | business rule violated |
| security number already registered | conflict with existing state |

## Flow

1. The inbound adapter receives the request and validates the payload's **shape**.
2. Translates it into the command `CreateCustomerCommand`.
3. The use case `CreateCustomerUseCase` executes: checks through the outbound port
   `CustomerRepository` that the security number is not registered yet, then builds the
   `Customer` aggregate, which validates its invariants at creation against today's date.
4. The outbound port `CustomerRepository` persists it. A unique-constraint violation from a
   concurrent request is translated into the same conflict as step 3's check.
5. The adapter translates the returned aggregate into the response — a reference only.

Nothing in this flow names annotations, columns, or framework types — that belongs to
the partials.

## Components

State: **NEW** to be created · **CHANGE** already exists and changes · **REUSE**
already exists and serves.

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

| File | Layer | Role | State | Detailed by |
|---|---|---|---|---|
| `infrastructure/rest/CustomerController.java` | inbound adapter | receives HTTP | NEW | `rest-api-architect` |
| `infrastructure/rest/dto/CreateCustomerRequest.java` | inbound adapter | input DTO | NEW | `rest-api-architect` |
| output DTO for the reference, if any | inbound adapter | output DTO — whether a body exists at all is `rest-api-architect`'s call | NEW | `rest-api-architect` |
| `infrastructure/rest/CustomerMapper.java` | inbound adapter | DTO ↔ command | NEW | `rest-api-architect` |
| `infrastructure/rest/ApiExceptionHandler.java` | inbound adapter | maps domain exceptions to HTTP — none exists yet | NEW | `rest-api-architect` |
| `application/usecase/customer/CreateCustomerUseCase.java` | application | concrete use case, opens the transaction | NEW | `domain-modeling` |
| `application/usecase/customer/CreateCustomerCommand.java` | application | command (record) | NEW | `domain-modeling` |
| `application/port/CustomerRepository.java` | application | outbound port — save, and look up by security number | NEW | `domain-modeling` |
| `domain/model/Customer.java` | domain | aggregate | NEW | `domain-modeling` |
| `domain/model/SecurityNumber.java` | domain | typed field for `securityNumber`, 11 digits — shape (value object or primitive) is `domain-modeling`'s call | NEW | `domain-modeling` |
| typed field for `name`, if any | domain | trimmed name, 2 to 120 characters — shape is `domain-modeling`'s call | NEW | `domain-modeling` |
| typed field for `birthDate`, if any | domain | birth date carrying the age rule — shape is `domain-modeling`'s call | NEW | `domain-modeling` |
| source of today's date | domain / application | clock used by the age rule — `java.time.Clock` or a port, `domain-modeling`'s call | NEW | `domain-modeling` |
| `domain/exception/` — exceptions for the situations below | domain | named in `10-dominio.md` | NEW | `domain-modeling` |
| `infrastructure/persistence/customer/CustomerRepositoryJpaAdapter.java` | outbound adapter | implements the port | NEW | `persistence-architect` |
| `infrastructure/persistence/customer/CustomerEntity.java` | outbound adapter | persistence entity | NEW | `persistence-architect` |
| `infrastructure/persistence/customer/CustomerJpaRepository.java` | outbound adapter | Spring Data interface | NEW | `persistence-architect` |
| `src/main/resources/db/migration/V1__create_customers.sql` | infrastructure | migration — first of the project | NEW | `persistence-architect` |

Paths derived from the blueprint's `packages.map`: `application.usecase.customer` groups the
use case and its command by the aggregate it writes; the persistence adapter carries the
aggregate's own subpackage (`…/persistence/customer/`). Output port and adapter names follow
the blueprint's convention: `<Capability>` and `<Capability><Technology>Adapter`.

## Invariants

| Rule | Guaranteed by | Who violates it |
|---|---|---|
| `securityNumber` is exactly 11 numeric digits | the security number's creation | caller with letters, punctuation, or a different length |
| `securityNumber` is unique in the system | check at the outbound port + unique constraint | second registration, or a race between two requests |
| `name`, trimmed, is 2 to 120 characters and not blank | `Customer`'s creation | blank or overly long payload |
| 18th birthday strictly before today | `Customer`'s creation, against the clock's date | a minor, or a customer whose 18th birthday is today |
| completed age at most 130 | `Customer`'s creation, against the clock's date | a typo such as 1825 instead of 1985 |
| `birthDate` is not in the future | `Customer`'s creation, against the clock's date | a typo, or a wrong date format interpreted |

Invariants live in the aggregate, never in the caller —
`@.claude/rules/architecture-ddd.md`.

## Errors

| Situation | Kind |
|---|---|
| security number already registered | conflict with existing state |
| customer not strictly over 18 | business rule |
| security number not exactly 11 digits | validation |
| name blank or outside 2 to 120 characters | validation |
| birth date in the future, or age over 130 | validation |
| required field missing, or a date that does not parse | shape — rejected in the adapter, never reaches the domain |

Situations and kinds, never exception names or statuses. The exception class and
`errorCode` belong to `10-dominio.md` (`@.claude/rules/error-handling.md`); the HTTP
mapping belongs to `30-rest.md`.

## Expected tests

| Level | Target | Detailed by |
|---|---|---|
| unit | `Customer`: name trim and length, 11-digit security number, age boundaries (18th birthday yesterday / today / tomorrow, 29 February, age 130 / 131, future date) with a fixed clock | `test-architect` |
| unit | use case: security number already registered is a conflict, nothing persisted | `test-architect` |
| slice | controller: created with reference only, malformed payload, each validation error, under-age, conflict | `test-architect` |
| integration | `CustomerRepositoryJpaAdapter` against a real database, including the unique constraint | `test-architect` |

## Out of scope for this use case

Written down so nobody merges it back in by accident:

- Reading, updating and deleting a customer → their own use cases, their own triggers; not
  requested.
- Check-digit validation of the security number (Brazilian CPF algorithm) → decided against:
  any 11 digits are accepted.
- Publishing a `CustomerCreated` event → no consumer exists; a future case that needs it adds
  it in its own `## Impact on approved use cases`.
- Authentication of the caller → the endpoint is public by decision.

## Impact on approved use cases

none — UC-001 is the first case.

## Implementation order

Derived from the Components table, in compile order.

- [ ] 1. domain typed fields (`SecurityNumber`, and name / birth date if typed)
- [ ] 2. `domain/model/Customer.java`
- [ ] 3. domain exceptions — named in `10-dominio.md`
- [ ] 4. `application/usecase/customer/CreateCustomerCommand.java`
- [ ] 5. `application/port/CustomerRepository.java`
- [ ] 6. `application/usecase/customer/CreateCustomerUseCase.java`
- [ ] 7. `src/main/resources/db/migration/V1__create_customers.sql`
- [ ] 8. `infrastructure/persistence/customer/` (entity, Spring Data, adapter)
- [ ] 9. `infrastructure/rest/` (DTOs, mapper, controller, exception handler)
- [ ] 10. tests for the levels above
- [ ] 11. `./mvnw clean verify` green
