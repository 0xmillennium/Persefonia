# ADR 0001: Record Architecture Decisions

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | documentation |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia needs durable records for decisions that affect long-term maintainability, architecture, security, persistence, operations, testing, and documentation governance.

Commit messages, private notes, and temporary planning material are not reliable long-term decision records.

## Decision

Persefonia records material decisions as ADRs under `docs/decisions/`.

Each ADR must describe one durable decision, its context, its consequences, alternatives considered, and review triggers.

The decision index is maintained in [INDEX.md](INDEX.md).

New ADRs must use [TEMPLATE.md](TEMPLATE.md).

## Consequences

Future maintainers can understand why important constraints exist.

Decision history becomes discoverable without preserving temporary planning material.

Material decision changes require a new ADR or a superseding ADR instead of silent rewriting.

## Alternatives considered

- Rely on commit messages.
- Keep decisions only in private local notes.
- Mix decisions into general documentation files.

These alternatives were rejected because they make decisions harder to discover and easier to accidentally lose.

## Review triggers

- The project adopts a different decision-recording mechanism.
- The repository reaches a public maintainer stage that requires broader documentation.
- ADRs become too heavy or too vague to maintain.
