# bank-app — instructions for coding agents

Read `CLAUDE.md`, at this root, before changing anything. It is written for every agent, not
only Claude Code: the stack, the build and test commands, the invariants, the module
structure, and its section "Under another agent", which says which of this project's checks
hold for you and which do not.

- **Norms** live in `.claude/rules/`, one topic per file; `.claude/rules/00-index.md` says
  which file covers what. Open the one your change touches.
- **Each module with import restrictions** has its own `AGENTS.md`, listing what that module
  may not import.
