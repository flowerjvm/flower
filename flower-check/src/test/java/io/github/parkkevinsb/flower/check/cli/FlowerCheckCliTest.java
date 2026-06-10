package io.github.parkkevinsb.flower.check.cli;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class FlowerCheckCliTest {

    @Test
    void listRulesDoesNotRequireSourceRoot() {
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--list-rules"
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("flower-check rules:");
        assertThat(out.toString()).contains("FLOWER-CHECK-001");
        assertThat(out.toString()).contains("FLOWER-CHECK-016");
        assertThat(out.toString()).contains("opt-in");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void listRulesReflectsConfig(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Files.write(config, String.join("\n",
                "rules:",
                "  FLOWER-CHECK-001: off",
                "  FLOWER-CHECK-004: info",
                "agentRulesEnabled: true").getBytes(StandardCharsets.UTF_8));

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                "--list-rules"
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).containsPattern("FLOWER-CHECK-001\\s+ERROR\\s+disabled");
        assertThat(out.toString()).containsPattern("FLOWER-CHECK-004\\s+INFO\\s+enabled");
        assertThat(out.toString()).containsPattern("FLOWER-CHECK-006\\s+ERROR\\s+enabled");
        assertThat(out.toString()).contains("flower-check: 16 rules.");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void failOnOptionPreservesLoadedConfig(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Files.write(config, String.join("\n",
                "rules:",
                "  FLOWER-CHECK-001: off",
                "failOn: error").getBytes(StandardCharsets.UTF_8));
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                "--fail-on", "warning",
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).doesNotContain("FLOWER-CHECK-001");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void reportsBaselineAcceptedDebtWithoutFailing(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Files.write(root.resolve("flower-check-baseline.txt"),
                "FLOWER-CHECK-001 WaitStep.java:4\n".getBytes(StandardCharsets.UTF_8));
        Files.write(config, "baselineFile: flower-check-baseline.txt\n".getBytes(StandardCharsets.UTF_8));
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("accepted debt from baseline");
        assertThat(out.toString()).contains("BASELINE  FLOWER-CHECK-001");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void writesBaselineAndDoesNotFailOnCurrentFindings(@TempDir Path root) throws IOException {
        Path baseline = root.resolve("flower-check-baseline.txt");
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--write-baseline", baseline.toString(),
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("wrote 1 baseline finding");
        assertThat(new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8))
                .contains("FLOWER-CHECK-001 WaitStep.java:4");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void writeBaselineKeepsCurrentlyAcceptedDebt(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Path baseline = root.resolve("flower-check-baseline.txt");
        Files.write(baseline, "FLOWER-CHECK-001 WaitStep.java:4\n".getBytes(StandardCharsets.UTF_8));
        Files.write(config, "baselineFile: flower-check-baseline.txt\n".getBytes(StandardCharsets.UTF_8));
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                "--write-baseline", baseline.toString(),
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(out.toString()).contains("wrote 1 baseline finding");
        assertThat(new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8))
                .isEqualTo("FLOWER-CHECK-001 WaitStep.java:4\n");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void writeBaselineCanCreateConfiguredMissingBaselineFile(@TempDir Path root) throws IOException {
        Path config = root.resolve("flower-check.config");
        Path baseline = root.resolve("flower-check-baseline.txt");
        Files.write(config, "baselineFile: flower-check-baseline.txt\n".getBytes(StandardCharsets.UTF_8));
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--config", config.toString(),
                "--write-baseline", baseline.toString(),
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.OK);
        assertThat(new String(Files.readAllBytes(baseline), StandardCharsets.UTF_8))
                .isEqualTo("FLOWER-CHECK-001 WaitStep.java:4\n");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void formatOptionSelectsSarifReporter(@TempDir Path root) throws IOException {
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--format", "sarif",
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.FINDINGS);
        assertThat(out.toString()).contains("\"version\": \"2.1.0\"");
        assertThat(out.toString()).contains("\"ruleId\": \"FLOWER-CHECK-001\"");
        assertThat(out.toString()).doesNotContain("flower-check: 1 finding");
        assertThat(err.toString()).isEmpty();
    }

    @Test
    void invalidFormatIsUsageError(@TempDir Path root) {
        StringBuilder out = new StringBuilder();
        StringBuilder err = new StringBuilder();
        int code = new FlowerCheckCli().execute(new String[] {
                "--format", "xml",
                root.toString()
        }, out, err);

        assertThat(code).isEqualTo(ExitCode.USAGE);
        assertThat(err.toString()).contains("invalid report format");
    }

    private static void writeJava(Path root, String name, String... lines) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }
}
