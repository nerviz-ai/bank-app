# UC-001 · Tests

> Partial of `docs/use-cases/UC-001-create-customer/`. Owner: `test-architect`.
> Inherits the invariants from `10-dominio.md`, the queries from `20-persistencia.md`, and
> the contract cases from `30-rest.md` — doesn't reinvent them.
> Contains no code. Reference shapes in `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`.

| Field | Value |
|---|---|
| Partials read | `00`, `10`, `20`, `30` (no `25`, `28`, `32`, `35` — none applies) |
| Engine in integration tests | PostgreSQL 16 (`postgres:16-alpine`), ephemeral container via the existing `TestcontainersConfiguration` |
| Invariants from `10-dominio.md` | 11 |
| Invariants with a named test | 11 (two of them set by the use case, covered at aggregate level only) |

Test paths mirror production under `src/test/java/dev/nerviz/bankapp/`.

## 1 · Distribution by level

| Behavior | Level | Why here |
|---|---|---|
| `SecurityNumber` format, trim, masking | Domain unit | Value-object rule, no context |
| `Customer` name, date and age rules, leap day, `rehydrate` skipping the date rules | Domain unit | Aggregate invariants, fixed clock |
| Duplicate security number aborts before saving; format checked before the lookup | Application unit | Orchestration, `CustomerRepository` replaced by a double |
| Command and request DTO never print the security number or birth date | Unit | Masking — `@.claude/rules/logging.md` § Masking candidates |
| `Idempotency-Key` header presence and format | Unit | Interceptor component, no context |
| HTTP contract (201, 400, 409, 422, 500) | Web slice | **Cases defined in `30-rest.md` § 5** — here only level and data. Use case is a double, so `Test`, not `IT` |
| Mapping, `char(11)`, re-read of the aggregate, unique constraint translation | Integration | Only fails against the real engine |
| Idempotency store: claim, replay, reuse, in-progress, reclaim, release | Integration | Two-transaction shape and PK collision only exist in the real database |
| Idempotent replay end to end (aspect + store + use case) — one customer for two identical calls | Integration | The aspect, the store and the transaction meet only in the full context |
| Dependency direction and names | Architecture | Once per project — `ArchitectureTest` not installed yet; `test-architect` setup mode at the `/new-feature` pre-flight |

Contract replay cases of `30-rest.md` § 5 ("Idempotent replay", "Reused key") run in
`CreateCustomerIdempotencyIT`, not in the web slice: the slice doesn't load the aspect or a
database, and a replay asserted against a double proves nothing.

**Change outside this case's classes:** `BankAppApplicationTests` runs `@SpringBootTest`
without `@ActiveProfiles("test")` — it gets the annotation (`@.claude/rules/testing.md`
§ Slices and context). Not an approved case's file: the bootstrap's own.

## 2 · Cases per test class

Fixed clock in every domain and application test: `2026-01-15T10:00:00Z`, UTC — today is
`2026-01-15`.

