package io.github.parkkevinsb.flower.testkit;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * {@link FlowerListener} that records lifecycle callbacks for assertions.
 */
public final class RecordingFlowerListener implements FlowerListener {

    private final List<RecordedEvent> events = new ArrayList<>();
    private final Map<FlowId, FlowSnapshot> latestByFlow = new LinkedHashMap<>();

    @Override
    public void onFlowSubmitted(FlowSnapshot flow) {
        record(RecordedEvent.flow(EventType.FLOW_SUBMITTED, flow));
    }

    @Override
    public void onStepEntered(FlowSnapshot flow, String stepId) {
        record(RecordedEvent.step(EventType.STEP_ENTERED, flow, stepId));
    }

    @Override
    public void onStepExited(FlowSnapshot flow, String stepId) {
        record(RecordedEvent.step(EventType.STEP_EXITED, flow, stepId));
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        record(RecordedEvent.flow(EventType.FLOW_FINISHED, flow));
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        record(RecordedEvent.flow(EventType.FLOW_FAILED, flow, cause));
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        record(RecordedEvent.flow(EventType.FLOW_CANCELLED, flow));
    }

    @Override
    public void onListenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
        record(RecordedEvent.listenerError(flow, callbackName, cause));
    }

    @Override
    public void onWorkerError(String workerName, Throwable cause) {
        record(RecordedEvent.workerError(workerName, cause));
    }

    public synchronized List<RecordedEvent> events() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public synchronized List<RecordedEvent> eventsFor(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        List<RecordedEvent> out = new ArrayList<>();
        for (RecordedEvent event : events) {
            if (event.flow() != null && flowId.equals(event.flow().flowId())) {
                out.add(event);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized Optional<FlowSnapshot> latest(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        return Optional.ofNullable(latestByFlow.get(flowId));
    }

    public synchronized long count(EventType type) {
        long count = 0L;
        for (RecordedEvent event : events) {
            if (event.type() == type) {
                count++;
            }
        }
        return count;
    }

    public synchronized void clear() {
        events.clear();
        latestByFlow.clear();
    }

    private synchronized void record(RecordedEvent event) {
        events.add(event);
        if (event.flow() != null) {
            latestByFlow.put(event.flow().flowId(), event.flow());
        }
    }

    public enum EventType {
        FLOW_SUBMITTED,
        STEP_ENTERED,
        STEP_EXITED,
        FLOW_FINISHED,
        FLOW_FAILED,
        FLOW_CANCELLED,
        LISTENER_ERROR,
        WORKER_ERROR
    }

    public static final class RecordedEvent {
        private final EventType type;
        private final FlowSnapshot flow;
        private final String stepId;
        private final String callbackName;
        private final String workerName;
        private final Throwable cause;

        private RecordedEvent(
                EventType type,
                FlowSnapshot flow,
                String stepId,
                String callbackName,
                String workerName,
                Throwable cause) {
            this.type = type;
            this.flow = flow;
            this.stepId = stepId;
            this.callbackName = callbackName;
            this.workerName = workerName;
            this.cause = cause;
        }

        static RecordedEvent flow(EventType type, FlowSnapshot flow) {
            return flow(type, flow, null);
        }

        static RecordedEvent flow(EventType type, FlowSnapshot flow, Throwable cause) {
            return new RecordedEvent(type, flow, null, null, null, cause);
        }

        static RecordedEvent step(EventType type, FlowSnapshot flow, String stepId) {
            return new RecordedEvent(type, flow, stepId, null, null, null);
        }

        static RecordedEvent listenerError(FlowSnapshot flow, String callbackName, Throwable cause) {
            return new RecordedEvent(EventType.LISTENER_ERROR, flow, null, callbackName, null, cause);
        }

        static RecordedEvent workerError(String workerName, Throwable cause) {
            return new RecordedEvent(EventType.WORKER_ERROR, null, null, null, workerName, cause);
        }

        public EventType type() {
            return type;
        }

        public FlowSnapshot flow() {
            return flow;
        }

        public String stepId() {
            return stepId;
        }

        public String callbackName() {
            return callbackName;
        }

        public String workerName() {
            return workerName;
        }

        public Throwable cause() {
            return cause;
        }

        @Override
        public String toString() {
            return "RecordedEvent{"
                    + "type=" + type
                    + (flow != null ? ", flow=" + flow : "")
                    + (stepId != null ? ", stepId=" + stepId : "")
                    + (callbackName != null ? ", callbackName=" + callbackName : "")
                    + (workerName != null ? ", workerName=" + workerName : "")
                    + (cause != null ? ", cause=" + cause : "")
                    + '}';
        }
    }
}
