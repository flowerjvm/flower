package io.github.flowerjvm.flower.studio.view;

/** One source-owned run inside a correlated trace. */
public final class RunSummaryView {

    private final String runId;
    private final String source;
    private final String parentRunId;
    private final String label;
    private final TraceStatus status;
    private final String startedAt;
    private final String updatedAt;
    private final long durationMillis;
    private final int eventCount;
    private final int depth;

    public RunSummaryView(
            String runId,
            String source,
            String parentRunId,
            String label,
            TraceStatus status,
            String startedAt,
            String updatedAt,
            long durationMillis,
            int eventCount,
            int depth) {
        this.runId = runId;
        this.source = source;
        this.parentRunId = parentRunId;
        this.label = label;
        this.status = status;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.durationMillis = durationMillis;
        this.eventCount = eventCount;
        this.depth = depth;
    }

    public String getRunId() {
        return runId;
    }

    public String getSource() {
        return source;
    }

    public String getParentRunId() {
        return parentRunId;
    }

    public String getLabel() {
        return label;
    }

    public TraceStatus getStatus() {
        return status;
    }

    public String getStartedAt() {
        return startedAt;
    }

    public String getUpdatedAt() {
        return updatedAt;
    }

    public long getDurationMillis() {
        return durationMillis;
    }

    public int getEventCount() {
        return eventCount;
    }

    public int getDepth() {
        return depth;
    }
}
