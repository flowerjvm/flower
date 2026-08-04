package io.github.flowerjvm.flower.studio.view;

/** Observation and Trace activity inside one deterministic time bucket. */
public final class MonitoringTimeBucketView {

    private final String startedAt;
    private final String endedAt;
    private final int traces;
    private final int failures;
    private final int modelCalls;
    private final int toolCalls;
    private final long inputTokens;
    private final long outputTokens;

    MonitoringTimeBucketView(
            String startedAt,
            String endedAt,
            int traces,
            int failures,
            int modelCalls,
            int toolCalls,
            long inputTokens,
            long outputTokens) {
        this.startedAt = startedAt;
        this.endedAt = endedAt;
        this.traces = traces;
        this.failures = failures;
        this.modelCalls = modelCalls;
        this.toolCalls = toolCalls;
        this.inputTokens = inputTokens;
        this.outputTokens = outputTokens;
    }

    public String getStartedAt() { return startedAt; }
    public String getEndedAt() { return endedAt; }
    public int getTraces() { return traces; }
    public int getFailures() { return failures; }
    public int getModelCalls() { return modelCalls; }
    public int getToolCalls() { return toolCalls; }
    public long getInputTokens() { return inputTokens; }
    public long getOutputTokens() { return outputTokens; }
}