| Class | Level | Methods |
|---|---|---|
| `domain/model/SecurityNumberTest` | Domain | `acceptsElevenDigits` · `trimsSurroundingWhitespace` (`" 12345678901 "`) · `rejectsMissingValue` → `SECURITY_NUMBER_REQUIRED` · `rejectsMalformedValue` (parameterized: `"1234567890"`, `"123456789012"`, `"123.456.789-01"`, `"1234567890a"`, `""`, `"   "`) → `SECURITY_NUMBER_INVALID` · `masksAllButLastTwoDigits` (`"*********01"`) · `toStringIsMasked` |
| `domain/model/CustomerTest` | Domain | `registersWithTrimmedNameAndClockInstant` · `rejectsMissingId` → `CUSTOMER_ID_REQUIRED` · `rejectsBlankName` (parameterized: `null`, `""`, `"   "`) → `CUSTOMER_NAME_REQUIRED` · `rejectsNameOutsideLength` (parameterized: `"A"`, `" A "`, 121 × `"a"`) → `CUSTOMER_NAME_LENGTH` · `acceptsNameAtBounds` (parameterized: 2 and 120 characters) · `rejectsMissingSecurityNumber` → `SECURITY_NUMBER_REQUIRED` · `rejectsMissingBirthDate` → `BIRTH_DATE_REQUIRED` · `rejectsFutureBirthDate` (`2026-01-16`) → `BIRTH_DATE_IN_FUTURE` · `rejectsAgeOver130` (`1895-01-15`, 131st birthday today) → `BIRTH_DATE_TOO_OLD` · `acceptsAge130` (`1895-01-16`) · `rejectsCustomerNotStrictlyOver18` (parameterized: `2008-01-15` — 18th birthday today, `2008-01-16`, `2010-06-01`) → `CUSTOMER_UNDERAGE` · `accepts18thBirthdayYesterday` (`2008-01-14`) · `appliesFebruary28AnniversaryForLeapDayBirth` (parameterized over the clock: born `2008-02-29`; clock `2026-02-28` → `CUSTOMER_UNDERAGE`, clock `2026-03-01` → accepted) · `rehydrateDoesNotReapplyDateRules` (born `2015-03-10`) · `rejectsMissingRegisteredAtOnRehydrate` → `REGISTERED_AT_REQUIRED` |
| `application/usecase/customer/CreateCustomerUseCaseTest` | Application | `createsCustomerAndReturnsItsId` (asserts the saved aggregate's fields and that the returned id is the saved one) · `rejectsAlreadyRegisteredSecurityNumber` → `SECURITY_NUMBER_ALREADY_REGISTERED`, `save` never called · `rejectsMalformedSecurityNumberBeforeLookup` → `SECURITY_NUMBER_INVALID`, `existsBySecurityNumber` never called · `rejectsUnderageWithoutSaving` → `CUSTOMER_UNDERAGE` |
| `application/usecase/customer/CreateCustomerCommandTest` | Unit | `toStringMasksSecurityNumberAndBirthDate` — raw `12345678901` and `1990-05-17` absent |
| `infrastructure/rest/dto/CreateCustomerRequestTest` | Unit | `maskedRepresentationHidesSecurityNumberAndBirthDate` — renders the DTO the way `GlobalHttpMethodLogAspect` does (`LogMask`), asserts both raw values absent and `name` present |
| `infrastructure/rest/idempotent/IdempotencyKeyInterceptorTest` | Unit | `rejectsMissingKey` · `rejectsMalformedKey` (parameterized: `"abc"`, `""`, `"123"`) · `acceptsUuidKey` · `ignoresHandlerWithoutIdempotent` — shape `templates/IdempotencyKeyInterceptorTest.java.example` |
| `infrastructure/rest/CustomerControllerTest` | Web slice | One method per row of `30-rest.md` § 5, except "Idempotent replay" and "Reused key" (below): `createsCustomerReturningLocationAndIdOnly` · `rejectsUnderage` · `rejectsDuplicateSecurityNumber` · `rejectsInvalidSecurityNumber` (parameterized: `"1234567890"`, `"123.456.789-01"`) · `rejectsBlankName` · `rejectsNameOutsideLength` (parameterized: 1 and 121 characters) · `rejectsFutureBirthDate` · `rejectsAgeOver130` · `rejectsMissingField` (parameterized: `name`, `securityNumber`, `birthDate` — `violations[0].field` equals it, `errorCode` absent) · `rejectsUnparseableBirthDate` · `rejectsMissingIdempotencyKey` · `hidesInternalFailureBehindTraceId` · `neverEchoesPersonalDataInErrors` (parameterized over the error cases above) — shape `templates/ControllerTest.java.example` |
| `infrastructure/persistence/customer/CustomerRepositoryJpaAdapterIT` | Integration | `persistsAndRereadsTheSameAggregate` (reads back through `CustomerJpaRepository` + mapper: name, 11-digit number, birth date, `registeredAt`) · `reportsWhetherSecurityNumberExists` · `rejectsDuplicateSecurityNumber` → **`SECURITY_NUMBER_ALREADY_REGISTERED`** asserted on the exception, not only the type — proves the flush inside the adapter — shape `templates/PersistenceIT.java.example` |
| `infrastructure/persistence/idempotency/IdempotencyKeyStoreIT` | Integration | `claimsNewKey` · `replaysCompletedResponse` · `rejectsSameKeyWithDifferentBody` → `IDEMPOTENCY_KEY_REUSED` · `rejectsKeyStillInProgress` → `IDEMPOTENCY_KEY_IN_PROGRESS` · `reclaimsKeyAfterLeaseExpires` · `releaseFreesKeyForRetry` |
| `infrastructure/rest/CreateCustomerIdempotencyIT` | Integration (`@SpringBootTest`, MockMvc, `@ActiveProfiles("test")`) | `replaysOriginalResponseForSameKey` — two identical `POST`s, both 201 with identical `Location` and body, exactly one row in `customers` · `rejectsSameKeyWithDifferentBody` → 409, `IDEMPOTENCY_KEY_REUSED` · `releasesKeyWhenCreationFails` — an underage `POST` leaves no `idempotency_keys` row |

Every exception assertion checks the type **and** `errorCode()`.

## 3 · Data and doubles

**Factory** — `CustomerFixtures` (`src/test/java/dev/nerviz/bankapp/CustomerFixtures.java`,
shape `templates/TestFixtures.java.example`), returns the valid case; each test changes only
the field under test.

| Element | In the test |
|---|---|
| `CustomerFixtures.FIXED_CLOCK` | `Clock.fixed(2026-01-15T10:00:00Z, UTC)` |
| `CustomerFixtures.customer()` | `Customer.register` with name `"Maria Silva"`, security number `"12345678901"`, birth date `1990-05-17`, fixed clock |
| `CustomerFixtures.command()` | `CreateCustomerCommand("Maria Silva", "12345678901", 1990-05-17)` |
| `CustomerFixtures.requestJson()` | the same values as the controller's JSON body |
| `CustomerRepository` | Mockito double in `CreateCustomerUseCaseTest`: `existsBySecurityNumber` → `false` (happy) / `true` (duplicate); `save` returns its argument |
| `CreateCustomerUseCase` | Mockito double (`@MockitoBean`) in `CustomerControllerTest`, throwing the domain exception per case |
| `Clock` in Spring tests | a `@TestConfiguration` `Clock` bean fixed at the same instant, overriding `ClockConfig` — the date cases stay deterministic |
| `Idempotency-Key` | a literal UUID per test, e.g. `0190f3b2-6c1e-7a40-9b8e-2f1d4c5a6b7c` |
| Database | the existing `TestcontainersConfiguration` (`postgres:16-alpine`); Flyway runs `V1`, `V2` as in production; tables truncated between tests |

No double for `Customer`, `SecurityNumber` or `CustomerId`: the real constructors validate.

## 4 · Coverage and gaps

| Invariant from `10-dominio.md` | Test that guarantees it |
|---|---|
| 1 `id` present | `CustomerTest.rejectsMissingId` |
| 2 name not blank | `CustomerTest.rejectsBlankName` |
| 3 name 2–120, trimmed | `CustomerTest.rejectsNameOutsideLength` · `acceptsNameAtBounds` · `registersWithTrimmedNameAndClockInstant` |
| 4 security number present | `SecurityNumberTest.rejectsMissingValue` · `CustomerTest.rejectsMissingSecurityNumber` |
| 5 security number 11 digits | `SecurityNumberTest.rejectsMalformedValue` |
| 6 birth date present | `CustomerTest.rejectsMissingBirthDate` |
| 7 not in the future | `CustomerTest.rejectsFutureBirthDate` |
| 8 age at most 130 | `CustomerTest.rejectsAgeOver130` · `acceptsAge130` |
| 9 strictly over 18 | `CustomerTest.rejectsCustomerNotStrictlyOver18` · `accepts18thBirthdayYesterday` · `appliesFebruary28AnniversaryForLeapDayBirth` |
| 10 `registeredAt` present | `CustomerTest.rejectsMissingRegisteredAtOnRehydrate` |
| 11 security number unique | `CreateCustomerUseCaseTest.rejectsAlreadyRegisteredSecurityNumber` + `CustomerRepositoryJpaAdapterIT.rejectsDuplicateSecurityNumber` |

No open gap.

**Deliberately left uncovered:** `CustomerResponse`'s accessor and `ClockConfig` (a
configuration class, excluded from the gate by `**/*Config.class`). `CustomerMapper` is
covered through `CustomerControllerTest`, not by its own test.

Coverage gate (80% lines, 70% branches) is not wired yet — it comes with ArchUnit through
`test-architect` setup mode, offered at the `/new-feature` pre-flight.

## 5 · Test dependencies

none — JUnit, AssertJ, Mockito and MockMvc come with the `spring-boot-starter-*-test`
starters already in `pom.xml`; Testcontainers (`spring-boot-testcontainers`,
`testcontainers-junit-jupiter`, `testcontainers-postgresql`) is declared. No test waits on an
asynchronous effect, so no Awaitility.

## Impact on approved use cases

none — UC-001 is the first case.
