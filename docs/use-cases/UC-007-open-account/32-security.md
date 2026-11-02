# UC-007 · Security

> Partial of `00-use-case.md`, `10-domain.md`, `30-rest.md`. Owner: `security-architect`.
> Norms: `@.claude/rules/authorization.md`. Servlet stack. Wiring package:
> `infrastructure/config/security` (inherited from `UC-006`).

## 1 · Access per endpoint

| Method | Path | `operationId` | Rule | Enforced | Meta-annotation | Outcomes |
|---|---|---|---|---|---|---|
| `POST` | `/api/v1/customers/{customerId}/accounts` | `openAccount` | role `CUSTOMER` or `OPERATOR`; a `CUSTOMER` only for its own customer | request rule in the chain (`hasAnyRole(CUSTOMER, OPERATOR)`); owner in the use case (`Actor.mayActFor`) | none — the expression appears once | anonymous → 401 · authenticated without either role → 403 · `CUSTOMER` for its own / `OPERATOR` for any → 201; `CUSTOMER` for another → 404 |

Public paths of the project after this case — unchanged: API docs (`api-docs-public: true`,
`UC-006`), Actuator `health` and `info`, `POST /api/v1/customers` (`UC-001`, public by the user's
decision). This case adds no `permitAll`.

**Where the actor comes from.** The controller resolves `Actor` from the authenticated `Jwt`
(`templates/CurrentActorResolution.java.example`): authority `ROLE_OPERATOR` →
`Actor.Operator()`; otherwise authority `ROLE_CUSTOMER` and claim `customer_id` (a UUID) →
`Actor.Customer(CustomerId.of(uuid))`. A `CUSTOMER` token without a parseable `customer_id`
claim → 403 (`ACCESS_DENIED`), never an actor with no id. The use case never reads the security
context.

## 2 · Mechanism

Inherited from `UC-006-anonymize-customer` § 2 — OAuth2 resource server, signed JWT
(`JwtResourceServerConfig`, issuer `OAUTH2_ISSUER_URI`, audience `bank-app`). Nothing re-asked.

## 3 · Authorities

| Fact | Value | Source |
|---|---|---|
| Roles in the token | flat `roles` claim → `ROLE_` (REUSE) | `UC-006` § 3 |
| Principal name | `sub` (REUSE) | `UC-006` § 3 |
| Customer identity | claim **`customer_id`**, the `CustomerId` UUID `UC-001` assigns, present in customer tokens; added by the identity provider (a mapper on its client) | user's answer in this run, 2026-10-30. Owner of the IdP configuration: whoever runs the bank's identity provider; locally `BL-05` |
| Role catalog | `Roles.CUSTOMER` — **NEW** constant; `OPERATOR`, `DATA_PROTECTION_OFFICER` REUSE | `CUSTOMER` is new to this case (user's answer) |

## 4 · Project-wide setup

| Component | State |
|---|---|
| `SecurityConfig` filter chain | **CHANGE** — one request rule `POST /api/v1/customers/*/accounts` → `hasAnyRole(CUSTOMER, OPERATOR)`, placed before `anyRequest().authenticated()`; the existing exact matcher `POST CUSTOMERS` `permitAll` does not match the sub-path |
| `JwtResourceServerConfig`, `ClaimPathAuthoritiesConverter` | REUSE |
| `TokenAuthoritiesProperties` | **CHANGE** — adds `customer-id-claim` (default `customer_id`) |
| `Roles` | **CHANGE** — `CUSTOMER` |
| 401 / 403 handlers, `SecurityAuditListener`, `OpenApiSecurityConfig` | REUSE |
| Actor resolution | **NEW** — `infrastructure/rest/CurrentActorResolver.java` (reads `Jwt`, builds `Actor`), used by `AccountController` |

Default access: deny by default (REUSE). CSRF off, stateless, CORS and Actuator exposure as
`UC-006`.

## 5 · Ownership

| Fact | Value |
|---|---|
| Owner | the `Customer` itself — `OpenAccountCommand.customerId`; the new `Account.owner` records it (`10-domain.md` § 1) |
| Actor | `Actor`, in `OpenAccountCommand` (`10-domain.md` § 3), source § 1 above |
| Not the owner | `CustomerNotFoundException` → 404 `CUSTOMER_NOT_FOUND`, same as absent (`10-domain.md` § 2) |
| Override | role `OPERATOR` → `Actor.Operator()`, acts for any customer |

## 6 · Failure responses

| Case | Status | Header | Body |
|---|---|---|---|
| no / invalid / expired token | 401 | `WWW-Authenticate: Bearer` | `ProblemDetail`, `errorCode = AUTHENTICATION_REQUIRED` (REUSE) |
| authenticated, neither `CUSTOMER` nor `OPERATOR` | 403 | — | `ProblemDetail`, `errorCode = ACCESS_DENIED` (REUSE) |
| `CUSTOMER` without a valid `customer_id` claim | 403 | — | same |
| `CUSTOMER` acting for another customer | 404 | — | `CUSTOMER_NOT_FOUND` (`30-rest.md` § 3) |

## 7 · Schema requirements

none — token mechanism; the identity provider holds credentials and the `customer_id` link.

## 8 · Declared dependencies

none — the security and resource-server starters are declared since `UC-006`.

## 9 · Configuration

| Property | Value | Profile |
|---|---|---|
| `app.security.token.customer-id-claim` | `customer_id` | all |

No secret.

## 10 · Contract test cases

| Level | Case | Expected |
|---|---|---|
| HTTP through the real chain | anonymous `POST` | 401 |
| HTTP through the real chain | token with role `DATA_PROTECTION_OFFICER` only | 403 |
| HTTP through the real chain | `CUSTOMER` token, no `customer_id` claim | 403 |
| HTTP through the real chain | `CUSTOMER` token, `customer_id` = path id | 201 |
| HTTP through the real chain | `OPERATOR` token, any id | 201 |
| use case | `Actor.Customer(other)` for a customer that exists | `CustomerNotFoundException` |
| unit | `CurrentActorResolver`: operator, customer with claim, customer without claim | `Actor.Operator`, `Actor.Customer`, denial |

## 11 · Deferred and requirements to other partials

| Item | Norm | Owner |
|---|---|---|
| The production identity provider adds `customer_id` to customer tokens and grants `CUSTOMER` | `@.claude/rules/spec-external-facts.md` — a fact of another system, sourced from the user's answer | outside this project — the IdP's operator; until it does, every customer call answers 403 and only `OPERATOR` can open accounts |
| Local Keycloak (`BL-05`) gains the `customer_id` mapper and a `CUSTOMER` test user | — | `backlog` → append to `BL-05`'s description at consolidation |

## Design patterns

none — one resolver with two branches over a closed pair of roles.

## Impact on approved use cases

none — the chain gains one rule for a new path; no earlier endpoint changes access.

## Implementation order

1. `Roles.CUSTOMER`, `TokenAuthoritiesProperties.customerIdClaim`
2. `SecurityConfig` request rule
3. `CurrentActorResolver`, wired into `AccountController`
4. tests of § 10
