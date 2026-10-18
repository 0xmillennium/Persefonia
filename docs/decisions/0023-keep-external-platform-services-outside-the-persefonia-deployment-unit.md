# ADR 0023: Keep External Platform Services Outside the Persefonia Deployment Unit

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-09-05 |
| Scope | architecture / operations / security |
| Supersedes | none |
| Superseded by | ADR 0028 |

Superseded by [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md) and [ADR 0033](0033-keep-production-runtime-topology-external-and-use-compose-for-containerized-local-development.md), which further move production PostgreSQL, Redis, and Compose topology outside this repository while preserving the external platform boundary recorded below.

## Context

Persefonia integrates with Cloudflare, Traefik, Authelia, and Postfix in production, but integrating with a platform service is distinct from owning its lifecycle. Without a clear ownership boundary, application deployment descriptors could expand into a general platform orchestrator.

## Decision

In the historical single-node deployment unit, Persefonia managed only the application, PostgreSQL, Redis, and durable Media mounts. Cloudflare, Traefik, Authelia, Postfix, host networking, and edge TLS remained externally provisioned and operated platform services.

External ownership does not make a service optional at runtime: Persefonia consumes narrow runtime contracts (reverse-proxy headers and routing from Traefik, OIDC endpoints from Authelia, SMTP submission from Postfix, and edge caching/purge APIs from Cloudflare) without provisioning those containers or storing their state in domain persistence.

PostgreSQL plus durable Media remains the recovery unit ([ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md)), while Redis ([ADR 0009](0009-keep-redis-auxiliary-only.md)) and external platform states remain outside durable recovery. Runtime secrets stay on the deployment host and never enter the OCI image, Git repository, or domain database.

## Consequences

Persefonia consumes narrow integration contracts with a smaller operational blast radius and cleaner secret separation.

Production deployment requires pre-existing external platform services verified by host preflight rather than self-contained provisioning.

## Alternatives considered

- Provision Traefik, Authelia, or Postfix inside Persefonia Compose. Rejected because Persefonia is an application, not a host platform orchestrator.
- Store external provider state in Persefonia domain persistence. Rejected because it violates bounded-context boundaries.
- Introduce Kubernetes to manage service ownership. Rejected as unnecessary operational complexity.

## Review triggers

- Persefonia begins provisioning its own OIDC, reverse-proxy, or SMTP infrastructure.
- PostgreSQL or Redis ownership moves to external runtime or managed services (triggered in ADR 0028).
- Deployment moves to a multi-node architecture.
