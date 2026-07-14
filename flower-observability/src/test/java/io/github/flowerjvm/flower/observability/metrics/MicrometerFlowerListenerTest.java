package io.github.flowerjvm.flower.observability.metrics;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Tags;
import io.micrometer.core.instrument.Timer;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class MicrometerFlowerListenerTest {

    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final MicrometerFlowerListener listener = new MicrometerFlowerListener(registry);

    @Test
    void incrementsSubmittedCounter() {
        FlowSnapshot snap = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);

        listener.onFlowSubmitted(snap);

        Counter c = registry.find(FlowerMetricNames.FLOW_SUBMITTED)
                .tags(FlowerMetricNames.TAG_FLOW_TYPE, "quay-work")
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);
    }

    @Test
    void recordsFlowDurationOnFinish() {
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot finish = snapshot("quay-work", "WO-1", FlowState.FINISHED, null, 0);

        listener.onFlowSubmitted(submit);
        listener.onFlowFinished(finish);

        Timer t = registry.find(FlowerMetricNames.FLOW_DURATION)
                .tags(Tags.of(
                        FlowerMetricNames.TAG_FLOW_TYPE, "quay-work",
                        FlowerMetricNames.TAG_OUTCOME, FlowerMetricNames.OUTCOME_FINISHED))
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);

        Counter finished = registry.find(FlowerMetricNames.FLOW_FINISHED)
                .tags(FlowerMetricNames.TAG_FLOW_TYPE, "quay-work")
                .counter();
        assertThat(finished).isNotNull();
        assertThat(finished.count()).isEqualTo(1.0);
    }

    @Test
    void recordsFailureOutcomeAndCounter() {
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot fail = snapshot("quay-work", "WO-1", FlowState.FAILED, null, 0);

        listener.onFlowSubmitted(submit);
        listener.onFlowFailed(fail, new RuntimeException("boom"));

        Timer t = registry.find(FlowerMetricNames.FLOW_DURATION)
                .tag(FlowerMetricNames.TAG_OUTCOME, FlowerMetricNames.OUTCOME_FAILED)
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);

        Counter failed = registry.find(FlowerMetricNames.FLOW_FAILED).counter();
        assertThat(failed).isNotNull();
        assertThat(failed.count()).isEqualTo(1.0);
    }

    @Test
    void recordsCancelOutcomeAndCounter() {
        FlowSnapshot submit = snapshot("quay-work", "WO-1", FlowState.READY, null, 0);
        FlowSnapshot cancel = snapshot("quay-work", "WO-1", FlowState.CANCELLED, null, 0);

        listener.onFlowSubmitted(submit);
        listener.onFlowCancelled(cancel);

        Timer t = registry.find(FlowerMetricNames.FLOW_DURATION)
                .tag(FlowerMetricNames.TAG_OUTCOME, FlowerMetricNames.OUTCOME_CANCELLED)
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);
    }

    @Test
    void recordsStepEnteredAndDuration() {
        FlowSnapshot enter = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 0);
        FlowSnapshot exit = snapshot("quay-work", "WO-1", FlowState.RUNNING, "execute-sts", 10);

        listener.onStepEntered(enter, "execute-sts");
        listener.onStepExited(exit, "execute-sts");

        Counter c = registry.find(FlowerMetricNames.STEP_ENTERED)
                .tags(Tags.of(
                        FlowerMetricNames.TAG_FLOW_TYPE, "quay-work",
                        FlowerMetricNames.TAG_STEP_ID, "execute-sts"))
                .counter();
        assertThat(c).isNotNull();
        assertThat(c.count()).isEqualTo(1.0);

        Timer t = registry.find(FlowerMetricNames.STEP_DURATION)
                .tags(Tags.of(
                        FlowerMetricNames.TAG_FLOW_TYPE, "quay-work",
                        FlowerMetricNames.TAG_STEP_ID, "execute-sts"))
                .timer();
        assertThat(t).isNotNull();
        assertThat(t.count()).isEqualTo(1L);
    }

    @Test
    void terminalWithoutSubmitDoesNotRecordTimer() {
        FlowSnapshot finish = snapshot("quay-work", "WO-1", FlowState.FINISHED, null, 0);

        listener.onFlowFinished(finish);

        assertThat(registry.find(FlowerMetricNames.FLOW_DURATION).timer()).isNull();
        Counter finished = registry.find(FlowerMetricNames.FLOW_FINISHED).counter();
        assertThat(finished).isNotNull();
        assertThat(finished.count()).isEqualTo(1.0);
    }

    private static FlowSnapshot snapshot(String flowType, String flowKey, FlowState state,
                                         String currentStepId, int currentStepNo) {
        return new FlowSnapshot(FlowId.of(flowType, flowKey), state, currentStepId, currentStepNo, null);
    }
}
