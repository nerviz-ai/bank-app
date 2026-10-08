---
status: implemented
---

# UC-003 — Get customer

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block below holds only the final decisions — one
value per fact, after divergences were resolved — and points to the partial that details
it. The partial is read for detail; where a block here and its partial disagree, **this
file wins** and the losing value is listed in § Resolved divergences.

| Partial | Owner | Status |
|---|---|---|
| `00-caso-de-uso.md` | `use-case-design` | ✅ |
| `10-dominio.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ |
| `32-seguranca.md` | `security-architect` | — skipped: `Access` public, project has no filter chain |
| `28-cliente-http.md` | `http-client-architect` | — not applicable (no port of kind `external HTTP`) |
| `25-mensageria.md` | `messaging-architect` | — not applicable (no event) |
| `35-jobs.md` | `jobs-architect` | — not applicable (no Form B, no scheduled trigger, no deferred job) |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | HTTP, inbound REST adapter — public |
| Input | `customerId` (UUID) in the path; no body |
| Output | full record — `id`, `name`, `securityNumber` (in clear), `birthDate` (in clear), `registeredAt` |
| Side effects | 0 — read by primary key of `customers` |
| Transaction | read-only, opens at `GetCustomerUseCase.get`, closes before the response |
| Repetition | a read; repeating it changes nothing — no idempotency mechanism |
| Events | none |
| Accepted risk | public endpoint returning a national identifier and a birth date in clear to anyone holding an id, with no identity check and no record of who read it — user's explicit decision, `00-caso-de-uso.md` header |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-3.

| Decision | Value |
|---|---|
| Aggregate | `Customer` — REUSE, unchanged; the adapter rebuilds it with `Customer.rehydrate(...)` |
| Value objects | `CustomerId`, `SecurityNumber` — REUSE |
| Use case | concrete `application/usecase/customer/GetCustomerUseCase` (NEW) — `Customer get(GetCustomerCommand)`, `@Transactional(readOnly = true)`; command `GetCustomerCommand(UUID id)` (NEW), null → `ValidationException` `CUSTOMER_ID_REQUIRED` |
| Output port | `application/port/CustomerRepository` · kind `persistence` — CHANGE: gains `Optional<Customer> findById(CustomerId id)` |
| Exception family | REUSE — no new class |
| `errorCode`s | `NotFoundException`: `CUSTOMER_NOT_FOUND` (NEW code, message `"customer not found"`, no personal data) · `ValidationException`: `CUSTOMER_ID_REQUIRED` (REUSE) |
| Masking | `securityNumber` and `birthDate` already masked in `Customer.toString()` / `SecurityNumber.toString()` |
| Events | none |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-6.

| Decision | Value |
|---|---|
| Engine | PostgreSQL 16 — REUSE |
| Table | `customers` — REUSE, no change |
| Query | `findById` → `CustomerJpaRepository.findById(UUID)` (inherited) mapped with `CustomerPersistenceMapper::toDomain`; served by `pk_customers` |
| Classes | `infrastructure/persistence/customer/CustomerRepositoryJpaAdapter` — CHANGE (implements `findById`, no `@Transactional`, no exception translation); `CustomerJpaRepository`, `CustomerPersistenceMapper`, `CustomerEntity` — REUSE |
| Migrations | none |
| Configuration | none — UC-001's stands |
| Personal data at rest | no new column; `security_number`, `birth_date` erasure stays `BL-02` |
| Declared dependencies | none |
| Compose service | `postgres` already declared — none pending |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.5 Messaging — conditional

none — `10-dominio.md` § 4: no event.

---

## 3.6 Jobs — conditional

none — no Form B outbox, no scheduled trigger, no deferred scheduled job.

---

## 4. REST API — implement after 2

Detail: `30-rest.md` § 1-6.

| Decision | Value |
|---|---|
| Endpoint | `GET /api/v1/customers/{customerId}`, `operationId` `getCustomer` → `200` + `CustomerDetailsResponse` + `Cache-Control: no-store` |
| Classes | `infrastructure/rest/` — `CustomerController` CHANGE (`get(@PathVariable UUID customerId)`, `@GetMapping("/{customerId}")`); `CustomerApi` CHANGE (`get` + `@GetCustomerOpenApiDocs`); `openapi/GetCustomerOpenApiDocs` NEW; `dto/CustomerDetailsResponse` NEW (implements `LogMask`, `@MaskSensitiveData` DOCUMENT on `securityNumber`, DATE on `birthDate`, `toString()` = `mask(this)`); `CustomerMapper` CHANGE (`toDetailsResponse(Customer)`, `toGetCommand(UUID)`); `ApiExceptionHandler` REUSE |
| `CustomerResponse` | unchanged — stays UC-001's `{ id }` |
| Errors | `CUSTOMER_NOT_FOUND` `404` + `errorCode` · `customerId` not a UUID `400` + `violations[0].field = customerId`, no `errorCode` · unmapped `500` + `traceId` |
| Idempotency | none — `GET` |
| Pagination | none — single resource |
| Personal data in responses | `securityNumber` **full value**, `birthDate` **full value** — receiver: the bank's internal front-end, showing the customer's registration data to the customer or an operator; reason: the screen displays both values (`30-rest.md` § 6) |
| Dependencies | none |

---

## 4.5 Security — conditional

none — skipped by step 3b's rule: `Access` is public and the project has no
`SecurityFilterChain` (no `spring-boot-starter-security` in `pom.xml`; the only grep hit is
`HstsHeaderFilter`'s `@ConditionalOnMissingClass`). Public by the user's decision, recorded in
`00-caso-de-uso.md` with the accepted risk.

---

## 5. Tests — validates 1-4

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `GetCustomerCommandTest`, `GetCustomerUseCaseTest`, `CustomerDetailsResponseTest` (masking) — NEW |
| Slice | `CustomerControllerTest` — CHANGE: `GET` cases of `30-rest.md` § 5, `@MockitoBean GetCustomerUseCase` |
| Integration | `CustomerRepositoryJpaAdapterIT` — CHANGE (`findsSavedCustomerById`, `returnsEmptyForUnknownId`); `GetCustomerIT` — NEW (`createdLocationResolvesToTheCustomer`) |
| Fixtures | `CustomerFixtures` — REUSE |
| Coverage gate | already wired — `ArchitectureTest` and JaCoCo `check` exist |
| Test dependencies | none |

---

## Design patterns

none — every partial (`10`, `20`, `30`) records `none`: no force in the spec, no symptom on
disk.

| Force or symptom | Pattern | Classes and interfaces | Decided in |
|---|---|---|---|

---

## Impact on approved use cases

| Approved case | Change | Decided in | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `CustomerRepository` gains `findById(CustomerId)`; `CustomerRepositoryJpaAdapter` implements it | `10-dominio.md`, `20-persistencia.md` | — (adds no precondition) |
| `UC-001-create-customer` | `CustomerController` gains `get`; `CustomerApi` gains `get` + `@GetCustomerOpenApiDocs`; `CustomerMapper` gains `toDetailsResponse`, `toGetCommand` | `30-rest.md` | — (adds no precondition) |
| `UC-001-create-customer` | the `Location` returned by creation now answers 200 instead of 404 | `00-caso-de-uso.md`, `30-rest.md` | — (adds no precondition) |
| `UC-001-create-customer` | `CustomerControllerTest` and `CustomerRepositoryJpaAdapterIT` gain the read cases | `40-testes.md` | — (adds no precondition) |

UC-001's `POST`, its body, its idempotency and its stored replay body are untouched. Logged in
`docs/use-cases/UC-001-create-customer/CHANGELOG.md`.

---

## Out of scope

| Item | Owner | What the project does not guarantee until it ships |
|---|---|---|
| Authentication of the caller, audit of who read which customer | not requested — public by decision | anyone holding an id reads the customer's security number and birth date; no trail of reads |
| Deletion / anonymization of a customer | `BL-02` (existing) | `security_number` and `birth_date` are kept indefinitely and remain readable through this endpoint |
| Listing or searching customers, updating a customer | not requested | — |

No partial deferred anything: every `Deferred` block is `none`.

---

## Implementation order (checklist)

[x] 1. Domain exceptions — none new; `CUSTOMER_NOT_FOUND` is a code on `NotFoundException`
[x] 2. Value objects — REUSE
[x] 3. Aggregate — REUSE
[x] 4. Events — n/a, none
[x] 5. Use case command — `GetCustomerCommand`
[x] 6. Use case — `GetCustomerUseCase` (concrete, `@Transactional(readOnly = true)`)
[x] 7. Output ports — `CustomerRepository.findById`
[x] 8. Migrations — none
[x] 9. Persistence entities — REUSE
[x] 10. Spring Data interfaces — REUSE (`findById` inherited)
[x] 11. Repository adapters — `CustomerRepositoryJpaAdapter.findById`
[x] 12. Persistence properties — none
[x] 13. DTOs + manual mapper — `CustomerDetailsResponse`, `CustomerMapper` methods
[x] 14. Controller + contract interface + `GetCustomerOpenApiDocs` — `ApiExceptionHandler` REUSE
[x] 15. Idempotency — n/a, `GET`
[x] 16. Test fixtures — `CustomerFixtures` REUSE
[x] 17. Unit tests
[x] 18. Integration + contract tests
[x] 19. `./mvnw verify` — green build, coverage gate

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Exception for "customer not found" | `NEW or REUSE` (`00-caso-de-uso.md`) | REUSE — plain `NotFoundException`, `errorCode` `CUSTOMER_NOT_FOUND`, no subclass (first occurrence) | `10-dominio.md` |
| Output DTO for the full record | "new DTO or a change to `CustomerResponse`" (`00-caso-de-uso.md`) | NEW `CustomerDetailsResponse`; `CustomerResponse` stays `{ id }` so personal data stays out of UC-001's 201 and of `idempotency_keys` | `30-rest.md` |
| Path and success status | "read of one customer by its id, over HTTP" (`00-caso-de-uso.md`) | `GET /api/v1/customers/{customerId}` → 200 + `Cache-Control: no-store` | `30-rest.md` |
