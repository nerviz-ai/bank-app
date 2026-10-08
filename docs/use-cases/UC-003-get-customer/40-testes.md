# UC-003 · Tests

> Partial of `docs/use-cases/UC-003-get-customer/`. Owner: `test-architect`.
> Inherits the invariants from `10-dominio.md`, the queries from `20-persistencia.md`, and
> the contract cases from `30-rest.md` — doesn't reinvent them.
> Contains no code. Reference shapes in `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`.

| Field | Value |
|---|---|
| Partials read | `00`, `10`, `20`, `30` (`32`, `28`, `25`, `35` do not exist — skipped or not applicable) |
| Engine in integration tests | PostgreSQL 16, ephemeral container — REUSE `TestcontainersConfiguration` |
| Invariants from `10-dominio.md` | 3 (§ 2) |
| Invariants with a named test | 3 |
| `@SpringBootTest` without `@ActiveProfiles("test")` | none — survey returned no hit |

Test paths relative to `src/test/java/dev/nerviz/bankapp/`.

## 1 · Distribution by level

Each behavior at **one** level. Repetition across levels is a bug in the distribution.

| Behavior | Level | Why here |
|---|---|---|
| `GetCustomerCommand` rejects a null id | Application unit | Record invariant; no context |
| Found returns the aggregate; absent raises `CUSTOMER_NOT_FOUND` | Application unit | Orchestration: `CustomerRepository` substituted by a double |
| `findById` re-reads a saved customer with the same values; unknown id is empty | Integration | Mapping and SQL only fail against the real engine |
| HTTP contract (200 + `Cache-Control`, 404, 400, 500, no leak in errors) | Web slice | **Cases defined in `30-rest.md` § 5** — here only the level and the data. Use case substituted by a double, so `Test`, not `IT` |
| `CustomerDetailsResponse` masks `securityNumber` and `birthDate` in `toString()` | Adapter unit | Masking derivation match (`@.claude/rules/logging.md` § Masking candidates); proves the mask hides the value, which ArchUnit cannot |
| `Location` returned by creation resolves to the created customer | Integration (full context) | Crosses UC-001 and UC-003 end to end through the real controller, use case and database — the one row of `30-rest.md` § 5 a slice cannot prove |
| Dependency direction and names | Architecture | `ArchitectureTest` — REUSE, runs once for the project |

`Customer.rehydrate`'s structural invariants are already covered by UC-001's `CustomerTest`;
not repeated.

## 2 · Cases per test class

| Class | Level | State | Methods |
|---|---|---|---|
| `application/usecase/customer/GetCustomerCommandTest` | Application | NEW | `rejectsNullId` — asserts `ValidationException` **and** `CUSTOMER_ID_REQUIRED` |
| `application/usecase/customer/GetCustomerUseCaseTest` | Application | NEW | `returnsCustomerWhenFound` · `rejectsUnknownId` — asserts `NotFoundException` **and** `CUSTOMER_NOT_FOUND`, and that the message carries no personal data |
| `infrastructure/persistence/customer/CustomerRepositoryJpaAdapterIT` | Integration | CHANGE (UC-001's class) | gains `findsSavedCustomerById` (every field equal, `securityNumber` and `birthDate` included) · `returnsEmptyForUnknownId` |
| `infrastructure/rest/CustomerControllerTest` | Web slice | CHANGE (UC-001's class) | gains one method per `GET` row of `30-rest.md` § 5: `returnsCustomerFullRecordWithNoStore` · `answersNotFoundForUnknownId` · `rejectsIdThatIsNotUuid` · `hidesInternalFailureOnReadBehindTraceId` · `neverEchoesPersonalDataInNotFound`. `GetCustomerUseCase` added as a `@MockitoBean` |
| `infrastructure/rest/dto/CustomerDetailsResponseTest` | Adapter unit | NEW | `masksSecurityNumberAndBirthDateInToString` — asserts neither the raw 11 digits nor the raw `yyyy-MM-dd` appears |
| `infrastructure/rest/GetCustomerIT` | Integration (full context) | NEW | `createdLocationResolvesToTheCustomer` — `POST` (with `Idempotency-Key`) then `GET` the returned `Location`: 200, same `id`, same `securityNumber` |

No `GetCustomerOpenApiDocs` test: an annotation with no behavior. No `CustomerMapper` test of
its own: package-private, branch-free, exercised by the slice.

Each method asserts the exception **and** the stable code, never just the type.

`IT` suffix on the integration ones — without it failsafe doesn't run them.

## 3 · Data and doubles

**Factory** — `CustomerFixtures` (REUSE). `CustomerFixtures.customer()` gives the valid
aggregate (`Maria Silva`, `12345678901`, `1990-05-17`, `FIXED_CLOCK` `2026-01-15T10:00:00Z`);
`requestJson()` feeds the `POST` of `GetCustomerIT`. No second factory.

| Element | In the test |
|---|---|
| `CustomerRepository` | Double in `GetCustomerUseCaseTest`: `findById` returns `Optional.of(customer())` in the found case, `Optional.empty()` in the unknown case |
| `GetCustomerUseCase` | `@MockitoBean` in `CustomerControllerTest`: returns `customer()`, or throws `NotFoundException("CUSTOMER_NOT_FOUND", …)`, or a `RuntimeException` for the 500 case |
| Unknown id | a fixed, well-formed UUID never saved (e.g. `00000000-0000-7000-8000-000000000000`) |
| `Clock` | `CustomerFixtures.FIXED_CLOCK` — only where a customer is built; the read itself depends on no time |
| Database | Ephemeral PostgreSQL 16 container — REUSE `TestcontainersConfiguration`; Flyway runs `V1`, `V2` |

`GetCustomerIT` and `CustomerRepositoryJpaAdapterIT` build their own unique security numbers per
test, or clean `customers` between tests, so `uq_customers_security_number` never collides
across methods.

## 4 · Coverage and gaps

| Invariant from `10-dominio.md` § 2 | Test that guarantees it |
|---|---|
| 1 — a customer with the requested id exists | `GetCustomerUseCaseTest.rejectsUnknownId` + `CustomerControllerTest.answersNotFoundForUnknownId` |
| 2 — the id is not null | `GetCustomerCommandTest.rejectsNullId` (and UC-001's `CustomerId` coverage) |
| 3 — a rehydrated customer satisfies the structural invariants | UC-001's `CustomerTest` — REUSE; `findsSavedCustomerById` exercises the path |

**Deliberately left uncovered:** `CustomerDetailsResponse`'s accessors, `GetCustomerOpenApiDocs`,
and `CustomerMapper`'s trivial mapping — structure with no branches.

No open gap.

## 5 · Test dependencies

none — JUnit, AssertJ, Mockito, Spring test slices and the Testcontainers PostgreSQL module
are already in `pom.xml`.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerControllerTest` gains the `GET` methods above and a `@MockitoBean GetCustomerUseCase` | the controller now has two operations; the slice context needs both use cases |
| `UC-001-create-customer` | `CustomerRepositoryJpaAdapterIT` gains `findsSavedCustomerById` and `returnsEmptyForUnknownId` | the adapter gained `findById` |
