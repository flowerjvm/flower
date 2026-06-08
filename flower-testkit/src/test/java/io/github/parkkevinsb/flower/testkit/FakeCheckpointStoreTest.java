package io.github.parkkevinsb.flower.testkit;

import io.github.parkkevinsb.flower.core.flow.FlowId;
import io.github.parkkevinsb.flower.core.flow.FlowPersistence;
import io.github.parkkevinsb.flower.core.flow.FlowState;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpoint;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FakeCheckpointStoreTest {

    @Test
    void stores_finds_filters_and_deletes_checkpoints() {
        FakeCheckpointStore store = new FakeCheckpointStore();
        FlowCheckpoint first = checkpoint("order", "O-1", "orders");
        FlowCheckpoint second = checkpoint("order", "O-2", "other");

        store.save(first);
        store.save(second);
        store.delete(first.flowId());

        assertThat(store.find(first.flowId())).isEmpty();
        assertThat(store.find(second.flowId())).contains(second);
        assertThat(store.findActive()).containsExactly(second);
        assertThat(store.findActiveByWorker("other")).containsExactly(second);
        assertThat(store.saves()).containsExactly(first, second);
        assertThat(store.deletes()).containsExactly(first.flowId());
    }

    private static FlowCheckpoint checkpoint(String flowType, String flowKey, String workerName) {
        return new FlowCheckpoint(
                FlowId.of(flowType, flowKey),
                FlowState.RUNNING,
                "step",
                1,
                true,
                FlowPersistence.DURABLE,
                workerName,
                100L);
    }
}
