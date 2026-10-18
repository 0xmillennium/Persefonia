# ADR 0027: Require Explicit POSIX ACLs for Non-Root Compose File Secrets

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-27 |
| Scope | security / operations / testing |
| Supersedes | none |
| Superseded by | none |

Refined by [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md) and [ADR 0033](0033-keep-production-runtime-topology-external-and-use-compose-for-containerized-local-development.md), which move production preflight enforcement to the external host runtime while preserving the POSIX ACL contract below.

## Context

Persefonia runs as UID `10001`, while pinned PostgreSQL and Redis containers run as non-root UIDs `70` and `999`. Because Docker Compose bind-mounts file-backed secrets without `uid`/`gid`/`mode` remapping and database/Redis passwords are shared across two distinct container UIDs, standard single-owner POSIX mode bits cannot grant least-privilege access to both consumers without exposing secrets to other principals.

## Decision

Production secrets remain file-backed and containers remain non-root; secrets must never be passed as ordinary environment variables. The deployment operator (`id -u` / `id -g`) owns the `secrets/` directory (`0700`, numeric ACL `user::rwx`, `group::---`, `other::---`, with no named entries or default ACLs) and all five nonempty regular secret files.

Each secret file must have base ACL `user::rw-`, `group::---`, `mask::r--`, `other::---`, plus only the following read-only named-user grants:

| File | Required named user ACLs |
|---|---|
| `postgres_password` | `user:70:r--`, `user:10001:r--` |
| `redis_password` | `user:999:r--`, `user:10001:r--` |
| `contact_rate_limit_secret` | `user:10001:r--` |
| `oidc_client_secret` | `user:10001:r--` |
| `cloudflare_api_token` | `user:10001:r--` |

Symlinks, extra named principals, default ACLs, and write/execute grants are forbidden. Host preflight requires `getfacl` and validates the exact numeric `getfacl -cpn` output before any Docker or Compose invocation, failing closed if tooling is missing or any grant diverges.

## Consequences

Host preflight rejects secret permissions that would either fail non-root container reads or over-expose credentials.

Operators must provision explicit POSIX ACLs on an ACL-capable filesystem for every secret file rather than relying on directory inheritance or `chmod` alone.

## Alternatives considered

- `chmod 0644` or `0604`. Rejected because world-readable secrets violate the host trust boundary.
- Single-service `chown`. Rejected because PostgreSQL and Redis passwords must be read by both the service UID (`70` / `999`) and the application UID (`10001`).
- Compose file-secret `uid`/`gid`/`mode` fields. Rejected because Docker Compose ignores remapping for bind-mounted file secrets.
- Environment-variable secrets or root containers. Rejected because they weaken secret isolation and non-root execution.

## Review triggers

- Application (`10001`), PostgreSQL (`70`), or Redis (`999`) runtime UIDs change.
- Rootless Docker, `userns-remap`, Swarm, Kubernetes, or an external secrets manager is adopted.
