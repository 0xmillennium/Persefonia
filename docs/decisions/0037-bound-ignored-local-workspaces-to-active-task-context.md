# ADR 0037: Bound Ignored Local Workspaces to Active Task Context

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-10-20 |
| Scope | documentation |
| Supersedes | ADR 0034 |
| Superseded by | none |

## Context

[ADR 0034](0034-permit-structured-ignored-local-working-contracts.md) permits structured local contracts without defining their retention lifecycle. Accumulating completed explorations, copied workspaces and generated artifacts makes current context difficult to identify. Temporary evidence needs a bounded home while durable decisions remain discoverable in committed ADRs.

## Decision

Keep `.local/` Git-ignored. Permanent working context consists of `.local/TODO.md`, the actionable queue, and concise `.local/tooling/DESIGN-TOOLCHAIN.md` operational guidance.

Task context beyond a short queue entry belongs under `.local/work/<task>/`. Reuse the directory for continuing work, with one current report, necessary supporting evidence and an explicit retention condition. Record scope, status, relevant source/artifact identities, applicable approval provenance, observed validation and unresolved work. Simple changes need no task directory.

Before removing completed material, extract remaining actions into TODO. Preserve evidence while a concrete unresolved finding or acceptance condition needs it; preserve original identities and timestamps when relocating it. Remove the task directory when its retention condition is satisfied. Do not maintain permanent archives, copied workspaces, downloaded executables or source-media/frame libraries in `.local/`. Generated intermediates use owned temporary directories or existing build/test outputs.

Local records index explicit approvals and evidence; they do not automatically establish product or architectural authority. Durable decisions graduate into committed ADRs. Committed documentation remains governed by ADR 0002, and deleting `.local/` must never make the repository architecturally unintelligible.

## Consequences

Current work remains identifiable without accumulating a second repository history. Maintainers must extract unresolved actions and assess evidence retention before cleanup. Ignored context remains local and cannot supply required architecture to another checkout.

## Alternatives considered

- Unrestricted local categories or permanent archives: rejected because completed material obscures active context.
- TODO-only notes: rejected because complex work needs inspectable evidence and approval provenance.
- Commit temporary reports: rejected under ADR 0002.

## Review triggers

- Multiple maintainers need shared task evidence.
- Release acceptance requires a durable evidence store.
- Local context accumulates without satisfied retention conditions being enforced.
