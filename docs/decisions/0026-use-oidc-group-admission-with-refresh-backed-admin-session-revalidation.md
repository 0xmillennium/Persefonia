# ADR 0026: Use OIDC Group Admission with Refresh-Backed Admin Session Revalidation

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-26 |
| Scope | security / identity / authentication / authorization |
| Supersedes | none; refines ADR 0007 and ADR 0025 identity admission |
| Superseded by | none |

Runtime-ownership refinement: [ADR 0028](0028-separate-application-delivery-from-host-runtime-topology.md) supersedes the historical tracked-runtime-source paragraph below referencing `compose.production.yaml`, `.env.production.example`, `docker/postgresql/`, `docker/redis/`, and `docker/redis-start.sh`. Production topology, server configuration, and host preflight are externally owned. OIDC admission and revalidation semantics remain unchanged.

## Context

[ADR 0007](0007-use-oidc-for-admin-authentication.md) keeps authentication external and authorization local. Authelia remains the authentication provider and owns its two-factor policy. Subject/email admission lists do not express the intended provider eligibility, and checking local account existence first lets a provisioned identity bypass later admission changes. Login-time claims alone also leave long-lived admin sessions authorized after external eligibility or local account state changes.

## Decision

Every interactive OIDC login must pass coarse admission through the configured `persefonia.security.admin-access.required-oidc-group`, supplied in production by `PERSEFONIA_ADMIN_REQUIRED_OIDC_GROUP` (currently `admin`). Group matching is exact and case-sensitive. Groups are strictly parsed as string collections into transient typed values; they are not persisted, audited, added to AdminPrincipal, or converted into application roles or authorities. No legacy subject/email admission fallback remains.

The OIDC subject binds an external identity to the local AdminAccount. ACTIVE status and local OWNER / EDITOR roles remain the complete application authorization authority. Admission occurs before bootstrap locking or account resolution, including for existing accounts. Production enables initial OWNER bootstrap and automatic EDITOR provisioning for subsequent eligible identities. Provider groups such as `owner` have no role effect. Existing collision protection, bootstrap locking, and mandatory provisioning Audit remain.

Production requests `openid,profile,email,groups,offline_access` through authorization_code and client_secret_basic. External Authelia client configuration must separately permit these scopes and authorization_code / refresh_token grants. Spring Security owns initial authorization-code login and refresh through DefaultOAuth2AuthorizedClientManager with a refresh-token provider. An explicit HttpSessionOAuth2AuthorizedClientRepository is shared by login and refresh, including rotated tokens. Access and refresh tokens are session-scoped infrastructure state, never durable application state or AdminPrincipal fields. Missing client or refresh token fails revalidation closed. POST logout invalidates the session and therefore discards authorized-client state.

Successful login initializes a session timestamp. Before authorizing `/admin` requests, sessions revalidate when the last successful validation is at least five minutes old or absent. Revalidation retrieves fresh UserInfo with the current/refreshed access token using Spring's OidcUserService and DefaultOAuth2UserService, with a two-second connect timeout, five-second read timeout, redirects disabled, and OAuth2 error handling. Fresh UserInfo must contain both `sub` and `groups`. Its `sub` must be a non-blank String exactly equal to the subject bound to the authenticated OIDC identity, without trimming, case conversion, or identity fallback. These requirements are checked directly against fresh UserInfo before ordinary claim mapping; a stale or merged ID-token subject cannot satisfy freshness. Fresh UserInfo still overlays ID-token claims for ordinary profile mapping. Missing/malformed/mismatched fresh `sub` or absent fresh `groups` is INVALID_PROVIDER_RESPONSE, while present groups without the required group follow normal admission denial. The principal's bound subject is also checked. The framework-free read-only use case then reloads local account status and roles without provisioning, saves, Audit, or lastLoginAt changes.

A successful revalidation rebuilds the principal and authorities only from the current local account, creates a new SecurityContext, and explicitly saves it through the servlet session context repository before request authorization. Only the last successful Instant is additional session revalidation state. HTTP session timeout is unchanged; no Redis session storage is introduced.

Group removal, missing/disabled local account, subject mismatch, or malformed provider identity invalidates the session, clears the context, and denies the current request with 403. Revoked OAuth authorization, absent authorized client, or absent refresh token does the same with 401. Temporary provider timeout, connectivity failure, or 5xx returns 503 without destroying the session or advancing the timestamp. Failed revalidation never continues the admin request; a later due request may retry after an outage. Error messages remain generic and contain no identity claims or credentials.

No database schema change is made. ADR 0007 remains valid and is refined, not replaced. [ADR 0025](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) retains deployment and secret ownership; its historical allowlisted-identity wording is refined by this admission contract.

Runtime source remains `compose.production.yaml`, `.env.production.example`, `docker/postgresql/`, `docker/redis/redis.conf`, and the shared executable `docker/redis-start.sh`. Host-derived hardening is preserved: Redis's default user can only PING and its credential-backed application user is restricted to the rate-limit keyspace. Real secrets remain ignored in the flat `secrets/` directory; corresponding `.examples` files contain fake examples only. Real `.env.production` stays host-local and ignored. No competing production Compose/config authorities or host credentials enter tracked templates.

## Consequences

External group removal and local disablement revoke future admin operations at the next due validation, bounded by the five-minute interval. Local role changes become visible to authorization on that request and subsequent session requests. Provider availability is now required periodically for admin operations, but temporary outages preserve the session for retry. Public routes, OWNER command authorization, logout, and actuator isolation retain their existing behavior.

OAuth and UserInfo protocol handling remain in app infrastructure. Identity Access stays framework-free, with no token, group, or session persistence changes. CI can verify runtime structure using synthetic configuration without real secret contents.

## Alternatives considered

- Keep subject/email lists or a compatibility fallback. Rejected because admission must reflect the required provider group for every login.
- Convert provider groups to local roles. Rejected because local authorization authority must remain inside Persefonia.
- Revalidate only login-time claims or refreshed ID tokens. Rejected because fresh UserInfo is required to observe current group membership.
- Persist refresh tokens in PostgreSQL or Redis. Rejected because browser-session authorization state is ephemeral infrastructure state.
- Fail open or destroy sessions during temporary IdP outages. Rejected because operations must be blocked while retaining recoverable authenticated sessions.
- Implement refresh or UserInfo protocols manually. Rejected because Spring Security provides those lifecycle and integrity mechanisms.

## Review triggers

- The admission group contract or Authelia claim shape changes.
- Session lifetime or revocation-latency requirements change.
- The deployment becomes multi-instance or adopts shared HTTP sessions.
- Local authorization roles or account binding rules change.
- A requirement emerges for back-channel/RP logout or durable OAuth credentials.
