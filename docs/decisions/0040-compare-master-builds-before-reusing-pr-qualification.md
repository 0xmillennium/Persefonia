# ADR 0040: Compare Master Builds Before Reusing PR Qualification

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-21 |
| Scope | architecture / security / operations |
| Supersedes | ADR 0022, ADR 0032 |
| Superseded by | none |

[ADR 0042](0042-use-fixed-lean-verification-and-explicit-diagnostics.md) governs qualification inventory and receipts. Gate validates current-policy raw native discovery/execution reports; the retired browser-receipts job is not required. Shadow remains default; policy changes preclude reuse.

## Context

Equal trees do not establish equal tested bytes or trusted policy. Delivery requires authoritative master candidates and immutable OCI promotion.

## Decision

CI_QUALIFICATION_REUSE supports off, shadow and enforce; unset defaults to shadow and invalid modes fail closed. PRs always qualify fully. Eligible comparisons require first-attempt trusted master pushes uniquely associated with a merged same-repository PR and successful first-attempt PR CI completed within the preceding 24 hours. Support tested two-parent merges and single-parent squashes retaining their recorded base.

Forks, ambiguity, reruns, policy changes and unknown metadata require full qualification.

The master verifier checks API identities, trees/parents, native receipts, artifact digests and inner BootJar bytes. A fresh master build and Java 21 smoke must succeed. Deterministic production manifests captured during actual JVM execution must equal master output. Resolved toolchain, runner, browser/decoder and selection policy identities must match.

Manifests are comparison data, never executable classpath imports. Treat artifacts as bounded untrusted data; validate paths, schemas, duplicates, CRC and digests.

Off performs no lookup. Shadow compares and always fully qualifies master. Enforce permits skipped expensive lanes only with valid identity-bound master equivalence. Misses require fresh full producer workspaces and their tested candidates. Comparison build/runtime failures remain failures.

Failed full qualification cannot consume stale success. Retain PR identities and qualification evidence for at least 14 days.

Delivery admits only successful trusted master-push CI, validates qualification and consumes its authoritative master BootJar: fully tested or freshly rebuilt equivalent output.

Preserve ADR 0022's same-byte OCI promotion, ADR 0024's native AMD64/ARM64 qualification, RC protections and automatic/manual deployment parity.

## Consequences

Enforcement avoids duplicate suites but needs conservative comparison and an off switch. Shadow and misses may add a build. Enable enforcement only after an eligible shadow PR/master pair, negative admission evidence and bounded matched hosted measurements. Local fixtures cannot prove hosted performance.

## Alternatives considered

- Trust tree equality or self-asserted receipts: rejected as insufficient evidence.
- Deploy PR artifacts directly: rejected to retain master authority.

## Review triggers

- Merge strategy, trust policy, runtimes or artifact schemas change.
- Hosted evidence supports enabling enforcement or exposes mismatches.
