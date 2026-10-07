# UC-002 · Domain and application

> Partial of `docs/use-cases/UC-002-prune-expired-idempotency-keys/`. Owner: `domain-modeling`.
> Inherits the canonical names from `00-caso-de-uso.md` — doesn't reinvent them.
> Contains no code. Form references in
> `.claude/skills/domain-modeling/templates/*.java.example`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Aggregate | none — `idempotency_keys` is application infrastructure shared by every `Idempotency-Key` route, not a domain concept |
| Divergences from the mother spec | one — the clock. `00-caso-de-uso.md` left "whose clock" to `persistence-architect`; the port signature below fixes it, because a signature is this partial's. Application `Clock`, the same one that already writes `expires_at` (`IdempotencyRequest.now()` + 24 h in `IdempotencyKeyStore.claim`), so "expired" means one thing. Recorded for consolidation |

Paths relative to `src/main/java/dev/nerviz/bankapp/`. Survey: `IdempotencyKeyPort`
(`application/port/`), `IdempotencyKeyStore` and `IdempotencyKeyJpaRepository`
(`infrastructure/persistence/idempotency/`) exist; `IdempotencyKeyJpaRepository` already
declares `findByExpiresAtBefore(Instant)`, unbounded and loading entities, Javadoc "read by a
future cleanup job (`BL-01`)" — no caller. Mother spec's NEW/CHANGE/REUSE states confirmed.

## 1 · Aggregate and value objects

none — the use case reads and writes no aggregate. The domain package is not touched.

**Value objects:** none. The two fields of the command (§ 3) are positive counts with no
formation rule beyond a lower bound, checked in the command's compact constructor —
`@.claude/rules/value-objects.md` criterion: a bound on a single primitive used in one place
does not earn a type.

**Masking candidates** (`@.claude/rules/logging.md` § Masking candidates): none. The command
carries two counts; the port carries an `Instant` and a count; `idempotency_keys` holds no
personal data (`UC-001` `20-persistencia.md` § 1).

**State reachability:** none — no state field.

## 2 · Invariants

**Exception family: REUSE** — `DomainException`, `ValidationException`,
`BusinessRuleViolationException`, `ConflictException`, `NotFoundException` exist in
`domain/exception/`. No new class.

| # | Invariant | Where it's enforced | Exception | `errorCode` |
|---|---|---|---|---|
| 1 | `batchSize` ≥ 1 | `PruneExpiredIdempotencyKeysCommand` compact constructor | `ValidationException` | `PRUNE_BATCH_SIZE_INVALID` |
| 2 | `maxBatches` ≥ 1 | `PruneExpiredIdempotencyKeysCommand` compact constructor | `ValidationException` | `PRUNE_MAX_BATCHES_INVALID` |
| 3 | only rows with `expires_at` strictly before the cutoff are deleted | the port's contract (§ 3), implemented by the adapter's statement — `20-persistencia.md` | — | — |
| 4 | one call deletes at most `limit` rows | the port's contract, implemented by the adapter's statement | — | — |
| 5 | the cutoff is the application clock's current instant, read **once per run** — every batch of one run uses the same cutoff | `PruneExpiredIdempotencyKeysUseCase.prune` | — | — |

Invariants 1-2 fire only on a misconfigured trigger: the command is built from the job's
properties, never from outside input. `jobs-architect` validates those properties at startup
as well (`35-jobs.md` § 8); the compact constructor is the guarantee that holds without it.

Invariant 5: a cutoff re-read per batch would let a long run chase rows that expire while it
runs. Reading it once bounds the run to what was expired when it started.

Invariants 3-4 raise nothing: a failing statement is an infrastructure failure, propagated as
the adapter's exception — no domain error kind applies (`00-caso-de-uso.md` § Errors).

## 3 · Ports

**Input** — clean architecture: no interface. The concrete use case is the input boundary.

`application/usecase/idempotency/PruneExpiredIdempotencyKeysUseCase.java`

```
class PruneExpiredIdempotencyKeysUseCase
    PruneExpiredIdempotencyKeysUseCase(IdempotencyKeyPort idempotencyKeyPort, Clock clock)
    long prune(PruneExpiredIdempotencyKeysCommand command)
```

