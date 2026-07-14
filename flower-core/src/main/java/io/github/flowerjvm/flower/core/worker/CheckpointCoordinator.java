package io.github.flowerjvm.flower.core.worker;

import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.time.Clock;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.function.Consumer;

/**
 * Coordinates durable Flow checkpoint persistence for one Worker.
 */
final class CheckpointCoordinator {

    private final String workerName;
    private final Clock clock;
    private final FlowCheckpointStore checkpointStore;
    private final Consumer<Throwable> workerErrorReporter;
    private final ConcurrentMap<FlowId, FlowCheckpoint> lastSaved = new ConcurrentHashMap<>();

    CheckpointCoordinator(
            String workerName,
            Clock clock,
            FlowCheckpointStore checkpointStore,
            Consumer<Throwable> workerErrorReporter) {
        if (workerName == null || workerName.isEmpty()) {
            throw new IllegalArgumentException("workerName must not be null or empty");
        }
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.workerName = workerName;
        this.clock = clock;
        this.checkpointStore = checkpointStore;
        this.workerErrorReporter = workerErrorReporter == null
                ? new Consumer<Throwable>() {
                    @Override
                    public void accept(Throwable ignored) {
                    }
                }
                : workerErrorReporter;
    }

    boolean saveActive(Flow flow) {
        if (!requiresCheckpoint(flow)) {
            return true;
        }
        FlowCheckpoint checkpoint = flow.checkpoint(workerName, clock.currentTimeMillis());
        FlowCheckpoint previous = lastSaved.get(flow.flowId());
        if (checkpoint.sameStoredPositionAs(previous)) {
            return true;
        }
        try {
            checkpointStore.save(checkpoint);
            lastSaved.put(flow.flowId(), checkpoint);
            return true;
        } catch (Throwable t) {
            markCheckpointFailed(flow, t, "save");
            return false;
        }
    }

    boolean saveTerminalTombstone(Flow flow) {
        if (!requiresCheckpoint(flow)) {
            return true;
        }
        if (flow.state() == FlowState.CHECKPOINT_FAILED) {
            return false;
        }
        FlowCheckpoint checkpoint = flow.checkpoint(workerName, clock.currentTimeMillis());
        try {
            checkpointStore.save(checkpoint);
            lastSaved.put(flow.flowId(), checkpoint);
            return true;
        } catch (Throwable t) {
            markCheckpointFailed(flow, t, "terminal save");
            return false;
        }
    }

    void cleanupTerminalTombstoneBestEffort(Flow flow) {
        if (!requiresCheckpoint(flow)) {
            return;
        }
        try {
            checkpointStore.delete(flow.flowId());
        } catch (Throwable t) {
            reportWorkerError(t);
            System.err.println("[flower] worker " + workerName + " terminal checkpoint cleanup failed for "
                    + flow.flowId() + ": " + t);
        } finally {
            forget(flow.flowId());
        }
    }

    boolean supportsDurableFlows() {
        return checkpointStore.capabilities().durable();
    }

    void forget(FlowId flowId) {
        if (flowId != null) {
            lastSaved.remove(flowId);
        }
    }

    private boolean requiresCheckpoint(Flow flow) {
        return flow.persistence() == FlowPersistence.DURABLE;
    }

    private void markCheckpointFailed(Flow flow, Throwable cause, String action) {
        forget(flow.flowId());
        flow.checkpointFailed(cause);
        reportWorkerError(cause);
        System.err.println("[flower] worker " + workerName + " checkpoint " + action + " failed for "
                + flow.flowId() + ": " + cause);
    }

    private void reportWorkerError(Throwable cause) {
        try {
            workerErrorReporter.accept(cause);
        } catch (Throwable ignored) {
        }
    }
}
