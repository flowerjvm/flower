# Flower Roadmap

This file tracks planned work and ideas that are not part of the current public
API contract. The README should describe what exists today; this file can
describe where the ecosystem may go next.

## Current Focus

- Keep `flower-core` small, explicit, and dependency-light.
- Make the public build reproducible for outside contributors.
- Publish artifacts through standard Java ecosystem channels.
- Provide examples that users can clone and run without reading the whole
  README first.

## Near-Term Work

- Move public artifacts from GitHub Packages to Maven Central.
- Publish the Gradle plugin through the Gradle Plugin Portal.
- Add a runnable `examples/` module that demonstrates Spring Boot, JDBC
  checkpoints, event delivery, and the internal console.
- Split long-form README material into focused docs under `docs/`.
- Add CI coverage for multiple JDKs, with special attention to Java 8
  compatibility for core modules.

## Developer Tooling Direction

`flower-check` exists today as build-time tooling. Future developer tooling may
help coding agents and humans create Flower code that follows the same rules
before code reaches review.

Possible future work includes:

- Agent-installable Flower usage skills and templates.
- MCP tools that expose Flower concepts, examples, and checks to development
  agents.
- Stronger static checks for blocking calls, hidden schedulers, worker misuse,
  and unclear Flow / Step boundaries.

These tools should guide Flower usage. They should not add AI or model runtime
behavior to `flower-core`.

## Runtime Integration Direction

Higher-level runtimes may eventually wrap Flower with policy, approval,
auditing, tool access, or agent/action boundaries. Those layers belong outside
`flower-core` unless a small, general-purpose API proves necessary.

Possible future work includes:

- Controlled action execution layers.
- Secure MCP/tool gateways for application actions.
- Integration patterns for LLM or external tool responses as ordinary Flower
  events, signals, durable facts, or deadlines.

## Non-Goals For Flower Core

`flower-core` should remain outside these scopes:

- BPMN modeling.
- Distributed workflow execution.
- Durable replay engines.
- Saga frameworks.
- LLM or agent runtimes.
- General-purpose job scheduling platforms.

