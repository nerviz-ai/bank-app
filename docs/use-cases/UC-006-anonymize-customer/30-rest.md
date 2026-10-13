# UC-006 · REST adapter

> Partial of `docs/use-cases/UC-006-anonymize-customer/`. Owner: `rest-api-architect`.
> Inherits the canonical names from `00-caso-de-uso.md` and the ports from `10-dominio.md` —
> doesn't reinvent them.
> Contains no code. Reference shapes in
> `.claude/skills/rest-api-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/api-rest.md`.

| Field | Value |
|---|---|
| Parent spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Resource | `customers` (REUSE — `CustomerController`, `CustomerApi`) |
| Version prefix | `/api/v1` |
| Divergences from `10-dominio.md` | none in the port. **Divergence from `00-caso-de-uso.md`, decided here by the user:** the situation "no customer with that id" answers **204**, the same as success, not "not found" — `@.claude/rules/api-rest.md` § Success statuses (`DELETE` of a nonexistent resource is 204, for the whole API). The domain still raises `CustomerNotFoundException`; the controller translates it to 204 for this operation only |

## 1 · Endpoints

| Method | Path | `operationId` | Inbound port | Success |
|---|---|---|---|---|
| `DELETE` | `/api/v1/customers/{customerId}` | `anonymizeCustomer` | `AnonymizeCustomerUseCase.anonymize` | 204, no body |

- Path variable `customerId` — the name `GET /api/v1/customers/{customerId}` (`UC-003`) already
  uses; the request's `{id}` is the same placeholder, spelled as the resource already spells it.
- `DELETE` is the verb the request names. Its effect is the erasure of the resource's personal
  data, not the removal of the row: `GET` of the same id keeps answering 200 (`UC-003`) with the
  anonymized representation. Repeated `DELETE` gives the same final state and the same 204 —
  `@.claude/rules/api-rest.md` § Verbs.
