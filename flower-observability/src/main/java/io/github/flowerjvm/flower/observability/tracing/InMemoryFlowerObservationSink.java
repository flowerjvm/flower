package io.github.flowerjvm.flower.observability.tracing;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe in-memory observation sink for tests and local inspection. */
public final class InMemoryFlowerObservationSink implements FlowerObservationSink {

    private final CopyOnWriteArrayList<FlowerObservationEvent> events =
            new CopyOnWriteArrayList<>();

    @Override
    public void publish(FlowerObservationEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        events.add(event);
    }

    public List<FlowerObservationEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
