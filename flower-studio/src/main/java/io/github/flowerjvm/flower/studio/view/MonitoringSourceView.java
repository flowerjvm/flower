package io.github.flowerjvm.flower.studio.view;

/** Observation volume and Trace coverage for one source runtime. */
public final class MonitoringSourceView {

    private final String source;
    private final int eventCount;
    private final int traceCount;

    MonitoringSourceView(String source, int eventCount, int traceCount) {
        this.source = source;
        this.eventCount = eventCount;
        this.traceCount = traceCount;
    }

    public String getSource() { return source; }
    public int getEventCount() { return eventCount; }
    public int getTraceCount() { return traceCount; }
}
