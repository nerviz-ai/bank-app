# UC-001 · REST adapter

> Partial of `docs/use-cases/UC-001-create-customer/`. Owner: `rest-api-architect`.
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
| Divergences from `00-caso-de-uso.md` | none — "reference only" is honoured as a body carrying `id` alone, plus `Location`; `api-rest.md` § Success statuses requires a body on 201, and a reduced representation satisfies both |

Paths below are relative to `src/main/java/dev/nerviz/bankapp/infrastructure/rest/`. No
`@RestController` exists in the project yet: every class here is NEW.

## 1 · Endpoints

| Method | Path | `operationId` | Inbound port | Success |
|---|---|---|---|---|
| `POST` | `/api/v1/customers` | `createCustomer` | `CreateCustomerUseCase.create` | 201 + `Location: /api/v1/customers/{customerId}` (relative, built from the controller's path constant) + `CustomerResponse` |

No `GET`: the parent spec has no read use case, and an endpoint with no port behind it is a
guessed contract. `Location` still points at the resource's canonical URI; it answers 404
until a read case exists (`## Out of scope` of the parent spec).

`POST /api/v1/customers` requires `Idempotency-Key`: it creates a resource
(`api-rest.md` § Idempotency). The business duplicate guard (unique security number) does
not replace it — it answers the second call with 409, where the protocol requires the
retried call to get the original 201 back.

**Access:** public (parent spec). The project has no Spring Security filter chain; the
idempotency key's caller identity is the constant `anonymous` — see block 4.

**OpenAPI.** `@Tag(name = "Customers")` and `@CreateCustomerOpenApiDocs` on the contract
interface `CustomerApi`, alone — no Spring mapping or binding annotation there.
`@CreateCustomerOpenApiDocs` composes `@Operation(summary, operationId = "createCustomer")`,
the `@RequestBody` attribute with an `@ExampleObject`
(`{"name":"Maria Silva","securityNumber":"12345678901","birthDate":"1990-05-17"}`),
`@Parameter` for the `Idempotency-Key` header (UUID, example), and one `@ApiResponse` per
status of block 3 — 201, 400, 409, 422, 500 — each error pointing at `ProblemDetail`.
`CustomerController` implements `CustomerApi` and carries `@RestController`,
`@RequestMapping(CustomerController.PATH)`, `@PostMapping`, `@Idempotent`, `@Valid`,
`@RequestBody`. Shapes: `templates/Api.java.example`, `templates/OpenApiDocs.java.example`,
`templates/Controller.java.example`.

## 2 · DTOs

| DTO | Direction | Fields | Shape validation |
|---|---|---|---|
| `dto/CreateCustomerRequest` | input | `name` (`String`), `securityNumber` (`String`), `birthDate` (`LocalDate`, ISO-8601 `yyyy-MM-dd`) | `@NotNull` on each field — presence only |
| `dto/CustomerResponse` | output | `id` (`UUID`) | — |

Every field carries `@Schema` with `description`, `example` and explicit `requiredMode =
REQUIRED`. `securityNumber`'s `@Schema` documents `pattern = "^[0-9]{11}$"` for the client;
the pattern is **not** enforced with `@Pattern` — see block 3.

**Why presence only.** Every format rule (blank or out-of-range name, 11 digits, date limits)
is a domain invariant with its own `errorCode` (`10-dominio.md` § 2). A `@NotBlank`,
`@Size` or `@Pattern` on the same field would fire first, as a `violations` 400 without
`errorCode`, and make the domain's code unreachable through this endpoint. Kept to
`@NotNull`, the domain answers each format error with a 400 carrying its `errorCode`.

**Masking** — re-derived over the DTO fields by `@.claude/rules/logging.md` § Masking
candidates. Same result as `10-dominio.md` § 1, no divergence:

| DTO | Field | Match | Annotation |
|---|---|---|---|
| `CreateCustomerRequest` | `securityNumber` | name (National id) | `@MaskSensitiveData(maskedType = MaskedType.DOCUMENT)` |
| `CreateCustomerRequest` | `birthDate` | name (Birth) | `@MaskSensitiveData(maskedType = MaskedType.DATE)` |
| `CreateCustomerRequest` | `name` | none — counter-list | — |
| `CustomerResponse` | `id` | none — counter-list | — |

`CreateCustomerRequest` implements `LogMask`. Both live in `commons.logging`, which today
holds only `package-info.java` — `commons-logging-installer` provides them before the
executor runs (`/new-feature` pre-flight).

**Translation** — `CustomerMapper`, static, manual (`templates/RestMapper.java.example`):
`toCommand(CreateCustomerRequest) → CreateCustomerCommand` and
`toResponse(CustomerId) → CustomerResponse`. No domain type in the public signature of the
controller.

## 3 · Error map

| Situation | Domain exception | Status | `errorCode` | Extra fields |
|---|---|---|---|---|
| Security number already registered | `SecurityNumberAlreadyRegisteredException` (`ConflictException`) | 409 | `SECURITY_NUMBER_ALREADY_REGISTERED` | `errorCode` |
| Customer not strictly over 18 | `BusinessRuleViolationException` | 422 | `CUSTOMER_UNDERAGE` | `errorCode` |
| Name blank after trimming | `ValidationException` | 400 | `CUSTOMER_NAME_REQUIRED` | `errorCode` |
| Name outside 2 to 120 characters after trimming | `ValidationException` | 400 | `CUSTOMER_NAME_LENGTH` | `errorCode` |
| Security number not exactly 11 digits | `ValidationException` | 400 | `SECURITY_NUMBER_INVALID` | `errorCode` |
| Birth date in the future | `ValidationException` | 400 | `BIRTH_DATE_IN_FUTURE` | `errorCode` |
| Age over 130 | `ValidationException` | 400 | `BIRTH_DATE_TOO_OLD` | `errorCode` |
| Same key, different body | `ConflictException` | 409 | `IDEMPOTENCY_KEY_REUSED` | `errorCode` |
| Same key, request still in progress | `ConflictException` | 409 + `Retry-After` | `IDEMPOTENCY_KEY_IN_PROGRESS` | `errorCode` |
| `Idempotency-Key` missing or not a UUID | — (`MissingIdempotencyKeyException`, structural) | 400 | **none** | `violations` |
| `name`, `securityNumber` or `birthDate` missing | — (bean validation, `@NotNull`) | 400 | **none** | `violations` |
| Unreadable JSON, `birthDate` not `yyyy-MM-dd`, wrong type | — (deserialization) | 400 | **none** | — |
| Unmapped | — | 500 | **none** | `traceId` |

Unreachable through this endpoint, listed so no test asserts them:
`CUSTOMER_ID_REQUIRED` and `REGISTERED_AT_REQUIRED` (set by the use case, never by the
client); `CUSTOMER_NAME_REQUIRED`, `SECURITY_NUMBER_REQUIRED` and `BIRTH_DATE_REQUIRED` for a
**missing** field — intercepted by `@NotNull`. `CUSTOMER_NAME_REQUIRED` stays reachable for a
blank or whitespace-only `name`.

`detail` never echoes the security number or the birth date. `ApiExceptionHandler`
(`templates/ApiExceptionHandler.java.example`) returns `ProblemDetail`; `traceId` comes from
the Micrometer `Tracer`.

## 4 · Pagination, idempotency, and dependencies

**Pagination** — none: no collection endpoint.

**Idempotency** — `@Idempotent` + `IdempotencyAspect`, first `Idempotency-Key` endpoint of the
project, so every piece is NEW:

| Half | Where it lives | In this partial |
|---|---|---|
| Header presence and format | `idempotent/IdempotencyKeyInterceptor` + `idempotent/MissingIdempotencyKeyException`, registered by `idempotent/IdempotencyWebConfig` for handlers annotated `@Idempotent` | yes — 400 above |
| Annotation and aspect | `idempotent/Idempotent`, `idempotent/IdempotencyAspect`, `idempotent/IdempotencyRequestHasher`, `idempotent/IdempotencyResponseCodec`, `idempotent/AspectInvocationException` (`templates/IdempotencyAspect.java.example`) | yes |
| Claim (own transaction), then effect + stored response (one transaction) | `application/shared/IdempotentExecution` and its port | no — declared; shape in `persistence-architect`'s templates |
| `idempotency_keys` table | `20-persistencia.md` | no — **handed off to `persistence-architect`** |

Caller identity stored with the key: the constant `anonymous` — the endpoint is public and
the project has no principal to read. When a later case secures the endpoint, the identity
becomes the principal's, in that case's impact section.

**Schema requirements** — read by `persistence-architect` in its first pass:

| Requirement | Why |
|---|---|
| `idempotency_keys` table, shared — NEW, first in the project (route, caller, body hash, status, stored response, lease, TTL ≥ 24 h) | `POST /api/v1/customers` requires `Idempotency-Key` (`api-rest.md` § Idempotency) |
| A retention for expired `idempotency_keys` rows — the body hash and stored response are kept ≥ 24 h, never forever | TTL in `api-rest.md` § Idempotency; the stored response holds only `id`, so no personal data — the request body is stored as a hash only |
| `customers` expected volume: not given; no collection endpoint, so pagination doesn't depend on it | — |

**Dependencies to add** — the executor applies them to `pom.xml`:

| Artifact | Why | Version |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-aspectj` | `IdempotencyAspect` (`@Aspect`, `@Around`) — Boot 4's name for the former `spring-boot-starter-aop` | managed by the Spring Boot BOM (parent 4.1.1) |

Already declared in `pom.xml`, versions kept: `springdoc-openapi-starter-webmvc-ui` 3.1.1,
`micrometer-tracing-bridge-otel` (BOM), `spring-boot-starter-validation` (BOM).

## 5 · Contract test cases

| Case | Request | Asserts |
|---|---|---|
| Happy creation | valid `POST` + key | 201, `Location` = `/api/v1/customers/{id}`, body `id` equals the id in `Location`, no other field |
| Underage — 18th birthday today | `birthDate` = today − 18 years | 422 **and** `errorCode = CUSTOMER_UNDERAGE` |
| Duplicate security number | second `POST`, new key, same `securityNumber` | 409 **and** `errorCode = SECURITY_NUMBER_ALREADY_REGISTERED` |
| Security number with 10 digits / with punctuation | `"1234567890"` / `"123.456.789-01"` | 400 **and** `errorCode = SECURITY_NUMBER_INVALID` |
| Blank name | `name = "   "` | 400 **and** `errorCode = CUSTOMER_NAME_REQUIRED` |
| Name of 1 / 121 characters | | 400 **and** `errorCode = CUSTOMER_NAME_LENGTH` |
| Future birth date | `birthDate` = tomorrow | 400 **and** `errorCode = BIRTH_DATE_IN_FUTURE` |
| Age 131 | `birthDate` = today − 131 years | 400 **and** `errorCode = BIRTH_DATE_TOO_OLD` |
| Missing field | `POST` without `securityNumber` | 400, `violations[0].field = securityNumber`, `errorCode` absent |
| Unparseable date | `birthDate = "17/05/1990"` | 400, no `errorCode` |
| Missing key | `POST` without `Idempotency-Key` | 400, `violations`, no `errorCode` |
| Idempotent replay | two identical `POST`s, same key | one customer created; both 201 with identical `Location` and body |
| Reused key | same key, different body | 409 **and** `errorCode = IDEMPOTENCY_KEY_REUSED` |
| Unmapped failure | use case throws an unexpected exception | 500, `traceId` present, generic `detail` |
| No leak | any error above | `detail` contains neither the security number nor the birth date |

The clock is fixed in the test so "today" is deterministic. Body-shape fixture:
`templates/error-responses.json.example`.

## 6 · Personal data

none — the only response body is `CustomerResponse { id }`, and an aggregate's `id` is on the
counter-list of `@.claude/rules/logging.md` § Masking candidates. `personal-data.md` § How to
verify grep 1 over the response DTOs designed here: no hit. Security number and birth date
enter in the request and never leave in a response or an error `detail`.

## Design patterns

none — no force in the spec, no symptom on disk: one resource, one operation, no
`@RestController` in `src/` to repeat. The idempotency aspect is `api-rest.md`'s mandated
mechanism, not a pattern chosen here.

## Impact on approved use cases

none — UC-001 is the first case.
