# UC-004 · Domain and application

> Partial of `docs/use-cases/UC-004-initiate-kyc-verification/`. Owner: `domain-modeling`.
> Inherits the canonical names from `00-caso-de-uso.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/domain-modeling/templates/*.java.example`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Aggregate | `Customer` — REUSE, CHANGE (gains `status`) |
| Divergences from the mother spec | none — the open shape of `status` is closed here: `CustomerStatus` is an `enum` (closed set with no formation rule, `@.claude/rules/value-objects.md` criterion 3). The mother spec's NEW/CHANGE/REUSE states are confirmed against the code on disk |

Package base: `dev.nerviz.bankapp`. Paths below are relative to
`src/main/java/dev/nerviz/bankapp/`.

This case creates no use-case class: it changes `Customer` and `CreateCustomerUseCase`
(`UC-001-create-customer`), and adds one event and one output port.

## 1 · Aggregate and value objects

**Aggregate root:** `Customer` — boundary unchanged (the customer alone, no reference to
another aggregate). One field added:

| Field | Type | Why this type |
|---|---|---|
| `status` | `CustomerStatus` | Closed set of three values with no formation rule of its own — `enum`, criterion 3 of `@.claude/rules/value-objects.md`. A `String` would let any text through the domain |

The record's components become, in order:
`Customer(CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant registeredAt, CustomerStatus status)`.
Every other field, type and value object is REUSE, as `UC-001-create-customer/10-dominio.md`
§ 1 fixed them.

**`CustomerStatus`** — `domain/model/CustomerStatus.java`, `enum`:
`KYC_IN_PROGRESS`, `ACTIVE`, `REJECTED_BY_KYC`. No behaviour in this case — no transition is
designed here (see State reachability).

**Creation and reconstruction**

| Factory | Change | Runs |
|---|---|---|
| `Customer.register(CustomerId, String, SecurityNumber, LocalDate, Clock)` | signature unchanged; the result is born with `status = KYC_IN_PROGRESS` | unchanged invariants and date rules, as in `UC-001` |
| `Customer.rehydrate(CustomerId, String, SecurityNumber, LocalDate, Instant, CustomerStatus)` | gains the `status` parameter, last | compact-constructor invariants only — any of the three values is accepted, since each is a persisted fact |

`register` keeps its parameter list on purpose: a caller cannot create a customer in any status
other than `KYC_IN_PROGRESS`. The invariant "a newly created customer is `KYC_IN_PROGRESS`"
lives in the factory, not in the use case.

**Masking candidates** — derived by `@.claude/rules/logging.md` § Masking candidates.

| Shape | Field | Match | Decision |
|---|---|---|---|
| `Customer` | `status` | none | not a candidate |
| `KycVerificationRequested` | `securityNumber` (`SecurityNumber`) | type stands in for a national id; name `securityNumber` | masked in `toString()` — `SecurityNumber.toString()` already returns `masked()` |
| `KycVerificationRequested` | `birthDate` (`LocalDate`) | name `birthDate` | masked in `toString()` (`***`) |
| `KycVerificationRequested` | `name` | none — counter-list | not a candidate |

`toString()` masking covers log lines only. The event's **content** carries both values in
clear on purpose: the receiver (the KYC application) needs the real document and date to
validate them (`00-caso-de-uso.md` § Personal data crossing the boundary in clear). The form on
the wire and at rest in the outbox is `messaging-architect` § 8 and `persistence-architect`'s.

**State reachability**

| State field | Value | Reached by | Status |
|---|---|---|---|
| `status` | `KYC_IN_PROGRESS` | `Customer.register(...)` — the creation factory | this case |
| `status` | `ACTIVE` | nothing in the domain yet — **`BL-03`** (KYC result consumer) designs the transition method. Rows that existed before this change reach it through the migration's backfill, a one-time data fact owned by `20-persistencia.md`, not a behaviour | named future case (`BL-03`) |
| `status` | `REJECTED_BY_KYC` | nothing yet — **`BL-03`** | named future case (`BL-03`) |

No transition method is designed here: no use case in this project moves a customer out of
`KYC_IN_PROGRESS` yet, and a method nobody calls is dead code. `BL-03` owns both transitions
and the invariant that only `KYC_IN_PROGRESS` may move. `rehydrate` is not a row above — it
rebuilds persisted state, it does not transition.

## 2 · Invariants

**Exception family: REUSE** — the five classes exist in `domain/exception/`. No new class.

| # | Invariant | Where it's enforced | Exception | `errorCode` |
|---|---|---|---|---|
| 1 | `status` is present | `Customer` compact constructor | `ValidationException` | `CUSTOMER_STATUS_REQUIRED` |
| 2 | a customer created through `register` is `KYC_IN_PROGRESS` | `Customer.register` — no parameter lets the caller choose | none — not violable through the API | — |
| 3 | `KycVerificationRequested`'s fields are present (`customerId`, `name`, `securityNumber`, `birthDate`, `occurredAt`) | `KycVerificationRequested` compact constructor | `ValidationException` | `KYC_REQUEST_FIELD_REQUIRED` |
| 4 | a committed creation records exactly one KYC request; a rolled-back one records none | **outside the aggregate** — `CreateCustomerUseCase` calls `RequestKycVerification.request` inside its transaction, and the port's implementation writes inside that transaction (Form B) | none new — a failure to record propagates and rolls the creation back | — |

Invariant 3 uses one `errorCode` for the five fields: the event is built only from an
already-valid `Customer` (§ 4), so the check is a programming-error guard, not a caller-facing
rule; five codes would name situations no request can produce.

