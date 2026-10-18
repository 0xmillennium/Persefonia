# ADR 0026: Use OIDC Group Admission with Refresh-Backed Admin Session Revalidation

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-26 |
| Scope | security / architecture |
| Supersedes | none |
| Superseded by | none |

Refines [ADR 0007](0007-use-oidc-for-admin-authentication.md) and [ADR 0025](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) on OIDC identity admission.

## Context

[ADR 0007](0007-use-oidc-for-admin-authentication.md) keeps authentication external and authorization local. Static subject or email allowlists do not express provider group eligibility, and checking only login-time claims leaves long-lived admin sessions active after provider group removal or local role changes.

## Decision

Interactive OIDC login requires exact, case-sensitive membership in `persefonia.security.admin-access.required-oidc-group` before bootstrap locking or local account resolution. Provider groups are transient admission inputs only and are never persisted, audited, or mapped to application roles.

The OIDC `sub` claim binds the identity to a local `AdminAccount`, whose `ACTIVE` status and local `OWNER` / `EDITOR` roles remain the sole application authorization authority. Initial login may bootstrap the first `OWNER` and provision subsequent eligible identities as `EDITOR`.

Production requests `openid,profile,email,groups,offline_access` and stores OAuth tokens only in the HTTP session. When `/admin` validation is absent or at least five minutes old, the session refreshes tokens if needed, fetches UserInfo with bounded timeouts, verifies exact `sub` and group membership, and reloads local account status and roles read-only.

Group removal, disabled/missing account, subject mismatch, or malformed UserInfo invalidates the session (`403`). Revoked OAuth authorization or missing refresh state invalidates the session (`401`). Transient provider timeouts or `5xx` errors return `503` without destroying the session.

## Consequences

Group revocation and local account disablement or role changes take effect within five minutes across active sessions.

Admin requests periodically require OIDC availability, failing closed (`503`) on transient outages while preserving sessions and keeping Identity Access domain code framework-free.

## Alternatives considered

- Keep static allowlists or map provider groups to local roles. Rejected because group membership governs admission while authorization remains local ([ADR 0007](0007-use-oidc-for-admin-authentication.md)).
- Revalidate only login claims or ID tokens. Rejected because fresh UserInfo is required to observe current group membership.
- Persist refresh tokens in PostgreSQL or Redis. Rejected because session OAuth credentials are ephemeral infrastructure state.
- Fail open or destroy sessions on transient outages. Rejected because requests must fail closed while preserving recoverable sessions.

## Review triggers

- The admission group contract, OIDC claim format, or local account binding rules change.
- Session lifetime, revocation latency, or multi-instance session-sharing requirements change.
