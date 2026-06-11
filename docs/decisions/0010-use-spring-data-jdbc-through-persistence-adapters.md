# ADR 0010: Use Spring Data JDBC Through Persistence Adapters

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | persistence |
| Supersedes | none |
| Superseded by | none |

## Context

Content Publishing needs aggregate persistence without coupling framework-free domain modules to Spring Data JDBC.

The persistence approach must preserve [ADR 0004: Use Modular Monolith with App Composition Root](0004-use-modular-monolith-with-app-composition-root.md), [ADR 0005: Keep Domain Modules Framework-free](0005-keep-domain-modules-framework-free.md), and [ADR 0006: Use Explicit SQL Migrations](0006-use-explicit-sql-migrations.md).

## Decision

Use Spring Data JDBC through app-level persistence adapters with adapter-local persistence models.

Domain modules remain framework-free. Spring Data JDBC annotations and classes stay in app adapter persistence models, not in domain classes.

Domain aggregates map to persistence models at the adapter boundary through explicit mappers.

Spring Data JDBC may be used where aggregate-shaped persistence is clean. `JdbcTemplate` and `NamedParameterJdbcTemplate` remain allowed for custom queries and explicit replacement or upsert behavior when conventions are insufficient.

`ContentRevision` remains a separate aggregate root.

Only aggregate roots may have repositories. Child repositories are forbidden.

## Consequences

Content Publishing can use Spring Data JDBC without weakening domain-module boundaries.

Manual mapping adds adapter code, but makes the domain-persistence boundary explicit and reviewable.

Custom SQL remains available for route/read queries, replacement of render snapshots and headings, and other cases where Spring Data JDBC conventions obscure the intended persistence behavior.

Future maintainers must keep Spring Data JDBC dependencies and annotations out of `:content-publishing`.

## Alternatives considered

- Annotate domain classes with Spring Data JDBC annotations.
- Add Spring Data JDBC dependency to `:content-publishing`.
- Use JPA or Hibernate.
- Add child repositories for render snapshots, rendered headings, or metadata.
- Use JSONB or array columns as shortcuts for core aggregate state.

These alternatives were rejected because they either couple domain modules to infrastructure, weaken aggregate boundaries, or hide core state from relational constraints.

## Review triggers

- Content Publishing aggregate boundaries change.
- Spring Data JDBC cannot support a required aggregate persistence behavior without excessive adapter complexity.
- A future persistence model proposes domain annotations, child repositories, JPA/Hibernate, or JSONB/array shortcuts for core aggregate state.
