package io.github.flowerjvm.flower.observability.tracing;

import io.github.flowerjvm.flower.core.trace.FlowerTraceEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/** Thread-safe in-memory trace sink for tests, demos, and local inspection. */
public final class InMemoryFlowerTraceSink implements FlowerTraceSink {

    private final CopyOnWriteArrayList<FlowerTraceEvent> events = new CopyOnWriteArrayList<>();

    @Override
    public void publish(FlowerTraceEvent event) {
        if (event == null) {
            throw new IllegalArgumentException("event must not be null");
        }
        events.add(event);
    }

    public List<FlowerTraceEvent> snapshot() {
        return Collections.unmodifiableList(new ArrayList<>(events));
    }

    public void clear() {
        events.clear();
    }

    public int size() {
        return events.size();
    }
}