A status value read back that is not one of the three never reaches the domain: the
persistence mapping fails first, and translating that is `20-persistencia.md`'s (no framework
exception crosses the port — `@.claude/rules/error-handling.md`).

## 3 · Ports

**Input** — none new. `application/usecase/customer/CreateCustomerUseCase.java` — CHANGE:

```
class CreateCustomerUseCase
    CreateCustomerUseCase(CustomerRepository customerRepository,
                          RequestKycVerification requestKycVerification,
                          Clock clock)
    CustomerId create(CreateCustomerCommand command)
```

`create` — steps 1-4 unchanged from `UC-001-create-customer/10-dominio.md` § 3, then:

5. `Customer saved = customerRepository.save(customer)`;
6. `requestKycVerification.request(KycVerificationRequested.of(saved))`;
7. returns `saved.id()`.

Same transaction, opened and closed on `create` — unchanged. Step 6 runs after `save` so a
security-number conflict detected at `save` (the constraint race) raises before anything is
recorded; the rollback then discards both anyway. Throws the same exceptions as before.

`GetCustomerUseCase` — REUSE: it already returns the `Customer` aggregate, which now carries
`status`. No signature changes.

**Command** — none new. `CreateCustomerCommand` — REUSE, unchanged: the status is not input.

**Output** — `application/port/CustomerRepository.java` · kind `persistence` — REUSE, no
signature change. Its implementation now maps `status` both ways (`20-persistencia.md`).

**Output** — `application/port/RequestKycVerification.java` · kind `messaging` — NEW

```
interface RequestKycVerification
    void request(KycVerificationRequested event)
```

Contract, written in its Javadoc: **records** the request **inside the caller's transaction**
— it does not send. A committed transaction guarantees the request will be delivered (Form B,
`00-caso-de-uso.md`); a rolled-back one guarantees it never will. Any failure to record
propagates and rolls the caller back; no framework exception crosses the port. No Kafka,
Spring, JPA or Jackson type in the signature. The name follows `@.claude/rules/naming.md` —
output port named for the capability.

## 4 · Events

| Event | Payload | Emitted when | Consumed by | Durability |
|---|---|---|---|---|
| `KycVerificationRequested` | `CustomerId customerId`, `String name`, `SecurityNumber securityNumber`, `LocalDate birthDate`, `Instant occurredAt` | a `Customer` is created and persisted — every committed creation, once | **the KYC application** (outside this project), which answers on its own topic; the answer is consumed by **`BL-03`** | **must not be lost** — a lost request leaves the customer `KYC_IN_PROGRESS` forever, nothing sweeps for it, and the KYC verdict is a regulatory record |

`domain/event/KycVerificationRequested.java` — immutable `record`, no framework types.
Factory `KycVerificationRequested.of(Customer customer)`: copies the four fields and sets
`occurredAt = customer.registeredAt()` — the creation instant, already taken from the injected
`Clock`; no second clock read, so the event and the row agree on when it happened.

`customerId` is the correlation the KYC answer must carry back (`00-caso-de-uso.md`); it is
not in the original request text and is required.

**External delivery:** yes — over Kafka, to a consumer outside this project. Topic, key,
envelope (event id included), serialization and the relay belong to `messaging-architect`, in
`25-mensageria.md`. The publication form follows from the Durability column; it is not decided
here.

## 5 · Components to create

Refines the `00-caso-de-uso.md` rows marked `Detailed by: domain-modeling`.

| File | Type | State |
|---|---|---|
| `domain/model/CustomerStatus.java` | `enum` | NEW |
| `domain/model/Customer.java` | record aggregate — gains `status`; `register` sets `KYC_IN_PROGRESS`; `rehydrate` gains the parameter; `toString()` includes `status` | CHANGE |
| `domain/event/KycVerificationRequested.java` | record event, `of(Customer)`, masked `toString()` | NEW |
| `domain/exception/…` | typed family | REUSE |
| `application/port/RequestKycVerification.java` | interface, kind `messaging` | NEW |
| `application/port/CustomerRepository.java` | interface | REUSE |
| `application/usecase/customer/CreateCustomerUseCase.java` | class — gains the port in its constructor and step 6 | CHANGE |
| `application/usecase/customer/CreateCustomerCommand.java` | record | REUSE |
| `application/usecase/customer/GetCustomerUseCase.java` | class | REUSE |

Every caller of `Customer.rehydrate` and of the record's canonical constructor changes with the
new component — the persistence mapper (`20-persistencia.md`) and the test fixtures
(`40-testes.md`).

## Design patterns

none — no force in the spec, no symptom on disk. `CustomerStatus` has no behaviour per value in
this case (no transition, no rule enumerated per status), so nothing calls for State or
Strategy; `BL-03` re-evaluates when it adds the transitions. `KycVerificationRequested.of` is the
naming rule's creation convention (`@.claude/rules/naming.md` § Methods), not a catalog pattern.
`CreateCustomerUseCase` gains one collaborator and one line — no branching to replace.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `Customer` gains `status` (`CustomerStatus`); `register` sets `KYC_IN_PROGRESS`; `rehydrate` gains a `status` parameter | every new customer enters KYC |
| `UC-001-create-customer` | `CreateCustomerUseCase` gains `RequestKycVerification` in its constructor and records `KycVerificationRequested` after `save`, in the same transaction | the KYC application must be asked, and the request must not be lost |
| `UC-003-get-customer` | the `Customer` returned by `GetCustomerUseCase` carries `status`; no signature change | the read exposes the status (`30-rest.md`) |

No row adds a precondition: no use case starts requiring a status.
