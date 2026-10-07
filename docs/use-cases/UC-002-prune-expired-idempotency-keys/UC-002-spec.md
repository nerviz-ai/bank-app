---
status: approved
---

# UC-002 — Prune expired idempotency keys

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block below holds only the final decisions — one
value per fact, after divergences were resolved — and points to the partial that details
it. The partial is read for detail; where a block here and its partial disagree, **this
file wins** and the losing value is listed in § Resolved divergences.

| Partial | Owner | Status |
|---|---|---|
| `00-caso-de-uso.md` | `use-case-design` | ✅ — from backlog row `BL-01` (retired) |
| `10-dominio.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ — every block `none`: scheduled trigger, no endpoint |
| `32-seguranca.md` | `security-architect` | — skipped: no HTTP trigger, `Access` not applicable |
| `28-cliente-http.md` | `http-client-architect` | — not applicable (no port of kind `external HTTP`) |
| `25-mensageria.md` | `messaging-architect` | — not applicable (no event) |
| `35-jobs.md` | `jobs-architect` | ✅ — trigger is a schedule |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | schedule, every 1 hour — no HTTP, no message |
| Input | none — the cutoff is the application clock's current instant |
| Output | none to a caller; deleted count to the log |
| Side effects | 1 — bounded `DELETE` from `idempotency_keys` where `expires_at < cutoff` |
| Transaction | one per batch; committed batches survive a later failure |
| Repetition | rerun deletes nothing more; never an unexpired row |
| Concurrency | more than one replica, every replica runs the pass; the delete skips rows another transaction holds |
| Personal data | none — `idempotency_keys` holds none |
| Events | none |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-5.

| Decision | Value |
|---|---|
| Aggregate / value objects | none — domain package untouched |
| Use case | concrete `application/usecase/idempotency/PruneExpiredIdempotencyKeysUseCase` — `PruneExpiredIdempotencyKeysUseCase(IdempotencyKeyPort, Clock)`, `long prune(PruneExpiredIdempotencyKeysCommand)`. Reads `Instant.now(clock)` once; calls `deleteExpired(cutoff, batchSize)` while the last call returned exactly `batchSize` and fewer than `maxBatches` calls were made; returns the total. **No `@Transactional`** — each port call commits on its own |
| Command | `application/usecase/idempotency/PruneExpiredIdempotencyKeysCommand(int batchSize, int maxBatches)` — compact constructor rejects `< 1` |
| Output port | `application/port/IdempotencyKeyPort` · kind `persistence` · CHANGE — adds `int deleteExpired(Instant cutoff, int limit)`: commits on its own, called with no transaction active, deletes at most `limit` rows with `expires_at` strictly before `cutoff`, safe concurrently with itself. `claim`, `complete`, `release` unchanged |
| Exception family | REUSE — no new class |
| `errorCode`s | `ValidationException`: `PRUNE_BATCH_SIZE_INVALID`, `PRUNE_MAX_BATCHES_INVALID` |
| Clock | application `Clock` bean (`ClockConfig`, REUSE) — the same clock that writes `expires_at` |
| Masking | none — no personal data in any type |
| Events | none |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-6.

| Decision | Value |
|---|---|
| Engine | PostgreSQL 16 (inherited) |
| Table | `idempotency_keys` — no change; `ix_idempotency_keys_expires_at` REUSE |
| Migrations | none |
| Statement | native, in `IdempotencyKeyJpaRepository.deleteExpiredBatch(Instant cutoff, int limit)` → `int`, `@Modifying` + `@Transactional` + `@Query(nativeQuery = true)`: `DELETE FROM idempotency_keys WHERE idempotency_key IN (SELECT idempotency_key FROM idempotency_keys WHERE expires_at < :cutoff LIMIT :limit FOR UPDATE SKIP LOCKED)` — verbatim in `20-persistencia.md` § 3 |
| Coordination strategy | `FOR UPDATE SKIP LOCKED` in the sub-select — no lease column |
| Removed | `IdempotencyKeyJpaRepository.findByExpiresAtBefore(Instant)` — unused, unbounded |
| Adapter | `IdempotencyKeyStore.deleteExpired` — `requireNoTransaction("deleteExpired")`, then `repository.deleteExpiredBatch(cutoff, limit)` |
| Batch size | `app.jobs.prune-idempotency-keys.batch-size: 1000` |
| Retention | 24 h TTL already in `expires_at` — now enforced |
| Declared dependencies | none |
| Pending compose service | none |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.5 Messaging — conditional

none — `10-dominio.md` § 4: no event.

---

## 3.6 Jobs — conditional, implemented by Block J

Detail: `35-jobs.md` § 1-8.

| Decision | Value |
|---|---|
| Technology | `@Scheduled` alone — first job of the project; no ShedLock, no persistent scheduler |
| Project-wide wiring (NEW, once) | `infrastructure/scheduling/SchedulingConfig` (`@EnableScheduling`, `SchedulingConfigurer` setting the `ObservationRegistry`), `infrastructure/scheduling/JobRunRecorder` (timer `jobs.execution{job,outcome}`, gauge `jobs.last_success.seconds{job}`) — shape `templates/ScheduledJob.java.example` |
| Job | `infrastructure/scheduling/idempotency/PruneExpiredIdempotencyKeysJob` — `@ConditionalOnProperty(app.jobs.prune-idempotency-keys.enabled, matchIfMissing = true)`, `@Scheduled(fixedDelayString = "${app.jobs.prune-idempotency-keys.interval:PT1H}", initialDelayString = "${app.jobs.prune-idempotency-keys.initial-delay:PT5M}")`; builds the command from its properties, runs `recorder.run("prune-idempotency-keys", …)`, logs the count at `INFO` (> 0) or `DEBUG` (0) |
| Properties | `infrastructure/scheduling/idempotency/PruneIdempotencyKeysJobProperties` — record, `@ConfigurationProperties("app.jobs.prune-idempotency-keys")`, validated: `batchSize` ≥ 1, `maxBatches` ≥ 1. `interval` and `initial-delay` are **not** bound — placeholders only |
| Replica count | more than one (`00-caso-de-uso.md`, user's requirement) |
| Coordination | none around the trigger — every replica runs; the delete partitions the rows |
| Missed run | skipped — next pass catches up from `expires_at` |
| Alarms | `jobs.execution{outcome=failure}` > 0 for 3 h · `jobs.last_success.seconds` older than 3 h |
| Configuration | `app.jobs.prune-idempotency-keys`: `enabled: true`, `interval: PT1H`, `initial-delay: PT5M`, `batch-size: 1000`, `max-batches: 100`; `spring.task.scheduling.pool.size: 1`, `thread-name-prefix: job-`, `shutdown.await-termination: true`, `await-termination-period: PT30S` |
| Test profile | `src/test/resources/application-test.yml` NEW — `app.jobs.prune-idempotency-keys.enabled: false` |
| Declared dependencies | none |

---

## 4. REST API — implement after 2

none — scheduled trigger, no endpoint (`30-rest.md`).

---

## 4.5 Security — conditional

none — skipped by step 3b: no HTTP trigger, so no filter-chain row applies; `Access` is not
applicable.

---

## 5. Tests — validates 1-3.6

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `PruneExpiredIdempotencyKeysCommandTest`, `PruneExpiredIdempotencyKeysUseCaseTest` (fixed clock `2026-01-15T10:00:00Z`), `PruneExpiredIdempotencyKeysJobTest`, `JobRunRecorderTest`, `PruneIdempotencyKeysJobPropertiesTest` (`ApplicationContextRunner`) |
| Integration | `IdempotencyKeyStoreIT` CHANGE — five `deleteExpired` methods, two of them concurrent with bounded waits |
| Context | `BankAppApplicationTests` CHANGE — `pruneJobIsOffUnderTestProfile` |
| Coverage gate | unchanged — 80 % lines / 70 % branches |
| Test dependencies | none |

---

## Design patterns

none — every partial (`10`, `20`, `30`, `35`) records `none`.

| Force or symptom | Pattern | Classes and interfaces | Decided in |
|---|---|---|---|

---

## Impact on approved use cases

| Approved case | Change | Decided in | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `IdempotencyKeyPort` gains `deleteExpired`; `IdempotencyKeyStore` implements it | `10-dominio.md` | — no precondition added |
| `UC-001-create-customer` | `IdempotencyKeyJpaRepository`: `deleteExpiredBatch` added, `findByExpiresAtBefore` removed; the `idempotency_keys` 24 h retention deferred to `BL-01` is enforced — `UC-001`'s `## Out of scope` row for `BL-01` is closed | `20-persistencia.md` | — no precondition added |
| `UC-001-create-customer` | `IdempotencyKeyStoreIT` gains `deleteExpired` cases; `BankAppApplicationTests` gains the job-off check | `40-testes.md` | — no precondition added |

