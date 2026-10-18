# ADR 0016: Use Post-Commit Operational Side Effects and Same-Transaction Audit Appends

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-26 |
| Scope | architecture / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Important admin mutations produce two distinct operational side effects with different reliability requirements. Mandatory audit records must exist if and only if the business mutation commits, whereas external side effects such as cache purge can fail or stall and must never roll back a committed business mutation.

## Decision

Mandatory audit records for important admin mutations are appended in the same application transaction as the source mutation through Audit application ports. Source application services may depend on Audit application ports, never on Audit repositories or infrastructure.

Failure-prone external side effects—including cache purge, mail notification, and aggregate Insights observations—are scheduled through `PostCommitTaskExecutor` and run only after the source transaction commits. Their failures never roll back the business transaction; instead, failures are persisted for operational visibility and emit safe metrics and logs.

A transactional outbox, durable queue, or automatic retry scheduler is not required for this boundary and remains an optional future upgrade. Real side-effect listeners must not execute inside source command transactions unless they are transactionally safe same-database appends like Audit.

## Consequences

Audit records commit atomically with their source mutations without requiring a separate delivery mechanism.

External side effects cannot corrupt or roll back business state, though post-commit execution is best-effort and relies on persisted failure state and operator visibility.

Future maintainers must keep external integrations out of in-transaction command execution and never persist secrets, tracking identifiers, or raw request payloads in audit or operational records.

## Alternatives considered

- Append audit records after commit. Rejected because a post-commit failure could lose the audit record of a committed mutation.
- Execute cache purge inside the source transaction. Rejected because external latency or failure would stall or roll back committed business state.
- Build a transactional outbox or retry queue immediately. Rejected as unnecessary complexity when same-transaction audit and visible post-commit purge satisfy current requirements.
- Use an in-transaction event bus with arbitrary subscribers. Rejected because listeners could trigger external side effects before commit.

## Review triggers

- Audit appends need to span multiple transactions or datastores.
- Cache purge requires delivery guarantees or automatic retries beyond best-effort.
- Multiple external integrations require durable post-commit delivery, justifying a transactional outbox.
- Operational side effects must run for actions outside a database transaction.
