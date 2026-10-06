# 🌱 Genesis — the `/init-project` run that created this project

This is **not** a report from `ArchHook.java audit`. That hook's trail only starts once
a live Claude Code session opens with this project as its root — the run that created
the project happened before any such session could exist here. This entry is a one-time,
meta-repo-side reconstruction: no per-tool-call timeline, no per-piece breakdown, no ranked
stages — that granularity was never captured, because no hook was watching yet. Started,
Finished, tokens and cost are the exception: `ArchHook.java audit genesis` read them from
that session's transcripts, the main session's and its subagents', and priced them from
`pricing.json` — the whole run, from the `/init-project` message to the moment it ran. Every
report that follows this one in this directory *is* hook-produced, and looks different.

| | |
|---|---|
| Command | `/init-project` |
| Blueprint | `clean-architecture-single-module` — Same domain/application/infrastructure separation as `clean-architecture-multi-module`, but in a single Maven module: the boundary is enforced only by ArchUnit, not the compiler |
| Coordinates | `dev.nerviz:bank-app` |
| Build tool | maven |
| Started | 2026-10-06T15:35:51Z |
| Finished | 2026-10-06T15:55:46Z |
| Tokens | 254 input · 54,820 output · 23,403,080 cache read · 406,612 cache write — 127 requests, claude-sonnet-5, claude-opus-5-5 |
| Cost | USD 6.38 |
| Build | PASSED |

## Output contract

The exact block reported to the user at the end of the run, verbatim:

```
✅ Project bank-app created — blueprint clean-architecture-single-module

Modules:
  . → depends on nothing

Versions (resolved by the Initializr): Java 21 · Spring Boot 4.1.1 · maven
Active features: rest, validation, persistence-jpa, uuid-v7, flyway, openapi, testcontainers, actuator, observability, archunit
Business code: none — by design. 13 package-info.java written
Boundaries: 15 rules in .claude/forbidden-imports.txt — blocking verified ✓
Checkstyle: config/checkstyle/checkstyle.xml — plugin 3.6.0 · tool 14.3.0, validate phase · checkstyle-test.xml over src/test
Spotless: check bound to the build (verify) — formatting and unused imports, main and test
Lombok: lombok.config at the root — @Data and @Setter stop compilation
ArchUnit: to be installed — `test-architect` skill (see Next steps)
Coverage: JaCoCo generates a report; the 80%/70% gate comes in with `test-architect`
Self-contained: 19 rules + 20 skills + 3 agents + ArchHook.java + extensions.json written by `ArchHook.java export` — no dead paths ✓
Provenance: .claude/.arch-provenance.json — blueprint clean-architecture-single-module, ref working-tree, commit c95951f. `/arch-doctor` reports anything edited since
Audit trail: .claude/audit-usage/ active — one report per skill or agent invocation from now on, by `/command` or by the model. GENESIS.md records this run itself. Fill pricing.json to see cost
Docker: Dockerfile + docker-compose.yml — app, postgres (persistence-jpa), otel-collector (observability), sonarqube (local SonarQube, sonarqube-setup) — extend with `docker-architect` for anything a future use case adds
Local secrets: .env (untracked) holds DB_PASSWORD, read by docker compose and imported by application.yml for the host run; a fresh clone copies .env.example
Observability UI: none — the collector exports to `debug`, which writes spans and metrics to its own stdout and is not a dashboard. Run `/docker-architect` to add one: Jaeger (traces, one container) or Grafana + Tempo + Prometheus (traces and metrics, three)
Transport: topology A edge — HTTP/2 on, HSTS by HstsHeaderFilter, ForwardedHeadersIT
SonarQube: local container on http://localhost:9000 — scanner 5.8.0.7211, CI step none
MCP: none — no server designed for this project yet
Build: PASSED
Docs: README.md (English, default) + README.pt-br.md — origin, blueprint, stack, skills/agents, this report

Next steps:
  1. /use-case-design <first-use-case-name>
  2. Install the architecture tests (ArchUnit) and wire up the coverage gate with the
     `test-architect` skill as soon as business classes exist. Until then boundaries
     are guaranteed only by the hook (inside Claude Code). This blueprint is
     `layout: single-module`: there is no per-layer POM, so outside Claude Code (a plain
     `mvn verify`, or any edit made without it) the project has no enforcement at all.
  3. SonarQube — local: `docker compose up -d sonarqube`, log in at http://localhost:9000
     as admin/admin (a password change is forced), create a token under My Account →
     Security, `export SONAR_TOKEN=…`, then `./mvnw -B verify sonar:sonar`
```

---

*Written once, by `project-initializer`, at the end of the run that created this
project. Never rewritten, never appended to.*
