# ADR 0034: Permit Structured Ignored Local Working Contracts

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-05 |
| Scope | documentation |
| Supersedes | ADR 0003 |
| Superseded by | none |

## Context

[ADR 0003](0003-use-local-ignored-todo-for-temporary-notes.md) confines temporary notes to `.local/TODO.md`. Active design work also needs structured working contracts, authority indexes, and decision ledgers to distinguish approved references from unresolved questions without making a working queue a design specification or committing temporary planning history.

## Decision

Committed `docs/` remains canonical and ADR-focused under [ADR 0001](0001-record-architecture-decisions.md) and [ADR 0002](0002-keep-documentation-limited-to-decision-records.md). This decision supersedes ADR 0003's TODO-only restriction, not the committed documentation boundary.

The repository-local `.local/` directory remains ignored by Git. It may contain structured temporary working contracts and decision ledgers alongside `.local/TODO.md`, which remains the actionable working queue. `.local/design/` is specifically permitted for active design work, including indexes of approved artifacts, unresolved questions, implementation handoff criteria, and bounded qualification findings. Do not unignore or commit these working documents.

Working documents do not become architectural or product authority automatically. An authority index records an explicit approval and its artifact, scope, and provenance; writing or generating an entry is not approval. Candidate decisions and unresolved questions must remain distinguishable from approved decisions.

Durable architectural decisions must graduate into the appropriate committed ADR under `docs/decisions/`. Remove superseded temporary decision detail from local notes and retain only useful references and remaining actions. Temporary visual exploration and execution history must not be copied wholesale into ADRs.

The repository remains the executable source of truth. Deleting `.local/` must not make its architecture unintelligible: durable constraints and rationale must remain discoverable through committed ADRs, implementation contracts, and tests.

## Consequences

Active work can retain structured context without expanding public documentation or conflating pending design choices with accepted architecture. Maintainers must preserve approval provenance and promote durable architectural decisions rather than leaving them solely in local ledgers.

Ignored working documents are local and can be lost; maintainers must back them up separately if needed. Other checkouts cannot rely on their presence. Their absence must not prevent understanding or maintaining the committed architecture.

## Alternatives considered

- Keep all temporary context in `.local/TODO.md`. Rejected because a queue cannot cleanly represent artifact authority, unresolved design questions, and handoff criteria.
- Commit active design ledgers under `docs/`. Rejected because it violates ADR 0002's documentation boundary and preserves temporary planning history as apparent canonical guidance.
- Keep authority and decisions only in external tools or chat history. Rejected because working context becomes hard to recover and durable architecture can become dependent on external access.

## Review triggers

- Shared working contracts become necessary across maintainers or checkouts.
- The project reaches the public maintainer documentation stage described in ADR 0002.
- Durable architectural decisions remain solely in ignored ledgers.
- Removing local workspace state prevents understanding the committed architecture.
