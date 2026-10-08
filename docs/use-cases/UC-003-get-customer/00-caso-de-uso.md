# UC-003 · Get customer

> Parent spec. Fixes the boundary and the names; **doesn't** detail any layer.
> Per-layer detail lives in this folder's partials, each with its own owner.

| Field | Value |
|---|---|
| Identifier | `UC-003-get-customer` |
| Date | 2026-10-08 |
| Trigger | read of one customer by its id, over HTTP — inbound REST adapter. **The exact path and verb belong to `rest-api-architect`, in `30-rest.md`** |
| Access | public — anyone may call it, no caller identity required (business fact, user's answer, confirmed after the risk below was stated). Mechanism, the `permitAll` reason and status belong to `security-architect`, in `32-seguranca.md` |
| Side effects | **0** — read only: one lookup in `customers` by primary key. Nothing is written |
| External calls | none — no side effect reaches a system this service does not own |
| Publication | none — a read announces nothing |
| Transaction | read-only, opens at the use case `GetCustomerUseCase`, closes before the response |
| Repetition | repeating the request returns the same customer and changes nothing (business fact): a read creates no duplicate. No idempotency mechanism is needed — confirmation is `rest-api-architect`'s, in `30-rest.md` |
| Concurrency | none — no write, no business key that could collide. A read concurrent with the creation of the same id sees it or answers not found |
| Personal data | the response carries `securityNumber` (national identification number, 11 digits) and `birthDate` **in clear** (user's answer). Receiver: the bank's internal front-end, which shows the customer's registration data to the customer or to an operator; reason: that screen displays the identifier and the birth date themselves (`@.claude/rules/personal-data.md` § In transit, § Admitted exception). The form of each field in the body is recorded by `rest-api-architect` in `30-rest.md` § 6. Neither value ever appears in clear in logs — `@.claude/rules/logging.md` |
| Accepted risk | **public + personal data in clear**, user's explicit decision after being told: anyone holding a customer id reads its national identifier and birth date, with no identity check and no record of who read it. Ids are not secret — UC-001 returns them in `Location` |
| Active blueprint | `clean-architecture-single-module` |

## Trigger, payload, and response

**In**

| Field | Type | Required | Rule |
|---|---|---|---|
| `id` | identifier (the customer's id, as UC-001 returns it) | ✅ | must parse as a customer id; carried by the request itself, no body |

**Out**

Situations, not statuses. The HTTP code for each is `rest-api-architect`'s decision.

| Situation | Result |
|---|---|
| found | the customer's full record: `id`, `name`, `securityNumber` (in clear), `birthDate` (in clear), `registeredAt` |
| id does not parse as a customer id | rejected without touching the domain |
| no customer with that id | not found |

## Flow

1. The inbound adapter receives the request and validates the **shape** of the id.
2. Translates it into the command `GetCustomerCommand`.
3. The use case `GetCustomerUseCase` executes: asks the outbound port `CustomerRepository` for
   the customer with that id.
4. Absent → the not-found situation. Present → the `Customer` aggregate, rehydrated by the
   persistence adapter, is returned.
5. The adapter translates the aggregate into the response — the full record.

The read crosses the domain — the aggregate is rehydrated and returned — so the admitted
exception of `@.claude/rules/architecture-ddd.md` (pure read straight from inbound to outbound
adapter, with an ADR) is **not** used.

Nothing in this flow names annotations, columns, or framework types — that belongs to
the partials.

## Components

State: **NEW** to be created · **CHANGE** already exists and changes · **REUSE**
already exists and serves.

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

| File | Layer | Role | State | Detailed by |
|---|---|---|---|---|
| `infrastructure/rest/CustomerController.java` | inbound adapter | receives HTTP — gains the read operation beside UC-001's creation | CHANGE | `rest-api-architect` |
| `infrastructure/rest/CustomerApi.java` | inbound adapter | contract interface — gains the read operation's documentation | CHANGE | `rest-api-architect` |
| OpenAPI docs annotation for the read operation | inbound adapter | beside `infrastructure/rest/openapi/CreateCustomerOpenApiDocs.java` | NEW | `rest-api-architect` |
| output DTO for the full record | inbound adapter | `infrastructure/rest/dto/CustomerResponse.java` carries `id` only and serves UC-001 — a new DTO or a change to it is `rest-api-architect`'s call | NEW | `rest-api-architect` |
| `infrastructure/rest/CustomerMapper.java` | inbound adapter | aggregate → response DTO | CHANGE | `rest-api-architect` |
| `infrastructure/rest/ApiExceptionHandler.java` | inbound adapter | already maps the not-found kind and an unparseable path variable | REUSE | `rest-api-architect` |
| `application/usecase/customer/GetCustomerUseCase.java` | application | concrete use case, opens the read-only transaction | NEW | `domain-modeling` |
| `application/usecase/customer/GetCustomerCommand.java` | application | command (record) carrying the id | NEW | `domain-modeling` |
| `application/port/CustomerRepository.java` | application | outbound port — gains a lookup by id | CHANGE | `domain-modeling` |
| `domain/model/Customer.java` | domain | aggregate — `rehydrate` already exists | REUSE | `domain-modeling` |
| `domain/model/CustomerId.java` | domain | typed id | REUSE | `domain-modeling` |
| `domain/model/SecurityNumber.java` | domain | typed security number | REUSE | `domain-modeling` |
| `domain/exception/` — exception for "customer not found" | domain | `NotFoundException` exists as the family base; whether a subclass is named is `10-dominio.md`'s call | NEW or REUSE | `domain-modeling` |
| `infrastructure/persistence/customer/CustomerRepositoryJpaAdapter.java` | outbound adapter | implements the new lookup | CHANGE | `persistence-architect` |
| `infrastructure/persistence/customer/CustomerJpaRepository.java` | outbound adapter | Spring Data interface — lookup by primary key is inherited | REUSE | `persistence-architect` |
| `infrastructure/persistence/customer/CustomerPersistenceMapper.java` | outbound adapter | entity → aggregate already exists | REUSE | `persistence-architect` |
| `src/main/resources/db/migration/` | infrastructure | no migration — lookup by primary key of `customers` | REUSE | `persistence-architect` |

Paths derived from the blueprint's `packages.map`: `application.usecase.customer` groups the
query with the aggregate it reads (`@.claude/rules/naming.md` § Grouping); the persistence
adapter keeps the aggregate's own subpackage (`…/persistence/customer/`).

## Invariants

| Rule | Guaranteed by | Who violates it |
|---|---|---|
| the id has the shape of a customer id | the inbound adapter's parsing, then `CustomerId`'s creation | caller with an arbitrary string in place of the id |
| a returned customer satisfies `Customer`'s structural invariants | `Customer.rehydrate` | a row written outside the application |

No new invariant: a read enforces none of its own. Invariants live in the aggregate —
`@.claude/rules/architecture-ddd.md`.

## Errors

| Situation | Kind |
|---|---|
| no customer with that id | not found |
| id does not parse as a customer id | shape — rejected in the adapter, never reaches the domain |

Situations and kinds, never exception names or statuses. The exception class and
`errorCode` belong to `10-dominio.md` (`@.claude/rules/error-handling.md`); the HTTP
mapping belongs to `30-rest.md`.

## Expected tests

| Level | Target | Detailed by |
|---|---|---|
| unit | use case: found returns the aggregate; absent is the not-found situation | `test-architect` |
| slice | controller: found with the full record, unparseable id, not found | `test-architect` |
| integration | `CustomerRepositoryJpaAdapter` lookup by id against a real database — present and absent | `test-architect` |
| integration | create (UC-001) then read: the `Location` returned by creation resolves to the created customer | `test-architect` |

## Out of scope for this use case

Written down so nobody merges it back in by accident:

- Listing or searching customers (by security number, by name) → its own use case; not
  requested.
- Updating a customer → its own use case; not requested.
- Deleting or anonymizing a customer → `BL-02`, already in the backlog.
- Authentication of the caller, and an audit record of who read which customer → the endpoint
  is public by decision; the accepted risk is in the header above.

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `CustomerRepository` gains a lookup by id; `CustomerRepositoryJpaAdapter` implements it | this case reads; UC-001 only wrote | — (adds no precondition) |
| `UC-001-create-customer` | `CustomerController` and `CustomerApi` gain the read operation | same resource, same controller | — (adds no precondition) |
| `UC-001-create-customer` | the `Location` URI returned by creation now resolves instead of answering not found (UC-001 § Out of scope recorded it as unresolved until a read case existed) | this case is that read | — (adds no precondition) |

## Implementation order

Derived from the Components table, in compile order.

- [ ] 1. domain exception for "customer not found" — named in `10-dominio.md`
- [ ] 2. `application/port/CustomerRepository.java` — lookup by id
- [ ] 3. `application/usecase/customer/GetCustomerCommand.java`
- [ ] 4. `application/usecase/customer/GetCustomerUseCase.java`
- [ ] 5. `infrastructure/persistence/customer/CustomerRepositoryJpaAdapter.java` — lookup by id
- [ ] 6. `infrastructure/rest/` (output DTO, mapper, OpenAPI docs, contract interface, controller)
- [ ] 7. tests for the levels above
- [ ] 8. `./mvnw clean verify` green
