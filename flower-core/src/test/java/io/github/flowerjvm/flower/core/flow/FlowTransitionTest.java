package io.github.flowerjvm.flower.core.flow;

import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.trace.StepTransition;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class FlowTransitionTest {

    @Test
    void exit_observer_can_cancel_a_done_transition() {
        assertExitObserverCancellation(StepResult.done());
    }

    @Test
    void exit_observer_can_cancel_a_goto_transition() {
        assertExitObserverCancellation(StepResult.goTo("next"));
    }

    @Test
    void exit_observer_can_cancel_a_finish_transition() {
        assertExitObserverCancellation(StepResult.finish());
    }

    private static void assertExitObserverCancellation(StepResult result) {
        List<StepTransition> transitions = new ArrayList<>();
        Flow flow = Flow.builder("transition-test", result.type().name())
                .step("first", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return result;
                    }
                })
                .step("next", new Step() {
                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        return StepResult.done();
                    }
                })
                .build();
        flow.attach(new ManualClock(), InMemoryEventBus.create(), new LifecycleObserver() {
            @Override
            public void onStepEntered(String stepId) {
            }

            @Override
            public void onStepExited(String stepId) {
                flow.cancel();
            }

            @Override
            public void onStepTransitioned(StepTransition transition) {
                transitions.add(transition);
            }
        });

        flow.tick();

        assertThat(flow.state()).isEqualTo(FlowState.CANCELLED);
        assertThat(flow.failureCause()).isNull();
        assertThat(transitions).hasSize(1);
        assertThat(transitions.get(0).origin()).isEqualTo(StepTransition.Origin.EXTERNAL);
        assertThat(transitions.get(0).outcome()).isEqualTo(StepTransition.Outcome.CANCELLED);
    }
}
