# UC-004 · Initiate KYC verification

> Parent spec. Fixes the boundary and the names; **doesn't** detail any layer.
> Per-layer detail lives in this folder's partials, each with its own owner.

**This case creates no use-case class.** It is an extension of `UC-001-create-customer` (the
customer is now born `KYC_IN_PROGRESS` and creation asks the KYC application to verify it) and
of `UC-003-get-customer` (the read exposes the status). Almost all of its content lands in
`## Impact on approved use cases`; the slug names the capability added, not the trigger, which
still belongs to `UC-001` (`@.claude/rules/naming.md` § Use case identifier).

| Field | Value |
|---|---|
| Identifier | `UC-004-initiate-kyc-verification` |
| Date | 2026-10-08 |
| Trigger | customer creation, over HTTP — the existing inbound REST adapter of `UC-001-create-customer`, unchanged. **The exact path and verb belong to `rest-api-architect`, in `30-rest.md`** |
| Access | unchanged — public, as decided in `UC-001-create-customer` (creation) and `UC-003-get-customer` (read). This case adds no endpoint and changes no caller. Mechanism belongs to `security-architect`, in `32-seguranca.md` |
| Side effects | **1** — `INSERT` into `customers` (now carrying the status) **plus** the append of the KYC-verification request to the outbox, **in the same transaction**. Publication: **Form B (outbox), append inside the transaction** — a boundary fact, by `references/scope-boundary.md` § The one exception: a lost request leaves the customer `KYC_IN_PROGRESS` forever, nothing sweeps for it, and the KYC verdict is a regulatory record. The relay that sends the row is infrastructure, not a use case. The broker send itself, the topic and the relay schedule belong to `messaging-architect` and `jobs-architect` |
| External calls | none — no HTTP call. The request reaches the KYC application through the broker; the KYC application is a consumer of this project's topic, not a system this service calls |
| Transaction | opens at `CreateCustomerUseCase.create`, closes before the response — `INSERT customers` and the outbox append commit or roll back together |
| Repetition | unchanged from `UC-001`: the same security number never creates a second customer; the same `Idempotency-Key` replays the original response. Business fact added here: **a replay or a rejected creation never emits a second KYC request** — only a committed creation emits one |
| Concurrency | unchanged — `UNIQUE(security_number)` is the arbiter; no application-level lock. The outbox relay may resend a request (send succeeded, mark failed): duplicates reach the KYC application, whose tolerance is **unknown** (user's answer) |
| Active blueprint | `clean-architecture` (`layout: single-module`) |

## Trigger, payload, and response

**In** — unchanged from `UC-001-create-customer`: `name`, `securityNumber`, `birthDate`.

**Out**

| Situation | Result |
|---|---|
| created | unchanged from `UC-001` — reference only (`id` and the location). The status is **not** added to the creation response (user's answer) |
| read of one customer (`UC-003`) | the representation gains `status` (user's answer) |
| malformed payload, underage, security number already in use, idempotency-key conflicts | unchanged from `UC-001` — and **no KYC request is emitted** |

**Published — the KYC-verification request** (business content; topic, key, serialization and
envelope belong to `messaging-architect`, in `25-mensageria.md`)

| Field | Holds | Why |
|---|---|---|
| customer id | the created customer's `id` | correlation — the KYC result (`BL-03`) must name the customer it answers. Not in the request text; added because the reply is unusable without it |
| `name` | the customer's name | requested |
| `securityNumber` | the customer's security number, **in clear** | requested — the KYC application validates the document itself |
| `birthDate` | the customer's birth date, **in clear** | requested — the KYC application validates it |

**Personal data crossing the boundary in clear:** `securityNumber` and `birthDate` (and `name`),
receiver **the KYC application**, reason **KYC validation needs the real values** — a business
fact stated in the request. The form on the wire and at rest in the outbox belongs to
`messaging-architect` § 8 and `persistence-architect`
(`@.claude/rules/personal-data.md` § In transit, § At rest).

## Customer status — the values the field admits

| Value | Meaning | Produced by |
|---|---|---|
| `KYC_IN_PROGRESS` | created, KYC request emitted, no verdict yet | this case — every customer created from now on |
| `ACTIVE` | KYC approved | `BL-03` (KYC result consumer); **also every customer that existed before this change** (user's answer — grandfathered, no request emitted for them) |
| `REJECTED_BY_KYC` | KYC rejected | `BL-03` |

Closed set. Shape (value object or `enum`) is `domain-modeling`'s, by
`@.claude/rules/value-objects.md`. This case only **produces** `KYC_IN_PROGRESS` and the
backfill to `ACTIVE`; it adds no transition between statuses.

## Flow

1. The inbound adapter of `UC-001` receives the creation request — unchanged.
2. `CreateCustomerUseCase` builds the `Customer` aggregate, which is now born with status
   `KYC_IN_PROGRESS`.
3. The outbound port `CustomerRepository` persists it — unchanged port.
4. In the same transaction, the outbound port `RequestKycVerification` records the
   `KycVerificationRequested` event (Form B: an outbox append, not a send).
5. Commit. The response is unchanged.
6. Later, outside the request: the relay sends the recorded request to the broker — infrastructure
   owned by `messaging-architect` and `jobs-architect`.

The read (`UC-003`) maps the status into its representation; its flow is otherwise unchanged.

## Components

State: **NEW** to be created · **CHANGE** already exists and changes · **REUSE**
already exists and serves. Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they
start with `src/`.

| File | Layer | Role | State | Detailed by |
|---|---|---|---|---|
| `domain/model/Customer.java` | domain | aggregate — gains `status`; born `KYC_IN_PROGRESS` in `register`, restored in `rehydrate` | CHANGE | `domain-modeling` |
| `domain/model/CustomerStatus.java` | domain | typed field for `status`, closed set (`KYC_IN_PROGRESS`, `ACTIVE`, `REJECTED_BY_KYC`) — value object or `enum`, `domain-modeling` decides | NEW | `domain-modeling` |
| `domain/event/KycVerificationRequested.java` | domain | event — customer id, name, security number, birth date | NEW | `domain-modeling` |
| `application/port/RequestKycVerification.java` | application | outbound port — records the event inside the caller's transaction | NEW | `domain-modeling` |
| `application/usecase/customer/CreateCustomerUseCase.java` | application | calls the new port after persisting, same transaction | CHANGE | `domain-modeling` |
| `application/usecase/customer/GetCustomerUseCase.java` | application | returns the aggregate, now carrying the status | REUSE | `domain-modeling` |
| `infrastructure/messaging/…` | outbound adapter | implements `RequestKycVerification` (outbox append), relay sender, Kafka wiring | NEW | `messaging-architect` |
| scheduling of the relay and the outbox prune, under `infrastructure/scheduling/` | infrastructure | relay schedule, prune | NEW | `jobs-architect` |
| `infrastructure/persistence/customer/CustomerEntity.java` | outbound adapter | maps the status column | CHANGE | `persistence-architect` |
| `infrastructure/persistence/customer/CustomerPersistenceMapper.java` | outbound adapter | maps the status both ways | CHANGE | `persistence-architect` |
| outbox table, entity and store, under `infrastructure/persistence/` | outbound adapter | shared outbox | NEW | `persistence-architect` |
| `src/main/resources/db/migration/V<N>__…` | infrastructure | `customers` gains the status, existing rows `ACTIVE`; outbox table | NEW | `persistence-architect` |
| `infrastructure/rest/dto/CustomerDetailsResponse.java` | inbound adapter | gains `status` | CHANGE | `rest-api-architect` |
| `infrastructure/rest/CustomerMapper.java` | inbound adapter | maps the status into the read representation | CHANGE | `rest-api-architect` |
| `infrastructure/rest/openapi/GetCustomerOpenApiDocs.java` | inbound adapter | documents `status` | CHANGE | `rest-api-architect` |
| `infrastructure/rest/dto/CustomerResponse.java` | inbound adapter | creation response — unchanged | REUSE | `rest-api-architect` |

Paths derived from the code on disk (the blueprint's `packages.map`: `domain.model`,
`domain.event`, `application.port`, `application.usecase.<aggregate>`, `infrastructure.*`).
Migration number `<N>` is re-checked on disk by `persistence-architect`. A broker service in
`docker-compose.yml` is not on disk today: whoever needs it **records** it (`messaging-architect`
step 9); this run writes no compose block.

## Invariants

| Rule | Guaranteed by | Who violates it |
|---|---|---|
| a newly created customer has status `KYC_IN_PROGRESS` | `Customer.register` | a creation path that bypasses `register` |
| status is one of the three values | the `status` type | a persisted row with an unknown value |
| a committed creation records exactly one KYC request; a rolled-back creation records none | same transaction for `INSERT customers` and the outbox append | an append outside the transaction |
| every customer that existed before this change is `ACTIVE` | the migration | none afterwards — one-time backfill |

Invariants live in the aggregate, never in the caller — `@.claude/rules/architecture-ddd.md`.

## Errors

| Situation | Kind |
|---|---|
| status value read back that is not one of the three | validation (data integrity on rehydrate) |
| failure to record the KYC request | none new — it fails the whole transaction like any persistence failure: no customer, no request, the caller sees the existing unexpected-error result |

No new business error situation for the caller. Situations and kinds only — names belong to
`10-dominio.md`, HTTP mapping to `30-rest.md`.

## Expected tests

| Level | Target | Detailed by |
|---|---|---|
| unit | `Customer.register`: born `KYC_IN_PROGRESS`; `rehydrate` restores any of the three | `test-architect` |
| unit | `CreateCustomerUseCase`: records one request with the four fields after saving; no request when the domain rejects | `test-architect` |
| slice | read representation carries `status` | `test-architect` |
| integration | creation commits customer + outbox row together; rollback leaves neither; migration backfills existing rows to `ACTIVE` | `test-architect` |
| integration | relay sends the recorded request to the broker | `test-architect` |

## Out of scope for this use case

- **Consuming the KYC verdict** (`APPROVED` → `ACTIVE`, `REJECT` → `REJECTED_BY_KYC`) →
  **`BL-03`**. Out because reading a topic is a trigger, never an effect of this case. **Until
  `BL-03` ships, every customer created after this change stays `KYC_IN_PROGRESS` with no way
  out** — nothing in this project moves it.
- Requesting KYC for customers that existed before this change → not requested; they are
  `ACTIVE` by the user's decision.
- A deadline for the KYC verdict (no answer after N days) → not requested.
- Restricting any operation to `ACTIVE` customers → not requested; no case requires a status
  today.
- Exposing the status in the creation response → declined by the user.

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | the `Customer` aggregate gains `status`; `register` sets `KYC_IN_PROGRESS` | KYC must run on every new customer | — no precondition added |
| `UC-001-create-customer` | `CreateCustomerUseCase` records a `KycVerificationRequested` event in the outbox, inside its transaction | the KYC application must be asked to verify; Form B so the request is never lost | — no precondition added |
| `UC-001-create-customer` | `customers` gains the status column; every existing row becomes `ACTIVE` | existing customers are grandfathered (user's answer) | — no precondition added |
| `UC-003-get-customer` | the read representation gains `status` | callers must see where the customer is in KYC (user's answer) | — no precondition added |

No row adds a precondition: no case starts requiring a status.

## Implementation order

Derived from the Components table, in compile order.

- [ ] 1. `domain/model/CustomerStatus.java`
- [ ] 2. `domain/model/Customer.java` (status)
- [ ] 3. `domain/event/KycVerificationRequested.java`
- [ ] 4. `application/port/RequestKycVerification.java`
- [ ] 5. `application/usecase/customer/CreateCustomerUseCase.java`
- [ ] 6. migrations — status column with backfill, outbox table
- [ ] 7. `infrastructure/persistence/` (customer mapping, outbox store)
- [ ] 8. `infrastructure/messaging/` (port adapter, relay sender, Kafka wiring)
- [ ] 9. `infrastructure/scheduling/` (relay, prune)
- [ ] 10. `infrastructure/rest/` (read representation, mapper, OpenAPI)
- [ ] 11. tests for the levels above
- [ ] 12. `./mvnw clean verify` green
