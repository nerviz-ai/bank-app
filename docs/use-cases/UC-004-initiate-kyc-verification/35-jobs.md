# UC-004 · Jobs

> Partial of `docs/use-cases/UC-004-initiate-kyc-verification/`. Owner: `jobs-architect`.
> Contains no code. Form references in `.claude/skills/jobs-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/scheduling.md`.

| Field | Value |
|---|---|
| Mother spec | `00-caso-de-uso.md` |
| Why this partial exists | `25-mensageria.md` § 2 chose **Form B** — the project's first outbox: relay schedule and prune job |
| Scheduling package | `infrastructure.scheduling` (blueprint `packages.map`) — subpackage `outbox` |
| Divergences from other partials | none |

Survey: `@Scheduled` in use — `SchedulingConfig` (the one `@EnableScheduling`), `JobRunRecorder`,
`PruneExpiredIdempotencyKeysJob` (`UC-002`). No scheduler or lock library in `pom.xml`. No relay
trigger in `src/main`, no pre-split `OutboxRelay` in the messaging package — nothing to migrate.

No question asked. Every axis inherited or settled: technology — `@Scheduled`, `UC-002`
`35-jobs.md` § 2; replicas — **more than one**, `UC-002` `35-jobs.md` § 3; relay — polling pass,
next pass is the catch-up and the retry; prune — calendar-anchored, idempotent, missed run
skipped; operator control — not requested.

## 1 · Jobs

| Job | Kind | Calls | Trigger | Cadence property | On/off property | Coordination | Missed run | Per pass |
|---|---|---|---|---|---|---|---|---|
| `outbox/OutboxRelayJob` | polling pass | `RelayOutboxEventsUseCase.relayPending(RelayOutboxEventsCommand)` | `fixedDelay` | `app.outbox.poll-interval: PT1S` — read only by the placeholder | `app.outbox.enabled` — default `true`, `false` in the test profile (`@ConditionalOnProperty`) | locked or leased **row claim** — every replica runs the pass; no lock around the trigger (§ 3) | n/a — pending rows wait; the next pass sends them | at most `app.outbox.batch-size` rows claimed; one failing send records the failure on that row and the pass continues with the next |
| `outbox/OutboxPruneJob` | calendar-anchored | `PruneOutboxEventsUseCase.prunePublished(PruneOutboxEventsCommand)` | `cron = "${app.outbox.prune-cron}"`, `zone = "UTC"` | `app.outbox.prune-cron: 0 30 3 * * *` (03:30 UTC, off-peak) | `app.outbox.prune-enabled` — default `true`, `false` in the test profile | overlap-tolerant bounded delete that skips rows a concurrent transaction holds — every replica fires at 03:30 and they split the rows (§ 3) | skipped — the cutoff is `now − prune-after` against persisted `published_at`, so the next run catches up by construction | batches of `app.outbox.prune-batch-size`; loops until a batch deletes fewer than the bound; a failing batch ends the run, earlier batches stay committed |

The relay is always `@Scheduled` with fixed delay (`references/tool-decision-matrix.md` § 3):
never overlaps itself on one instance, and replicas stagger naturally. The trigger builds
`RelayOutboxEventsCommand(batchSize)` from `OutboxProperties`, runs the pass through
`JobRunRecorder`, and records the two delivery metrics from the returned `RelayOutcome` (§ 5).
Shape: `templates/OutboxRelayJob.java.example`.

The prune is cron because it is housekeeping anchored to an off-peak hour, with an explicit zone
(`@.claude/rules/scheduling.md` § Triggers). Builds `PruneOutboxEventsCommand(retention,
batchSize)` from its properties; logs the deleted count — `INFO` when greater than zero, `DEBUG`
when zero. Shape: `templates/OutboxPruneJob.java.example`.

**Application classes this partial names** (clean architecture — concrete use case, command next
to it, no input interface):

| Class | Layer | Note |
|---|---|---|
| `application/usecase/outbox/RelayOutboxEventsCommand` | application | `batchSize ≥ 1` — `RelayOutboxEventsUseCase` itself is `25-mensageria.md`'s |
| `application/usecase/outbox/PruneOutboxEventsUseCase` | application | loops over `OutboxRetentionGateway.deletePublishedBefore(cutoff, limit)`; cutoff from the injected `Clock` |
| `application/usecase/outbox/PruneOutboxEventsCommand` | application | `retention` positive, `batchSize ≥ 1` |
| `application/port/OutboxRetentionGateway` | application, outbound port · kind `persistence` | implemented by `persistence-architect`'s outbox store |

