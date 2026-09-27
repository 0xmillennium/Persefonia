# ADR 0028: Separate Application Delivery from Host Runtime Topology

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / operations / deployment / repository ownership |
| Supersedes | ADR 0023 |
| Superseded by | none |

## Context

Persefonia delivery qualifies an immutable application OCI artifact under [ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md) and [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md). The production runtime has a separately owned repository and delivery lifecycle. Keeping production Compose, dependency-server configuration, and host preflight in Persefonia creates competing authorities and couples application delivery to infrastructure changes.

[ADR 0023](0023-keep-external-platform-services-outside-the-persefonia-deployment-unit.md) kept edge, identity, and mail platform services external but included PostgreSQL and Redis lifecycle in Persefonia's deployment unit. The boundary must now distinguish application contracts from the host runtime that supplies them. This ownership change does not remove dependencies or change application feature behavior.

## Decision

The release/deployment unit owned by this repository is the Persefonia application OCI artifact. Persefonia owns application source, application configuration, the application image contract, the application-only Compose contract, application runtime dependency contracts, CI / delivery / deployment orchestration, and immutable artifact identity rules.

The production host runtime owns, outside this repository, production Compose topology, PostgreSQL service lifecycle and server configuration, Redis service lifecycle and server configuration/startup, production environment configuration, runtime secrets, durable PostgreSQL state, durable Media state, host runtime networks, host preflight enforcement, and host service lifecycle. Production runtime source is version-controlled and delivered independently. Future runtime configuration changes belong to that external repository/lifecycle; they must not be synchronized manually from Persefonia or copied on every application deployment.

PostgreSQL remains the required durable relational datastore. Redis remains a required application dependency with auxiliary, reconstructable state under [ADR 0009](0009-keep-redis-auxiliary-only.md). External lifecycle ownership makes neither dependency optional and does not add Redis to readiness. PostgreSQL plus durable Media remains the coordinated recovery unit under [ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md), with compatible application/schema state required for recovery. Storage ownership does not change recovery correctness or Flyway ownership and startup semantics.

[ADR 0027](0027-require-explicit-posix-acls-for-non-root-compose-file-secrets.md) remains an accepted host-runtime security requirement. Its exact POSIX ACL and non-root secret-access semantics remain in force; implementation and preflight enforcement now belong to the external production runtime. This repository documents application-consumed credentials through fake secret examples and keeps real credentials and sensitive environment files ignored.

`compose.yaml` is an application-container/local contract, not a standalone infrastructure stack or production topology authority. Its only service is `app`. It retains the supplied application image, non-root execution, container hardening, file-backed application secret targets, durable Media bind, readiness healthcheck, and loopback-only application port. It does not own dependency startup, database volumes, custom runtime networks, or production routing/integrations. This repository must not carry a competing production Compose descriptor or copies of production PostgreSQL/Redis server configuration.

PostgreSQL and Redis server configuration must not be embedded into the Persefonia application image. The OCI image remains the Java application image and its build context remains restricted to application-image inputs.

Application dependency endpoints must be configurable. The Docker profile consumes `PERSEFONIA_POSTGRES_HOST` / `PERSEFONIA_POSTGRES_PORT` and `PERSEFONIA_REDIS_HOST` / `PERSEFONIA_REDIS_PORT`. Defaults `postgres:5432` and `redis:6379` preserve the accepted external runtime's application-facing contract. Database name/username, Redis client username/key prefix, and existing mounted-secret property names remain unchanged. These inputs describe consumed endpoints, not dependency-container published ports.

[ADR 0025](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) remains accepted for exact OCI digest authority, the GitHub-hosted runner boundary, authenticated SSH, mandatory SSH host identity verification, host-owned application secrets, and no blind automatic rollback. Its historical repository-owned production Compose/preflight/topology portions are superseded by this boundary. [ADR 0026](0026-use-oidc-group-admission-with-refresh-backed-admin-session-revalidation.md) retains its OIDC admission/revalidation semantics; its historical tracked-runtime-source paragraph is superseded.

Later D5 deployment automation may invoke the externally owned runtime over authenticated SSH using the qualified immutable artifact. This decision does not implement that mutation, install host helpers, synchronize host files, or change the current Deploy RC workflow. The external runtime is an already-provisioned contract; repository checks cannot establish its live state.

## Consequences

Application delivery and host runtime topology each have one source of authority and independent review/delivery lifecycles. Application deployment can consume existing runtime contracts without rebuilding or reconfiguring PostgreSQL/Redis servers. CI verifies the application Compose descriptor with synthetic inputs and architecture tests enforce the ownership boundary without host access.

The application-only Compose descriptor requires separately provisioned, reachable PostgreSQL and Redis plus a pre-existing Media directory and appropriate secret files. It is no longer a complete standalone stack. Production connectivity, network attachment, secret ACLs, server hardening, storage provisioning, and host preflight remain external runtime responsibilities. Preserving endpoint defaults is a repository-level compatibility guarantee, not live production validation.

## Alternatives considered

- Retain duplicate production Compose/configuration in both repositories. Rejected because competing authorities drift and make ownership and delivery ordering ambiguous.
- Put PostgreSQL/Redis configuration into the Persefonia application image. Rejected because a Java application artifact must not become the delivery vehicle for independently operated server configuration.
- Build custom PostgreSQL/Redis images as part of Persefonia delivery. Rejected because the current architecture has no application-owned server-image requirement and this would couple independent service lifecycles to application releases.
- Synchronize production runtime files from Persefonia on every application deployment. Rejected because application promotion would silently deliver infrastructure changes and bypass the external runtime's version control and review lifecycle.
- Keep application and infrastructure lifecycle ownership mixed in one repository. Rejected because the accepted runtime is already externally owned and application delivery needs a focused immutable-artifact boundary.

## Review triggers

- Production topology becomes owned by Persefonia again.
- PostgreSQL or Redis moves to a managed service in a way that changes application contracts.
- Application deployment begins managing infrastructure lifecycle.
- Multi-node orchestration is introduced.
- Custom PostgreSQL/Redis images become necessary.
- Recovery-unit semantics change.
