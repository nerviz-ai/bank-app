# UC-005 · Domain and application

> Partial of `docs/use-cases/UC-005-record-kyc-verdict/`. Owner: `domain-modeling`.
> Inherits the canonical names from `00-caso-de-uso.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/domain-modeling/templates/*.java.example`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Aggregate | `Customer` — REUSE, CHANGE (gains the KYC transition) |
| Divergences from the mother spec | none — the open shape of the verdict is closed here: `KycVerdict` is an `enum` (closed set, no formation rule — `@.claude/rules/value-objects.md` criterion 3). The mother spec's NEW/CHANGE/REUSE states are confirmed against the code on disk |

Package base: `dev.nerviz.bankapp`. Paths below are relative to
`src/main/java/dev/nerviz/bankapp/`.

## 1 · Aggregate and value objects

**Aggregate root:** `Customer` — boundary unchanged, components unchanged
(`id, name, securityNumber, birthDate, registeredAt, status`). One behaviour added:

```
Customer recordKycVerdict(KycVerdict verdict)
```

| Current `status` | Verdict | Result |
|---|---|---|
| `KYC_IN_PROGRESS` | any | a **new** `Customer`, every component copied, `status = verdict.resultingStatus()` |
| `verdict.resultingStatus()` (already there) | the same outcome | **`this`**, unchanged — duplicate, no-op |
| the other final status | the opposite outcome | throws `ConflictException` `KYC_VERDICT_CONFLICT` — the first verdict stands |

`Customer` is an immutable record: the transition returns the next state, it never mutates. A
`null` verdict raises `ValidationException` `KYC_VERDICT_REQUIRED`.

**`KycVerdict`** — `domain/model/KycVerdict.java`, `enum`: `APPROVED`, `REJECT`. Constant names
are the wire values `00-caso-de-uso.md` fixed. One method:

```
CustomerStatus resultingStatus()     // APPROVED → ACTIVE · REJECT → REJECTED_BY_KYC
```

The mapping lives on the verdict, not in the use case and not in the adapter: one place says
which status each outcome produces. `CustomerStatus` — REUSE, values unchanged; its Javadoc
sentence "No transition is designed here" becomes stale and is updated by the executor (§ Impact).

**Masking candidates** — derived by `@.claude/rules/logging.md` § Masking candidates.

| Shape | Field | Match | Decision |
|---|---|---|---|
| `RecordKycVerdictCommand` | `customerId` (`UUID`) | none | not a candidate |
| `RecordKycVerdictCommand` | `verdict` (`KycVerdict`) | none | not a candidate |
| `Customer` | unchanged | as fixed by `UC-001` / `UC-004` | `toString()` unchanged |

No new personal data enters the domain in this case.

**State reachability**

| State field | Value | Reached by | Status |
|---|---|---|---|
| `status` | `KYC_IN_PROGRESS` | `Customer.register(...)` | `UC-004` |
| `status` | `ACTIVE` | `Customer.recordKycVerdict(APPROVED)` from `KYC_IN_PROGRESS`; customers that existed before `UC-004` via its migration backfill | this case |
| `status` | `REJECTED_BY_KYC` | `Customer.recordKycVerdict(REJECT)` from `KYC_IN_PROGRESS` | this case |

Every value of `CustomerStatus` is now reachable through the aggregate's own API.

## 2 · Invariants

**Exception family: REUSE** — the five classes exist in `domain/exception/`. One named
subclass promoted (below).

| # | Invariant | Where it's enforced | Exception | `errorCode` |
|---|---|---|---|---|
| 1 | a customer leaves `KYC_IN_PROGRESS` only through a KYC verdict | `Customer.recordKycVerdict` — the only method that produces `ACTIVE` / `REJECTED_BY_KYC` from a live aggregate | none — not violable through the API | — |
| 2 | `ACTIVE` and `REJECTED_BY_KYC` are final: an opposite verdict never overwrites them | `Customer.recordKycVerdict` | `ConflictException` | `KYC_VERDICT_CONFLICT` |
| 3 | the same verdict twice changes nothing | `Customer.recordKycVerdict` returns `this` | none — no-op | — |
| 4 | the verdict is present | `Customer.recordKycVerdict` and `RecordKycVerdictCommand` compact constructor | `ValidationException` | `KYC_VERDICT_REQUIRED` |
| 5 | the customer id is present | `RecordKycVerdictCommand` compact constructor | `ValidationException` | `CUSTOMER_ID_REQUIRED` (existing) |
| 6 | the customer exists | **outside the aggregate** — `RecordKycVerdictUseCase`, on an empty `findById` | `CustomerNotFoundException` (NEW, `extends NotFoundException`) | `CUSTOMER_NOT_FOUND` |

**Promotion — `CustomerNotFoundException extends NotFoundException`**, fixed `errorCode`
`CUSTOMER_NOT_FOUND`, no state. Entry bar of `@.claude/rules/error-handling.md` § Shape of the
base classes met: the code now repeats across two call sites — `GetCustomerUseCase.java:31`
(`UC-003`) and `RecordKycVerdictUseCase` (this case). The `UC-003` call site switches to it under
§ Impact; the REST mapping is unchanged (same family, same `errorCode`).

