---
status: implemented
---

# UC-006 — Anonymize customer

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block below holds only the final decisions — one
value per fact, after divergences were resolved — and points to the partial that details
it. The partial is read for detail; where a block here and its partial disagree, **this
file wins** and the losing value is listed in § Resolved divergences.

Designed from backlog row `BL-02`. Ends the "life of the record" retention of the customer's
personal data, and brings the project's **first security filter chain**.

| Partial | Owner | Status |
|---|---|---|
| `00-caso-de-uso.md` | `use-case-design` | ✅ |
| `10-dominio.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ |
| `32-seguranca.md` | `security-architect` | ✅ — first secured case, project-wide setup |
| `28-cliente-http.md` | `http-client-architect` | — not applicable (no port of kind `external HTTP`) |
| `29-ia.md` | `ai-architect` | — not applicable (no port of kind `LLM` or `vector store`) |
| `25-mensageria.md` | `messaging-architect` | — not applicable (no event) |
| `35-jobs.md` | `jobs-architect` | — not applicable (no Form B, no scheduled trigger, nothing deferred to a job) |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `22-cache.md` | `cache-architect` | — not applicable (`Freshness`: none) |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | HTTP `DELETE /api/v1/customers/{customerId}`, inbound REST adapter |
| Access | role `DATA_PROTECTION_OFFICER`, JWT from the bank's identity provider |
| Side effects | 1 — `UPDATE` of one `customers` row: `security_number` and `birth_date` erased, `name` = `ANONYMIZED`, `anonymized_at` stamped. Row and `id` kept |
| External calls · publication | none · none |
| Transaction | opens and closes at `AnonymizeCustomerUseCase.anonymize` |
| Repetition | already anonymized → nothing written, first `anonymized_at` kept, same 204 |
| Precondition | only a final KYC status (`ACTIVE`, `REJECTED_BY_KYC`) |
| Concurrency | the row's existing `@Version` |
| Freshness | none — the case only writes |
| Out of scope | copies in `outbox_events` (7 days after publish; dead-lettered rows until `BL-04`), on the KYC topic (7 days) and in the KYC application; old tuples, WAL and backups; who anonymized; re-registration with the same number is allowed |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-3.

| Decision | Value |
|---|---|
| Aggregate | `Customer` — CHANGE: components `(id, name, identity, registeredAt, status)`; `securityNumber`/`birthDate` accessors removed |
| New type | `CustomerIdentity` — sealed interface, records `Identified(SecurityNumber, LocalDate)` and `Anonymized(Instant anonymizedAt)` |
| New behaviour | `Customer.anonymize(Clock)` — already anonymized → `this`; `KYC_IN_PROGRESS` → refused; else placeholder name + `Anonymized`. `ANONYMIZED_NAME = "ANONYMIZED"`, `isAnonymized()` |
| Masking | `Customer.toString()` masks `name`; `Identified.toString()` masks both values |
| Use case | concrete `AnonymizeCustomerUseCase(CustomerRepository, Clock)`, `void anonymize(AnonymizeCustomerCommand)`; saves only when `!next.equals(customer)` |
| Command | `AnonymizeCustomerCommand(UUID customerId)` — `CUSTOMER_ID_REQUIRED` on `null` |
| Output ports | `CustomerRepository` — REUSE, signature unchanged |
| Exceptions | REUSE family. New codes: `CUSTOMER_KYC_IN_PROGRESS` (`BusinessRuleViolationException`), `CUSTOMER_ANONYMIZED_NAME_MISMATCH`, `CUSTOMER_IDENTITY_REQUIRED`, `ANONYMIZED_AT_REQUIRED` (`ValidationException`). REUSE: `CUSTOMER_NOT_FOUND`, `CUSTOMER_CONCURRENT_UPDATE` |
| Events | none |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-4. Migration SQL: § 4 of that partial — the executor
materializes it verbatim.

| Decision | Value |
|---|---|
| Table | `customers` — REUSE, expand only: `security_number`, `birth_date` nullable; `anonymized_at timestamptz NULL` added |
| Constraint | `ck_customers_identity` — identified (both values, no instant) or anonymized (no values, instant, name `ANONYMIZED`); added `NOT VALID`, then validated |
| Uniqueness | `uq_customers_security_number` unchanged — `NULL`s distinct |
| Mapping | `CustomerEntity` gains `anonymizedAt` and `anonymize(String, Instant)`; the two personal-data columns lose `updatable = false`. `CustomerPersistenceMapper` maps the sealed type both ways |
| Adapter | `CustomerRepositoryJpaAdapter.save` update path applies status **and** anonymization |
| Migration | `V5__anonymize_customers.sql` — `<N>` re-checked on disk at implementation |
| Shared tables | none touched |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.4 Cache — conditional

none — `00-caso-de-uso.md`'s `Freshness` row reads `none`: the case only writes.

---

## 3.5 Messaging — conditional

none — `10-dominio.md` § 4 names no event.

---

## 3.6 Jobs — conditional

none — no Form B outbox, no scheduled trigger.

---

## 3.7 AI — conditional

none — `10-dominio.md` declares no port of kind `LLM` or `vector store`.

---

## 4. REST API — implement after 2

Detail: `30-rest.md` § 1-6.

| Decision | Value |
|---|---|
| Endpoint | `DELETE /api/v1/customers/{customerId}`, `operationId` `anonymizeCustomer` → `204`, no body |
| Unknown id | `204` — the controller catches `CustomerNotFoundException` for this operation only; `GET`'s 404 untouched |
| Errors | KYC in progress `422` `CUSTOMER_KYC_IN_PROGRESS` · concurrent writer `409` `CUSTOMER_CONCURRENT_UPDATE` · non-UUID id `400` · unexpected `500` with `traceId` — all mappings REUSE in `ApiExceptionHandler` |
| Idempotency | none — `DELETE` idempotent by state comparison |
| Classes | `CustomerApi` + `CustomerController` CHANGE; `AnonymizeCustomerOpenApiDocs` NEW; `CustomerMapper` CHANGE (`toAnonymizeCommand`, `toDetailsResponse` switches on `identity()`) |
| `UC-003` read | `CustomerDetailsResponse.securityNumber`/`.birthDate` `NOT_REQUIRED`, `@JsonInclude(NON_NULL)` — absent for an anonymized customer; `name` gains `@MaskSensitiveData(maskedType = MaskedType.NAME)` |
| Dependencies | none — springdoc, tracing bridge, validation already in `pom.xml` |

---

## 4.5 Security — implement right after 4

Detail: `32-seguranca.md` § 1-10.

| Decision | Value |
|---|---|
| Mechanism | OAuth2 resource server, signed token (JWT) — NEW, first secured case |
| Authorities | flat `roles` claim → `ROLE_`; `scope` → `SCOPE_`; audience `bank-app`; principal `sub`. `Roles.DATA_PROTECTION_OFFICER`, `Roles.OPERATOR` |
| Access | `DELETE /api/v1/customers/*` → `hasRole(DATA_PROTECTION_OFFICER)` as a **chain request rule** (no method security); `GET /api/v1/customers/{customerId}` → authenticated (**changed** from public); `POST /api/v1/customers` → `permitAll` |
| Public paths | `POST /api/v1/customers` (UC-001 registration, kept public); OpenAPI document and UI; `/actuator/health/**`, `/actuator/info` — reasons in § 1 |
| Actuator | `/actuator/**` else → `OPERATOR`; health details `when-authorized`, role `OPERATOR` |
| Ownership | none |
| Failures | no/invalid token `401` + `WWW-Authenticate: Bearer` · without role `403` — Problem Details, no `errorCode` |
| Setup | `SecurityConfig`, `SecurityAccessProperties`, `JwtResourceServerConfig`, `TokenAuthoritiesProperties`, `ClaimPathAuthoritiesConverter`, Problem Details entry point and denied handler, `Roles`, `SecurityAuditListener`, `OpenApiSecurityConfig` — all NEW in `infrastructure/config/security/`. No `MethodSecurityConfig`, no `SecurityExceptionHandler` |
| HSTS | default headers kept; Spring Security's writer replaces the now-inert `HstsHeaderFilter`, same value |
| CORS | origins from `CORS_ALLOWED_ORIGINS` (empty → no mapping); `GET`, `POST`, `DELETE`; `Authorization`, `Content-Type`, `Idempotency-Key` |
| Configuration | `OAUTH2_ISSUER_URI` (default `http://localhost:8180/realms/bank`), `OAUTH2_AUDIENCE` (`bank-app`), `CORS_ALLOWED_ORIGINS` — in `application.yml` and `.env.example` |
| Dependencies | `spring-boot-starter-security`, `spring-boot-starter-security-oauth2-resource-server` — managed by the Boot parent |

Implemented by the executor's Block S. **Until `BL-05` ships, a local `./mvnw spring-boot:run`
answers 401 on every protected call** — no local identity provider exists.

---

## 5. Tests — validates 1-4.5

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `CustomerIdentityTest` NEW, `CustomerTest` CHANGE, `AnonymizeCustomerCommandTest` NEW, `AnonymizeCustomerUseCaseTest` NEW, `ClaimPathAuthoritiesConverterTest` NEW, `CustomerDetailsResponseTest` CHANGE |
| Slice | `CustomerControllerTest` — real filter chain, `jwt()`; one case per row of `30-rest.md` § 5 and `32-seguranca.md` § 10 |
| Integration | `CustomerRepositoryJpaAdapterIT` CHANGE, `AnonymizeCustomersMigrationIT` NEW, `AnonymizeCustomerIT` NEW, `SecurityChainIT` NEW, `GetCustomerIT` CHANGE; `ForwardedHeadersIT` REUSE as the HSTS proof |
| Fixtures | `CustomerFixtures.withStatus` reads `identity()`; `anonymized()` NEW through the real transition |
| Coverage gate | project gate (ArchUnit and JaCoCo `check` already installed) |
| Test dependencies | `spring-boot-starter-security-test`, `spring-boot-starter-security-oauth2-resource-server-test` — managed by the Boot parent |

---

## Design patterns

none — every partial recorded `none`.

| Force or symptom | Pattern | Classes and interfaces | Decided in |
|---|---|---|---|

---

## Impact on approved use cases

| Approved case | Change | Decided in |
|---|---|---|
| `UC-001-create-customer` | `Customer` replaces `securityNumber`/`birthDate` with `CustomerIdentity identity`; `register` keeps its signature; `rehydrate` takes `identity`; `toString` masks `name` | `10-dominio.md` |
| `UC-001-create-customer` | `customers.security_number`, `birth_date` nullable; `anonymized_at`, `ck_customers_identity`; `CustomerEntity` and `CustomerPersistenceMapper` changes; retention "until anonymized" closes `UC-001`'s deferred row | `20-persistencia.md` |
| `UC-001-create-customer`, `UC-005-record-kyc-verdict` | `CustomerRepositoryJpaAdapter.save` update path applies anonymization besides status | `20-persistencia.md` |
| `UC-001-create-customer` | `POST /api/v1/customers` stays public behind the new chain (`permitAll`, reason recorded); `CreateCustomerOpenApiDocs` gains an empty security requirement | `32-seguranca.md` |
| `UC-003-get-customer` | `GET /api/v1/customers/{customerId}` becomes **authenticated** (was public); `GetCustomerOpenApiDocs` gains a 401 | `32-seguranca.md` |
| `UC-003-get-customer` | `CustomerDetailsResponse`: `securityNumber`/`birthDate` optional and omitted when anonymized; `name` masked in logs; mapper switches on `identity()` — **OpenAPI contract change** | `30-rest.md` |
| `UC-001`, `UC-003` | `CustomerController` gains `AnonymizeCustomerUseCase`; `CustomerControllerTest` and `GetCustomerIT` run with the chain and send `jwt()` on reads | `30-rest.md`, `40-testes.md` |
| `UC-004-initiate-kyc-verification` | `KycVerificationRequested.of(Customer)` reads `identity()`; an `Anonymized` identity is `KYC_REQUEST_FIELD_REQUIRED` | `10-dominio.md` |
| all | `/actuator/metrics` requires `OPERATOR` | `32-seguranca.md` |

No row adds a precondition to an earlier case: every approved use case stays reachable end to end.

---

## Infrastructure

| Service | Image | Decision | Detail | Decided in |
|---|---|---|---|---|
| `postgres` | `postgres:16-alpine` | `exists` | — | `20-persistencia.md` |

The local identity provider is not a row here: it is deferred to `BL-05` (§ Out of scope).

---

## Out of scope

| Item | Owner | Consequence until it ships |
|---|---|---|
| Local Keycloak service in `docker-compose.yml` (realm `bank`, audience `bank-app`, flat `roles` mapper, test users with `DATA_PROTECTION_OFFICER` and `OPERATOR`) | `BL-05` | a local run answers 401 on every protected call; tests are unaffected (`jwt()`, never a live provider) |

---

## Implementation order (checklist)

- [x] 1. Domain exceptions — n/a, family REUSE; new `errorCode`s only (`10-dominio.md` § 2)
- [x] 2. Value objects + constructor validation — `CustomerIdentity` (sealed, two records)
- [x] 3. Aggregate + invariants — `Customer` (`identity`, `anonymize`, `isAnonymized`, `ANONYMIZED_NAME`, masked `toString`); `KycVerificationRequested.of` reads `identity()`
- [x] 4. Events — n/a, none
- [x] 5. Use case command — `AnonymizeCustomerCommand`
- [x] 6. Use case — `AnonymizeCustomerUseCase`
- [x] 7. Output ports — n/a, `CustomerRepository` unchanged
- [x] 8. Migration — materialize `V5__anonymize_customers.sql` from `20-persistencia.md` § 4
- [x] 9. Persistence entity — `CustomerEntity` changes
- [x] 10. Spring Data interface — n/a, unchanged
- [x] 11. Repository adapter — `CustomerPersistenceMapper`, `CustomerRepositoryJpaAdapter.save`
- [x] 12. Persistence properties — n/a, none
- [x] 13. DTOs + manual mapper — `CustomerDetailsResponse`, `CustomerMapper`
- [x] 14. Controller + contract interface + `ApiExceptionHandler` — `CustomerApi`, `CustomerController`, `AnonymizeCustomerOpenApiDocs`, OpenAPI changes on the two existing docs; handler unchanged
- [x] 15. Idempotency — n/a, `DELETE` needs no key
- [x] 16. Test fixtures — `CustomerFixtures`
- [x] 17. Unit tests
- [x] 18. Integration + contract tests
- [x] 19. `./mvnw verify` — green build, coverage gate

Block S (security, `32-seguranca.md` § Implementation order) runs right after step 14.

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Response to an unknown id | not found (`00-caso-de-uso.md` Out, Errors) | `204`, the same as success — the use case still raises `CustomerNotFoundException`, the controller translates it | `30-rest.md` — user's decision, `@.claude/rules/api-rest.md` § Success statuses |
| Access refusal | one situation "not identified, or without the role" (`00-caso-de-uso.md`) | two statuses: `401` and `403` | `32-seguranca.md` |
| Where the anonymization time lives | a field on `Customer` (`00-caso-de-uso.md` Components) | inside `CustomerIdentity.Anonymized` | `10-dominio.md` |
| `domain/model/CustomerIdentity.java` | absent from `00-caso-de-uso.md` Components | NEW | `10-dominio.md` |
| `ApiExceptionHandler` | CHANGE (conditional) (`00-caso-de-uso.md`) | REUSE, no change — every mapping exists | `30-rest.md` |
| Security wiring location | "per `security-architect`" (`00-caso-de-uso.md`) | `infrastructure/config/security/` | `32-seguranca.md` |
