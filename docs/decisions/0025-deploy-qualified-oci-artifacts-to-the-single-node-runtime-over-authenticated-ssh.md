# ADR 0025: Deploy Qualified OCI Artifacts to the Single-Node Runtime over Authenticated SSH

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-25 |
| Scope | architecture / operations / deployment / security |
| Supersedes | none |
| Superseded by | none |

Identity-admission refinement: [ADR 0026](0026-use-oidc-group-admission-with-refresh-backed-admin-session-revalidation.md) replaces the historical OWNER identity allowlist configuration with required OIDC group admission and refresh-backed session revalidation. Deployment and secret ownership remain unchanged.

## Context

[ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md) establishes build-once promotion of a qualified OCI artifact. [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md) makes its top-level image-index digest authoritative for both supported platforms. Deployment needs a transport and runtime ownership boundary that preserves those decisions without moving application secrets or Docker authority into CI.

## Decision

Deployment automation uses a GitHub-hosted GitHub Actions runner, authenticated SSH, and a single-node Docker Compose runtime. The deployment mechanism consumes an already-qualified OCI artifact. It does not compile application code, run Gradle or frontend builds, build an image, or otherwise recreate application bytes.

The full Git commit SHA identifies source. The exact top-level OCI image-index digest identifies the deployable artifact. The runtime consumes a reference of the form `ghcr.io/0xmillennium/persefonia@sha256:<top-level-index-digest>`. Mutable tags may aid discovery, but they are not deployment authority. Qualification remains bound to the digest under ADRs 0022 and 0024.

Runtime artifact identity is fixed to the exact repository `ghcr.io/0xmillennium/persefonia` and a SHA-256 digest containing exactly 64 lowercase hexadecimal characters. A digest-pinned image from another repository is not a Persefonia deployment artifact. Tags, including tag-plus-digest references, are rejected by preflight.

The runner remains GitHub-hosted so application and runtime secrets and Docker daemon authority stay on the deployment host instead of moving GitHub Actions execution into that host. SSH must authenticate the deployment host as well as the client. Host identity verification is mandatory; `StrictHostKeyChecking=no` or an equivalent bypass is not acceptable. The host-key verification mechanism belongs to deployment automation.

Application runtime secrets remain deployment-host-owned, including PostgreSQL and Redis credentials, the OIDC client secret, Cloudflare API token, SMTP credentials, contact and rate-limit secrets, OWNER allowlisted identities, and other application-level production secrets. GitHub deployment automation may hold only the minimum transport information needed to reach and authenticate to the deployment host; it is not the canonical store for application runtime secrets.

The repository owns Compose descriptors, the application runtime contract, deployment logic, and qualified OCI artifact identity rules. The deployment host owns environment-specific runtime configuration, application secrets, Docker runtime state, durable Media, database volumes and state, and host-level deployment state. Cloudflare, Traefik, Authelia, and Postfix remain externally owned under [ADR 0023](0023-keep-external-platform-services-outside-the-persefonia-deployment-unit.md); deployment automation does not provision or manage them. PostgreSQL and Media remain durable runtime state under [ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md), while Redis remains auxiliary under [ADR 0009](0009-keep-redis-auxiliary-only.md).

The current tracked RC workflow resolves and qualifies the immutable artifact and verifies SSH target trust. Its remote probe checks only the authenticated principal. Neither that workflow nor any tracked `scripts/deploy/` entrypoint performs target-host Compose runtime mutation. The repository therefore cannot enforce invocation of an externally owned host deployment entrypoint.

The host deployment entrypoint must resolve the exact immutable `PERSEFONIA_IMAGE_REF`, run the host-adapted preflight immediately before the first Compose pull/up/create/run/restart or recreate operation, and abort mutation on any non-zero preflight result. Preflight and mutation must use the same exact image reference, host deployment environment, and Compose descriptor. Successful artifact or SSH qualification alone does not authorize bypassing this gate. Confirming this ordering in the actual host entrypoint remains a host qualification condition.

Repository `scripts/deploy/preflight.sh` defines the preflight semantics using `compose.production.yaml`, `.env.production`, `docker/postgresql/`, `docker/redis/`, and `docker/redis-start.sh`. The external Homie-Lab Website-Stack layout instead uses `docker-compose.yml`, `.env`, `postgresql/`, `redis/`, `scripts/preflight.sh`, and `scripts/redis-start.sh`. Host adaptation must preserve the same checks despite these path differences; the repository script is not an executable substitute for that external layout. Both preflights must require Docker Compose 2.33.1 or newer and reject inherited `DOCKER_HOST`, `DOCKER_CONTEXT`, `DOCKER_TLS_VERIFY`, `DOCKER_CERT_PATH`, `COMPOSE_PROJECT_NAME`, and `COMPOSE_FILE`, including empty-but-defined values, before the first Docker invocation. The configured env file is validated separately. Unexpected inherited values must cause an explicit failure rather than being silently unset, so Docker daemon, context, and Compose project/file identity cannot be redirected.

For an environment or transport failure, fix the environment and retry the same qualified digest. Examples include an unreachable host, incorrect runtime configuration, a missing host-side secret, an incorrect external network, or an external dependency outage. For an application or artifact defect, fix source, run CI, create a new qualified artifact, and deploy its new digest. Qualification does not transfer between digests.

Deployment does not assume blind automatic rollback. Application startup may run Flyway migrations, so a previous image is not automatically schema-compatible after a newer application has started. A deployment failure requires diagnosis before selecting a recovery action. An explicitly designed rollback procedure may be added separately.

## Consequences

Deployment can promote qualified bytes without giving CI ownership of application secrets or production Docker state. The host must provide its runtime configuration, secrets, and external dependencies, and deployment automation must verify SSH host identity.

Environment failures can be retried with the same artifact. Artifact defects require a new qualified digest. Recovery cannot assume that reversing only the application image restores a compatible runtime.

## Alternatives considered

- Run a self-hosted Actions runner on the deployment host. Rejected because Actions execution would gain proximity to application secrets and Docker daemon authority.
- Deploy by mutable image tag or rebuild on the host. Rejected because either would sever deployment from the exact qualified OCI artifact.
- Bypass SSH host authentication. Rejected because transport encryption without host identity does not establish the intended deployment host.
- Roll back automatically to the previous image after any failure. Rejected because Flyway migrations may make that image incompatible with the current schema.

## Review triggers

- The deployment topology moves beyond a single-node Compose runtime.
- The transport or runner trust boundary changes.
- Artifact identity or qualification semantics change.
- Runtime secret or external platform ownership changes.
- A rollback procedure is designed with explicit schema-compatibility guarantees.
