# flower-check

`flower-check` is a Flower build-time developer tooling module.

It is not part of `flower-core` runtime execution. Its job is to inspect a
host application's source code and fail the build when generated or handwritten
Flower code violates known Flower patterns.

## Why This Exists

In the AI coding-agent era, users may ask an AI agent to implement Flower
flows, steps, AI harnesses, or agent-runtime actions.

Documentation and MCP guidance can help, but guidance is not enforcement.

`flower-check` is the enforcement layer:

```text
AI agent writes code
-> flower-check scans source
-> forbidden Flower patterns are found
-> build fails
-> bad workflow code cannot be merged or released
```

## Placement

`flower-check` lives inside the main `flower` repository.

Reason:

```text
flower-check depends on Flower API rules.
When Flower APIs change, checks should evolve in the same repository.
```

It may later publish as:

```text
flower-check-cli
flower-check-gradle-plugin
flower-check-maven-plugin
```

A compiling implementation now exists, so `flower-check` is registered in the
Maven reactor and runs during the module's `verify` phase. The design and
rules live in [`docs/`](docs) - start at
[`docs/00-INDEX.md`](docs/00-INDEX.md). Contributors (human or AI) must follow
[`AGENTS.md`](AGENTS.md) before writing code.

## First Scope

The first version should scan Java source and report violations with file and
line references.

Initial findings:

```text
FLOWER-CHECK-001
  Step must not call Thread.sleep or block the Worker thread.

FLOWER-CHECK-002
  Step must not call LLM/provider SDKs directly.
  Delegate model calls to an application service or higher-level integration
  layer and wake Flower through state, events, signals, or deadlines.

FLOWER-CHECK-003
  Flow code must not directly tick another Flow.
  Submit child flows through a Worker and wait through state/event checks.

FLOWER-CHECK-004
  Await/wait-style Step should have timeout or cancellation behavior.

FLOWER-CHECK-005
  Durable Flow Step should declare recovery policy.

FLOWER-CHECK-006
  Agent write action should not bypass ActionRegistry / PolicyGate.

FLOWER-CHECK-007
  Business write action should emit or require an audit event.

FLOWER-CHECK-008
  Approval-required action must not execute directly.
```

## Enforcement Path

CLI:

```bash
flower-check src/main/java
flower-check --write-baseline flower-check-baseline.txt src/main/java
flower-check --list-rules
```

Host application Maven build:

```xml
<dependency>
    <groupId>io.github.flowerjvm</groupId>
    <artifactId>flower-check-annotations</artifactId>
    <version>0.1.0</version>
    <scope>provided</scope>
</dependency>

<plugin>
    <groupId>io.github.flowerjvm</groupId>
    <artifactId>flower-check-maven-plugin</artifactId>
    <version>0.1.0</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Copyable host project templates live in [`templates/`](templates):

```text
flower-check.config              default host configuration
maven-plugin.xml                 dependency/plugin snippet for pom.xml
github-actions-flower-check.yml  pull-request workflow example
gradle-plugin-settings.gradle.kts
                                 Kotlin DSL plugin repository snippet
gradle-plugin-build.gradle.kts   Kotlin DSL plugin application snippet
gradle-plugin-settings.gradle    Groovy DSL plugin repository snippet
gradle-plugin-build.gradle       Groovy DSL plugin application snippet
gradle-build.gradle.kts          Kotlin DSL task snippet for build.gradle.kts
gradle-build.gradle              Groovy DSL task snippet for build.gradle
github-actions-flower-check-gradle.yml
                                 Gradle pull-request workflow example
```

Then:

```bash
mvn verify
mvn -Dflower.check.skip=true verify
./gradlew check
./gradlew -Pflower.check.skip=true check
```

This scans the host project's `src/main/java` by default. Any active finding at
or above `failOn` fails that host build.

The annotations dependency is optional unless the project needs an official
approval marker such as `@FlowerSchedulerApproved` for intentional recurring
schedulers. Projects may still configure their own approval annotation names.

CI:

```text
pull request
-> mvn verify
-> flower-check
-> tests
-> build
-> merge blocked on failure
```

## Relationship To Other Flower Tools

```text
Developer guidance tools
  = roadmap work for helping humans and coding agents design Flower flows.

flower-check
  = rejects known bad Flower code patterns.

flower-testkit
  = verifies Flow behavior deterministically.

CI
  = runs flower-check and tests automatically.

AI Reviewer
  = summarizes intent, risk, and check/test results for humans.
```

## Non-Goals

Do not make the first version:

```text
a general Java linter
a security scanner
a replacement for tests
a full static analyzer for all business logic
a dashboard
a hosted service
```

The first useful version should be small, boring, and strict.

## Implementation Notes

Current implementation:

```text
1. CLI scans Java source files.
2. JavaParser is primary; conservative text fallback remains.
3. Rules are discovered through ServiceLoader.
4. Plain text and SARIF reporters are available.
5. Existing findings can be written to a baseline file for controlled adoption.
6. Official source-retained check annotations are available for host projects.
7. The Maven plugin runs flower-check in host applications during `verify`.
8. The Flower reactor also self-checks its own source roots during `verify`.
9. Maven plugin behavior is covered by Invoker fixture projects that run real
   host `mvn verify` builds.
10. Gradle adoption snippets have opt-in smoke coverage that runs temporary
    host Gradle builds when `-Dflower.check.gradle.smoke=true` is set.
11. The dedicated Gradle plugin registers `flowerCheck`, wires it into `check`,
    and is covered by Gradle TestKit functional tests.
12. The publish workflow re-consumes the published Maven and Gradle plugins
    from GitHub Packages in temporary host projects before the snapshot is
    accepted.
```

Use a parser when rules need structure, such as identifying classes extending
`Step` or methods overriding `onTick`.

Avoid brittle rules that generate too many false positives. Each rule should
explain:

```text
what was found
why it is risky
what to do instead
```
