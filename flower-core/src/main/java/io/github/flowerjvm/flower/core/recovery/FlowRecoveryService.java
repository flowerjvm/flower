package io.github.flowerjvm.flower.core.recovery;

import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.worker.DuplicatePolicy;
import io.github.flowerjvm.flower.core.worker.Worker;

import java.util.List;

/**
 * Small helper for manually recovering durable Flows from checkpoints.
 *
 * <p>It does not start Workers, create schema, lock rows, or delete failed
 * checkpoints. It only rebuilds fresh Flow instances and submits them to the
 * Worker chosen by the application.
 */
public final class FlowRecoveryService {

    private final FlowCheckpointStore checkpointStore;
    private final FlowFactoryRegistry registry;

    public static FlowRecoveryService create(
            FlowCheckpointStore checkpointStore,
            FlowFactoryRegistry registry) {
        return new FlowRecoveryService(checkpointStore, registry);
    }

    public FlowRecoveryService(
            FlowCheckpointStore checkpointStore,
            FlowFactoryRegistry registry) {
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        this.checkpointStore = checkpointStore;
        this.registry = registry;
    }

    public Flow recover(FlowCheckpoint checkpoint, Worker worker) {
        return recover(checkpoint, worker, DuplicatePolicy.REJECT);
    }

    public Flow recover(FlowCheckpoint checkpoint, Worker worker, DuplicatePolicy policy) {
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        Flow flow = registry.recover(checkpoint);
        worker.submit(flow, policy);
        return flow;
    }

    public int recoverActive(Worker worker) {
        return recoverAll(checkpointStore.findActive(), worker, DuplicatePolicy.REJECT);
    }

    public int recoverActive(Worker worker, DuplicatePolicy policy) {
        return recoverAll(checkpointStore.findActive(), worker, policy);
    }

    public int recoverActiveForWorker(Worker worker) {
        return recoverActiveForWorker(worker, DuplicatePolicy.REJECT);
    }

    public int recoverActiveForWorker(Worker worker, DuplicatePolicy policy) {
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        return recoverAll(checkpointStore.findActiveByWorker(worker.name()), worker, policy);
    }

    private int recoverAll(List<FlowCheckpoint> checkpoints, Worker worker, DuplicatePolicy policy) {
        if (checkpoints == null || checkpoints.isEmpty()) {
            return 0;
        }
        int recovered = 0;
        for (FlowCheckpoint checkpoint : checkpoints) {
            recover(checkpoint, worker, policy);
            recovered++;
        }
        return recovered;
    }
}