## 2 · Technology

| Decision | Value | Why |
|---|---|---|
| Scheduler | `@Scheduled` — **inherited** | `UC-002-prune-expired-idempotency-keys/35-jobs.md` § 2; one technology per project. The relay is `@Scheduled` by the matrix regardless; the prune needs no catch-up, no retry, no operator UI |
| Coordination tool | none — the claim and the bounded delete partition the work (§ 3) | No lock mechanism exists; both jobs are correct under N replicas without one |
| Inherited? | yes — technology and `JobRunRecorder` / `SchedulingConfig` reused | — |

Rejected, one line each: **ShedLock** — a lock around the relay would idle every replica but one,
and the prune is overlap-tolerant. **Quartz**, **db-scheduler**, **Spring Batch**, **JobRunr** —
no requirement any of them meets that `@Scheduled` does not; a second scheduler is forbidden.

## 3 · Coordination

| Fact | Value | Source |
|---|---|---|
| Replicas in production | more than one — the mechanisms below are correct for any N | inherited, `UC-002` `35-jobs.md` § 3 |

| Job | Mechanism | Bounds |
|---|---|---|
| `OutboxRelayJob` | **Locked or leased claim** — two replicas never claim the same pending row in the same window. Required from persistence (§ 6); lease vs `SKIP LOCKED` is its `Claim strategy`. A row whose lease expires (instance died mid-send) is claimable again → at-least-once, duplicates by design (`25-mensageria.md` § 3) | the lease, if chosen, must outlive one pass: `batch-size` × `delivery.timeout.ms` (30 s, `25-mensageria.md` § 5) |
| `OutboxPruneJob` | **Overlap-tolerant delete** — each batch deletes only `PUBLISHED` rows older than the cutoff that no concurrent transaction holds; held rows are skipped, not waited on | none — no lock |

Per-aggregate ordering: the claim returns rows in occurrence order; with several replicas two
events of **one** customer could be claimed by two instances. This case emits exactly one event
per customer, so no ordering is at stake today. Recorded so a later event of the same aggregate
re-checks it.

## 4 · Execution guarantees

| Guarantee | Value |
|---|---|
| Body idempotent | Relay: re-sending a row is allowed (at-least-once, stable `eventId`). Prune: a deleted row cannot be deleted again |
| Per-pass bound | Relay: `batch-size` claimed rows. Prune: `prune-batch-size` per statement, looping until a short batch |
| One failing item | Relay: the row's attempts and last error are recorded, backoff applies per row (`25-mensageria.md` § 4), the pass continues; at the attempt ceiling the row is dead-lettered. Prune: a failing statement ends the run, the exception escapes through `JobRunRecorder` (`outcome=failure`) |
| Missed run | Relay: pending rows survive downtime and drain on restart. Prune: skipped, cutoff on persisted state |
| Pass longer than the interval | Relay: impossible to overlap on one instance (`fixedDelay`); bounded by `batch-size` × send timeout. Prune: bounded by the loop's short-batch exit |

## 5 · Observability

| Metric | Job | Alarm it exists for |
|---|---|---|
| `jobs.execution` (timer, tags `job=outbox-relay`, `outcome`) | `OutboxRelayJob` | `outcome=failure` rate above zero for 5 min — the pass itself is failing (database, not broker) |
| `jobs.last_success.seconds` (gauge, `job=outbox-relay`) | `OutboxRelayJob` | older than 2 min — the relay stopped; KYC requests are not leaving |
| `outbox.events.dead_lettered` (counter) — name from `25-mensageria.md` § 4 | recorded by `OutboxRelayJob` from `RelayOutcome` | any increment — a customer is stuck in `KYC_IN_PROGRESS` with no request delivered |
| `outbox.pending.age.seconds` (gauge — oldest pending row) — name from `25-mensageria.md` § 4 | registered by `OutboxRelayJob`, read from `RelayOutboxEventsUseCase.oldestPendingAge()` | above 5 min — broker unreachable or relay starved |
| `jobs.execution` (timer, `job=outbox-prune`, `outcome`) | `OutboxPruneJob` | `outcome=failure` on two consecutive nights |
| `jobs.last_success.seconds` (gauge, `job=outbox-prune`) | `OutboxPruneJob` | older than 48 h — outbox (personal data in clear) is growing past its retention |

