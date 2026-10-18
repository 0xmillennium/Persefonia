# ADR 0033: Keep Production Runtime Topology External and Use Compose for Containerized Local Development

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-01 |
| Scope | architecture / operations |
| Supersedes | ADR 0028 |
| Superseded by | none |

## Context

The previous application-only root `compose.yaml` ([ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md)) could not run a source checkout without separately provisioned PostgreSQL, Redis, build toolchains, and secret files. Contributors need a self-contained local container runtime while production runtime topology remains externally owned.

## Decision

Persefonia owns application source, the production OCI recipe (`Dockerfile` and `supported-platforms.txt`), CI/CD qualification, and a containerized local development stack. Production Compose topology, production PostgreSQL/Redis lifecycles, ingress, OIDC, SMTP, and host secrets remain externally owned.

The tracked root `compose.yaml` is exclusively for local development and runs `app`, `postgres`, and `redis`. `app` uses a digest-pinned JDK 25 + Node 22 toolchain image, bind-mounts the checkout, and runs `:app:bootRun` without building or pulling a release image. `postgres` (named volume) and `redis` (disposable under [ADR 0009](0009-keep-redis-auxiliary-only.md)) use the digest-pinned CI images.

Tracked `.env.example` references fake `secrets/*.examples` files so copying it to ignored `.env` enables `docker compose up`. Credentials enter containers via file-backed Compose secrets (`/run/secrets/`), never plain environment variables. Local Redis resets `default` to `PING` only for healthchecks and restricts the application user to rate-limit commands under its key prefix.

An optional ignored `compose.override.yaml` (from inert `compose.override.example.yaml`) may connect `app` to external OIDC, Cloudflare, SMTP, or reverse-proxy services using file-backed secrets. Production delivery and deployment workflows ([ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md), [ADR 0030](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md)) never consume local `compose.yaml` or `.env.example`.

## Consequences

Contributors can run and inspect the checkout via `docker compose up` without host Java, Node, PostgreSQL, or Redis installations, while production hosts remain decoupled from the local developer stack.

Pinned toolchain and dependency image digests must be updated explicitly when baseline versions advance.

## Alternatives considered

- Keep the application-only root `compose.yaml`. Rejected because it cannot run a source checkout standalone.
- Track a second production Compose file in this repository. Rejected because it would recreate competing production topology authority.
- Build a custom local image or auto-seed test fixtures on startup. Rejected because an upstream toolchain image suffices and browser fixtures are test-only.

## Review triggers

- The pinned toolchain image no longer supports the required JDK, Node, or target CPU architectures.
- Production artifact or host runtime ownership boundaries change.
