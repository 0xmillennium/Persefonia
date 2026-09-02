# ADR 0018: Preserve Java 21 Compatibility Independently of the Development JDK

| Field | Value |
|---|---|
| Status | Accepted |
| Date | 2026-09-03 |
| Scope | architecture / runtime / compatibility |
| Supersedes | none |
| Superseded by | none |

## Context

Persefonia has an established Java 21 application compatibility baseline. Its normal development and build environment currently uses JDK 25 so that build tooling can evolve without requiring the minimum deployment environment to change at the same time.

The JDK executing a build and the minimum Java version supported by the application serve different purposes. Treating them as one version would allow a tooling upgrade to raise deployment requirements accidentally.

## Decision

Persefonia preserves Java 21 as its minimum Java compatibility baseline independently of the JDK used for development and build execution. Development and build tooling may use a newer supported JDK, while production code must not require Java language features or APIs newer than the declared compatibility baseline unless this decision is explicitly superseded.

A newer development JDK does not authorize production use of newer language features or JDK APIs. Production code must not depend on preview features or internal JDK APIs as application contracts.

The compatibility baseline may change only through an explicit future architecture decision. Its validation is therefore separate from merely checking which JDK executes the build.

## Consequences

Development tooling can advance independently of the minimum deployment environment.

Maintainers must distinguish successful execution on a newer JDK from compatibility with the declared baseline. Dependency upgrades must preserve that baseline as well as source compatibility.

Production code cannot take advantage of newer Java language features or APIs until the minimum compatibility decision changes, even when those capabilities are available to developers locally.

## Alternatives considered

- Keep the development JDK permanently equal to the compatibility baseline. Rejected because build-tool evolution and minimum application compatibility are separate concerns.
- Raise the compatibility baseline whenever the development JDK is upgraded. Rejected because it unnecessarily couples development tooling changes to deployment requirements.
- Allow newer APIs whenever the active development JDK provides them. Rejected because it makes the compatibility contract dependent on individual build environments and therefore unreliable.

## Review triggers

- The project intentionally raises its minimum supported Java version.
- Required framework or dependency choices no longer support Java 21.
- Production functionality requires a newer Java language or API baseline.
- The project intentionally adopts a different compatibility strategy.
