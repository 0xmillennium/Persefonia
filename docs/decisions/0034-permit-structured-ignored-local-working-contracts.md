# ADR 0034: Permit Structured Ignored Local Working Contracts

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-10-05 |
| Scope | documentation |
| Supersedes | ADR 0003 |
| Superseded by | ADR 0037 |

Superseded by [ADR 0037](0037-bound-ignored-local-workspaces-to-active-task-context.md), which retains structured local context while bounding it to active tasks with explicit retention conditions.

## Context

[ADR 0003](0003-use-local-ignored-todo-for-temporary-notes.md) confined temporary notes to `.local/TODO.md`. Active design and qualification work also needs structured temporary working contracts, authority indexes, and decision ledgers without turning `.local/TODO.md` into a specification or committing temporary planning history to `docs/`.

## Decision

Committed `docs/` remains canonical and limited to `docs/decisions/` under [ADR 0001](0001-record-architecture-decisions.md) and [ADR 0002](0002-keep-documentation-limited-to-decision-records.md).

The Git-ignored `.local/` directory may hold structured temporary working contracts and decision ledgers alongside `.local/TODO.md` (which remains the actionable working queue), including `.local/design/` and `.local/tooling/`. These working files must never be unignored or committed.

Local working documents do not become architectural or product authority automatically; they record explicit human approvals, scope, and provenance while keeping unresolved questions distinct from accepted decisions.

Durable architectural decisions must graduate into committed ADRs under `docs/decisions/`, and deleting `.local/` must never make the committed repository architecturally unintelligible.

## Consequences

Active work can maintain structured local context without polluting committed documentation or conflating pending design exploration with accepted architecture.

Because `.local/` is untracked, maintainers must promote durable constraints into committed ADRs, contracts, and tests so other checkouts never depend on local workspace files.

## Alternatives considered

- Keep all temporary context in `.local/TODO.md`. Rejected because a task queue cannot cleanly represent approval provenance and design ledgers.
- Commit working contracts under `docs/`. Rejected because it violates ADR 0002 and preserves temporary planning history as apparent canonical guidance.
- Keep working context only in external tools or chat history. Rejected because local context becomes hard to inspect and recover.

## Review triggers

- Multiple maintainers require shared working contracts across checkouts.
- The project reaches the public maintainer documentation stage described in ADR 0002.
- Durable architectural decisions remain unpromoted in `.local/`.
