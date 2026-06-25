package io.github.parkkevinsb.flower.eventloop.persistence;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;

import java.util.Collections;
import java.util.List;
import java.util.Optional;

/**
 * Storage boundary for durable {@link EventFlow} checkpoints.
 *
 * <p>This SPI is separate from core's tick-flow checkpoint store because the
 * payload is different: event-loop recovery must remember pending awaits.
 */
public interface EventFlowCheckpointStore {

    EventFlowCheckpointStore NOOP = new NoopEventFlowCheckpointStore();

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
    public void save(EventFlowCheckpoint checkpoint) {
    }

    @Override
    public void delete(FlowId flowId) {
    }
}
