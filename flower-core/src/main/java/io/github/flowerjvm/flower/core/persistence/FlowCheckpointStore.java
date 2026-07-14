package io.github.flowerjvm.flower.core.persistence;

import io.github.flowerjvm.flower.core.flow.FlowId;

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
     * Capabilities advertised by this store.
     *
     * <p>The default assumes a custom implementation really persists
     * checkpoints through {@link #save(FlowCheckpoint)} and
     * {@link #delete(FlowId)}, but does not assume recovery queries are
     * implemented because {@link #findActive()} has an empty default.
     */
    default CheckpointStoreCapabilities capabilities() {
        return CheckpointStoreCapabilities.durableOnly();
    }

    /**
     * Persist or replace the latest checkpoint for a durable Flow.
     *
     * <p>Non-terminal checkpoints ({@code READY}/{@code RUNNING}) are recovery
     * positions. Terminal checkpoints ({@code FINISHED}/{@code FAILED}/
     * {@code CANCELLED}) are tombstones: they prove a Flow reached a terminal
     * state even if later cleanup deletion fails.
     */
    void save(FlowCheckpoint checkpoint);

    /**
     * Remove a checkpoint row after it is no longer needed. For terminal Flows,
     * correctness must come from a terminal tombstone saved before this delete;
     * deletion is only cleanup/compaction.
     */
    void delete(FlowId flowId);

    /**
     * Find the latest checkpoint for one Flow id.
     */
    default Optional<FlowCheckpoint> find(FlowId flowId) {
        return Optional.empty();
    }

    /**
     * Find recoverable active checkpoints known to this store. Implementations
     * must return only {@code READY}/{@code RUNNING} checkpoints, not terminal
     * tombstones.
     */
    default List<FlowCheckpoint> findActive() {
        return Collections.emptyList();
    }

    /**
     * Find recoverable active checkpoints last owned by the given Worker.
     * Implementations must return only {@code READY}/{@code RUNNING}
     * checkpoints, not terminal tombstones.
     */
    default List<FlowCheckpoint> findActiveByWorker(String workerName) {
        return Collections.emptyList();
    }
}

final class NoopFlowCheckpointStore implements FlowCheckpointStore {
    @Override
    public CheckpointStoreCapabilities capabilities() {
        return CheckpointStoreCapabilities.none();
    }

    @Override
    public void save(FlowCheckpoint checkpoint) {
    }

    @Override
    public void delete(FlowId flowId) {
    }
}
