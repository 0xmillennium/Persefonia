# ADR 0002: Keep Documentation Limited to Decision Records

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | documentation |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia is still in an early development stage.

Broad public maintainer documentation would become stale quickly and could expose private planning details before the project is ready for public consumption.

The current documentation need is not onboarding. The current need is preserving durable decisions that future development must not accidentally violate.

## Decision

Committed documentation is limited to `docs/decisions/` until the project reaches a public maintainer documentation stage.

Do not create committed architecture guides, development guides, operations runbooks, testing guides, public README content, roadmap documents, or reference documents during this stage.

Decision records are allowed because they preserve durable constraints needed for maintenance and later development.

Temporary notes must use the mechanism defined in [ADR 0003: Use Local Ignored TODO for Temporary Notes](0003-use-local-ignored-todo-for-temporary-notes.md).

## Consequences

The repository stays clean and avoids premature documentation sprawl.

Important decisions remain committed and reviewable.

General explanations and onboarding guides are deferred until they can be accurate and stable.

Maintainers must resist adding broad documentation too early.

## Alternatives considered

- Keep a full public maintainer documentation tree immediately.
- Keep no documentation at all.
- Store temporary planning notes under `docs/`.

A full docs tree was rejected as premature.

No documentation was rejected because durable decisions still need to be preserved.

Temporary planning notes under `docs/` were rejected because they pollute the repository and create history cleanup risk.

## Review triggers

- The project approaches public release.
- External contributors are expected.
- Deployment runbooks become necessary.
- The system reaches a stable enough shape for public onboarding documentation.
