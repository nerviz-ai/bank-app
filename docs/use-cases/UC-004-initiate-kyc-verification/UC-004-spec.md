---
status: implemented
---

# UC-004 — Initiate KYC verification

Consolidated spec. Recipient: `java-spring-boot-developer`.

**Consolidated by reference.** Each block below holds only the final decisions — one
value per fact, after divergences were resolved — and points to the partial that details
it. The partial is read for detail; where a block here and its partial disagree, **this
file wins** and the losing value is listed in § Resolved divergences.

**No new use-case class.** This case extends `UC-001-create-customer` (customer born
`KYC_IN_PROGRESS`, KYC request recorded in a transactional outbox) and `UC-003-get-customer`
(read exposes `status`). It adds the project's first Kafka producer, outbox, relay and prune.

| Partial | Owner | Status |
|---|---|---|
| `00-caso-de-uso.md` | `use-case-design` | ✅ |
| `10-dominio.md` | `domain-modeling` | ✅ |
| `30-rest.md` | `rest-api-architect` | ✅ |
| `32-seguranca.md` | `security-architect` | — skipped: `Access` public (unchanged), project has no filter chain |
| `28-cliente-http.md` | `http-client-architect` | — not applicable (no port of kind `external HTTP`) |
| `25-mensageria.md` | `messaging-architect` | ✅ |
| `35-jobs.md` | `jobs-architect` | ✅ |
| `20-persistencia.md` | `persistence-architect` | ✅ |
| `40-testes.md` | `test-architect` | ✅ |

Paths relative to `src/main/java/dev/nerviz/bankapp/` unless they start with `src/`.

---

## 1. Use case

Detail: `00-caso-de-uso.md`.

| Decision | Value |
|---|---|
| Trigger | unchanged — `POST /api/v1/customers` (`UC-001`), public |
| Side effects | 1 — `INSERT customers` (with `status`) + outbox append, same transaction (Form B, boundary fact) |
| Transaction | opens at `CreateCustomerUseCase.create`, closes before the response |
| Repetition | unchanged; a replay or a rejected creation never records a KYC request |
| Statuses | `KYC_IN_PROGRESS` (every new customer), `ACTIVE` (pre-existing rows; later `BL-03`), `REJECTED_BY_KYC` (`BL-03`) |
| Visibility | `status` in the read (`UC-003`); **not** in the creation response |

---

## 2. Domain model — implement first

Detail: `10-dominio.md` § 1-5.

| Decision | Value |
|---|---|
| Aggregate | `Customer` gains `CustomerStatus status` (last component); `register` unchanged signature, sets `KYC_IN_PROGRESS`; `rehydrate(…, CustomerStatus)` |
| New types | `domain/model/CustomerStatus` (`enum`); `domain/event/KycVerificationRequested(CustomerId customerId, String name, SecurityNumber securityNumber, LocalDate birthDate, Instant occurredAt)` with `of(Customer)` (`occurredAt = registeredAt`), masked `toString()` |
| Output port | NEW `application/port/RequestKycVerification` · kind `messaging` — `void request(KycVerificationRequested)`; records inside the caller's transaction |
| Use case | `CreateCustomerUseCase` CHANGE — constructor gains the port; after `save`, `request(KycVerificationRequested.of(saved))` |
| Exceptions | REUSE family; new `errorCode`s `CUSTOMER_STATUS_REQUIRED`, `KYC_REQUEST_FIELD_REQUIRED` (`ValidationException`) |
| Transitions | none designed — `ACTIVE` / `REJECTED_BY_KYC` transitions belong to `BL-03` |

---

## 3. Persistence — implement after 2

Detail: `20-persistencia.md` § 1-5. Migration SQL: § 4 of that partial — the executor
materializes it verbatim.

