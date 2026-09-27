# ADR 0029: Execute Repository-Owned RC Deployment Orchestration Ephemerally over Authenticated SSH

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / operations / deployment / security |
| Supersedes | none |
| Superseded by | none |

## Context

[ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md) and [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md) make the qualified top-level OCI index digest the application deployment authority. [ADR 0025](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) selects a GitHub-hosted runner and authenticated SSH. [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md) assigns application deployment orchestration to Persefonia and production topology, state, and preflight to an external runtime. The earlier Deploy RC workflow qualified the artifact and SSH target but performed no deployment mutation.

## Decision

Deploy RC remains on a GitHub-hosted runner and consumes only the exact Delivery-qualified `ghcr.io/0xmillennium/persefonia@sha256:<64 lowercase hex>` OCI image-index reference. The workflow re-verifies the Delivery handoff, registry index, supported platforms, and signed provenance before entering the `rc` environment. Its checkout is pinned to the qualified source. It verifies the pinned ED25519 SSH host key and expected non-root principal, then preserves that verified host record for the deployment connection.

Persefonia owns the deployment orchestration script. The runner streams its repository-owned Bash payload over authenticated SSH to a remote shell process. No release-specific deployment executable is installed or synchronized on the host. The external runtime retains its production `docker-compose.yml`, `.env`, secrets, PostgreSQL and Redis lifecycle, durable state, networks, and `scripts/preflight.sh`. Persefonia neither copies nor synchronizes Website-Stack files.

The payload invokes the host-owned preflight with the exact `PERSEFONIA_IMAGE_REF` and absolute production environment path before its first deployment-side Docker invocation. A non-zero preflight result prohibits pull or update. The first payload Docker invocation after successful preflight explicitly pulls `app` only. All Compose calls use the same external descriptor, environment file, and exact image reference. The update is limited to `app` with `--no-deps`, `--no-build`, `--pull never`, and a bounded health wait. PostgreSQL and Redis are never requested for pull or update.

Runtime acceptance requires PostgreSQL and Redis container IDs to remain unchanged, a healthy app, an exact `Config.Image` reference, and equality between the running app image ID and the image ID resolved from the exact qualified reference. The remote payload emits a strict eight-record success protocol only after all checks pass. The runner validates that protocol before exposing workflow outputs. A bounded public HTTPS ingress smoke verifies the home page, robots document, and same-site admin OAuth redirect after runtime acceptance.

Runtime application secrets remain host-owned. Deployment has no blind automatic rollback. An application startup can run Flyway migrations, so a previous image is not presumed schema-compatible. Failures after mutation remain visible for diagnosis and explicit recovery decisions.

## Consequences

A normal qualified merge can deploy automatically without an operator SSH login or edits to Website-Stack. The repository can verify orchestration locally with synthetic runtime fixtures, but live host preflight and public ingress acceptance require the merge-triggered remote run. The external runtime interface is a dependency of this mechanism, not a claim that the live host was inspected.

## Alternatives considered

- Install a persistent host deployment script. Rejected because it creates another release-specific host lifecycle and version skew.
- Embed remote Bash in workflow YAML. Rejected because deployment policy would be harder to test and review.
- Synchronize Website-Stack from Persefonia on each release. Rejected because it transfers external runtime ownership to application delivery.
- Run a self-hosted GitHub runner on the production host. Rejected because Actions execution would gain proximity to runtime secrets and Docker authority.
- Deploy by mutable tag or rebuild on the host. Rejected because either disconnects deployment from the exact qualified OCI index.
- Roll back automatically. Rejected because Flyway migrations can make a prior application image schema-incompatible.

## Review triggers

- Deployment moves beyond one Compose node, or production topology becomes Persefonia-owned again.
- The external runtime interface, path, or service contract materially changes.
- The transport or runner trust boundary, artifact authority, or host preflight ownership changes.
- Deployment begins managing PostgreSQL or Redis lifecycle.
- A rollback procedure is designed with explicit migration and schema compatibility guarantees.
- An infrastructure delivery mechanism replaces the current external-runtime contract.