`prune`:

1. `cutoff = Instant.now(clock)` — once (invariant 5);
2. `deleted = idempotencyKeyPort.deleteExpired(cutoff, command.batchSize())`, adds it to the
   total;
3. repeats step 2 while the last call returned exactly `batchSize` and fewer than
   `maxBatches` calls were made;
4. returns the total deleted.

**No `@Transactional` on this method.** One transaction per batch (`00-caso-de-uso.md`
§ Transaction): each `deleteExpired` call commits on its own, the same shape the port already
uses for `claim` and `release`. A run-wide transaction would hold the deleted rows' locks for
the whole run and lose every batch on a late failure — the opposite of what the mother spec
fixed. This is the one use case whose transaction boundary is the port call, not the use
case method; `@.claude/rules/architecture-ddd.md` § Application places the transaction at
the application layer, and the port's contract is where that layer states it here.

Constructor injection, `private final` fields. Throws `ValidationException` only through the
command; a database failure propagates unchanged and ends the run — batches already
committed stay committed.

Logging and metrics of the count are the trigger's (`35-jobs.md` § 5); the use case returns
the number and logs nothing of its own beyond `@LogExecution` if the executor applies it.

**Command** — `application/usecase/idempotency/PruneExpiredIdempotencyKeysCommand.java`

```
record PruneExpiredIdempotencyKeysCommand(int batchSize, int maxBatches)
```

Built by the trigger from the job's properties. The values — default batch size and run
ceiling — are not this partial's: `persistence-architect` bounds the batch by what the
statement can hold (`20-persistencia.md`), `jobs-architect` owns the properties
(`35-jobs.md` § 8). `maxBatches` exists so one run cannot run past the next tick when the
backlog is large; the rest waits for the next hour.

**Output** — `application/port/IdempotencyKeyPort.java` · kind `persistence` · **CHANGE**

One method added; `claim`, `complete` and `release` unchanged.

```
interface IdempotencyKeyPort
    IdempotencyClaim claim(IdempotencyRequest request)          // unchanged
    void complete(UUID key, StoredResponse response)             // unchanged
    void release(UUID key)                                       // unchanged

    /** Commits on its own. Called with no transaction active. Deletes at most {@code limit}
     *  rows whose expires_at is strictly before {@code cutoff}; returns how many it deleted.
     *  Safe to run concurrently with itself: two callers never fail each other and never
     *  delete a row with expires_at at or after the cutoff. */
    int deleteExpired(Instant cutoff, int limit)
```

The concurrency sentence is a contract the adapter must meet whatever `jobs-architect`
decides in `35-jobs.md` § 3: with a single-runner lock it holds trivially; without one, the
statement has to tolerate the overlap (`20-persistencia.md` decides how).

Extending this port rather than declaring a second one: it is the one port that owns
`idempotency_keys`, its adapter already holds the table's TTL, and a second port over the same
table would split the table's lifecycle across two owners.

## 4 · Events

none — the mother spec declares no publication, and nothing outside the process needs to learn
that keys were pruned. No event, so `messaging-architect` does not run.

## 5 · Components to create

Refines the `00-caso-de-uso.md` rows marked `Detailed by: domain-modeling`.

| File | Type | State |
|---|---|---|
| `application/usecase/idempotency/PruneExpiredIdempotencyKeysUseCase.java` | class | NEW |
| `application/usecase/idempotency/PruneExpiredIdempotencyKeysCommand.java` | record | NEW |
| `application/port/IdempotencyKeyPort.java` | interface — `deleteExpired` added | CHANGE |
| `domain/exception/ValidationException.java` | two new `errorCode`s, no new class | REUSE |

## Design patterns

none — no force in the spec, no symptom on disk. The batch loop is a bounded `while` in one
method with one exit condition; nothing in the mother spec enumerates variants, strategies or
rules per type, and `IdempotencyKeyPort` has a single implementation.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `IdempotencyKeyPort` gains `int deleteExpired(Instant cutoff, int limit)`; `IdempotencyKeyStore` implements it. `claim`, `complete`, `release` and `UC-001`'s request path unchanged | `UC-001` deferred the prune of `idempotency_keys` to `BL-01`, which this case is |
