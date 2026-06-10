# flower-check-maven-plugin

Maven integration for `flower-check`.

Add this plugin to a project that uses Flower when you want `mvn verify` to fail
if the project uses Flower in unsafe ways.

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
