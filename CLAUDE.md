# bank-app

Spring Boot · Java 21 · Build: maven · Architecture: **Clean Architecture (Uncle Bob) — single-module**

**Bounded context: `bank-app`.** First segment of every topic name, for every use
case, forever — renaming it is a migration of every topic, not a refactor. Whoever designs
messaging reads this line; nobody decides the prefix inside a use case.

**Transport: topology A — TLS terminates at the edge.** The application holds no key and trusts
`X-Forwarded-*` only from `TRUSTED_PROXIES`; HSTS is written by the application, never by the
edge. No local Caddy edge: the local profile runs plain HTTP on 8080. Norms:
`.claude/rules/transport-security.md`.

## Commands

| Action | Command |
|---|---|
| Full build | `./mvnw clean verify` |
| Tests | `./mvnw test` |
| Run locally | `./mvnw spring-boot:run` |
| Format | `./mvnw spotless:apply` |
| Read the audit trail — what each skill and agent run cost and changed | `/audit-usage` |

## Invariants (non-negotiable)

1. The `domain` package doesn't import Spring, JPA, Jackson, or any framework.
2. Dependencies always point inward: adapters → application → domain.
3. No framework exception crosses the domain boundary.
4. Every new public class is born with a test. No exception.
5. Constructor injection — never `@Autowired` on a field.
6. Secrets never go into a versioned file — `.claude/rules/secrets.md`.

## Routing — when X, read Y

| If the task involves | Use |
|---|---|
| Designing a use case before implementing it | skill `use-case-design` |
| Aggregate, value object, invariant, ports | skill `domain-modeling` |
| Table, JPA mapping, migration, index, slow query, datasource | skill `persistence-architect` |
| Endpoint, controller, DTO, OpenAPI | skill `rest-api-architect` |
| Who may call an endpoint — roles, groups, only the owner — Spring Security, JWT, 401/403, CORS, Actuator exposure | skill `security-architect` — norms in `.claude/rules/authorization.md` |
| Calling another system over HTTP — `RestClient`, `@HttpExchange`, OpenAPI client, `WebClient` stream, Feign, timeouts, retry, circuit breaker, outbound authentication, a provider down (502/503/504) | skill `http-client-architect` — norms in `.claude/rules/http-client.md` |
| Design pattern, growing `if`/`switch` chain, class with too many responsibilities | Inside `/new-feature`: decided by each design skill in its partial's `## Design patterns`, through `gof-design-patterns` § Design-time use. On existing code, outside a feature run: skill `gof-design-patterns` — manual only (`/gof-design-patterns`) |
| Tests, coverage, installing ArchUnit | skill `test-architect` |
| Orchestrating a full feature (use case → domain → REST → persistence → tests) | skill `new-feature` — manual only: the user types `/new-feature <description>`, the model can't invoke it. One use case per run |
| Diagnosing hooks and enforcement | `/arch-doctor` |
| What a skill or agent run cost, what it chained, what it changed — invoked by `/command` or by the model | skill `audit-usage` — consolidates spend per skill and agent and failures across runs, and points at the report to open. The reports themselves are `.claude/audit-usage/<timestamp>--<piece>.md`, written by the `audit` hook at every `Stop`, never by hand; `history.jsonl` next to them is one line per top-level run, `nodes.jsonl` one line per piece it chained. Delete the directory to switch the trail off |
| Kafka producer/consumer, publishing or consuming a domain event over a broker | skill `messaging-architect` |
| Scheduled or background job, cron, a job running twice across replicas, the outbox relay's schedule | skill `jobs-architect` |
| Adding a service to `docker-compose.yml` (database, broker, observability backend), image tag consistency with Testcontainers | skill `docker-architect` — the single owner of every service block |
| HTTPS, TLS, certificates, HTTP/2, HSTS, running behind a proxy — where TLS terminates | skill `transport-security-setup` — the decision is the **Transport:** paragraph above; norms in `.claude/rules/transport-security.md`, key material in `.claude/rules/secrets.md` |
| SonarQube or SonarCloud analysis — scanner plugin, server URL, CI step, a local SonarQube container | skill `sonarqube-setup` — asks whether a server exists; never writes a token, the scanner reads `SONAR_TOKEN` from the environment |
| Reducing SonarQube issues at their source — run the analysis, trace each group of issues to the `.claude/` template or norm that produced it, report it upstream | skill `sonar-lessons` — manual only (`/sonar-lessons`). Writes `docs/lessons-learned/sonar-NNN.md` and ends with the `/report-issue` command that files it |
| Reporting a defect or a gap in this `.claude/` upstream, to Nerviz — from a description or a `docs/lessons-learned/` file | skill `report-issue` — manual only (`/report-issue`). Asks for the evidence a maintainer needs to verify it, warns when this project is behind the latest release, opens the issue only after confirmation, carrying no path, package or code of this project |
| Pulling a newer version of this `.claude/` into the project | skill `arch-adopt` — manual only (`/arch-adopt`), refuses a dirty worktree |
| Creating a git repo, committing, or pushing this project | skill `git-publish` — chained automatically after `java-spring-boot-developer` succeeds; behind two confirmations |

