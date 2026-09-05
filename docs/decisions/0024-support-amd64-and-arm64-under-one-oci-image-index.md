# ADR 0024: Support AMD64 and ARM64 Under One OCI Image Index

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-06 |
| Scope | architecture / operations / deployment / supply-chain |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia is publicly distributed for deployment on common Debian-based Linux servers, which include both AMD64 and ARM64 hosts. ADR 0022 establishes an immutable OCI digest as the deployable artifact authority, but its multi-platform review trigger now requires the authority and qualification semantics to be explicit.

## Decision

Persefonia supports exactly `linux/amd64` and `linux/arm64` OCI runtime platforms. Both are first-class release platforms. The exact CI-verified BootJar byte sequence is packaged into both runtime variants; Java is not rebuilt separately for either platform.

One multi-platform OCI image index is the deployment authority. Its top-level digest is the immutable release identity, while the per-platform manifests are children used for implementation and verification. Both runtime variants require native runtime verification before a candidate is qualified. Source, release-candidate, and final aliases must point to the qualified top-level index digest, never to a child manifest.

The pinned runtime base image must support both platforms. Adding or removing a supported runtime platform requires an explicit architecture review.

## Consequences

Public users can pull one digest reference and their container runtime selects the appropriate verified child manifest. The same application bytes and one authoritative digest preserve the promotion semantics of ADR 0022.

Publishing and qualification require native runner coverage and registry evidence for both platforms, which increases delivery time and infrastructure dependence. Maintainers must not treat a successful build or one platform's smoke test as candidate qualification.

## Alternatives considered

- Support only AMD64. Rejected because public Debian-based server deployments include ARM64 and it would make ARM64 second-class.
- Build one Java application per architecture. Rejected because the application artifact is architecture-independent and separate builds would weaken same-byte traceability.
- Treat child-manifest digests as release authority. Rejected because consumers resolve the top-level index and release qualification must identify one artifact.

## Review triggers

- Adding or removing a supported OCI runtime platform.
- The pinned runtime base no longer supports either required platform.
- OCI index or immutable artifact promotion semantics change materially.
- Native GitHub-hosted verification can no longer qualify one of the required platforms.
