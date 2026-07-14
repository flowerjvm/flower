package io.github.flowerjvm.flower.testkit;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.recovery.FlowFactoryRegistry;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowTestHarnessTest {

    @Test
    void ticks_flow_deterministically_and_records_lifecycle() {
        FlowTestHarness harness = FlowTestHarness.create();
        Flow flow = Flow.builder("order", "O-1")
                .step("prepare", doneStep())
                .step("complete", doneStep())
                .build();

        harness.submit(flow)
                .tick()
                .assertFlow("order", "O-1")
                .isRunning()
                .currentStepIs("complete");

        harness.tick()
                .assertFlow("order", "O-1")
                .isFinished()
                .isNotActive();

        List<RecordingFlowerListener.RecordedEvent> events =
                harness.listener().eventsFor(FlowId.of("order", "O-1"));
        assertThat(events).extracting(RecordingFlowerListener.RecordedEvent::type)
                .containsExactly(
                        RecordingFlowerListener.EventType.FLOW_SUBMITTED,
                        RecordingFlowerListener.EventType.STEP_ENTERED,
                        RecordingFlowerListener.EventType.STEP_EXITED,
                        RecordingFlowerListener.EventType.STEP_ENTERED,
                        RecordingFlowerListener.EventType.STEP_EXITED,
                        RecordingFlowerListener.EventType.FLOW_FINISHED);
    }

    @Test
    void publishes_events_to_waiting_steps() {
        FlowTestHarness harness = FlowTestHarness.create();
        Flow flow = Flow.builder("order", "O-1")
                .step("payment", new WaitPaymentStep())
                .build();

        harness.submit(flow)
                .tick()
                .assertFlow("order", "O-1")
                .isRunning()
                .currentStepIs("payment");

        harness.publish(new PaymentApproved("O-1"))
                .tick()
                .assertFlow("order", "O-1")
                .isFinished();
    }

    @Test
    void fake_checkpoint_store_keeps_durable_positions_and_context() {
        FlowTestHarness harness = FlowTestHarness.create();
        ExecutionContext context = TestExecutionContexts.full(
                "tenant-a", "user-1", "session-1", "run-1", "trace-1", "corr-1");
        Flow flow = Flow.builder("order", "O-1")
                .executionContext(context)
                .durable()
                .durableStep("payment", stayStepWithStepNo(7), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();

        harness.submit(flow).tick();

        FlowCheckpoint checkpoint = harness.checkpointStore().get(FlowId.of("order", "O-1"));
        assertThat(checkpoint).isNotNull();
        assertThat(checkpoint.persistence()).isEqualTo(FlowPersistence.DURABLE);
        assertThat(checkpoint.currentStepId()).isEqualTo("payment");
        assertThat(checkpoint.currentStepNo()).isEqualTo(7);
        assertThat(checkpoint.executionContext()).isEqualTo(context);
        harness.assertFlow("order", "O-1")
                .isRunning()
                .tenantIdIs("tenant-a")
                .runIdIs("run-1")
                .traceIdIs("trace-1");
    }

    @Test
    void restart_harness_recovers_active_durable_flows_from_shared_checkpoint_store() {
        FlowTestHarness first = FlowTestHarness.create();
        ExecutionContext context = TestExecutionContexts.tenantRun("tenant-a", "run-1");
        Flow flow = durablePaymentFlow("O-1", context);

        first.submit(flow).tick();

        FlowTestHarness restarted = first.restart();
        FlowFactoryRegistry registry = FlowFactoryRegistry.builder()
                .register("order", flowId -> durablePaymentFlow(flowId.flowKey(), ExecutionContext.empty()))
                .build();

        int recovered = restarted.recoverActiveCount(registry);

        assertThat(recovered).isEqualTo(1);
        restarted.tick()
                .assertFlow("order", "O-1")
                .isRunning()
                .currentStepIs("payment")
                .stepNoIs(7)
                .tenantIdIs("tenant-a")
                .runIdIs("run-1");
    }

    @Test
    void assertions_report_missing_flow() {
        FlowTestHarness harness = FlowTestHarness.create();

        assertThatThrownBy(() -> harness.assertFlow("missing", "1").exists())
                .isInstanceOf(AssertionError.class)
                .hasMessageContaining("missing/1");
    }

    private static Step doneStep() {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                return StepResult.done();
            }
        };
    }

    private static Step stayStepWithStepNo(int stepNo) {
        return new Step() {
            @Override
            protected StepResult onTick(StepContext ctx) {
                ctx.setStepNo(stepNo);
                return StepResult.stay();
            }
        };
    }

    private static Flow durablePaymentFlow(String orderId, ExecutionContext context) {
        return Flow.builder("order", orderId)
                .executionContext(context)
                .durable()
                .durableStep("payment", stayStepWithStepNo(7), RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
    }

    private static final class WaitPaymentStep extends Step {
        @Override
        protected void onEnter(StepContext ctx) {
            ctx.subscribe(PaymentApproved.class, event -> {
                if (event.orderId.equals(ctx.flowId().flowKey())) {
                    ctx.signal("paid");
                }
            });
        }

        @Override
        protected StepResult onTick(StepContext ctx) {
            return ctx.hasSignal("paid") ? StepResult.done() : StepResult.stay();
        }
    }

    private static final class PaymentApproved {
        private final String orderId;

        private PaymentApproved(String orderId) {
            this.orderId = orderId;
        }
    }
}
