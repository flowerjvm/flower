package io.github.parkkevinsb.flower.eventloop.recovery;

import io.github.parkkevinsb.flower.core.worker.DuplicatePolicy;
import io.github.parkkevinsb.flower.eventloop.flow.EventFlow;
import io.github.parkkevinsb.flower.eventloop.worker.EventWorker;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;

import java.util.List;

/**
 * Helper for manually recovering durable {@link EventFlow}s from checkpoints.
 *
 * <p>It rebuilds fresh flow definitions and submits them to the chosen
 * {@link EventWorker}. It does not create schema, lock rows, or start workers.
 */
public final class EventFlowRecoveryService {

    private final EventFlowCheckpointStore checkpointStore;
    private final EventFlowFactoryRegistry registry;

    public static EventFlowRecoveryService create(
            EventFlowCheckpointStore checkpointStore,
            EventFlowFactoryRegistry registry) {
        return new EventFlowRecoveryService(checkpointStore, registry);
    }

    public EventFlowRecoveryService(
            EventFlowCheckpointStore checkpointStore,
            EventFlowFactoryRegistry registry) {
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.checkpointStore = checkpointStore;
        this.registry = registry;
    }

    public EventFlow recover(EventFlowCheckpoint checkpoint, EventWorker worker) {
        return recover(checkpoint, worker, DuplicatePolicy.REJECT);
    }

    public EventFlow recover(EventFlowCheckpoint checkpoint, EventWorker worker, DuplicatePolicy policy) {
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        EventFlow flow = registry.recover(checkpoint);
        worker.submit(flow, policy);
        return flow;
    }

    public int recoverActive(EventWorker worker) {
        return recoverAll(checkpointStore.findActive(), worker);
    }

    public int recoverActive(EventWorker worker, DuplicatePolicy policy) {
        return recoverAll(checkpointStore.findActive(), worker, policy);
    }

    public int recoverActiveForWorker(EventWorker worker) {
        return recoverActiveForWorker(worker, DuplicatePolicy.REJECT);
    }

    public int recoverActiveForWorker(EventWorker worker, DuplicatePolicy policy) {
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        return recoverAll(checkpointStore.findActiveByWorker(worker.name()), worker, policy);
    }

    private int recoverAll(List<EventFlowCheckpoint> checkpoints, EventWorker worker) {
        return recoverAll(checkpoints, worker, DuplicatePolicy.REJECT);
    }

    private int recoverAll(List<EventFlowCheckpoint> checkpoints, EventWorker worker, DuplicatePolicy policy) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (EventFlowCheckpoint checkpoint : checkpoints) {
            recover(checkpoint, worker, policy);
            recovered++;
        }
        return recovered;
    }
}
