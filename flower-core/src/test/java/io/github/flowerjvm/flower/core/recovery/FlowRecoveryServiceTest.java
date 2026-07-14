package io.github.flowerjvm.flower.core.recovery;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowPersistence;
import io.github.flowerjvm.flower.core.flow.FlowState;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpointStore;
import io.github.flowerjvm.flower.core.step.RecoveryPolicy;
import io.github.flowerjvm.flower.core.step.Step;
import io.github.flowerjvm.flower.core.step.StepContext;
import io.github.flowerjvm.flower.core.step.StepResult;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.Worker;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FlowRecoveryServiceTest {

    @Test
    void recovers_active_checkpoints_into_worker() {
        ManualClock clock = new ManualClock(1_000);
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Worker worker = attachedWorker("orders", clock, store);
        FlowId id = FlowId.of("order", "42");
        store.save(checkpoint(id, "orders", 9));

        List<String> trace = new ArrayList<>();
        FlowFactoryRegistry registry = FlowFactoryRegistry.builder()
                .register("order", flowId -> orderFlow(flowId, trace))
                .build();
        FlowRecoveryService recovery = FlowRecoveryService.create(store, registry);

        int recovered = recovery.recoverActive(worker);
        worker.tickOnce();

        assertThat(recovered).isEqualTo(1);
        assertThat(trace).containsExactly("enter:42", "tick:9");
        assertThat(store.find(id)).isEmpty();
        assertThat(store.deletes).containsExactly(id);
    }

    @Test
    void recovers_only_checkpoints_last_owned_by_target_worker() {
        ManualClock clock = new ManualClock(1_000);
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Worker worker = attachedWorker("orders", clock, store);
        FlowId owned = FlowId.of("order", "owned");
        FlowId other = FlowId.of("order", "other");
        store.save(checkpoint(owned, "orders", 1));
        store.save(checkpoint(other, "other-worker", 1));

        List<String> trace = new ArrayList<>();
        FlowFactoryRegistry registry = FlowFactoryRegistry.builder()
                .register("order", flowId -> orderFlow(flowId, trace))
                .build();

        int recovered = FlowRecoveryService.create(store, registry)
                .recoverActiveForWorker(worker);
        worker.tickOnce();

        assertThat(recovered).isEqualTo(1);
        assertThat(trace).containsExactly("enter:owned", "tick:1");
        assertThat(store.find(owned)).isEmpty();
        assertThat(store.find(other)).isPresent();
    }

    @Test
    void missing_factory_fails_without_deleting_checkpoint() {
        ManualClock clock = new ManualClock(1_000);
        InMemoryCheckpointStore store = new InMemoryCheckpointStore();
        Worker worker = attachedWorker("orders", clock, store);
        FlowId id = FlowId.of("order", "42");
        store.save(checkpoint(id, "orders", 1));
        FlowRecoveryService recovery = FlowRecoveryService.create(
                store,
                FlowFactoryRegistry.builder().build());

        assertThatThrownBy(() -> recovery.recoverActive(worker))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("No FlowFactory");
        assertThat(store.find(id)).isPresent();
        assertThat(store.deletes).isEmpty();
    }

    @Test
    void registry_rejects_duplicate_flow_type() {
        FlowFactory factory = flowId -> orderFlow(flowId, new ArrayList<>());

        assertThatThrownBy(() -> FlowFactoryRegistry.builder()
                .register("order", factory)
                .register("order", factory))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("duplicate");
    }

    private static Worker attachedWorker(String name, ManualClock clock, FlowCheckpointStore store) {
        Worker worker = Worker.builder(name).build();
        Engine.builder()
                .clock(clock)
                .eventBus(InMemoryEventBus.create())
                .worker(worker)
                .checkpointStore(store)
                .build()
                .attach();
        return worker;
    }

    private static Flow orderFlow(FlowId flowId, List<String> trace) {
        return Flow.builder(flowId.flowType(), flowId.flowKey())
                .durable()
                .durableStep("wait", new Step() {
                    @Override
                    protected void onEnter(StepContext ctx) {
                        trace.add("enter:" + flowId.flowKey());
                    }

                    @Override
                    protected StepResult onTick(StepContext ctx) {
                        trace.add("tick:" + ctx.stepNo());
                        return StepResult.done();
                    }
                }, RecoveryPolicy.REENTER_IDEMPOTENT)
                .build();
    }

    private static FlowCheckpoint checkpoint(FlowId id, String workerName, int stepNo) {
        return new FlowCheckpoint(
                id,
                FlowState.RUNNING,
                "wait",
                stepNo,
                true,
                FlowPersistence.DURABLE,
                workerName,
                1_000);
    }

    private static final class InMemoryCheckpointStore implements FlowCheckpointStore {
        private final Map<FlowId, FlowCheckpoint> checkpoints = new LinkedHashMap<>();
        private final List<FlowId> deletes = new ArrayList<>();

        @Override
        public void save(FlowCheckpoint checkpoint) {
            checkpoints.put(checkpoint.flowId(), checkpoint);
        }

        @Override
        public void delete(FlowId flowId) {
            checkpoints.remove(flowId);
            deletes.add(flowId);
        }

        @Override
        public Optional<FlowCheckpoint> find(FlowId flowId) {
            return Optional.ofNullable(checkpoints.get(flowId));
        }

        @Override
        public List<FlowCheckpoint> findActive() {
            return new ArrayList<>(checkpoints.values());
        }

        @Override
        public List<FlowCheckpoint> findActiveByWorker(String workerName) {
            return checkpoints.values().stream()
                    .filter(c -> workerName == null
                            ? c.workerName() == null
                            : workerName.equals(c.workerName()))
                    .collect(Collectors.toList());
        }
    }
}