`jobs.*` come from `JobRunRecorder` (REUSE). Scheduled runs are traced through
`SchedulingConfig`'s observation hook (REUSE).

## 6 · Schema requirements

Read by `persistence-architect` in its first pass. Requirements, never DDL.

| Requirement | Why | Addressee |
|---|---|---|
| Relay claim on `outbox_events` **locked or leased** — two instances never claim the same pending row; a crashed instance's claim becomes claimable again | § 3: more than one replica, no lock around the trigger | `persistence-architect` — its `Claim strategy`; a lease must outlive `batch-size` × 30 s |
| `OutboxRelayGateway.oldestPendingAge()` (or equivalent read) — age of the oldest pending, non-dead-lettered row | § 5's gauge | `persistence-architect` |
| `OutboxRetentionGateway.deletePublishedBefore(cutoff, limit)` — deletes at most `limit` **published** rows with publish time before `cutoff`, own transaction, never a pending or dead-lettered row, **skipping rows a concurrent transaction holds** | § 3: prune on every replica at once | `persistence-architect` — statement and index are its own |
| Retention value for `app.outbox.prune-after` | the prune reads it; the window is persistence's (`20-persistencia.md` § 1) — short, the payload carries a national id in clear (`25-mensageria.md` § 8) | `persistence-architect` |
| No scheduling tool table | § 2 — `@Scheduled` alone | — |

## 7 · Declared dependencies

none — `@Scheduled` and Micrometer already on the classpath (`UC-002`).

## 8 · Configuration

`OutboxProperties` (record, `@ConfigurationProperties("app.outbox")`, validated at startup) binds
`batch-size`, `prune-after`, `prune-batch-size` — **not** `poll-interval`, `prune-cron`,
`enabled`, `prune-enabled`, which are read only by placeholders / `@ConditionalOnProperty`
(`@.claude/rules/scheduling.md` § Triggers). Lives in `infrastructure/scheduling/outbox/`.

| Property | Value | Owner of the value |
|---|---|---|
| `app.outbox.enabled` | `true`; `false` in `src/test/resources/application-test.yml` | this partial |
| `app.outbox.poll-interval` | `PT1S` | this partial |
| `app.outbox.batch-size` | — | `20-persistencia.md` |
| `app.outbox.prune-enabled` | `true`; `false` in the test profile | this partial |
| `app.outbox.prune-cron` | `0 30 3 * * *` (zone `UTC` on the annotation) | this partial |
| `app.outbox.prune-after` | — | `20-persistencia.md` § 1 |
| `app.outbox.prune-batch-size` | — | `20-persistencia.md` |
| `spring.task.scheduling.pool.size` | `2` — was `1` | this partial — three jobs now; a 1 s relay sharing one thread with the hourly and nightly prunes delays them behind it. Two threads keep the relay from starving the prunes |

Full shape in `templates/application-jobs.yml.example`.

## 9 · Deferred

none

## Design patterns

none — the relay and the prune have different skeletons (claim-send-mark vs a delete loop); each
follows its exemplar. `JobRunRecorder` is reused, not a new pattern.

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-002-prune-expired-idempotency-keys` | `spring.task.scheduling.pool.size` raised from `1` to `2` in `application.yml` | `UC-002` `35-jobs.md` § 8: "the next job raises it" — two jobs arrive here |

## Implementation order

1. `RelayOutboxEventsCommand`, `PruneOutboxEventsCommand`, `OutboxRetentionGateway`, `PruneOutboxEventsUseCase`
2. `infrastructure/scheduling/outbox/` — `OutboxProperties`, `OutboxRelayJob`, `OutboxPruneJob` — after `RelayOutboxEventsUseCase` exists
3. Properties in `application.yml`; `enabled: false` / `prune-enabled: false` in `application-test.yml`
4. Tests — `40-testes.md`
