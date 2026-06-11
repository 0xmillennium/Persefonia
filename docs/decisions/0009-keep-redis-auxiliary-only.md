# ADR 0009: Keep Redis Auxiliary-only

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | persistence |
| Supersedes | none |
| Superseded by | none |

## Context

Redis is useful for auxiliary runtime capabilities, but it should not become the source of truth for domain or authorization state.

Durable state must remain reproducible from the primary database and application-managed durable storage.

## Decision

Redis is auxiliary-only.

Redis must not be used as primary durable storage for aggregate state, authorization state, or other state that cannot be safely lost or rebuilt.

## Consequences

Domain state remains durable outside Redis.

Redis-backed features must tolerate data loss or be rebuildable from durable state.

Features that require durable state must use the primary persistence model instead.

## Alternatives considered

- Store primary domain state in Redis.
- Store primary authorization state in Redis.
- Use Redis as an implicit source of truth for operational behavior.

These alternatives were rejected because they make recovery and correctness harder.

## Review triggers

- A future feature proposes Redis as durable state.
- Session storage strategy changes.
- Redis-backed behavior becomes required for correctness instead of performance or auxiliary behavior.
