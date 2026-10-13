# UC-006 · Tests

> Partial of `docs/use-cases/UC-006-anonymize-customer/`. Owner: `test-architect`.
> Inherits the invariants from `10-dominio.md`, the queries from `20-persistencia.md`, the
> contract cases from `30-rest.md` and the security cases from `32-seguranca.md` — doesn't
> reinvent them.
> Contains no code. Reference shapes in `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`, `@.claude/rules/authorization.md` § Tests.

| Field | Value |
|---|---|
| Partials read | `00`, `10`, `20`, `30`, `32` |
| Engine in integration tests | PostgreSQL 16, ephemeral container — `TestcontainersConfiguration` (REUSE) |
| Invariants from `10-dominio.md` | 8 |
| Invariants with a named test | 8 |

## 1 · Distribution by level

| Behavior | Level | Why here |
|---|---|---|
| `CustomerIdentity` variants reject missing values | Domain unit | value-type rule, no context |
| `Customer.anonymize`: final status → anonymized; already anonymized → same instance; `KYC_IN_PROGRESS` → refused | Domain unit | aggregate transition and its guards |
| `Customer` rejects an anonymized identity with another name; a missing identity | Domain unit | constructor invariants |
| `Customer.recordKycVerdict` keeps the identity (anonymized included) | Domain unit | regression of `UC-005` behaviour over the new field |
| `Customer.toString()` hides name, security number, birth date | Domain unit | masking proven on the value, not only declared |
| Use case: not found; saves only on change; propagates the KYC refusal | Application unit | orchestration with the port doubled |
| Anonymized row round-trip, update path, `NULL`s and `anonymized_at` | Integration | mapping and SQL fail only against the real engine |
| `ck_customers_identity` rejects a half-anonymized row; `V5` keeps existing rows valid | Integration | database constraint, not a domain one |
| HTTP contract of `anonymizeCustomer` and the `UC-003` read changes | Web slice, **real filter chain** | cases in `30-rest.md` § 5 and `32-seguranca.md` § 10 — here the level and the data. Use case doubled, so `Test`, not `IT` |
| Role mapping from the `roles` claim | Adapter unit | `ClaimPathAuthoritiesConverter` is plain code over a `Jwt` |
| End to end: anonymize an `ACTIVE` customer, then `GET` shows it without personal data; a fresh (`KYC_IN_PROGRESS`) customer is refused | Integration (`@SpringBootTest`) | the only place the chain, controller, use case, adapter and migration meet |
| Actuator, docs, CORS, HSTS through the chain | Integration (`@SpringBootTest`) | project-wide setup; a slice does not load actuator nor the real Tomcat |

No behavior at two levels: the controller slice does not re-check the aggregate rules, and the
end-to-end test asserts only the two paths that cross every layer.

## 2 · Cases per test class

