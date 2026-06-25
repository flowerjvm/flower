package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.step.EventStep;
import io.github.parkkevinsb.flower.eventloop.step.EventStepContext;
import io.github.parkkevinsb.flower.eventloop.step.EventStepResult;
import io.github.parkkevinsb.flower.eventloop.worker.EventWorker;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.ManualClock;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class EventWorkerListenerTest {

    @Test
    void listenerReceivesSubmittedStepAndTerminalCallbacks() {
        RecordingListener listener = new RecordingListener();
        EventWorker worker = EventWorker.builder("listen")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .listeners(Collections.singletonList(listener))
                .build();

        EventFlow flow = EventFlow.builder("listen", "ok")
                .step("first", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.next();
                    }
                })
                .step("second", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(listener.events()).containsExactly(
                "submitted:CREATED:null",
                "entered:RUNNING:first",
                "exited:RUNNING:first",
                "entered:RUNNING:second",
                "exited:RUNNING:second",
                "finished:FINISHED:null");
    }

    @Test
    void listenerErrorsAreReportedAndDoNotStopFlow() {
        RecordingListener listener = new RecordingListener() {
            @Override
            public void onStepEntered(FlowSnapshot flow, String stepId) {
                super.onStepEntered(flow, stepId);
                throw new IllegalStateException("listener boom");
            }
        };
        EventWorker worker = EventWorker.builder("listen-error")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .listeners(Collections.singletonList(listener))
                .build();

        EventFlow flow = EventFlow.builder("listen", "listener-error")
                .step("only", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(listener.events()).contains(
                "listenerError:onStepEntered:listener boom",
                "finished:FINISHED:null");
    }

    @Test
    void checkpointDeleteErrorsAreReportedAsWorkerErrors() {
        RecordingListener listener = new RecordingListener();
        EventWorker worker = EventWorker.builder("worker-error")
                .clock(new ManualClock())
                .eventBus(InMemoryEventBus.create())
                .checkpointStore(new EventFlowCheckpointStore() {
                    @Override
                    public void save(EventFlowCheckpoint checkpoint) {
                    }

                    @Override
                    public void delete(io.github.parkkevinsb.flower.core.flow.FlowId flowId) {
                        throw new IllegalStateException("delete boom");
                    }
                })
                .listeners(Collections.singletonList(listener))
                .build();

        EventFlow flow = EventFlow.builder("listen", "worker-error")
                .durable()
                .step("only", new EventStep() {
                    @Override
                    protected EventStepResult onEnter(EventStepContext ctx) {
                        return EventStepResult.finish();
                    }
                })
                .build();

        worker.submit(flow);
        worker.drain();

        assertThat(flow.state()).isEqualTo(FlowState.FINISHED);
        assertThat(listener.events()).contains(
                "workerError:worker-error:delete boom",
                "finished:FINISHED:null");
    }

    private static class RecordingListener implements FlowerListener {
        private final List<String> events = new ArrayList<>();

        List<String> events() {
            return events;
        }

        @Override
        public void onFlowSubmitted(FlowSnapshot flow) {
            events.add("submitted:" + flow.state() + ":" + flow.currentStepId());
        }

        @Override
        public void onStepEntered(FlowSnapshot flow, String stepId) {
            events.add("entered:" + flow.state() + ":" + stepId);
        }

        @Override
        public void onStepExited(FlowSnapshot flow, String stepId) {
            events.add("exited:" + flow.state() + ":" + stepId);
        }

        @Override
        public void onFlowFinished(FlowSnapshot flow) {
            events.add("finished:" + flow.state() + ":" + flow.currentStepId());
        }

        @Override
        public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
            events.add("failed:" + flow.state() + ":" + cause.getMessage());
        }

        @Override
        public void onFlowCancelled(FlowSnapshot flow) {
            events.add("cancelled:" + flow.state() + ":" + flow.currentStepId());
        }

        @Override
        public void onListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
            events.add("listenerError:" + callbackName + ":" + cause.getMessage());
        }

        @Override
        public void onWorkerError(String workerName, Throwable cause) {
            events.add("workerError:" + workerName + ":" + cause.getMessage());
        }
    }
}
