---
status: implemented
---

# UC-001 — Create customer

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
| `35-jobs.md` | `jobs-architect` | — not applicable (no Form B, no scheduled trigger; the `idempotency_keys` prune went to `BL-01` by the user's decision) |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | HTTP, inbound REST adapter — public |
| Input | `name` (trimmed, 2–120), `securityNumber` (exactly 11 digits, no check digit, unique), `birthDate` (18th birthday strictly before today; age ≤ 130; not in the future) |
| Output | reference only — `id` and `Location` |
| Side effects | 1 — `INSERT` into `customers` |
| Transaction | opens at `CreateCustomerUseCase.create`, closes before the response |
| Repetition | same security number never creates a second customer (409); same `Idempotency-Key` replays the original 201 |
| Events | none |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-5.

| Decision | Value |
|---|---|
| Aggregate | `Customer(CustomerId id, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant registeredAt)` — `register(id, name, securityNumber, birthDate, Clock)` runs the date rules; `rehydrate(...)` does not |
| Value objects | `CustomerId` (UUID v7, generated in `CreateCustomerUseCase` with `Generators.timeBasedEpochGenerator()`), `SecurityNumber` (`value()`, `masked()`, `toString()` masked) |
| Today | `LocalDate.now(clock)`; clock bean `Clock.systemUTC()` in `infrastructure/config/ClockConfig.java` (NEW) |
| Use case | concrete `application/usecase/customer/CreateCustomerUseCase` — `CustomerId create(CreateCustomerCommand)`; command `CreateCustomerCommand(String name, String securityNumber, LocalDate birthDate)`, `toString()` masked |
| Output port | `application/port/CustomerRepository` · kind `persistence` — `existsBySecurityNumber(SecurityNumber)`, `save(Customer)` |
| Exception family | NEW — `DomainException`, `NotFoundException`, `ValidationException`, `BusinessRuleViolationException`, `ConflictException`, plus `SecurityNumberAlreadyRegisteredException extends ConflictException` |
| `errorCode`s | `ValidationException`: `CUSTOMER_ID_REQUIRED`, `CUSTOMER_NAME_REQUIRED`, `CUSTOMER_NAME_LENGTH`, `SECURITY_NUMBER_REQUIRED`, `SECURITY_NUMBER_INVALID`, `BIRTH_DATE_REQUIRED`, `BIRTH_DATE_IN_FUTURE`, `BIRTH_DATE_TOO_OLD`, `REGISTERED_AT_REQUIRED` · `BusinessRuleViolationException`: `CUSTOMER_UNDERAGE` · `SecurityNumberAlreadyRegisteredException`: `SECURITY_NUMBER_ALREADY_REGISTERED` · `ConflictException`: `CUSTOMER_PERSISTENCE_CONFLICT` (any other integrity violation on `customers`) |
| Masking | `securityNumber` and `birthDate` masked in aggregate, command and request DTO; never in an exception message |
| Events | none |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-6. Migration SQL: § 4 of that partial — the executor
materializes it verbatim.

| Decision | Value |
|---|---|
| Engine | PostgreSQL 16 |
| Table | `customers` — `id uuid` PK, `name varchar(120)`, `security_number char(11)`, `birth_date date`, `registered_at timestamptz`, `version bigint` |
| Business key | `uq_customers_security_number UNIQUE (security_number)` — adapter uses `saveAndFlush` and translates that constraint to `SecurityNumberAlreadyRegisteredException` |
| Classes | `infrastructure/persistence/customer/` — `CustomerEntity` (extends `AssignedIdEntity<UUID>`, `@JdbcTypeCode(SqlTypes.CHAR)` on `security_number`), `CustomerPersistenceMapper`, `CustomerJpaRepository`, `CustomerRepositoryJpaAdapter`; `infrastructure/persistence/shared/AssignedIdEntity` NEW |
| Migrations | `V1__create_customers.sql`, `V2__create_idempotency_keys.sql` — `<N>` re-checked on disk |
| Shared tables | `idempotency_keys` — NEW (first creation `POST`): `IdempotencyKeyPort` + types in `application/port/`, `IdempotentExecution` in `application/shared/`, store in `infrastructure/persistence/idempotency/` |
| Configuration | Hikari pool 10, timeouts, query timeout 5 s, UTC, Flyway validation — `20-persistencia.md` § 5 |
| Personal data at rest | `security_number`, `birth_date` — life of the customer record; erasure is `BL-02` |
| Declared dependencies | none |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.5 Messaging — conditional

none — `10-dominio.md` § 4: no event.

---

## 3.6 Jobs — conditional

none — no Form B outbox, no scheduled trigger. The `idempotency_keys` prune is `BL-01`.

---

## 4. REST API — implement after 2

Detail: `30-rest.md` § 1-6.

| Decision | Value |
|---|---|
| Endpoint | `POST /api/v1/customers`, `operationId` `createCustomer` → `201` + relative `Location: /api/v1/customers/{id}` + `CustomerResponse { id }` |
| Classes | `infrastructure/rest/` — `CustomerApi` (docs only: `@Tag`, `@CreateCustomerOpenApiDocs`), `CreateCustomerOpenApiDocs`, `CustomerController`, `CustomerMapper`, `ApiExceptionHandler`; `dto/CreateCustomerRequest` (`@NotNull` only, implements `LogMask`, `@MaskSensitiveData` DOCUMENT / DATE), `dto/CustomerResponse` |
| Errors | domain `ValidationException` `400` + `errorCode` · `CUSTOMER_UNDERAGE` `422` · `SECURITY_NUMBER_ALREADY_REGISTERED` `409` · bean validation / unreadable body / missing key `400` without `errorCode` · unmapped `500` + `traceId` |
| Idempotency | `Idempotency-Key` required — `@Idempotent` + `IdempotencyAspect` and the interceptor in `infrastructure/rest/idempotent/`, caller identity `anonymous`; 409 `IDEMPOTENCY_KEY_REUSED` / `IDEMPOTENCY_KEY_IN_PROGRESS` |
| Pagination | none — no collection |
| Personal data in responses | none — body carries `id` only |
| Dependencies | add `org.springframework.boot:spring-boot-starter-aspectj` (BOM-managed); springdoc 3.1.1, tracing bridge, validation already in `pom.xml` |

---

## 4.5 Security — conditional

none — skipped by step 3b's rule: `Access` is public and the project has no
`SecurityFilterChain` (no `spring-boot-starter-security` in `pom.xml`). The endpoint is public
by the user's decision, recorded in `00-caso-de-uso.md`.

---

## 5. Tests — validates 1-4

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `SecurityNumberTest`, `CustomerTest` (fixed clock `2026-01-15T10:00:00Z`), `CreateCustomerUseCaseTest`, `CreateCustomerCommandTest`, `CreateCustomerRequestTest`, `IdempotencyKeyInterceptorTest` |
| Slice | `CustomerControllerTest` — `@WebMvcTest`, cases of `30-rest.md` § 5 except replay |
| Integration | `CustomerRepositoryJpaAdapterIT`, `IdempotencyKeyStoreIT`, `CreateCustomerIdempotencyIT` — Testcontainers Postgres |
| Existing test | `BankAppApplicationTests` gets `@ActiveProfiles("test")` |
| Coverage gate | 80% lines / 70% branches — wired by `test-architect` setup mode |
| Test dependencies | none |

---

## Design patterns

none — every partial (`10`, `20`, `30`) records `none`: no force in the spec, no symptom on
disk.

| Force or symptom | Pattern | Classes and interfaces | Decided in |
|---|---|---|---|

---

## Impact on approved use cases

none — UC-001 is the first case.

| Approved case | Change | Decided in |
|---|---|---|

---

## Out of scope

| Item | Owner | What the project does not guarantee until it ships |
|---|---|---|
| Prune of expired `idempotency_keys` rows | `BL-01` | Expired rows are never deleted; the table grows one row per creation call (no personal data in it) |
| Deletion / anonymization of a customer | `BL-02` | `security_number` and `birth_date` are kept indefinitely; there is no erasure path |
| Read, update, list of customers | not requested | `Location` points at a URI that answers 404 until a read case exists |

---

## Implementation order (checklist)

[x] 1. Domain exceptions — family of five + `SecurityNumberAlreadyRegisteredException` (`10-dominio.md` § 2)
[x] 2. Value objects — `CustomerId`, `SecurityNumber`
[x] 3. Aggregate — `Customer` with `register` / `rehydrate`
[x] 4. Events — n/a, none
[x] 5. Use case command — `CreateCustomerCommand`
[x] 6. Use case — `CreateCustomerUseCase` (concrete, `@Transactional`), `ClockConfig`
[x] 7. Output ports — `CustomerRepository`; `IdempotencyKeyPort` and its types
[x] 8. Migrations — `V1`, `V2` from `20-persistencia.md` § 4
[x] 9. Persistence entities — `AssignedIdEntity`, `CustomerEntity`, `IdempotencyKeyEntity`
[x] 10. Spring Data interfaces
[x] 11. Repository adapters — `CustomerRepositoryJpaAdapter`, `IdempotencyKeyStore`
[x] 12. Persistence properties
[x] 13. DTOs + manual mapper
[x] 14. Controller + contract interface + `CreateCustomerOpenApiDocs` + `ApiExceptionHandler`
[x] 15. Idempotency interceptor + aspect + `IdempotentExecution` (first time: store + component) + `spring-boot-starter-aspectj`
[x] 16. Test fixtures — `CustomerFixtures`
[x] 17. Unit tests
[x] 18. Integration + contract tests; `@ActiveProfiles("test")` on `BankAppApplicationTests`
[x] 19. `./mvnw verify` — green build, coverage gate

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Translation of an integrity violation on `customers` other than the security-number unique | no `errorCode` in `10-dominio.md` | `ConflictException` `CUSTOMER_PERSISTENCE_CONFLICT` — asked by `20-persistencia.md` § 3, carried into the domain block, owner `10-dominio.md` | `20-persistencia.md` (requirement) → `10-dominio.md` block |
| Shape of `name` and `birthDate` | "typed field, if any — shape open" (`00-caso-de-uso.md`) | `String` and `LocalDate` | `10-dominio.md` |
| Output DTO | "output DTO for the reference, if any" (`00-caso-de-uso.md`) | `CustomerResponse { id }` | `30-rest.md` |
