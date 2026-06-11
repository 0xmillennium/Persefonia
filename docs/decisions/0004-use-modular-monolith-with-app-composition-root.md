# ADR 0004: Use Modular Monolith with App Composition Root

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-06-11 |
| Scope | architecture |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia needs strong internal boundaries without the operational cost of distributed services.

The project should be easy to run, test, and deploy while still preventing feature code from collapsing into one unstructured application module.

## Decision

Persefonia is implemented as a modular monolith.

The application deploys as one Spring Boot application.

`:app` is the composition root. It wires framework configuration, security configuration, infrastructure adapters, and application startup.

Feature business logic must not drift into `:app`.

## Consequences

Deployment remains simple.

Internal module boundaries must be protected by build structure and architecture tests.

`:app` may depend on modules it composes, but other modules must not depend on `:app`.

Future service extraction remains possible only if a clear operational need appears.

## Alternatives considered

- Microservices from the beginning.
- A single unstructured application module.

Microservices were rejected as unnecessary operational complexity.

A single unstructured module was rejected because it weakens maintainability.

## Review triggers

- Independent deployment becomes necessary.
- Module boundaries can no longer be enforced in one process.
- Operational needs justify service extraction.
