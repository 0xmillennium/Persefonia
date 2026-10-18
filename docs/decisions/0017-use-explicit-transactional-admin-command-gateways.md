# ADR 0017: Use Explicit Transactional Application Gateways for Admin Mutations

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-02 |
| Scope | architecture / persistence / security |
| Supersedes | none |
| Superseded by | none |

## Context

Mandatory Audit records for admin mutations must commit atomically with their source domain changes ([ADR 0016](0016-use-post-commit-operational-side-effects.md)). Exposing concrete application services directly to controllers or relying on repository-scoped transactions cannot coordinate a complete use case across source mutation, Discovery projection, and Audit append while keeping application modules framework-free ([ADR 0005](0005-keep-domain-modules-framework-free.md)).

## Decision

Every state-changing admin use case is exposed through a narrow, framework-free command gateway interface owned by its source application module. The `:app` composition root implements each gateway with a Spring `@Transactional` adapter using default `REQUIRED` propagation, and web controllers depend on these gateway interfaces rather than concrete mutation services.

Source application services retain `OWNER` command authorization ([ADR 0008](0008-require-owner-authorization-for-admin-mutations.md)) and domain mutation logic. Route protection remains defense in depth only.

Repository-local `TransactionTemplate` usage is an aggregate-persistence fallback that joins the active gateway transaction, not the use-case transaction boundary. `REQUIRES_NEW` is forbidden for both source mutations and mandatory same-transaction Audit appends.

Expected validation and business rejections return typed results. Unexpected runtime, persistence, transaction, or Audit failures propagate so Spring rolls back the transaction.

## Consequences

Admin mutations gain an explicit use-case transaction boundary in `:app` while source modules stay framework-free and controllers remain thin transport adapters.

Authorization stays testable independently of HTTP routing, and unexpected infrastructure or Audit failures reliably roll back the entire mutation.

## Alternatives considered

- Put `@Transactional` on web controllers. Rejected because transaction boundaries belong to application composition, not HTTP transport adapters.
- Treat repository-local transactions as the use-case boundary. Rejected because they cannot atomically coordinate source mutation, Discovery, and Audit.
- Convert unexpected exceptions into business result values. Rejected because it hides infrastructure failures and risks partial commits.
- Use `REQUIRES_NEW` for Audit append. Rejected because Audit and source state could commit independently.

## Review triggers

- Admin mutations require coordination across more than one database transaction.
- Mandatory Audit delivery changes from the same-transaction model in ADR 0016.
- A command cannot be expressed through a source-owned gateway without cross-context repository access.
- The `OWNER` mutation authorization model changes.
