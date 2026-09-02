# ADR 0019: Keep Cache Invalidation Outside Public-Exposure Correctness

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-03 |
| Scope | architecture / caching / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia has strict public-exposure rules. Authoritative business state and the current Discovery projection determine whether a resource is eligible for public exposure.

[ADR 0016](0016-use-post-commit-operational-side-effects.md) places failure-prone cache purge work outside the source business transaction and prevents purge failure from rolling back committed business state. A fail-open operation performed after commit cannot also be the authority that decides whether a resource is public or private.

## Decision

Public-exposure correctness is determined by authoritative domain state and Discovery, not by CDN or cache invalidation state. Cache invalidation is an operational freshness and performance mechanism and remains outside the correctness boundary for public visibility.

Caching of mutable public resources must preserve application and origin authority when lifecycle-sensitive public state changes. Cache purge failure must not redefine whether a resource is published, private, unlisted, discoverable, or otherwise eligible for a public route.

This rule is provider-independent. No CDN or cache provider is a required dependency of public-exposure correctness, and a future implementation may change providers or delivery mechanisms only if it preserves this boundary.

ADR 0016 defines the delivery and transaction boundary for failure-prone operational side effects. This ADR defines the public-correctness consequence of that boundary and does not supersede ADR 0016. Stronger delivery guarantees may be introduced when operational requirements justify them, but public-exposure authority remains independently defined.

## Consequences

CDN or cache-provider availability is not part of business-state correctness. A purge failure can become an operational incident without rolling back or redefining source-domain state.

Mutable public caching strategies must defer to authoritative application and origin state. Aggressive stale-serving strategies are unsuitable where they can violate lifecycle-sensitive exposure rules.

Future provider and delivery implementations must preserve the same correctness boundary, even if their reliability guarantees differ.

## Alternatives considered

- Make successful cache purge part of unpublish or private-state correctness. Rejected because invalidation is failure-prone and intentionally outside the business transaction.
- Execute cache invalidation inside the business transaction. Rejected because external operational infrastructure must not control or delay the source business commit and this conflicts with ADR 0016.
- Disable caching entirely. Rejected because safe caching requires an authoritative correctness boundary, not the absence of caching.
- Require a durable outbox immediately. Rejected because it is an unnecessary reliability escalation for the current architecture; stronger delivery can be adopted later without moving public-exposure authority into cache infrastructure.

## Review triggers

- Cache invalidation acquires stronger delivery guarantees.
- A transactional outbox or durable queue becomes part of the architecture.
- The public route or Discovery authority model changes.
- The CDN or cache architecture changes materially.
- Intentional stale serving becomes a formal product requirement.
