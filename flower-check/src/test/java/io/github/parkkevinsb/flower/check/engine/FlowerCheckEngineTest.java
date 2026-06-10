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
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
    void reportsBlockingCallInsidePrivateHelperReachedFromLifecycle(@TempDir Path root) throws IOException {
        writeJava(root, "HelperWaitStep.java",
                "package demo;",
                "class HelperWaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        waitForExternalWork();",
                "        return StepResult.done();",
                "    }",
                "    private void waitForExternalWork() throws Exception {",
                "        Thread.sleep(1000);",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("FLOWER-CHECK-001");
        assertThat(result.findings().get(0).line()).isEqualTo(8);
    }

    @Test
    void reportsWaitJoinFutureGetAndBusyWaitInsideLifecycle(@TempDir Path root) throws IOException {
        writeJava(root, "BlockingShapesStep.java",
                "package demo;",
                "class BlockingShapesStep extends Step {",
                "    private Object lock;",
                "    private Thread worker;",
                "    private java.util.concurrent.Future<?> future;",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        lock.wait();",
                "        worker.join();",
                "        future.get();",
                "        while (!future.isDone()) { }",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings())
                .extracting(Finding::ruleId)
                .containsOnly("FLOWER-CHECK-001");
        assertThat(result.findings()).hasSize(4);
    }

    @Test
    void allowsFutureGetWithTimeout(@TempDir Path root) throws IOException {
        writeJava(root, "TimedFutureStep.java",
                "package demo;",
                "class TimedFutureStep extends Step {",
                "    private java.util.concurrent.Future<?> future;",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        future.get(1, java.util.concurrent.TimeUnit.SECONDS);",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
    }

    @Test
    void ignoresBlockingCallInConstructorOutsideStepLifecycle(@TempDir Path root) throws IOException {
        writeJava(root, "ConstructorWaitStep.java",
                "package demo;",
                "class ConstructorWaitStep extends Step {",
                "    ConstructorWaitStep() throws Exception {",
                "        Thread.sleep(1000);",
                "    }",
                "    protected StepResult onTick(StepContext ctx) {",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
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
    void suppressesFindingOnFollowingLineWhenReasonIsPresent(@TempDir Path root) throws IOException {
        writeJava(root, "SuppressedWaitStep.java",
                "package demo;",
                "class SuppressedWaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        // flower-check:ignore FLOWER-CHECK-001 reason: legacy API blocks briefly",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
    }

    @Test
    void suppressesFindingOnSameLineWhenReasonIsPresent(@TempDir Path root) throws IOException {
        writeJava(root, "InlineSuppressedWaitStep.java",
                "package demo;",
                "class InlineSuppressedWaitStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        Thread.sleep(1000); // flower-check:ignore FLOWER-CHECK-001 reason: test fixture",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
        assertThat(result.failed()).isFalse();
    }

    @Test
    void doesNotSuppressDifferentRule(@TempDir Path root) throws IOException {
        writeJava(root, "WrongRuleSuppressionStep.java",
                "package demo;",
                "class WrongRuleSuppressionStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        // flower-check:ignore FLOWER-CHECK-002 reason: not the blocking-call rule",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).hasSize(1);
        assertThat(result.findings().get(0).ruleId()).isEqualTo("FLOWER-CHECK-001");
        assertThat(result.failed()).isTrue();
    }

    @Test
    void rejectsSuppressionWithoutReason(@TempDir Path root) throws IOException {
        writeJava(root, "BadSuppressionStep.java",
                "package demo;",
                "class BadSuppressionStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) throws Exception {",
                "        // flower-check:ignore FLOWER-CHECK-001",
                "        Thread.sleep(1000);",
                "        return StepResult.done();",
                "    }",
                "}");

        assertThatThrownBy(() -> FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid flower-check suppression");
    }

    @Test
    void ignoresSuppressionSyntaxMentionedOutsideLineComment(@TempDir Path root) throws IOException {
        writeJava(root, "SuppressionDocsStep.java",
                "package demo;",
                "/** Mention // flower-check:ignore FLOWER-CHECK-001 without making a suppression. */",
                "class SuppressionDocsStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        return StepResult.done();",
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
