package io.github.flowerjvm.flower.check.engine;

import io.github.flowerjvm.flower.check.config.FlowerCheckConfig;
import io.github.flowerjvm.flower.check.finding.Finding;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Collections;

import static org.assertj.core.api.Assertions.assertThat;

class RuleCatalogTest {

    @Test
    void detectsProviderCallInsideStep(@TempDir Path root) throws IOException {
        writeJava(root, "BadProviderStep.java",
                "package demo;",
                "class BadProviderStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        new OpenAIClient().chat();",
                "        return StepResult.done();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-002");
    }

    @Test
    void detectsDirectFlowDrivingInsideStep(@TempDir Path root) throws IOException {
        writeJava(root, "BadDriveStep.java",
                "package demo;",
                "class BadDriveStep extends Step {",
                "    private Worker worker;",
                "    protected StepResult onTick(StepContext ctx) {",
                "        worker.tickOnce();",
                "        return StepResult.done();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-003");
    }

    @Test
    void detectsSignalWaitWithoutTimeoutWhenFiniteWaitIsObvious(@TempDir Path root) throws IOException {
        writeJava(root, "PaymentApprovalStep.java",
                "package demo;",
                "class PaymentApprovalStep extends Step {",
                "    protected void onEnter(StepContext ctx) {",
                "        ctx.subscribe(PaymentApproved.class, event -> ctx.signal(\"paid\"));",
                "    }",
                "    protected StepResult onTick(StepContext ctx) {",
                "        return ctx.hasSignal(\"paid\") ? StepResult.done() : StepResult.stay();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-004");
    }

