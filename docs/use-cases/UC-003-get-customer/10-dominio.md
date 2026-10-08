# UC-003 · Domain and application

> Partial of `docs/use-cases/UC-003-get-customer/`. Owner: `domain-modeling`.
> Inherits the canonical names from `00-caso-de-uso.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/domain-modeling/templates/*.java.example`.

Paths relative to `src/main/java/dev/nerviz/bankapp/`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Aggregate | `Customer` — REUSE, modeled by `UC-001-create-customer` |
| Divergences from the mother spec | one, a refinement: the "customer not found" row reads `NEW or REUSE`; decided **REUSE** — plain `NotFoundException` with a string `errorCode`, no new class (§ 2) |

## 1 · Aggregate and value objects

**Aggregate root:** `Customer` — REUSE, unchanged. Boundary as UC-001 fixed it: the customer
alone, no reference to another aggregate. This case adds no field, no method and no factory:
the persistence adapter rebuilds it through the existing `Customer.rehydrate(...)`, which is
exactly what that factory exists for — reconstruction of persisted state, not a transition.

| Field | Type | Why this type |
|---|---|---|
| `id` | `CustomerId` | REUSE — value object over `UUID` |
| `name` | `String` | REUSE — trimmed, 2–120, checked by `Customer`'s compact constructor |
| `securityNumber` | `SecurityNumber` | REUSE — 11 digits, `toString()` masked |
| `birthDate` | `LocalDate` | REUSE — masked in `Customer.toString()` |
| `registeredAt` | `Instant` | REUSE |

**Value objects:** none new. `CustomerId` and `SecurityNumber` are REUSE.

**Masking candidates** — derived by `@.claude/rules/logging.md` § Masking candidates over the
aggregate, the command and the response shape this case returns:

| Field | Where | Matches by | Masked in logs | Crosses the boundary in clear |
|---|---|---|---|---|
| `securityNumber` | `Customer`, response | name (`securityNumber`) and type (`SecurityNumber`) | yes — `SecurityNumber.toString()` delegates to `masked()` | **yes, in the response body** — recorded decision in `00-caso-de-uso.md` § Personal data: receiver the bank's internal front-end, reason the screen displays the identifier itself. The body's form is `30-rest.md` § 6's |
| `birthDate` | `Customer`, response | name (`birthDate`) | yes — `Customer.toString()` prints `birthDate=***` | **yes, in the response body** — same recorded decision |
| `id` | `GetCustomerCommand`, response | — counter-list (`id` of an aggregate) | no — not a candidate | yes |
| `name` | response | — counter-list | no — not a candidate | yes |

No exception message carries either personal value: the not-found message names neither the
id's owner data nor anything but the situation (§ 2).

**State reachability:** none — `Customer` has no state field.

## 2 · Invariants

**Exception family: REUSE** — `DomainException`, `NotFoundException`, `ValidationException`,
`BusinessRuleViolationException`, `ConflictException` exist in `domain/exception/` (UC-001).
No class is missing.

| # | Invariant | Where it's enforced | Exception | `errorCode` |
|---|---|---|---|---|
| 1 | a customer with the requested id exists | `GetCustomerUseCase`, on an empty `Optional` from the output port | `NotFoundException` | `CUSTOMER_NOT_FOUND` |
| 2 | the id is not null | `CustomerId`'s compact constructor — REUSE | `ValidationException` | `CUSTOMER_ID_REQUIRED` — REUSE |
| 3 | a rehydrated customer satisfies the structural invariants | `Customer`'s compact constructor via `rehydrate` — REUSE | `ValidationException` | the existing codes of UC-001 |

`CUSTOMER_NOT_FOUND` is a first occurrence: one call site, no caller needs to `catch` it
specifically. By `@.claude/rules/error-handling.md` § Shape of the base classes it stays the
plain typed family with a string `errorCode` — no `CustomerNotFoundException`. A second call
site in a later case promotes it there, under that case's `## Impact on approved use cases`.

