package io.github.flowerjvm.flower.eventloop.persistence;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.persistence.CheckpointStoreCapabilities;
import io.github.flowerjvm.flower.eventloop.flow.EventFlow;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for durable {@link EventFlow} checkpoints.
 *
 * <p>This SPI is separate from core's tick-flow checkpoint store because the
 * payload is different: event-loop recovery must remember pending awaits.
 *
 * <p>{@link #save(EventFlowCheckpoint)} may receive either an active checkpoint
 * or a terminal tombstone. {@link #findActive()} and
 * {@link #findActiveByWorker(String)} must return only recoverable active
 * checkpoints. {@link #delete(FlowId)} is cleanup/compaction, not the
 * correctness boundary for terminal flows.
 */
public interface EventFlowCheckpointStore {

    EventFlowCheckpointStore NOOP = new NoopEventFlowCheckpointStore();

    /**
     * Capabilities advertised by this store.
     */
    default CheckpointStoreCapabilities capabilities() {
        return CheckpointStoreCapabilities.durableOnly();
    }

    void save(EventFlowCheckpoint checkpoint);

    void delete(FlowId flowId);

    default Optional<EventFlowCheckpoint> find(FlowId flowId) {
        return Optional.empty();
    }

    default List<EventFlowCheckpoint> findActive() {
        return Collections.emptyList();
    }

    default List<EventFlowCheckpoint> findActiveByWorker(String workerName) {
        return Collections.emptyList();
    }
}

final class NoopEventFlowCheckpointStore implements EventFlowCheckpointStore {
    @Override
    public CheckpointStoreCapabilities capabilities() {
        return CheckpointStoreCapabilities.none();
    }

    @Override
    public void save(EventFlowCheckpoint checkpoint) {
    }

    @Override
    public void delete(FlowId flowId) {
    }
}
