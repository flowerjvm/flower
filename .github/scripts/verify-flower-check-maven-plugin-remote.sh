#!/usr/bin/env bash
set -euo pipefail

: "${FLOWER_VERSION:?FLOWER_VERSION is required}"
: "${GITHUB_TOKEN:?GITHUB_TOKEN is required}"

export GITHUB_ACTOR="${GITHUB_ACTOR:-github-actions}"

work_dir="${RUNNER_TEMP:-${TMPDIR:-/tmp}}/flower-check-maven-plugin-remote-smoke"
rm -rf "$work_dir"
mkdir -p "$work_dir/src/main/java/smoke"

cat > "$work_dir/settings.xml" <<'XML'
<settings xmlns="http://maven.apache.org/SETTINGS/1.0.0"
          xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
          xsi:schemaLocation="http://maven.apache.org/SETTINGS/1.0.0 https://maven.apache.org/xsd/settings-1.0.0.xsd">
    <servers>
        <server>
            <id>github</id>
            <username>${env.GITHUB_ACTOR}</username>
            <password>${env.GITHUB_TOKEN}</password>
        </server>
    </servers>
</settings>
XML

cat > "$work_dir/pom.xml" <<XML
<?xml version="1.0" encoding="UTF-8"?>
<project xmlns="http://maven.apache.org/POM/4.0.0"
         xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
         xsi:schemaLocation="http://maven.apache.org/POM/4.0.0 https://maven.apache.org/xsd/maven-4.0.0.xsd">
    <modelVersion>4.0.0</modelVersion>

    <groupId>smoke</groupId>
    <artifactId>flower-check-maven-plugin-remote-smoke</artifactId>
    <version>1.0.0-SNAPSHOT</version>

    <properties>
        <maven.compiler.release>8</maven.compiler.release>
        <project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>
    </properties>

    <repositories>
        <repository>
            <id>github</id>
            <url>https://maven.pkg.github.com/flowerjvm/flower</url>
        </repository>
    </repositories>

    <pluginRepositories>
        <pluginRepository>
            <id>github</id>
            <url>https://maven.pkg.github.com/flowerjvm/flower</url>
        </pluginRepository>
    </pluginRepositories>

    <build>
        <plugins>
            <plugin>
                <groupId>org.apache.maven.plugins</groupId>
                <artifactId>maven-compiler-plugin</artifactId>
                <version>3.13.0</version>
            </plugin>
            <plugin>
                <groupId>io.github.flowerjvm</groupId>
                <artifactId>flower-check-maven-plugin</artifactId>
                <version>$FLOWER_VERSION</version>
                <executions>
                    <execution>
                        <goals>
                            <goal>check</goal>
                        </goals>
                    </execution>
                </executions>
            </plugin>
        </plugins>
    </build>
</project>
XML

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
until mvn -B -s "$work_dir/settings.xml" -Dmaven.repo.local="$work_dir/repository" -U -f "$work_dir/pom.xml" verify; do
    if [ "$attempt" -ge "$max_attempts" ]; then
        exit 1
    fi

    echo "Remote Maven plugin smoke failed on attempt $attempt; retrying after package metadata refresh."
    attempt=$((attempt + 1))
    sleep 20
done
