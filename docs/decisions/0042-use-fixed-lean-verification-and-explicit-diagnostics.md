# ADR 0042: Use Fixed Lean Verification and Explicit Diagnostics

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-23 |
| Scope | architecture / testing / operations |
| Supersedes | ADR 0041, ADR 0039 |
| Superseded by | none |

This record supersedes ADR 0041 and only ADR 0039's test selection, browser topology and local check semantics.

## Context

Native journeys duplicate controller and server assertions, while automatic risk expansion makes development and qualification unpredictable. Test size (resources) and scope (behavior) are separate concerns.

## Decision

PRs and fully qualified master run the same ordinary contract. TypeScript logic and focused controller tests own scroll progression, Writing B lifecycle, gallery decisions, geometry, media deadlines and cleanup. JVM/JTE/MockMvc and real PostgreSQL retain SSR, metadata, forms, OWNER, CSRF, privacy, public exposure, transactions and migration constraints. Use the lowest effective layer without a numerical pyramid.

A source-owned manifest bounds mandatory native execution to sixteen identities, covering Chromium desktop and touch, Firefox and WebKit. Native tests own actual paint, reflow, focus/hash, decoding and native gesture arbitration. Use isolated contexts, one worker, zero retries and deadlines. One immutable BootJar and disposable database serve one discovery and one execution process. No test mutates shared fixtures.

Gate independently parses the raw native discovery and execution reports and validates every required identity against the source manifest. Missing, extra, duplicated, skipped, failed, cancelled, retried, stale or mismatched source/tree/candidate/run/attempt evidence fails admission. The separate browser-receipts job is retired. Policy/schema changes invalidate earlier receipts.

Ordinary check includes the representative scalar/live physics corpus and essential JVM/database checks; it excludes native browsers and exhaustive diagnostics. Full physics, GPU/codec, wheel exploration and authenticated focus diagnostics require explicit invocation. Retire redundant E2E after preserving effective oracles. No automatic risk expansion or nightly diagnostics.

Gradle tracks relevant source and build inputs and permits safe compilation/resource reuse. CI tests execute freshly; cached test success never constitutes qualification. Existing Java 21, immutable OCI provenance, AMD64/ARM64, RC and Delivery admission remain mandatory. ADR 0040 reuse remains shadow by default; this decision does not enable enforcement.

## Consequences

Ordinary verification becomes predictable. Exploration is explicit; native timing needs hosted evidence. Budget exhaustion fails closed.

## Alternatives considered

- Moving all legacy E2E into an optional profile retains redundant maintenance cost.
- Automatic deep selection or nightly matrices retain unpredictable or recurring cost.

## Review triggers

- A new native behavior requires exceeding the manifest bound.
- Regression evidence reveals a missing lower-layer oracle.
- Qualification trust, artifact identity or required checks change.
