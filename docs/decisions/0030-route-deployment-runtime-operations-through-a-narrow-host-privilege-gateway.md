# ADR 0030: Route Deployment Runtime Operations Through a Narrow Host Privilege Gateway

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / operations / security |
| Supersedes | ADR 0029 |
| Superseded by | none |

## Context

The dedicated non-root SSH deployment principal intentionally lacks direct access to production runtime directories, `.env`, secrets, and the Docker socket. Granting Docker group membership, broad filesystem ACLs, or unrestricted `sudo` would violate least privilege. Repository-owned SSH orchestration needs a narrow privilege bridge to the externally owned runtime.

## Decision

Deploy RC runs on a GitHub-hosted runner over authenticated SSH with pinned host identity, streaming the repository-owned orchestration payload into a clean remote Bash environment for the exact Delivery-qualified `ghcr.io/0xmillennium/persefonia@sha256:<64-hex>` artifact.

An externally owned, root-protected host gateway at `/usr/local/libexec/persefonia-runtimectl` executes all privileged runtime operations via narrowly scoped non-interactive `sudo`. The deployment principal has no direct access to runtime directories, `.env`, secrets, or the Docker socket, and Persefonia remains agnostic of physical host paths and secret ACL mechanics.

The gateway exposes only semantic commands (`preflight`, `pull-app`, `up-app`, `service-id`, `app-health`, `app-config-image`, `app-image-id`, `qualified-image-id`) and rejects arbitrary commands, Docker flags, or filesystem paths.

Repository orchestration validates inputs, runs `preflight` before `pull-app`, snapshots PostgreSQL and Redis container IDs, runs `up-app`, and verifies unchanged dependency IDs, `app` health, exact configured image reference, and running image ID equality before emitting the eight-record completion protocol. Same-digest reruns may succeed without container recreation; blind automatic rollback remains forbidden.

## Consequences

The SSH deployment account is restricted to semantic gateway operations, and repository tests can verify orchestration order and failure handling against deterministic fakes without Docker or host access.

The external host runtime can evolve its physical layout independently as long as the `persefonia-runtimectl` command contract remains stable.

## Alternatives considered

- Log in over SSH directly as the runtime owner or add the deployment user to the `docker` group. Rejected because both grant broad host and secret authority.
- Broaden filesystem ACLs or grant generic `sudo` / `sudo docker`. Rejected because deployment only requires bounded application lifecycle operations.
- Install release-specific orchestration scripts on the host. Rejected to avoid host script version skew.

## Review triggers

- The `persefonia-runtimectl` subcommand contract or sudoers boundary changes.
- Deployment moves away from the single-node external Compose runtime.
- A schema-safe rollback procedure is introduced.
