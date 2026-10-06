---
paths:
  - "**/src/**/*.java"
status: active
---

# Naming — packages, classes, methods, tests

Single principle: the name states the **role**, never the design pattern. No `Impl`
suffix. No generic `execute()`.

## Packages

Lowercase, no underscore, logical role as suffix. The concrete map comes from the
active blueprint's `packages.map` (e.g. hexagonal: `domain.model`, `domain.event`,
`application.port.in`, `application.service`, `adapter.in.rest`,
`adapter.out.persistence`). A blueprint may redefine the map; the principle doesn't
change.

## Classes

| Role | Convention | Example |
|---|---|---|
| Entity, aggregate, value object | Noun | `Order`, `Money` |
| REST controller | `<Resource>Controller` | `OrderController` |
| Inbound REST DTO | `<Verb><Resource>Request` | `ConfirmOrderRequest` |
| Outbound REST DTO | `<Resource>Response` | `OrderResponse` |
| Mapper (DTO or JPA entity ↔ domain) | `<Resource>Mapper` | `OrderMapper` |
| JPA entity — name distinct from the aggregate | `<Resource>Entity` | `OrderEntity` |
| Outbound port implementation | `<Resource>RepositoryAdapter` | `OrderRepositoryAdapter` |
| Spring Data interface | `<Resource>JpaRepository` | `OrderJpaRepository` |
| Messaging consumer / producer | `<Event>Listener` / `<Event>Publisher` | `OrderConfirmedListener` |
| Global exception handler | `<Area>ExceptionHandler` | `ApiExceptionHandler` |
| Spring configuration | `<Area>Config` | `SecurityConfig` |

## Architecture vocabulary

The names of the use case, its ports, and their implementations change with the
architecture. They belong to this project's own naming convention, fixed at generation
time from blueprint
`clean-architecture-single-module`, not to the table above. Where the two disagree, the convention below
wins.

Same naming convention as `clean-architecture-multi-module` — splitting into a module or a package doesn't change the class vocabulary:

- **Use case (concrete, no interface):** <Verb><Noun>UseCase — e.g. CreateUserUseCase
- **Command (record, co-located in the SAME package as its UseCase, one per use case):** <Verb><Noun>Command — e.g. CreateUserCommand. The one type in the use-case package that doesn't end in `UseCase`; being a record, the ArchUnit role rules already exempt it by type
- **Grouping (one subpackage per aggregate, always — even with a single aggregate):** `application.usecase.<aggregate>` — e.g. application.usecase.user holds CreateUserUseCase and CreateUserCommand. A use case lives with the aggregate it writes, a query with the one it reads, the outbox relay and prune use cases in `outbox`; one that touches no aggregate, with the one its trigger concerns (a reaction to OrderConfirmed: `order`), else under the noun of its own name (RequestShippingQuote: `shippingquote`). Same lowercase segment as the persistence adapter's subpackage; the test tree mirrors it. A type the use cases of several aggregates share stays at the package root
- **Output port (interface, lives in application, implemented by infra):** <Capability> — e.g. SendNotification, RegisterEvent, DataProvider
- **Port implementation (lives in infrastructure):** <Capability><Technology>Adapter — e.g. SendNotificationEmailAdapter, DataProviderJpaAdapter
- **Controller:** <Noun>Controller — e.g. UserController
- **Persisted entity:** <Noun>Entity — e.g. UserEntity
- **Cross-layer mapper:** <Noun>Mapper — e.g. UserMapper

## Use case identifier

`UC-NNN-<slug-kebab>`, where `NNN` is sequential across the whole project and never
reused — a number that existed in the history belongs to the case that had it, even after
that case's folder is deleted.

The slug names the **trigger's verb and object**: `UC-001-register-customer` for a case
triggered by "register a customer". Never a generic verb — no `process`, `handle`,
`execute`, `manage`.

**The case that creates no use-case class.** A case can be entirely an extension of
already-approved cases: it changes their behavior, adds fields, ports or events, and
introduces no new class of its own. It still gets its own sequential `UC-NNN` — the change
has a spec, a review and a decision date, and those need an identifier. Its slug is the
exception to the paragraph above: **it names the capability being added**, not the trigger,
because the trigger already belongs to the case being changed and two cases cannot carry
the same slug. `initiate-kyc-verification` over a case whose trigger is still "register
customer" is right; `register-customer-v2` and `change-register-customer` are not.

Each altered case records the change in a `CHANGELOG.md` in **its own** folder, one line
per change: date, the `UC-NNN` that made it, and what changed. The approved spec itself is
immutable and is never edited to record it — that is why the log is a separate file.
Without it, immutability protects the text and loses the history: whoever reads
`UC-001-register-customer/` has no way to learn its behavior changed elsewhere.

## Methods

- Use case: a specific verb — `ConfirmOrderUseCase.confirm(...)`.
- Boolean predicate: prefix `is`, `has`, or `can` — `isPaid()`.
- Value object or aggregate with invariants: static factory `of`, `from`, or `create`,
  never a public constructor.

## Restricted identifiers

`record`, `yield`, `var`, `permits`, `sealed` and `when` are never the name of a variable,
parameter, field, method, or type — in `src/main` and in `src/test` alike. They compile, and
they read as the keyword they shadow. A variable holding a `ProducerRecord`,
`ConsumerRecord` or any other "record of something" is named for what it holds —
`sent`, `received`, `pending` — never `record`.

## Tests

Class, method, and test data factory names are the rule of
`@.claude/rules/testing.md` § Names and shape and § Test data. This rule doesn't repeat
them: the convention used to be written in both files and diverged — English with a
`should` prefix here, descriptive Portuguese there.