Message: `"customer not found"` — fixed text, no id interpolated beyond what the request path
already carries, no personal data.

An id that does not parse as a `UUID` never reaches this layer: it is a shape error of the
adapter (`00-caso-de-uso.md` § Errors).

## 3 · Ports

**Use case** — `application/usecase/customer/GetCustomerUseCase.java` — NEW

Concrete class, no interface (clean architecture — `@.claude/rules/naming.md` § Architecture
vocabulary), as `CreateCustomerUseCase` sits beside it.

```
class GetCustomerUseCase
    GetCustomerUseCase(CustomerRepository customers)
    Customer get(GetCustomerCommand command)
```

Builds `CustomerId.of(command.id())`, asks `customers.findById(...)`, returns the aggregate or
throws `NotFoundException("CUSTOMER_NOT_FOUND", "customer not found")`. The read-only
transaction opens and closes here — `@Transactional(readOnly = true)` on the method, application
layer, as `CreateCustomerUseCase` carries its own (`@.claude/rules/architecture-ddd.md`,
Application section). Constructor injection, `private final` field. Returns the domain
aggregate: the adapter maps it to its DTO, the use case knows no response shape.

**Command** — `application/usecase/customer/GetCustomerCommand.java` — NEW

```
record GetCustomerCommand(UUID id)
```

A single field, kept as a record anyway: `@.claude/rules/naming.md` § Command fixes one command
per use case, co-located with it, and the shape matches `CreateCustomerCommand` and
`PruneExpiredIdempotencyKeysCommand`. JDK type at the input — the use case builds the value
object and lets its invariant fire. Compact constructor rejects `null` with
`ValidationException("CUSTOMER_ID_REQUIRED", "customer id is required")`. No masking override:
`id` is on the counter-list.

**Output** — `application/port/CustomerRepository.java` · kind `persistence` — CHANGE

```
interface CustomerRepository
    boolean existsBySecurityNumber(SecurityNumber securityNumber)   // REUSE — UC-001
    Customer save(Customer customer)                                 // REUSE — UC-001
    Optional<Customer> findById(CustomerId id)                       // NEW — this case
```

`Optional` on a query return is the one place `@.claude/rules/code-quality.md` allows it.
Absent is `Optional.empty()`, never `null` and never an exception from the adapter: deciding
that absence is an error is the use case's business, not persistence's. No JPA, Spring or SQL
types in the signature.

No output port of kind `messaging` or `external HTTP`.

## 4 · Events

none — a read emits nothing (`00-caso-de-uso.md` § Publication).

## 5 · Components to create

Refines the `00-caso-de-uso.md` rows marked `Detailed by: domain-modeling`.

| File | Type | State |
|---|---|---|
| `domain/model/Customer.java` | record aggregate | REUSE |
| `domain/model/CustomerId.java` | record VO | REUSE |
| `domain/model/SecurityNumber.java` | record VO | REUSE |
| `domain/exception/NotFoundException.java` | typed family | REUSE — new `errorCode` `CUSTOMER_NOT_FOUND`, no new class |
| `application/usecase/customer/GetCustomerCommand.java` | record | NEW |
| `application/usecase/customer/GetCustomerUseCase.java` | class | NEW |
| `application/port/CustomerRepository.java` | interface | CHANGE — gains `findById` |

## Design patterns

none — no force in the spec, no symptom on disk. The case adds one query method and one
single-path use case: no enumerated variants, no rule per type, no construction with
invariants beyond the existing `rehydrate`, no predicate shared between deciding and querying.

| Force or symptom | Pattern | Classes and interfaces it creates | "When not" checked |
|---|---|---|---|

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerRepository` gains `Optional<Customer> findById(CustomerId id)`; its implementation `CustomerRepositoryJpaAdapter` must implement it (`20-persistencia.md`) | this case reads by id; UC-001 only checked existence by security number and saved. Adds no precondition to UC-001 |
