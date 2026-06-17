package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowState;

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
    private final ExecutionContext executionContext;
    private final List<EventStepDefinition> steps;
    private final Map<String, Integer> indexById;

    private volatile FlowState state = FlowState.CREATED;
    private volatile int currentIndex = -1;
    private volatile Throwable failureCause;

    EventFlow(FlowId flowId, ExecutionContext executionContext, List<EventStepDefinition> steps) {
        this.flowId = flowId;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
        this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
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

    public FlowState state() {
        return state;
    }

    public Throwable failureCause() {
        return failureCause;
    }

    public String currentStepId() {
        if (currentIndex < 0 || currentIndex >= steps.size() || state.isTerminal()) {
            return null;
        }
        return steps.get(currentIndex).stepId();
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
