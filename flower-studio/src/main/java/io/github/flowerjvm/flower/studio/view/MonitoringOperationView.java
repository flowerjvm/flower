package io.github.flowerjvm.flower.studio.view;

/** Aggregated Step, model, Tool, or Action operation health. */
public final class MonitoringOperationView {

    private final String category;
    private final String name;
    private final int count;
    private final int completed;
    private final int failures;
    private final double failureRate;
    private final long averageDurationMillis;

    MonitoringOperationView(
            String category,
            String name,
            int count,
            int completed,
            int failures,
            long durationTotal,
            int durationCount) {
        this.category = category;
        this.name = name;
        this.count = count;
        this.completed = completed;
        this.failures = failures;
        this.failureRate = completed == 0 ? 0.0d : ((double) failures) / completed;
        this.averageDurationMillis = durationCount == 0 ? 0L : durationTotal / durationCount;
    }

    public String getCategory() { return category; }
    public String getName() { return name; }
    public int getCount() { return count; }
    public int getCompleted() { return completed; }
    public int getFailures() { return failures; }
    public double getFailureRate() { return failureRate; }
    public long getAverageDurationMillis() { return averageDurationMillis; }
}
