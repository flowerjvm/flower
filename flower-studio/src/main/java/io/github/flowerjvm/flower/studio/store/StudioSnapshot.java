package io.github.flowerjvm.flower.studio.store;

import io.github.flowerjvm.flower.studio.model.ObservationRecord;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable local observation-file snapshot. */
public final class StudioSnapshot {

    private final List<ObservationRecord> events;
    private final StudioDiagnostics diagnostics;

    public StudioSnapshot(
            List<ObservationRecord> events,
            StudioDiagnostics diagnostics) {
        if (events == null || diagnostics == null) {
            throw new IllegalArgumentException("events and diagnostics must not be null");
        }
        this.events = Collections.unmodifiableList(new ArrayList<ObservationRecord>(events));
        this.diagnostics = diagnostics;
    }

    public List<ObservationRecord> events() {
        return events;
    }

    public StudioDiagnostics diagnostics() {
        return diagnostics;
    }
}
