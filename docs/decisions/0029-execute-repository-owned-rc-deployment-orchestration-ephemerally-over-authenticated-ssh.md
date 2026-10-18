# ADR 0029: Execute Repository-Owned RC Deployment Orchestration Ephemerally over Authenticated SSH

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-09-28 |
| Scope | architecture / operations / security |
| Supersedes | none |
| Superseded by | ADR 0030 |

Superseded by [ADR 0030](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md), which retains ephemeral SSH orchestration while routing host runtime operations through a narrow privilege gateway instead of direct Compose execution.

## Context

Under [ADR 0025](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) and [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md), Persefonia owns RC deployment orchestration from a GitHub-hosted runner over authenticated SSH, while the production host owns runtime topology and preflight. Deployment needed an executable orchestration mechanism without installing release-specific scripts on the host.

## Decision

Deploy RC runs on a GitHub-hosted runner, re-verifies the Delivery-qualified `ghcr.io/0xmillennium/persefonia@sha256:<64-hex>` image index and signed provenance, authenticates the pinned ED25519 SSH host key, and streams a repository-owned Bash orchestration payload to a remote shell without installing persistent scripts on the host.

In the historical mechanism recorded by this ADR, the streamed payload invoked the host-owned preflight directly and updated only the `app` service while preserving running PostgreSQL and Redis container identities.

Runtime acceptance required unchanged PostgreSQL/Redis container IDs, a healthy `app` container running the exact qualified image ID, a strict eight-record output protocol, and post-deploy public HTTPS smoke checks, with no blind automatic rollback.

## Consequences

Qualified merges could deploy automatically without manual SSH logins or host-installed release scripts, while synthetic fixtures verified the streamed payload contract in CI.

## Alternatives considered

- Install a persistent deployment script on the host. Rejected because it creates a second release lifecycle and version skew.
- Embed remote shell logic inline in GitHub Actions YAML. Rejected because it is harder to test deterministically.
- Synchronize host runtime files from Persefonia. Rejected because production topology is externally owned ([ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md)).

## Review triggers

- The deployment principal no longer has direct access to host runtime paths or Docker (triggered in ADR 0030).
- Deployment moves beyond a single-node Compose runtime.
- A schema-safe rollback procedure is introduced.
