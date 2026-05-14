package io.github.parkkevinsb.flower.observability.awaiter;

import io.github.parkkevinsb.flower.core.flow.Flow;
import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowSnapshot;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;

import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Listener-backed helper for tests and command-line tools that need to wait
 * until a Flow reaches a terminal state.
 *
 * <p>Register one instance as a {@link FlowerListener} before submitting the
 * Flow, then call {@link #awaitTerminal(FlowId, long)}. Terminal snapshots are
 * captured from listener callbacks because Workers remove terminal Flows from
 * their active snapshots at the end of a tick.
 */
public final class FlowAwaiter implements FlowerListener {

    private final ConcurrentMap<FlowId, FlowSnapshot> terminalSnapshots = new ConcurrentHashMap<>();
    private final ConcurrentMap<FlowId, CountDownLatch> latches = new ConcurrentHashMap<>();

    public static FlowAwaiter create() {
        return new FlowAwaiter();
    }

    private FlowAwaiter() {
    }

    public FlowSnapshot awaitTerminal(Flow flow, long timeoutMillis)
            throws InterruptedException, TimeoutException {
        if (flow == null) {
            throw new IllegalArgumentException("flow must not be null");
        }
        return awaitTerminal(flow.flowId(), timeoutMillis);
    }

    public FlowSnapshot awaitTerminal(String flowType, String flowKey, long timeoutMillis)
            throws InterruptedException, TimeoutException {
        return awaitTerminal(FlowId.of(flowType, flowKey), timeoutMillis);
    }

    public FlowSnapshot awaitTerminal(FlowId flowId, long timeoutMillis)
            throws InterruptedException, TimeoutException {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        if (timeoutMillis < 0L) {
            throw new IllegalArgumentException("timeoutMillis must not be negative: " + timeoutMillis);
        }

        FlowSnapshot existing = terminalSnapshots.get(flowId);
        if (existing != null) {
            return existing;
        }

        CountDownLatch latch = latches.computeIfAbsent(flowId, id -> new CountDownLatch(1));
        existing = terminalSnapshots.get(flowId);
        if (existing != null) {
            return existing;
        }

        if (!latch.await(timeoutMillis, TimeUnit.MILLISECONDS)) {
            throw new TimeoutException(
                    "Flow did not reach a terminal state within " + timeoutMillis + "ms: " + flowId);
        }

        FlowSnapshot snapshot = terminalSnapshots.get(flowId);
        if (snapshot == null) {
            throw new TimeoutException("Flow terminal signal was received without a snapshot: " + flowId);
        }
        return snapshot;
    }

    public Optional<FlowSnapshot> terminalSnapshot(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        return Optional.ofNullable(terminalSnapshots.get(flowId));
    }

    /**
     * Clears remembered state for a Flow id so the same awaiter can observe
     * another run with the same id.
     */
    public void clear(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        terminalSnapshots.remove(flowId);
        latches.remove(flowId);
    }

    @Override
    public void onFlowFinished(FlowSnapshot flow) {
        complete(flow);
    }

    @Override
    public void onFlowFailed(FlowSnapshot flow, Throwable cause) {
        complete(flow);
    }

    @Override
    public void onFlowCancelled(FlowSnapshot flow) {
        complete(flow);
    }

    private void complete(FlowSnapshot flow) {
        if (flow == null) {
            return;
        }
        FlowId flowId = flow.flowId();
        terminalSnapshots.put(flowId, flow);
        latches.computeIfAbsent(flowId, id -> new CountDownLatch(1)).countDown();
    }
}
