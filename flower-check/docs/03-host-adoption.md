---
doc_level: L3
status: active
authority: host-adoption
depends_on:
  - 00-INDEX.md
  - 01-architecture.md
  - 02-rule-catalog.md
supersedes: []
last_reviewed: 2026-06-11
---

# 03. Host Project Adoption

This document defines the supported first adoption path for projects that use
Flower. `flower-check` remains build-time tooling: host projects add the Maven
plugin, optionally add source-retained check annotations, and run `mvn verify`
in CI.

## Files To Copy

Use the templates under `../templates/`:

```text
flower-check.config              default host configuration
maven-plugin.xml                 dependency/plugin snippet for pom.xml
github-actions-flower-check.yml  pull-request workflow example
gradle-build.gradle.kts          Kotlin DSL task snippet for build.gradle.kts
gradle-build.gradle              Groovy DSL task snippet for build.gradle
github-actions-flower-check-gradle.yml
                                 Gradle pull-request workflow example
```

The templates are intentionally small and reviewable. They must not require a
runtime dependency on `flower-core`, and they must keep strict defaults:

```text
failOn: error
agentRulesEnabled: false
```

Agent rules stay opt-in because not every Flower host application has an
agent/action layer. Scheduler approval stays default-on because recurring
schedulers are a common AI escape hatch and should require explicit approval.

## Maven Adoption

Add the contents of `templates/maven-plugin.xml` to the host `pom.xml`.

The Maven plugin runs during `verify`, scans `src/main/java` by default, and
fails the build when active findings meet or exceed `failOn`.

Host projects that use recurring schedulers intentionally should also keep the
`flower-check-annotations` dependency from the template and mark the approved
site:

```java
@FlowerSchedulerApproved(reason = "User approved periodic partner reconciliation")
```

The annotation is SOURCE-retained. It documents approval for source analysis
and has no runtime behavior.

## Gradle Adoption

Until a dedicated Gradle plugin module exists, Gradle host projects should wire
the `flower-check` CLI jar into the normal `check` lifecycle. Copy the matching
snippet into the host build file:

```text
templates/gradle-build.gradle.kts  -> build.gradle.kts
templates/gradle-build.gradle      -> build.gradle
```

The snippets create a detached `flowerCheck` configuration, add the
`flower-check` CLI jar, add `flower-check-annotations` as `compileOnly`, and
register a `flowerCheck` `JavaExec` task. The task scans `src/main/java` and is
attached to `check`, so violations fail the normal Gradle build:

```bash
./gradlew check
```

Temporary local bypasses must stay explicit:

```bash
./gradlew -Pflower.check.skip=true check
```

For controlled debt migration, generate a reviewed baseline with:

```bash
./gradlew -Pflower.check.writeBaseline=flower-check-baseline.txt check
```

## CI Adoption

Copy `templates/github-actions-flower-check.yml` to:

```text
.github/workflows/flower-check.yml
```

The workflow runs `mvn -B verify` for pull requests. Because the Maven plugin is
bound to `verify`, Flower usage violations block the pull request through the
normal Maven build.

If the host project consumes Flower snapshots from GitHub Packages, the
workflow must provide package read credentials in Maven `settings.xml`. The
template uses `GITHUB_TOKEN` for the common case. Cross-repository/private
package access may require a repository secret with `read:packages`.

For Gradle projects, copy `templates/github-actions-flower-check-gradle.yml`
instead, usually to `.github/workflows/flower-check.yml`. The Gradle workflow
runs `./gradlew --no-daemon check`, and the build script snippet reads
`GITHUB_ACTOR` / `GITHUB_TOKEN` when resolving Flower artifacts from GitHub
Packages.

## Existing Debt

For a host project that already has findings, use one controlled adoption pass:

```bash
mvn -Dflower.check.writeBaseline=flower-check-baseline.txt verify
```

Commit the generated baseline only after reviewing every entry. Normal CI runs
then use:

```text
baselineFile: flower-check-baseline.txt
```

in `flower-check.config`. Baselined findings are accepted debt; new findings
still fail.

Prefer baselines for migration debt, inline suppressions for one justified
source site, and rule disablement only when a rule truly does not apply to the
project.
