package io.github.parkkevinsb.flower.eventloop.worker;

import io.github.parkkevinsb.flower.core.event.EventBus;
import io.github.parkkevinsb.flower.core.listener.FlowerListener;
import io.github.parkkevinsb.flower.core.time.Clock;
import io.github.parkkevinsb.flower.eventloop.persistence.EventFlowCheckpointStore;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executor;

/**
 * Fluent builder for {@link EventWorker}.
 */
public final class EventWorkerBuilder {

    private final String name;
    private Clock clock;
    private EventBus eventBus;
    private EventFlowCheckpointStore checkpointStore = EventFlowCheckpointStore.NOOP;
    private final List<FlowerListener> listeners = new ArrayList<>();
    private Executor asyncExecutor;

    EventWorkerBuilder(String name) {
        if (name == null || name.isEmpty()) {
            throw new IllegalArgumentException("worker name must not be null or empty");
        }
        this.name = name;
    }

    public EventWorkerBuilder clock(Clock clock) {
        if (clock == null) {
            throw new IllegalArgumentException("clock must not be null");
        }
        this.clock = clock;
        return this;
    }

    public EventWorkerBuilder eventBus(EventBus eventBus) {
        if (eventBus == null) {
            throw new IllegalArgumentException("eventBus must not be null");
        }
        this.eventBus = eventBus;
        return this;
    }

    public EventWorkerBuilder checkpointStore(EventFlowCheckpointStore checkpointStore) {
        if (checkpointStore == null) {
            throw new IllegalArgumentException("checkpointStore must not be null");
        }
        this.checkpointStore = checkpointStore;
        return this;
    }

    public EventWorkerBuilder listener(FlowerListener listener) {
        if (listener == null) {
            throw new IllegalArgumentException("listener must not be null");
        }
        this.listeners.add(listener);
        return this;
    }

    public EventWorkerBuilder listeners(List<FlowerListener> listeners) {
        if (listeners == null) {
            throw new IllegalArgumentException("listeners must not be null");
        }
        for (FlowerListener listener : listeners) {
            listener(listener);
        }
        return this;
    }

    public EventWorkerBuilder asyncExecutor(Executor asyncExecutor) {
        if (asyncExecutor == null) {
            throw new IllegalArgumentException("asyncExecutor must not be null");
        }
        this.asyncExecutor = asyncExecutor;
        return this;
    }

    public EventWorker build() {
        if (clock == null) {
            throw new IllegalStateException("EventWorker requires a Clock");
        }
        if (eventBus == null) {
            throw new IllegalStateException("EventWorker requires an EventBus");
        }
        return new EventWorker(
                name,
                clock,
                eventBus,
                checkpointStore,
                Collections.unmodifiableList(new ArrayList<>(listeners)),
                asyncExecutor);
    }
}
