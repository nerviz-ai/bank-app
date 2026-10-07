# UC-001 · Domain and application

> Partial of `docs/use-cases/UC-001-create-customer/`. Owner: `domain-modeling`.
> Inherits the canonical names from `00-caso-de-uso.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/domain-modeling/templates/*.java.example`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Aggregate | `Customer` |
| Divergences from the mother spec | none — the open rows of its component table are closed here: `name` stays `String`, `birthDate` stays `LocalDate`, today's date comes from an injected `java.time.Clock` |

Package base: `dev.nerviz.bankapp`. Paths below are relative to
`src/main/java/dev/nerviz/bankapp/`.

## 1 · Aggregate and value objects

**Aggregate root:** `Customer`. Boundary: the customer alone — no reference to another
aggregate.

| Field | Type | Why this type |
|---|---|---|
| `id` | `CustomerId` | Value object over `UUID` (v7). Prevents passing another aggregate's id in its place |
| `name` | `String` | Free text — counter-catalog of `@.claude/rules/value-objects.md`. Trim, not blank and 2 to 120 characters are invariants of `Customer` |
| `securityNumber` | `SecurityNumber` | Formation rule (exactly 11 digits) and own behaviour (`masked()`) — criteria 1 and 4 |
| `birthDate` | `LocalDate` | `java.time` is already an immutable value with a unit; not wrapped (`value-objects.md` § Numbers). The age rules depend on today and live in `Customer.register` |
| `registeredAt` | `Instant` | Creation instant, from the injected `Clock` |

**Value objects**

