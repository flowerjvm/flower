package io.github.flowerjvm.flower.studio.view;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Summary row rendered in the Studio trace list. */
public final class TraceSummaryView {

    private final String traceId;
    private final String displayName;
    private final TraceStatus status;
    private final String startedAt;
    private final String updatedAt;
    private final long durationMillis;
    private final int eventCount;
    private final int runCount;
    private final int modelCalls;
    private final int toolCalls;
    private final int actions;
    private final int waits;
    private final int failures;
    private final long inputTokens;
    private final long outputTokens;
    private final List<String> sources;
    private final String rootRunId;

    public TraceSummaryView(
            String traceId,
            String displayName,
            TraceStatus status,
            String startedAt,
            String updatedAt,
            long durationMillis,
            int eventCount,
            int runCount,
            int modelCalls,
            int toolCalls,
            int actions,
            int waits,
            int failures,
            long inputTokens,
            long outputTokens,
            List<String> sources,
            String rootRunId) {
        this.traceId = traceId;
        this.displayName = displayName;
        this.status = status;
        this.startedAt = startedAt;
        this.updatedAt = updatedAt;
        this.durationMillis = durationMillis;
        this.eventCount = eventCount;
        this.runCount = runCount;
        this.modelCalls = modelCalls;
        this.toolCalls = toolCalls;
        this.actions = actions;
        this.waits = waits;
        this.failures = failures;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
        this.sources = Collections.unmodifiableList(new ArrayList<String>(sources));
        this.rootRunId = rootRunId;
    }

    public String getTraceId() {
        return traceId;
    }

    public String getDisplayName() {
        return displayName;
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

    public int getRunCount() {
        return runCount;
    }

    public int getModelCalls() {
        return modelCalls;
    }

    public int getToolCalls() {
        return toolCalls;
    }

    public int getActions() {
        return actions;
    }

    public int getWaits() {
        return waits;
    }

    public int getFailures() {
        return failures;
    }

    public long getInputTokens() {
        return inputTokens;
    }

    public long getOutputTokens() {
        return outputTokens;
    }

    public List<String> getSources() {
        return sources;
    }

    public String getRootRunId() {
        return rootRunId;
    }
}
