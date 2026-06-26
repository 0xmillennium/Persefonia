# ADR 0016: Use Post-Commit Operational Side Effects and Same-Transaction Audit Appends

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-26 |
| Scope | architecture / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia is about to grow operational state changes: mandatory audit records for important admin mutations, and cache invalidation for public surfaces when published content changes. These need a clear delivery model before any of them is implemented.

Two side-effect kinds have different reliability needs. An audit record must exist exactly when an important admin mutation successfully commits, and it must never alter or authorize the business action. Cache purge touches external infrastructure, can fail or be slow, and must never roll back the business transaction that triggered it.

A post-commit mechanism already exists. The contact workflow registers owner mail notification and an aggregate Insights observation through `PostCommitTaskExecutor`, which runs tasks only after the source transaction commits and isolates their failures from the business result. No general event bus, queue, or transactional outbox exists, and the Audit and Platform Operations modules are still empty scaffolding.

## Decision

Mandatory audit records for important admin mutations are appended in the same application transaction as the mutation, through Audit application ports. Source application services may depend on Audit application ports but never on Audit repositories or infrastructure.

Failure-prone operational side effects, cache purge first among them, are requested and executed after the source transaction commits, using the existing post-commit mechanism. Their failures never roll back the source business transaction; instead they are persisted and made visible to the owner, and they emit safe metrics and logs.

A transactional outbox, queue, or retry scheduler is not implemented for this phase. The transactional outbox remains a deliberate future reliability option, not current debt.

Real side-effect listeners must not be attached directly inside source command execution unless they are transactionally safe and explicitly reviewed. Audit appends qualify because they share the transaction; cache purge and external integrations do not and must run after commit.

## Consequences

Audit becomes a passive, same-transaction concern: when an important admin mutation commits, its audit record is already durable, with no separate delivery guarantee to build.

Cache purge and similar external effects cannot corrupt business state, but they are best-effort. Their failures must produce operational visibility (persisted attempts, failure counters, owner-facing operations view) rather than silent loss or business rollback.

Source application services gain a dependency on Audit application ports and on post-commit scheduling, but stay free of Audit and Platform Operations infrastructure. The app composition root continues to own provider adapters and wiring.

Future maintainers must remember: never attach external or failure-prone side effects to in-transaction command execution, and never persist secrets, tracking identifiers, or raw request data in audit or operations state. Adopting an outbox later is an additive reliability upgrade, not a correction of this decision.

## Alternatives considered

- Append audit records after commit like cache purge. Rejected: a post-commit failure could lose an audit record for a successful mutation, weakening the audit guarantee.
- Run cache purge inside the source transaction. Rejected: external infrastructure failure or latency would roll back or stall committed business changes.
- Build a transactional outbox, queue, or retry scheduler now. Rejected: it widens scope well beyond current needs; same-transaction audit plus post-commit fail-open cache purge is sufficient for this phase.
- Introduce a general event bus with real subscribers attached in-transaction. Rejected: it risks side effects firing before commit and couples contexts through listeners instead of explicit ports.

## Review triggers

- Audit append needs to span more than one transaction or context.
- Cache purge requires delivery guarantees, ordering, or retries beyond best-effort.
- A second failure-prone integration needs reliable post-commit delivery, justifying a transactional outbox.
- Operational side effects need to run for actions that are not wrapped in a source transaction.
