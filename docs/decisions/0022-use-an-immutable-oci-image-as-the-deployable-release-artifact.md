# ADR 0022: Use an Immutable OCI Image as the Deployable Release Artifact

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-05 |
| Scope | architecture / delivery / operations / release |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia needs one deployable artifact that can move from CI verification through registry-backed runtime smoke, release-candidate deployment, coordinated recovery qualification, and final release without rebuilding between stages. Rebuilding at any later stage risks CI-tested bytes differing from RC bytes, or RC-qualified bytes differing from final-release bytes.

Image tags alone are ambiguous because a mutable tag can later resolve to a different artifact. Source identity, deployable-artifact identity, and release/qualification identity answer different questions and must remain distinct.

## Decision

Persefonia's deployable release artifact is an OCI image. The BootJar remains a build/package intermediate; after image creation, the OCI artifact is the deployment authority. The conceptual path is source commit to verified BootJar to OCI image to deployment. Raw BootJar deployment is not an equivalent production path for the selected containerized deployment model.

The full Git commit SHA is the source identity and answers which source state produced an artifact. The OCI manifest or image-index digest (`sha256:...`) is the artifact identity and answers which exact immutable deployable artifact is being used. The digest means the registry digest of the deployable OCI manifest or image index, not a layer digest or config digest. A release identity such as `v0.1.0-rc.1` or `v0.1.0` assigns release or qualification meaning to that artifact. These identities are not interchangeable.

The OCI digest is the deployment artifact authority. Deployments must conceptually resolve a reference such as `registry.example/persefonia@sha256:<digest>`; a tag alone is not sufficient deployment identity.

Persefonia builds once and promotes the same artifact: one source state produces one verified OCI candidate and one OCI digest, which then moves through registry smoke, RC deployment, recovery qualification, and final release. Promotion may resolve, verify, alias, and deploy an existing digest; it must not compile Java, run Vite or npm builds, recreate the BootJar, or rebuild the image.

A different OCI digest is a different release candidate. If source or artifact correction produces a new digest, prior RC and recovery qualification do not transfer; the new digest must pass the required qualification again. Release qualification is digest-bound. Conversely, a deployment failure caused solely by external environment configuration may be retried with the same digest and requalified without a rebuild when the artifact has not changed.

Temporary CI publication may use a non-release alias equivalent to `ci-<run-id>-<attempt>`. After registry-backed qualification, the digest may receive `sha-<full-git-sha>`, meaning that the digest is the delivery-eligible candidate produced for that exact commit. The same digest may then receive `v0.1.0-rc.1` and, after all required qualification, `v0.1.0`. A fix after an RC produces a new source commit, a new digest, and the next RC, such as `v0.1.0-rc.2`; the final release must not rebuild the RC artifact.

Application/build version and release/qualification alias are distinct. For the first release train, the future release-candidate artifact should report application version `0.1.0`, while `v0.1.0-rc.N` remains an external release/qualification identity. The current `0.1.0-SNAPSHOT` project version is unchanged by this decision; the version transition belongs to D1.

The OCI image must be environment-neutral. It must not contain database or Redis passwords, OIDC client secrets, SMTP credentials, Cloudflare tokens, deployment hostnames, `.env` content, or deployment-host secrets. Runtime configuration and secrets are external to the image; the exact configuration injection mechanism belongs to D2.

The architectural requirement is an OCI-compatible registry with immutable digest semantics. GHCR is the currently intended implementation provider, but changing to another OCI-compatible registry does not supersede this decision unless artifact identity or promotion semantics change.

For the initial release train, `latest`, `main`, and `master` are not deployment authorities. `master` remains the canonical repository branch from which future candidate creation originates unless explicitly changed, but that branch convention does not make an OCI `master` tag a valid deployment authority. Future tags may exist for convenience while deployment authority remains the digest.

## Consequences

Artifact and release traceability become explicit. Same-byte promotion, digest-bound recovery qualification, rollback analysis, registry portability, and supply-chain evidence compatibility are supported by one immutable artifact identity.

Digest handling is operationally more explicit, and tag promotion requires registry metadata operations. Fixes require a new candidate rather than mutating an existing artifact. A final `v0.1.0` therefore means that the exact OCI digest passed CI verification, registry-backed artifact smoke, RC deployment, coordinated PostgreSQL plus durable Media recovery qualification, OWNER deep recovery verification with `CONSISTENT`, and representative release smoke.

This preserves the Java 21 compatibility baseline in [ADR 0018](0018-preserve-java-21-compatibility-independently-of-development-jdk.md) and binds release qualification to the database-plus-durable-asset recovery unit in [ADR 0020](0020-treat-database-and-asset-storage-as-one-recovery-unit.md).

## Alternatives considered

- Rebuild per environment. Rejected because qualification would apply to different bytes.
- Build the image on the deployment host. Rejected because the deployment host would become a build environment and reproducibility would weaken.
- Deploy the raw BootJar directly as the release artifact. Rejected for the selected containerized deployment model.
- Use a mutable image tag as deployment authority. Rejected because the same tag can resolve to different artifacts.
- Treat a registry-specific identifier as architecture. Rejected because the architecture must remain OCI-provider-independent.
- Build an RC-specific binary and rebuild a final binary. Rejected because the final release would no longer be the exact qualified artifact.

## Review triggers

- The deployment unit stops being an OCI artifact.
- Another immutable artifact format replaces OCI.
- Multi-platform artifact identity materially changes the promotion contract.
- Release qualification no longer binds to one immutable digest.
