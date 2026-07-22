# flower-check-gradle-plugin

Gradle integration for `flower-check`.

Apply the plugin to a project that uses Flower when you want `gradle check` to
fail if the project uses Flower in unsafe ways.

`flower-check-gradle-plugin` is build-time tooling only. It does not add
anything to the host application's runtime classpath.

## Usage

Configure the plugin repository in `settings.gradle.kts`:

```kotlin
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
    }
}
```

Then apply the plugin:

```kotlin
plugins {
    java
    id("io.github.flowerjvm.flower-check") version "0.1.1"
}
```

Default behavior:

```text
./gradlew check
-> flowerCheck task scans main Java source roots
-> reports Flower usage findings
-> fails the build on active ERROR findings
```

Useful properties:

```bash
./gradlew check -Pflower.check.skip=true
./gradlew check -Pflower.check.config=flower-check.config
./gradlew check -Pflower.check.failOn=warning
./gradlew check -Pflower.check.includeTests=true
./gradlew check -Pflower.check.format=sarif -Pflower.check.outputFile=build/reports/flower-check.sarif
./gradlew check -Pflower.check.writeBaseline=flower-check-baseline.txt
```

Equivalent build script configuration:

```kotlin
flowerCheck {
    includeTests.set(false)
    configFile.set(layout.projectDirectory.file("flower-check.config"))
    failOn.set("warning")
    outputFile.set(layout.buildDirectory.file("reports/flower-check.sarif"))
    format.set("sarif")
}
```

## Building This Plugin

The Gradle plugin consumes the stable `flower-check` release from Maven
Central. When developing both builds together, install the Maven checker
artifacts locally first so the local build takes precedence:

```bash
mvn -pl flower-check,flower-check-annotations -am install -DskipTests
gradle -p flower-check-gradle-plugin --no-daemon check
```

Release publishing stages both the plugin implementation and its generated
plugin marker, signs them, and publishes them to Maven Central. Snapshot builds
continue to use GitHub Packages and are verified there by the repository
publish workflow.
