# ADR 0020: Treat Database and Asset Storage as One Recovery Unit

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-03 |
| Scope | architecture / persistence / recovery |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia's durable business state is split between a relational database and durable binary asset storage. The database holds asset metadata and business references, while the asset store holds the corresponding binary data. The current asset store is filesystem-backed, but the consistency requirement is independent of that provider.

Restoring either durable component alone can produce state that is structurally available but semantically incomplete or inconsistent. Recovery therefore has to preserve the relationship between business metadata and the referenced assets.

## Decision

Persefonia treats its relational database state and durable asset binary storage as one logical recovery unit. A valid recovery strategy must preserve consistency between business metadata and references and the corresponding durable binary assets.

Recovered persistent state must be used with an application and schema state compatible with the restored data.

Auxiliary state, including caches, Redis rate-limit state, and application sessions, is not part of the source-of-truth recovery unit. It may be rebuilt rather than restored as durable business state.

This decision is storage-provider-independent. Moving binary assets to another persistent backend does not remove the recovery invariant.

## Consequences

A database-only backup and an asset-only backup are each incomplete as a Persefonia recovery unit.

Backup and recovery mechanisms must preserve logical consistency across the durable components. This coordination adds operational complexity compared with backing up either store independently.

Future storage migrations must preserve the same invariant. Auxiliary caches, sessions, and short-lived Redis state can be recreated instead of recovered as source-of-truth state.

## Alternatives considered

- Treat PostgreSQL as the only recovery authority. Rejected because the database does not contain the durable binary asset contents.
- Treat asset storage as independently recoverable. Rejected because binary data depends on matching business metadata and references.
- Treat Redis, session, and cache state as part of the same durable recovery unit. Rejected because those stores are auxiliary or ephemeral by architecture.
- Restore persistent stores independently and repair inconsistencies afterward. Rejected because routine recovery must produce coherent source-of-truth state rather than depend on later reconciliation.

## Review triggers

- Asset storage moves from the filesystem to object storage or another durable backend.
- Asset ownership or storage semantics change.
- Binary assets move into another durable store or multiple durable asset stores are introduced.
- The PostgreSQL backup and recovery architecture changes materially.
- Explicit recovery point or recovery time objectives are introduced.
