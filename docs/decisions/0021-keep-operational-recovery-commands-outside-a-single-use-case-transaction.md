# ADR 0021: Keep Operational Recovery Commands Outside a Single Use-Case Transaction

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-04 |
| Scope | architecture / operations / transactions / security |
| Supersedes | none |
| Superseded by | none |

## Context

Normal source-domain admin mutations use the single `REQUIRED` application gateway established by [ADR 0017](0017-use-explicit-transactional-admin-command-gateways.md), so source correctness, Discovery, and mandatory Audit commit atomically. Cache recovery is different: its purpose is to coordinate a committed operator request with independent reservation and result transactions around an external provider call. One encompassing transaction would either make the Audit record untruthful until provider completion or place the failure-prone provider call inside a database transaction.

This decision builds on OWNER authorization at both route and application layers in [ADR 0008](0008-require-owner-authorization-for-admin-mutations.md), post-commit transaction choreography in [ADR 0016](0016-use-post-commit-operational-side-effects.md), normal mutation gateways in ADR 0017, and the cache's freshness-only role in [ADR 0019](0019-keep-cache-invalidation-outside-public-exposure-correctness.md).

## Decision

Normal source-domain state-changing admin use cases remain governed by ADR 0017 and use one `REQUIRED` transactional gateway containing source mutation, correctness-critical synchronous work, and mandatory Audit.

Operational recovery commands that coordinate independent local persistence phases around a failure-prone external side effect use a narrow framework-free command boundary and application-layer OWNER authorization, but their orchestration gateway does not create one encompassing database transaction.

Each recovery request first runs a short transaction that revalidates eligibility, derives its server-side attempt number, and appends the mandatory Audit record describing operator intent. Only after that transaction commits may the existing cache execution infrastructure reserve work in `REQUIRES_NEW`, invoke the provider without a database transaction, and persist the result in `REQUIRES_NEW`.

This qualification applies only to operational-recovery workflows. It does not weaken ADR 0017 for Content, Project, Media, Taxonomy, Redirect, IAM, or any other normal source mutation. Cache invalidation remains freshness infrastructure and does not become a public-exposure correctness authority.

## Consequences

An accepted recovery request has durable attribution even if the process stops before provider execution. An Audit action ending in `.requested` records intent and does not claim provider or persistence success. Audit failure blocks execution. A race after preflight can leave a truthful request record while a later reservation safely rejects changed state.

The workflow deliberately retains the crash gap accepted by ADR 0016. Reservation, provider invocation, and result persistence remain separate phases; optimistic locking and reservation generations protect newer recovery work from delayed provider results.

## Alternatives considered

- Wrap preflight, provider execution, and result persistence in one `REQUIRED` transaction. Rejected because external work must remain transaction-free and durable reservations/results require independent commits.
- Append Audit after provider completion. Rejected because Audit answers who requested recovery and must be mandatory before execution, not an operational outcome log.
- Weaken ADR 0017 for all admin mutations. Rejected because normal source mutations still require one atomic source/Discovery/Audit transaction.
- Add an outbox, queue, scheduler, or automatic retry. Rejected for the MVP by ADR 0016 and the explicit manual-recovery scope.

## Review triggers

- Recovery moves to a durable queue, outbox, or external workflow engine.
- Audit semantics change from mandatory request attribution.
- Provider execution can participate safely in a distributed transaction.
- Operational recovery expands beyond cache invalidation.
