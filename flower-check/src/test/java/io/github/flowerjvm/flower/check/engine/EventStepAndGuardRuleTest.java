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

class EventStepAndGuardRuleTest {

    @Test
    void detectsBlockingCallInEventStep(@TempDir Path root) throws IOException {
        writeJava(root, "BlockingEventStep.java",
                "package demo;",
                "class BlockingEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) throws Exception {",
                "        Thread.sleep(1000);",
                "        return EventStepResult.finish();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-001");
    }

    @Test
    void allowsBlockingProviderWorkInsideOfficialAsyncBoundary(@TempDir Path root) throws IOException {
        writeJava(root, "AsyncRequestEventStep.java",
                "package demo;",
                "class AsyncRequestEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        return EventStepResult.await(",
                "                AwaitCondition.event(Response.class),",
                "                AwaitCondition.deadlineIn(1000))",
                "                .thenRunAsync(async -> {",
                "                    try { Thread.sleep(1000); } catch (Exception ignored) { }",
                "                    new OpenAIClient().chat();",
                "                });",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void detectsDirectProviderCallInEventStep(@TempDir Path root) throws IOException {
        writeJava(root, "ProviderEventStep.java",
                "package demo;",
                "class ProviderEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        new OpenAIClient().chat();",
                "        return EventStepResult.finish();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-002");
    }

    @Test
    void detectsSideEffectInInlineGuard(@TempDir Path root) throws IOException {
        writeJava(root, "SideEffectGuardFlow.java",
                "package demo;",
                "class StartStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}",
                "class SideEffectGuardFlow {",
                "    Flow create(Repository repository) {",
                "        return Flow.builder(\"operation\", \"1\")",
                "                .step(\"start\", new StartStep(), ctx -> {",
                "                    repository.save(\"started\");",
                "                    return GuardResult.pass();",
                "                })",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-017");
    }

    @Test
    void detectsBlockingAndMutationInNamedGuard(@TempDir Path root) throws IOException {
        writeJava(root, "MutatingGuard.java",
                "package demo;",
                "class MutatingGuard implements Guard {",
                "    private int checks;",
                "    public GuardResult check(StepContext ctx) throws Exception {",
                "        Thread.sleep(10);",
                "        checks++;",
                "        repository.save(\"guard-check\");",
                "        return GuardResult.hold();",
                "    }",
                "}");

        CheckResult result = run(root);
        assertHasRule(result, "FLOWER-CHECK-001");
        assertHasRule(result, "FLOWER-CHECK-017");
    }

    @Test
    void allowsGuardLocalBookkeeping(@TempDir Path root) throws IOException {
        writeJava(root, "CountingGuard.java",
                "package demo;",
                "class CountingGuard implements Guard {",
                "    private int checks;",
                "    public GuardResult check(StepContext ctx) {",
                "        checks++;",
                "        this.checks = checks + 1;",
                "        return checks < 3 ? GuardResult.hold() : GuardResult.pass();",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void allowsReadOnlyGuard(@TempDir Path root) throws IOException {
        writeJava(root, "ReadOnlyGuardFlow.java",
                "package demo;",
                "class ReadyStep extends Step {",
                "    protected StepResult onTick(StepContext ctx) { return StepResult.done(); }",
                "}",
                "class ReadOnlyGuardFlow {",
                "    Flow create(ReadinessService service) {",
                "        return Flow.builder(\"operation\", \"1\")",
                "                .step(\"ready\", new ReadyStep(), ctx ->",
                "                        service.canStart() ? GuardResult.pass() : GuardResult.hold())",
                "                .build();",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void detectsFiniteEventAwaitWithoutDeadline(@TempDir Path root) throws IOException {
        writeJava(root, "ApprovalEventStep.java",
                "package demo;",
                "class ApprovalEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        return EventStepResult.await(AwaitCondition.event(Approval.class));",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-018");
    }

    @Test
    void detectsMissingRecoveryForDurableEventAwait(@TempDir Path root) throws IOException {
        writeJava(root, "DurableApprovalEventFlow.java",
                "package demo;",
                "class DurableApprovalEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        return EventStepResult.await(",
                "                AwaitCondition.signal(\"approval\", \"A-1\"),",
                "                AwaitCondition.deadlineIn(1000));",
                "    }",
                "}",
                "class DurableApprovalEventFlow {",
                "    EventFlow create() {",
                "        return EventFlow.builder(\"approval\", \"A-1\")",
                "                .durable()",
                "                .step(\"wait\", new DurableApprovalEventStep())",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-019");
    }

    @Test
    void detectsPredicateAwaitInDurableEventFlow(@TempDir Path root) throws IOException {
        writeJava(root, "PredicateEventFlow.java",
                "package demo;",
                "class PredicateEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        return waits();",
                "    }",
                "    protected EventStepResult onRecover(EventStepContext ctx, EventRecoveryContext recovery) {",
                "        return waits();",
                "    }",
                "    private EventStepResult waits() {",
                "        return EventStepResult.await(",
                "                AwaitCondition.event(Response.class, response -> response != null),",
                "                AwaitCondition.deadlineIn(1000));",
                "    }",
                "}",
                "class PredicateEventFlow {",
                "    EventFlow create() {",
                "        return EventFlow.builder(\"request\", \"1\")",
                "                .durable()",
                "                .step(\"wait\", new PredicateEventStep())",
                "                .build();",
                "    }",
                "}");

        assertHasRule(run(root), "FLOWER-CHECK-019");
    }

    @Test
    void allowsRecoverableDurableEventAwait(@TempDir Path root) throws IOException {
        writeJava(root, "RecoverableEventFlow.java",
                "package demo;",
                "class RecoverableApprovalStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) { return waits(); }",
                "    protected EventStepResult onRecover(EventStepContext ctx, EventRecoveryContext recovery) {",
                "        return waits();",
                "    }",
                "    private EventStepResult waits() {",
                "        return EventStepResult.await(",
                "                AwaitCondition.signal(\"approval\", \"A-1\"),",
                "                AwaitCondition.deadlineIn(1000));",
                "    }",
                "}",
                "class RecoverableEventFlow {",
                "    EventFlow create() {",
                "        return EventFlow.builder(\"approval\", \"A-1\")",
                "                .durable()",
                "                .step(\"wait\", new RecoverableApprovalStep())",
                "                .build();",
                "    }",
                "}");

        assertThat(run(root).findings()).isEmpty();
    }

    @Test
    void validatesEventFlowGoToAndDuplicateIds(@TempDir Path root) throws IOException {
        writeJava(root, "InvalidEventFlow.java",
                "package demo;",
                "class RoutingEventStep extends EventStep {",
                "    protected EventStepResult onEnter(EventStepContext ctx) {",
                "        return EventStepResult.goTo(\"missing\");",
                "    }",
                "}",
                "class InvalidEventFlow {",
                "    EventFlow create() {",
                "        return EventFlow.builder(\"route\", \"1\")",
                "                .step(\"start\", new RoutingEventStep())",
                "                .step(\"start\", new RoutingEventStep())",
                "                .build();",
                "    }",
                "}");

        CheckResult result = run(root);
        assertHasRule(result, "FLOWER-CHECK-009");
        assertHasRule(result, "FLOWER-CHECK-013");
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
