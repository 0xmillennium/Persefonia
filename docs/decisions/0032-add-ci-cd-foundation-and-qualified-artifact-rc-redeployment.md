# ADR 0032: Add CI/CD Foundation and Qualified-Artifact RC Redeployment

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-29 |
| Scope | architecture / operations / security |
| Supersedes | none |
| Superseded by | ADR 0040 |

## Context

Reliable application delivery requires both automatic promotion of newly qualified artifacts and an operator-triggered redeployment path for an already-qualified artifact that preserves identical trust, provenance, and runtime verification boundaries.

## Decision

The repository's CI/CD Foundation comprises PR/master CI (`CI / Gate`), BootJar handoff to Delivery without source rebuild, multi-platform OCI image index qualification ([ADR 0024](0024-support-amd64-and-arm64-under-one-oci-image-index.md)), provenance verification, automatic Deploy RC over authenticated SSH via the narrow host gateway ([ADR 0030](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md)), and explicit operator redeployment of an already-qualified artifact.

Operator redeployment accepts only a 40-character lowercase Git commit SHA and a `sha256:<64-hex>` digest, deriving the OCI repository from the trusted GitHub context. Before entering the `rc` environment, the workflow re-verifies the `sha-<git-sha>` registry alias, multi-platform index, and GitHub-signed provenance against the supplied digest.

Redeployment never rebuilds code or reruns Delivery. It shares the `rc` environment, concurrency group, SSH trust boundary, and `persefonia-runtimectl` orchestration with automatic Deploy RC: preserving PostgreSQL and Redis container IDs, verifying `app` health and exact running image identity, and running public HTTPS smoke checks without blind automatic rollback.

## Consequences

RC deployment has two entry points—automatic post-Delivery promotion and explicit operator replay—that converge on one verified deployment implementation without rebuilding artifacts.

Automation tests enforce parity between the automatic and manual workflows, while host topology and GitHub environment rules remain externally owned.

## Alternatives considered

- Rebuild source or rerun Delivery to redeploy. Rejected because it creates a new digest instead of replaying the qualified artifact ([ADR 0022](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md)).
- Accept mutable tags or operator-supplied registry URLs. Rejected because they weaken exact artifact authority.
- Maintain a separate deployment script for manual redeployment. Rejected because it would split the runtime and privilege contract.

## Review triggers

- Artifact qualification, provenance, or `sha-<git-sha>` alias semantics change.
- The `rc` environment, SSH trust boundary, or `persefonia-runtimectl` gateway contract changes.
- Release qualification policy changes.
