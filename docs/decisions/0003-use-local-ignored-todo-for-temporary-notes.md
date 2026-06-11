# ADR 0003: Use Local Ignored TODO for Temporary Notes

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | documentation |
| Supersedes | none |
| Superseded by | none |

## Context

Not every useful note is mature enough to become a committed decision record.

Temporary notes, candidate decisions, future implementation reminders, and cleanup ideas need a place to live without polluting repository history.

## Decision

Temporary notes are kept in `.local/TODO.md`.

The `.local/` directory must be ignored by Git.

Nothing in `.local/TODO.md` is a project decision.

When a note becomes durable, it must be promoted to an ADR under `docs/decisions/` and removed from `.local/TODO.md`.

## Consequences

Temporary thinking has a place without entering committed history.

The committed repository stays clean.

Important decisions still have a promotion path into ADRs.

Local notes can be lost unless the maintainer backs them up separately.

## Alternatives considered

- Store temporary notes under `docs/`.
- Store temporary notes in committed TODO files.
- Store all notes only in memory or chat history.

Committed temporary notes were rejected because they create documentation sprawl and history cleanup risk.

Untracked memory or chat-only notes were rejected because they are too easy to lose.

## Review triggers

- The project adopts an issue tracker for all future planning.
- More than one maintainer needs shared planning notes.
- Local notes start containing decisions that should be promoted.
