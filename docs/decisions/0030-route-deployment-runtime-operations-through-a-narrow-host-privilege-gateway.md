# ADR 0030: Route Deployment Runtime Operations Through a Narrow Host Privilege Gateway

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / operations / deployment / security |
| Supersedes | ADR 0029 |
| Superseded by | none |

## Context

The dedicated non-root SSH deployment principal intentionally lacks access to production runtime directories, the runtime `.env`, runtime secrets, and the Docker socket. Deriving the production runtime location from that principal's `$HOME` was incorrect. Granting Docker group membership, broad filesystem traversal, generic sudo, or secret access would expand its authority beyond deployment needs. Repository-owned deployment orchestration needs a narrow privileged bridge to the externally owned single-node Compose runtime.

## Decision

Deploy RC remains on a GitHub-hosted runner and uses the dedicated non-root SSH principal with pinned host identity. The runner streams the repository-owned orchestration payload into a clean, non-profile remote Bash environment. The exact Delivery-qualified `ghcr.io/0xmillennium/persefonia@sha256:<64 lowercase hex>` reference remains the sole artifact authority.

One externally owned, root-protected host gateway at `/usr/local/libexec/persefonia-runtimectl` performs privileged runtime operations. The deployment account invokes only this gateway through narrowly scoped non-interactive sudo. It needs no direct access to production runtime directories, `.env`, secrets, or Docker socket, and receives no generic sudo authority. The gateway implementation and sudoers policy remain external-runtime concerns. Persefonia does not know the runtime operator, physical production paths, Docker authorization details, or secret ownership and ACL details.

The gateway interface is limited to `preflight`, `pull-app`, `up-app`, `service-id`, `app-health`, `app-config-image`, `app-image-id`, and `qualified-image-id`. It provides semantic operations; it does not accept arbitrary privileged commands, Docker arguments, or filesystem paths from the repository. The external runtime owns Compose details, host preflight, and the bounded application update behavior.

Repository orchestration validates the source SHA and exact image reference, invokes preflight immediately before pull, snapshots PostgreSQL and Redis container identities after pull, requests the application-only update, and verifies those dependency identities remain unchanged. It then requires a healthy resulting app, its configured exact reference to equal the qualified reference, and its running image ID to equal the ID resolved from that exact reference. App container recreation is not required: a same-digest rerun may succeed with the same container ID. Only complete success emits the existing eight-record protocol. Failures remain visible, with no blind automatic rollback.

## Consequences

The deployment account can request only the runtime actions exposed by the external gateway. Repository tests can verify operation order, output validation, and failure handling with deterministic fakes without reading production files or calling Docker. The host gateway and its authorization policy must remain compatible with this interface; local tests cannot establish live host behavior. A merge-triggered CI → Delivery → Deploy RC run remains necessary for remote acceptance.

The gateway's physical runtime implementation can evolve independently when its semantic API remains stable. A failed update may require explicit diagnosis and recovery, especially when application startup can run Flyway migrations.

## Alternatives considered

- Use the privileged runtime owner as the SSH deployment principal. Rejected because remote login would gain the owner's broad runtime authority.
- Add the deployment account to the Docker group. Rejected because Docker API access grants broad host control.
- Broaden filesystem ACLs for runtime traversal and reads. Rejected because deployment does not need direct runtime or secret access.
- Grant generic sudo. Rejected because it permits unrelated privileged commands.
- Allow `sudo docker`. Rejected because arbitrary Docker arguments exceed the narrow operation contract.
- Install release-specific deployment orchestration on the host. Rejected because it adds a second application-release lifecycle and version skew.
- Copy Website-Stack or runtime configuration from Persefonia. Rejected because it creates competing production-runtime authority.
- Run a self-hosted Actions runner on the production host. Rejected because workflow execution would gain proximity to runtime secrets and Docker authority.

## Review triggers

- The runtime gateway API materially changes.
- Runtime topology moves into Persefonia ownership.
- Deployment moves away from the single-node external Compose runtime.
- The SSH or runner trust boundary changes.
- The deployment principal receives direct Docker or runtime access.
- Host privilege-management strategy changes.
- Rollback is formally redesigned with schema and migration guarantees.
- Artifact authority changes.
