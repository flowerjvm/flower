package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.flow.FlowStepSnapshot;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * An ordered sequence of {@link EventStep}s for one domain instance, driven by
 * an {@link EventWorker}.
 *
 * <p>The flow holds position and lifecycle state. The {@link EventWorker} owns
 * the await registrations (event subscriptions and deadlines) and applies step
 * transitions. A flow is not thread-safe; it is owned by one worker.
 *
 * <p>State reuses core {@link FlowState} so event-driven and tick-driven flows
 * share the same lifecycle vocabulary.
 */
public final class EventFlow {

    private final FlowId flowId;
    private ExecutionContext executionContext;
    private final List<EventStepDefinition> steps;
    private final Map<String, Integer> indexById;
    private final FlowPersistence persistence;
    private final String definitionVersion;

    private volatile FlowState state = FlowState.CREATED;
    private volatile int currentIndex = -1;
    private volatile Throwable failureCause;
    private EventFlowCheckpoint recoveryCheckpoint;

    EventFlow(
            FlowId flowId,
            ExecutionContext executionContext,
            List<EventStepDefinition> steps,
            FlowPersistence persistence,
            String definitionVersion) {
        this.flowId = flowId;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        this.persistence = persistence == null ? FlowPersistence.TRANSIENT : persistence;
        this.definitionVersion = definitionVersion;
        Map<String, Integer> idx = new HashMap<>();
        for (int i = 0; i < this.steps.size(); i++) {
            idx.put(this.steps.get(i).stepId(), i);
        }
        this.indexById = Collections.unmodifiableMap(idx);
    }

    public static EventFlowBuilder builder(String flowType, String flowKey) {
        return new EventFlowBuilder(flowType, flowKey);
    }

    public FlowId flowId() {
        return flowId;
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    public FlowPersistence persistence() {
        return persistence;
    }

    public String definitionVersion() {
        return definitionVersion;
    }

    public FlowState state() {
        return state;
    }

    public Throwable failureCause() {
        return failureCause;
    }

    public FlowSnapshot snapshot() {
        return new FlowSnapshot(
                flowId,
                state,
                currentStepId(),
                state.isTerminal() ? -1 : currentIndex,
                0,
                stepSnapshots(),
                failureCause,
                executionContext);
    }

    /**
     * Prepare this freshly-built durable flow to resume from a saved checkpoint.
     *
     * <p>The flow must still be in {@link FlowState#CREATED}. The worker later
     * activates the checkpoint and calls {@link EventStep#onRecover}.
     */
    public EventFlow recoverFrom(EventFlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        if (state != FlowState.CREATED) {
            throw new IllegalStateException("EventFlow can only recover before submit, state=" + state);
        }
        if (persistence != FlowPersistence.DURABLE) {
            throw new IllegalStateException("Only durable event flows can recover from checkpoints: " + flowId);
        }
        if (checkpoint.persistence() != FlowPersistence.DURABLE) {
            throw new IllegalArgumentException("checkpoint is not durable: " + checkpoint);
        }
        if (!flowId.equals(checkpoint.flowId())) {
            throw new IllegalArgumentException(
                    "checkpoint flowId mismatch. expected=" + flowId + ", actual=" + checkpoint.flowId());
        }
        if (checkpoint.state().isTerminal()) {
            throw new IllegalArgumentException("terminal checkpoints cannot be recovered: " + checkpoint);
        }
        if (checkpoint.state() != FlowState.RUNNING) {
            throw new IllegalArgumentException("event-flow checkpoint must be RUNNING: " + checkpoint);
        }
        if (!indexById.containsKey(checkpoint.currentStepId())) {
            throw new IllegalArgumentException(
                    "checkpoint stepId not found in flow: " + checkpoint.currentStepId());
        }
        if (definitionVersion != null
                && checkpoint.definitionVersion() != null
                && !definitionVersion.equals(checkpoint.definitionVersion())) {
            throw new IllegalArgumentException(
                    "checkpoint definitionVersion mismatch. expected=" + definitionVersion
                            + ", actual=" + checkpoint.definitionVersion());
        }
        if (!checkpoint.executionContext().isEmpty()) {
            this.executionContext = checkpoint.executionContext();
        }
        this.recoveryCheckpoint = checkpoint;
        return this;
    }

    public String currentStepId() {
        if (currentIndex < 0 || currentIndex >= steps.size() || state.isTerminal()) {
            return null;
        }
        return steps.get(currentIndex).stepId();
    }

    private List<FlowStepSnapshot> stepSnapshots() {
        List<FlowStepSnapshot> out = new ArrayList<>();
        boolean recoverable = persistence == FlowPersistence.DURABLE;
        for (int i = 0; i < steps.size(); i++) {
            EventStepDefinition def = steps.get(i);
            out.add(new FlowStepSnapshot(
                    i,
                    def.stepId(),
                    def.step().getClass().getName(),
                    false,
                    recoverable,
                    null));
        }
        return Collections.unmodifiableList(out);
    }

    // ------------------------------------------------------------------
    // Worker-facing (package-private) state transitions
    // ------------------------------------------------------------------

    List<EventStepDefinition> steps() {
        return steps;
    }

    int currentIndex() {
        return currentIndex;
    }

    EventStepDefinition currentStep() {
        if (currentIndex < 0 || currentIndex >= steps.size()) {
            return null;
        }
        return steps.get(currentIndex);
    }

    Integer indexOf(String stepId) {
        return indexById.get(stepId);
    }

    EventFlowCheckpoint recoveryCheckpoint() {
        return recoveryCheckpoint;
    }

    void clearRecoveryCheckpoint() {
        recoveryCheckpoint = null;
    }

    void activateRecoveryCheckpoint(EventFlowCheckpoint checkpoint) {
        Integer index = indexById.get(checkpoint.currentStepId());
        if (index == null) {
            throw new IllegalStateException(
                    "checkpoint stepId not found in flow: " + checkpoint.currentStepId());
        }
        this.state = checkpoint.state();
        this.currentIndex = index;
    }

    void markRunningAt(int index) {
        this.state = FlowState.RUNNING;
        this.currentIndex = index;
    }

    void setCurrentIndex(int index) {
        this.currentIndex = index;
    }

    void finish() {
        this.state = FlowState.FINISHED;
    }

    void fail(Throwable cause) {
        this.failureCause = cause;
        this.state = FlowState.FAILED;
    }

    void cancel() {
        this.state = FlowState.CANCELLED;
    }
}