    @Test
    void detectsDurableStepWithoutRecoveryPolicy(@TempDir Path root) throws IOException {
        writeJava(root, "BadDurableFlow.java",
                "package demo;",
                "class PlainStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}",
                "class BadDurableFlow {",
                "    Flow create() {",
                "        return Flow.builder(\"order\", \"1\")",
                "                .durable()",
                "                .step(\"plain\", new PlainStep())",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-005");
    }

    @Test
    void allowsDurableStepPolicyResolvedFromStepType(@TempDir Path root) throws IOException {
        writeJava(root, "GoodDurableFlow.java",
                "package demo;",
                "class RecoverableStep extends DurableStep {",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}",
                "class GoodDurableFlow {",
                "    Flow create() {",
                "        return Flow.builder(\"order\", \"1\")",
                "                .durable()",
                "                .step(\"recoverable\", new RecoverableStep())",
                "                .build();",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void detectsUnknownGoToTarget(@TempDir Path root) throws IOException {
        writeJava(root, "BadGoTo.java",
                "package demo;",
                "class JumpStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        return StepResult.goTo(\"missing\");",
                "    }",
                "}",
                "class BadGoTo {",
                "    Flow create() {",
                "        return Flow.builder(\"order\", \"1\")",
                "                .step(\"start\", new JumpStep())",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-009");
    }

    @Test
    void detectsControlDecisionInsideSubscribeCallback(@TempDir Path root) throws IOException {
        writeJava(root, "BadCallbackStep.java",
                "package demo;",
                "class BadCallbackStep extends Step {",
                "    protected void onEnter(StepContext ctx) {",
                "        ctx.subscribe(Event.class, event -> ctx.setStepNo(10));",
                "    }",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-010");
    }

    @Test
    void detectsStepOwningRuntime(@TempDir Path root) throws IOException {
        writeJava(root, "RuntimeOwnerStep.java",
                "package demo;",
                "class RuntimeOwnerStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        Engine.builder().build();",
                "        return StepResult.done();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-011");
    }

    @Test
    void detectsRawEventBusSubscriptionInsideStep(@TempDir Path root) throws IOException {
        writeJava(root, "RawSubStep.java",
                "package demo;",
                "class RawSubStep extends Step {",
                "    protected void onEnter(StepContext ctx) {",
                "        ctx.eventBus().subscribe(Event.class, event -> ctx.signal(\"x\"));",
                "    }",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-012");
    }

    @Test
    void detectsDuplicateStepIds(@TempDir Path root) throws IOException {
        writeJava(root, "DuplicateSteps.java",
                "package demo;",
                "class AStep extends Step { protected StepResult onTick(StepContext ctx) { return StepResult.done(); } }",
                "class BStep extends Step { protected StepResult onTick(StepContext ctx) { return StepResult.done(); } }",
                "class DuplicateSteps {",
                "    Flow create() {",
                "        return Flow.builder(\"order\", \"1\")",
                "                .step(\"same\", new AStep())",
                "                .step(\"same\", new BStep())",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-013");
    }

    @Test
    void detectsBusinessUseOfExecutionContext(@TempDir Path root) throws IOException {
        writeJava(root, "BusinessContextStep.java",
                "package demo;",
                "class BusinessContextStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) {",
                "        ctx.executionContext().approvalState();",
                "        return StepResult.done();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-014");
    }

    @Test
    void detectsSharedStepInstance(@TempDir Path root) throws IOException {
        writeJava(root, "SharedStepFactory.java",
                "package demo;",
                "class SharedStep extends Step { protected StepResult onTick(StepContext ctx) { return StepResult.done(); } }",
                "class SharedStepFactory {",
                "    Flow create() {",
                "        Step shared = new SharedStep();",
                "        return Flow.builder(\"order\", \"1\")",
                "                .step(\"a\", shared)",
                "                .step(\"b\", shared)",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-015");
    }

    @Test
    void detectsSpringScheduledMethodWithoutApproval(@TempDir Path root) throws IOException {
        writeJava(root, "ScheduledCleanup.java",
                "package demo;",
                "@interface Scheduled { long fixedRate() default 0L; }",
                "class ScheduledCleanup {",
                "    @Scheduled(fixedRate = 1000L)",
                "    void run() { }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-016");
    }

    @Test
    void allowsScheduledMethodWithDefaultApprovalAnnotation(@TempDir Path root) throws IOException {
        writeJava(root, "ApprovedScheduledCleanup.java",
                "package demo;",
                "import io.github.flowerjvm.flower.check.annotation.FlowerSchedulerApproved;",
                "@interface Scheduled { long fixedRate() default 0L; }",
                "class ApprovedScheduledCleanup {",
                "    @FlowerSchedulerApproved(reason = \"User approved the recurring cleanup scheduler\")",
                "    @Scheduled(fixedRate = 1000L)",
                "    void run() { }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void detectsRecurringSchedulerApiWithoutApproval(@TempDir Path root) throws IOException {
        writeJava(root, "PollingJob.java",
                "package demo;",
                "class PollingJob {",
                "    void start(java.util.concurrent.ScheduledExecutorService scheduler) {",
                "        scheduler.scheduleAtFixedRate(() -> { }, 0L, 1L, java.util.concurrent.TimeUnit.SECONDS);",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-016");
    }

    @Test
    void allowsRecurringSchedulerApiWithConfiguredApprovalAnnotation(@TempDir Path root) throws IOException {
        writeJava(root, "ApprovedPollingJob.java",
                "package demo;",
                "@interface ProjectSchedulerApproved { }",
                "@ProjectSchedulerApproved",
                "class ApprovedPollingJob {",
                "    void start(java.util.concurrent.ScheduledExecutorService scheduler) {",
                "        scheduler.scheduleWithFixedDelay(() -> { }, 0L, 1L, java.util.concurrent.TimeUnit.SECONDS);",
                "    }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.builder()
                        .addSchedulerApprovalAnnotation("ProjectSchedulerApproved")
                        .build())
                .run(Collections.singletonList(root.toString()));

        assertThat(result.findings()).isEmpty();
    }

    @Test
    void allowsOneShotTaskSchedulerScheduleWithoutApproval(@TempDir Path root) throws IOException {
        writeJava(root, "PartnerDelay.java",
                "package demo;",
                "class PartnerDelay {",
                "    void delay(TaskScheduler scheduler) {",
                "        scheduler.schedule(() -> { }, java.time.Instant.now());",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void agentRulesAreOptIn(@TempDir Path root) throws IOException {
        writeJava(root, "DeleteAction.java",
                "package demo;",
                "class DeleteAction {",
                "    private Repository repository;",
                "    void run() { repository.delete(); }",
                "}");

        assertThat(run(root).findings()).isEmpty();

        CheckResult optedIn = FlowerCheckEngine.create(FlowerCheckConfig.builder()
                        .agentRulesEnabled(true)
                        .build())
                .run(Collections.singletonList(root.toString()));

        assertHasRule(optedIn, "FLOWER-CHECK-006");
        assertHasRule(optedIn, "FLOWER-CHECK-007");
    }

    @Test
    void detectsApprovalRequiredDirectExecutionWhenAgentRulesAreEnabled(@TempDir Path root) throws IOException {
        writeJava(root, "ApprovalRequiredAction.java",
                "package demo;",
                "class ApprovalRequiredAction {",
                "    private Repository repository;",
                "    void run() { repository.update(); }",
                "}");

        CheckResult result = FlowerCheckEngine.create(FlowerCheckConfig.builder()
                        .agentRulesEnabled(true)
                        .build())
                .run(Collections.singletonList(root.toString()));

        assertHasRule(result, "FLOWER-CHECK-008");
    }

    private static CheckResult run(Path root) {
        return FlowerCheckEngine.create(FlowerCheckConfig.defaults())
                .run(Collections.singletonList(root.toString()));
    }

    private static void assertHasRule(CheckResult result, String ruleId) {
        assertThat(result.findings())
                .extracting(Finding::ruleId)
                .contains(ruleId);
    }

    private static void writeJava(Path root, String name, String... lines) throws IOException {
        Path file = root.resolve(name);
        Files.write(file, String.join("\n", lines).getBytes(StandardCharsets.UTF_8));
    }
}
