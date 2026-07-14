package io.github.flowerjvm.flower.eventloop.persistence;

import io.github.flowerjvm.flower.core.context.ExecutionContext;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Durable event-loop position for one {@link EventFlow}.
 *
 * <p>Unlike core tick-flow checkpoints, this records the await descriptors that
 * would need to be re-registered on recovery. It still does not serialize Java
 * step instances, subscriptions, event objects, or predicate lambdas.
 *
 * <p>Terminal states may also be stored as tombstones. A terminal tombstone is
 * not recoverable, but it prevents a completed flow from reappearing if later
 * cleanup fails.
 */
public final class EventFlowCheckpoint {

    private final FlowId flowId;
    private final FlowState state;
    private final String currentStepId;
    private final boolean currentStepEntered;
    private final FlowPersistence persistence;
    private final String workerName;
    private final long updatedAtMillis;
    private final String definitionVersion;
    private final ExecutionContext executionContext;
    private final long awaitGeneration;
    private final List<EventAwaitCheckpoint> awaits;

    public EventFlowCheckpoint(
            FlowId flowId,
            FlowState state,
            String currentStepId,
            boolean currentStepEntered,
            FlowPersistence persistence,
            String workerName,
            long updatedAtMillis,
            String definitionVersion,
            ExecutionContext executionContext,
            long awaitGeneration,
            List<EventAwaitCheckpoint> awaits) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (state == null) {
            throw new IllegalArgumentException("state must not be null");
        }
        if (persistence == null) {
            throw new IllegalArgumentException("persistence must not be null");
        }
        if (state == FlowState.RUNNING && (currentStepId == null || currentStepId.isEmpty())) {
            throw new IllegalArgumentException("currentStepId is required for RUNNING checkpoints");
        }
        if (currentStepEntered && (currentStepId == null || currentStepId.isEmpty())) {
            throw new IllegalArgumentException("currentStepId is required when currentStepEntered is true");
        }
        if (awaitGeneration < 0L) {
            throw new IllegalArgumentException("awaitGeneration must not be negative: " + awaitGeneration);
        }
        this.flowId = flowId;
        this.state = state;
        this.currentStepId = currentStepId;
        this.currentStepEntered = currentStepEntered;
        this.persistence = persistence;
        this.workerName = workerName;
        this.updatedAtMillis = updatedAtMillis;
        this.definitionVersion = definitionVersion;
        this.executionContext = executionContext == null ? ExecutionContext.empty() : executionContext;
        this.awaitGeneration = awaitGeneration;
        List<EventAwaitCheckpoint> copy = awaits == null
                ? Collections.<EventAwaitCheckpoint>emptyList()
                : new ArrayList<>(awaits);
        for (EventAwaitCheckpoint await : copy) {
            if (await == null) {
                throw new IllegalArgumentException("await checkpoint must not be null");
            }
        }
        this.awaits = Collections.unmodifiableList(copy);
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

    public long awaitGeneration() {
        return awaitGeneration;
    }

    public List<EventAwaitCheckpoint> awaits() {
        return awaits;
    }

    /**
     * Compare checkpoint fields that describe the durable stored position.
     *
     * <p>{@code updatedAtMillis} is intentionally excluded. It is a write time,
     * not part of the recovery position. When a future schema adds a persisted
     * field, decide here whether that field affects recovery/ownership/await
     * re-registration. If it does, include it in this comparison so dirty
     * checkpoint skipping stays correct.
     */
    public boolean sameStoredPositionAs(EventFlowCheckpoint other) {
        if (other == null) {
            return false;
        }
        return Objects.equals(flowId, other.flowId)
                && state == other.state
                && Objects.equals(currentStepId, other.currentStepId)
                && currentStepEntered == other.currentStepEntered
                && persistence == other.persistence
                && Objects.equals(workerName, other.workerName)
                && Objects.equals(definitionVersion, other.definitionVersion)
                && Objects.equals(executionContext, other.executionContext)
                && awaitGeneration == other.awaitGeneration
                && Objects.equals(awaits, other.awaits);
    }

    @Override
    public String toString() {
        return "EventFlowCheckpoint{" + flowId + " " + state
                + (currentStepId != null ? " @" + currentStepId : "")
                + (currentStepEntered ? " entered" : " not-entered")
                + ", awaits=" + awaits
                + ", persistence=" + persistence
                + (workerName != null ? ", worker=" + workerName : "")
                + (definitionVersion != null ? ", definitionVersion=" + definitionVersion : "")
                + (!executionContext.isEmpty() ? ", " + executionContext : "")
                + ", awaitGeneration=" + awaitGeneration
                + ", updatedAtMillis=" + updatedAtMillis
                + "}";
    }
}