`KYC_VERDICT_CONFLICT` is a first occurrence: plain `ConflictException`, no subclass.

**What the caller does with them** is not the domain's — `00-caso-de-uso.md` fixed it as a
business fact, and `messaging-architect` implements it: `CustomerNotFoundException` and the
`KYC_VERDICT_CONFLICT` conflict are **discarded with a warning log and a metric, never retried**;
`ValidationException` is the malformed-message path.

## 3 · Ports

**Input** — `application/usecase/customer/RecordKycVerdictUseCase.java` — NEW, concrete class
(clean architecture, no interface).

```
class RecordKycVerdictUseCase
    RecordKycVerdictUseCase(CustomerRepository customerRepository)
    void record(RecordKycVerdictCommand command)
```

`record`, in one read-write transaction opened and closed on this method
(`@.claude/rules/architecture-ddd.md` § Application):

1. `Customer customer = customerRepository.findById(CustomerId.of(command.customerId()))`
   `.orElseThrow(CustomerNotFoundException::new)`;
2. `Customer next = customer.recordKycVerdict(command.verdict())`;
3. if `next.status() != customer.status()` → `customerRepository.save(next)`; otherwise nothing
   is written (duplicate).

Returns nothing: the trigger has no response. Throws `CustomerNotFoundException`,
`ConflictException` (`KYC_VERDICT_CONFLICT`), `ValidationException`. Uses no `Clock`: the case
records no time (`00-caso-de-uso.md` — status only).

**Command** — `application/usecase/customer/RecordKycVerdictCommand.java` — NEW

```
record RecordKycVerdictCommand(UUID customerId, KycVerdict verdict)
```

JDK type for the id (the use case builds `CustomerId`, as `GetCustomerCommand` does); the verdict
is already the domain `enum` — parsing the wire string into it is a shape check, the adapter's,
and an unknown value never reaches the command. Compact constructor rejects either `null`
(invariants 4 and 5).

**Output** — `application/port/CustomerRepository.java` · kind `persistence` — REUSE, no
signature change. `findById` loads; `save` must **update** an existing row (it already persists
`status` as an updatable column — `UC-004/20-persistencia.md`). Whether `save` of an existing id
updates without a duplicate-key path is `20-persistencia.md`'s to confirm.

No other output port: no event, no external call.

## 4 · Events

none — the case emits no event (`00-caso-de-uso.md` § Out of scope: announcing the outcome was
not requested). It **consumes** one, from outside this project:

| Incoming | Business content | Published by | Consumed by |
|---|---|---|---|
| the KYC verdict | customer id, verdict (`APPROVED` \| `REJECT`) | the KYC application, on a contract this project defines | this case's inbound messaging adapter → `RecordKycVerdictCommand` |

It is not a domain event of this project — no class under `domain/event/`. Its payload record,
topic, envelope, event id and dedupe belong to `messaging-architect`, in `25-mensageria.md`.

**External delivery** (outbound): none.

## 5 · Components to create

Refines the `00-caso-de-uso.md` rows marked `Detailed by: domain-modeling`.

| File | Type | State |
|---|---|---|
| `domain/model/KycVerdict.java` | `enum` with `resultingStatus()` | NEW |
| `domain/model/Customer.java` | record aggregate — gains `recordKycVerdict(KycVerdict)` | CHANGE |
| `domain/model/CustomerStatus.java` | `enum` — values unchanged, Javadoc updated | CHANGE (doc only) |
| `domain/exception/CustomerNotFoundException.java` | `extends NotFoundException`, fixed `CUSTOMER_NOT_FOUND` | NEW |
| `domain/exception/…` | typed family | REUSE |
| `application/usecase/customer/RecordKycVerdictCommand.java` | record | NEW |
| `application/usecase/customer/RecordKycVerdictUseCase.java` | class, `@Transactional` on `record` | NEW |
| `application/usecase/customer/GetCustomerUseCase.java` | throws `CustomerNotFoundException` | CHANGE |
| `application/port/CustomerRepository.java` | interface | REUSE |

## Design patterns

none — no force in the spec, no symptom on disk. The force for State would be behaviour varying
per status; here there is one operation over three statuses, a three-row table expressed as two
comparisons in `recordKycVerdict` — "When not" of State applies (few states, one transition, no
growth named in any spec). The verdict-to-status mapping is a method on `KycVerdict`, an enum
carrying its own data, not a Strategy. No existing code switches on `CustomerStatus`.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-004-initiate-kyc-verification` | `Customer` gains `recordKycVerdict(KycVerdict)`; `CustomerStatus` Javadoc no longer says no transition exists | `UC-004` deferred both transitions to `BL-03`, which is this case |
| `UC-003-get-customer` | `GetCustomerUseCase` throws `CustomerNotFoundException` instead of `new NotFoundException("CUSTOMER_NOT_FOUND", …)`; same family and `errorCode`, so the REST mapping and the response are unchanged | `CUSTOMER_NOT_FOUND` now has two call sites — promotion bar of `@.claude/rules/error-handling.md` |

No row adds a precondition: no use case starts requiring a status.
