# Architecture Decision Index

This directory contains durable decision records for Persefonia.

A decision record is used only when a decision affects long-term maintainability, architecture, security, persistence, operations, testing, or documentation governance.

This directory is not a planning archive.

## What belongs here

- long-lived architecture decisions
- security model decisions
- persistence model decisions
- module boundary decisions
- documentation governance decisions
- decisions future maintainers must understand before changing the system

## What does not belong here

- roadmap notes
- feature planning
- private product planning
- temporary implementation notes
- execution reports
- command transcripts
- local scratch notes
- documentation-only migration placeholders

Temporary notes belong in `.local/TODO.md`.

## Lifecycle

Decision records are append-only by default.

If a decision changes, do not silently rewrite history. Create a new ADR and mark the old one as superseded.

Allowed statuses:

- Proposed
- Accepted
- Superseded
- Deprecated
- Rejected

## Template

Use [TEMPLATE.md](TEMPLATE.md) for new decision records.

## Current decisions

- [ADR 0001: Record Architecture Decisions](0001-record-architecture-decisions.md) — establishes ADRs as the durable decision mechanism.
- [ADR 0002: Keep Documentation Limited to Decision Records](0002-keep-documentation-limited-to-decision-records.md) — keeps committed documentation minimal until the project is mature enough for public maintainer guides.
- [ADR 0003: Use Local Ignored TODO for Temporary Notes](0003-use-local-ignored-todo-for-temporary-notes.md) — defines `.local/TODO.md` as the private, uncommitted place for immature notes.
- [ADR 0004: Use Modular Monolith with App Composition Root](0004-use-modular-monolith-with-app-composition-root.md) — keeps deployment simple while preserving internal boundaries.
- [ADR 0005: Keep Domain Modules Framework-free](0005-keep-domain-modules-framework-free.md) — protects domain code from framework and infrastructure coupling.
- [ADR 0006: Use Explicit SQL Migrations](0006-use-explicit-sql-migrations.md) — requires executable Flyway SQL migrations and forbids documentation-only migration placeholders.
- [ADR 0007: Use OIDC for Admin Authentication](0007-use-oidc-for-admin-authentication.md) — avoids local admin password handling and keeps local admin authority inside Persefonia.
- [ADR 0008: Require OWNER Authorization for Admin Mutations](0008-require-owner-authorization-for-admin-mutations.md) — requires application-layer authorization for state-changing admin commands.
- [ADR 0009: Keep Redis Auxiliary-only](0009-keep-redis-auxiliary-only.md) — prevents Redis from becoming durable domain or authorization state.
- [ADR 0010: Use Spring Data JDBC Through Persistence Adapters](0010-use-spring-data-jdbc-through-persistence-adapters.md) — permits Spring Data JDBC in app-level adapters while keeping Content Publishing domain code framework-free.
