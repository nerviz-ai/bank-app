# UC-002 · Tests

> Partial of `docs/use-cases/UC-002-prune-expired-idempotency-keys/`. Owner: `test-architect`.
> Inherits invariants from `10-dominio.md`, the statement from `20-persistencia.md`, the job
> from `35-jobs.md`. Contains no code. Shape references in
> `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`, `@.claude/rules/scheduling.md`.

| Field | Value |
|---|---|
| Partials read | `00`, `10`, `20`, `30`, `35` |
| Engine in integration tests | PostgreSQL 16, ephemeral container (`TestcontainersConfiguration`, REUSE) |
| Invariants from `10-dominio.md` | 5 |
| Invariants with a named test | 5 |

Survey: every `@SpringBootTest` already carries `@ActiveProfiles("test")`
(`BankAppApplicationTests`, `ForwardedHeadersIT`, `CreateCustomerIdempotencyIT`) — nothing
to add. `IdempotencyKeyStoreIT` exists (`@DataJpaTest`, `NOT_SUPPORTED`, rows deleted in
`@AfterEach`) — extended, not duplicated. No question asked: no HTTP contract, no external
system, the time and concurrency axes are fixed by the partials.

## 1 · Distribution by level

| Behavior | Level | Why here |
|---|---|---|
| Command rejects `batchSize` / `maxBatches` below 1 (invariants 1-2) | Application unit | Compact-constructor rule; no context |
| Loop stops on a short batch, stops at `maxBatches`, reads the cutoff once (invariants 4-5 as orchestration) | Application unit | Orchestration over a port double and a fixed clock |
| Only rows with `expires_at < cutoff` are deleted; at most `limit` per call (invariants 3-4) | Integration | The condition and the `LIMIT` live in native SQL — only the real engine proves them |
| A row held by another transaction is skipped, not waited on; two concurrent calls neither fail nor delete an unexpired row (`20-persistencia.md` § 1 coordination, `35-jobs.md` § 3) | Integration | `FOR UPDATE SKIP LOCKED` behavior exists only in PostgreSQL |
| `deleteExpired` refuses to run inside a transaction | Integration | Guard on the real adapter, same as `claim`/`release` already tested there |
| Trigger builds the command from its properties and calls the use case through the recorder | Adapter unit | Driving adapter with doubles — never waiting on the scheduler (`@.claude/rules/scheduling.md` § Triggers) |
| Recorder: timer by outcome, last-success gauge, failure rethrown | Adapter unit | `SimpleMeterRegistry`, fixed clock; no context |
| Job properties rejected below 1 at startup | Context unit | `ApplicationContextRunner` — binding and validation only |
| Under the `test` profile no prune trigger bean exists | Context (existing `BankAppApplicationTests`) | `@.claude/rules/scheduling.md` § Triggers: the switch is proven once, in the context that boots every trigger |
| HTTP contract | — | none — `30-rest.md` § 5 is `none` |
| Dependency direction, trigger in `infrastructure.scheduling` | Architecture | `ArchitectureTest`, project-wide, REUSE |

## 2 · Cases per test class

