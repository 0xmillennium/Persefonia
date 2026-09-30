# ADR 0033: Keep Production Runtime Topology External and Use Compose for Containerized Local Development

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-01 |
| Scope | architecture / operations / developer experience / supply-chain |
| Supersedes | ADR 0028 |
| Superseded by | none |

## Context

The root Compose descriptor previously described one deployed application container supplied with an already-built image. It could not start the current source checkout without separately provisioned dependencies, build tools, writable media, and secret files. That made local browser review depend on a production-style artifact while the repository still correctly kept production runtime topology external.

## Decision

Persefonia owns application source and configuration contracts, the production OCI artifact recipe in the root `Dockerfile`, the supported OCI platforms in root `supported-platforms.txt`, CI/CD artifact qualification and handoff, and a reproducible containerized local development environment. It does not own production Compose topology, production PostgreSQL or Redis lifecycle, reverse-proxy deployment, OIDC-provider or SMTP deployment, host networking, storage, secrets, or the host's orchestration technology.

The single tracked root `compose.yaml` is exclusively for local development. It starts exactly `app`, `postgres`, and `redis` on the default project network. `app` uses a digest-pinned, prebuilt upstream JDK 25 and Node 22 toolchain image, bind-mounts the current checkout, and invokes the repository Gradle wrapper's `:app:bootRun` task. It neither builds a Persefonia image nor pulls a Persefonia release image. The PostgreSQL and Redis images are digest-pinned to the accepted CI dependency identities. PostgreSQL has a named durable volume; Redis state is disposable under [ADR 0009](0009-keep-redis-auxiliary-only.md).

The tracked `.env.example` contains non-secret local configuration and paths to safe, fake `secrets/*.examples` files. Contributors copy it to ignored `.env` and run `docker compose up`; no credential-generation step is required. Sensitive values enter the three containers through Compose secrets, never ordinary service environment variables. PostgreSQL reads its password through `POSTGRES_PASSWORD_FILE`; the application reads its datasource password, Redis password, and contact rate-limit secret through the local profile's optional `/run/secrets/` configtree import. Real local secret files remain ignored and can replace the example sources by changing only file paths in `.env`.

Redis disables the default user and creates a named application ACL user from the shared non-secret username and rate-limit key prefix. Its password is read from a Compose secret, hashed into an ephemeral ACL file, and never placed in the Redis process arguments. The application user may use only the commands needed for connection and rate-limit counting, and only keys beneath its configured prefix. Redis runs as its non-root image identity, and its authenticated healthcheck uses the named user. An ignored `compose.override.yaml` may connect the app to existing external services. The tracked `compose.override.example.yaml` demonstrates OIDC and Cloudflare configuration with non-secret environment inputs and file-backed credentials, plus supported reverse-proxy settings. It adds no service. SMTP authentication is not part of the current application configuration. Build caches and media live under ignored `.runtime/`. `docker compose down` preserves PostgreSQL; `docker compose down -v` resets its local volume. The browser fixture remains an explicit, destructive review action and never runs on startup.

The production delivery path remains source, qualified BootJar, root `Dockerfile`, immutable multi-platform OCI image/index, CI/CD, and an externally owned host runtime. Delivery, deploy, and redeploy workflows cannot consume local `compose.yaml` or `.env.example`. The narrow authenticated runtime gateway in [ADR 0030](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md) remains authoritative for production mutation.

This decision changes the local Compose meaning in [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md), while retaining its external production ownership boundary. It does not reverse immutable artifact authority in [ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md), AMD64 and ARM64 production support in [ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md), production secret ACL requirements in [ADR 0027](0027-require-explicit-posix-acls-for-non-root-compose-file-secrets.md), or CI/CD qualification and redeployment in [ADR 0032](0032-add-ci-cd-foundation-and-qualified-artifact-rc-redeployment.md). Local Compose file-backed secrets preserve ADR 0027's non-environment secret boundary; its production POSIX ACL matrix remains externally enforced.

## Consequences

A checkout can run and review current source without host Java, Node, Gradle, PostgreSQL, or Redis installations and without waiting for a release image. Source edits take effect after `docker compose restart app`, which reruns Gradle. The pinned toolchain and dependency images need explicit updates when their supported versions change. Contributors keep local data and caches under their own control. Production hosts remain free to use Compose or another runtime and are not coupled to the developer stack.

## Alternatives considered

- Keep the application-only root Compose descriptor. Rejected because it does not provide a reproducible source runtime for contributors.
- Add a production Compose example alongside local Compose. Rejected because it would create a competing production topology authority.
- Build a repository-owned development image. Deferred because a maintained prebuilt upstream image meets the current toolchain and platform requirements.
- Add wrapper scripts or automatic fixture seeding. Rejected because ordinary Compose commands already express the lifecycle and the fixture is destructive test data.

## Review triggers

- The pinned upstream toolchain no longer supplies the required JDK, Node, CPU platforms, or non-root runtime behavior.
- Local source builds require a materially different build or cache strategy.
- Production artifact or host runtime ownership changes.
- The accepted PostgreSQL or Redis compatibility identity changes.
