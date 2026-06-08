package io.github.parkkevinsb.flower.testkit;

import io.github.parkkevinsb.flower.core.context.ExecutionContext;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.flow.FlowState;

import java.util.Optional;

/**
 * Tiny assertion helper for Flow snapshots.
 *
 * <p>No assertion library is required at runtime. Failed checks throw
 * {@link AssertionError}, so this helper works with JUnit, AssertJ, or plain
 * test code.
 */
public final class FlowAssertions {

    private final FlowId flowId;
    private final Optional<FlowSnapshot> activeSnapshot;
    private final Optional<FlowSnapshot> latestSnapshot;

    FlowAssertions(
            FlowId flowId,
            Optional<FlowSnapshot> activeSnapshot,
            Optional<FlowSnapshot> latestSnapshot) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        this.flowId = flowId;
        this.activeSnapshot = activeSnapshot == null ? Optional.empty() : activeSnapshot;
        this.latestSnapshot = latestSnapshot == null ? Optional.empty() : latestSnapshot;
    }

    public FlowAssertions exists() {
        snapshot();
        return this;
    }

    public FlowAssertions isActive() {
        if (!activeSnapshot.isPresent()) {
            fail("Expected active flow " + flowId + " but it was not active");
        }
        return this;
    }

    public FlowAssertions isNotActive() {
        if (activeSnapshot.isPresent()) {
            fail("Expected flow " + flowId + " to be inactive but it was active");
        }
        return this;
    }

    public FlowAssertions stateIs(FlowState expected) {
        if (expected == null) {
            throw new IllegalArgumentException("expected must not be null");
        }
        FlowState actual = snapshot().state();
        if (actual != expected) {
            fail("Expected flow " + flowId + " state " + expected + " but was " + actual);
        }
        return this;
    }

    public FlowAssertions isReady() {
        return stateIs(FlowState.READY);
    }

    public FlowAssertions isRunning() {
        return stateIs(FlowState.RUNNING);
    }

    public FlowAssertions isFinished() {
        return stateIs(FlowState.FINISHED);
    }

    public FlowAssertions isFailed() {
        return stateIs(FlowState.FAILED);
    }

    public FlowAssertions isCancelled() {
        return stateIs(FlowState.CANCELLED);
    }

    public FlowAssertions currentStepIs(String expectedStepId) {
        FlowSnapshot snapshot = snapshot();
        String actual = snapshot.currentStepId();
        if (expectedStepId == null ? actual != null : !expectedStepId.equals(actual)) {
            fail("Expected flow " + flowId + " currentStepId " + expectedStepId + " but was " + actual);
        }
        return this;
    }

    public FlowAssertions stepNoIs(int expectedStepNo) {
        int actual = snapshot().currentStepNo();
        if (actual != expectedStepNo) {
            fail("Expected flow " + flowId + " stepNo " + expectedStepNo + " but was " + actual);
        }
        return this;
    }

    public FlowAssertions tenantIdIs(String expectedTenantId) {
        return contextValueIs("tenantId", expectedTenantId, snapshot().executionContext().tenantIdOrNull());
    }

    public FlowAssertions userIdIs(String expectedUserId) {
        return contextValueIs("userId", expectedUserId, snapshot().executionContext().userIdOrNull());
    }

    public FlowAssertions runIdIs(String expectedRunId) {
        return contextValueIs("runId", expectedRunId, snapshot().executionContext().runIdOrNull());
    }

    public FlowAssertions traceIdIs(String expectedTraceId) {
        return contextValueIs("traceId", expectedTraceId, snapshot().executionContext().traceIdOrNull());
    }

    public FlowSnapshot snapshot() {
        Optional<FlowSnapshot> best = activeSnapshot.isPresent() ? activeSnapshot : latestSnapshot;
        if (!best.isPresent()) {
            fail("No snapshot found for flow " + flowId);
        }
        return best.get();
    }

    public ExecutionContext executionContext() {
        return snapshot().executionContext();
    }

    private FlowAssertions contextValueIs(String name, String expected, String actual) {
        if (expected == null ? actual != null : !expected.equals(actual)) {
            fail("Expected flow " + flowId + " " + name + " " + expected + " but was " + actual);
        }
        return this;
    }

    private static void fail(String message) {
        throw new AssertionError(message);
    }
}
