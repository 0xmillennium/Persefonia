# ADR 0014: Use PostgreSQL Full Text Search for Public Search

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-24 |
| Scope | architecture / public search / privacy |
| Supersedes | none |
| Superseded by | none |

## Context

Public search needs to query eligible public resources without adding a separate search platform or broadening what is observable about visitor queries.

Discovery already stores public `search_text` for projected resources. That is enough for the first public search implementation.

## Decision

Public search uses PostgreSQL full text search over `discovery.discoverable_resources.search_text`.

The domain model does not add a `searchVector` value object or logical column. SQL may use PostgreSQL full text functions, but a durable `search_vector` schema concept is not part of this decision.

Elasticsearch and OpenSearch are out of scope.

Search uses `GET`. Search result pages are `noindex, follow` and are not long-cache public pages.

Search terms are not persisted. Search terms are not written to Insights. Application code must not intentionally log raw search query terms.

## Consequences

Search can be implemented with the existing database and Discovery projection.

Operations stay simpler because there is no separate search cluster.

Search ranking and linguistic behavior are limited to PostgreSQL capabilities until this decision is revisited.

Future maintainers must treat query text as visitor-sensitive data.

## Alternatives considered

- Introduce Elasticsearch or OpenSearch.
- Add a logical `searchVector` concept to the domain model.
- Persist search terms immediately for analytics.

These alternatives were rejected because they add infrastructure or privacy risk before the product needs them.

## Review triggers

- PostgreSQL full text search is not good enough for public search quality.
- Search analytics are proposed.
- Search moves to a separate service or index.
- Public search cache policy changes.
