# ADR 0005: Keep Domain Modules Framework-free

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | architecture |
| Supersedes | none |
| Superseded by | none |

## Context

Domain logic must remain testable, portable, and independent from framework and infrastructure concerns.

Framework dependencies inside domain modules make the model harder to reason about and harder to protect with architecture tests.

## Decision

Domain modules must not depend on:

- Spring
- Spring Security
- JDBC
- JPA
- Hibernate
- servlet APIs
- web templates

Domain modules may expose domain models, value objects, domain services, repository ports, and application-level contracts.

Framework and infrastructure integration belongs in adapter or composition modules.

This decision supports [ADR 0004: Use Modular Monolith with App Composition Root](0004-use-modular-monolith-with-app-composition-root.md).

## Consequences

Domain code stays easier to test and reason about.

Adapters must translate between framework types and domain/application contracts.

Architecture tests must protect this boundary.

Shortcuts that put Spring or persistence concerns into domain modules are not allowed.

## Alternatives considered

- Put Spring annotations directly in domain classes.
- Let domain modules use JDBC repositories directly.
- Allow framework dependencies whenever convenient.

These alternatives were rejected because they weaken the long-term boundary model.

## Review triggers

- A framework dependency is proposed for a domain module.
- Architecture tests require a boundary exception.
- The module model is redesigned.
