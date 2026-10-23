# ADR 0039: Separate Frontend and JVM CI Qualification

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-21 |
| Scope | architecture / testing / operations |
| Supersedes | ADR 0031 |
| Superseded by | ADR 0042 |

[ADR 0042](0042-use-fixed-lean-verification-and-explicit-diagnostics.md) supersedes test selection, browser topology and local check semantics below, defining ordinary tests, explicit diagnostics and Gate-owned native admission. Separate Build/JVM, Frontend, Verify and Gate ownership remains.

## Context

Full frontend stress verification currently delays the JVM producer and browser startup. Native results must remain complete and attributable when independent lanes overlap. Required check contexts and complete local verification remain stable.

## Decision

Automation remains the early gate. Build + JVM owns production compilation, resources, JTE, Vite, BootJar, all JVM verification, ownership/format checks and Java 21 smoke in one workspace. Frontend verification independently discovers and executes every unit case and both complete 100-seed stress pipelines. Browser qualification consumes the producer's checksum-verified candidate across eight isolated partitions, with one worker and zero retries, without waiting for frontend stress.

Verify is an always-evaluated non-browser aggregator of both producer receipts. Browser receipts independently reconcile discovery, execution and candidate identity. Gate always evaluates Automation, Verify and browser acceptance. Missing, failed, cancelled or unexpected skipped work fails admission. Receipts bind source/tree, workflow, run, attempt, lane, policy and candidate where applicable; source-only frontend receipts do not claim BootJar execution.

Root check, applicationCheck and app.check retain their full meanings. Explicit JVM and frontend lifecycle tasks partition CI ownership; future checks must belong to one lane while remaining reachable from full local verification. Test-result success stamps cannot replace fresh execution. JVM tests never import another runner's compiled outputs.

Bounded diagnostics preserve the original test failure and classify trace readability separately. Native decoding, virtual JavaScript time and compositor progress remain distinct prerequisites. Required GPU checks cannot qualify through fallback behavior.

## Consequences

Frontend stress overlaps production/JVM work and browsers start earlier. Receipts and lifecycle ownership add maintenance obligations; hosted performance requires matched measurements. Additional aggregation and artifact transfer have real costs.

## Alternatives considered

- Wider output/classpath bundles: rejected to preserve the producer/test workspace boundary.
- Reduced stress, retries or more browser workers: rejected because qualification coverage and first-attempt semantics must remain intact.
- Keeping serial frontend stress: rejected because it delays independent browser work.

## Review triggers

- A new verification task or browser partition changes ownership.
- Receipt admission or required checks change.
- Hosted measurements show excessive transfer or aggregation overhead.
