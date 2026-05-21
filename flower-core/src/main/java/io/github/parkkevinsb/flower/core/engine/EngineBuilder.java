package io.github.parkkevinsb.flower.core.engine;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.event.InMemoryEventBus;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.persistence.FlowCheckpointStore;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.core.time.SystemClock;
import io.github.parkkevinsb.flower.core.worker.Worker;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Fluent builder for {@link Engine}.
 *
 * <p>Defaults:
 * <ul>
 *   <li>{@link Clock} = {@link SystemClock#INSTANCE}</li>
 *   <li>{@link EventBus} = {@link InMemoryEventBus}</li>
 *   <li>no listeners</li>
 * </ul>
 *
 * <p>At least one Worker is required.
 */
public final class EngineBuilder {

    private Clock clock = SystemClock.INSTANCE;
    private EventBus eventBus;
    private final Map<String, Worker> workers = new LinkedHashMap<>();
    private final List<FlowerListener> listeners = new ArrayList<>();
    private FlowCheckpointStore checkpointStore = FlowCheckpointStore.NOOP;

    EngineBuilder() {
    }

    public EngineBuilder clock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        return this;
    }

    public EngineBuilder eventBus(EventBus eventBus) {
        this.eventBus = eventBus;
        return this;
    }

    public EngineBuilder worker(Worker worker) {
        if (worker == null) {
            throw new IllegalArgumentException("worker must not be null");
        }
        if (workers.containsKey(worker.name())) {
            throw new IllegalArgumentException("duplicate worker name: " + worker.name());
        }
        workers.put(worker.name(), worker);
        return this;
    }

    public EngineBuilder listener(FlowerListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        listeners.add(listener);
        return this;
    }

    public EngineBuilder checkpointStore(FlowCheckpointStore checkpointStore) {
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.checkpointStore = checkpointStore;
        return this;
    }

    public Engine build() {
        if (workers.isEmpty()) {
            throw new IllegalStateException("Engine must have at least one Worker");
        }
        EventBus bus = eventBus != null ? eventBus : InMemoryEventBus.create();
        return new Engine(clock, bus, workers, listeners, checkpointStore);
    }
}
