package io.github.flowerjvm.flower.testkit;

import io.github.flowerjvm.flower.core.engine.Engine;
import io.github.flowerjvm.flower.core.event.InMemoryEventBus;
import io.github.flowerjvm.flower.core.flow.Flow;
import io.github.flowerjvm.flower.core.flow.FlowId;
import io.github.flowerjvm.flower.core.flow.FlowSnapshot;
import io.github.flowerjvm.flower.core.persistence.FlowCheckpoint;
import io.github.flowerjvm.flower.core.recovery.FlowFactoryRegistry;
import io.github.flowerjvm.flower.core.recovery.FlowRecoveryService;
import io.github.flowerjvm.flower.core.time.ManualClock;
import io.github.flowerjvm.flower.core.worker.DuplicatePolicy;
import io.github.flowerjvm.flower.core.worker.Worker;

import java.util.List;
import java.util.Optional;

/**
 * Small deterministic harness for testing one Flower {@link Worker}.
 *
 * <p>The harness creates an {@link Engine}, a {@link Worker}, a
 * {@link ManualClock}, an {@link InMemoryEventBus}, a
 * {@link RecordingFlowerListener}, and a {@link FakeCheckpointStore}. It calls
 * {@link Engine#attach()} instead of starting scheduler threads, so tests drive
 * progress explicitly with {@link #tick()}.
 */
public final class FlowTestHarness implements AutoCloseable {

    public static final String DEFAULT_WORKER_NAME = "test";
    public static final long DEFAULT_INTERVAL_MILLIS = 100L;

    private final Engine engine;
    private final Worker worker;
    private final ManualClock clock;
    private final InMemoryEventBus eventBus;
    private final RecordingFlowerListener listener;
    private final FakeCheckpointStore checkpointStore;

    private FlowTestHarness(Builder b) {
        this.clock = new ManualClock(b.startMillis);
        this.eventBus = InMemoryEventBus.create();
        this.listener = new RecordingFlowerListener();
        this.checkpointStore = b.checkpointStore == null
                ? new FakeCheckpointStore()
                : b.checkpointStore;
        this.worker = Worker.builder(b.workerName)
                .intervalMillis(b.intervalMillis)
                .build();
        this.engine = Engine.builder()
                .clock(clock)
                .eventBus(eventBus)
                .worker(worker)
                .listener(listener)
                .checkpointStore(checkpointStore)
                .build();
        this.engine.attach();
    }

    public static FlowTestHarness create() {
        return builder().build();
    }

    public static Builder builder() {
        return new Builder();
    }

    public Engine engine() {
        return engine;
    }

    public Worker worker() {
        return worker;
    }

    public ManualClock clock() {
        return clock;
    }

    public InMemoryEventBus eventBus() {
        return eventBus;
    }

    public RecordingFlowerListener listener() {
        return listener;
    }

    public FakeCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    public FlowTestHarness submit(Flow flow) {
        worker.submit(flow);
        return this;
    }

    public FlowTestHarness submit(Flow flow, DuplicatePolicy policy) {
        worker.submit(flow, policy);
        return this;
    }

    public FlowTestHarness publish(Object event) {
        eventBus.publish(event);
        return this;
    }

    public FlowTestHarness tick() {
        worker.tickOnce();
        return this;
    }

