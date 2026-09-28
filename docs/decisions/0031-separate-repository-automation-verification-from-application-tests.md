# ADR 0031: Separate Repository Automation Verification from Application Tests

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-28 |
| Scope | architecture / testing / operations |
| Supersedes | none |
| Superseded by | none |

## Context

Repository workflows and deployment scripts have contracts that differ from application behavior. Keeping their tests in the application module couples repository policy to application test infrastructure and makes automation failures harder to identify before application verification starts.

## Decision

The `automation-tests` module owns repository automation, workflow, delivery, and deployment contracts. Application modules own application and use-case tests. Automation tests use a bounded process harness, parsed YAML 1.2 workflow documents, and executable shell and jq fixtures without starting an application context or requiring database, Redis, Docker, or host runtime services.

Static source verification is a separate layer. The repository locks actionlint and ShellCheck release artifacts by version and SHA-256, verifies downloads before extraction, then runs actionlint with inline ShellCheck, `bash -n`, and ShellCheck on tracked automation sources. Parsed workflow tests enforce repository-specific policy; executable tests enforce machine protocols and script behavior. Registry, native container, host, and public-ingress qualification remain integration and acceptance layers in the trusted delivery pipeline.

CI runs Automation before application Verify. Gate evaluates both job results and is the intended stable required check (`CI / Gate`). Delivery and RC deployment continue to consume only the qualified outputs of the existing CI and Delivery workflow chain. GitHub branch-protection settings remain external to this repository and must be verified through the GitHub interface or API.

## Consequences

Automation failures stop CI before application services and frontend tooling are started. The root `check` task includes both `automationCheck` and `applicationCheck`; each lifecycle can also run independently. Application tests no longer maintain workflow or script ownership policy. The automation module stays independent of application runtime dependencies, while real delivery and deployment qualification continue to exercise registry and host boundaries remotely.

## Alternatives considered

- Keep automation contracts in the application test suite. Rejected because repository policy would continue to require application build infrastructure and could not fail early.
- Rely only on static analysis. Rejected because static checks cannot establish deployment protocol behavior or repository-specific workflow policy.
- Rely only on executable fixtures. Rejected because fixtures cannot provide complete GitHub Actions syntax and shell analysis coverage.

## Review triggers

- GitHub Actions workflow semantics or repository deployment authority changes materially.
- Automation verification begins to require application runtime infrastructure.
- The CI required-check strategy changes.
- A new automation language or artifact type requires a different static-analysis layer.
