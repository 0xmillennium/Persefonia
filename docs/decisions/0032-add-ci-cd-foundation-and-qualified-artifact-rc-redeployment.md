# ADR 0032: Add CI/CD Foundation and Qualified-Artifact RC Redeployment

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-29 |
| Scope | architecture / operations / deployment / supply-chain |
| Supersedes | none |
| Superseded by | none |

## Context

Reliable application delivery requires a qualified artifact and a deployment path whose trust and runtime checks remain consistent when an operator intentionally retries an existing artifact. CI, Delivery, and automatic Deploy RC have already executed successfully on the real RC environment, but an explicit path for redeploying an already-qualified artifact was missing. Persefonia owns source verification, artifact qualification, Delivery, and deployment orchestration; Website-Stack owns privileged host and runtime mechanics.

## Decision

The **CI/CD Foundation** is the repository's delivery and deployment contract. It comprises PR/master CI, fast production-automation validation, CI-produced BootJar handoff, Delivery without an application-source rebuild, immutable multi-platform OCI artifact qualification, provenance and supply-chain verification, exact-digest automatic RC deployment, pinned SSH host identity, a low-privilege deployment principal, the narrow Website-Stack runtime gateway, application-only mutation, PostgreSQL/Redis lifecycle preservation, exact configured and running image verification, application health verification, public ingress verification, and explicit operator redeployment of an already-qualified artifact.

An operator redeployment accepts only a qualified 40-character lowercase source SHA and a `sha256:` digest with 64 lowercase hexadecimal characters. The OCI repository is derived from the trusted GitHub repository context. Mutable tags and arbitrary repository references are not deployment authority. The existing source alias, registry index, supported-platform, digest/body, and GitHub-signed provenance checks re-establish qualification before any RC credential or mutation. The source alias is published only after Delivery's native platform and supply-chain verification, so binding the alias to the supplied digest also binds that qualification. The workflow uses its current trusted master revision for verification and deployment scripts, including when the artifact's source SHA is historical.

Redeployment neither rebuilds the application or image nor starts a new Delivery run. It uses the same `rc` GitHub environment, pinned SSH target, deployment principal, deployment runner, Website-Stack gateway, and RC concurrency group as automatic deployment. The existing runner captures PostgreSQL and Redis identities before application mutation and requires them to remain unchanged; it verifies the exact configured image and running image identity, application health, and public ingress. A same-digest run may succeed without recreating the application container. Automatic rollback is not introduced.

GitHub ruleset and branch-protection state, GitHub `rc` environment configuration, Cloudflare account configuration, Website-Stack topology, gateway implementation and sudo policy, and Traefik, Authelia, and Postfix internals remain externally owned. Their configuration is not duplicated here.

## Consequences

RC has two trusted entry surfaces: automatic deployment of a newly qualified artifact and explicit replay of an already-qualified artifact. Both converge on one executable deployment implementation. Operators can retry or recover an application deployment without producing another artifact. The extra workflow must be kept aligned with the automatic workflow's trust and deployment contracts; automation tests enforce this relationship.

This capability does not establish product or release readiness; application behavior requires independent qualification. The operator path also requires real RC acceptance after the workflow is merged before its live behavior can be considered verified.

## Alternatives considered

- Rebuild or rerun Delivery to redeploy. Rejected because it would change artifact identity and repeat production work.
- Use mutable tags or an operator-supplied repository. Rejected because they would weaken exact artifact authority.
- Add a separate host deployment implementation. Rejected because it would split the accepted runtime and privilege contract.

## Review triggers

- Artifact qualification, provenance, or source-alias policy changes.
- The RC environment, SSH trust boundary, or Website-Stack gateway contract changes.
- The supported platform set or deployment artifact authority changes.
- The release qualification policy changes.
