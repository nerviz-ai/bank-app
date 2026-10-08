# UC-003 · REST adapter

> Partial of `docs/use-cases/UC-003-get-customer/`. Owner: `rest-api-architect`.
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
| Divergences from `00-caso-de-uso.md` | none — the "output DTO for the full record" row is resolved as a **new** DTO, `CustomerDetailsResponse`; `CustomerResponse` stays UC-001's `{ id }` (block 2) |

Paths below are relative to `src/main/java/dev/nerviz/bankapp/infrastructure/rest/`.
`CustomerController` already exists (UC-001): this case adds one operation to it.

## 1 · Endpoints

| Method | Path | `operationId` | Inbound port | Success |
|---|---|---|---|---|
| `GET` | `/api/v1/customers/{customerId}` | `getCustomer` | `GetCustomerUseCase.get` | 200 + `CustomerDetailsResponse` + `Cache-Control: no-store` |

The path is the canonical URI UC-001 already returns in `Location`
(`CustomerController.BASE_PATH + "/" + id`) — that `Location` now resolves.

`{customerId}` is bound as `UUID`. No `Idempotency-Key`: `GET` is safe and idempotent by
contract (`api-rest.md` § Idempotency). No `ETag`: no update case exists, so there is no
`If-Match` for it to serve.

**`Cache-Control: no-store`** — decided here: the body carries a national identifier and a
birth date in clear (block 6), on a public endpoint. `no-store` keeps them out of browser and
intermediary caches; it costs nothing on a read nobody caches today. Set by the controller on
the `ResponseEntity` (`CacheControl.noStore()`).

