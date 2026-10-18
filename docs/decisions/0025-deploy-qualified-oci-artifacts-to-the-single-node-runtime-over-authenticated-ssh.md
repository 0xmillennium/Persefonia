# ADR 0025: Deploy Qualified OCI Artifacts to the Single-Node Runtime over Authenticated SSH

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-25 |
| Scope | architecture / operations / security |
| Supersedes | none |
| Superseded by | none |

Refined by [ADR 0026](0026-use-oidc-group-admission-with-refresh-backed-admin-session-revalidation.md) (OIDC group admission), [ADR 0030](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md) (host privilege gateway), and [ADR 0033](0033-keep-production-runtime-topology-external-and-use-compose-for-containerized-local-development.md) (external production topology).

## Context

[ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md) and [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md) establish build-once promotion of a multi-platform OCI image index. Deployment needs a transport and runtime trust boundary that promotes qualified digests without moving production secrets or Docker daemon authority into CI.

## Decision

Deployment runs on a GitHub-hosted runner over authenticated SSH to the single-node Compose runtime, consuming only an already-qualified `ghcr.io/0xmillennium/persefonia@sha256:<64-hex>` reference and never rebuilding code or accepting mutable tags.

Keeping the runner GitHub-hosted leaves production secrets and Docker daemon authority on the host. SSH must verify both client and host keys (`StrictHostKeyChecking=no` is forbidden), and GitHub Actions holds only minimum SSH transport credentials.

Application runtime secrets, durable PostgreSQL and Media state ([ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md)), and host runtime configuration remain host-owned. Mandatory host preflight must validate the exact digest reference and runtime environment before any container pull or update.

Environment or transport failures may be retried with the same qualified digest, while application defects require a new qualified digest. Because startup may run Flyway migrations, blind automatic rollback to a prior image is forbidden.

## Consequences

Deployment promotes exact qualified bytes without exposing production application secrets or host Docker authority to CI.

Transient environment failures can be retried with the same digest, while schema-affecting failures require explicit diagnosis rather than blind image rollback.

## Alternatives considered

- Run a self-hosted runner on the deployment host. Rejected because CI jobs would gain proximity to runtime secrets and Docker authority.
- Deploy by mutable tag or rebuild on the host. Rejected because it severs deployment from the qualified OCI digest.
- Bypass SSH host-key verification. Rejected because it allows target spoofing.
- Roll back automatically to the prior image on failure. Rejected because Flyway migrations can make the prior image schema-incompatible.

## Review triggers

- Deployment moves beyond a single-node Compose runtime or changes its SSH/runner trust boundary.
- A rollback procedure with explicit schema-compatibility guarantees is introduced.
