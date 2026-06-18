package io.github.parkkevinsb.flower.eventloop;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.core.time.SystemClock;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

abstract class SpecializedEventWorkerBuilder<B extends SpecializedEventWorkerBuilder<B>> {

    private final String role;
    private String workerName;
    private Clock clock = SystemClock.INSTANCE;
    private EventBus eventBus;
    private EventFlowCheckpointStore checkpointStore = EventFlowCheckpointStore.NOOP;
    private List<FlowerListener> listeners = Collections.emptyList();
    private Executor offloadExecutor;

    SpecializedEventWorkerBuilder(String role, String name) {
        if (role == null || role.isEmpty()) {
            throw new IllegalArgumentException("role must not be null or empty");
        }
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("name must not be null or empty");
        }
        this.role = role;
        this.workerName = role + "-" + name;
    }

    protected abstract B self();

    public B workerName(String workerName) {
        if (workerName == null || workerName.isEmpty()) {
            throw new IllegalArgumentException("workerName must not be null or empty");
        }
        this.workerName = workerName;
        return self();
    }

    public B clock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        return self();
    }

    public B eventBus(EventBus eventBus) {
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }
        this.eventBus = eventBus;
        return self();
    }

    public B checkpointStore(EventFlowCheckpointStore checkpointStore) {
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.checkpointStore = checkpointStore;
        return self();
    }

    public B listeners(List<FlowerListener> listeners) {
        if (listeners == null) {
            this.listeners = Collections.emptyList();
            return self();
        }
        List<FlowerListener> copy = new ArrayList<>(listeners);
        for (FlowerListener listener : copy) {
            if (listener == null) {
                throw new IllegalArgumentException("listeners must not contain null");
            }
        }
        this.listeners = Collections.unmodifiableList(copy);
        return self();
    }

    public B addListener(FlowerListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        List<FlowerListener> copy = new ArrayList<>(listeners);
        copy.add(listener);
        listeners = Collections.unmodifiableList(copy);
        return self();
    }

    public B offloadExecutor(Executor offloadExecutor) {
        if (offloadExecutor == null) {
            throw new IllegalArgumentException("offloadExecutor must not be null");
        }
        this.offloadExecutor = offloadExecutor;
        return self();
    }

    protected String role() {
        return role;
    }

    protected String workerName() {
        return workerName;
    }

    protected EventFlowCheckpointStore checkpointStore() {
        return checkpointStore;
    }

    protected EventWorker buildDelegate() {
        if (eventBus == null) {
            throw new IllegalStateException(role + " event worker requires an EventBus");
        }
        return new EventWorker(
                workerName,
                clock,
                eventBus,
                checkpointStore,
                listeners,
                offloadExecutor);
    }
}