## Rules

The detailed rules live in `.claude/rules/` — index at `.claude/rules/00-index.md`.
Read the rule **when** you need it. Don't load them all: each one is the single source
of truth for its topic and is cited, never copied.

## Known pitfalls

- **`AskUserQuestion` with one option fails** with `InputValidationError`. A question with
  one option isn't a question: decide, and record the decision. **More than 4 options, or
  more than 4 questions in one call, fails the same way:** ask only what is still open, offer
  four and leave the rest to Other, and send further questions in the next call.
- **The shell may be zsh.** Quote every glob passed to a command (`--include='*.java'`);
  unquoted, zsh aborts with `no matches found` before the command runs. To list
  `docs/use-cases/UC-*`, use `find docs/use-cases -maxdepth 1 -name 'UC-*'`, not `ls`.
- **Git goes through `git-publish` only**, behind its two confirmations. Never approve a
  broad `git *` permission mid-task.
- **`/clear` between two `/new-feature` runs.** One use case per run, in a clean context.
- **Local values live in the untracked `.env`** — `DB_PASSWORD` and any host port moved off
  its default. docker compose reads it for the services, and `application.yml` imports it for
  the run on the host. A fresh clone copies `.env.example` to `.env` first; without it,
  `docker compose up` stops and names the missing variable. Never commit `.env`, never read
  it back into the conversation.
- **The database image applies `DB_NAME`, `DB_USERNAME` and `DB_PASSWORD` only once**, when its
  volume is empty. Changing them later changes what `docker exec … env` shows and nothing
  else: the old database and user stay. `docker compose down -v` applies the new values, and
  deletes the data.

## Module structure

- `.` — depends on: nothing

This blueprint is `layout: single-module`: there is no per-layer POM and the compiler
enforces no boundary at all. Outside Claude Code the project has no enforcement
whatsoever until ArchUnit is installed (`test-architect`) — boundaries are guaranteed
only by `.claude/forbidden-imports.txt` and the hook that reads it, inside Claude Code.

**One exception to where Spring wiring goes: adapter-local wiring stays with its adapter.**
A `@Configuration` that builds a `ProducerFactory`, a `KafkaTemplate`, a `DefaultErrorHandler`,
or enables the scheduling a relay depends on carries broker types, and
`.claude/rules/messaging.md` § Boundary requires every such type to sit under the messaging
package. It is wiring, and it still does not go to the configuration package. The coverage
exclusion follows the same logic — configuration classes are excluded by what they are
(`**/*Config.class`, `**/*Configuration.class`), not only by the package they sit in, so this
exception does not silently move the coverage number.
