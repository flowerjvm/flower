package io.github.flowerjvm.flower.eventloop;

import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpoint;
import io.github.flowerjvm.flower.eventloop.persistence.EventFlowCheckpointStore;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeEventFlowCheckpointStore implements EventFlowCheckpointStore {

    private final Map<FlowId, EventFlowCheckpoint> checkpoints = new LinkedHashMap<>();
    private final List<EventFlowCheckpoint> saves = new ArrayList<>();
    private final List<FlowId> deletes = new ArrayList<>();
    private RuntimeException saveFailure;
    private RuntimeException failSaveOnCallFailure;
    private RuntimeException deleteFailure;
    private int failSaveOnCall = -1;
    private int saveCalls;

    @Override
    public void save(EventFlowCheckpoint checkpoint) {
        saveCalls++;
        if (saveFailure != null || saveCalls == failSaveOnCall) {
            RuntimeException failure = saveFailure != null
                    ? saveFailure
                    : failSaveOnCallFailure == null
                            ? new IllegalStateException("save failed")
                            : failSaveOnCallFailure;
            throw failure;
        }
        saves.add(checkpoint);
        checkpoints.put(checkpoint.flowId(), checkpoint);
    }

    @Override
    public void delete(FlowId flowId) {
        if (deleteFailure != null) {
            deletes.add(flowId);
            throw deleteFailure;
        }
        checkpoints.remove(flowId);
        deletes.add(flowId);
    }

    @Override
    public Optional<EventFlowCheckpoint> find(FlowId flowId) {
        return Optional.ofNullable(checkpoints.get(flowId));
    }

    @Override
    public List<EventFlowCheckpoint> findActive() {
        List<EventFlowCheckpoint> out = new ArrayList<>();
        for (EventFlowCheckpoint checkpoint : checkpoints.values()) {
            if (checkpoint.state() == FlowState.RUNNING) {
                out.add(checkpoint);
            }
        }
        return out;
    }

    @Override
    public List<EventFlowCheckpoint> findActiveByWorker(String workerName) {
        List<EventFlowCheckpoint> out = new ArrayList<>();
        for (EventFlowCheckpoint checkpoint : checkpoints.values()) {
            if (checkpoint.state() == FlowState.RUNNING
                    && workerName.equals(checkpoint.workerName())) {
                out.add(checkpoint);
            }
        }
        return out;
    }

    int deleteCount() {
        return deletes.size();
    }

    int saveCount() {
        return saveCalls;
    }

    List<EventFlowCheckpoint> saves() {
        return new ArrayList<>(saves);
    }

    void failSavesWith(RuntimeException failure) {
        this.saveFailure = failure;
    }

    void failSaveOnCall(int call, RuntimeException failure) {
        this.failSaveOnCall = call;
        this.failSaveOnCallFailure = failure;
    }

    void failDeletesWith(RuntimeException failure) {
        this.deleteFailure = failure;
    }
}
