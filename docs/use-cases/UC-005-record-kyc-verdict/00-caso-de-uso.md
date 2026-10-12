# UC-005 · Record KYC verdict

> Parent spec. Fixes the boundary and the names; **doesn't** detail any layer.
> Per-layer detail lives in this folder's partials, each with its own owner.

Designed from backlog row **`BL-03`** (split from `UC-004-initiate-kyc-verification`). It provides
the only way out of `KYC_IN_PROGRESS`, the status `UC-004` gives every new customer.

| Field | Value |
|---|---|
| Identifier | `UC-005-record-kyc-verdict` |
| Date | 2026-10-12 |
| Trigger | a message on the **KYC result topic**, consumed over Kafka — inbound messaging adapter. Published by the KYC application (outside this project) in answer to the request `UC-004` emits. **This project defines the contract** — topic name, field names, serialization (user's answer); the KYC application adopts it. Topic, key, group, serialization and envelope belong to `messaging-architect`, in `25-mensageria.md` |
| Access | not applicable — no HTTP trigger. Who may publish on the topic is a broker concern, out of this case |
| Side effects | **1** — `UPDATE customers.status` of one customer. No publication, no notification, no external call. A consumer-side dedupe record, if `messaging-architect` chooses one, rides in the same transaction and is part of the idempotency mechanism, not a second effect |
| External calls | none — the KYC application is reached only through the broker; this case calls no system over HTTP |
| Transaction | opens at `RecordKycVerdictUseCase.record`, closes before the message is acknowledged — the status change commits before the offset does |
| Repetition | the consumer may receive duplicates. **Reprocessing the same verdict changes nothing** (business fact, from `BL-03`). A second verdict that differs from the one already applied **keeps the first** and is discarded with a warning (user's answer). The mechanism — state comparison, a dedupe table, or both — is delegated to `messaging-architect` |
| Concurrency | two verdicts for the same customer arriving at once: ordering per customer and the state comparison are the arbiters. Partition key and any lock belong to `messaging-architect` and `persistence-architect` |
| Active blueprint | `clean-architecture` (`layout: single-module`) |

## Trigger, payload, and response

**In** — the KYC verdict message (business content only; envelope, event id and format are
`messaging-architect`'s)

| Field | Holds | Rule |
|---|---|---|
| customer id | the `id` of the customer the verdict answers — the same id `UC-004` sends in the request | required, a well-formed customer id |
| verdict | the KYC outcome | required, one of `APPROVED`, `REJECT` — any other value is malformed |

No decision time, no rejection reason: the customer keeps **the status only** (user's answer).

**Out** — no synchronous response. Situations:

| Situation | Result |
|---|---|
| verdict `APPROVED`, customer `KYC_IN_PROGRESS` | status becomes `ACTIVE` |
| verdict `REJECT`, customer `KYC_IN_PROGRESS` | status becomes `REJECTED_BY_KYC` |
| same verdict already applied (duplicate) | nothing changes; acknowledged silently |
| customer already in a final status with a **different** outcome — including a grandfathered `ACTIVE` customer receiving any `REJECT` | nothing changes; the message is discarded with a warning log and a metric (user's answer) |
| customer id unknown | nothing changes; the message is discarded with a warning log and a metric (user's answer) |
| malformed message (missing field, unknown verdict, unreadable payload) | rejected in the adapter, never reaches the domain — what happens to it (skip or dead-letter) is `messaging-architect`'s |

## Status transitions this case adds

| From | Verdict | To |
|---|---|---|
| `KYC_IN_PROGRESS` | `APPROVED` | `ACTIVE` |
| `KYC_IN_PROGRESS` | `REJECT` | `REJECTED_BY_KYC` |
| `ACTIVE` | `APPROVED` | `ACTIVE` — no change |
| `REJECTED_BY_KYC` | `REJECT` | `REJECTED_BY_KYC` — no change |
| `ACTIVE` | `REJECT` | `ACTIVE` — first verdict kept, discarded with warning |
| `REJECTED_BY_KYC` | `APPROVED` | `REJECTED_BY_KYC` — first verdict kept, discarded with warning |

`ACTIVE` and `REJECTED_BY_KYC` are final for this case. Where the verdict-to-status mapping
lives, and how the two "no change" kinds are told apart, is `domain-modeling`'s.

## Flow

1. The inbound messaging adapter receives the message and validates its **shape**.
2. Translates it into the command `RecordKycVerdictCommand` (customer id, verdict).
3. `RecordKycVerdictUseCase` opens the transaction and loads the `Customer` through the outbound
   port `CustomerRepository`. Absent → discarded with warning, nothing written.
4. The `Customer` aggregate applies the verdict: moves out of `KYC_IN_PROGRESS`, or reports that
   the verdict is a duplicate or conflicts with the status already reached.
5. On a move, `CustomerRepository` persists the new status. Commit.
6. The adapter acknowledges the message.

## Components

State: **NEW** to be created · **CHANGE** already exists and changes · **REUSE**
already exists and serves. Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they
start with `src/`.

| File | Layer | Role | State | Detailed by |
|---|---|---|---|---|
| `infrastructure/messaging/kycverdict/` — listener named `<Event>Listener`, payload record | inbound adapter | receives the Kafka message, validates shape, maps to the command | NEW | `messaging-architect` |
| consumer configuration (deserializer, error handler, group) under `infrastructure/messaging/` | inbound adapter | consumer wiring | NEW | `messaging-architect` |
| `application/usecase/customer/RecordKycVerdictCommand.java` | application | command | NEW | `domain-modeling` |
| `application/usecase/customer/RecordKycVerdictUseCase.java` | application | orchestrates, opens transaction | NEW | `domain-modeling` |
| `application/port/CustomerRepository.java` | application | outbound port — `findById`, `save` | REUSE (a change, if any, is `domain-modeling`'s) | `domain-modeling` |
| `domain/model/Customer.java` | domain | aggregate — gains the transition out of `KYC_IN_PROGRESS` | CHANGE | `domain-modeling` |
| `domain/model/CustomerStatus.java` | domain | closed set, unchanged values | REUSE | `domain-modeling` |
| typed field for the verdict, closed set (`APPROVED`, `REJECT`) — value object or `enum`, `domain-modeling` decides | domain | verdict | NEW | `domain-modeling` |
| `infrastructure/persistence/customer/` (entity, mapper, adapter) | outbound adapter | updates `status` | REUSE (`status` already mapped, updatable) | `persistence-architect` |
| `src/main/resources/db/migration/V<n>__…sql` | infrastructure | migration — only if a dedupe table is asked for by `25-mensageria.md` § 6 | conditional | `persistence-architect` |

## Invariants

| Rule | Guaranteed by | Who violates it |
|---|---|---|
| a customer leaves `KYC_IN_PROGRESS` only through a KYC verdict | `Customer`'s transition | a caller setting the status directly |
| `ACTIVE` and `REJECTED_BY_KYC` are final — a later verdict never overwrites them | `Customer`'s transition | a duplicate or a conflicting verdict |
| verdict is `APPROVED` or `REJECT` | the verdict type, and the adapter's shape check | a malformed message |

Invariants live in the aggregate, never in the caller — `@.claude/rules/architecture-ddd.md`.

## Errors

| Situation | Kind |
|---|---|
| customer id unknown | not found — **discarded with warning**, never retried |
| verdict conflicts with the final status already reached | conflict with existing state — **discarded with warning**, never retried |
| same verdict already applied | not an error — idempotent no-op |
| malformed message | shape — rejected in the adapter, never reaches the domain |
| database unavailable while applying | infrastructure — the message is not acknowledged; retry and dead-letter belong to `messaging-architect` |

Situations and kinds, never exception names. The exception class and `errorCode` belong to
`10-dominio.md` (`@.claude/rules/error-handling.md`).

## Expected tests

| Level | Target | Detailed by |
|---|---|---|
| unit | `Customer`: each row of § Status transitions | `test-architect` |
| unit | use case: unknown id, conflict and duplicate write nothing | `test-architect` |
| integration | listener against a real broker and database: approve, reject, duplicate, conflict, unknown id, malformed | `test-architect` |

## Out of scope for this use case

- Storing the decision time or a rejection reason → not requested (user's answer: status only).
- Announcing the outcome (event, notification to the customer) → not requested; would be its own
  use case reacting to an event.
- A deadline for a verdict that never arrives → not requested, as already recorded in `UC-004`.
- Re-driving dead-lettered outbox rows → `BL-04`.

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-004-initiate-kyc-verification` | `Customer` gains the transition out of `KYC_IN_PROGRESS`; `CustomerStatus`'s "no transition is designed here" note no longer holds | `UC-004` deferred both transitions to `BL-03`, which is this case | — no precondition added |

No row adds a precondition: no approved use case becomes unreachable. This case **restores**
end-to-end reachability of `ACTIVE` / `REJECTED_BY_KYC` for customers created since `UC-004`.

## Implementation order

Derived from the Components table, in compile order.

- [ ] 1. verdict type — shape named in `10-dominio.md`
- [ ] 2. `domain/model/Customer.java` — transition
- [ ] 3. domain exceptions — named in `10-dominio.md`
- [ ] 4. `application/usecase/customer/RecordKycVerdictCommand.java`
- [ ] 5. `application/usecase/customer/RecordKycVerdictUseCase.java`
- [ ] 6. migration — only if `25-mensageria.md` § 6 asks for a dedupe table
- [ ] 7. `infrastructure/messaging/kycverdict/` (payload, listener) and consumer configuration
- [ ] 8. tests for the levels above
- [ ] 9. `./mvnw clean verify` green
