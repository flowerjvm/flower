package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * Read-only view of a {@link Flow} captured at a point in time.
 *
 * <p>Used by listeners and dump APIs so that observers cannot mutate Flow
 * state. Snapshots are immutable - they reflect the values that were live
 * at capture time, not the latest values.
 */
public final class FlowSnapshot {

    private final FlowId flowId;
    private final FlowState state;
    private final String currentStepId;
    private final int currentStepIndex;
    private final int currentStepNo;
    private final List<FlowStepSnapshot> steps;
    private final Throwable failureCause;
    private final ExecutionContext executionContext;

    public FlowSnapshot(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            Throwable failureCause) {
        this(flowId, state, currentStepId, -1, currentStepNo,
                Collections.<FlowStepSnapshot>emptyList(), failureCause, ExecutionContext.empty());
    }

    public FlowSnapshot(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            Throwable failureCause,
            ExecutionContext executionContext) {
        this(flowId, state, currentStepId, -1, currentStepNo,
                Collections.<FlowStepSnapshot>emptyList(), failureCause, executionContext);
    }

    public FlowSnapshot(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepIndex,
            int currentStepNo,
            List<FlowStepSnapshot> steps,
            Throwable failureCause,
            ExecutionContext executionContext) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        this.flowId = flowId;
        this.state = state;
        this.currentStepId = currentStepId;
        this.currentStepIndex = currentStepIndex;
        this.currentStepNo = currentStepNo;
        this.steps = steps == null
                ? Collections.<FlowStepSnapshot>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(steps));
        this.failureCause = failureCause;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
    }

    public FlowId flowId() {
        return flowId;
    }

    public FlowState state() {
        return state;
    }

    /**
     * @return Flow-level Step id of the current Step, or {@code null} if
     *         the Flow has not entered any Step yet or has terminated.
     */
    public String currentStepId() {
        return currentStepId;
    }

    /**
     * @return zero-based index of the current Step in the Flow definition, or
     *         {@code -1} when there is no current Step.
     */
    public int currentStepIndex() {
        return currentStepIndex;
    }

    public int currentStepNo() {
        return currentStepNo;
    }

    /**
     * @return Flow Step definitions in declaration order. This list is empty
     *         for lightweight lifecycle-event snapshots.
     */
    public List<FlowStepSnapshot> steps() {
        return steps;
    }

    /**
     * @return failure cause if {@link FlowState#FAILED} or
     *         {@link FlowState#CHECKPOINT_FAILED}, otherwise {@code null}.
     */
    public Throwable failureCause() {
        return failureCause;
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public String toString() {
        return "FlowSnapshot{" + flowId + " " + state
                + (currentStepId != null
                        ? " @" + currentStepId + stepIndexText() + "/no=" + currentStepNo
                        : "")
                + (!executionContext.isEmpty() ? " " + executionContext : "")
                + (failureCause != null ? " cause=" + failureCause : "")
                + "}";
    }

    private String stepIndexText() {
        return currentStepIndex >= 0 ? "[" + currentStepIndex + "]" : "";
    }
}
