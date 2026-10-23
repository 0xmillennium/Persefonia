# ADR 0041: Use Risk-Owned Test Profiles and a Shared Native Lifecycle

| Field | Value |
|---|---|
| Status | Superseded |
| Date | 2026-10-22 |
| Scope | architecture / testing / operations |
| Supersedes | ADR 0039 |
| Superseded by | ADR 0042 |

[ADR 0042](0042-use-fixed-lean-verification-and-explicit-diagnostics.md) supersedes this record's risk routing and local check semantics. This record superseded only ADR 0039's selection and browser partition topology.

## Context

Browser cross-products and exploratory physics impose disproportionate cost. Deterministic logic, SSR, databases and native presentation need separate owners.

## Decision

Use TypeScript tests for deterministic state and DOM projection; JTE/MockMvc for SSR, metadata, forms and public policy; real PostgreSQL for constraints, concurrency, transactions and mandatory Audit rollback. Retire native assertions only when replacement oracles demonstrate defect detection. Preserve unique paint, decoding, GPU, touch, focus, CSP and fallback witnesses.

One artifact-backed native job runs Chromium desktop/touch, Firefox and WebKit sequentially against one application/database lifecycle. Public fixtures are immutable; contexts are fresh. Admin risk adds two Chromium forms in the JVM producer's workspace, using a separate disposable lifecycle and explicit preview classes; test authentication never enters BootJar. Required capabilities cannot become skips. Use one worker and zero retries.

Source policy defines ordinary identities, skips and independent deep families. PR base-to-tested-tree and master-parent changes determine risks. Physics requires both full pipelines; media requires relevant deep native tests; admin/shared forms require JVM and isolated native evidence. Unknown, truncated, renamed or deleted paths block pending reviewed classification. Writing/CSS retains bounded ordinary witnesses. No nightly deep schedule is implied.

Receipts bind policy, source/tree, candidate, environment, run/attempt, profile, independent discovery and first outcomes. Old schemas cannot qualify. Preserve early Automation, independent Build/JVM and Frontend, non-browser Verify, always-evaluated Gate, master candidate authority, Java 21 and immutable OCI/RC delivery. ADR 0040 defaults to shadow; policy changes cannot reuse old qualification.

`check` and `applicationCheck` retain comprehensive local JVM/frontend coverage. Ordinary physics reduction requires demonstrated saved scalar/coalesced regressions and input classes. Comprehensive and individual deep commands retain full exploration. Reuse fingerprinted build outputs locally; selected CI tests must execute.

## Consequences

Shared setup may increase the sequential tail. Hosted qualification and timing remain necessary; latency targets are unproven. A two-job split requires a separate measured decision. Missing infrastructure blocks qualification.

## Alternatives considered

- Four or eight isolated jobs: repeat setup without measured need.
- Browser emulation or nightly matrices: weaken evidence or retain recurring cost.

## Review triggers

- Hosted critical path, fixture isolation, regression coverage or trust boundaries change.
