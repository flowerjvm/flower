package io.github.flowerjvm.flower.studio.view;

import io.github.flowerjvm.flower.studio.store.StudioDiagnostics;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Trace-list API response. */
public final class TraceListView {

    private final List<TraceSummaryView> traces;
    private final List<String> sources;
    private final int totalMatched;
    private final StudioDiagnostics diagnostics;

    public TraceListView(
            List<TraceSummaryView> traces,
            List<String> sources,
            int totalMatched,
            StudioDiagnostics diagnostics) {
        this.traces = Collections.unmodifiableList(new ArrayList<TraceSummaryView>(traces));
        this.sources = Collections.unmodifiableList(new ArrayList<String>(sources));
        this.totalMatched = totalMatched;
        this.diagnostics = diagnostics;
    }

    public List<TraceSummaryView> getTraces() {
        return traces;
    }

    public List<String> getSources() {
        return sources;
    }

    public int getTotalMatched() {
        return totalMatched;
    }

    public StudioDiagnostics getDiagnostics() {
        return diagnostics;
    }
}
