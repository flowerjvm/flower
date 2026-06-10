# flower-check-maven-plugin

Maven integration for `flower-check`.

Add this plugin to a project that uses Flower when you want `mvn verify` to fail
if the project uses Flower in unsafe ways.

Use the official source-retained approval annotation when a recurring scheduler
is truly intentional:

```xml
<dependency>
    <groupId>io.github.parkkevinsb.flower</groupId>
    <artifactId>flower-check-annotations</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <scope>provided</scope>
</dependency>
```

```xml
<plugin>
    <groupId>io.github.parkkevinsb.flower</groupId>
    <artifactId>flower-check-maven-plugin</artifactId>
    <version>0.1.0-SNAPSHOT</version>
    <executions>
        <execution>
            <goals>
                <goal>check</goal>
            </goals>
        </execution>
    </executions>
</plugin>
```

Default behavior:

```text
mvn verify
-> scans src/main/java
-> reports Flower usage findings
-> fails the build on active ERROR findings
```

Useful properties:

```bash
mvn verify -Dflower.check.skip=true
mvn verify -Dflower.check.config=flower-check.config
mvn verify -Dflower.check.failOn=warning
mvn verify -Dflower.check.includeTests=true
mvn verify -Dflower.check.format=sarif -Dflower.check.outputFile=target/flower-check.sarif
mvn verify -Dflower.check.writeBaseline=flower-check-baseline.txt
```

`flower.check.writeBaseline` records current findings and exits successfully so
a project can adopt the checker without fixing all existing debt at once.

Integration coverage:

```bash
mvn -pl flower-check-maven-plugin -am verify
```

The plugin runs Maven Invoker fixture projects that prove `mvn verify` succeeds
for clean host code, fails for unsafe Flower Step blocking, accepts an explicit
baseline, and accepts the official scheduler approval annotation.

Scheduler approval example:

```java
import io.github.parkkevinsb.flower.check.annotation.FlowerSchedulerApproved;
import org.springframework.scheduling.annotation.Scheduled;

class ReconciliationJob {

    @FlowerSchedulerApproved(
        reason = "User approved periodic partner reconciliation outside a Flower flow",
        approvedBy = "ops-owner",
        approvedAt = "2026-06-11",
        reference = "OPS-1234"
    )
    @Scheduled(fixedDelay = 60000)
    void reconcile() {
    }
}
```
