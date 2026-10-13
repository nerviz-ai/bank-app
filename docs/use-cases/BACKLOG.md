# Use case backlog

Use cases a split left for later, in dependency order. Each row carries a **`BL-NN`**, its own
identifier, assigned when the row is appended and never reused. **It is not a `UC` number and
never becomes one** — the two sequences are independent, which is what lets a row be dropped or
merged without leaving a hole in the `UC` sequence. No slug either: both the number and the slug
are fixed only when the case is designed.

| Id | Depends on | Description for `/new-feature` | Split from |
|---|---|---|---|
| `BL-04` | `UC-004-initiate-kyc-verification` | REST endpoint or operator command that resets a dead-lettered row of the shared `outbox_events` table to pending (attempts 0, `dead_lettered` false), so the relay re-sends it. Unknown event id is not found; a row that is not dead-lettered is a conflict | `UC-004-initiate-kyc-verification` — deferred by `25-mensageria.md` § 9 |
| `BL-05` | `UC-006-anonymize-customer` | Local Keycloak service in `docker-compose.yml` for development: realm `bank`, a client whose tokens carry audience `bank-app` and a flat top-level `roles` claim, test users holding `DATA_PROTECTION_OFFICER` and `OPERATOR`, reachable at `http://localhost:8180/realms/bank` (the default of `OAUTH2_ISSUER_URI`). Owner of the service block: `docker-architect`. Until it exists, a local run answers 401 on every protected call | `UC-006-anonymize-customer` — deferred by `32-seguranca.md` § 11 |

## Retired

A row leaves the table above when its `/new-feature` starts and the real `UC-NNN` is assigned.
It lands here instead of being deleted: a spec that cited `BL-NN` is immutable once approved, so
the citation has to stay resolvable exactly at the moment the case stops being backlog.

| Id | Became | Date |
|---|---|---|
| `BL-01` | `UC-002-prune-expired-idempotency-keys` | 2026-10-08 |
| `BL-03` | `UC-005-record-kyc-verdict` | 2026-10-12 |
| `BL-02` | `UC-006-anonymize-customer` | 2026-10-13 |
