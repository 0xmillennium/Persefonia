# ADR 0041: Use Risk-Owned Test Profiles and a Shared Native Lifecycle

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-22 |
| Scope | architecture / testing / operations |
| Supersedes | ADR 0039 (selection and browser partition topology only) |
| Superseded by | none |

## Context

Browser cross-products and exploratory physics impose disproportionate cost. Deterministic logic, SSR, databases and native presentation need separate owners.

## Decision

Use focused TypeScript tests for deterministic state and DOM projection; JTE/MockMvc for SSR, metadata, forms and public policy; real PostgreSQL tests for constraints, concurrency, transactions and mandatory Audit rollback. Retire native assertions only after replacement oracles demonstrate defect detection. Preserve unique native paint, decoding, GPU, touch, focus, CSP and fallback witnesses.

One artifact-backed native job runs Chromium desktop, Chromium touch, Firefox and WebKit sequentially sharing application/database lifecycle. Public fixtures are immutable; contexts are fresh. Admin risk adds two Chromium forms in the JVM producer’s own workspace with a separate disposable lifecycle and only explicit preview classes; test authentication never enters BootJar. Required capabilities cannot become skips. Use one worker and zero retries.

Source policy defines ordinary identities, skips and independent deep families. PR base-to-tested-tree and master-parent changes determine risks. Physics owners require both full pipelines; media owners require relevant deep native tests; admin/shared-form owners require JVM and isolated native evidence. Unknown, truncated, renamed or deleted paths block pending reviewed classification. Routine writing/CSS retains bounded ordinary witnesses. No nightly deep schedule is implied.

Receipts bind policy, source, tree, candidate, environment, run, attempt, profile, independent discovery and first outcomes. Old schemas cannot qualify this policy. Preserve early Automation, independent Build/JVM and Frontend, non-browser Verify, always-evaluated Gate, authoritative master candidates, Java 21 and immutable OCI/RC delivery. ADR 0040 remains shadow by default; policy changes cannot reuse old qualification.

`check` and `applicationCheck` retain comprehensive local JVM/frontend meaning. Ordinary physics reduction requires demonstrated saved scalar/coalesced regressions and input classes. Comprehensive and individual deep commands retain full exploration. Fingerprint build outputs for local reuse; CI selected tests must actually execute. See the developer testing guide.

## Consequences

Shared setup may increase the sequential tail. Hosted qualification and timing remain necessary; latency targets are unproven. A two-job split requires a separate measured decision. Missing infrastructure blocks qualification.

## Alternatives considered

- Four or eight isolated jobs: repeat setup without measured need.
- Browser emulation or nightly matrices: weaken evidence or retain recurring cost.

## Review triggers

- Hosted critical path, fixture isolation, regression coverage or trust boundaries change.
