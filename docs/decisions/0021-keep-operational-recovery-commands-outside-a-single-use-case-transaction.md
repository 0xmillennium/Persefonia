# ADR 0021: Keep Operational Recovery Commands Outside a Single Use-Case Transaction

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-04 |
| Scope | architecture / operations / security |
| Supersedes | none |
| Superseded by | none |

## Context

Normal admin mutations use a single `REQUIRED` transaction gateway ([ADR 0017](0017-use-explicit-transactional-admin-command-gateways.md)). Operational cache recovery is different: it coordinates a committed operator request with independent reservation and result transactions around an external provider call. A single encompassing transaction would either delay Audit attribution until provider completion or hold a database transaction across external network I/O.

## Decision

Normal source-domain admin mutations remain governed by [ADR 0017](0017-use-explicit-transactional-admin-command-gateways.md). Operational recovery commands that coordinate independent persistence phases around an external provider call use a framework-free command boundary with application-layer `OWNER` authorization ([ADR 0008](0008-require-owner-authorization-for-admin-mutations.md)), without one encompassing database transaction.

Each recovery request first commits a short preflight transaction that validates eligibility, derives the attempt number, and appends the mandatory `.requested` Audit record. Only after commit does the recovery executor reserve work in `REQUIRES_NEW`, invoke the external provider outside any database transaction, and persist the outcome in `REQUIRES_NEW`.

This exception applies only to operational-recovery workflows and does not weaken ADR 0017 for domain mutations or [ADR 0019](0019-keep-cache-invalidation-outside-public-exposure-correctness.md) for public-exposure authority.

## Consequences

Accepted recovery requests have durable `.requested` Audit attribution before external execution begins, and Audit failure blocks execution.

Reservation, provider execution, and result persistence remain distinct phases protected by optimistic locking and reservation generations rather than a single database transaction.

## Alternatives considered

- Wrap preflight, provider call, and result persistence in one `REQUIRED` transaction. Rejected because external network I/O must not hold a database transaction.
- Append Audit only after provider completion. Rejected because Audit records mandatory operator intent before execution, not provider outcome.
- Weaken ADR 0017 across all admin mutations. Rejected because domain mutations require atomic source, Discovery, and Audit commits.

## Review triggers

- Recovery adopts a durable outbox, queue, or external workflow engine.
- Audit semantics change from mandatory request attribution.
- Operational recovery expands beyond cache invalidation.