`UC-001`'s request path is unchanged. Its `CHANGELOG.md`
(`docs/use-cases/UC-001-create-customer/CHANGELOG.md`, NEW) gets one line when this spec is
implemented: `<date> · UC-002 · IdempotencyKeyPort gains deleteExpired; idempotency_keys 24 h retention enforced by an hourly prune (BL-01 closed)`.

---

## Out of scope

| Item | Owner | What the project does not guarantee until it ships |
|---|---|---|
| Deletion / anonymization of a customer | `BL-02` | unchanged from `UC-001` — `security_number` and `birth_date` kept indefinitely |
| Changing the 24 h TTL or the 5-minute lease | `@.claude/rules/api-rest.md` § Idempotency | — |
| Single-run coordination (ShedLock) | a later job that must run once | nothing — this job does not need it |

---

## Implementation order (checklist)

- [ ] 1. Domain exceptions — n/a, REUSE (two new `errorCode`s only)
- [ ] 2. Value objects — n/a, none
- [ ] 3. Aggregate — n/a, none
- [ ] 4. Events — n/a, none
- [ ] 5. Use case command — `PruneExpiredIdempotencyKeysCommand`
- [ ] 6. Use case — `PruneExpiredIdempotencyKeysUseCase` (concrete, no `@Transactional`)
- [ ] 7. Output ports — `IdempotencyKeyPort.deleteExpired`
- [ ] 8. Migration — n/a, none
- [ ] 9. Persistence entity — n/a, `IdempotencyKeyEntity` unchanged
- [ ] 10. Spring Data interface — `deleteExpiredBatch` added, `findByExpiresAtBefore` removed
- [ ] 11. Repository adapter — `IdempotencyKeyStore.deleteExpired`
- [ ] 12. Persistence properties — `app.jobs.prune-idempotency-keys.batch-size`
- [ ] 13. DTOs + manual mapper — n/a, no endpoint
- [ ] 14. Controller + contract interface + `ApiExceptionHandler` — n/a, no endpoint
- [ ] 15. Idempotency interceptor / REST properties — n/a; Block J: `SchedulingConfig`, `JobRunRecorder`, `PruneIdempotencyKeysJobProperties`, `PruneExpiredIdempotencyKeysJob`, job and scheduling properties in `application.yml`, `src/test/resources/application-test.yml`
- [ ] 16. Test fixtures — none new; private row helper in `IdempotencyKeyStoreIT`
- [ ] 17. Unit tests
- [ ] 18. Integration + context tests
- [ ] 19. `./mvnw verify` — green build, coverage gate

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Whose clock defines "now" for the cutoff | "`persistence-architect`'s decision" (`00-caso-de-uso.md`) | application `Clock`, passed as `Instant cutoff` in the port signature — same clock that writes `expires_at` | `10-dominio.md` (port signature owner); `20-persistencia.md` agrees |
| Location of the batch-size value | open in `10-dominio.md` § 3 | `1000`, under `app.jobs.prune-idempotency-keys.batch-size` (property name `35-jobs.md`, value `20-persistencia.md`) | `35-jobs.md` + `20-persistencia.md` |
