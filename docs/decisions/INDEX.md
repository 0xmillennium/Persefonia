# Architecture Decision Index

This directory contains durable decision records for Persefonia.

A decision record is used only when a decision affects long-term maintainability, architecture, security, persistence, operations, testing, or documentation governance.

This directory is not a planning archive.

## What belongs here

- long-lived architecture decisions
- security model decisions
- persistence model decisions
- module boundary decisions
- documentation governance decisions
- decisions future maintainers must understand before changing the system

## What does not belong here

- roadmap notes
- feature planning
- private product planning
- temporary implementation notes
- execution reports
- command transcripts
- local scratch notes
- documentation-only migration placeholders

Temporary notes belong in `.local/TODO.md`.

## Lifecycle

Decision records are append-only by default.

If a decision changes, do not silently rewrite history. Create a new ADR and mark the old one as superseded.

Allowed statuses:

- Proposed
- Accepted
- Superseded
- Deprecated
- Rejected

## Template

Use [TEMPLATE.md](TEMPLATE.md) for new decision records.

## Current decisions

- [ADR 0001: Record Architecture Decisions](0001-record-architecture-decisions.md) — establishes ADRs as the durable decision mechanism.
- [ADR 0002: Keep Documentation Limited to Decision Records](0002-keep-documentation-limited-to-decision-records.md) — keeps committed documentation minimal until the project is mature enough for public maintainer guides.
- [ADR 0003: Use Local Ignored TODO for Temporary Notes](0003-use-local-ignored-todo-for-temporary-notes.md) — defines `.local/TODO.md` as the private, uncommitted place for immature notes.
- [ADR 0004: Use Modular Monolith with App Composition Root](0004-use-modular-monolith-with-app-composition-root.md) — keeps deployment simple while preserving internal boundaries.
- [ADR 0005: Keep Domain Modules Framework-free](0005-keep-domain-modules-framework-free.md) — protects domain code from framework and infrastructure coupling.
- [ADR 0006: Use Explicit SQL Migrations](0006-use-explicit-sql-migrations.md) — requires executable Flyway SQL migrations and forbids documentation-only migration placeholders.
- [ADR 0007: Use OIDC for Admin Authentication](0007-use-oidc-for-admin-authentication.md) — avoids local admin password handling and keeps local admin authority inside Persefonia.
- [ADR 0008: Require OWNER Authorization for Admin Mutations](0008-require-owner-authorization-for-admin-mutations.md) — requires application-layer authorization for state-changing admin commands.
- [ADR 0009: Keep Redis Auxiliary-only](0009-keep-redis-auxiliary-only.md) — prevents Redis from becoming durable domain or authorization state.
- [ADR 0010: Use Spring Data JDBC Through Persistence Adapters](0010-use-spring-data-jdbc-through-persistence-adapters.md) — permits Spring Data JDBC in app-level adapters while keeping Content Publishing domain code framework-free.
- [ADR 0011: Reserve Tag and Series Public Route Projections](0011-reserve-tag-and-series-public-route-projections.md) — reserves tag and series public route/projection shapes without implementing the feature surface.
- [ADR 0012: Constrain Public Navigation and Hreflang to Listed Public Content](0012-constrain-public-navigation-and-hreflang-to-listed-public-content.md) — keeps public relationship navigation and hreflang limited to listed public content.
- [ADR 0013: Use Discovery Eligibility for Public Index Surfaces](0013-use-discovery-eligibility-for-public-index-surfaces.md) — keeps dynamic search, sitemap, and feed inclusion driven by Discovery eligibility.
- [ADR 0014: Use PostgreSQL Full Text Search for Public Search](0014-use-postgresql-full-text-search-for-public-search.md) — chooses PostgreSQL full text search over Discovery search text and keeps search terms private.
- [ADR 0015: Publish Machine Readable Public Discovery Documents](0015-publish-machine-readable-public-discovery-documents.md) — defines sitemap, robots, and Atom feed publication rules.
- [ADR 0016: Use Post-Commit Operational Side Effects and Same-Transaction Audit Appends](0016-use-post-commit-operational-side-effects.md) — appends mandatory audit records in-transaction and runs failure-prone side effects like cache purge after commit, deferring a transactional outbox.
- [ADR 0017: Use Explicit Transactional Application Gateways for Admin Mutations](0017-use-explicit-transactional-admin-command-gateways.md) — places source-authorized admin mutations behind framework-free gateways implemented by transactional app adapters.
- [ADR 0018: Preserve Java 21 Compatibility Independently of the Development JDK](0018-preserve-java-21-compatibility-independently-of-development-jdk.md) — preserves Java 21 as the application compatibility baseline independently of the newer JDK used for development and build execution.
- [ADR 0019: Keep Cache Invalidation Outside Public-Exposure Correctness](0019-keep-cache-invalidation-outside-public-exposure-correctness.md) — keeps public-exposure correctness authoritative in domain and Discovery state rather than cache invalidation delivery.
- [ADR 0020: Treat Database and Asset Storage as One Recovery Unit](0020-treat-database-and-asset-storage-as-one-recovery-unit.md) — requires relational business state and durable binary asset storage to remain logically consistent as one recovery unit.
- [ADR 0021: Keep Operational Recovery Commands Outside a Single Use-Case Transaction](0021-keep-operational-recovery-commands-outside-a-single-use-case-transaction.md) — commits OWNER authorization and mandatory recovery-request Audit before coordinating independent cache reservation, provider, and result phases.
- [ADR 0022: Use an Immutable OCI Image as the Deployable Release Artifact](0022-use-an-immutable-oci-image-as-the-deployable-release-artifact.md) — makes the OCI digest the immutable deployment authority and requires same-artifact promotion through qualification and final release.
- [ADR 0023: Keep External Platform Services Outside the Persefonia Deployment Unit](0023-keep-external-platform-services-outside-the-persefonia-deployment-unit.md) — superseded by ADR 0028; records the historical deployment boundary with externally operated edge, identity, and mail services.
- [ADR 0024: Support AMD64 and ARM64 Under One OCI Image Index](0024-support-amd64-and-arm64-under-one-oci-image-index.md) — defines one multi-platform OCI index digest as deployment authority for the first-class AMD64 and ARM64 runtime variants.
- [ADR 0025: Deploy Qualified OCI Artifacts to the Single-Node Runtime over Authenticated SSH](0025-deploy-qualified-oci-artifacts-to-the-single-node-runtime-over-authenticated-ssh.md) — defines the authenticated deployment trust boundary and preserves host-owned runtime configuration and exact-digest artifact authority.

