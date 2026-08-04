package io.github.flowerjvm.flower.studio.view;

/** Aggregate runtime counts and usage for the loaded observation window. */
public final class MonitoringOverviewView {

    private final String windowStart;
    private final String windowEnd;
    private final int traceCount;
    private final int eventCount;
    private final int completed;
    private final int failed;
    private final int waiting;
    private final int running;
    private final int interrupted;
    private final int cancelled;
    private final int unknown;
    private final long averageTraceDurationMillis;
    private final int modelCalls;
    private final int toolCalls;
    private final int toolFailures;
    private final int actions;
    private final int approvalsRequested;
    private final int waits;
    private final int timeouts;
    private final long inputTokens;
    private final long outputTokens;

    MonitoringOverviewView(
            String windowStart,
            String windowEnd,
            int traceCount,
            int eventCount,
            int completed,
            int failed,
            int waiting,
            int running,
            int interrupted,
            int cancelled,
            int unknown,
            long averageTraceDurationMillis,
            int modelCalls,
            int toolCalls,
            int toolFailures,
            int actions,
            int approvalsRequested,
            int waits,
            int timeouts,
            long inputTokens,
            long outputTokens) {
        this.windowStart = windowStart;
        this.windowEnd = windowEnd;
        this.traceCount = traceCount;
        this.eventCount = eventCount;
        this.completed = completed;
        this.failed = failed;
        this.waiting = waiting;
        this.running = running;
        this.interrupted = interrupted;
        this.cancelled = cancelled;
        this.unknown = unknown;
        this.averageTraceDurationMillis = averageTraceDurationMillis;
        this.modelCalls = modelCalls;
        this.toolCalls = toolCalls;
        this.toolFailures = toolFailures;
        this.actions = actions;
        this.approvalsRequested = approvalsRequested;
        this.waits = waits;
        this.timeouts = timeouts;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String getWindowStart() { return windowStart; }
    public String getWindowEnd() { return windowEnd; }
    public int getTraceCount() { return traceCount; }
    public int getEventCount() { return eventCount; }
    public int getCompleted() { return completed; }
    public int getFailed() { return failed; }
    public int getWaiting() { return waiting; }
    public int getRunning() { return running; }
    public int getInterrupted() { return interrupted; }
    public int getCancelled() { return cancelled; }
    public int getUnknown() { return unknown; }
    public long getAverageTraceDurationMillis() { return averageTraceDurationMillis; }
    public int getModelCalls() { return modelCalls; }
    public int getToolCalls() { return toolCalls; }
    public int getToolFailures() { return toolFailures; }
    public int getActions() { return actions; }
    public int getApprovalsRequested() { return approvalsRequested; }
    public int getWaits() { return waits; }
    public int getTimeouts() { return timeouts; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
}
