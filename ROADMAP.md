# Flower Roadmap

This file tracks planned work and ideas that are not part of the current public
API contract. The README should describe what exists today; this file can
describe where the ecosystem may go next.

## Current Focus

- Keep `flower-core` small, explicit, and dependency-light.
- Make the public build reproducible for outside contributors.
- Keep Maven Central and plugin publication reliable.
- Maintain examples that users can clone and run without reading the whole
  README first.

## Near-Term Work

- Publish the Gradle plugin through the Gradle Plugin Portal.
- Expand runnable sample repositories for Spring Boot, JDBC checkpoints, event
  delivery, and the internal console.
- Split long-form README material into focused docs under `docs/`.
- Add CI coverage for multiple JDKs, with special attention to Java 8
  compatibility for core modules.

## Developer Tooling Direction

`flower-check` exists today as build-time tooling, and the Flower plugin makes
Flower guidance available to coding agents before code reaches review. Future
developer tooling may deepen that integration.

Possible future work includes:

- Expand the existing Flower skills and templates with more application
  patterns.
- MCP tools that expose Flower concepts, examples, and checks to development
  agents.
- Stronger static checks for blocking calls, hidden schedulers, worker misuse,
  and unclear Flow / Step boundaries.

These tools should guide Flower usage. They should not add AI or model runtime
behavior to `flower-core`.

## Runtime Integration Direction

Higher-level runtimes already wrap Flower with agent loops, structured AI task
validation, policy, approval, auditing, and governed tool access. These
responsibilities live in
[Flower Agent](https://github.com/flowerjvm/flower-agent),
[Flower AI Harness](https://github.com/flowerjvm/flower-ai-harness), and
[Flower Action Runtime](https://github.com/flowerjvm/flower-action-runtime).
They should remain outside `flower-core` unless a small, general-purpose API
proves necessary.

Possible future work includes:

- Secure MCP/tool gateways for application actions.
- More documented integration patterns for LLM or external tool responses as
  ordinary Flower events, signals, durable facts, or deadlines.

## Worker Scheduling Direction

Today, each tick-driven `Worker` uses a `ScheduledExecutorService` and wakes at
its configured `intervalMillis` to run one short tick. This is intentionally
simple and deterministic, and it keeps Worker behavior easy to test with
`tickOnce()` and `ManualClock`.

Deployments with many mostly-idle Workers may eventually benefit from an
event-driven scheduler that wakes a Worker only when useful work is possible,
such as a submission, cancellation, event, signal, or deadline. This would be
an optimization of the existing Worker line, not a replacement for the
separate `flower-eventloop` runtime and not a change to the Step contract.

This direction should be pursued only after measurements show idle Worker
wakeups are a meaningful cost. Until then, tune `intervalMillis` per Worker and
keep each tick short and non-blocking.

## Non-Goals For Flower Core

`flower-core` should remain outside these scopes:

- BPMN modeling.
- Distributed workflow execution.
- Durable replay engines.
- Saga frameworks.
- LLM or agent runtimes.
- General-purpose job scheduling platforms.
