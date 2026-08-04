package io.github.flowerjvm.flower.core.worker;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.listener.FlowerListener;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import io.github.flowerjvm.flower.core.trace.FlowerTraceListener;
import io.github.flowerjvm.flower.core.trace.StepTransition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds and dispatches the tick-driven Worker's standard Flower trace. */
final class WorkerTraceDispatcher implements CheckpointCoordinator.Observer {

    @FunctionalInterface
    interface ListenerErrorReporter {
        void report(FlowSnapshot flow, String callbackName, Throwable cause);
    }

    private final String workerName;
    private final Clock clock;
    private final List<FlowerTraceListener> listeners;
    private final ListenerErrorReporter listenerErrorReporter;
    private final Map<Flow, FlowTraceState> states = new IdentityHashMap<>();

    WorkerTraceDispatcher(
            String workerName,
            Clock clock,
            List<FlowerListener> listeners,
            ListenerErrorReporter listenerErrorReporter) {
        this.workerName = workerName;
        this.clock = clock;
        this.listeners = traceListeners(listeners);
        this.listenerErrorReporter = listenerErrorReporter;
    }

    void flowSubmitted(Flow flow) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        FlowCheckpoint recovery = state.recoveryCheckpoint;
        if (recovery == null) {
            emit(flow, FlowerTraceEventType.FLOW_STARTED, null,
                    Collections.<String, Object>emptyMap());
        } else {
            emit(flow, FlowerTraceEventType.FLOW_RECOVERED, null,
                    recoveryAttributes(recovery));
        }
    }

    void flowRecovered(Flow flow, FlowCheckpoint checkpoint) {
        if (enabled()) {
            state(flow).markRecovered(checkpoint);
        }
    }

    void stepStarted(Flow flow, String stepId, int stepNo, boolean recovered) {
        if (!enabled()) {
            return;
        }
        String stepRunId = state(flow).startStepRun();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.STEP_ID, stepId);
        attributes.put(FlowerTraceAttributes.STEP_NO, stepNo);
        attributes.put(FlowerTraceAttributes.STEP_RECOVERED, recovered);
        emit(flow, FlowerTraceEventType.STEP_STARTED, stepRunId, attributes);
    }

    void stepTransitioned(Flow flow, StepTransition transition) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        String stepRunId = state.currentStepRunId;
        emit(flow, traceType(transition, stepRunId), stepRunId,
                transitionAttributes(transition));
        state.currentStepRunId = null;
    }

    void flowSuspended(Flow flow) {
        if (!enabled()) {
            return;
        }
        emit(flow, FlowerTraceEventType.FLOW_SUSPENDED, currentStepRunId(flow),
                flowPositionAttributes(flow));
        forget(flow);
    }

    void flowTerminated(Flow flow) {
        if (!enabled()) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        FlowerTraceEventType type;
        switch (flow.state()) {
            case FINISHED:
                type = FlowerTraceEventType.FLOW_COMPLETED;
                break;
            case FAILED:
            case CHECKPOINT_FAILED:
                type = FlowerTraceEventType.FLOW_FAILED;
                addError(attributes, flow.failureCause());
                break;
            case CANCELLED:
                type = FlowerTraceEventType.FLOW_CANCELLED;
                break;
            default:
                return;
        }
        emit(flow, type, null, attributes);
        forget(flow);
    }

    ExecutionContext checkpointContext(Flow flow) {
        if (!enabled()) {
            return flow.executionContext();
        }
        return state(flow).checkpointContext(flow.executionContext());
    }

    @Override
    public void onSaved(Flow flow, FlowCheckpoint checkpoint, String action) {
        if (!enabled()) {
            return;
        }
        emit(flow, FlowerTraceEventType.CHECKPOINT_SAVED, currentStepRunId(flow),
                checkpointAttributes(checkpoint, action));
    }

    @Override
    public void onFailed(Flow flow, Throwable cause, String action) {
        if (!enabled()) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.CHECKPOINT_ACTION, action);
        addError(attributes, cause);
        emit(flow, FlowerTraceEventType.CHECKPOINT_FAILED, currentStepRunId(flow), attributes);
    }

    private boolean enabled() {
        return !listeners.isEmpty();
    }

    private void emit(
            Flow flow,
            FlowerTraceEventType type,
            String stepRunId,
            Map<String, Object> attributes) {
        FlowTraceState state = state(flow);
        long sequence = state.nextSequence();
        ExecutionContext context = flow.executionContext();
        FlowerTraceEvent event = FlowerTraceEvent.builder(type)
                .eventId(state.runtimeId + ":event:" + sequence)
                .traceId(state.traceId)
                .flowRunId(state.flowRunId)
                .stepRunId(stepRunId)
                .parentRunId(stepRunId == null ? null : state.flowRunId)
                .flowId(flow.flowId())
                .workerName(workerName)
                .sequence(sequence)
                .occurredAt(Instant.ofEpochMilli(clock.currentTimeMillis()))
                .attribute(FlowerTraceAttributes.FLOW_STATE, flow.state().name())
                .attribute(FlowerTraceAttributes.FLOW_PERSISTENCE, flow.persistence().name())
                .attribute(FlowerTraceAttributes.FLOW_DEFINITION_VERSION, flow.definitionVersion())
                .attribute(FlowerTraceAttributes.FLOW_RUNTIME_ID, state.runtimeId)
                .attribute(FlowerTraceAttributes.TENANT_ID, context.tenantIdOrNull())
                .attribute(FlowerTraceAttributes.USER_ID, context.userIdOrNull())
                .attribute(FlowerTraceAttributes.SESSION_ID, context.sessionIdOrNull())
                .attribute(FlowerTraceAttributes.CORRELATION_ID, context.correlationIdOrNull())
                .attributes(attributes)
                .build();
        FlowSnapshot snapshot = flow.snapshot();
        for (FlowerTraceListener listener : listeners) {
            try {
                listener.onTraceEvent(event);
            } catch (Throwable failure) {
                reportListenerError(snapshot, failure);
            }
        }
    }

    private FlowTraceState state(Flow flow) {
        synchronized (states) {
            FlowTraceState existing = states.get(flow);
            if (existing != null) {
                return existing;
            }
            ExecutionContext context = flow.executionContext();
            String flowRunId = context.runIdOrNull();
            if (flowRunId == null) {
                flowRunId = UUID.randomUUID().toString();
            }
            String traceId = context.traceIdOrNull();
            if (traceId == null) {
                traceId = flowRunId;
            }
            FlowTraceState created = new FlowTraceState(flowRunId, traceId);
            states.put(flow, created);
            return created;
        }
    }

    private String currentStepRunId(Flow flow) {
        synchronized (states) {
            FlowTraceState state = states.get(flow);
            return state == null ? null : state.currentStepRunId;
        }
    }

    private void forget(Flow flow) {
        synchronized (states) {
            states.remove(flow);
        }
    }

    private void reportListenerError(FlowSnapshot snapshot, Throwable cause) {
        if (listenerErrorReporter == null) {
            return;
        }
        try {
            listenerErrorReporter.report(snapshot, "onTraceEvent", cause);
        } catch (Throwable ignored) {
        }
    }

    private static List<FlowerTraceListener> traceListeners(List<FlowerListener> listeners) {
        if (listeners == null || listeners.isEmpty()) {
            return Collections.emptyList();
        }
        List<FlowerTraceListener> traces = new ArrayList<>();
        for (FlowerListener listener : listeners) {
            if (listener instanceof FlowerTraceListener) {
                traces.add((FlowerTraceListener) listener);
            }
        }
        return Collections.unmodifiableList(traces);
    }

    private static FlowerTraceEventType traceType(
            StepTransition transition,
            String stepRunId) {
        if (stepRunId == null
                && transition.origin() == StepTransition.Origin.GUARD
                && transition.outcome() == StepTransition.Outcome.GOTO) {
            return FlowerTraceEventType.STEP_SKIPPED;
        }
        switch (transition.outcome()) {
            case FAILED:
                return FlowerTraceEventType.STEP_FAILED;
            case CANCELLED:
                return FlowerTraceEventType.STEP_CANCELLED;
            default:
                return FlowerTraceEventType.STEP_COMPLETED;
        }
    }

    private static Map<String, Object> transitionAttributes(StepTransition transition) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.STEP_ID, transition.stepId());
        attributes.put(FlowerTraceAttributes.STEP_NO, transition.stepNo());
        attributes.put(FlowerTraceAttributes.STEP_TRANSITION_ORIGIN, transition.origin().name());
        attributes.put(FlowerTraceAttributes.STEP_OUTCOME, transition.outcome().name());
        if (transition.targetStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_TARGET_ID, transition.targetStepId());
        }
        addError(attributes, transition.cause());
        return attributes;
    }

    private static Map<String, Object> checkpointAttributes(
            FlowCheckpoint checkpoint,
            String action) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.CHECKPOINT_ACTION, action);
        attributes.put(
                FlowerTraceAttributes.CHECKPOINT_UPDATED_AT_MILLIS,
                checkpoint.updatedAtMillis());
        attributes.put(
                FlowerTraceAttributes.CHECKPOINT_STEP_ENTERED,
                checkpoint.currentStepEntered());
        if (checkpoint.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, checkpoint.currentStepId());
        }
        attributes.put(FlowerTraceAttributes.STEP_NO, checkpoint.currentStepNo());
        return attributes;
    }

    private static Map<String, Object> recoveryAttributes(FlowCheckpoint checkpoint) {
        Map<String, Object> attributes = checkpointAttributes(checkpoint, "RECOVERY_SOURCE");
        attributes.put(FlowerTraceAttributes.FLOW_RECOVERED, true);
        if (checkpoint.workerName() != null) {
            attributes.put(FlowerTraceAttributes.FLOW_RECOVERY_WORKER, checkpoint.workerName());
        }
        attributes.put(
                FlowerTraceAttributes.FLOW_RECOVERY_CHECKPOINT_AT_MILLIS,
                checkpoint.updatedAtMillis());
        return attributes;
    }

    private static Map<String, Object> flowPositionAttributes(Flow flow) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (flow.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, flow.currentStepId());
            attributes.put(FlowerTraceAttributes.STEP_NO, flow.currentStepNo());
        }
        return attributes;
    }

    private static void addError(Map<String, Object> attributes, Throwable cause) {
        if (cause == null) {
            return;
        }
        attributes.put(FlowerTraceAttributes.ERROR_TYPE, cause.getClass().getName());
        if (cause.getMessage() != null) {
            attributes.put(FlowerTraceAttributes.ERROR_MESSAGE, cause.getMessage());
        }
    }

    private static final class FlowTraceState {
        private final String flowRunId;
        private final String traceId;
        private String runtimeId;
        private FlowCheckpoint recoveryCheckpoint;
        private long sequence;
        private long stepSequence;
        private String currentStepRunId;

        private FlowTraceState(String flowRunId, String traceId) {
            this.flowRunId = flowRunId;
            this.traceId = traceId;
            this.runtimeId = flowRunId;
        }

        private long nextSequence() {
            return ++sequence;
        }

        private String startStepRun() {
            currentStepRunId = runtimeId + ":step:" + (++stepSequence);
            return currentStepRunId;
        }

        private void markRecovered(FlowCheckpoint checkpoint) {
            recoveryCheckpoint = checkpoint;
            runtimeId = flowRunId + ":recovery:" + UUID.randomUUID();
        }

        private ExecutionContext checkpointContext(ExecutionContext context) {
            ExecutionContext base = context == null ? ExecutionContext.empty() : context;
            ExecutionContext.Builder builder = base.toBuilder();
            if (base.runIdOrNull() == null) {
                builder.runId(flowRunId);
            }
            if (base.traceIdOrNull() == null) {
                builder.traceId(traceId);
            }
            return builder.build();
        }
    }
}