| Class | Level | Methods |
|---|---|---|
| `PruneExpiredIdempotencyKeysCommandTest` (NEW, `application/usecase/idempotency/`) | Application | `acceptsMinimumValues` (1, 1) · `rejectsBatchSizeBelowOne` (parameterized: `0`, `-1`; asserts `ValidationException` + `PRUNE_BATCH_SIZE_INVALID`) · `rejectsMaxBatchesBelowOne` (parameterized: `0`, `-1`; asserts `PRUNE_MAX_BATCHES_INVALID`) |
| `PruneExpiredIdempotencyKeysUseCaseTest` (NEW, `application/usecase/idempotency/`) | Application | `returnsZeroAndCallsOnceWhenNothingExpired` (port returns `0`) · `stopsAfterShortBatch` (port returns `1000, 1000, 3` with `batchSize` 1000 → returns `2003`, 3 calls) · `stopsAtMaxBatchesWhenEveryBatchIsFull` (port always returns `batchSize`, `maxBatches` 4 → 4 calls, returns `4 × batchSize`) · `usesOneCutoffFromTheClockForEveryBatch` (captor: every call receives the fixed clock's instant) · `propagatesPortFailureWithoutFurtherCalls` (second call throws → exception escapes, no third call) |
| `IdempotencyKeyStoreIT` (CHANGE — methods added) | Integration | `deleteExpiredRemovesOnlyRowsBeforeCutoff` (rows at `cutoff − 1 s`, exactly `cutoff`, `cutoff + 1 h` → only the first deleted, returns `1`) · `deleteExpiredDeletesAtMostLimit` (5 expired, `limit` 2 → returns `2`, 3 remain) · `deleteExpiredSkipsRowLockedByAnotherTransaction` (thread A holds `SELECT … FOR UPDATE` on one expired row inside an open transaction; `deleteExpired` from the test thread returns without waiting, the held row remains, the others are gone) · `concurrentDeleteExpiredNeitherFailsNorTouchesUnexpired` (2 threads, released together by a latch, over N expired + M unexpired rows → both return normally, counts sum to N, the M remain) · `deleteExpiredRefusesActiveTransaction` (inside `TransactionOperations` → `IllegalStateException`) |
| `PruneExpiredIdempotencyKeysJobTest` (NEW, `infrastructure/scheduling/idempotency/`) | Adapter unit | `callsUseCaseWithCommandFromProperties` (properties 1000/100 → command `(1000, 100)`) · `recordsRunUnderJobName` (real `JobRunRecorder` over `SimpleMeterRegistry` → timer `jobs.execution{job=prune-idempotency-keys,outcome=success}` count 1) |
| `JobRunRecorderTest` (NEW, `infrastructure/scheduling/`) | Adapter unit | `recordsSuccessAndLastSuccessGauge` · `recordsFailureAndRethrows` (pass throws → timer `outcome=failure`, gauge unchanged from its seed, exception escapes) · `seedsGaugeWithStartTimeBeforeFirstSuccess` |
| `PruneIdempotencyKeysJobPropertiesTest` (NEW, `infrastructure/scheduling/idempotency/`) | Context unit | `bindsConfiguredValues` · `rejectsBatchSizeBelowOne` · `rejectsMaxBatchesBelowOne` — `ApplicationContextRunner`, context fails to start |
| `BankAppApplicationTests` (CHANGE — method added) | Context | `pruneJobIsOffUnderTestProfile` — no `PruneExpiredIdempotencyKeysJob` bean in the context |

Concurrency tests (`skips…`, `concurrent…`) bound every wait with a timeout on the latch and
on `Future.get` — a regression to a blocking `FOR UPDATE` turns into a failed assertion, not
a hung build. No Awaitility: the threads are joined, nothing is polled.

## 3 · Data and doubles

| Element | In the test |
|---|---|
| `Clock` | `Clock.fixed(2026-01-15T10:00:00Z, UTC)` — same instant as `CustomerFixtures` |
| `IdempotencyKeyPort` | Mockito double in `PruneExpiredIdempotencyKeysUseCaseTest`; real `IdempotencyKeyStore` in the IT |
| Rows in the IT | Inserted through `IdempotencyKeyEntity`'s existing constructor `(IdempotencyRequest, Instant expiresAt)` with chosen `expiresAt` — a private helper in `IdempotencyKeyStoreIT`, next to `HASH_A`; no new fixture class (one consumer) |
| Lock holder (IT) | A second thread with its own `TransactionTemplate`, executing a native `SELECT … FOR UPDATE` on one key and waiting on a latch until the delete returns |
| `PruneExpiredIdempotencyKeysUseCase` | Mockito double in the job test |
| `MeterRegistry` | `SimpleMeterRegistry` — real, in-memory |
| Database | Ephemeral PostgreSQL 16; migrations `V1`, `V2` run as in production |

## 4 · Coverage and gaps

| Invariant (`10-dominio.md` § 2) | Test |
|---|---|
| 1 · `batchSize` ≥ 1 | `PruneExpiredIdempotencyKeysCommandTest.rejectsBatchSizeBelowOne` |
| 2 · `maxBatches` ≥ 1 | `PruneExpiredIdempotencyKeysCommandTest.rejectsMaxBatchesBelowOne` |
| 3 · only `expires_at < cutoff` deleted | `IdempotencyKeyStoreIT.deleteExpiredRemovesOnlyRowsBeforeCutoff` |
| 4 · at most `limit` per call | `IdempotencyKeyStoreIT.deleteExpiredDeletesAtMostLimit` |
| 5 · one cutoff per run | `PruneExpiredIdempotencyKeysUseCaseTest.usesOneCutoffFromTheClockForEveryBatch` |

Left uncovered on purpose: the `@Scheduled` cadence itself (`PT1H`, `PT5M`) — proving the
framework fires a `fixedDelay` is testing Spring; the placeholder is read by the annotation
only. `SchedulingConfig` is excluded from coverage by name (`**/*Config.class`).

Coverage gate unchanged: 80 % lines / 70 % branches, wired by setup mode.

## 5 · Test dependencies

none — JUnit, AssertJ, Mockito, Testcontainers PostgreSQL and Micrometer's
`SimpleMeterRegistry` are already on the test classpath.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `IdempotencyKeyStoreIT` gains five `deleteExpired` methods; `BankAppApplicationTests` gains `pruneJobIsOffUnderTestProfile` | the store and the context `UC-001` tested now carry the prune |
