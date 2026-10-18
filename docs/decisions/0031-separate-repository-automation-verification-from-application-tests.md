# ADR 0031: Separate Repository Automation Verification from Application Tests

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / testing / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Repository workflows and deployment scripts have contracts that differ from application behavior. Keeping their tests inside the application module couples repository policy to application test infrastructure and delays automation failure detection until application verification runs.

## Decision

The `:automation-tests` module owns repository workflow, delivery, and deployment script contracts, while `:app` and domain modules own application and use-case tests. Automation tests use a bounded process harness, parsed YAML 1.2 workflows, and shell/jq fixtures without starting Spring or requiring PostgreSQL, Redis, or Docker.

Static shell and workflow verification runs as a dedicated layer using version- and SHA-256-pinned `actionlint` and `ShellCheck` binaries alongside `bash -n` on tracked automation sources.

In CI, `Automation` runs before `Verify`. Browser qualification follows `Verify` in an isolated viewport/engine matrix consuming the checksum-verified BootJar without rebuilding the application. `CI / Gate` aggregates `Automation`, `Verify`, and the browser matrix as the required status check.

## Consequences

Automation defects fail fast in CI before application services or browser engines start, and `:automation-tests` remains free of runtime infrastructure dependencies.

The root `check` task runs both `automationCheck` and `applicationCheck`, while each verification suite can also execute independently.

## Alternatives considered

- Keep workflow and script contracts in `:app` tests. Rejected because repository policy checks should not require application build and database infrastructure.
- Rely only on static analysis (`actionlint` / `ShellCheck`). Rejected because static linting cannot verify deployment protocol state machines or repository-specific workflow policy.
- Rely only on executable script fixtures. Rejected because fixtures alone do not provide comprehensive GitHub Actions YAML and shell static analysis.

## Review triggers

- GitHub Actions workflow semantics or deployment orchestration boundaries change materially.
- Automation verification begins requiring live container or application runtime infrastructure.
- The CI `Gate` required-check strategy changes.
