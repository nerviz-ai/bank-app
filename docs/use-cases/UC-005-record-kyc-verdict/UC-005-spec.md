---
status: approved
---

# UC-005 — Record KYC verdict

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block below holds only the final decisions — one
value per fact, after divergences were resolved — and points to the partial that details
it. The partial is read for detail; where a block here and its partial disagree, **this
file wins** and the losing value is listed in § Resolved divergences.

Designed from backlog row `BL-03`. Adds the project's first Kafka consumer and the only way out
of `KYC_IN_PROGRESS`.

| Partial | Owner | Status |
|---|---|---|
| `00-caso-de-uso.md` | `use-case-design` | ✅ |
| `10-dominio.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ — no endpoint |
| `32-seguranca.md` | `security-architect` | — skipped: no HTTP trigger, `Access` not applicable |
| `28-cliente-http.md` | `http-client-architect` | — not applicable (no port of kind `external HTTP`) |
| `25-mensageria.md` | `messaging-architect` | ✅ — consumer |
| `35-jobs.md` | `jobs-architect` | — not applicable (no Form B, no scheduled trigger, nothing deferred to a job) |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | Kafka message on `bank-app.customer.kyc-verification-completed`, published by the KYC application on a contract this project defines |
| Side effects | 1 — `UPDATE customers.status` of one customer. No publication, no external call |
| Transaction | opens at `RecordKycVerdictUseCase.record`, closes before the offset is acknowledged |
| Transitions | `KYC_IN_PROGRESS` + `APPROVED` → `ACTIVE` · `KYC_IN_PROGRESS` + `REJECT` → `REJECTED_BY_KYC` · same verdict on its final status → no-op · opposite verdict on a final status → unchanged, message to DLQ |
| Unknown customer id | unchanged, message to DLQ |
| Recorded | status only — no decision time, no reason |
| Repetition | duplicates are safe: idempotent by state comparison |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-5.

| Decision | Value |
|---|---|
| Aggregate | `Customer` CHANGE — `Customer recordKycVerdict(KycVerdict)`: new instance from `KYC_IN_PROGRESS`; `this` for the verdict already applied; `ConflictException` `KYC_VERDICT_CONFLICT` for the opposite one; `ValidationException` `KYC_VERDICT_REQUIRED` for `null` |
| New types | `domain/model/KycVerdict` (`enum` `APPROVED`, `REJECT`, `CustomerStatus resultingStatus()`) |
| `CustomerStatus` | values unchanged; Javadoc "no transition is designed here" updated |
| Use case | NEW `application/usecase/customer/RecordKycVerdictUseCase` — `void record(RecordKycVerdictCommand)`, `@Transactional`; `findById` → `orElseThrow(CustomerNotFoundException)` → `recordKycVerdict` → `save` only on a status change |
| Command | NEW `RecordKycVerdictCommand(UUID customerId, KycVerdict verdict)` — rejects `null`s (`CUSTOMER_ID_REQUIRED`, `KYC_VERDICT_REQUIRED`) |
| Output ports | `CustomerRepository` — REUSE, no signature change |
| Exceptions | NEW `domain/exception/CustomerNotFoundException extends NotFoundException`, fixed `CUSTOMER_NOT_FOUND` (promoted: second call site); `GetCustomerUseCase` switches to it. New `errorCode`s: `KYC_VERDICT_CONFLICT` (`ConflictException`), `KYC_VERDICT_REQUIRED`, `KYC_VERDICT_UNKNOWN` (`ValidationException` — the latter raised by the listener's mapping, § 3.5) |
| Events | none emitted |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-3.

| Decision | Value |
|---|---|
| Schema | unchanged — `customers.status` and `customers.version` exist. **No migration** |
| Mapping | `CustomerEntity` gains package-private `changeStatus(CustomerStatus)` |
| Adapter | `CustomerRepositoryJpaAdapter.save` — **load-and-mutate**: `findById` present → `changeStatus` → `saveAndFlush(entity)`; absent → today's insert path unchanged. Closes the defect where `save` of an existing id was an `INSERT` (PK conflict) |
| Translation | NEW: `ObjectOptimisticLockingFailureException` → `ConflictException` `CUSTOMER_CONCURRENT_UPDATE`; existing translations unchanged |
| Dedupe / outbox | none |
| Declared dependencies | none |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.5 Messaging — conditional (executor Block M)

Detail: `25-mensageria.md` § 1-5.

| Decision | Value |
|---|---|
| Topic | `bank-app.customer.kyc-verification-completed`, key `customerId` (publisher's obligation), 3 partitions, replicas `${KAFKA_TOPIC_REPLICAS:1}` — `NewTopic` declared here |
| Payload contract | JSON text `{eventId: UUID, customerId: UUID, verdict: "APPROVED"\|"REJECT"}`; adapter record `KycVerificationCompletedPayload(UUID eventId, UUID customerId, String verdict)` |
| Listener | `infrastructure/messaging/kycverificationcompleted/KycVerificationCompletedListener` — maps `verdict` (`"APPROVED"`/`"REJECT"`; `null` passed through; anything else `ValidationException` `KYC_VERDICT_UNKNOWN`), calls `record`, then `ack.acknowledge()` |
| Idempotency | state comparison — no dedupe table; consumer in this project, **guaranteed** |
| Group / offsets | `bank-app.kyc-verdict`, `auto-offset-reset: earliest`, `enable-auto-commit: false`, `ack-mode: manual`, `StringDeserializer` key and value |
| Retry | transient: 3 retries, 1 s / 2 s / 4 s, then DLQ. `DomainException` not retryable — **except** `ConflictException` `CUSTOMER_CONCURRENT_UPDATE`, retryable. Conversion failures not retryable |
| DLQ | `bank-app.customer.kyc-verification-completed.dlq` (`NewTopic`, 3 partitions); each dead-lettered record: one `WARN` line (`eventId`, `customerId`, `errorCode` or class) + counter `kyc.verdict.dead.lettered` tagged `reason` |
| Wiring | NEW `infrastructure/messaging/listener/KafkaListenerConfig` (one `StringJacksonJsonMessageConverter`, `DefaultErrorHandler` + `DeadLetterPublishingRecoverer` on Boot's `KafkaTemplate`); NEW `KycVerificationCompletedTopicProperties` (`app.kafka.topics.kyc-verification-completed.*`), `KycVerificationCompletedTopicConfig` |
| Test profile | `spring.kafka.listener.auto-startup: false` in `application-test.yml` |
| Personal data | none |
| Declared dependencies | none — `spring-boot-starter-kafka` already declared |

---

## 3.6 Jobs — conditional

none — no Form B outbox, no scheduled trigger, no deferred job.

---

## 4. REST API

none — messaging trigger, no endpoint (`30-rest.md`). `CustomerNotFoundException` reaches
`ApiExceptionHandler` through `UC-003` with the same family and `errorCode`: no handler or
OpenAPI change.

---

## 4.5 Security — conditional

none — skipped by step 3b: no HTTP trigger, `Access` not applicable.

---

## 5. Tests — validates 1-3.5

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `CustomerTest` (CHANGE — transition table), `KycVerdictTest`, `RecordKycVerdictCommandTest`, `RecordKycVerdictUseCaseTest`, `GetCustomerUseCaseTest` (CHANGE), `KycVerificationCompletedListenerTest`, `KafkaListenerConfigTest` |
| Integration | `CustomerRepositoryJpaAdapterIT` (CHANGE — update twice, stale version); NEW `KycVerdictKafkaIT` (real broker + Postgres: approve, reject, duplicate, conflict → DLQ, unknown id → DLQ, malformed → DLQ) |
| Test dependencies | none |

---

## Design patterns

none — every partial recorded `none` (`10-dominio.md`, `20-persistencia.md`, `25-mensageria.md`,
`30-rest.md`).

---

## Impact on approved use cases

| Approved case | Change | Decided in |
|---|---|---|
| `UC-004-initiate-kyc-verification` | `Customer` gains `recordKycVerdict`; `CustomerStatus` Javadoc updated | `10-dominio.md` |
| `UC-004-initiate-kyc-verification` | `application.yml` gains `spring.kafka.consumer.*` / `spring.kafka.listener.*`; `application-test.yml` gains `spring.kafka.listener.auto-startup: false`; the `kafka` compose service it left pending is materialized | `25-mensageria.md` |
| `UC-003-get-customer` | `GetCustomerUseCase` throws `CustomerNotFoundException`; its unit test asserts it; response unchanged | `10-dominio.md`, `40-testes.md` |
| `UC-001-create-customer` | `CustomerRepositoryJpaAdapter.save` branches insert/update (one PK lookup before insert); `CustomerEntity.changeStatus`; lock-failure translation; `CustomerRepositoryJpaAdapterIT` gains update cases | `20-persistencia.md`, `40-testes.md` |

No row adds a precondition: no approved use case becomes unreachable. This case **restores**
end-to-end reachability of `ACTIVE` / `REJECTED_BY_KYC` for customers created since `UC-004`.

---

## Infrastructure

| Service | Image | Decision | Detail | Decided in |
|---|---|---|---|---|
| `postgres` | as declared | `exists` | — | `20-persistencia.md` |
| `kafka` | `apache/kafka:4.1.0` — the tag `KafkaContainerConfiguration` pins | `materialize` | single-node KRaft, EXTERNAL listener `${KAFKA_EXTERNAL_PORT:-29092}`, `app` gets `KAFKA_BOOTSTRAP_SERVERS` (internal listener). Added by `/new-feature-implement` | `25-mensageria.md` |

---

## Out of scope

| Item | Owner | What the project does not guarantee until it ships |
|---|---|---|
| Decision time / rejection reason | not requested | only the status records the verdict |
| Announcing the outcome (event, customer notification) | not requested | nobody outside is told a customer became `ACTIVE` / `REJECTED_BY_KYC` |
| KYC verdict deadline | not requested (as in `UC-004`) | a request never answered leaves the customer `KYC_IN_PROGRESS` |
| Re-drive of dead-lettered verdicts | not requested | a DLQ record is replayed by hand |
| Re-drive of dead-lettered outbox rows | `BL-04` | unchanged from `UC-004` |

**Contract hand-off:** the payload contract and partition key of § 3.5 are this project's to
give the KYC application; until it adopts them, no verdict arrives.

---

## Implementation order (checklist)

- [ ] 1. Domain exceptions — `CustomerNotFoundException`; new `errorCode`s
- [ ] 2. Value objects — `KycVerdict`
- [ ] 3. Aggregate — `Customer.recordKycVerdict`; `CustomerStatus` Javadoc
- [ ] 4. Events — n/a, none
- [ ] 5. Command — `RecordKycVerdictCommand`
- [ ] 6. Use case — `RecordKycVerdictUseCase`; `GetCustomerUseCase` (CHANGE)
- [ ] 7. Output ports — n/a, `CustomerRepository` REUSE
- [ ] 8. Migration — n/a, none
- [ ] 9. Persistence entity — `CustomerEntity.changeStatus`
- [ ] 10. Spring Data interface — n/a, REUSE
- [ ] 11. Repository adapter — `CustomerRepositoryJpaAdapter.save` load-and-mutate, lock translation
- [ ] 12. Persistence properties — n/a, none
- [ ] 13. DTOs + manual mapper — n/a, no endpoint
- [ ] 14. Controller + contract interface + `ApiExceptionHandler` — n/a, unchanged
- [ ] 15. Idempotency interceptor — n/a
- [ ] 16. Test fixtures — `CustomerFixtures` REUSE
- [ ] 17. Unit tests
- [ ] 18. Integration + contract tests
- [ ] 19. `./mvnw verify` — green build, coverage gate

Block M (messaging, after REST): `KafkaListenerConfig`; `KycVerificationCompletedTopicProperties`,
`KycVerificationCompletedTopicConfig`; `KycVerificationCompletedPayload`,
`KycVerificationCompletedListener`; consumer/listener properties in `application.yml` and
`application-test.yml`; `KycVerdictKafkaIT`.

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Unknown customer id / conflicting verdict handling | discarded with warning log + metric (`00-caso-de-uso.md`, `10-dominio.md` § 2) | no retry, to the DLQ with `WARN` log + counter (user's answer in `25-mensageria.md`, aligned with `@.claude/rules/messaging.md` § Retry and DLQ); status unchanged either way | `25-mensageria.md` — owns consumer retry and DLQ |
| Unknown verdict string | malformed, unnamed (`00-caso-de-uso.md`) | `ValidationException` `KYC_VERDICT_UNKNOWN`, raised by the listener mapping | requirement from `25-mensageria.md` → `errorCode` carried in the domain block, owner `10-dominio.md` |
| `CUSTOMER_CONCURRENT_UPDATE` retry | not retryable (`DomainException` family rule, `25-mensageria.md` § 4) | retryable | requirement from `20-persistencia.md` → carried in the messaging block, owner `25-mensageria.md` |
| Listener package | `infrastructure/messaging/kycverdict/` (`00-caso-de-uso.md`) | `infrastructure/messaging/kycverificationcompleted/` — one subpackage per event flow, named for the event | `25-mensageria.md` |
| `save` on an existing id | assumed to update (`10-dominio.md` § 3) | load-and-mutate branch added — today it inserts and conflicts | `20-persistencia.md` |
| Shape of the verdict | "typed field — shape open" (`00-caso-de-uso.md`) | `enum KycVerdict` | `10-dominio.md` |
