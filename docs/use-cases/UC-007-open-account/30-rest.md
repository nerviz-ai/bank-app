# UC-007 · REST adapter

> Partial of `00-use-case.md` and `10-domain.md`. Owner: `rest-api-architect`. Inbound adapter
> package: `infrastructure/rest` (found via `@RestController`). Norms: `@.claude/rules/api-rest.md`.

## 1 · Endpoints

| Method | Path | Mapping `version` | `operationId` | Inbound port | Success |
|---|---|---|---|---|---|
| `POST` | `/api/v1/customers/{customerId}/accounts` | `1+` | `openAccount` | `OpenAccountUseCase.open` | 201 + `Location: /api/v1/accounts/{accountId}` (relative) + `AccountResponse` |

An account is created under its owner: the owner is part of the path, not the body, so the
request carries no body. No `GET`: no read use case exists; `Location` answers 404 until one
does (`00-use-case.md` § Out of scope).

`Idempotency-Key` **required**: the `POST` creates a resource (`api-rest.md` § Idempotency), and
the domain allows many accounts per customer — without the key, a lost response retried opens a
second account.

New controller `AccountController` (mapping `/customers/{customerId}/accounts`) implementing
`AccountApi`; `@Tag(name = "Accounts")` and `@OpenAccountOpenApiDocs` on `AccountApi` alone —
`@PostMapping`, `@PathVariable`, `@Idempotent` on the controller. `@OpenAccountOpenApiDocs`
documents 201, 400, 401, 403, 404, 409, 422, 500, each error as `ProblemDetail`, the
`Idempotency-Key` header and the `customerId` path parameter with examples.

**Actor.** The controller builds `Actor` from the authenticated principal and passes it in
`OpenAccountCommand`; which claim maps to `CustomerId` and how `OPERATOR` is detected is
`security-architect`'s (`32-security.md`).

## 2 · DTOs

| DTO | Field | Type | Shape validation | From / to |
|---|---|---|---|---|
| — (no request body) | `customerId` path variable | `UUID` | type conversion — a non-UUID is 400 | `OpenAccountCommand.customerId` |
| `AccountResponse` (record) | `id` | `UUID` | — | `Account.id().value()` |
| | `customerId` | `UUID` | — | `Account.owner().value()` |
| | `balance` | `BigDecimal`, scale 2, serialized as a JSON number (`0.00`) | — | `Account.balance().amount()` |
| | `currency` | `String`, always `"USD"` | — | constant — the domain has a single currency |
| | `status` | `String` (`ACTIVE` · `INACTIVE`) | — | `Account.status().name()` |

Mapper `AccountMapper`, in the project's mechanism (`@.claude/rules/mapping.md`).

**Masking** re-derived over every field (`@.claude/rules/logging.md` § Masking candidates):
no match by type or name. `AccountResponse` does not implement `LogMask`. Matches
`10-domain.md` — no divergence.

## 3 · Error map

| Situation | Exception | Status | `errorCode` | Body |
|---|---|---|---|---|
| `customerId` not a UUID | `MethodArgumentTypeMismatchException` | 400 | `VALIDATION_FAILED` (project handler, as existing) | `ProblemDetail` |
| `Idempotency-Key` missing | `MissingIdempotencyKeyException` (REUSE) | 400 | as existing (`UC-001`) | `ProblemDetail` |
| no token / invalid token | filter chain | 401 | `32-security.md` | `ProblemDetail` |
| authenticated, neither a customer nor `OPERATOR` | filter chain / method security | 403 | `32-security.md` | `ProblemDetail` |
| customer absent, **or** not the actor's | `CustomerNotFoundException` (REUSE) | 404 | `CUSTOMER_NOT_FOUND` | `ProblemDetail` |
| customer not `ACTIVE` or anonymized | `BusinessRuleViolationException` | 422 | `CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT` | `ProblemDetail` |
| same key, different request | `ConflictException` | 409 | `IDEMPOTENCY_KEY_REUSED` | `ProblemDetail` |
| same key, still in progress | `ConflictException` | 409 + `Retry-After` | `IDEMPOTENCY_KEY_IN_PROGRESS` | `ProblemDetail` |
| unsupported API version | as `UC-001` | 400 | as existing | `ProblemDetail` |
| unexpected | any | 500 | `INTERNAL_ERROR` | `ProblemDetail` + `traceId` |

`ACCOUNT_ID_REQUIRED` and `MONEY_*` are unreachable through this endpoint: the id is generated
and the balance is `Money.zero()`. No bean validation duplicates a domain invariant.

## 4 · Pagination, idempotency, and dependencies

**Pagination:** none — single-resource creation.

**Idempotency:** REUSE — `idempotency_keys`, `IdempotencyKeyStore`, `IdempotentExecution`,
`IdempotencyAspect`, `IdempotencyKeyInterceptor` and the prune job exist since `UC-001`/`UC-002`.
The endpoint carries `@Idempotent`; command and use case signature unchanged.

**CHANGE — caller scope of the key.** `IdempotencyAspect` stores every key under the constant
caller `anonymous` (`IdempotencyAspect.java:36`). This endpoint is authenticated: two customers
choosing the same key would replay each other's 201 — another customer's account id. The aspect
scopes the key by the authenticated principal's name when one exists, and keeps `anonymous` for
a public endpoint. Recorded in § Impact.

**Schema requirements:** none — `idempotency_keys` already has a caller column for the scope
(`UC-001`'s `20-persistence.md`); this case adds no transport table or column.

**Dependencies:** none new — springdoc (`pom.xml:146`), validation and tracing already declared.

## 5 · Contract test cases

| Scenario | Request | Expected |
|---|---|---|
| opened by the owner | customer actor, own ACTIVE customer, new key | 201, `Location` `/api/v1/accounts/{id}`, body `balance = 0.00`, `currency = USD`, `status = ACTIVE` |
| opened by an operator | `OPERATOR` actor, any ACTIVE customer | 201 |
| replay | same key, same request | 201 with the **same** `id`; one account stored |
| reused key | same key, other `customerId` | 409 **and** `errorCode = IDEMPOTENCY_KEY_REUSED` |
| missing key | no `Idempotency-Key` | 400 |
| malformed id | `customerId = abc` | 400 |
| not found | unknown UUID | 404 **and** `errorCode = CUSTOMER_NOT_FOUND` |
| another customer's | customer actor, someone else's `customerId` | 404 **and** `errorCode = CUSTOMER_NOT_FOUND` |
| not eligible | customer `KYC_IN_PROGRESS`, `REJECTED_BY_KYC` or anonymized | 422 **and** `errorCode = CUSTOMER_NOT_ELIGIBLE_FOR_ACCOUNT` |
| unexpected | use case throws `RuntimeException` | 500 with `traceId` |

401 and 403 cases: `32-security.md` § 10.

## 6 · Personal data

none — `AccountResponse` carries ids, an amount, a currency and a status; grep 1 of
`@.claude/rules/personal-data.md` § How to verify finds no match.

## 7 · Versioning

inherited from `UC-001-create-customer` — `ApiVersioningConfig` supports `1`, no deprecation.
This case adds a new endpoint at `1+`; nothing breaking, no new version.

## 8 · Admission

none — `00-use-case.md` has no limit.

## Design patterns

none — one controller, one mapper; no translation repeated across resources beyond the existing
handler.

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `IdempotencyAspect` scopes the key by the authenticated principal's name; public endpoints keep `anonymous` | a key shared across callers on an authenticated endpoint would replay another caller's response | — (no precondition; `UC-001`'s public endpoint behaves the same) |