**Access:** public (parent spec, user's decision with the accepted risk recorded there). The
project has no `SecurityFilterChain`.

**OpenAPI.** `@GetCustomerOpenApiDocs` on `CustomerApi#get`, alone — beside the existing
`@CreateCustomerOpenApiDocs`; `@Tag(name = "Customers")` already on the interface.
`@GetCustomerOpenApiDocs` (NEW, `openapi/GetCustomerOpenApiDocs.java`) composes
`@Operation(summary = "Returns one customer by id", operationId = "getCustomer")`,
`@Parameter(name = "customerId", in = PATH, required = true, description = "Customer id as
returned by createCustomer", example = "018f9a2e-6b7d-7e2a-9c1f-8a0d5e2b7c11")`, and one
`@ApiResponse` per status of block 3 — 200 (schema `CustomerDetailsResponse`), 400, 404, 500 —
each error pointing at `ProblemDetail`. `CustomerController` gains the method implementing it,
carrying `@GetMapping("/{customerId}")` and `@PathVariable UUID customerId`. Shapes:
`templates/Api.java.example`, `templates/OpenApiDocs.java.example`,
`templates/Controller.java.example`.

## 2 · DTOs

| DTO | Direction | Fields | Shape validation |
|---|---|---|---|
| path variable `customerId` | input | `UUID` | type conversion only — not a UUID is a 400 (block 3) |
| `dto/CustomerDetailsResponse` | output | `id` (`UUID`), `name` (`String`), `securityNumber` (`String`, 11 digits), `birthDate` (`LocalDate`, ISO-8601 `yyyy-MM-dd`), `registeredAt` (`Instant`, ISO-8601 UTC) | — |

Every field carries `@Schema` with `description`, `example` and `requiredMode = REQUIRED`.

**Why a new DTO and not `CustomerResponse` with more fields.** `CustomerResponse { id }` is
UC-001's 201 body, chosen there as "reference only" by the user, and it is the body
`idempotency_keys` stores for a replay — no personal data in that table today. Widening it
would put the security number and birth date in the creation response **and** in the stored
replay body, a change to an approved case nobody asked for. `CustomerDetailsResponse` follows
`naming.md`'s `<Resource>Response` with the qualifier `Details` distinguishing the full
representation from the reference.

**Masking** — re-derived over the DTO fields by `@.claude/rules/logging.md` § Masking
candidates. Same result as `10-dominio.md` § 1, no divergence:

| DTO | Field | Match | Annotation |
|---|---|---|---|
| `CustomerDetailsResponse` | `securityNumber` | name (National id) | `@MaskSensitiveData(maskedType = MaskedType.DOCUMENT)` |
| `CustomerDetailsResponse` | `birthDate` | name (Birth) | `@MaskSensitiveData(maskedType = MaskedType.DATE)` |
| `CustomerDetailsResponse` | `id`, `name`, `registeredAt` | none — counter-list | — |

`CustomerDetailsResponse` implements `LogMask` and overrides `toString()` with `mask(this)`, as
`CreateCustomerRequest` does: `GlobalHttpMethodLogAspect` logs every response by default, and
without it the security number and birth date would be logged raw on every read.

Existing DTOs re-checked: `CreateCustomerRequest` masks both candidates; `CustomerResponse`
carries `id` only. No unmasked match.

**Translation** — `CustomerMapper` (CHANGE), static, manual: gains
`toDetailsResponse(Customer) → CustomerDetailsResponse` (`securityNumber().value()` for the
clear value) and `toGetCommand(UUID) → GetCustomerCommand`. No domain type in the controller's
public signature.

## 3 · Error map

| Situation | Domain exception | Status | `errorCode` | Extra fields |
|---|---|---|---|---|
| No customer with that id | `NotFoundException` | 404 | `CUSTOMER_NOT_FOUND` | `errorCode` |
| `customerId` is not a UUID | — (`MethodArgumentTypeMismatchException`, structural) | 400 | **none** | `violations[0].field = customerId` |
| Unmapped | — | 500 | **none** | `traceId` |

Every row is already handled by `ApiExceptionHandler` (REUSE): `NotFoundException` → 404,
`MethodArgumentTypeMismatchException` → 400 with `violations`, `Exception` → 500 with
`traceId`. No change to the handler.

Unreachable through this endpoint, listed so no test asserts them: `CUSTOMER_ID_REQUIRED` — a
path variable is never absent, and a non-UUID never reaches the command. The structural
invariant codes of `Customer.rehydrate` would surface only for a row written outside the
application, as a 400 nobody can cause through this API.

`detail` is the fixed `"customer not found"` — no personal data.

## 4 · Pagination, idempotency, and dependencies

**Pagination** — none: single resource, no collection.

**Idempotency** — none: `GET` is idempotent by contract; no `@Idempotent`, no key.

**Schema requirements** — read by `persistence-architect` in its first pass:

| Requirement | Why |
|---|---|
| none — lookup by the primary key of `customers` | no table or column this transport needs that the domain didn't model |

**Dependencies to add** — none. springdoc 3.1.1, `micrometer-tracing-bridge-otel` and
`spring-boot-starter-validation` already in `pom.xml`, versions kept.

## 5 · Contract test cases

| Case | Request | Asserts |
|---|---|---|
| Found | `GET /api/v1/customers/{id}` of an existing customer | 200, body `id`, `name`, `securityNumber` (11 digits, in clear), `birthDate` (`yyyy-MM-dd`), `registeredAt`; header `Cache-Control: no-store` |
| Not found | `GET` with a well-formed UUID nobody has | 404 **and** `errorCode = CUSTOMER_NOT_FOUND` |
| Not a UUID | `GET /api/v1/customers/abc` | 400, `violations[0].field = customerId`, `errorCode` absent |
| Unmapped failure | use case throws an unexpected exception | 500, `traceId` present, generic `detail` |
| No leak in errors | 404 | `detail` contains no security number nor birth date |
| No leak in logs | found | `CustomerDetailsResponse.toString()` masks `securityNumber` and `birthDate` |
| `Location` resolves | `POST` (UC-001) then `GET` the returned `Location` | 200, same `id` |

Body-shape fixture: `templates/error-responses.json.example`.

## 6 · Personal data

`personal-data.md` § How to verify grep 1 over `CustomerDetailsResponse`: two hits,
`securityNumber` and `birthDate`, both matching `@.claude/rules/logging.md` § Masking
candidates by name.

| Field | Form | Receiver and reason |
|---|---|---|
| `securityNumber` | **full value** | The bank's internal front-end, which shows the customer's registration data to the customer or to an operator; the screen displays the identifier itself. User's decision, recorded in `00-caso-de-uso.md` § Personal data, with the accepted risk of a public endpoint |
| `birthDate` | **full value** | Same receiver and reason — the screen displays the birth date |

`@MaskSensitiveData` on both fields covers the log line, not the body: in the body both leave
the process in clear, by the decision above. `Cache-Control: no-store` (block 1) keeps them out
of caches.

## Design patterns

none — no force in the spec, no symptom on disk: one more operation on an existing controller,
the mapper gains two static methods, the handler is reused unchanged.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerController` gains `get(UUID)`; `CustomerApi` gains `get` with `@GetCustomerOpenApiDocs`; `CustomerMapper` gains `toDetailsResponse` and `toGetCommand` | same resource, same controller; UC-001's `POST`, its body and its idempotency are untouched |
| `UC-001-create-customer` | the `Location` UC-001 returns now answers 200 instead of 404 | this case is the read UC-001's out-of-scope line waited for |
