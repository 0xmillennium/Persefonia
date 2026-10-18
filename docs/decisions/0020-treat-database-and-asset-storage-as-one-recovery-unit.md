# ADR 0020: Treat Database and Asset Storage as One Recovery Unit

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-03 |
| Scope | architecture / persistence / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia's durable business state is split between PostgreSQL (asset metadata and references) and durable binary asset storage (currently filesystem-backed). Restoring either store alone leaves state semantically incomplete or inconsistent, so recovery must preserve the relationship between database metadata and referenced binary assets.

## Decision

Persefonia treats its relational database state and durable asset binary storage as one logical recovery unit. Valid backups and restores must preserve consistency between database references and durable binary assets across a compatible application and schema version.

Auxiliary state—including caches, Redis rate-limit state ([ADR 0009](0009-keep-redis-auxiliary-only.md)), and HTTP sessions—is outside the durable recovery unit and may be rebuilt rather than restored. This invariant is storage-provider-independent.

## Consequences

Neither a database-only backup nor an asset-only backup is a complete recovery unit; backup and restore workflows must coordinate both stores.

Future storage migrations must preserve this invariant, while auxiliary caches, sessions, and short-lived Redis state can be recreated on startup.

## Alternatives considered

- Treat PostgreSQL or asset storage as independently recoverable. Rejected because metadata and binary contents depend on each other.
- Include Redis, session, and cache state in the durable recovery unit. Rejected because those stores are auxiliary or ephemeral.
- Restore stores independently and reconcile later. Rejected because routine recovery must yield a coherent source of truth immediately.

## Review triggers

- Binary asset storage moves from the filesystem to object storage or multiple durable stores.
- Asset ownership, PostgreSQL backup architecture, or recovery point/time objectives change.
