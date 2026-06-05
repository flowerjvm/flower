package io.github.parkkevinsb.flower.core.persistence;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;

/**
 * Durable position of one Flow.
 *
 * <p>A checkpoint is intentionally small: it remembers where Flower should
 * resume ticking, not the complete Java object graph, event history, or Step
 * instance fields.
 */
public final class FlowCheckpoint {

    private final FlowId flowId;
    private final FlowState state;
    private final String currentStepId;
    private final int currentStepNo;
    private final boolean currentStepEntered;
    private final FlowPersistence persistence;
    private final String workerName;
    private final long updatedAtMillis;
    private final String definitionVersion;
    private final ExecutionContext executionContext;

    public FlowCheckpoint(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            boolean currentStepEntered,
            FlowPersistence persistence,
            String workerName,
            long updatedAtMillis) {
        this(flowId, state, currentStepId, currentStepNo, currentStepEntered,
                persistence, workerName, updatedAtMillis, null);
    }

    public FlowCheckpoint(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            boolean currentStepEntered,
            FlowPersistence persistence,
            String workerName,
            long updatedAtMillis,
            String definitionVersion) {
        this(flowId, state, currentStepId, currentStepNo, currentStepEntered,
                persistence, workerName, updatedAtMillis, definitionVersion, ExecutionContext.empty());
    }

    public FlowCheckpoint(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            int currentStepNo,
            boolean currentStepEntered,
            FlowPersistence persistence,
            String workerName,
            long updatedAtMillis,
            String definitionVersion,
            ExecutionContext executionContext) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (currentStepNo < 0) {
            throw new IllegalArgumentException("currentStepNo must not be negative: " + currentStepNo);
        }
        if (persistence == null) {
            throw new IllegalArgumentException("persistence must not be null");
        }
        if (currentStepEntered && (currentStepId == null || currentStepId.isEmpty())) {
            throw new IllegalArgumentException("currentStepId is required when currentStepEntered is true");
        }
        if (state == FlowState.RUNNING && (currentStepId == null || currentStepId.isEmpty())) {
            throw new IllegalArgumentException("currentStepId is required for RUNNING checkpoints");
        }
        this.flowId = flowId;
        this.state = state;
        this.currentStepId = currentStepId;
        this.currentStepNo = currentStepNo;
        this.currentStepEntered = currentStepEntered;
        this.persistence = persistence;
        this.workerName = workerName;
        this.updatedAtMillis = updatedAtMillis;
        this.definitionVersion = definitionVersion;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
    }

    public FlowId flowId() {
        return flowId;
    }

    public FlowState state() {
        return state;
    }

    public String currentStepId() {
        return currentStepId;
    }

    public int currentStepNo() {
        return currentStepNo;
    }

    public boolean currentStepEntered() {
        return currentStepEntered;
    }

    public FlowPersistence persistence() {
        return persistence;
    }

    public String workerName() {
        return workerName;
    }

    public long updatedAtMillis() {
        return updatedAtMillis;
    }

    public String definitionVersion() {
        return definitionVersion;
    }

    public ExecutionContext executionContext() {
        return executionContext;
    }

    @Override
    public String toString() {
        return "FlowCheckpoint{" + flowId + " " + state
                + (currentStepId != null ? " @" + currentStepId + "/no=" + currentStepNo : "")
                + (currentStepEntered ? " entered" : " not-entered")
                + ", persistence=" + persistence
                + (workerName != null ? ", worker=" + workerName : "")
                + (definitionVersion != null ? ", definitionVersion=" + definitionVersion : "")
                + (!executionContext.isEmpty() ? ", " + executionContext : "")
                + ", updatedAtMillis=" + updatedAtMillis
                + "}";
    }
}
