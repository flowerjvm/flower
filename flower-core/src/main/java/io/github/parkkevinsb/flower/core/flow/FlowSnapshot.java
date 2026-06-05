package io.github.parkkevinsb.flower.core.flow;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;

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
    private final int currentStepNo;
    private final Throwable failureCause;
    private final ExecutionContext executionContext;

    public FlowSnapshot(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            Throwable failureCause) {
        this(flowId, state, currentStepId, currentStepNo, failureCause, ExecutionContext.empty());
    }

    public FlowSnapshot(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
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
        this.currentStepNo = currentStepNo;
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

    public int currentStepNo() {
        return currentStepNo;
    }

    /**
     * @return failure cause if {@link FlowState#FAILED}, otherwise {@code null}.
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
                + (currentStepId != null ? " @" + currentStepId + "/no=" + currentStepNo : "")
                + (!executionContext.isEmpty() ? " " + executionContext : "")
                + (failureCause != null ? " cause=" + failureCause : "")
                + "}";
    }
}
