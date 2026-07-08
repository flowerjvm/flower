# Contributing To Flower

Thanks for helping Flower become easier to use and easier to trust.

Flower is a small Java orchestration toolkit. Contributions should preserve the
core shape:

```text
Engine -> Worker -> Flow -> Step -> StepResult
```

## Development Setup

Use a JDK that can run the full build. JDK 17 is the default development
baseline because the Spring Boot starter uses Spring Boot 3.x. The core modules
compile with `--release 8` unless a module documents a newer requirement.

Use Maven 3.9.x for the reactor build:

```bash
mvn -B verify
```

The Gradle plugin is built separately after installing the checker artifacts
into your local Maven repository:

```bash
mvn -B -pl flower-check,flower-check-annotations -am install -DskipTests
gradle -p flower-check-gradle-plugin --no-daemon check
```

The Bloom integration adapter lives in the Bloom repository as
`bloom-flower-adapter`. The default Flower reactor must stay independent of
Bloom.

## Pull Request Expectations

Before opening a pull request:

- Run `mvn -B verify`.
- Keep API changes small and intentional.
- Add or update tests when behavior changes.
- Update README or module docs when public behavior changes.
- Keep `flower-core` free of optional framework dependencies.

Discuss larger changes first, especially:

- Worker scheduling or threading model changes.
- Step lifecycle or `StepResult` semantics.
- Persistence schema changes.
- New runtime dependencies in `flower-core`.
- New background executors, schedulers, or thread ownership rules.

## Design Rules

Worker ticks should stay short and non-blocking. Long waits should be modeled
as state, events, signals, timeouts, or durable external facts.

Orchestration should be visible in Flow and Step code. Avoid hiding workflow
progress inside ad-hoc scheduled jobs, callbacks, static flags, or service
methods that Flower cannot observe.

Use additional workers to separate execution classes, not as a substitute for
per-flow scheduling. A worker may tick many flows; a flow should express its own
waiting and retry behavior through steps.

Flower core is deliberately not BPMN, not a distributed scheduler, not a replay
engine, and not a saga framework. Keep those boundaries intact unless the
project explicitly changes scope.

