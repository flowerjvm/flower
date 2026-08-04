package io.github.flowerjvm.flower.eventloop.worker;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.time.Clock;
import io.github.flowerjvm.flower.core.trace.FlowerTraceAttributes;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;
import io.github.flowerjvm.flower.core.trace.FlowerTraceEventType;
import io.github.flowerjvm.flower.eventloop.event.EventSignal;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;
import io.github.flowerjvm.flower.eventloop.persistence.EventAwaitCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.flowerjvm.flower.eventloop.step.AwaitCondition;
import io.github.flowerjvm.flower.eventloop.step.EventStepDefinition;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Builds and dispatches the event-loop projection of the standard Flower trace. */
final class EventTraceDispatcher {

    private static final long NO_DEADLINE = Long.MIN_VALUE;

    private final String workerName;
    private final Clock clock;
    private final ListenerDispatcher listeners;
    private final Map<EventFlow, FlowTraceState> states = new IdentityHashMap<>();

    EventTraceDispatcher(String workerName, Clock clock, ListenerDispatcher listeners) {
        this.workerName = workerName;
        this.clock = clock;
        this.listeners = listeners;
    }

    boolean enabled() {
        return listeners.traceEnabled();
    }

    void startFlow(EventFlow flow, EventFlowCheckpoint recovery) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        if (state.started) {
            return;
        }
        state.started = true;
        if (recovery == null) {
            emit(flow, FlowerTraceEventType.FLOW_STARTED, null,
                    Collections.<String, Object>emptyMap());
            return;
        }
        state.markRecovered();
        emit(flow, FlowerTraceEventType.FLOW_RECOVERED, null, recoveryAttributes(recovery));
    }

    void startStep(EventFlow flow, EventStepDefinition step, boolean recovered) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        String stepRunId = state.startStepRun();
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.STEP_ID, step.stepId());
        attributes.put(FlowerTraceAttributes.STEP_RECOVERED, recovered);
        attributes.put(FlowerTraceAttributes.STEP_CALLBACK, recovered ? "RECOVER" : "ENTER");
        emit(flow, FlowerTraceEventType.STEP_STARTED, stepRunId, attributes);
    }

    void finishStep(EventFlow flow, String outcome, String targetStepId, Throwable cause) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        String stepRunId = state.currentStepRunId;
        if (stepRunId == null) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (flow.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, flow.currentStepId());
        }
        attributes.put(FlowerTraceAttributes.STEP_OUTCOME, outcome);
        if (targetStepId != null) {
            attributes.put(FlowerTraceAttributes.STEP_TARGET_ID, targetStepId);
        }
        addError(attributes, cause);
        FlowerTraceEventType type = "FAIL".equals(outcome)
                ? FlowerTraceEventType.STEP_FAILED
                : "CANCELLED".equals(outcome)
                        ? FlowerTraceEventType.STEP_CANCELLED
                        : FlowerTraceEventType.STEP_COMPLETED;
        emit(flow, type, stepRunId, attributes);
        state.currentStepRunId = null;
    }

    void waiting(
            EventFlow flow,
            long generation,
            List<Map<String, Object>> descriptors) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        state.waiting = true;
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.WAIT_GENERATION, generation);
        attributes.put(FlowerTraceAttributes.WAIT_CONDITIONS, descriptors);
        if (flow.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, flow.currentStepId());
        }
        emit(flow, FlowerTraceEventType.FLOW_WAITING, state.currentStepRunId, attributes);
    }

    void resumed(
            EventFlow flow,
            long generation,
            String reason,
            Object event,
            long deadlineAtMillis) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = state(flow);
        if (!state.waiting && !"RECOVERY".equals(reason)) {
            return;
        }
        state.waiting = false;
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.WAIT_GENERATION, generation);
        attributes.put(FlowerTraceAttributes.RESUME_REASON, reason);
        if (flow.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, flow.currentStepId());
        }
        if (event instanceof EventSignal) {
            EventSignal signal = (EventSignal) event;
            attributes.put(FlowerTraceAttributes.RESUME_SIGNAL_NAME, signal.name());
            attributes.put(FlowerTraceAttributes.RESUME_SIGNAL_KEY, signal.key());
        } else if (event != null) {
            attributes.put(FlowerTraceAttributes.RESUME_EVENT_TYPE, event.getClass().getName());
        }
        if (deadlineAtMillis != NO_DEADLINE) {
            attributes.put(FlowerTraceAttributes.RESUME_DEADLINE_AT_MILLIS, deadlineAtMillis);
        }
        emit(flow, FlowerTraceEventType.FLOW_RESUMED, state.currentStepRunId, attributes);
    }

    void clearWaiting(EventFlow flow) {
        if (!enabled()) {
            return;
        }
        FlowTraceState state = existingState(flow);
        if (state != null) {
            state.waiting = false;
        }
    }

    void checkpointSaved(EventFlow flow, EventFlowCheckpoint checkpoint, String action) {
        if (!enabled()) {
            return;
        }
        emit(flow, FlowerTraceEventType.CHECKPOINT_SAVED, currentStepRunId(flow),
                checkpointAttributes(checkpoint, action));
    }

    void checkpointFailed(EventFlow flow, Throwable cause, String action) {
        if (!enabled()) {
            return;
        }
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.CHECKPOINT_ACTION, action);
        addError(attributes, cause);
        emit(flow, FlowerTraceEventType.CHECKPOINT_FAILED, currentStepRunId(flow), attributes);
    }

    void flowTerminated(EventFlow flow) {
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

    void suspended(EventFlow flow) {
        if (!enabled()) {
            return;
        }
        emit(flow, FlowerTraceEventType.FLOW_SUSPENDED, currentStepRunId(flow),
                flowPositionAttributes(flow));
        forget(flow);
    }

    ExecutionContext checkpointContext(EventFlow flow) {
        if (!enabled()) {
            return flow.executionContext();
        }
        return state(flow).checkpointContext(flow.executionContext());
    }

    String currentStepRunId(EventFlow flow) {
        FlowTraceState state = existingState(flow);
        return state == null ? null : state.currentStepRunId;
    }

    void forget(EventFlow flow) {
        synchronized (states) {
            states.remove(flow);
        }
    }

    Map<String, Object> eventWaitDescriptor(AwaitCondition.Event event) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "EVENT");
        descriptor.put(FlowerTraceAttributes.WAIT_EVENT_TYPE, event.eventType().getName());
        descriptor.put(FlowerTraceAttributes.WAIT_EVENT_PREDICATE, event.hasPredicate());
        return Collections.unmodifiableMap(descriptor);
    }

    Map<String, Object> signalWaitDescriptor(AwaitCondition.Signal signal) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "SIGNAL");
        descriptor.put(FlowerTraceAttributes.WAIT_SIGNAL_NAME, signal.name());
        descriptor.put(FlowerTraceAttributes.WAIT_SIGNAL_KEY, signal.key());
        return Collections.unmodifiableMap(descriptor);
    }

    Map<String, Object> deadlineWaitDescriptor(long deadlineAtMillis) {
        Map<String, Object> descriptor = new LinkedHashMap<>();
        descriptor.put(FlowerTraceAttributes.WAIT_CONDITION_TYPE, "DEADLINE");
        descriptor.put(FlowerTraceAttributes.WAIT_DEADLINE_AT_MILLIS, deadlineAtMillis);
        return Collections.unmodifiableMap(descriptor);
    }

    List<Map<String, Object>> waitDescriptors(List<EventAwaitCheckpoint> awaits) {
        if (awaits == null || awaits.isEmpty()) {
            return Collections.emptyList();
        }
        List<Map<String, Object>> descriptors = new ArrayList<>();
        for (EventAwaitCheckpoint await : awaits) {
            Map<String, Object> descriptor = new LinkedHashMap<>();
            descriptor.put(FlowerTraceAttributes.WAIT_CONDITION_TYPE, await.type().name());
            switch (await.type()) {
                case EVENT:
                    descriptor.put(FlowerTraceAttributes.WAIT_EVENT_TYPE, await.eventTypeName());
                    descriptor.put(FlowerTraceAttributes.WAIT_EVENT_PREDICATE, false);
                    break;
                case SIGNAL:
                    descriptor.put(FlowerTraceAttributes.WAIT_SIGNAL_NAME, await.signalName());
                    descriptor.put(FlowerTraceAttributes.WAIT_SIGNAL_KEY, await.signalKey());
                    break;
                case DEADLINE:
                    descriptor.put(
                            FlowerTraceAttributes.WAIT_DEADLINE_AT_MILLIS,
                            await.deadlineAtMillis());
                    break;
                default:
                    throw new IllegalStateException("Unknown await checkpoint type: " + await.type());
            }
            descriptors.add(Collections.unmodifiableMap(descriptor));
        }
        return immutableDescriptors(descriptors);
    }

    List<Map<String, Object>> immutableDescriptors(List<Map<String, Object>> descriptors) {
        return Collections.unmodifiableList(new ArrayList<>(descriptors));
    }

    List<Map<String, Object>> withoutDeadlineDescriptors(
            List<Map<String, Object>> descriptors) {
        if (descriptors.isEmpty()) {
            return descriptors;
        }
        List<Map<String, Object>> remaining = new ArrayList<>();
        for (Map<String, Object> descriptor : descriptors) {
            if (!"DEADLINE".equals(descriptor.get(FlowerTraceAttributes.WAIT_CONDITION_TYPE))) {
                remaining.add(descriptor);
            }
        }
        return immutableDescriptors(remaining);
    }

    private void emit(
            EventFlow flow,
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
        listeners.traceEvent(flow, event);
    }

    private FlowTraceState state(EventFlow flow) {
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

    private FlowTraceState existingState(EventFlow flow) {
        synchronized (states) {
            return states.get(flow);
        }
    }

    private static Map<String, Object> checkpointAttributes(
            EventFlowCheckpoint checkpoint,
            String action) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        attributes.put(FlowerTraceAttributes.CHECKPOINT_ACTION, action);
        attributes.put(
                FlowerTraceAttributes.CHECKPOINT_UPDATED_AT_MILLIS,
                checkpoint.updatedAtMillis());
        attributes.put(
                FlowerTraceAttributes.CHECKPOINT_STEP_ENTERED,
                checkpoint.currentStepEntered());
        attributes.put(
                FlowerTraceAttributes.CHECKPOINT_AWAIT_GENERATION,
                checkpoint.awaitGeneration());
        if (checkpoint.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, checkpoint.currentStepId());
        }
        return attributes;
    }

    private Map<String, Object> recoveryAttributes(EventFlowCheckpoint checkpoint) {
        Map<String, Object> attributes = checkpointAttributes(checkpoint, "RECOVERY_SOURCE");
        attributes.put(FlowerTraceAttributes.FLOW_RECOVERED, true);
        if (checkpoint.workerName() != null) {
            attributes.put(FlowerTraceAttributes.FLOW_RECOVERY_WORKER, checkpoint.workerName());
        }
        attributes.put(
                FlowerTraceAttributes.FLOW_RECOVERY_CHECKPOINT_AT_MILLIS,
                checkpoint.updatedAtMillis());
        attributes.put(FlowerTraceAttributes.WAIT_GENERATION, checkpoint.awaitGeneration());
        attributes.put(FlowerTraceAttributes.WAIT_CONDITIONS, waitDescriptors(checkpoint.awaits()));
        return attributes;
    }

    private static Map<String, Object> flowPositionAttributes(EventFlow flow) {
        Map<String, Object> attributes = new LinkedHashMap<>();
        if (flow.currentStepId() != null) {
            attributes.put(FlowerTraceAttributes.STEP_ID, flow.currentStepId());
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
        private long sequence;
        private long stepSequence;
        private String currentStepRunId;
        private boolean started;
        private boolean waiting;

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

        private void markRecovered() {
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
