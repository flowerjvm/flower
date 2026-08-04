package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.studio.store.StudioDiagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Correlated trace detail response. */
public final class TraceDetailView {

    private final TraceSummaryView summary;
    private final List<RunSummaryView> runs;
    private final List<TimelineEventView> events;
    private final StudioDiagnostics diagnostics;

    public TraceDetailView(
            TraceSummaryView summary,
            List<RunSummaryView> runs,
            List<TimelineEventView> events,
            StudioDiagnostics diagnostics) {
        this.summary = summary;
        this.runs = Collections.unmodifiableList(new ArrayList<RunSummaryView>(runs));
        this.events = Collections.unmodifiableList(new ArrayList<TimelineEventView>(events));
        this.diagnostics = diagnostics;
    }

    public TraceSummaryView getSummary() {
        return summary;
    }

    public List<RunSummaryView> getRuns() {
        return runs;
    }

    public List<TimelineEventView> getEvents() {
        return events;
    }

    public StudioDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
