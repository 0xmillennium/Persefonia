# ADR 0007: Use OIDC for Admin Authentication

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | security |
| Supersedes | none |
| Superseded by | none |

## Context

Admin authentication should rely on a standard identity protocol and avoid local password handling.

At the same time, external provider roles or groups should not directly become application authority.

## Decision

Persefonia uses OIDC for admin authentication.

Persefonia does not store admin passwords.

Local admin authorization is derived from Persefonia admin account state, not from provider groups or roles.

## Consequences

The application avoids local admin password storage.

OIDC provider configuration must be handled carefully.

Local admin roles remain under application control.

Provider identity is used for authentication, not as the source of application authorization.

This decision works together with [ADR 0008: Require OWNER Authorization for Admin Mutations](0008-require-owner-authorization-for-admin-mutations.md).

## Alternatives considered

- Local username/password admin login.
- Provider groups as direct application admin authority.
- No local admin account state.

These alternatives were rejected for the current security model.

## Review triggers

- Multiple identity providers are introduced.
- Local password login becomes a requirement.
- Provider trust assumptions change.
- Application authorization is redesigned.
