# UC-002 · Prune expired idempotency keys

> Parent spec. Fixes the boundary and the names; **doesn't** detail any layer.
> Per-layer detail lives in this folder's partials, each with its own owner.

| Field | Value |
|---|---|
| Identifier | `UC-002-prune-expired-idempotency-keys` |
| Date | 2026-10-08 |
| Origin | backlog row `BL-01`, deferred by `UC-001-create-customer` `20-persistencia.md` § Deferred |
| Trigger | schedule — every 1 hour. No HTTP, no message. **Technology, cron or fixed-rate form, missed-run and overlap policy belong to `jobs-architect`, in `35-jobs.md`** |
| Access | not applicable — no HTTP trigger; nothing outside the process can start a run |
| Side effects | **1** — `DELETE` from the shared `idempotency_keys` table, rows whose `expires_at` is before the current instant |
| External calls | none — no side effect reaches a system this service does not own |
| Publication | none — no other system needs to learn that keys were pruned; no domain event |
| Transaction | one transaction per batch: each bounded batch commits on its own. A run that fails part-way keeps the batches already committed; the next run continues from what remains. No transaction spans the whole run |
| Repetition | a second run over the same rows deletes nothing more — a deleted row cannot be deleted twice, and a row not yet expired is never touched (business fact). Running the pass twice, or on two replicas at the same instant, never deletes a row that has not expired |
| Concurrency | the service runs on more than one replica, and the pass must be safe there (user's requirement). Whether exactly one replica runs each pass, or every replica runs it and the delete itself tolerates the overlap, belongs to `jobs-architect` (`35-jobs.md` § 3); the bounded delete's statement belongs to `persistence-architect` |
| Personal data | none — `idempotency_keys` holds no personal data (`UC-001` `20-persistencia.md` § 1: `body_hash` is a SHA-256, `response_body` is `{"id": …}` only). Pruning it enforces the 24 h retention that table already declares |
| Active blueprint | `clean-architecture-single-module` |

## Trigger, payload, and response

**In**

none — the schedule carries no payload. The only input is the current instant, taken from a
clock, never from configuration. Whose clock — the application's or the database's — is
`persistence-architect`'s decision, recorded in `20-persistencia.md`; whichever it is, it is
the same clock the store already uses to write `expires_at`, so "expired" means one thing.

**Out**

No caller, no response. The observable results:

| Situation | Result |
|---|---|
| expired rows exist | every row with `expires_at` before the current instant is deleted, in bounded batches, until none remains or the run's own limit is reached |
| no expired row | nothing is deleted; the run ends normally |
| database unavailable or a batch fails | the run ends with a failure; batches already committed stay deleted; the next scheduled run retries what remains |

How many rows were deleted per run is a metric and a log line, not a response — its form is
`jobs-architect`'s (`35-jobs.md` § 5).

## Flow

1. The scheduling trigger fires once per hour.
2. It calls the use case `PruneExpiredIdempotencyKeysUseCase`.
3. The use case asks the outbound port for one bounded batch of expired rows to be deleted,
   and repeats while a batch came back full and the run's own limit allows another.
4. Each batch is its own transaction.
5. The run ends; the count of deleted rows is reported to logs and metrics.

Nothing in this flow names annotations, columns, or framework types — that belongs to
the partials.

## Components

State: **NEW** to be created · **CHANGE** already exists and changes · **REUSE**
already exists and serves.

Paths relative to `src/main/java/dev/nerviz/bankapp/`.

| File | Layer | Role | State | Detailed by |
|---|---|---|---|---|
| `infrastructure/scheduling/idempotency/` — the hourly trigger class | scheduling adapter | fires every hour, calls the use case | NEW | `jobs-architect` |
| `application/usecase/idempotency/PruneExpiredIdempotencyKeysUseCase.java` | application | concrete use case, loops over bounded batches | NEW | `domain-modeling` |
| `application/port/IdempotencyKeyPort.java` | application | shared output port — gains the bounded delete of expired rows | CHANGE | `domain-modeling` |
| `infrastructure/persistence/idempotency/IdempotencyKeyStore.java` | outbound adapter | implements the new port method | CHANGE | `persistence-architect` |
| `infrastructure/persistence/idempotency/IdempotencyKeyJpaRepository.java` | outbound adapter | the bounded delete statement | CHANGE | `persistence-architect` |
| `src/main/resources/db/migration/V2__create_idempotency_keys.sql` | infrastructure | `ix_idempotency_keys_expires_at` already serves `expires_at < now` | REUSE | `persistence-architect` |
| scheduling tables or dependency, if the coordination chosen needs one | infrastructure | coordination across replicas | decided in `35-jobs.md` | `jobs-architect` → `persistence-architect` |
| `src/main/resources/application.yml` | configuration | on/off property, cadence, batch size | CHANGE | `jobs-architect` |

No aggregate, no value object, no domain exception: `idempotency_keys` is application
infrastructure shared by every `Idempotency-Key` route, not a domain concept. The domain
package is not touched.

The use case groups under `idempotency` — it writes no aggregate, so it takes the noun of its
own name (blueprint naming convention), the same segment as the persistence adapter's
subpackage.

Extending `IdempotencyKeyPort` rather than declaring a second port is this spec's starting
point because the port already exists and owns the table; `domain-modeling` may split it,
and says why in `10-dominio.md` if it does.

## Invariants

| Rule | Guaranteed by | Who violates it |
|---|---|---|
| only rows whose `expires_at` is strictly before the current instant are deleted — never a row still inside its 24 h window | the delete's own condition | a delete written without the condition, or with a clock other than the one that wrote `expires_at` |
| one batch deletes at most the configured bound | the delete statement's limit | an unbounded `DELETE` holding locks across the whole table |
| the pass never affects any other table | the port method's scope | none — stated so nobody adds a side effect here |
| two replicas running at once never delete an unexpired row, and never fail each other | coordination in `35-jobs.md` § 3, or a delete that tolerates the overlap | concurrent runs with no coordination and no overlap tolerance |

A row with status `IN_PROGRESS` whose `expires_at` has passed is deleted like any other: its
claim lease (5 minutes) ended long before its 24 h expiry, so it is an abandoned claim, not
work in flight.

## Errors

| Situation | Kind |
|---|---|
| database unavailable, or a batch fails | infrastructure — the run fails and logs it; no domain error kind applies, nothing is reported to a caller |
| another replica is running the pass | not an error — the outcome is `jobs-architect`'s coordination decision |

Situations and kinds, never exception names. The use case raises no domain exception: there
is no input to validate and no business rule to violate.

## Expected tests

| Level | Target | Detailed by |
|---|---|---|
| unit | use case: loops while a batch comes back full, stops on a short batch, stops at the run's limit | `test-architect` |
| integration | `IdempotencyKeyStore` against a real database: deletes only expired rows, respects the batch bound, leaves unexpired rows | `test-architect` |
| integration | two concurrent runs never delete an unexpired row and neither fails | `test-architect` |
| unit or slice | trigger: disabled by its property, calls the use case when enabled | `test-architect` |

## Out of scope for this use case

Written down so nobody merges it back in by accident:

- Changing the 24 h TTL or the `IN_PROGRESS` lease → `@.claude/rules/api-rest.md`
  § Idempotency owns both; this case only enforces the TTL already written in `expires_at`.
- Pruning any other table (a future outbox, a dedupe table) → each table's own prune, decided
  where that table is born.
- Deletion or anonymization of a customer → `BL-02`.
- Any notification, event or external call → none; the request says "no other effect".

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `IdempotencyKeyPort` and `IdempotencyKeyStore` gain a bounded delete of expired rows; the `idempotency_keys` 24 h retention declared in `UC-001` `20-persistencia.md` § 1 is now enforced. `UC-001`'s `## Out of scope` row for `BL-01` is closed by this case | `UC-001` deferred the prune to `BL-01` | — no precondition added; `UC-001`'s behavior for its callers is unchanged |

## Implementation order

Derived from the Components table, in compile order.

- [ ] 1. `application/port/IdempotencyKeyPort.java` — new method
- [ ] 2. `application/usecase/idempotency/PruneExpiredIdempotencyKeysUseCase.java`
- [ ] 3. `infrastructure/persistence/idempotency/` — repository statement and store method
- [ ] 4. scheduling coordination, if `35-jobs.md` needs a table or a dependency
- [ ] 5. `infrastructure/scheduling/idempotency/` — the hourly trigger
- [ ] 6. `application.yml` — the job's properties
- [ ] 7. tests for the levels above
- [ ] 8. `./mvnw clean verify` green
