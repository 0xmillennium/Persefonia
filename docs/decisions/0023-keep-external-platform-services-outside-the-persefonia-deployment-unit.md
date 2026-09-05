# ADR 0023: Keep External Platform Services Outside the Persefonia Deployment Unit

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-05 |
| Scope | architecture / operations / deployment / security |
| Supersedes | none |
| Superseded by | none |

## Context

The locked technology and deployment topology selects Cloudflare, Traefik, Authelia, and Postfix, but selection of a service is distinct from Persefonia owning its lifecycle. The current real deployment model treats them as externally operated platform services. Without a durable ownership boundary, a future maintainer could incorrectly expand Persefonia Compose into a general platform orchestrator.

## Decision

The current Persefonia-managed deployment unit consists of the Persefonia application, PostgreSQL runtime/data, Redis runtime, durable Media storage mount, and Persefonia deployment descriptors/configuration. This is the current single-node MVP ownership boundary; it does not require every component to remain self-hosted in Compose forever.

Cloudflare, Traefik, Authelia, Postfix or another SMTP relay, the host firewall/network, and TLS/edge platform infrastructure are external to Persefonia's deployment lifecycle. Persefonia integrates with these services, but does not provision or operate their complete lifecycle.

External ownership does not mean optional at runtime. Production admin authentication may require externally owned Authelia; configured Contact mail delivery may require an externally owned Postfix relay; and production ingress may require externally owned Traefik. Ownership and runtime requirement are separate concerns.

Persefonia depends only on narrow configuration and runtime contracts: Traefik provides reverse-proxy connectivity, a trusted-proxy boundary, forwarded HTTPS metadata, and routing to the application; Authelia provides OIDC issuer/discovery, client identity and secret, and authorization, token, and JWK endpoints; Postfix provides an SMTP endpoint with TLS/authentication settings where required; and Cloudflare provides edge routing/cache behavior and cache-purge credentials when enabled. Provider runtime state is not Persefonia domain state.

Persefonia production Compose must not provision Cloudflare, Traefik, Authelia, or Postfix. Future production Compose may join an existing external Traefik network, configure external endpoint addresses, and consume environment or secret configuration, but it must not define those platform services as Persefonia-owned containers. This decision does not reverse their selection in the locked technology/deployment model; it clarifies lifecycle ownership and provisioning responsibility only.

As required by [ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md), PostgreSQL plus durable Media is the Persefonia recovery unit. Redis rate-limit state, session state, application cache, CDN cache, Traefik runtime state, Authelia session state, and Postfix runtime queue/state are not part of the durable recovery set. Per [ADR 0009](0009-keep-redis-auxiliary-only.md), Redis remains auxiliary: it may be inside the Persefonia-managed runtime deployment unit while remaining outside durable recovery correctness. Ownership and durability are separate dimensions. Operational recovery retains the boundary established by [ADR 0021](0021-keep-operational-recovery-commands-outside-a-single-use-case-transaction.md).

Runtime secrets must not be stored in the OCI image, Git repository, or domain database. External-platform credentials are environment and deployment concerns. The exact mounted-secret implementation belongs to D2. Deployment connectivity credentials and runtime application credentials are distinct concerns; runtime application secrets remain on the deployment environment or host.

## Consequences

Deployment ownership is clear, with a smaller blast radius, cleaner repository responsibility, provider lifecycle independence, better secret separation, and a simpler Compose topology. Persefonia consumes focused platform contracts without coupling application or domain architecture to Traefik internals, Authelia internals, Postfix administration, or edge-provider lifecycle state.

The production environment has external prerequisites, and deployment preflight must verify them. A local environment may not fully reproduce every platform component. Those prerequisites are an explicit operational boundary, not an architectural failure.

## Alternatives considered

- Provision all selected platform services in Persefonia Compose. Rejected because Persefonia would become a platform orchestrator.
- Put external-provider configuration or state into Persefonia domain persistence. Rejected because it violates bounded-context and dependency-inversion boundaries.
- Remove Authelia, Traefik, Postfix, or Cloudflare from the architecture. Rejected because their selection remains part of the locked technology and deployment model.
- Introduce Kubernetes or another orchestration platform to solve ownership. Rejected as unnecessary and out of scope.

## Review triggers

- Persefonia begins provisioning its OIDC provider.
- Reverse-proxy lifecycle moves inside Persefonia.
- SMTP relay lifecycle becomes Persefonia-managed.
- PostgreSQL or Redis ownership moves to managed services in a way that materially changes the deployment unit.
- Deployment moves to a multi-host or multi-node architecture.
- Platform ownership boundaries are redesigned.
