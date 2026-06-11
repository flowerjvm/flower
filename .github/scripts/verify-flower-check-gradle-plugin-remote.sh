#!/usr/bin/env bash
set -euo pipefail

: "${FLOWER_VERSION:?FLOWER_VERSION is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

export GITHUB_ACTOR="${GITHUB_ACTOR:-github-actions}"

work_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/flower-check-gradle-plugin-remote-smoke"
rm -rf "$work_dir"
mkdir -p "$work_dir/src/main/java/smoke"

cat > "$work_dir/settings.gradle.kts" <<'KOTLIN'
pluginManagement {
    repositories {
        gradlePluginPortal()
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/parkkevinsb/flower")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        mavenCentral()
        maven {
            url = uri("https://maven.pkg.github.com/parkkevinsb/flower")
            credentials {
                username = providers.environmentVariable("GITHUB_ACTOR").orNull
                password = providers.environmentVariable("GITHUB_TOKEN").orNull
            }
        }
    }
}

rootProject.name = "flower-check-gradle-plugin-remote-smoke"
KOTLIN

cat > "$work_dir/build.gradle.kts" <<KOTLIN
plugins {
    java
    id("io.github.parkkevinsb.flower.flower-check") version "$FLOWER_VERSION"
}
KOTLIN

cat > "$work_dir/src/main/java/smoke/RemoteSmoke.java" <<'JAVA'
package smoke;

public final class RemoteSmoke {
    private RemoteSmoke() {
    }

    public static String ok() {
        return "ok";
    }
}
JAVA

attempt=1
max_attempts=4
until gradle -p "$work_dir" --no-daemon --refresh-dependencies clean check --stacktrace; do
    if [ "$attempt" -ge "$max_attempts" ]; then
        exit 1
    fi

    echo "Remote Gradle plugin smoke failed on attempt $attempt; retrying after package metadata refresh."
    attempt=$((attempt + 1))
    sleep 20
done
