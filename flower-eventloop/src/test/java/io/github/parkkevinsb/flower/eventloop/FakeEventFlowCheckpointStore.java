package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.flow.FlowId;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

final class FakeEventFlowCheckpointStore implements EventFlowCheckpointStore {

    private final Map<FlowId, EventFlowCheckpoint> checkpoints = new LinkedHashMap<>();
    private final List<FlowId> deletes = new ArrayList<>();

    @Override
    public void save(EventFlowCheckpoint checkpoint) {
        checkpoints.put(checkpoint.flowId(), checkpoint);
    }

    @Override
    public void delete(FlowId flowId) {
        checkpoints.remove(flowId);
        deletes.add(flowId);
    }

    @Override
    public Optional<EventFlowCheckpoint> find(FlowId flowId) {
        return Optional.ofNullable(checkpoints.get(flowId));
    }

    @Override
    public List<EventFlowCheckpoint> findActive() {
        return new ArrayList<>(checkpoints.values());
    }

    @Override
    public List<EventFlowCheckpoint> findActiveByWorker(String workerName) {
        List<EventFlowCheckpoint> out = new ArrayList<>();
        for (EventFlowCheckpoint checkpoint : checkpoints.values()) {
            if (workerName.equals(checkpoint.workerName())) {
                out.add(checkpoint);
            }
        }
        return out;
    }

    int deleteCount() {
        return deletes.size();
    }
}
