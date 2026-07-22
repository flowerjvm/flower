# Changelog

This project records notable changes here.

## Unreleased

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