- [ADR 0026: Use OIDC Group Admission with Refresh-Backed Admin Session Revalidation](0026-use-oidc-group-admission-with-refresh-backed-admin-session-revalidation.md) — requires group admission and five-minute fresh UserInfo/local authorization revalidation while keeping OAuth credentials session-scoped.
- [ADR 0027: Require Explicit POSIX ACLs for Non-Root Compose File Secrets](0027-require-explicit-posix-acls-for-non-root-compose-file-secrets.md) — requires exact numeric UID read grants for file-backed secrets while preserving operator ownership and non-root containers.
- [ADR 0028: Separate Application Delivery from Host Runtime Topology](0028-separate-application-delivery-from-host-runtime-topology.md) — owns application artifacts/contracts and delivery orchestration while production topology, dependency-server lifecycle, durable state, secrets, and host preflight belong to the external runtime.
- [ADR 0029: Execute Repository-Owned RC Deployment Orchestration Ephemerally over Authenticated SSH](0029-execute-repository-owned-rc-deployment-orchestration-ephemerally-over-authenticated-ssh.md) — superseded by ADR 0030; records the historical streamed payload with direct runtime access.
- [ADR 0030: Route Deployment Runtime Operations Through a Narrow Host Privilege Gateway](0030-route-deployment-runtime-operations-through-a-narrow-host-privilege-gateway.md) — keeps repository-owned SSH orchestration while an externally owned, narrowly privileged gateway performs runtime operations.
- [ADR 0031: Separate Repository Automation Verification from Application Tests](0031-separate-repository-automation-verification-from-application-tests.md) — separates deterministic automation source checks and contracts from application verification and establishes a stable CI gate.
