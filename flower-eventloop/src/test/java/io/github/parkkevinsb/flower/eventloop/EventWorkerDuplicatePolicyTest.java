package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EventWorkerDuplicatePolicyTest {

    static final class Wake {
    }

    @Test
    void duplicateSubmitRejectsByDefault() {
        EventWorker worker = new EventWorker("duplicates", new ManualClock(), InMemoryEventBus.create());
        EventFlow first = waitingFlow("same");
        EventFlow second = finishingFlow("same");

        worker.submit(first);

        assertThatThrownBy(() -> worker.submit(second))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already submitted");
        assertThat(second.state()).isEqualTo(FlowState.CREATED);
    }

    @Test
    void duplicateSubmitCanIgnoreNewFlow() {
        EventWorker worker = new EventWorker("duplicates", new ManualClock(), InMemoryEventBus.create());
        EventFlow first = waitingFlow("same");
        EventFlow second = finishingFlow("same");

        worker.submit(first);
        worker.submit(second, DuplicatePolicy.IGNORE);
        worker.drain();

        assertThat(first.state()).isEqualTo(FlowState.RUNNING);
        assertThat(second.state()).isEqualTo(FlowState.CREATED);
        assertThat(worker.activeCount()).isEqualTo(1);
    }

    @Test
    void duplicateSubmitCanReplaceExistingFlow() {
        EventWorker worker = new EventWorker("duplicates", new ManualClock(), InMemoryEventBus.create());
        EventFlow first = waitingFlow("same");
        EventFlow second = finishingFlow("same");

        worker.submit(first);
        worker.drain();

        assertThat(first.state()).isEqualTo(FlowState.RUNNING);

        worker.submit(second, DuplicatePolicy.REPLACE);
        worker.drain();

        assertThat(first.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(second.state()).isEqualTo(FlowState.FINISHED);
        assertThat(worker.activeCount()).isZero();
    }

    private static EventFlow waitingFlow(String key) {
        return EventFlow.builder("duplicate", key)
                .step("wait", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.await(AwaitCondition.event(Wake.class));
                    }
                })
                .build();
    }

    private static EventFlow finishingFlow(String key) {
        return EventFlow.builder("duplicate", key)
                .step("finish", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.finish();
                    }
                })
                .build();
    }
}
