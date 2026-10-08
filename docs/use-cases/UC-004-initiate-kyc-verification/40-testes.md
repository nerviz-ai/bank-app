# UC-004 · Tests

> Partial of `docs/use-cases/UC-004-initiate-kyc-verification/`. Owner: `test-architect`.
> Inherits the invariants from `10-dominio.md`, the queries from `20-persistencia.md`, the jobs
> from `35-jobs.md`, and the contract cases from `30-rest.md` — doesn't reinvent them.
> Contains no code. Reference shapes in `.claude/skills/test-architect/templates/`.
> Rules applied, never reproduced: `@.claude/rules/testing.md`.

| Field | Value |
|---|---|
| Partials read | `00`, `10`, `20`, `25`, `30`, `35` |
| Engine in integration tests | PostgreSQL 16 (`postgres:16-alpine`, REUSE) and Kafka (`apache/kafka`, NEW), ephemeral containers |
| Invariants from `10-dominio.md` | 4 |
| Invariants with a named test | 4 (invariant 2 through `register`, invariant 4 at application and integration level) |

No question asked: the error scenarios, the data (`CustomerFixtures`, REUSE), the clock (fixed,
REUSE `2026-01-15T10:00:00Z`) and the external system (the KYC application, replaced by reading
the topic) are fixed by the partials. Survey: every `@SpringBootTest` already carries
`@ActiveProfiles("test")`; `src/test/resources/application-test.yml` exists (`UC-002`).

## 1 · Distribution by level

| Behavior | Level | Why here |
|---|---|---|
| `register` produces `KYC_IN_PROGRESS`; `rehydrate` restores each status; missing status rejected | Domain unit | Aggregate invariants 1–2 |
| `KycVerificationRequested.of` copies the customer; missing field rejected; `toString` masks | Domain unit | Event invariant 3 and masking |
| Creation records exactly one request after `save`; none when the domain or the uniqueness check rejects; a recording failure propagates | Application unit | Orchestration — invariant 4's application half; ports are doubles |
| Relay pass: sends claimed rows, marks published, records failure and continues, reports dead-letters; `oldestPendingAge` | Application unit | Pass orchestration over `OutboxRelayGateway` / `OutboxEventSender` doubles |
| Prune loop stops on a short batch; cutoff = now − retention | Application unit | Loop logic over `OutboxRetentionGateway` double |
| Commands reject invalid numbers | Application unit | Record invariants |
| Event → payload mapping, event type and aggregate id handed to `OutboxAppender` | Adapter unit | Pure mapping, no I/O |
| Payload `toString` masks `securityNumber` / `birthDate` | Adapter unit | Masking candidates (`@.claude/rules/logging.md`) |
| `TopicResolver` maps the event type; unknown type rejected | Adapter unit | Pure mapping |
| Jobs call their port with the command from properties; relay job records the two delivery metrics | Adapter unit | Triggers tested through the port, never by waiting for a scheduler |
| Properties validation | Adapter unit | Startup validation |
| Status column round-trip; `CHECK` rejects an unknown value | Integration | Mapping and constraint only fail against the real engine |
| `V3` backfills existing rows to `ACTIVE` and leaves no default | Integration | Migration behavior |
| Outbox store: append needs a transaction, lease claim, backoff, dead-letter, mark, oldest-pending, bounded prune keeping pending and dead-lettered rows, two concurrent claims disjoint | Integration | SQL, `SKIP LOCKED`, `jsonb` — real engine only |
| Creation commits customer + one outbox row; rollback leaves neither; idempotent replay adds none | Integration | Transaction boundary across two tables — invariant 4's real half |
| Relay sends a row to the real topic with the right key and JSON text (not double-encoded), and marks it published | Integration | Serialization and broker ack only fail against a real broker |
| No outbox trigger bean under the test profile | Integration (context) | `@.claude/rules/scheduling.md` § Triggers — one context test |
| HTTP contract (`status` in the read, creation unchanged) | Web slice | **Cases defined in `30-rest.md` § 5** — level and data only |

## 2 · Cases per test class

Paths mirror `src/main` under `src/test/java/dev/nerviz/bankapp/`.

