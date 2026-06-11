# ADR 0008: Require OWNER Authorization for Admin Mutations

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | security |
| Supersedes | none |
| Superseded by | none |

## Context

State-changing admin actions need a consistent authorization rule before admin features expand.

Route protection proves that a request reached an admin-only surface, but it is not sufficient as the only mutation authorization boundary.

## Decision

State-changing admin commands require application-layer authorization from an active local admin with the `OWNER` role.

Route protection alone is not sufficient.

Controller annotations alone are not sufficient.

The authorization rule must be enforced before side effects occur.

This decision depends on the local admin authority model described in [ADR 0007: Use OIDC for Admin Authentication](0007-use-oidc-for-admin-authentication.md).

## Consequences

Future admin mutation services must call the admin command authorization policy.

`EDITOR` may exist as a role but does not grant state-changing mutation authority in the current model.

Tests must prove that unauthorized admin mutation attempts do not produce side effects.

## Alternatives considered

- Treat any authenticated admin as mutation-capable.
- Rely only on route protection.
- Rely only on controller annotations.

These alternatives were rejected because they create weak or unclear authorization boundaries.

## Review triggers

- A richer permission matrix is introduced.
- `EDITOR` receives mutation permissions.
- Admin mutation risk categories are introduced.
- Command authorization moves to a different model.
