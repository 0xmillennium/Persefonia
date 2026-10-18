# ADR 0028: Separate Application Delivery from Host Runtime Topology

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-09-28 |
| Scope | architecture / operations |
| Supersedes | ADR 0023 |
| Superseded by | ADR 0033 |

Superseded by [ADR 0033](0033-keep-production-runtime-topology-external-and-use-compose-for-containerized-local-development.md), which retains external production runtime ownership while repurposing root `compose.yaml` for containerized local development.

## Context

Persefonia delivers a qualified application OCI artifact ([ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md), [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md)), whereas the production host runtime is version-controlled and operated in a separate repository. Keeping production Compose topology, PostgreSQL/Redis server configuration, and host preflight inside Persefonia created competing authorities.

## Decision

The deployment unit owned by this repository is the Persefonia application OCI artifact, its runtime configuration contracts (`PERSEFONIA_POSTGRES_HOST`/`PORT`, `PERSEFONIA_REDIS_HOST`/`PORT`, defaulting to `postgres:5432` and `redis:6379`), and CI/CD delivery orchestration.

Production Compose topology, PostgreSQL and Redis container lifecycles and server configurations, host networks, durable PostgreSQL and Media storage ([ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md)), secret files and POSIX ACL enforcement ([ADR 0027](0027-require-explicit-posix-acls-for-non-root-compose-file-secrets.md)), and host preflight belong to the external production runtime repository. They must never be embedded in the application OCI image or synchronized from Persefonia during deployment.

PostgreSQL remains the required durable database and Redis remains a required auxiliary dependency ([ADR 0009](0009-keep-redis-auxiliary-only.md)). In the historical root `compose.yaml` contract established by this ADR (later superseded by ADR 0033), `compose.yaml` defined only the `app` container contract rather than a production stack.

## Consequences

Application delivery and host infrastructure topology each have a single authoritative repository and independent release lifecycles.

Application deployments update the `app` service against pre-existing PostgreSQL, Redis, Media, and secret contracts without reconfiguring host infrastructure.

## Alternatives considered

- Retain duplicate production Compose and server configs in both repositories. Rejected because competing authorities drift.
- Embed PostgreSQL/Redis server configuration or custom images in Persefonia delivery. Rejected because database and cache server lifecycles are independent of the Java application.
- Synchronize host runtime files from Persefonia on each deployment. Rejected because application promotion must not silently mutate host infrastructure.

## Review triggers

- Production topology ownership returns to Persefonia.
- PostgreSQL or Redis moves to a managed service that alters application endpoint contracts.
- Multi-node orchestration replaces the single-node external Compose runtime.
