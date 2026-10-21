# ADR 0022: Use an Immutable OCI Image as the Deployable Release Artifact

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-05 |
| Scope | architecture / operations |
| Supersedes | none |
| Superseded by | ADR 0040 |

## Context

Persefonia requires one deployable artifact that moves from CI verification through registry smoke, release-candidate (RC) deployment, recovery qualification, and final release without rebuilding between stages. Rebuilding at later stages or deploying by mutable tags risks running bytes that differ from the qualified candidate.

## Decision

Persefonia's deployable release artifact is an environment-neutral OCI image; the BootJar is a build intermediate packaged into that image.

Source identity (full Git commit SHA), artifact identity (`sha256:...` OCI manifest or image-index digest), and release aliases (`sha-<git-sha>`, `v0.1.0-rc.N`, `v0.1.0`) are distinct. The immutable OCI digest is the sole deployment authority (`registry.example/persefonia@sha256:<digest>`). Mutable tags (`latest`, `master`, or version tags) are never deployment authority.

Persefonia builds once and promotes the same digest across all stages without recompiling Java, rebuilding frontend assets, recreating the BootJar, or rebuilding the image. Qualification is strictly bound to the digest: source changes produce a new digest requiring full requalification, whereas external environment failures may be retried with the same qualified digest.

The OCI image must never contain runtime secrets, credentials, `.env` files, or host-specific configuration. The architecture requires an OCI-compatible registry with immutable digests; GHCR is the current provider.

## Consequences

Artifact traceability, same-byte promotion, digest-bound recovery qualification ([ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md)), and Java 21 baseline preservation ([ADR 0018](0018-preserve-java-21-compatibility-independently-of-development-jdk.md)) are guaranteed by one immutable digest.

Digest promotion requires explicit registry metadata operations, and any code fix requires producing and qualifying a new candidate digest rather than mutating an existing release artifact.

## Alternatives considered

- Rebuild per environment or on the deployment host. Rejected because qualification would apply to different bytes and weaken reproducibility.
- Deploy the raw BootJar directly. Rejected for the containerized deployment model.
- Use mutable image tags as deployment authority. Rejected because tags can resolve to different digests over time.
- Couple the architecture to a registry-specific identifier. Rejected to preserve OCI registry portability.

## Review triggers

- The deployment unit stops being an OCI artifact or adopts another immutable format.
- Multi-platform artifact identity changes promotion semantics.
- Release qualification no longer binds to a single immutable digest.
