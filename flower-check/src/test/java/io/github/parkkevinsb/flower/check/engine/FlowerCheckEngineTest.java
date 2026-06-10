package io.github.parkkevinsb.flower.check.engine;

import io.github.parkkevinsb.flower.check.config.FlowerCheckConfig;
import io.github.parkkevinsb.flower.check.finding.Finding;
import io.github.parkkevinsb.flower.check.rule.Severity;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * End-to-end skeleton test: the pipeline loads a file, runs the registered
 * reference rule, and reports a finding. This is the harness Codex extends as
 * real rules land - one test per rule, plus the flower-sample baseline.
 */
class FlowerCheckEngineTest {

    @Test
    void reportsBlockingCallInsideStepLifecycleAndFails(@TempDir Path root) throws IOException {
        writeJava(root, "WaitStep.java",
                "package demo;",
                "class WaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        Thread.sleep(1000);",
                "        return StepResult.stay();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).hasSize(1);
        Finding finding = result.findings().get(0);
        assertThat(finding.ruleId()).isEqualTo("FLOWER-CHECK-001");
        assertThat(finding.line()).isEqualTo(4);
        assertThat(finding.what()).isNotEmpty();
        assertThat(finding.why()).isNotEmpty();
        assertThat(finding.fix()).isNotEmpty();
        assertThat(result.worstSeverity()).isEqualTo(Severity.ERROR);
        assertThat(result.failed()).isTrue();
    }

    @Test
    void ignoresBlockingCallOutsideStepLifecycle(@TempDir Path root) throws IOException {
        writeJava(root, "SampleMain.java",
                "package demo;",
                "class SampleMain {",
                "    public static void main(String[] args) throws Exception {",
                "        Thread.sleep(1000);",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
    }

    @Test
    void cleanStepPassesWithNoFindings(@TempDir Path root) throws IOException {
        writeJava(root, "CleanStep.java",
                "package demo;",
                "class CleanStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
    }

    private static void writeJava(Path root, String name, String... lines) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }
}