| VO | Wraps | Own invariant | Factory |
|---|---|---|---|
| `CustomerId` | `UUID` | not null | `CustomerId.of(UUID)` |
| `SecurityNumber` | `String` | not null; exactly 11 characters, each `0`–`9`; no check-digit validation (mother spec, user's decision). `of` trims surrounding whitespace only — no mask removal: punctuation is rejected, not stripped | `SecurityNumber.of(String)` |

`SecurityNumber` exposes `value()` (the 11 digits, for persistence mapping) and `masked()`
(`*********` followed by the last 2 digits). Its `toString()` returns `masked()`, so a
`SecurityNumber` concatenated into a log line or an exception message never prints the full
value.

All `record`, immutable, validation in the compact constructor. Shape:
`templates/ValueObject.java.example`.

**Identity.** `CustomerId` is generated in the application layer, by
`CreateCustomerUseCase`, with `Generators.timeBasedEpochGenerator().generate()`
(`com.fasterxml.uuid:java-uuid-generator`, already in `pom.xml`) —
`@.claude/rules/persistence.md`: the id is generated where the aggregate is born, never by the
database or the adapter. The domain receives the id; it doesn't import the library.

**Creation and reconstruction**

| Factory | Used by | Runs |
|---|---|---|
| `Customer.register(CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Clock clock)` | `CreateCustomerUseCase` | compact-constructor invariants **plus** the date rules (invariants 6-8), against `LocalDate.now(clock)`; stamps `registeredAt = clock.instant()` |
| `Customer.rehydrate(CustomerId, String, SecurityNumber, LocalDate, Instant)` | persistence adapter | compact-constructor invariants only |

The date rules run only at creation: they depend on today, and a customer registered
yesterday stays valid regardless of how today moves. Putting them in the compact constructor
would re-run them at every rehydration.

**Today's date.** `LocalDate.now(clock)` — the calendar date in the clock's zone. The
application clock is `Clock.systemUTC()`, so "today" is the UTC date: a customer whose 18th
birthday falls today is rejected until 00:00 UTC of the next day. Recorded as a decision
(a business zone such as `America/Sao_Paulo` would be a change of the clock bean, not of the
domain). Clock bean: see § 5.

**Masking candidates** — derived by `@.claude/rules/logging.md` § Masking candidates.

| Shape | Field | Match | Decision |
|---|---|---|---|
| `Customer` | `securityNumber` | type `SecurityNumber` stands in for a national id; name `securityNumber` (National id) | masked — `masked()`, and `toString()` delegates to it |
| `Customer` | `birthDate` | name `birthDate` (Birth) | masked |
| `CreateCustomerCommand` | `securityNumber` (`String`) | name `securityNumber` | masked |
| `CreateCustomerCommand` | `birthDate` (`LocalDate`) | name `birthDate` | masked |
| `Customer`, command | `name` | none — counter-list | not a candidate |

No unmasked match, so no reason to record. Neither field appears in the use case's return
value (`CustomerId` only).

**State reachability:** none — `Customer` has no state field.

## 2 · Invariants

**Exception family: NEW — five classes in the `domain.exception` package:** `DomainException`
(abstract, `errorCode` required), `NotFoundException`, `ValidationException`,
`BusinessRuleViolationException`, `ConflictException`. Shapes in
`templates/DomainException.java.example` and the four typed ones. `NotFoundException` is
unused by this case and still created: the family is created once, whole.

| # | Invariant | Where it's enforced | Exception | `errorCode` |
|---|---|---|---|---|
| 1 | `id` is present | `Customer` compact constructor | `ValidationException` | `CUSTOMER_ID_REQUIRED` |
| 2 | `name` is present and not blank after trimming | `Customer` compact constructor | `ValidationException` | `CUSTOMER_NAME_REQUIRED` |
| 3 | `name`, trimmed, has 2 to 120 characters; the trimmed value is stored | `Customer` compact constructor | `ValidationException` | `CUSTOMER_NAME_LENGTH` |
| 4 | `securityNumber` is present | `SecurityNumber` compact constructor / `Customer` compact constructor | `ValidationException` | `SECURITY_NUMBER_REQUIRED` |
| 5 | `securityNumber` is exactly 11 digits `0`–`9` | `SecurityNumber` compact constructor | `ValidationException` | `SECURITY_NUMBER_INVALID` |
| 6 | `birthDate` is present | `Customer` compact constructor | `ValidationException` | `BIRTH_DATE_REQUIRED` |
| 7 | `birthDate` is not after today | `Customer.register` | `ValidationException` | `BIRTH_DATE_IN_FUTURE` |
| 8 | completed age at most 130: `birthDate.plusYears(131)` is after today | `Customer.register` | `ValidationException` | `BIRTH_DATE_TOO_OLD` |
| 9 | strictly over 18: `birthDate.plusYears(18)` is **before** today (equal is rejected) | `Customer.register` | `BusinessRuleViolationException` | `CUSTOMER_UNDERAGE` |
| 10 | `registeredAt` is present | `Customer` compact constructor | `ValidationException` | `REGISTERED_AT_REQUIRED` |
| 11 | `securityNumber` is unique in the system | **outside the aggregate** — `CreateCustomerUseCase` checks through `CustomerRepository.existsBySecurityNumber`; the unique constraint closes the race and the adapter translates its violation | `SecurityNumberAlreadyRegisteredException` (extends `ConflictException`) | `SECURITY_NUMBER_ALREADY_REGISTERED` |

Order inside `register`: 7, then 8, then 9 — a future date reports itself as such, not as
underage. `plusYears` on 29 February yields 28 February in a non-leap year (mother spec).

**Invariant 11 gets a named subclass** — `SecurityNumberAlreadyRegisteredException extends
ConflictException`, fixed `errorCode`, no state, with a constructor taking the `cause`. Entry
bar of `@.claude/rules/error-handling.md` met: the same `errorCode` is raised at two call
sites in this very case — the use case's pre-check and the persistence adapter's translation
of the constraint violation. One type keeps both from spelling the string separately.

The exception messages never contain the security number or the birth date — at most
`SecurityNumber.masked()`.

## 3 · Ports

**Input** — clean architecture: no interface. The concrete use case is the input boundary.

`application/usecase/customer/CreateCustomerUseCase.java`

```
class CreateCustomerUseCase
    CreateCustomerUseCase(CustomerRepository customerRepository, Clock clock)
    CustomerId create(CreateCustomerCommand command)
```

`create`:

1. builds `SecurityNumber.of(command.securityNumber())` (invariants 4-5);
2. `customerRepository.existsBySecurityNumber(securityNumber)` → `true` raises
   `SecurityNumberAlreadyRegisteredException`;
3. `Customer.register(new id, command.name(), securityNumber, command.birthDate(), clock)`
   (invariants 1-3, 6-10);
4. `customerRepository.save(customer)` and returns its `id()`.

The transaction opens and closes on this method (`@.claude/rules/architecture-ddd.md` §
Application) — the annotation lives on this application-layer class. Constructor injection,
`private final` fields. Throws `ValidationException`, `BusinessRuleViolationException` and
`SecurityNumberAlreadyRegisteredException`.

Validating the format (step 1) before the uniqueness lookup keeps a malformed number from
reaching the database query.

**Command** — `application/usecase/customer/CreateCustomerCommand.java`

```
record CreateCustomerCommand(String name, String securityNumber, LocalDate birthDate)
```

Primitive and JDK types: the command is where the outside world hasn't been validated yet.
No `jakarta.validation` — that lives on the adapter's DTO. `toString()` overridden to mask
`securityNumber` and `birthDate` (masking candidates, § 1).

**Output** — `application/port/CustomerRepository.java` · kind `persistence`

```
interface CustomerRepository
    boolean existsBySecurityNumber(SecurityNumber securityNumber)
    Customer save(Customer customer)
```

`existsBySecurityNumber` serves invariant 11. `save` returns the persisted aggregate and, on
a unique-constraint violation of the security number, throws
`SecurityNumberAlreadyRegisteredException` with the persistence exception as its `cause` —
no framework exception crosses the port. No JPA, Spring or SQL types in the signatures.

## 4 · Events

none — the mother spec fixes no publication: no other system needs to learn that a customer
was created. No `CustomerCreated` is designed; a future case that needs it adds it in its own
`## Impact on approved use cases`.

## 5 · Components to create

Refines the `00-caso-de-uso.md` rows marked `Detailed by: domain-modeling`.

| File | Type | State |
|---|---|---|
| `domain/model/CustomerId.java` | record VO | NEW |
| `domain/model/SecurityNumber.java` | record VO | NEW |
| `domain/model/Customer.java` | record aggregate | NEW |
| `domain/exception/DomainException.java` | abstract class | NEW |
| `domain/exception/NotFoundException.java` | class | NEW |
| `domain/exception/ValidationException.java` | class | NEW |
| `domain/exception/BusinessRuleViolationException.java` | class | NEW |
| `domain/exception/ConflictException.java` | class | NEW |
| `domain/exception/SecurityNumberAlreadyRegisteredException.java` | class, extends `ConflictException` | NEW |
| `application/usecase/customer/CreateCustomerCommand.java` | record | NEW |
| `application/usecase/customer/CreateCustomerUseCase.java` | class | NEW |
| `application/port/CustomerRepository.java` | interface | NEW |

**Requirement for the wiring, not a domain class:** a `java.time.Clock` bean
(`Clock.systemUTC()`) in `infrastructure/config/ClockConfig.java` — NEW, none exists. Written
by the executor with the configuration; recorded here because the use case's constructor
needs it.

## Design patterns

none — no force in the spec, no symptom on disk. The static factories `register` /
`rehydrate` are the naming rule's creation convention (`@.claude/rules/naming.md` § Methods),
not a catalog pattern; nothing in the mother spec enumerates variants or rules per type, and
`src/main/java` holds no domain or application class yet.

## Impact on approved use cases

none — UC-001 is the first case.