    public FlowTestHarness ticks(int count) {
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative: " + count);
        }
        for (int i = 0; i < count; i++) {
            tick();
        }
        return this;
    }

    public FlowTestHarness advance(long millis) {
        clock.advance(millis);
        return this;
    }

    public FlowTestHarness advanceAndTick(long millis) {
        return advance(millis).tick();
    }

    public FlowTestHarness recover(FlowCheckpoint checkpoint, FlowFactoryRegistry registry) {
        return recover(checkpoint, registry, DuplicatePolicy.REJECT);
    }

    public FlowTestHarness recover(
            FlowCheckpoint checkpoint,
            FlowFactoryRegistry registry,
            DuplicatePolicy policy) {
        recoveryService(registry).recover(checkpoint, worker, policy);
        return this;
    }

    public FlowTestHarness recoverActive(FlowFactoryRegistry registry) {
        recoverActive(registry, DuplicatePolicy.REJECT);
        return this;
    }

    public FlowTestHarness recoverActive(FlowFactoryRegistry registry, DuplicatePolicy policy) {
        recoverActiveCount(registry, policy);
        return this;
    }

    public int recoverActiveCount(FlowFactoryRegistry registry) {
        return recoverActiveCount(registry, DuplicatePolicy.REJECT);
    }

    public int recoverActiveCount(FlowFactoryRegistry registry, DuplicatePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        return recoveryService(registry).recoverActive(worker, policy);
    }

    public FlowTestHarness recoverActiveForWorker(FlowFactoryRegistry registry) {
        recoverActiveForWorker(registry, DuplicatePolicy.REJECT);
        return this;
    }

    public FlowTestHarness recoverActiveForWorker(FlowFactoryRegistry registry, DuplicatePolicy policy) {
        recoverActiveForWorkerCount(registry, policy);
        return this;
    }

    public int recoverActiveForWorkerCount(FlowFactoryRegistry registry) {
        return recoverActiveForWorkerCount(registry, DuplicatePolicy.REJECT);
    }

    public int recoverActiveForWorkerCount(FlowFactoryRegistry registry, DuplicatePolicy policy) {
        if (policy == null) {
            throw new IllegalArgumentException("policy must not be null");
        }
        return recoveryService(registry).recoverActiveForWorker(worker, policy);
    }

    /**
     * Stop this harness and create a new one that reuses the same checkpoint
     * store. This simulates a process restart while keeping durable Flow
     * positions available for {@link #recoverActive(FlowFactoryRegistry)}.
     */
    public FlowTestHarness restart() {
        stop();
        return FlowTestHarness.builder()
                .workerName(worker.name())
                .intervalMillis(worker.intervalMillis())
                .startMillis(clock.currentTimeMillis())
                .checkpointStore(checkpointStore)
                .build();
    }

    public Optional<FlowSnapshot> activeSnapshot(FlowId flowId) {
        if (flowId == null) {
            throw new IllegalArgumentException("flowId must not be null");
        }
        List<FlowSnapshot> snapshots = worker.snapshot();
        for (FlowSnapshot snapshot : snapshots) {
            if (flowId.equals(snapshot.flowId())) {
                return Optional.of(snapshot);
            }
        }
        return Optional.empty();
    }

    public Optional<FlowSnapshot> latestSnapshot(FlowId flowId) {
        Optional<FlowSnapshot> active = activeSnapshot(flowId);
        return active.isPresent() ? active : listener.latest(flowId);
    }

    public FlowAssertions assertFlow(String flowType, String flowKey) {
        return assertFlow(FlowId.of(flowType, flowKey));
    }

    public FlowAssertions assertFlow(FlowId flowId) {
        return new FlowAssertions(flowId, activeSnapshot(flowId), listener.latest(flowId));
    }

    public void stop() {
        engine.stop();
    }

    @Override
    public void close() {
        stop();
    }

    public static final class Builder {
        private String workerName = DEFAULT_WORKER_NAME;
        private long intervalMillis = DEFAULT_INTERVAL_MILLIS;
        private long startMillis = 0L;
        private FakeCheckpointStore checkpointStore;

        private Builder() {
        }

        public Builder workerName(String workerName) {
            if (workerName == null || workerName.isEmpty()) {
                throw new IllegalArgumentException("workerName must not be null or empty");
            }
            this.workerName = workerName;
            return this;
        }

        public Builder intervalMillis(long intervalMillis) {
            if (intervalMillis <= 0L) {
                throw new IllegalArgumentException("intervalMillis must be positive: " + intervalMillis);
            }
            this.intervalMillis = intervalMillis;
            return this;
        }

        public Builder startMillis(long startMillis) {
            this.startMillis = startMillis;
            return this;
        }

        public Builder checkpointStore(FakeCheckpointStore checkpointStore) {
            if (checkpointStore == null) {
                throw new IllegalArgumentException("checkpointStore must not be null");
            }
            this.checkpointStore = checkpointStore;
            return this;
        }

        public FlowTestHarness build() {
            return new FlowTestHarness(this);
        }
    }

    private FlowRecoveryService recoveryService(FlowFactoryRegistry registry) {
        if (registry == null) {
            throw new IllegalArgumentException("registry must not be null");
        }
        return FlowRecoveryService.create(checkpointStore, registry);
    }
}