| Class | Level | State | Methods |
|---|---|---|---|
| `domain/model/CustomerTest` | Domain | CHANGE | `registerBornKycInProgress` · `rehydrateRestoresStatus` (parameterized: `KYC_IN_PROGRESS`, `ACTIVE`, `REJECTED_BY_KYC`) · `rejectsMissingStatus` (`CUSTOMER_STATUS_REQUIRED`) · existing methods adapted to the new component |
| `domain/event/KycVerificationRequestedTest` | Domain | NEW | `ofCopiesCustomerFieldsAndRegistrationInstant` · `rejectsMissingField` (parameterized over the five fields, `KYC_REQUEST_FIELD_REQUIRED`) · `toStringMasksSecurityNumberAndBirthDate` |
| `application/usecase/customer/CreateCustomerUseCaseTest` | Application | CHANGE | `recordsOneKycRequestAfterSaving` (in-order: `save` then `request`; event carries id, name, security number, birth date, `registeredAt`) · `recordsNoRequestWhenSecurityNumberIsTaken` · `recordsNoRequestWhenCustomerIsUnderage` · `propagatesFailureToRecordRequest` · existing methods kept |
| `application/usecase/outbox/RelayOutboxEventsUseCaseTest` | Application | NEW | `sendsEachClaimedRecordAndMarksItPublished` · `recordsFailureAndContinuesWithNextRecord` · `countsDeadLetteredRecordsInOutcome` · `returnsEmptyOutcomeWhenNothingIsClaimed` · `reportsOldestPendingAge` |
| `application/usecase/outbox/RelayOutboxEventsCommandTest` | Application | NEW | `rejectsNonPositiveBatchSize` (parameterized: `0`, `-1`) |
| `application/usecase/outbox/PruneOutboxEventsUseCaseTest` | Application | NEW | `deletesInBatchesUntilShortBatch` · `usesCutoffOfNowMinusRetention` · `returnsTotalDeleted` |
| `application/usecase/outbox/PruneOutboxEventsCommandTest` | Application | NEW | `rejectsInvalidValues` (parameterized: zero retention, negative retention, batch `0`) |
| `infrastructure/messaging/kycverificationrequested/RequestKycVerificationOutboxAdapterTest` | Adapter unit | NEW | `appendsPayloadWithEventTypeAndCustomerIdAsAggregateId` · `payloadCarriesClearSecurityNumberAndBirthDate` (the § 8 decision — the wire value is the full one) |
| `infrastructure/messaging/kycverificationrequested/KycVerificationRequestedPayloadTest` | Adapter unit | NEW | `toStringMasksSecurityNumberAndBirthDate` |
| `infrastructure/messaging/outbox/TopicResolverTest` | Adapter unit | NEW | `resolvesKycVerificationRequestedTopic` · `rejectsUnknownEventType` |
| `infrastructure/scheduling/outbox/OutboxRelayJobTest` | Adapter unit | NEW | `runsPassWithBatchSizeFromProperties` · `incrementsDeadLetteredCounterFromOutcome` · `registersPendingAgeGauge` |
| `infrastructure/scheduling/outbox/OutboxPruneJobTest` | Adapter unit | NEW | `prunesWithRetentionAndBatchFromProperties` |
| `infrastructure/scheduling/outbox/OutboxPropertiesTest` | Adapter unit | NEW | `rejectsInvalidValues` (parameterized: batch `0`, max-attempts `0`, non-positive lease, prune-after, backoff) |
| `infrastructure/rest/CustomerControllerTest` | Web slice | CHANGE | cases of `30-rest.md` § 5: `getReturnsStatus` (parameterized over the three values) · `createResponseHasNoStatus` |
| `infrastructure/rest/dto/CustomerDetailsResponseTest` | Adapter unit | CHANGE | existing masking test kept; `status` stays readable in `toString` |
| `infrastructure/persistence/customer/CustomerRepositoryJpaAdapterIT` | Integration | CHANGE | `savesAndReadsStatus` (parameterized over the three values) · `rejectsUnknownStatusAtDatabase` (native insert, `CHECK` violation) |
| `infrastructure/persistence/customer/AddCustomersStatusMigrationIT` | Integration | NEW | `backfillsExistingRowsAsActive` · `leavesNoDefaultOnStatus` — Flyway migrated to `V2`, one row inserted, then to `V3`, on its own schema |
| `infrastructure/persistence/outbox/OutboxEventStoreIT` | Integration | NEW | `appendFailsOutsideTransaction` · `claimLeasesRowsAndHidesThemFromSecondClaim` · `expiredLeaseIsClaimableAgain` · `claimSkipsRowsNotYetDue` · `claimReturnsOccurrenceOrder` · `concurrentClaimsAreDisjoint` · `recordFailureBacksOffExponentially` · `recordFailureDeadLettersAtCeiling` · `markPublishedRemovesFromPending` · `oldestPendingAgeIgnoresPublishedAndDeadLettered` · `deletePublishedBeforeKeepsPendingAndDeadLettered` · `deletePublishedBeforeRespectsLimit` |
| `infrastructure/rest/CreateCustomerOutboxIT` | Integration | NEW | `creationRecordsOneOutboxRowWithPayload` (four fields + `eventId`, `occurredAt`) · `rejectedCreationRecordsNoRow` (duplicate security number) · `idempotentReplayRecordsNoSecondRow` |
| `infrastructure/messaging/outbox/OutboxRelayKafkaIT` | Integration | NEW | `relayPublishesRowToTopicAndMarksItPublished` — row appended, `RelayOutboxEventsUseCase.relayPending` called directly, record read from `bank-app.customer.kyc-verification-requested` with Awaitility: key = `customerId`, value parses as a JSON object (not a quoted string), no `__TypeId__` header, row `published_at` set |
| `BankAppApplicationTests` | Integration (context) | CHANGE | `noOutboxTriggerRunsUnderTestProfile` — context has no `OutboxRelayJob` / `OutboxPruneJob` bean |

