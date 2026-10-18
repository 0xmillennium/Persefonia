# ADR 0019: Keep Cache Invalidation Outside Public-Exposure Correctness

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-03 |
| Scope | architecture / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Authoritative domain state and the current Discovery projection determine whether a resource is eligible for public exposure. Because [ADR 0016](0016-use-post-commit-operational-side-effects.md) executes failure-prone cache purge after commit, a best-effort post-commit operation cannot also serve as the authority for public visibility.

## Decision

Public-exposure correctness is determined by authoritative domain state and Discovery, never by CDN or cache invalidation state. Cache invalidation is an operational freshness mechanism outside the public-visibility correctness boundary.

Caching of mutable public resources must preserve origin authority when lifecycle-sensitive state changes. Cache purge failure must not redefine whether a resource is published, private, unlisted, or discoverable.

This boundary is provider-independent: no CDN or cache provider is a required dependency of public-exposure correctness.

## Consequences

CDN or cache-provider outages do not corrupt business-state correctness; a purge failure is an operational freshness incident rather than a rollback or exposure-rule change.

Mutable public caching strategies must defer to origin state and avoid stale-serving policies that violate lifecycle-sensitive visibility transitions.

## Alternatives considered

- Make successful cache purge part of unpublish or private-state correctness. Rejected because invalidation is failure-prone and runs outside the business transaction.
- Execute cache invalidation inside the business transaction. Rejected because external infrastructure must not block or roll back domain commits ([ADR 0016](0016-use-post-commit-operational-side-effects.md)).
- Disable public caching entirely. Rejected because safe caching requires clear origin authority, not the absence of caching.

## Review triggers

- Cache invalidation adopts a durable outbox or stronger delivery guarantees.
- The public route or Discovery authority model changes.
- Intentional stale serving becomes a product requirement.