| Class | Level | State | Methods |
|---|---|---|---|
| `CustomerIdentityTest` | Domain | NEW | `identifiedRequiresBothValues` (parameterized: security number `null` → `SECURITY_NUMBER_REQUIRED`, birth date `null` → `BIRTH_DATE_REQUIRED`) · `anonymizedRequiresInstant` (`ANONYMIZED_AT_REQUIRED`) · `identifiedToStringMasksBothValues` |
| `CustomerTest` | Domain | CHANGE | `anonymizesFinalStatusCustomer` (parameterized: `ACTIVE`, `REJECTED_BY_KYC` — name `ANONYMIZED`, identity `Anonymized` at the fixed clock's instant, status and id and `registeredAt` unchanged) · `anonymizeReturnsSameInstanceWhenAlreadyAnonymized` (later clock; `anonymizedAt` stays the first) · `refusesAnonymizationWhileKycInProgress` (`BusinessRuleViolationException`, `CUSTOMER_KYC_IN_PROGRESS`) · `rejectsAnonymizedIdentityWithRealName` (`CUSTOMER_ANONYMIZED_NAME_MISMATCH`) · `rejectsMissingIdentity` (`CUSTOMER_IDENTITY_REQUIRED`) · `recordKycVerdictKeepsIdentity` · `toStringMasksName`. Existing methods: switch from `securityNumber()`/`birthDate()` to `identity()` |
| `AnonymizeCustomerCommandTest` | Application | NEW | `rejectsNullId` (`CUSTOMER_ID_REQUIRED`) |
| `AnonymizeCustomerUseCaseTest` | Application | NEW | `anonymizesAndSavesActiveCustomer` (saved argument anonymized) · `doesNotSaveWhenAlreadyAnonymized` (`save` never called) · `throwsNotFoundForUnknownId` (`CUSTOMER_NOT_FOUND`, `save` never called) · `propagatesKycInProgressRefusal` (`CUSTOMER_KYC_IN_PROGRESS`, `save` never called) |
| `CustomerRepositoryJpaAdapterIT` | Integration | CHANGE | `persistsAnonymizationOnExistingRow` (re-read: identity `Anonymized` with the instant, name `ANONYMIZED`; row's `security_number` and `birth_date` `NULL` via `JdbcTemplate`) · `anonymizedSecurityNumberIsFreeForNewCustomer` (a new customer with the old number saves; `existsBySecurityNumber` false right after anonymization) · `statusChangeOnAnonymizedRowKeepsItAnonymized`. Existing methods: switch to `identity()` |
| `AnonymizeCustomersMigrationIT` | Integration | NEW | `existingRowsSatisfyIdentityCheck` (row inserted as before `V5` stays readable) · `rejectsHalfAnonymizedRow` (parameterized via `JdbcTemplate`: `anonymized_at` set with `security_number` kept · `security_number` `NULL` without `anonymized_at` · `anonymized_at` set with another name — each a constraint violation naming `ck_customers_identity`) |
| `CustomerControllerTest` | Web slice | CHANGE | imports `SecurityConfig`, `JwtResourceServerConfig`, the two Problem Details handlers; `@MockitoBean JwtDecoder`. New, one per row of `30-rest.md` § 5 and the `anonymizeCustomer`/`getCustomer`/`createCustomer` rows of `32-seguranca.md` § 10: `anonymizeReturns204` · `anonymizeAlreadyAnonymizedReturns204` · `anonymizeUnknownIdReturns204` · `anonymizeKycInProgressReturns422` · `anonymizeConcurrentWriterReturns409` · `anonymizeMalformedIdReturns400` · `anonymizeWithoutTokenReturns401` (`WWW-Authenticate` present, use case never called) · `anonymizeWithoutRoleReturns403` (token with `ROLE_OPERATOR`) · `getAnonymizedCustomerOmitsPersonalData` (no `securityNumber`, no `birthDate` keys, `name = ANONYMIZED`) · `getWithoutTokenReturns401` · `createWithoutTokenReturns201`. Existing `get*` methods send `jwt()`; existing `create*` methods send none |
| `CustomerDetailsResponseTest` | Adapter unit | CHANGE | `toStringMasksName` (raw `Maria Silva` absent) — besides the existing masking of security number and birth date |
| `ClaimPathAuthoritiesConverterTest` | Adapter unit | NEW | `mapsRolesClaimToRoleAuthorities` (`roles: ["DATA_PROTECTION_OFFICER"]` → `ROLE_DATA_PROTECTION_OFFICER`) · `mapsScopeClaimToScopeAuthorities` · `noRolesClaimGivesNoRoleAuthority` |
| `AnonymizeCustomerIT` | Integration | NEW | `officerAnonymizesActiveCustomerAndReadShowsNoPersonalData` (seed: `CustomerFixtures.withStatus(ACTIVE)` through `CustomerRepository.save`; `DELETE` with a `ROLE_DATA_PROTECTION_OFFICER` `jwt()` → 204; second `DELETE` → 204; `GET` with any `jwt()` → 200 without the two keys) · `freshCustomerIsRefusedWhileKycInProgress` (create via `POST` → `DELETE` → 422 `CUSTOMER_KYC_IN_PROGRESS`) |
| `SecurityChainIT` | Integration | NEW | `healthAndInfoArePublic` · `apiDocsArePublic` · `metricsRequireOperator` (parameterized: no token → 401 · token without `ROLE_OPERATOR` → 403 · with it → 200) · `corsPreflightAllowsListedOriginOnly` (`app.security.cors.allowed-origins` set in the class's properties: listed → allowed headers · other → 403) |
| `GetCustomerIT` | Integration | CHANGE | `get` sends `jwt()`; `create` sends none |
| `ForwardedHeadersIT` | Integration | REUSE | `requestIsSecureAndHstsIsWritten` now proves Spring Security's `HstsHeaderWriter` (single header, same value) — no change expected; it is the HSTS case of `32-seguranca.md` § 10 |
| `KycVerificationRequestedTest`, `CreateCustomerUseCaseTest` | Domain / Application | CHANGE | read values through `identity()`; `KycVerificationRequestedTest` gains `rejectsAnonymizedCustomer` (`KYC_REQUEST_FIELD_REQUIRED`) |

`IT` suffix on every integration class — without it failsafe skips them.

## 3 · Data and doubles

| Element | In the test |
|---|---|
| `CustomerFixtures.customer()` | REUSE — `register(...)` through the real factory, `Identified` |
| `CustomerFixtures.withStatus(status)` | CHANGE — rehydrates with `base.identity()` instead of the two fields. `ACTIVE` and `REJECTED_BY_KYC` are reachable in production (`UC-005`), so seeding them is setup, not a state no path produces |
| `CustomerFixtures.anonymized()` | NEW — `withStatus(ACTIVE).anonymize(FIXED_CLOCK)`: reached through the real transition, never through `rehydrate` |
| `CustomerRepository` | Double in `AnonymizeCustomerUseCaseTest` — `findById` returns the fixture or empty; `save` verified |
| `Clock` | `CustomerFixtures.FIXED_CLOCK` (2026-01-15T10:00:00Z); a second fixed clock one day later for the "first `anonymizedAt` stays" case |
| Tokens | `SecurityMockMvcRequestPostProcessors.jwt()` with `authorities(...)` — `ROLE_DATA_PROTECTION_OFFICER`, `ROLE_OPERATOR`. Never a live provider; `@MockitoBean JwtDecoder` in slices, and in `@SpringBootTest` the `jwt()` post-processor bypasses decoding |
| Database | ephemeral PostgreSQL 16, all migrations `V1`–`V5` in order |

## 4 · Coverage and gaps

| Invariant from `10-dominio.md` | Test that guarantees it |
|---|---|
| 1 · `Identified` has both values | `CustomerIdentityTest.identifiedRequiresBothValues` |
| 2 · `Anonymized` has its instant | `CustomerIdentityTest.anonymizedRequiresInstant` |
| 3 · anonymized is named `ANONYMIZED` | `CustomerTest.rejectsAnonymizedIdentityWithRealName` + `AnonymizeCustomersMigrationIT.rejectsHalfAnonymizedRow` |
| 4 · only a final KYC status | `CustomerTest.refusesAnonymizationWhileKycInProgress` + `AnonymizeCustomerIT.freshCustomerIsRefusedWhileKycInProgress` |
| 5 · twice changes nothing | `CustomerTest.anonymizeReturnsSameInstanceWhenAlreadyAnonymized` + `AnonymizeCustomerUseCaseTest.doesNotSaveWhenAlreadyAnonymized` |
| 6 · the customer exists | `AnonymizeCustomerUseCaseTest.throwsNotFoundForUnknownId` (+ 204 in `CustomerControllerTest.anonymizeUnknownIdReturns204`) |
| 7 · nobody else changed the row | REUSE — `CustomerRepositoryJpaAdapterIT`'s existing `CUSTOMER_CONCURRENT_UPDATE` test; the adapter path is the same `saveAndFlush` |
| 8 · `identity` present | `CustomerTest.rejectsMissingIdentity` |

**Deliberately left uncovered:** `AnonymizeCustomerOpenApiDocs` (annotation, no branch), the
`Roles` constants, and `OpenApiSecurityConfig` beyond what `apiDocsArePublic` loads. No unique
business key is added — `uq_customers_security_number` keeps its existing duplicate test.

No open gap.

## 5 · Test dependencies

| Dependency | Version | Why |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-security-test` (test) | (managed by the Boot parent) | `jwt()` and the MockMvc security integration — Boot 4 test starter for `security` |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server-test` (test) | (managed by the Boot parent) | resource-server test support for the token mechanism — Boot 4 test starter for `oauth2-resource-server` |

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer`, `UC-003-get-customer` | `CustomerControllerTest` runs with the real chain: imports the security config, `get*` send `jwt()`, new 401 and public-201 cases | `32-seguranca.md` — tests run with the real filter chain (`@.claude/rules/authorization.md` § Tests) |
| `UC-003-get-customer` | `GetCustomerIT` sends `jwt()` on the read | the read is authenticated from now on |
| `UC-001`, `UC-004`, `UC-005` | `CustomerFixtures`, `CustomerTest`, `CustomerRepositoryJpaAdapterIT`, `CreateCustomerUseCaseTest`, `KycVerificationRequestedTest` read values through `identity()` | `10-dominio.md` replaced the `securityNumber`/`birthDate` components |
| `UC-003-get-customer` | `CustomerDetailsResponseTest` asserts the name is masked | `30-rest.md` — `name` gains `@MaskSensitiveData` |