## 3 · Data and doubles

| Item | Value |
|---|---|
| Customer factory | `CustomerFixtures` — REUSE, CHANGE: valid customer carries `KYC_IN_PROGRESS`; gains `withStatus(CustomerStatus)` built through `Customer.rehydrate` **only for read-side tests** (persistence, controller) — never to reach a state a production path is supposed to produce in a use-case test |
| Clock | fixed `2026-01-15T10:00:00Z`, UTC — REUSE |
| Doubles | `CustomerRepository`, `RequestKycVerification` (CreateCustomerUseCaseTest); `OutboxRelayGateway`, `OutboxEventSender` (relay); `OutboxRetentionGateway` (prune); `OutboxAppender` (adapter unit); `MeterRegistry` → `SimpleMeterRegistry`, real |
| Outbox rows in ITs | built through `OutboxEventStore.append` inside a test transaction, with literal payload text; `now` passed explicitly so lease and backoff are deterministic |
| Kafka IT | `KafkaContainer` (`org.testcontainers.kafka`) with `@ServiceConnection` in a dedicated test configuration imported only by `OutboxRelayKafkaIT`; a plain `KafkaConsumer<String,String>` reads the topic |
| Test profile | `application-test.yml` CHANGE: `app.outbox.enabled: false`, `app.outbox.prune-enabled: false`, and `spring.kafka.admin.auto-create: false` — the `NewTopic` bean must not make every context test wait on a broker no test starts. `OutboxRelayKafkaIT` re-enables `auto-create` with `@TestPropertySource` |
| Kafka image tag | `apache/kafka`, tag resolved once at implementation from the registry (latest stable 4.x), the same tag the pending compose service uses — `java .claude/hooks/ArchHook.java compose` checks the pair |

## 4 · Coverage and gaps

| Invariant (`10-dominio.md` § 2) | Named test |
|---|---|
| 1 status present | `CustomerTest.rejectsMissingStatus` |
| 2 born `KYC_IN_PROGRESS` | `CustomerTest.registerBornKycInProgress` |
| 3 event fields present | `KycVerificationRequestedTest.rejectsMissingField` |
| 4 one request per committed creation, none on rollback | `CreateCustomerUseCaseTest.recordsOneKycRequestAfterSaving` + `CreateCustomerOutboxIT` (all three methods) |

Deliberately uncovered: the KYC application's handling of duplicates (outside this project —
`25-mensageria.md` § 3, idempotency unknown); per-aggregate ordering under several relay
instances (one event per customer today, `35-jobs.md` § 3). No gap blocks implementation.

Coverage gate unchanged (80 % lines / 70 % branches). `KafkaOutboxConfig` is excluded by its
`*Config` name, as configuration.

## 5 · Test dependencies

| Dependency | Version | Why |
|---|---|---|
| `org.testcontainers:testcontainers-kafka` (test) | (managed by the Boot parent, like `testcontainers-postgresql`) | `OutboxRelayKafkaIT` against a real broker |
| `org.awaitility:awaitility` (test) | (managed by the Boot parent) | `OutboxRelayKafkaIT` waits for the record on the topic — `@.claude/rules/testing.md` § Asynchronous effects |

## Impact on approved use cases

| Approved case | Change | Why |
|---|---|---|
| `UC-001-create-customer` | `CustomerTest`, `CreateCustomerUseCaseTest`, `CustomerRepositoryJpaAdapterIT`, `CustomerFixtures` adapted to `status` and the new constructor parameter | `Customer` gains a component |
| `UC-003-get-customer` | `CustomerControllerTest`, `GetCustomerIT` expect `status` in the body | `CustomerDetailsResponse` gains `status` |
| `UC-002-prune-expired-idempotency-keys` | `application-test.yml` gains the outbox switches and `spring.kafka.admin.auto-create: false` | shared test profile |
