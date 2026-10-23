# ADR 0040: Compare Master Builds Before Reusing PR Qualification

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-21 |
| Scope | architecture / security / operations |
| Supersedes | ADR 0022, ADR 0032 |
| Superseded by | none |

[ADR 0042](0042-use-fixed-lean-verification-and-explicit-diagnostics.md) changes the qualification inventory and receipt policy. Comparisons require current-policy native evidence validated by Gate, including raw discovery/execution reports; the retired browser-receipts job is not an admission prerequisite. Shadow remains the default and policy changes remain ineligible for reuse.

## Context

Equal trees do not establish equal tested bytes or trusted policy. Delivery requires authoritative master candidates and immutable OCI promotion.

## Decision

CI_QUALIFICATION_REUSE supports off, shadow and enforce; unset defaults to shadow and invalid modes fail closed. PRs always qualify fully. Only first-attempt trusted master pushes uniquely associated with a merged same-repository PR may compare against successful first-attempt PR CI completed within the preceding 24 hours. Support requires a tested two-parent merge and single-parent squash retaining its recorded base.

Forks, ambiguity, reruns, policy changes and unknown metadata require full qualification.

The master verifier checks API identities, trees/parents, native receipts, artifact digests and inner BootJar bytes. A fresh master production build and Java 21 smoke must succeed. Deterministic production manifests captured at actual JVM execution must equal master output; resolved toolchain, runner, browser/decoder and selection policy identities must also match.

Manifests are comparison data, never executable classpath imports. Artifacts are bounded untrusted data with strict paths, schemas, duplicates, CRC and digest validation.

Off performs no lookup. Shadow compares but always executes full master qualification. Enforce permits intentionally unexecuted expensive lanes only with a valid identity-bound master equivalence decision. Misses select a fresh full producer workspace and its tested candidate; comparison build/runtime failure is a real failure.

Failed full qualification cannot consume stale success. Retain original PR identities and qualification evidence for at least 14 days.

Delivery admits only successful trusted master-push CI, validates its qualification and consumes the authoritative master BootJar: fully tested output or fresh equivalent output.

ADR 0022's same-byte OCI promotion and ADR 0024's native AMD64/ARM64 qualification remain unchanged, as do RC protections and automatic/manual deployment parity.

## Consequences

Eligible enforcement avoids duplicate suites but requires conservative comparison and an operational off switch. Misses and shadow comparisons can cost an extra build. Enforcement requires a real eligible shadow PR/master pair, negative admission evidence and bounded matched hosted measurements; local fixtures cannot prove hosted performance.

## Alternatives considered

- Trust tree equality or self-asserted receipts: rejected as insufficient evidence.
- Deploy PR artifacts directly: rejected to retain master authority.

## Review triggers

- Merge strategy, trust policy, runtimes or artifact schemas change.
- Hosted evidence supports enabling enforcement or exposes mismatches.
