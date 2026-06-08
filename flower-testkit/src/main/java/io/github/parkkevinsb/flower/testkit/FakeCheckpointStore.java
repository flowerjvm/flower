package io.github.parkkevinsb.flower.testkit;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory {@link FlowCheckpointStore} for deterministic tests.
 */
public final class FakeCheckpointStore implements FlowCheckpointStore {

    private final Map<FlowId, FlowCheckpoint> checkpoints = new LinkedHashMap<>();
    private final List<FlowCheckpoint> saves = new ArrayList<>();
    private final List<FlowId> deletes = new ArrayList<>();

    @Override
    public synchronized void save(FlowCheckpoint checkpoint) {
        if (checkpoint == null) {
            throw new IllegalArgumentException("checkpoint must not be null");
        }
        checkpoints.put(checkpoint.flowId(), checkpoint);
        saves.add(checkpoint);
    }

    @Override
    public synchronized void delete(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        checkpoints.remove(flowId);
        deletes.add(flowId);
    }

    @Override
    public synchronized Optional<FlowCheckpoint> find(FlowId flowId) {
        return Optional.ofNullable(checkpoints.get(flowId));
    }

    @Override
    public synchronized List<FlowCheckpoint> findActive() {
        return Collections.unmodifiableList(new ArrayList<>(checkpoints.values()));
    }

    @Override
    public synchronized List<FlowCheckpoint> findActiveByWorker(String workerName) {
        List<FlowCheckpoint> out = new ArrayList<>();
        for (FlowCheckpoint checkpoint : checkpoints.values()) {
            if (workerName == null
                    ? checkpoint.workerName() == null
                    : workerName.equals(checkpoint.workerName())) {
                out.add(checkpoint);
            }
        }
        return Collections.unmodifiableList(out);
    }

    public synchronized FlowCheckpoint get(FlowId flowId) {
        return checkpoints.get(flowId);
    }

    public synchronized boolean contains(FlowId flowId) {
        return checkpoints.containsKey(flowId);
    }

    public synchronized int size() {
        return checkpoints.size();
    }

    public synchronized List<FlowCheckpoint> saves() {
        return Collections.unmodifiableList(new ArrayList<>(saves));
    }

    public synchronized List<FlowId> deletes() {
        return Collections.unmodifiableList(new ArrayList<>(deletes));
    }

    public synchronized void clear() {
        checkpoints.clear();
        saves.clear();
        deletes.clear();
    }
}
