# UC-002 · Jobs

> Partial of `docs/use-cases/UC-002-prune-expired-idempotency-keys/`. Owner: `jobs-architect`.
> Contains no code. Form references in `.claude/skills/jobs-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/scheduling.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Why this partial exists | `00-caso-de-uso.md` § Trigger is a schedule — every 1 hour |
| Scheduling package | `infrastructure.scheduling` (blueprint `packages.map`) — subpackage `idempotency`, as `00-caso-de-uso.md` § Components named it |
| Divergences from other partials | none |

Survey: `pom.xml` (Boot parent 4.1.1) carries no scheduler and no lock library; `src/main`
has no `@Scheduled`, no `@EnableScheduling`; no earlier `35-jobs.md`; `UC-001`
`20-persistencia.md` records no `Claim strategy`. **First job of the project** — technology
and wiring are NEW here and reused by every later job. No Form B outbox in the project, so no
relay and no outbox prune.

No question asked. Each interview axis was settled by the specs: replicas — more than one
(`00-caso-de-uso.md` § Concurrency, the user's requirement); kind — polling pass (hourly, not
calendar-anchored); missed run — the next pass is the catch-up; volume — bounded batches, a
rerun from zero costs nothing; retry — the next pass; operator control — not requested.

## 1 · Jobs

| Job | Kind | Calls | Trigger | Cadence property | On/off property | Coordination | Missed run | Per pass |
|---|---|---|---|---|---|---|---|---|
| `PruneExpiredIdempotencyKeysJob` | polling pass | `PruneExpiredIdempotencyKeysUseCase.prune(PruneExpiredIdempotencyKeysCommand)` | `fixedDelay`, with `initialDelay` | `app.jobs.prune-idempotency-keys.interval: PT1H`, `app.jobs.prune-idempotency-keys.initial-delay: PT5M` — read only by the `@Scheduled` placeholders | `app.jobs.prune-idempotency-keys.enabled` — default `true`, `false` in the test profile | every instance runs the pass; the delete tolerates the overlap (§ 3) | skipped — the next pass deletes every row expired by then | batches of `batch-size` rows, up to `max-batches` batches; a failing batch ends the pass, earlier batches stay committed |

`fixedDelay`, not `fixedRate` and not `cron`: the work is not calendar-anchored ("every 1
hour", not "at minute 0"), and fixed delay never overlaps itself on one instance
(`@.claude/rules/scheduling.md` § Triggers). Replicas started at different moments stagger
their passes naturally; a cron would fire every replica at the same instant against the same
rows. No `zone` — no cron.

`initialDelay` PT5M: without it every replica prunes at boot, so a rolling deploy runs the
pass once per pod within seconds. Harmless (§ 3), but pointless; five minutes keeps it out of
startup.

The trigger builds `PruneExpiredIdempotencyKeysCommand(batchSize, maxBatches)` from its
properties record (§ 8), calls the use case through `JobRunRecorder`, and logs the returned
count — `INFO` when greater than zero, `DEBUG` when zero (`@.claude/rules/scheduling.md`
§ Observability). No other dependency on business code. Shape:
`templates/ScheduledJob.java.example`.

## 2 · Technology

| Decision | Value | Why |
|---|---|---|
| Scheduler | `@Scheduled` (Spring Framework) | One polling pass; no runtime-created schedule, no catch-up need, no per-execution retry, no operator UI. `references/tool-decision-matrix.md` § 2 row "many instances, work partitioned by row claim": `@Scheduled` ✅, every other tool overkill or redundant, and it carries zero infrastructure |
| Coordination tool | none — the delete partitions the work (§ 3) | No lock mechanism exists in the project; `references/tool-decision-matrix.md` § 3 locks an idempotent prune only "when a lock mechanism already exists". The user asked for "safe on more than one replica", not "runs once" |
| Inherited? | no — first job of this project | no scheduler in `pom.xml`, no earlier `35-jobs.md` |

Rejected, one line each: **ShedLock** — one table and two dependencies to stop an idempotent
hourly delete from running on a second replica, when the delete itself can skip what another
replica holds; adopt it when a job that must run once arrives. **Quartz** — eleven tables for
one static job. **db-scheduler** — a persistent scheduler for a pass with no catch-up need.
**Spring Batch** — one bounded loop, nothing to restart from. **JobRunr** — no operator-UI
requirement; LGPL not asked. **Kubernetes `CronJob`** — no deployment platform recorded, and
the job would need a CLI entry the use case does not have.

## 3 · Coordination

| Fact | Value | Source |
|---|---|---|
| Replicas in production | more than one — exact count not needed: the mechanism below is correct for any N | `00-caso-de-uso.md` § Concurrency (user's requirement); no earlier `35-jobs.md` or `Claim strategy` |

| Job | Mechanism | Bounds |
|---|---|---|
| `PruneExpiredIdempotencyKeysJob` | **Overlap-tolerant delete**, no lock around the trigger. Every replica runs the pass; each batch deletes only rows no concurrent transaction holds, so two replicas at the same instant split the rows instead of waiting on or failing each other. Required from persistence in § 6 | none — no lock to bound |

Why this meets `10-dominio.md` § 3's port contract: two callers never fail each other (no row
lock wait, no deadlock — a held row is skipped, not waited on) and never delete a row with
`expires_at` at or after the cutoff (the condition is in every statement). A row one replica
skips is either deleted by the replica holding it, or by the next pass.

Cost of N replicas: N short passes per hour, each finding little after the first. Accepted.

## 4 · Execution guarantees

| Guarantee | Value |
|---|---|
| Body idempotent | Yes — a deleted row cannot be deleted again; a rerun deletes only what expired since |
| Per-pass bound | `batch-size` rows per statement (value: `20-persistencia.md`), at most `max-batches` statements per pass. The use case stops early when a statement deletes fewer than `batch-size` |
| One failing item | No per-item processing — the unit is a batch. A failing statement ends the pass; its exception escapes to the scheduler's error handler through `JobRunRecorder` (recorded as `outcome=failure`); committed batches stay deleted; the next pass retries |
| Missed run | Skipped. The window is `expires_at`, persisted state — never "now minus the interval" — so the next pass catches up by construction |
| Pass longer than the interval | Impossible to overlap on one instance (`fixedDelay`); bounded by `max-batches` × statement time — at the § 8 values, far under one hour |

## 5 · Observability

| Metric | Job | Alarm it exists for |
|---|---|---|
| `jobs.execution` (timer, tags `job=prune-idempotency-keys`, `outcome`) | `PruneExpiredIdempotencyKeysJob` | `outcome=failure` count above zero for 3 consecutive hours |
| `jobs.last_success.seconds` (gauge, tag `job=prune-idempotency-keys`) | `PruneExpiredIdempotencyKeysJob` | older than 3 h — three missed or failed passes; the table is growing unpruned |

The deleted count is in the `INFO` log line, not a metric: nothing alarms on it, and a
counter per pass adds a series nobody reads (`@.claude/rules/observability.md` § Metrics).
Scheduled runs are observed through `SchedulingConfig`'s registrar hook, so each pass carries a
trace.

The timer and the gauge come from `JobRunRecorder`, NEW in this case and shared by every later
job (`templates/ScheduledJob.java.example`).

## 6 · Schema requirements

Read by `persistence-architect` in its first pass. Requirements, never DDL.

| Requirement | Why | Addressee |
|---|---|---|
| `IdempotencyKeyPort.deleteExpired(cutoff, limit)` — bounded delete of `idempotency_keys` rows with `expires_at` strictly before `cutoff`, at most `limit` rows per call, own transaction, **skipping rows a concurrent transaction holds** instead of waiting on them | § 3: more than one replica runs the pass with no lock; the statement is the coordination | `persistence-architect` — the statement, the batch bound's value, and whether `ix_idempotency_keys_expires_at` serves it are its own |
| No scheduling tool table | § 2 — `@Scheduled` alone | — |

## 7 · Declared dependencies

none — `@Scheduled` is in `spring-context`, already on the classpath through
`spring-boot-starter`. Micrometer (`MeterRegistry`, `ObservationRegistry`) is already present
through the tracing bridge `UC-001` declared.

## 8 · Configuration

Properties bound in `PruneIdempotencyKeysJobProperties` (record, `@ConfigurationProperties`
`app.jobs.prune-idempotency-keys`, validated at startup: both counts ≥ 1) — **except**
`interval` and `initial-delay`, read only by the `@Scheduled` placeholders and never also bound
(`@.claude/rules/scheduling.md` § Triggers).

| Property | Value | Owner of the value |
|---|---|---|
| `app.jobs.prune-idempotency-keys.enabled` | `true`; `false` in `src/test/resources/application-test.yml` | this partial |
| `app.jobs.prune-idempotency-keys.interval` | `PT1H` | this partial — `00-caso-de-uso.md` § Trigger |
| `app.jobs.prune-idempotency-keys.initial-delay` | `PT5M` | this partial |
| `app.jobs.prune-idempotency-keys.batch-size` | — | `20-persistencia.md` — what one statement can hold |
| `app.jobs.prune-idempotency-keys.max-batches` | `100` | this partial — one pass stays well inside the hour; a backlog larger than `batch-size × 100` drains over successive passes |
| `spring.task.scheduling.pool.size` | `1` — written explicitly | this partial — one job in the project; the next job raises it (`@.claude/rules/scheduling.md` § Triggers: pool size is a decision) |
| `spring.task.scheduling.thread-name-prefix` | `job-` | this partial |
| `spring.task.scheduling.shutdown.await-termination` / `await-termination-period` | `true` / `PT30S` | this partial — a pass in flight at shutdown finishes its batch |

`src/test/resources/application-test.yml` does not exist yet (the `test` profile is active in
`BankAppApplicationTests`, `ForwardedHeadersIT`, `CreateCustomerIdempotencyIT`): NEW, holding
the one `enabled: false` line.

Full shape in `templates/application-jobs.yml.example`.

## 9 · Deferred

none

## Design patterns

none — one job, one trigger calling one inbound port; no shared claim-process-mark skeleton
across jobs, no symptom on disk (no job exists yet). `JobRunRecorder` is the exemplar's shared
helper, not a catalog pattern.

## Impact on approved use cases

none — `UC-001` has no job; the port change is `10-dominio.md`'s row.

## Implementation order

1. `SchedulingConfig` (`@EnableScheduling` + observation registry) and `JobRunRecorder` in
   `infrastructure/scheduling/` — once per project
2. `PruneIdempotencyKeysJobProperties` and `PruneExpiredIdempotencyKeysJob` in
   `infrastructure/scheduling/idempotency/` — after `PruneExpiredIdempotencyKeysUseCase` exists
3. Properties in `application.yml`; `src/test/resources/application-test.yml` with
   `enabled: false`
4. Tests — `40-testes.md`
