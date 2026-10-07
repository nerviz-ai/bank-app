# UC-002 · REST adapter

> Partial of `docs/use-cases/UC-002-prune-expired-idempotency-keys/`. Owner: `rest-api-architect`.
> Inherits the canonical names from `00-caso-de-uso.md` and the ports from `10-dominio.md` —
> doesn't reinvent them.
> Rules applied, never reproduced: `@.claude/rules/api-rest.md`.

| Field | Value |
|---|---|
| Parent spec | `00-caso-de-uso.md` |
| Domain partial | `10-dominio.md` |
| Resource | none — the trigger is a schedule (`00-caso-de-uso.md` § Trigger); no HTTP exposure |
| Version prefix | not applicable |
| Divergences from `10-dominio.md` | none |

No endpoint is designed for this case. The mother spec fixes a scheduled trigger and
`Access: not applicable`; `PruneExpiredIdempotencyKeysUseCase` has no HTTP caller, and an
endpoint with no requested caller is a guessed contract (step 4). No question asked: every
axis of the interview has no subject without an endpoint. A manual "run now" endpoint was not
requested — adding one would be a second trigger and a new `Access` decision.

## 1 · Endpoints

none.

## 2 · DTOs

none. Existing DTOs re-derived against `@.claude/rules/logging.md` § Masking candidates
(step 5, existing-DTO check): this case touches no endpoint, so no existing DTO is in scope.

## 3 · Error map

none — no endpoint. `ValidationException` `PRUNE_BATCH_SIZE_INVALID` /
`PRUNE_MAX_BATCHES_INVALID` (`10-dominio.md` § 2) never reach `ApiExceptionHandler`: they
fire only from the scheduling trigger.

## 4 · Pagination, idempotency, and dependencies

| Decision | Value |
|---|---|
| Pagination | none — no collection exposed |
| Idempotency | none on the transport — no `POST`. The case **operates on** the shared `idempotency_keys` table `UC-001` created; it does not add an `Idempotency-Key` endpoint |
| Dependencies | none |

**Schema requirements** — none. The table, its columns and `ix_idempotency_keys_expires_at`
exist (`V2__create_idempotency_keys.sql`); what the bounded delete needs is
`persistence-architect`'s, from `10-dominio.md` § 3.

## 5 · Contract test cases

none — no endpoint.

## 6 · Personal data

none — no response body leaves the process.

## Design patterns

none — no controller, mapper or handler is added.

## Impact on approved use cases

none.
