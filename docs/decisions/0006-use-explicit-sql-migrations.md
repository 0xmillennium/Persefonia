# ADR 0006: Use Explicit SQL Migrations

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | persistence |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia needs reviewable, deterministic database schema evolution.

The repository must avoid accidental schema generation and documentation-only migration placeholders.

## Decision

Database schema changes are maintained through executable Flyway SQL migrations.

Executable migrations live under:

```text
app/src/main/resources/db/migration/
```

ORM-generated DDL is not used.

Documentation-only `.sql.todo` migration placeholders are forbidden.

## Consequences

Database evolution is explicit and reviewable.

Migration history is executable rather than aspirational.

Developers must write and maintain SQL carefully.

Future schema plans must not be stored as fake migration files.

## Alternatives considered

- ORM-generated DDL.
- Documentation-only migration placeholders.
- Manual database changes outside migration history.

These alternatives were rejected because they make schema state harder to review and reproduce.

## Review triggers

- The project changes migration tooling.
- A future persistence model no longer uses PostgreSQL.
- Migration volume or complexity requires additional migration governance.