- **Unknown id → 204** (user's decision): `CustomerController.anonymize` catches
  `CustomerNotFoundException` from the use case and returns 204. Catching it in the controller,
  not in `ApiExceptionHandler`, keeps `GET`'s 404 for the same exception untouched.
- No `Idempotency-Key`: `DELETE` is idempotent by contract, and the use case guarantees it by
  state comparison (`10-dominio.md` invariant 5) — `@.claude/rules/api-rest.md` § Idempotency.
- No `ETag`/`If-Match`: the request carries no representation to be stale against; a concurrent
  writer is caught by the row's version and answers 409 (block 3).
- Access (401, 403) is `32-seguranca.md`'s; this partial lists the rows so block 5 is complete.

**OpenAPI docs** — `infrastructure/rest/openapi/AnonymizeCustomerOpenApiDocs.java` (NEW),
composed annotation on `CustomerApi#anonymize` alone, shape of `GetCustomerOpenApiDocs`:
`@Operation(summary = "Anonymizes one customer by id", operationId = "anonymizeCustomer")`,
`@Parameter` `customerId` in path with the example UUID, and one `@ApiResponse` each for 204,
400, 401, 403, 409, 422, 500. The 204 description says it covers success, already anonymized,
and unknown id. The 401 and 403 descriptions follow `32-seguranca.md` § 6.

`CustomerApi` (CHANGE) gains `ResponseEntity<Void> anonymize(UUID customerId)` with
`@AnonymizeCustomerOpenApiDocs`. `CustomerController` (CHANGE) gains `@DeleteMapping("/{customerId}")`
with `@PathVariable UUID customerId`, and the constructor gains `AnonymizeCustomerUseCase`.

## 2 · DTOs

| DTO | Direction | Fields | Shape validation |
|---|---|---|---|
| — (no request body) | input | `customerId` — path variable, `UUID` | type conversion only: a non-UUID is 400 (`MethodArgumentTypeMismatchException`, existing handler) |
| — (no response body) | output | — | — |
| `CustomerDetailsResponse` (`UC-003`, CHANGE) | output | `securityNumber` and `birthDate` **absent from the JSON** for an anonymized customer: `@JsonInclude(JsonInclude.Include.NON_NULL)` on both components, `requiredMode = NOT_REQUIRED`, descriptions say "absent once the customer is anonymized". `name` carries `ANONYMIZED` then; its `@Schema` description says so | — |

Translation in `CustomerMapper` (CHANGE):

- `toAnonymizeCommand(UUID customerId)` → `new AnonymizeCustomerCommand(customerId)` (NEW method).
- `toDetailsResponse(Customer)` switches on `customer.identity()`:
  `Identified` → `securityNumber().value()` and `birthDate()` as today; `Anonymized` → both
  `null`, which `NON_NULL` drops from the body. The `null` stays inside the DTO, never reaches
  the domain.

Masking re-derived over every DTO field touched (`@.claude/rules/logging.md` § Masking candidates):
`securityNumber` (`DOCUMENT`) and `birthDate` (`DATE`) already carry `@MaskSensitiveData` and
`CustomerDetailsResponse` already implements `LogMask` — REUSE. **`name` is personal data**
(`@.claude/rules/personal-data.md` § What counts, full name) and is not masked in
`CustomerDetailsResponse` today — the domain partial flagged it too; recorded in Impact with
`@MaskSensitiveData(maskedType = MaskedType.NAME)`. The new
operation has no DTO: `customerId` is an opaque id, not a candidate.

## 3 · Error map

All mappings exist in `ApiExceptionHandler` — REUSE, no change.

| Situation | Domain exception | Status | `errorCode` | Extra fields |
|---|---|---|---|---|
| anonymized | — | 204 | — | no body |
| already anonymized | — (use case writes nothing) | 204 | — | no body |
| no customer with that id | `CustomerNotFoundException` — **caught in the controller** | 204 | — | no body (user's decision, see header) |
| KYC verification still in progress | `BusinessRuleViolationException` | 422 | `CUSTOMER_KYC_IN_PROGRESS` | `errorCode` |
| another writer changed the customer first | `ConflictException` | 409 | `CUSTOMER_CONCURRENT_UPDATE` | `errorCode` |
| `customerId` not a UUID | — (`MethodArgumentTypeMismatchException`, structural) | 400 | **none** | — |
| caller not identified | — (security) | 401 | per `32-seguranca.md` § 6 | per `32-seguranca.md` |
| caller without `DATA_PROTECTION_OFFICER` | — (security) | 403 | per `32-seguranca.md` § 6 | per `32-seguranca.md` |
| unmapped | — | 500 | **none** | `traceId` |

`CUSTOMER_ID_REQUIRED` and `CUSTOMER_ANONYMIZED_NAME_MISMATCH` / `CUSTOMER_IDENTITY_REQUIRED` /
`ANONYMIZED_AT_REQUIRED` are unreachable through this endpoint: the path variable is never
`null` once it converts, and the other three are programming-error guards no request can trip.

## 4 · Pagination, idempotency, and dependencies

**Pagination** — none: no collection.

**Idempotency** — by contract of `DELETE` and by state comparison in the use case; no
`Idempotency-Key`, no claim, no stored response.

**Schema requirements** — read by `persistence-architect`:

| Requirement | Why |
|---|---|
| none from transport | no key table, no collection to size. The schema change this case needs (`security_number` and `birth_date` nullable, the anonymization time) comes from `10-dominio.md`, not from transport |

**Dependencies to add** — none. `springdoc-openapi-starter-webmvc-ui`,
`micrometer-tracing-bridge-otel` and `spring-boot-starter-validation` are already in `pom.xml`.

## 5 · Contract test cases

| Case | Request | Asserts |
|---|---|---|
| Anonymize | `DELETE /api/v1/customers/{id}` as officer, customer `ACTIVE` | 204, empty body; use case called once with the id |
| Already anonymized | same, use case returns normally (nothing written) | 204, empty body |
| Unknown id | same, use case throws `CustomerNotFoundException` | 204, empty body — never 404 |
| KYC in progress | use case throws `BusinessRuleViolationException("CUSTOMER_KYC_IN_PROGRESS")` | 422 **and** `errorCode = CUSTOMER_KYC_IN_PROGRESS` |
| Concurrent writer | use case throws `ConflictException("CUSTOMER_CONCURRENT_UPDATE")` | 409 **and** `errorCode = CUSTOMER_CONCURRENT_UPDATE` |
| Malformed id | `DELETE /api/v1/customers/not-a-uuid` as officer | 400, `errorCode` absent, use case never called |
| `UC-003` read of an anonymized customer | `GET /api/v1/customers/{id}`, use case returns an anonymized `Customer` | 200; `name = ANONYMIZED`; **no** `securityNumber` key and **no** `birthDate` key in the JSON; `id`, `registeredAt`, `status` present |
| `UC-003` read of an identified customer | unchanged | 200 with `securityNumber` and `birthDate` — regression guard |
| Unauthenticated, wrong role | — | owned by `32-seguranca.md` § 10 |

## 6 · Personal data

| Field | Form | Receiver and reason |
|---|---|---|
| `DELETE` response | none — no body | — |
| `CustomerDetailsResponse.securityNumber`, `.birthDate` (`UC-003`) | full value while identified (decision of `UC-003`, unchanged); **absent** once anonymized | receiver and reason as recorded by `UC-003` `30-rest.md` § 6 — this case only removes the values |
| `CustomerDetailsResponse.name` (`UC-003`) | full value while identified; `ANONYMIZED` once anonymized | same receiver as `UC-003` — the front-end shows the customer's name |

## Design patterns

none — no force in the spec, no symptom on disk. One more operation on an existing controller
and one `switch` over a two-variant sealed type in the mapper.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-003-get-customer` | `CustomerDetailsResponse.securityNumber` and `.birthDate` become optional in the OpenAPI schema (`NOT_REQUIRED`) and are omitted from the JSON for an anonymized customer; `CustomerMapper.toDetailsResponse` switches on `identity()` | the user's answer: an anonymized customer is still found, with those fields empty. **Contract change for `UC-003` clients** — the `openapi.json` diff job will flag it |
| `UC-003-get-customer` | `CustomerDetailsResponse.name` gains `@MaskSensitiveData(maskedType = MaskedType.NAME)` | `name` is personal data and was logged in clear by the global REST aspect |
| `UC-001-create-customer`, `UC-003-get-customer` | `CustomerController` gains a constructor parameter (`AnonymizeCustomerUseCase`) | the new operation lives on the existing resource controller |
