package io.github.parkkevinsb.flower.core.persistence;

import io.github.parkkevinsb.flower.core.flow.FlowId;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for durable Flow checkpoints.
 *
 * <p>Core only defines this SPI. JDBC, Redis, JPA, file-backed stores, and
 * schema management belong in optional modules or the host application.
 */
public interface FlowCheckpointStore {

    FlowCheckpointStore NOOP = new NoopFlowCheckpointStore();

    /**
     * Persist or replace the latest checkpoint for a non-terminal durable Flow.
     */
    void save(FlowCheckpoint checkpoint);

    /**
     * Remove the active checkpoint for a terminal or explicitly cancelled Flow.
     */
    void delete(FlowId flowId);

    /**
     * Find the latest checkpoint for one Flow id.
     */
    default Optional<FlowCheckpoint> find(FlowId flowId) {
        return Optional.empty();
    }

    /**
     * Find all active checkpoints known to this store.
     */
    default List<FlowCheckpoint> findActive() {
        return Collections.emptyList();
    }

    /**
     * Find active checkpoints last owned by the given Worker.
     */
    default List<FlowCheckpoint> findActiveByWorker(String workerName) {
        return Collections.emptyList();
    }
}

final class NoopFlowCheckpointStore implements FlowCheckpointStore {
    @Override
    public void save(FlowCheckpoint checkpoint) {
    }

    @Override
    public void delete(FlowId flowId) {
    }
}
