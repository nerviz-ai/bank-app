# UC-006 · Security

> Partial of `docs/use-cases/UC-006-anonymize-customer/`. Owner: `security-architect`.
> Inherits the endpoints from `30-rest.md` and the domain from `10-dominio.md` — doesn't
> redecide them.
> Contains no code. Form references in `.claude/skills/security-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/authorization.md`,
> `@.claude/rules/api-rest.md` § Errors, `@.claude/rules/transport-security.md` § HSTS.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` — `Access`: only callers with the `DATA_PROTECTION_OFFICER` role; JWT issued by the bank's identity provider |
| Why this partial exists | `Access` names a role, and the project has no `SecurityFilterChain` yet — **first secured case**, so this partial also owns the project-wide setup and every endpoint already on disk |
| Stack | servlet (`spring-boot-starter-webmvc` in `pom.xml`, Boot `4.1.1`) |
| Wiring package | `infrastructure.config.security` (blueprint `packages.map`, entry `infrastructure.config`) |
| Divergences from other partials | none. `00-caso-de-uso.md` listed "caller not identified, or without the role" as one situation; here it is two statuses, 401 and 403 (§ 6) |

## 1 · Access per endpoint

| Method | Path | `operationId` | Rule | Enforced in | Meta-annotation | No credential | Without authority | With authority |
|---|---|---|---|---|---|---|---|---|
| `DELETE` | `/api/v1/customers/{customerId}` | `anonymizeCustomer` | role `DATA_PROTECTION_OFFICER` | chain: request rule `DELETE /api/v1/customers/*` → `hasRole(Roles.DATA_PROTECTION_OFFICER)` | — | 401 | 403 | 204 |
| `POST` | `/api/v1/customers` | `createCustomer` (`UC-001`) | public | chain: `permitAll` | — | 201 | — | 201 |
| `GET` | `/api/v1/customers/{customerId}` | `getCustomer` (`UC-003`) | authenticated (**changed**, user's decision) | chain: deny by default (`anyRequest().authenticated()`) | — | 401 | — (no role) | 200 |

The role check is a **request rule in the chain**, not method security: one endpoint, one
role, matched by method and path. No `@PreAuthorize` in the project yet, so no
`MethodSecurityConfig` and no method-security denial to translate — a later case that needs
one adds them. A 403 is not replaced by 404: the resource's existence is not sensitive
(the `DELETE` answers 204 for an unknown id anyway, `30-rest.md`).

Public rows of the project after this case (each with its reason):

| Path | Reason |
|---|---|
| `POST /api/v1/customers` | registration of a new customer — `UC-001`'s decision, kept by the user in this run. Protected only by `Idempotency-Key` and its own validation |
| `/v3/api-docs/**`, `/swagger-ui/**`, `/swagger-ui.html` | OpenAPI document and UI — public by the rule's default; the user kept it |
| `/actuator/health/**`, `/actuator/info` | liveness and readiness probes of the platform; details only for `OPERATOR` |

Request rules, specific to general: docs `permitAll` → health and info `permitAll` →
`/actuator/**` `hasRole(OPERATOR)` → `POST /api/v1/customers` `permitAll` →
`DELETE /api/v1/customers/*` `hasRole(DATA_PROTECTION_OFFICER)` → `anyRequest().authenticated()`.

## 2 · Mechanism

| Decision | Value | Why |
|---|---|---|
| Mechanism | OAuth2 resource server, signed token (JWT) | `references/mechanism-decision-matrix.md` § 2 row 3: the bank's identity provider issues signed tokens (`00-caso-de-uso.md`, `Access`) |
| Inherited? | no — first secured case | no security starter in `pom.xml`, no chain on disk, no earlier `32-seguranca.md` |
| Exemplars | `SecurityConfig.java.example` + `JwtResourceServerConfig.java.example` | — |

Rejected, one line each: **opaque token** — the provider issues JWTs, and introspection adds a
call per request. **Users owned by the application** — the bank's provider owns the accounts.
**API key** — no machine caller in this case.

## 3 · Authorities

| Fact | Value | Source |
|---|---|---|
| Roles in the token | flat top-level `roles` array → prefix `ROLE_` | asked in this run |
| Groups in the token | not used | no `Access` names a group |
| Scopes | `scope` → prefix `SCOPE_` (framework default, kept) | — |
| Audience expected | `bank-app` | asked in this run |
| Principal name | `sub` | decided — the subject is the stable id; no actor travels in this case's command |
| Role catalog | `Roles.DATA_PROTECTION_OFFICER`, `Roles.OPERATOR` — NEW, in `infrastructure.config.security` | first roles of the project |

## 4 · Project-wide setup

| Component | State | Exemplar | Decision |
|---|---|---|---|
| `SecurityConfig` + `SecurityAccessProperties` | NEW | `SecurityConfig.java.example` | stateless; CSRF off (bearer token, no cookie authenticates); **default headers kept — HSTS included**; request rules of § 1; deny by default |
| `JwtResourceServerConfig` + `TokenAuthoritiesProperties` + `ClaimPathAuthoritiesConverter` | NEW | `JwtResourceServerConfig.java.example` | issuer and audience validated by Boot's `issuer-uri` + `audiences`; converter reads `roles` → `ROLE_`, `scope` → `SCOPE_`; no `JwtDecoder` bean of our own |
| `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler` | NEW | `ProblemDetailSecurityHandlers.java.example` | 401 with `WWW-Authenticate: Bearer`, 403, Problem Details with `traceId` |
| `SecurityExceptionHandler` | not needed | `SecurityExceptionHandler.java.example` | no method security → no `AccessDeniedException` reaches MVC; added by the first case with `@PreAuthorize` |
| `MethodSecurityConfig` | not needed | `MethodSecurityAnnotations.java.example` | same reason |
| `Roles` | NEW | `MethodSecurityAnnotations.java.example` (constants only) | `DATA_PROTECTION_OFFICER`, `OPERATOR` |
| `SecurityAuditListener` | NEW | `SecurityAuditListener.java.example` | WARN line + `security.authentication.failures{reason}`, `security.authorization.denied{kind}`; never the token |
| `OpenApiSecurityConfig` | NEW | `OpenApiSecurityConfig.java.example` | bearer scheme, required globally; `CreateCustomerOpenApiDocs` overrides with an empty requirement (public) |
| `HstsHeaderFilter` | REUSE — becomes inert | — | `@ConditionalOnMissingClass(SecurityFilterChain)` drops it when the starter arrives; Spring Security's `HstsHeaderWriter` is the single writer, same value `max-age=31536000 ; includeSubDomains`, secure requests only (`@.claude/rules/transport-security.md` § HSTS). The chain must not call `headers().disable()` nor `httpStrictTransportSecurity().disable()` |
| CORS | NEW | `SecurityConfig.java.example` | origins from `app.security.cors.allowed-origins` (`CORS_ALLOWED_ORIGINS`), empty → no mapping; methods `GET`, `POST`, `DELETE`; headers `Authorization`, `Content-Type`, `Idempotency-Key`; no credentials (bearer, not cookies) |
| Actuator | CHANGE | — | `/actuator/metrics` and health details → `OPERATOR` (`@.claude/rules/authorization.md` § Default access; user confirmed); exposure list unchanged (`health,info,metrics`) |

## 5 · Ownership

none — `Access` is a role, not the owner. No actor travels in `AnonymizeCustomerCommand`.

## 6 · Failure responses

| Situation | Status | Header | Body |
|---|---|---|---|
| no token, malformed, expired, wrong issuer or audience — any protected path | 401 | `WWW-Authenticate: Bearer` | Problem Details, no reason in `detail` |
| token without `ROLE_DATA_PROTECTION_OFFICER` on `anonymizeCustomer` | 403 | — | Problem Details |
| token without `ROLE_OPERATOR` on `/actuator/metrics` or other non-public actuator | 403 | — | Problem Details |

No `errorCode` on 401/403: they are not domain errors. The 401 and 403 rows of `30-rest.md` § 3
are these.

## 7 · Schema requirements

none — the identity provider holds the credentials.

## 8 · Declared dependencies

| Dependency | Version | Why |
|---|---|---|
| `org.springframework.boot:spring-boot-starter-security` | (managed by the Boot parent) | filter chain |
| `org.springframework.boot:spring-boot-starter-security-oauth2-resource-server` | (managed by the Boot parent) | JWT decoding and validation — the Boot 4 name (Initializr id `oauth2-resource-server`) |

The test support (`spring-boot-starter-security-test`, `spring-boot-starter-security-oauth2-resource-server-test`)
is declared by `40-testes.md` § 5.

## 9 · Configuration

| Property | Value | Secret? |
|---|---|---|
| `spring.security.oauth2.resourceserver.jwt.issuer-uri` | `${OAUTH2_ISSUER_URI:http://localhost:8180/realms/bank}` | no — the default points at the local Keycloak deferred in § 11 |
| `spring.security.oauth2.resourceserver.jwt.audiences` | `${OAUTH2_AUDIENCE:bank-app}` | no |
| `app.security.api-docs-public` | `true` | no |
| `app.security.actuator-role` | `OPERATOR` | no |
| `app.security.cors.allowed-origins` | `${CORS_ALLOWED_ORIGINS:}` — comma-separated, empty by default | no |
| `app.security.token.principal-claim` | `sub` | no |
| `app.security.token.roles-claims` | `roles` | no |
| `app.security.token.scopes-claims` | `scope` | no |
| `management.endpoint.health.roles` | `OPERATOR` (`show-details: when-authorized` already set) | no |

One `spring:` root and one `app:` root in `application.yml` — merged, never a second root. Shape
in `templates/application-security.yml.example`. `OAUTH2_ISSUER_URI`, `OAUTH2_AUDIENCE` and
`CORS_ALLOWED_ORIGINS` go into `.env.example` with their defaults. With `issuer-uri`, Boot
resolves the provider's keys lazily: the application starts without a reachable provider, and
the first protected call answers 401 until one exists. The test profile keeps the chain on and
uses the test support's `jwt()` request post-processor — never a live provider.

## 10 · Contract test cases

| Endpoint | Case | Expected |
|---|---|---|
| `anonymizeCustomer` | no token | 401, `WWW-Authenticate` present, use case never called |
| `anonymizeCustomer` | token with `ROLE_OPERATOR` only | 403, use case never called |
| `anonymizeCustomer` | token with `ROLE_DATA_PROTECTION_OFFICER` | 204 |
| `createCustomer` (`UC-001`, impact) | no token | 201 — public proven public |
| `getCustomer` (`UC-003`, impact) | no token · any token | 401 · 200 |
| `/actuator/health` | no token | 200 |
| `/v3/api-docs` | no token | 200 |
| `/actuator/metrics` | no token · token without `ROLE_OPERATOR` · token with it | 401 · 403 · 200 |
| CORS preflight `OPTIONS /api/v1/customers/{id}` | `Origin` listed · `Origin` not listed | allowed headers present · 403 |
| HSTS | secure request (`X-Forwarded-Proto: https` from a trusted proxy) | `Strict-Transport-Security: max-age=31536000 ; includeSubDomains`, exactly one header |
| `ClaimPathAuthoritiesConverter` | token with `roles: ["DATA_PROTECTION_OFFICER"]` | authority `ROLE_DATA_PROTECTION_OFFICER`; no `roles` claim → no role authority |

## 11 · Deferred and requirements to other partials

| Decided | Missing | Norm | Intended owner |
|---|---|---|---|
| the local issuer default is `http://localhost:8180/realms/bank` | a Keycloak service in `docker-compose.yml` with a `bank` realm, audience `bank-app`, a flat `roles` claim mapper, and test users holding `DATA_PROTECTION_OFFICER` and `OPERATOR` — until it exists, a local `./mvnw spring-boot:run` answers 401 on every protected call | `@.claude/rules/authorization.md` § Mechanism (a provider the tests never call, a local one for the developer) | `backlog` — user's decision ("local Keycloak later"); consolidation assigns the `BL-NN` |

Requirements to other partials: none — no actor, no credential store.

## Design patterns

none — one claims source per kind of authority, no growing branch over authentication types.

## Impact on approved use cases

| Case | Endpoint | Before | After | Decision |
|---|---|---|---|---|
| `UC-001-create-customer` | `POST /api/v1/customers` | public (no chain existed) | public — `permitAll` with its reason | kept public — asked in this run |
| `UC-001-create-customer` | `CreateCustomerOpenApiDocs` | no security requirement | empty `@SecurityRequirements` — overrides the global bearer requirement | the document must say the operation is public |
| `UC-003-get-customer` | `GET /api/v1/customers/{customerId}` | public — accepted risk: anyone with an id read personal data | **authenticated** (any valid token, no role) | protected from now on — asked in this run. `UC-003`'s accepted risk narrows to any authenticated caller of the bank's provider |
| `UC-003-get-customer` | `GetCustomerOpenApiDocs` | no 401 | gains a 401 `@ApiResponse` | the operation can now answer it |
| `UC-001`, `UC-003` | `CustomerControllerTest` | ran with no filter chain | imports `SecurityConfig` and `JwtResourceServerConfig`; `getCustomer` cases send a `jwt()`; `createCustomer` cases send none; new cases of § 10 | listed for `test-architect` |
| all | Actuator `/actuator/metrics` | public (no chain existed) | `OPERATOR` only | `@.claude/rules/authorization.md` § Default access, user confirmed |

## Implementation order

1. The dependencies of § 8
2. `Roles`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`
3. `JwtResourceServerConfig` (+ properties, converter), then `SecurityConfig` (+ properties), `SecurityAuditListener`, `OpenApiSecurityConfig`
4. OpenAPI changes on `CreateCustomerOpenApiDocs` and `GetCustomerOpenApiDocs` — after the REST block
5. Properties in `application.yml`, variables in `.env.example`
6. Tests — `40-testes.md`