| Decision | Value |
|---|---|
| `customers` | `V3__add_customers_status.sql` — `status varchar(20) NOT NULL`, backfill `ACTIVE`, default dropped, `CHECK` on the three values |
| Mapping | `CustomerEntity.status` `@Enumerated(STRING)`; `CustomerPersistenceMapper` maps both ways |
| `outbox_events` | NEW shared table, `V4__create_outbox_events.sql` — exemplar columns + `next_attempt_at`, `claimed_until`; partial indexes pending / published |
| Claim strategy | **lease** — `UPDATE … WHERE event_id IN (SELECT … FOR UPDATE SKIP LOCKED) RETURNING`, lease `PT8M` |
| Pacing | batch `10`, max attempts `10`, backoff `base × 2^(attempts−1)` with base `PT1S`, cap `PT5M`; then dead-lettered |
| Retention | published rows `P7D` (user's answer); pending / dead-lettered kept; prune batch `500` |
| Classes | `infrastructure/persistence/outbox/` — `OutboxEventEntity`, `OutboxEventJpaRepository`, `OutboxEventStore` (implements `OutboxRelayGateway` and `OutboxRetentionGateway`) |
| Personal data at rest | `outbox_events.payload` — name, security number, birth date in clear, 7 days after publish |
| Declared dependencies | none |

---

## 3.3 Outbound HTTP — conditional

none — `10-dominio.md` declares no port of kind `external HTTP`.

---

## 3.5 Messaging — conditional (executor Block M)

Detail: `25-mensageria.md` § 1-9.

| Decision | Value |
|---|---|
| Topic | `bank-app.customer.kyc-verification-requested`, key `customerId`, 3 partitions, replicas `${KAFKA_TOPIC_REPLICAS:1}`, `retention.ms` from `app.kafka.topics.kyc-verification-requested.retention` (`P7D`) |
| Form | **B** — `RequestKycVerificationOutboxAdapter` → `OutboxAppender` (JSON text at append, `eventId` UUID v7) |
| Payload | `KycVerificationRequestedPayload(eventId, customerId, name, securityNumber, birthDate, occurredAt)`, masked `toString()` |
| Relay | `application/usecase/outbox/RelayOutboxEventsUseCase` (+ `oldestPendingAge()`), ports `OutboxRelayGateway`, `OutboxEventSender`, carriers `OutboxEventRecord`, `RelayOutcome`; `infrastructure/messaging/outbox/` — `OutboxAppender`, `KafkaOutboxEventSender`, `TopicResolver`, `KafkaOutboxConfig` |
| Producer config | `acks=all`, `enable.idempotence=true`, `StringSerializer` both, `delivery.timeout.ms=30000`, **`max.block.ms=10000`**, bootstrap `${KAFKA_BOOTSTRAP_SERVERS:localhost:${KAFKA_EXTERNAL_PORT:29092}}` |
| Consumer | none in this project |
| Dedupe owner | KYC application — **unknown** |
| Personal data in transit | `securityNumber`, `birthDate`, `name` — **full value** to the KYC application (it verifies them) |
| Declared dependencies | `org.springframework.boot:spring-boot-starter-kafka` (managed) |

---

## 3.6 Jobs — conditional (executor Block J)

Detail: `35-jobs.md` § 1-9.

| Decision | Value |
|---|---|
| Technology | `@Scheduled` — inherited (`UC-002`) |
| Replicas | more than one — inherited (`UC-002` § 3) |
| `OutboxRelayJob` | `fixedDelay` `app.outbox.poll-interval` `PT1S`, gate `app.outbox.enabled`; calls `RelayOutboxEventsUseCase.relayPending(RelayOutboxEventsCommand)`; records `outbox.events.dead_lettered`, `outbox.pending.age.seconds` |
| `OutboxPruneJob` | cron `app.outbox.prune-cron` `0 30 3 * * *`, zone `UTC`, gate `app.outbox.prune-enabled`; calls `PruneOutboxEventsUseCase.prunePublished(PruneOutboxEventsCommand)` over `OutboxRetentionGateway` |
| Properties | `infrastructure/scheduling/outbox/OutboxProperties` — `batch-size`, `max-attempts`, `backoff-base`, `backoff-max`, `lease`, `prune-after`, `prune-batch-size` |
| Pool | `spring.task.scheduling.pool.size` `1` → `2` |
| Declared dependencies | none |

---

## 4. REST API — implement after 2

Detail: `30-rest.md` § 1-6.

| Decision | Value |
|---|---|
| Endpoints | none new. `GET /api/v1/customers/{customerId}` — `CustomerDetailsResponse` gains `status` (`String`, enum name, `@Schema allowableValues`); `POST` unchanged |
| Mapper | `CustomerMapper.toDetailsResponse` adds `customer.status().name()` |
| Errors | none new; `ApiExceptionHandler` REUSE |
| Idempotency | unchanged; replay runs no use case → no second request |
| Personal data in responses | `status` not personal; existing full-value fields unchanged |
| Dependencies | none |

---

## 4.5 Security — conditional

none — skipped by step 3b's rule: `Access` is public and unchanged, and the project has no
`SecurityFilterChain`.

---

## 5. Tests — validates 1-4

Detail: `40-testes.md` § 1-5.

| Decision | Value |
|---|---|
| Unit | `CustomerTest`, `KycVerificationRequestedTest`, `CreateCustomerUseCaseTest`, relay / prune use cases and commands, adapter, payload, `TopicResolver`, both jobs, `OutboxPropertiesTest` |
| Slice | `CustomerControllerTest` — cases of `30-rest.md` § 5 |
| Integration | `CustomerRepositoryJpaAdapterIT`, `AddCustomersStatusMigrationIT`, `OutboxEventStoreIT`, `CreateCustomerOutboxIT`, `OutboxRelayKafkaIT`, `BankAppApplicationTests.noOutboxTriggerRunsUnderTestProfile` |
| Test profile | `app.outbox.enabled: false`, `app.outbox.prune-enabled: false`, `spring.kafka.admin.auto-create: false` |
| Test dependencies | `org.testcontainers:testcontainers-kafka`, `org.awaitility:awaitility` (both managed) |

---

## Design patterns

none — every partial (`10`, `20`, `25`, `30`, `35`) records `none`.

| Force or symptom | Pattern | Classes and interfaces | Decided in |
|---|---|---|---|

---

## Impact on approved use cases

| Approved case | Change | Why | Satisfied by |
|---|---|---|---|
| `UC-001-create-customer` | `Customer` gains `status`; `register` sets `KYC_IN_PROGRESS`; `rehydrate` gains the parameter | every new customer enters KYC | — no precondition added |
| `UC-001-create-customer` | `CreateCustomerUseCase` records `KycVerificationRequested` through `RequestKycVerification`, same transaction | KYC request must not be lost | — no precondition added |
| `UC-001-create-customer` | `customers.status` (`V3`), existing rows `ACTIVE`; entity and mapper map it; tests and `CustomerFixtures` adapted | grandfathered customers | — no precondition added |
| `UC-003-get-customer` | `CustomerDetailsResponse` and `CustomerMapper` carry `status`; `CustomerControllerTest`, `GetCustomerIT` expect it | callers see KYC state | — no precondition added |
| `UC-002-prune-expired-idempotency-keys` | `spring.task.scheduling.pool.size` `1` → `2`; shared `application-test.yml` gains outbox / Kafka switches | two new jobs | — no precondition added |

No row adds a precondition: no approved use case becomes unreachable.

---

## Out of scope

| Item | Owner | What the project does not guarantee until it ships |
|---|---|---|
| Consuming the KYC verdict (`APPROVED` → `ACTIVE`, `REJECT` → `REJECTED_BY_KYC`) | `BL-03` | **Every customer created after this ships stays `KYC_IN_PROGRESS` with no way out** |
| Re-drive of dead-lettered outbox rows | `BL-04` | a dead-lettered KYC request is re-driven only by hand in SQL |
| KYC request for pre-existing customers | not requested | they are `ACTIVE` by decision |
| KYC verdict deadline | not requested | a request the KYC application never answers leaves the customer `KYC_IN_PROGRESS` indefinitely |

**Delegated guarantee:** duplicates (at-least-once) reach the KYC application, whose
idempotency is **unknown** — accepted by the requester, not contracted.

**Pending service:** no `kafka` in `docker-compose.yml`. After approval, run:
`/docker-architect add a single-node KRaft kafka service for UC-004 (external listener on ${KAFKA_EXTERNAL_PORT:-29092}, app service gets KAFKA_BOOTSTRAP_SERVERS), tag aligned with the Testcontainers Kafka image`.

---

## Implementation order (checklist)

[x] 1. Domain exceptions — REUSE; new `errorCode`s only
[x] 2. `CustomerStatus`
[x] 3. `Customer` — `status`, `register`, `rehydrate`, `toString`
[x] 4. `KycVerificationRequested`
[x] 5. Command — REUSE `CreateCustomerCommand`; `RelayOutboxEventsCommand`, `PruneOutboxEventsCommand`
[x] 6. Use cases — `CreateCustomerUseCase` (CHANGE), `RelayOutboxEventsUseCase`, `PruneOutboxEventsUseCase`
[x] 7. Output ports — `RequestKycVerification`, `OutboxRelayGateway`, `OutboxEventSender`, `OutboxRetentionGateway`, `OutboxEventRecord`, `RelayOutcome`
[x] 8. Migrations — `V3`, `V4` from `20-persistencia.md` § 4
[x] 9. Persistence entities — `CustomerEntity` (CHANGE), `OutboxEventEntity`
[x] 10. Spring Data — `OutboxEventJpaRepository`
[x] 11. Adapters — `CustomerPersistenceMapper` (CHANGE), `OutboxEventStore`
[x] 12. Persistence properties — `app.outbox.*` values of `20-persistencia.md` § 5
[x] 13. DTOs + mapper — `CustomerDetailsResponse`, `CustomerMapper` (CHANGE)
[x] 14. Controller / contract / OpenAPI — no change beyond the DTO schema
[x] 15. Idempotency — REUSE
[x] 16. Test fixtures — `CustomerFixtures` (CHANGE)
[x] 17. Unit tests
[x] 18. Integration + contract tests
[x] 19. `./mvnw verify` — green build, coverage gate

Block M (messaging, after REST): `spring-boot-starter-kafka`; `…messaging.outbox` (`TopicResolver`,
`OutboxAppender`, `KafkaOutboxEventSender`, `KafkaOutboxConfig` with `NewTopic` and `retention.ms`
from `app.kafka.topics.kyc-verification-requested.retention`); `…messaging.kycverificationrequested`
(payload, `RequestKycVerificationOutboxAdapter`); producer properties of § 3.5.

Block J (jobs, after Block M): `OutboxProperties`, `OutboxRelayJob`, `OutboxPruneJob`; pool size
`2`; test-profile switches.

---

## Resolved divergences

| Fact | Discarded value | Adopted value | Decided by |
|---|---|---|---|
| Producer `max.block.ms` | client default 60 s (`25-mensageria.md` § 5 silent) | `10000` — asked by `20-persistencia.md` so the `PT8M` lease outlives a worst-case pass; carried into the messaging block, owner `25-mensageria.md` | `20-persistencia.md` (requirement) → `25-mensageria.md` block |
| Backoff formula | `2^attempts × 1 s` (`25-mensageria.md` § 4, guarantee wording) | `backoff-base × 2^(attempts−1)`, base `PT1S`, cap `PT5M` — same guarantee (per-row exponential, 5 min cap) | `20-persistencia.md` — owns the values the columns encode |
| Port adapter name | `KycVerificationRequestOutboxAdapter` (first draft of `25-mensageria.md`) | `RequestKycVerificationOutboxAdapter` — `<Capability><Technology>Adapter`, `@.claude/rules/naming.md` § Architecture vocabulary | naming rule |
| Shape of `status` | "typed field — shape open" (`00-caso-de-uso.md`) | `enum CustomerStatus` | `10-dominio.md` |
| `OutboxProperties` fields | `batch-size`, `prune-after`, `prune-batch-size` (`35-jobs.md` § 8) | plus `max-attempts`, `backoff-base`, `backoff-max`, `lease` | `20-persistencia.md` § 5 |
| Topic retention | broker default, implicit | explicit `retention.ms` `P7D` on the `NewTopic` (checklist row of `25-mensageria.md` § 9) | `25-mensageria.md` |
