# ADR 0017: Use Explicit Transactional Application Gateways for Admin Mutations

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-02 |
| Scope | architecture / transactions / security |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia requires mandatory Audit records for important admin mutations to commit atomically with their source changes. Some existing mutation paths expose concrete application services directly to web controllers or rely on transactions scoped only to repository work. Those shapes cannot coordinate a complete use case that will later include both source mutation and Audit append.

Admin route protection is also only an early guard. Application commands can be invoked outside an HTTP controller, so source application services must remain the authoritative mutation-authorization boundary. Expected validation and business rejection must stay distinct from unexpected persistence, transaction, or Audit failures so transaction rollback remains reliable.

This decision builds on, and does not supersede, [ADR 0004](0004-use-modular-monolith-with-app-composition-root.md), [ADR 0005](0005-keep-domain-modules-framework-free.md), [ADR 0008](0008-require-owner-authorization-for-admin-mutations.md), [ADR 0010](0010-use-spring-data-jdbc-through-persistence-adapters.md), and [ADR 0016](0016-use-post-commit-operational-side-effects.md).

## Decision

Every state-changing admin use case is exposed through a narrow, framework-free command gateway interface owned by its source application module. The `:app` composition root implements each gateway with a Spring `@Transactional` adapter using the default `REQUIRED` propagation. Web controllers depend on these gateway interfaces rather than concrete mutation services.

The source application service retains command authorization and domain mutation responsibilities. Admin route protection remains defense in depth and does not replace application-layer OWNER authorization.

Repository-local `TransactionTemplate` usage is an aggregate-persistence fallback, not the complete use-case transaction boundary. Mandatory future Audit append is coordinated inside the same transactional application gateway as its source mutation. `REQUIRES_NEW` is forbidden for both the source mutation and mandatory same-transaction Audit append.

Expected validation and business rejection are represented by typed results. Unexpected runtime, persistence, transaction, or Audit failures propagate and must not be converted into successful or expected business results.

## Consequences

Admin mutations gain an explicit transaction boundary in `:app` while source application and domain modules stay framework-free. Controllers have a stable, narrow dependency that can later coordinate same-transaction Audit without acquiring persistence or transaction responsibilities.

Authorization remains testable independently of HTTP routing, and unauthorized callers cannot reach mutation ports. Infrastructure failures remain visible to Spring transaction management and trigger rollback.

Repository adapters may retain local transaction fallbacks for aggregate consistency, but those transactions join an existing application transaction and do not define the complete use case.

## Alternatives considered

- Put `@Transactional` on web controllers. Rejected because transaction ownership belongs to the application composition boundary and controllers must remain transport adapters.
- Treat repository-local transactions as the use-case boundary. Rejected because they cannot coordinate source mutation with mandatory Audit work.
- Convert unexpected exceptions into UI business results. Rejected because this hides infrastructure failures and may allow partial commits.
- Use `REQUIRES_NEW` for Audit append. Rejected because Audit and source state could commit independently.
- Rely only on route protection for authorization. Rejected because application commands can be invoked outside the controller.

## Review triggers

- Admin mutations need coordination across more than one local database transaction.
- Mandatory Audit delivery semantics change from the same-transaction model in ADR 0016.
- A command cannot be represented through a narrow source-owned gateway without cross-context repository access.
- The OWNER-only mutation authorization model changes.
