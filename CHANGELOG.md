# Changelog

This project records notable changes here.

## Unreleased

## 0.1.3

- Fixed Flow definition aliasing so builder reuse cannot mutate an already
  built Flow, and rejected nested manual Worker ticks that could execute
  sibling Flows and durable checkpoint effects twice.
- Made `GoToMode.COMPLETE_CURRENT` an explicit runtime contract: Flower runs
  the current Step's exit lifecycle and cleanup before moving to the target,
  and fails closed for unsupported future modes.
- Preserved checkpoint-relevant event-loop transition state and documented
  durability boundaries for event publication and checkpoint failures.
- Made `flower-check` report parser fallback diagnostics, added strict
  baseline controls to its CLI, Maven plugin, and Gradle plugin, and improved
  configuration and remediation documentation.
- Fixed async trace and observation shutdown so closing a sink cannot
  interrupt and lose an in-flight storage write.
- Added Java 8 runtime CI for compatible modules, documented the separate
  Java requirements for core and the Spring Boot starter, enabled
  reproducible build timestamps, and packaged LICENSE and NOTICE metadata.

## 0.1.2

- Added payload-light runtime tracing across ordinary and event-loop flows,
  including waits, recovery, checkpoints, transition outcomes, and stable run
  and Step-attempt identities.
- Added local trace storage, sanitization, sampling, artifact handling,
  OpenTelemetry export, and common domain-observation adapters.
- Added the independent `flower-evaluation` module for datasets, experiments,
  deterministic evaluators, aggregate results, and result sinks.
- Linked the Spring Boot console to the opt-in local Flower Flow Graph tool.
- Extracted the read-only Flower Studio application into its own repository.

## 0.1.1

- Added SQLite checkpoint dialects and schema SQL for core and event-loop
  durable flows.
- Added Spring Boot starter selection with
  `flower.persistence.jdbc.dialect=sqlite`.
- Added real SQLite file integration tests, including shared host tables,
  checkpoint upsert/recovery, event awaits, and terminal tombstones.
- Extended `flower-check` to understand EventFlow, EventStep, and Guard
  execution callbacks.
- Added `FLOWER-CHECK-017` through `FLOWER-CHECK-019` for Guard side effects,
  missing event-await deadlines, and non-recoverable durable EventStep waits.
- Added Maven Central publication support for the Flower Check Gradle plugin.
- Refined blocking and async-boundary detection in `flower-check`.
- Kept runtime core APIs compatible with `0.1.0` applications.

## 0.1.0

- Moved Bloom integration ownership to the Bloom repository as
  `bloom-flower-adapter`.
- Kept the default Flower reactor independent of Bloom.
- Updated repository metadata for the `flowerjvm/flower` organization.
- Added public contribution, security, conduct, and roadmap documents.
- Initial development line for `flower-core`, JDBC persistence, Spring Boot
  integration, observability, testkit, event loop modules, and flower-check
  tooling.
