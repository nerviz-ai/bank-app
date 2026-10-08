# UC-004 · REST adapter

> Partial of `docs/use-cases/UC-004-initiate-kyc-verification/`. Owner: `rest-api-architect`.
> Inherits the canonical names from `00-caso-de-uso.md` and the ports from `10-dominio.md` —
> doesn't reinvent them.
> Contains no code. Reference shapes in
> `.claude/skills/rest-api-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/api-rest.md`.

| Field | Value |
|---|---|
| Parent spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Resource | `customers` |
| Version prefix | `/api/v1` |
| Divergences from `10-dominio.md` | none |
| Divergences from `00-caso-de-uso.md` | none |

Paths below are relative to `src/main/java/dev/nerviz/bankapp/infrastructure/rest/`.
This case adds **no endpoint**: it changes the representation of an existing one
(`UC-003-get-customer`) and leaves the creation endpoint (`UC-001-create-customer`) unchanged.

## 1 · Endpoints

| Method | Path | `operationId` | Inbound port | Success | State |
|---|---|---|---|---|---|
| `POST` | `/api/v1/customers` | `createCustomer` | `CreateCustomerUseCase.create` | 201 + `Location` + `CustomerResponse { id }` | REUSE — contract unchanged; the status is not added to the creation response (parent spec, user's answer) |
| `GET` | `/api/v1/customers/{customerId}` | `getCustomer` | `GetCustomerUseCase.get` | 200 + `CustomerDetailsResponse` (now with `status`) + `Cache-Control: no-store` | CHANGE — body gains one field |

Adding a field to a response is backward-compatible (`api-rest.md` § Versioning): no `/v2`,
`operationId` unchanged.

**Access:** unchanged — both public, as their approved cases decided. The project has no
`SecurityFilterChain`.

**OpenAPI.** `GetCustomerOpenApiDocs` — no change to the composed annotation itself: the 200
`@ApiResponse` already points at `CustomerDetailsResponse`'s schema, and the new field documents
itself through its `@Schema`. `CreateCustomerOpenApiDocs`, `CustomerApi` — unchanged.

## 2 · DTOs

| DTO | Direction | Fields | Shape validation | State |
|---|---|---|---|---|
| `dto/CustomerDetailsResponse` | output | existing `id`, `name`, `securityNumber`, `birthDate`, `registeredAt` **+ `status` (`String`)**, last component | — | CHANGE |
| `dto/CreateCustomerRequest` | input | unchanged | unchanged | REUSE |
| `dto/CustomerResponse` | output | unchanged — `id` | — | REUSE |

`status` carries `@Schema(description = "KYC lifecycle status of the customer",
allowableValues = {"KYC_IN_PROGRESS", "ACTIVE", "REJECTED_BY_KYC"}, example =
"KYC_IN_PROGRESS", requiredMode = REQUIRED)`. Typed `String` in the DTO, not `CustomerStatus`:
no domain type in the public contract (`api-rest.md` § DTOs). The values are the enum's names
exactly.

**Translation** — `CustomerMapper.toDetailsResponse(Customer)` (CHANGE) adds
`customer.status().name()`. No other mapper method changes.

**Masking** — re-derived over the changed DTO by `@.claude/rules/logging.md` § Masking
candidates. `status`: no match by type or by name — not a candidate. Existing fields keep their
annotations (`securityNumber` DOCUMENT, `birthDate` DATE); `CustomerDetailsResponse` already
implements `LogMask`. `CreateCustomerRequest` and `CustomerResponse` re-checked: no unmasked
match.

## 3 · Error map

No new row. The status adds no caller-facing error:

| Situation | Domain exception | Status | `errorCode` | Extra fields |
|---|---|---|---|---|
| any existing row of `UC-001` and `UC-003` | unchanged | unchanged | unchanged | unchanged |

Unreachable through either endpoint, listed so no test asserts them:
`CUSTOMER_STATUS_REQUIRED` — `register` always sets the status, and a persisted row always has
one (`20-persistencia.md`); `KYC_REQUEST_FIELD_REQUIRED` — the event is built from an
already-valid `Customer`. A failure to record the KYC request surfaces as the existing
unmapped 500 with `traceId`, and the creation is rolled back. `ApiExceptionHandler` — REUSE,
no change.

## 4 · Pagination, idempotency, and dependencies

**Pagination** — none: no collection.

**Idempotency** — unchanged. `POST /api/v1/customers` keeps `@Idempotent` and the shared
`idempotency_keys` table. A replay returns the stored 201 **without running the use case**, so
it records no second KYC request — the parent spec's repetition fact holds by construction. The
stored replay body is `CustomerResponse { id }`, unchanged: no status and no personal data
enter `idempotency_keys`.

**Schema requirements** — read by `persistence-architect` in its first pass:

| Requirement | Why |
|---|---|
| none from transport | `status` is a domain field `10-dominio.md` modeled; its column is persistence's own decision |

**Dependencies to add** — none. springdoc 3.1.1, `micrometer-tracing-bridge-otel`,
`spring-boot-starter-validation` and `spring-boot-starter-aspectj` already in `pom.xml`.

## 5 · Contract test cases

| Case | Request | Asserts |
|---|---|---|
| Read carries status | `GET /api/v1/customers/{id}` of a customer created through `POST` | 200, body `status = KYC_IN_PROGRESS`, other fields as in `UC-003` |
| Read of each value | `GET` with the use case stubbed to return a customer in `ACTIVE`, then `REJECTED_BY_KYC` | 200, `status` equals the enum name |
| Creation unchanged | valid `POST` + key | 201, `Location`, body has `id` only — no `status` field |
| Replay records nothing new | two identical `POST`s with the same key | same 201 twice; exactly one KYC request recorded (asserted at integration level, `40-testes.md`) |

Every existing case of `UC-001-create-customer/30-rest.md` § 5 and
`UC-003-get-customer/30-rest.md` § 5 stays and must stay green.

## 6 · Personal data

`personal-data.md` § How to verify grep 1 over the changed DTO: `status` — no hit.

| Field | Form | Receiver and reason |
|---|---|---|
| `status` | not personal data under § Masking candidates | — |

The two existing full-value fields of `CustomerDetailsResponse` (`securityNumber`, `birthDate`)
keep the decision recorded in `UC-003-get-customer/30-rest.md` § 6; this case changes neither.

## Design patterns

none — no force in the spec, no symptom on disk: one field added to an existing DTO and one
argument to an existing static mapper call.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-003-get-customer` | `CustomerDetailsResponse` gains `status` (`String`, enum name); `CustomerMapper.toDetailsResponse` maps it | callers must see where the customer is in KYC (parent spec, user's answer) |
