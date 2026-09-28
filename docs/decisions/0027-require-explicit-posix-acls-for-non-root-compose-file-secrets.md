# ADR 0027: Require Explicit POSIX ACLs for Non-Root Compose File Secrets

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-27 |
| Scope | security / operations / testing |
| Supersedes | none |
| Superseded by | none |

Runtime-ownership refinement: [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md) moves implementation and preflight enforcement to the external production runtime. The exact POSIX ACL matrix, operator ownership, non-root access, and fail-closed enforcement remain required. References below to repository preflight describe the historical implementation, not current repository ownership.

## Context

Persefonia runs as UID 10001. The pinned PostgreSQL and Redis images use non-root runtime identities with UIDs 70 and 999 respectively. Production Compose secrets are backed by host files. During real-host qualification, nonempty secret files passed preflight but runtime reads failed with `Permission denied`.

PostgreSQL and Redis passwords are shared with the application, so assigning each file to a single service owner cannot satisfy all consumers. Docker Compose does not implement `uid`, `gid`, or `mode` remapping for file-backed secrets because their sources are bind-mounted. See the [Docker Compose secrets reference](https://docs.docker.com/reference/compose-file/services/#secrets).

## Decision

Keep secrets file-backed and containers non-root. Secrets must not become environment variables. The deployment operator, identified by `id -u` and `id -g`, owns the secret directory and all five files with the operator's primary group.

The `secrets/` directory must exist, be a directory rather than a symlink, and have mode 0700. Its numeric ACL must consist only of `user::rwx`, `group::---`, and `other::---`. Named users, named groups, default ACLs, and extended masks are forbidden on the directory.

Every secret must be a nonempty regular file, never a symlink. Each file has exactly the base ACL `user::rw-`, `group::---`, `mask::r--`, and `other::---`, plus the following read-only named-user entries:

| File | Required named user ACLs |
|---|---|
| `postgres_password` | `user:70:r--`, `user:10001:r--` |
| `redis_password` | `user:999:r--`, `user:10001:r--` |
| `contact_rate_limit_secret` | `user:10001:r--` |
| `oidc_client_secret` | `user:10001:r--` |
| `cloudflare_api_token` | `user:10001:r--` |

No additional named users, named groups, default ACLs, write permissions, or execute permissions are permitted. Group and world read access remain forbidden.

The `getfacl -cpn` numeric view is authoritative for access grants. A file's stat group bits can represent its extended ACL mask rather than a grant to the owning group, so file mode alone cannot validate this contract. Repository preflight requires `getfacl` and enforces the exact matrix before network inspection or Compose configuration validation. Missing tooling fails preflight without a permission fallback. The host-adapted preflight must mirror these semantics while preserving its host layout.

## Consequences

Preflight now rejects access configurations that cannot serve the intended non-root consumers or that expose secrets to other principals. Operators must provision exact ACLs and install ACL tooling on a filesystem supporting these semantics. The directory has no inherited grants for future files; each new file needs explicit provisioning.

ACL parsing is deterministic and does not use service-UID `test -r`: BusyBox produced a false negative during host qualification while actual kernel opens and reads succeeded. Actual container reads remain part of post-deployment host qualification. Preflight validates access metadata without printing secret values or changing permissions.

## Alternatives considered

- `chmod 0644` or `0604`: rejected because world-readable secrets violate the runtime trust boundary.
- Single-service `chown`: rejected because shared passwords require access by two distinct UIDs.
- Compose file-secret `uid`, `gid`, and `mode`: rejected because Docker Compose ignores this remapping for file sources.
- Environment-variable secrets: rejected because they abandon the file-backed secret boundary.
- Running containers as root: rejected because secret access must preserve non-root execution.

## Review triggers

- Application runtime UID changes.
- PostgreSQL image, digest, or runtime user changes.
- Redis image, digest, or runtime user changes.
- Adoption of rootless Docker or `userns-remap`.
- Filesystem ACL semantics change.
- Migration to Swarm or Kubernetes.
- Adoption of an external secrets manager.
